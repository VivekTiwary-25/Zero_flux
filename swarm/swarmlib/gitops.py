"""All git work, done in the dedicated clone. Never rebase, never kill git."""
import contextlib
import json
import os
import shutil
import threading
import time
from pathlib import Path

from . import config, heal, util

_RLOCK = threading.RLock()
_depth = 0

LOW_SPEED = ["-c", "http.lowSpeedLimit=1000", "-c", "http.lowSpeedTime=30"]
FETCH_SPECS = ["+refs/heads/main:refs/remotes/origin/main",
               "+refs/heads/hb/*:refs/remotes/origin/hb/*",
               "+refs/heads/task/*:refs/remotes/origin/task/*"]


# ---------- locking ----------

def _lock_path():
    return util.state_dir() / "git.lock"


def _lock_is_stale(path):
    try:
        age = time.time() - path.stat().st_mtime
        pid = int(path.read_text().strip() or 0)
    except Exception:
        return True
    if age > 600:
        return True
    return pid == os.getpid() or not util.pid_alive(pid)


def _acquire_file():
    path = _lock_path()
    deadline = time.time() + 180
    while True:
        try:
            fd = os.open(str(path), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, str(os.getpid()).encode())
            os.close(fd)
            return
        except FileExistsError:
            if _lock_is_stale(path):
                try:
                    path.unlink()
                except OSError:
                    pass
                continue
            if time.time() > deadline:
                raise RuntimeError("git lock busy for 180 s")
            time.sleep(0.5)


def _release_file():
    try:
        _lock_path().unlink()
    except OSError:
        pass


@contextlib.contextmanager
def git_lock():
    global _depth
    with _RLOCK:
        if _depth == 0:
            _acquire_file()
        _depth += 1
        try:
            yield
        finally:
            _depth -= 1
            if _depth == 0:
                _release_file()


# ---------- running git ----------

def _env():
    env = dict(os.environ)
    env["GIT_TERMINAL_PROMPT"] = "0"
    env["GCM_INTERACTIVE"] = "never"
    return env


def git(*args, cwd=None, input=None):
    cmd = ["git"] + LOW_SPEED + [str(a) for a in args]
    return util.run(cmd, cwd=cwd or config.repo_dir(), env=_env(), timeout=None, input=input)


def short(text):
    lines = [x.strip() for x in (text or "").splitlines() if x.strip()]
    return lines[-1][:160] if lines else ""


def _status(**fields):
    path = util.state_dir() / "git_status.json"
    data = util.load_json(path, {}) or {}
    data.update(fields)
    util.save_json(path, data)


def _pull_ok():
    _status(last_pull_ok=util.now(), last_error="")


def _push_ok():
    _status(last_push_ok=util.now())


# ---------- fetch / sync ----------

def fetch_all():
    return git("fetch", "--prune", "origin", *FETCH_SPECS)


def _sync_once():
    r = fetch_all()
    if not r.ok:
        return False, "fetch failed: " + short(r.err or r.out)
    head = git("rev-parse", "HEAD").out.strip()
    target = git("rev-parse", "origin/main").out.strip()
    branch = git("symbolic-ref", "--short", "-q", "HEAD").out.strip()
    if head != target or branch != "main":
        r = git("checkout", "-f", "-B", "main", "origin/main")
        if not r.ok:
            return False, "checkout failed: " + short(r.err or r.out)
        r = git("reset", "--hard", "origin/main")
        if not r.ok:
            return False, "reset failed: " + short(r.err or r.out)
    return True, "ok"


def sync_main():
    try:
        with git_lock():
            heal.prepare_repo(config.repo_dir())
            ok, msg = _sync_once()
            if not ok:
                ok, diag = heal.ladder(msg, _sync_once)
                msg = "ok" if ok else (msg if diag == msg else "%s (%s)" % (msg, diag))
    except Exception as e:
        ok, msg = False, "sync error: %r" % e
    if ok:
        _pull_ok()
    else:
        _status(last_error=msg)
    return ok, msg


def commit_to_main(apply_fn, message):
    msg = "not started"
    try:
        with git_lock():
            for attempt in range(4):
                ok, msg = _commit_attempt(apply_fn, message)
                if ok:
                    return True, msg
                if attempt < 3:
                    time.sleep(1)
    except Exception as e:
        msg = "commit error: %r" % e
    _status(last_error=msg)
    return False, msg


