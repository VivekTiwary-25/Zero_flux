import sys
import time

from . import gitops, tasks, util


def _read_body(source):
    if source == "-":
        return sys.stdin.read()
    with open(source, encoding="utf-8") as f:
        return f.read()


def _guess_title(body):
    for line in body.splitlines():
        line = line.strip()
        if line.startswith("#"):
            return line.lstrip("#").strip()
    for line in body.splitlines():
        if line.strip():
            return line.strip()[:80]
    return "untitled"


def resolve_target(target):
    """machine/worker as given, or a kind resolved with pick_worker."""
    if "/" in target:
        return target
    gitops.fetch_all()
    return tasks.pick_worker(target, gitops.read_heartbeats())


def _write_task(repo, task, target):
    machine, worker = target.split("/", 1)
    dest = tasks.queue_dir(machine, worker)
    dest.mkdir(parents=True, exist_ok=True)
    (dest / (task["id"] + ".md")).write_text(tasks.render(task), encoding="utf-8", newline="\n")


def queue(args):
    target = resolve_target(args.target)
    if not target:
        print("No free worker for '%s' right now. Try 'swarm status' or name one: machine/worker." % args.target)
        return 1
    body = _read_body(args.file)
    kind = args.kind or (args.target if "/" not in args.target else "routine")
    task = {"id": tasks.new_id(), "title": args.title or _guess_title(body), "kind": kind,
            "files": args.files or "", "created": time.strftime("%Y-%m-%d %H:%M"), "body": body}
    ok, msg = gitops.commit_to_main(lambda repo: _write_task(repo, task, target),
                                    "swarm: queue %s → %s" % (task["id"], target))
    if not ok:
        print("Could not queue the task: %s" % msg)
        return 1
    util.event("queued task %s for %s" % (task["id"], target))
    print("%s -> %s" % (task["id"], target))
    return 0


def _find_queue_files(task_id):
    return [p for p in tasks.all_queue_files() if p.stem == task_id]


def _short(path):
    return "%s/%s" % (path.parent.parent.name, path.parent.name)


def tasks_cmd(args):
    gitops.fetch_all()
    heartbeats = gitops.read_heartbeats()
    branches = gitops.remote_task_branches()
    files = tasks.all_queue_files()
    waiting = [p for p in files if p.stem in branches]
    waiting_ids = {p.stem for p in waiting}
    running = {}
    for machine, hb in heartbeats.items():
        for w in hb.get("workers", []):
            if w.get("task"):
                running[w["task"]["id"]] = ("%s/%s" % (machine, w["name"]), w["task"])
    print("RUNNING")
    for tid, (who, t) in sorted(running.items()):
        print("  %-24s %s  %s  (%s)" % (who, tid, t.get("title", ""),
                                        util.fmt_age(util.now() - t.get("started", util.now()))))
    print("QUEUED")
    for p in files:
        if p.stem in waiting_ids or p.stem in running:
            continue
        title = tasks.parse(p.read_text(encoding="utf-8")).get("title", "")
        print("  %-24s %s  %s" % (_short(p), p.stem, title))
    print("WAITING REVIEW  (swarm review <id> / merge <id> / reject <id>)")
    for p in waiting:
        title = tasks.parse(p.read_text(encoding="utf-8")).get("title", "")
        print("  %-24s %s  %s" % (_short(p), p.stem, title))
    print("DONE (recent)")
    for machine, hb in sorted(heartbeats.items()):
        for d in (hb.get("done") or [])[-5:]:
            print("  %-24s %s  %s" % (d.get("worker", machine), d.get("id"),
                                      "ok" if d.get("ok") else "failed"))
    return 0


def _branch_check(task_id):
    gitops.fetch_all()
    if task_id not in gitops.remote_task_branches():
        print("No branch task/%s on GitHub. Is it finished yet? See: swarm tasks" % task_id)
        return False
    return True


def review(args):
    tid = args.id
    if not _branch_check(tid):
        return 1
    ref = "origin/task/" + tid
    rep = gitops.git("show", "%s:swarm/reports/%s.md" % (ref, tid))
    print("REPORT\n" + (rep.out.strip() if rep.ok else "(no report found)"))
    stat = gitops.git("diff", "--stat", "origin/main..." + ref)
    print("\nCHANGES\n" + stat.out.strip())
    diff = gitops.git("diff", "origin/main..." + ref)
    lines = diff.out.splitlines()
    shown = lines if args.full else lines[:300]
    print("\nDIFF\n" + "\n".join(shown))
    if len(shown) < len(lines):
        print("... %d more lines. Use: swarm review %s --full" % (len(lines) - len(shown), tid))
    return 0


def merge(args):
    tid = args.id
    if not _branch_check(tid):
        return 1
    state = {"conflict": False}

    def apply_fn(repo):
        state["conflict"] = False
        res = gitops.git("merge", "--no-ff", "--no-edit", "origin/task/" + tid, cwd=repo)
        if not res.ok:
            gitops.git("merge", "--abort", cwd=repo)
            state["conflict"] = True
            return
        for p in _find_queue_files(tid):
            p.unlink()

    ok, msg = gitops.commit_to_main(apply_fn, "swarm: merged %s" % tid)
    if state["conflict"]:
        print("Conflict merging %s. Resolve by hand in your own clone or: swarm reject %s --requeue <target>"
              % (tid, tid))
        return 1
    if not ok:
        print("Merge failed: %s" % msg)
        return 1
    if not _on_main(tid):
        with gitops.git_lock():
            push = gitops.git("push", "origin", "HEAD:refs/heads/main")
        if not push.ok:
            print("Merged locally but push failed: %s" % (push.err or push.out).strip())
            return 1
    gitops.delete_remote_branch("task/" + tid)
    util.event("merged task %s into main" % tid)
    print("Merged %s into main and deleted its branch." % tid)
    return 0


def _on_main(tid):
    gitops.fetch_all()
    res = gitops.git("merge-base", "--is-ancestor", "origin/task/" + tid, "origin/main")
    return res.ok


def reject(args):
    tid = args.id
    gitops.fetch_all()
    files = _find_queue_files(tid)
    original = tasks.parse(files[0].read_text(encoding="utf-8")) if files else None
    target = None
    if args.requeue:
        if not original:
            print("Queue file for %s not found, cannot requeue." % tid)
            return 1
        target = resolve_target(args.requeue)
        if not target:
            print("No free worker for '%s' right now." % args.requeue)
            return 1
    new = None
    if original:
        new = dict(original)
        new["id"] = tasks.new_id()
        new.pop("moved_from", None)
        if args.note:
            new["body"] = original["body"].rstrip() + "\n\n---\nPrevious attempt %s was rejected: %s\n" % (tid, args.note)

    def apply_fn(repo):
        for p in _find_queue_files(tid):
            p.unlink()
        if target and new:
            _write_task(repo, new, target)

    msg = "swarm: reject %s" % tid + (" and requeue as %s" % new["id"] if target else "")
    ok, out = gitops.commit_to_main(apply_fn, msg)
    if not ok:
        print("Reject failed: %s" % out)
        return 1
    if tid in gitops.remote_task_branches():
        gitops.delete_remote_branch("task/" + tid)
    util.event("rejected task %s" % tid + (" (requeued as %s -> %s)" % (new["id"], target) if target else ""))
    print("Rejected %s." % tid + (" Requeued as %s -> %s" % (new["id"], target) if target else ""))
    return 0
