"""Self-healing ladder, run before any alert is raised."""
# Auto-update check, 30 Oct.
import os
import shutil
import socket
import time
from pathlib import Path

from . import util

REPAIR_PROMPT = ("You are fixing the swarm's git clone at {repo}. Problem: {diagnosis}. "
                 "Only repair git state (locks, half-done merges/rebases, detached HEAD, corrupt index via "
                 "`git reset`). Do not edit tracked files, do not push, do not change code. "
                 "Reply with one line saying what you did.")


def net_ok(host=None, port=443, timeout=3):
    host = host or os.environ.get("SWARM_PROBE_HOST") or "github.com"
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def git_running():
    if util.is_windows():
        r = util.run(["tasklist", "/FI", "IMAGENAME eq git.exe", "/NH"], timeout=20)
        return "git.exe" in r.out.lower()
    return util.run(["pgrep", "-x", "git"], timeout=20).code == 0


def _lock_files(repo):
    git_dir = Path(repo) / ".git"
    names = ["index.lock", "HEAD.lock", "packed-refs.lock", "config.lock", "shallow.lock"]
    files = [git_dir / n for n in names]
    try:
        files += list((git_dir / "refs").rglob("*.lock"))
        files += list((git_dir / "worktrees").glob("*/index.lock"))
    except OSError:
        pass
    return [f for f in files if f.exists()]


def clear_stale_locks(repo):
    removed = []
    running = None
    for lock in _lock_files(repo):
        try:
            if running is None:
                running = git_running()
            old = time.time() - lock.stat().st_mtime > 600
            if running and not old:
                continue
            lock.unlink()
            removed.append(lock.name)
            util.event("cleared stale " + lock.name)
        except OSError:
            pass
    return removed


def _git(repo, *args):
    return util.run(["git"] + list(args), cwd=repo, timeout=None)


def abort_half_done(repo):
    git_dir = Path(repo) / ".git"
    actions = []
    for name in ("rebase-merge", "rebase-apply"):
        if (git_dir / name).exists():
            if not _git(repo, "rebase", "--abort").ok:
                shutil.rmtree(git_dir / name, ignore_errors=True)
            actions.append("aborted " + name)
    if (git_dir / "MERGE_HEAD").exists():
        _git(repo, "merge", "--abort")
        actions.append("aborted merge")
    if (git_dir / "CHERRY_PICK_HEAD").exists():
        _git(repo, "cherry-pick", "--abort")
        actions.append("aborted cherry-pick")
    for action in actions:
        util.event(action)
    return actions


def prepare_repo(repo=None):
    if repo is None:
        from . import config
        repo = config.repo_dir()
    return clear_stale_locks(repo) + abort_half_done(repo)


def _ok_of(result):
    if isinstance(result, tuple):
        return bool(result[0]), (str(result[1]) if len(result) > 1 else "")
    return bool(result), ""


def _try_retry(retry_fn):
    """Run retry_fn up to once and return (ok, message). Never raises."""
    try:
        return _ok_of(retry_fn())
    except Exception as e:
        return False, repr(e)


def _usable_worker():
    try:
        from . import config
        workers = config.workers()
    except BaseException:
        return None
    blocked = set()
    try:
        from . import listener
        blocked = {n for n, s in listener.STATES.items() if s.get("state") == "blocked"}
    except Exception:
        pass
    for w in workers:
        if w.get("enabled", True) and w.get("name") not in blocked and w.get("harness") != "fake":
            return w
    return None


def _repair_recently(problem):
    log = util.load_json(util.state_dir() / "repairs.json", {}) or {}
    return time.time() - log.get(problem[:80], 0) < 1800


def _note_repair(problem):
    path = util.state_dir() / "repairs.json"
    log = util.load_json(path, {}) or {}
    log[problem[:80]] = time.time()
    util.save_json(path, log)


def _run_repair_agent(problem, repo):
    worker = _usable_worker()
    if worker is None or _repair_recently(problem):
        return False
    try:
        from . import harness
    except Exception:
        return False
    _note_repair(problem)
    prompt = REPAIR_PROMPT.format(repo=repo, diagnosis=problem)
    util.event("repair agent started (%s)" % worker.get("name"))
    harness.run_agent(worker, repo, prompt, 600)
    return True


def ladder(problem, retry_fn):
    if not net_ok():
        return False, "offline"
    actions = []
    try:
        from . import config
        repo = config.repo_dir()
        actions = prepare_repo(repo)
    except BaseException:
        repo = None
    last = problem
    for attempt in range(2):
        ok, msg = _try_retry(retry_fn)
        if ok:
            return True, "fixed: " + (", ".join(actions) or "retry worked")
        last = msg or last
        if attempt == 0:
            time.sleep(5)
    if repo is not None and _run_repair_agent(problem, repo):
        ok, msg = _try_retry(retry_fn)
        if ok:
            return True, "fixed by agent"
        last = msg or last
    return False, last
