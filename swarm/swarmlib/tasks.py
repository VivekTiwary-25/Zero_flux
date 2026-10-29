import fnmatch
import secrets
import threading
import time
from pathlib import Path

from . import config, gitops, harness, util

KEY_ORDER = ["id", "title", "kind", "files", "created", "moved_from"]
BLOCKING = ("key_expired", "logged_out", "tokens", "bad_model", "offline")
_done_lock = threading.Lock()

PROMPT = """# Task {id}: {title}
You are one worker in a team "swarm". Work only in this folder (a git checkout on branch task/{id}).
Files that matter: {files}. Read only what you need.

{body}

Rules:
- Do not run git commit, git push, or change branches. The swarm commits for you.
- Do not edit anything under swarm/ except your report.
- When done, write swarm/reports/{id}.md: 3-6 short sentences: what you changed, how you checked it, what is left.
"""


def parse(text):
    text = text.replace("\r\n", "\n")
    task = {}
    body = text
    if text.startswith("---\n"):
        end = text.find("\n---", 3)
        if end != -1:
            for line in text[4:end].split("\n"):
                if ":" in line:
                    key, value = line.split(":", 1)
                    task[key.strip()] = value.strip()
            body = text[end + 4:]
            if body.startswith("\n"):
                body = body[1:]
    task["body"] = body
    return task


def render(task):
    keys = [k for k in KEY_ORDER if k in task]
    keys += [k for k in task if k not in KEY_ORDER and k != "body"]
    lines = ["%s: %s" % (k, task[k]) for k in keys if task[k] not in (None, "")]
    return "---\n" + "\n".join(lines) + "\n---\n" + task.get("body", "")


def new_id():
    return time.strftime("t%y%m%d-%H%M-") + secrets.token_hex(2)


def queue_root():
    return config.repo_dir() / "swarm" / "queue"


def queue_dir(machine, worker):
    return queue_root() / machine / worker


def queued(machine, worker):
    return sorted(queue_dir(machine, worker).glob("*.md"), key=lambda p: p.name)


def all_queue_files():
    return sorted(queue_root().glob("*/*/*.md"), key=lambda p: p.name)


def files_of(task):
    return task.get("files") or "(see task)"


def done_path():
    return util.state_dir() / "done.json"


def done_ids():
    return {d.get("id") for d in util.load_json(done_path(), [])}


def mark_done(entry):
    with _done_lock:
        done = util.load_json(done_path(), [])
        done.append(entry)
        util.save_json(done_path(), done[-500:])


def forget_done(task_id):
    with _done_lock:
        done = [d for d in util.load_json(done_path(), []) if d.get("id") != task_id]
        util.save_json(done_path(), done)


def _worker_key(machine, hb_worker):
    return "%s/%s" % (machine, hb_worker.get("name"))


def pick_worker(kind, heartbeats, exclude=frozenset()):
    routing = config.routing()
    settings = routing.get("settings", {})
    routes = routing.get("routes", {})
    patterns = list(routes.get(kind, []))
    patterns += [p for plist in routes.values() for p in plist if p not in patterns]
    never = routing.get("never_models", [])
    best = None
    for machine, hb in heartbeats.items():
        if util.now() - hb.get("ts", 0) >= settings.get("peer_stale_sec", 180):
            continue
        for w in hb.get("workers", []):
            key = _worker_key(machine, w)
            model = w.get("model", "")
            if key in exclude or w.get("state") in ("blocked", "disabled"):
                continue
            pct = w.get("remaining_pct")
            if pct is not None and pct <= settings.get("low_usage_pct", 3):
                continue
            if any(fnmatch.fnmatch(model, n) for n in never):
                continue
            index = next((i for i, p in enumerate(patterns) if fnmatch.fnmatch(model, p)), None)
            if index is None:
                continue
            load = (hb.get("queued") or {}).get(w.get("name"), 0) + (1 if w.get("state") == "busy" else 0)
            score = (index, load, -(pct if pct is not None else 50), key)
            if best is None or score < best[0]:
                best = (score, key)
    return best[1] if best else None