def _commit_attempt(apply_fn, message):
    repo = config.repo_dir()
    r = git("fetch", "origin", "+refs/heads/main:refs/remotes/origin/main")
    if not r.ok:
        return False, "fetch failed: " + short(r.err or r.out)
    for args in (("checkout", "-f", "-B", "main", "origin/main"), ("reset", "--hard", "origin/main")):
        r = git(*args)
        if not r.ok:
            return False, "%s failed: %s" % (args[0], short(r.err or r.out))
    apply_fn(repo)
    git("add", "-A", "swarm")
    if git("diff", "--cached", "--quiet").ok:
        return True, "nothing to commit"
    r = git("commit", "-m", message)
    if not r.ok:
        return False, "commit failed: " + short(r.err or r.out)
    r = git("push", "origin", "HEAD:refs/heads/main")
    if not r.ok:
        return False, "push failed: " + short(r.err or r.out)
    _push_ok()
    return True, "pushed"


# ---------- heartbeats ----------

def force_push(sha, ref):
    """The only force push in swarm; only for refs/heads/hb/*."""
    if not ref.startswith("refs/heads/hb/"):
        raise ValueError("force push refused for %s" % ref)
    return git("push", "-f", "origin", "%s:%s" % (sha, ref))


def push_heartbeat(machine, data):
    try:
        blob = git("hash-object", "-w", "--stdin", input=json.dumps(data))
        if not blob.ok:
            return False, "hash-object failed: " + short(blob.err)
        entry = "100644 blob %s\theartbeat.json\n" % blob.out.strip()
        tree = git("mktree", input=entry)
        if not tree.ok:
            return False, "mktree failed: " + short(tree.err)
        commit = git("commit-tree", tree.out.strip(), "-m", "hb " + machine)
        if not commit.ok:
            return False, "commit-tree failed: " + short(commit.err)
        r = force_push(commit.out.strip(), "refs/heads/hb/" + machine)
        if not r.ok:
            return False, "push failed: " + short(r.err or r.out)
        _push_ok()
        return True, "ok"
    except Exception as e:
        return False, "heartbeat error: %r" % e


def read_heartbeats():
    prefix = "refs/remotes/origin/hb/"
    r = git("for-each-ref", "--format=%(refname)", "refs/remotes/origin/hb")
    result = {}
    for ref in r.out.split():
        if not ref.startswith(prefix):
            continue
        shown = git("show", ref + ":heartbeat.json")
        if not shown.ok:
            continue
        try:
            result[ref[len(prefix):]] = json.loads(shown.out)
        except ValueError:
            pass
    return result


def remote_task_branches():
    prefix = "refs/remotes/origin/task/"
    r = git("for-each-ref", "--format=%(refname)", "refs/remotes/origin/task")
    return {ref[len(prefix):] for ref in r.out.split() if ref.startswith(prefix)}


# ---------- worktrees and task branches ----------

def worktree_path(task_id):
    return util.home() / "work" / task_id


def add_worktree(task_id):
    path = worktree_path(task_id)
    with git_lock():
        git("worktree", "prune")
        if path.exists():
            remove_worktree(task_id)
        path.parent.mkdir(parents=True, exist_ok=True)
        r = git("worktree", "add", "-f", "-B", "task/" + task_id, str(path), "origin/main")
    if not r.ok:
        raise RuntimeError("worktree add failed: " + short(r.err or r.out))
    return path


def _rmtree_force(path):
    def on_error(func, p, _exc):
        try:
            os.chmod(p, 0o700)
            func(p)
        except OSError:
            pass
    shutil.rmtree(path, onerror=on_error)


def remove_worktree(task_id):
    path = worktree_path(task_id)
    with git_lock():
        git("worktree", "remove", "--force", str(path))
        if path.exists():
            _rmtree_force(path)
        git("worktree", "prune")
        git("branch", "-D", "task/" + task_id)


def push_branch(task_id, cwd):
    return git("push", "-u", "origin", "task/" + task_id, cwd=cwd)


def delete_remote_branch(name):
    if not name.startswith("task/"):
        raise ValueError("only task/* branches may be deleted, got %s" % name)
    return git("push", "origin", "--delete", name)