def move_tasks(src_machine, src_worker, heartbeats, reason):
    moved = []
    src_root = queue_root() / src_machine

    def apply_fn(repo):
        del moved[:]
        exclude = set()
        if src_worker:
            exclude.add("%s/%s" % (src_machine, src_worker))
            dirs = [src_root / src_worker]
        else:
            hb = heartbeats.get(src_machine, {})
            exclude |= {_worker_key(src_machine, w) for w in hb.get("workers", [])}
            dirs = [d for d in sorted(src_root.glob("*")) if d.is_dir()]
            exclude |= {"%s/%s" % (src_machine, d.name) for d in dirs}
        for d in dirs:
            for path in sorted(d.glob("*.md")):
                task = parse(path.read_text(encoding="utf-8"))
                target = pick_worker(task.get("kind") or "routine", heartbeats, exclude)
                if not target:
                    moved.append((task.get("id", path.stem), None))
                    continue
                task["moved_from"] = "%s/%s" % (src_machine, d.name)
                tm, tw = target.split("/", 1)
                dest = queue_dir(tm, tw)
                dest.mkdir(parents=True, exist_ok=True)
                (dest / path.name).write_text(render(task), encoding="utf-8", newline="\n")
                path.unlink()
                moved.append((task.get("id", path.stem), target))

    if not any(src_root.glob("*/*.md")):
        return []
    ok, msg = gitops.commit_to_main(apply_fn, "swarm: move tasks from %s (%s)" % (src_machine, reason))
    if not ok:
        util.log("listener", "move_tasks failed: %s" % msg)
        return [(i, None) for i, _ in moved]
    for tid, target in moved:
        if target:
            util.event("moved task %s from %s to %s (%s)" % (tid, src_machine, target, reason))
    return moved


def _report_fallback(res):
    lines = ((res.out or "") + "\n" + (res.err or "")).strip().splitlines()[-40:]
    return "Agent exited with code %s without writing a report. Last output:\n\n%s\n" % (
        res.code, "\n".join(lines))


def _result(status, tid, title, problem=None):
    return {"status": status, "problem": problem, "id": tid, "title": title}


def _commit_and_push(tid, title, worker, wt):
    machine = config.machine()
    with gitops.git_lock():
        gitops.git("add", "-A", cwd=wt)
        msg = "task %s: %s (%s/%s)" % (tid, title, machine, worker["name"])
        res = gitops.git("commit", "-m", msg, cwd=wt)
        if not res.ok:
            return False, "commit failed: %s" % (res.err or res.out).strip()
        res = gitops.push_branch(tid, wt)
        if not res.ok:
            return False, "push failed: %s" % (res.err or res.out).strip()
    return True, ""


def _cleanup(tid):
    try:
        with gitops.git_lock():
            gitops.remove_worktree(tid)
            gitops.git("branch", "-D", "task/" + tid)
    except Exception as e:
        util.log("listener", "cleanup of %s failed: %s" % (tid, e))


def run_task(worker, path, holder=None):
    path = Path(path)
    task = parse(path.read_text(encoding="utf-8"))
    tid = task.get("id") or path.stem
    title = task.get("title") or tid
    if tid in done_ids() or tid in gitops.remote_task_branches():
        return _result("skipped", tid, title)
    machine = config.machine()
    try:
        with gitops.git_lock():
            wt = gitops.add_worktree(tid)
        prompt = PROMPT.format(id=tid, title=title, files=files_of(task), body="@@BODY@@")
        (wt / "SWARM_TASK.md").write_text(prompt.replace("@@BODY@@", task["body"].strip()),
                                          encoding="utf-8", newline="\n")
        timeout_s = config.settings().get("task_timeout_min", 45) * 60
        res = harness.run_agent(worker, wt, harness.TASK_PROMPT, timeout_s,
                                env_extra={"SWARM_TASK_ID": tid}, holder=holder)
        if holder and holder.get("abort"):
            return _result("skipped", tid, title, "stopped by swarm down")
        problem = harness.classify(res)
        if problem in BLOCKING:
            return _result("blocked", tid, title, problem)
        (wt / "SWARM_TASK.md").unlink()
        report = wt / "swarm" / "reports" / (tid + ".md")
        if not report.exists():
            report.parent.mkdir(parents=True, exist_ok=True)
            report.write_text(_report_fallback(res), encoding="utf-8", newline="\n")
        ok, err = _commit_and_push(tid, title, worker, wt)
        if not ok:
            return _result("error", tid, title, err)
        mark_done({"id": tid, "worker": "%s/%s" % (machine, worker["name"]),
                   "ok": res.code == 0, "ts": util.now()})
        util.event("task %s finished by %s/%s, branch task/%s ready for review"
                   % (tid, machine, worker["name"], tid), machine)
        return _result("done", tid, title)
    except Exception as e:
        util.log("listener", "run_task %s crashed: %r" % (tid, e))
        return _result("error", tid, title, repr(e))
    finally:
        _cleanup(tid)
