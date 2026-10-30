"""The listener: one per PC. Syncs git, runs queued tasks, publishes a heartbeat."""
import fnmatch
import hashlib
import os
import sys
import threading
import time
import traceback

from . import alerts, config, gitops, heal, util

RETRY_SEC = 900
MOVE_RETRY_SEC = 300

# worker name -> {"state","task","problem","blocked_until","holder","thread","usage", ...}
STATES = {}
LAST_HB = {}
MOVE_NOTES = {}
ALERT_SINCE = {}
GIT_ERROR = {"msg": None}
CTX = {"usage_at": 0, "boss": [], "workers": [], "move_at": {}}
START = util.now()
_sock = None


# ---------- small helpers ----------

def _step(name, fn, default=None):
    try:
        return fn()
    except SystemExit:
        raise
    except Exception:
        util.log("listener", "step %s failed: %s" % (name, traceback.format_exc()))
        return default


def down_flag():
    return (util.state_dir() / "down").exists()


def code_hash():
    h = hashlib.sha1()
    root = util.code_dir()
    for path in sorted(root.rglob("*.py")):
        try:
            h.update(path.read_bytes())
        except OSError:
            pass
    return h.hexdigest()[:8]


def _sleep(seconds):
    for _ in range(int(seconds)):
        if down_flag():
            return
        time.sleep(1)


def _is_never(model, routing):
    return any(fnmatch.fnmatch(model or "", p) for p in routing.get("never_models", []))


def _new_state():
    return {"state": "idle", "task": None, "problem": None, "blocked_until": 0, "holder": {},
            "thread": None, "usage": None, "moved": False, "target": None, "by_usage": False,
            "testing": False, "failed": set()}


def _sync_states(workers):
    names = {w["name"] for w in workers}
    for w in workers:
        st = STATES.setdefault(w["name"], _new_state())
        enabled = w.get("enabled", True)
        if not enabled and st["state"] in ("idle", "blocked"):
            st["state"] = "disabled"
        elif enabled and st["state"] == "disabled":
            st["state"] = "idle"
    for name in list(STATES):
        if name not in names and STATES[name]["state"] != "busy":
            del STATES[name]


def _busy_count():
    return sum(1 for s in STATES.values() if s["state"] == "busy")


def _queued(me, name):
    try:
        from . import tasks
        return tasks.queued(me, name)
    except Exception:
        return []


# ---------- blocking and unblocking ----------

def _block(w, st, problem, by_usage=False):
    now = util.now()
    resets_at = (st.get("usage") or {}).get("resets_at")
    st.update(state="blocked", problem=problem, task=None, moved=False, target=None, by_usage=by_usage)
    if problem == "offline":
        st["blocked_until"] = 0
    elif problem == "tokens" and resets_at and resets_at > now:
        st["blocked_until"] = resets_at
    else:
        st["blocked_until"] = now + RETRY_SEC
    util.event("worker %s blocked: %s" % (w["name"], problem))


def _unblock(w, st, why):
    st.update(state="idle", problem=None, moved=False, target=None, by_usage=False, testing=False)
    util.event("worker %s unblocked (%s)" % (w["name"], why))


def _retry_thread(w, st):
    try:
        from . import harness
        ok, msg = harness.quick_test(w)
    except Exception as e:
        ok, msg = False, repr(e)
    if st["state"] != "blocked":
        return
    if ok:
        _unblock(w, st, "test passed")
    else:
        st["blocked_until"] = util.now() + RETRY_SEC
        util.log("listener", "retry of %s failed: %s" % (w["name"], str(msg)[-200:]))
    st["testing"] = False


def _retry_blocked(workers, now):
    for w in workers:
        st = STATES[w["name"]]
        if st["state"] != "blocked" or st["testing"] or now < st["blocked_until"]:
            continue
        st["testing"] = True
        threading.Thread(target=_retry_thread, args=(w, st), daemon=True).start()


def _apply_usage(workers, low, now):
    for w in workers:
        st = STATES[w["name"]]
        pct = (st.get("usage") or {}).get("remaining_pct")
        if pct is None:
            continue
        if st["state"] == "idle" and pct <= low:
            _block(w, st, "tokens", by_usage=True)
        elif st["state"] == "blocked" and st["by_usage"] and pct > low:
            _unblock(w, st, "usage back to %s%%" % pct)


def _refresh_usage(me, workers, settings, now):
    if now - CTX["usage_at"] < settings.get("usage_refresh_sec", 300):
        return
    CTX["usage_at"] = now
    from . import usage
    for w in workers:
        if w.get("enabled", True):
            st = STATES[w["name"]]
            st["usage"] = _step("usage " + w["name"], lambda w=w: usage.usage_for(w))
    if config.routing().get("boss", {}).get("machine") == me:
        CTX["boss"] = _step("boss usage", usage.boss_usage, default=[]) or []


# ---------- moving tasks ----------

def _targets(moved):
    found = []
    for item in moved or []:
        target = item[1]
        if target and target not in found:
            found.append(target)
    return found


def _with_me(heartbeats, me, workers, now, net):
    merged = dict(heartbeats)
    merged[me] = _build_hb(me, workers, now, [], net)
    return merged


def _handle_moves(me, workers, heartbeats, now, net):
    from . import tasks
    for w in workers:
        st = STATES[w["name"]]
        if st["state"] != "blocked" or st["problem"] in (None, "offline"):
            continue
        paths = _queued(me, w["name"])
        if not paths:
            if not st["moved"]:
                st["moved"] = True
                st["target"] = "nobody (no queued tasks)"
            continue
        key = "worker:" + w["name"]
        if now - CTX["move_at"].get(key, 0) < MOVE_RETRY_SEC:
            continue
        CTX["move_at"][key] = now
        hbs = _with_me(heartbeats, me, workers, now, net)
        moved = tasks.move_tasks(me, w["name"], hbs, st["problem"])
        targets = _targets(moved)
        st["moved"] = True
        if targets:
            st["target"] = ", ".join(targets)


def _elected_mover(me, heartbeats, now, stale):
    fresh = [m for m, h in heartbeats.items() if m != me and now - h.get("ts", 0) < stale]
    return min(fresh + [me]) == me


def _move_dead_machines(me, workers, heartbeats, settings, now, net):
    from . import tasks
    stale = settings.get("peer_stale_sec", 180)
    dead = settings.get("peer_dead_sec", 600)
    mover = _elected_mover(me, heartbeats, now, stale)
    for machine, hb in heartbeats.items():
        if machine == me:
            continue
        age = now - hb.get("ts", 0)
        if age < stale:
            MOVE_NOTES.pop(machine, None)
            continue
        if not mover or age <= dead:
            continue
        queue_dir = config.repo_dir() / "swarm" / "queue" / machine
        if not list(queue_dir.glob("*/*.md")):
            continue
        key = "machine:" + machine
        if now - CTX["move_at"].get(key, 0) < MOVE_RETRY_SEC:
            continue
        CTX["move_at"][key] = now
        hbs = _with_me(heartbeats, me, workers, now, net)
        targets = _targets(tasks.move_tasks(machine, None, hbs, "pc down"))
        MOVE_NOTES[machine] = ", ".join(targets) or "nobody (no free worker)"


# ---------- dispatch ----------

def _done_ids():
    done = util.load_json(util.state_dir() / "done.json", []) or []
    return {d.get("id") for d in done if isinstance(d, dict)}


def _task_thread(w, path, st):
    result = None
    try:
        from . import tasks
        result = tasks.run_task(w, path, st["holder"])
    except Exception as e:
        util.log("listener", "run_task %s crashed: %s" % (path.stem, traceback.format_exc()))
        result = {"status": "error", "problem": repr(e), "id": path.stem, "title": ""}
    _finish_task(w, st, result or {})


def _finish_task(w, st, result):
    if st.get("shutdown"):
        return
    status = result.get("status")
    task_id = result.get("id") or (st.get("task") or {}).get("id")
    st["task"] = None
    st["holder"] = {}
    if status == "blocked":
        _block(w, st, result.get("problem") or "tokens")
        return
    st["state"] = "idle"
    if status == "error":
        from . import tasks
        st["failed"].add(task_id)
        st["problem"] = "task error: %s" % str(result.get("problem"))[:80]
        tasks.note_failure(task_id, result.get("title") or "", w["name"],
                           ("the swarm could not save the work: %s" % result.get("problem"))[:140], human=True)
    else:
        st["problem"] = None


def _first_runnable(me, name, st, skip):
    for path in _queued(me, name):
        if path.stem not in skip and path.stem not in st["failed"]:
            return path
    return None


def _task_title(path):
    try:
        from . import tasks
        return tasks.parse(path.read_text(encoding="utf-8")).get("title") or ""
    except Exception:
        return ""


def _start_task(w, st, path):
    st["state"] = "busy"
    st["problem"] = None
    st["holder"] = {}
    st["task"] = {"id": path.stem, "title": _task_title(path), "started": util.now()}
    t = threading.Thread(target=_task_thread, args=(w, path, st), daemon=True)
    st["thread"] = t
    t.start()


def _dispatch(me, workers, routing):
    skip = None
    for w in workers:
        st = STATES[w["name"]]
        if st["state"] != "idle" or _is_never(w.get("model"), routing):
            continue
        if not _queued(me, w["name"]):
            continue
        if skip is None:
            skip = _done_ids() | gitops.remote_task_branches()
        path = _first_runnable(me, w["name"], st, skip)
        if path is not None:
            _start_task(w, st, path)


# ---------- alerts and heartbeat ----------

def _stamp(found):
    keys = set()
    for a in found:
        keys.add(a["key"])
        a["since"] = ALERT_SINCE.setdefault(a["key"], a["since"])
    for key in list(ALERT_SINCE):
        if key not in keys:
            del ALERT_SINCE[key]
    return found


def _own_alerts(me, workers, net, settings, now):
    found = []
    if not net:
        found.append(alerts.offline(me))
    elif GIT_ERROR["msg"]:
        found.append(alerts.git_stuck(me, GIT_ERROR["msg"]))
    for w in workers:
        st = STATES[w["name"]]
        if st["state"] != "blocked":
            continue
        resets_at = (st.get("usage") or {}).get("resets_at") or st["blocked_until"]
        a = alerts.for_problem(st["problem"], me, w, resets_at, st.get("target"))
        if a:
            found.append(a)
    from . import tasks
    found += [alerts.task_failed(me, r) for r in tasks.open_failures()]
    low = [b for b in CTX["boss"] if b.get("remaining_pct") is not None
           and b["remaining_pct"] <= settings.get("boss_low_pct", 10)]
    if low:
        worst = min(low, key=lambda b: b["remaining_pct"])
        found.append(alerts.boss_low(me, worst["remaining_pct"], worst.get("window"), worst.get("resets_at")))
    for machine, note in MOVE_NOTES.items():
        ts = LAST_HB.get(machine, {}).get("ts", now)
        found.append(alerts.pc_down(machine, now - ts, note))
    return _stamp(found)


def _worker_entry(w, me):
    st = STATES[w["name"]]
    usage = st.get("usage") or {}
    task = st.get("task")
    return {"name": w["name"], "harness": w.get("harness"), "model": w.get("model"),
            "enabled": w.get("enabled", True), "kinds": w.get("kinds", []),
            "state": st["state"],
            "task": dict(task) if task else None,
            "remaining_pct": usage.get("remaining_pct"), "resets_at": usage.get("resets_at"),
            "usage_label": usage.get("label"), "problem": st.get("problem")}


def _build_hb(me, workers, now, own_alerts, net):
    done = util.load_json(util.state_dir() / "done.json", []) or []
    return {"machine": me, "os": config.machine_cfg(me).get("os"), "ts": now, "version": CTX.get("version"),
            "pid": os.getpid(), "started": START, "net_ok": net,
            "git": util.load_json(util.state_dir() / "git_status.json", {}) or {},
            "workers": [_worker_entry(w, me) for w in workers],
            "alerts": own_alerts, "events": util.recent_events(10), "done": done[-20:],
            "queued": {w["name"]: len(_queued(me, w["name"])) for w in workers}}


def _publish(me, workers, heartbeats, settings, net, now):
    own = _own_alerts(me, workers, net, settings, now)
    hb = _build_hb(me, workers, now, own, net)
    if net:
        ok, msg = gitops.push_heartbeat(me, hb)
        if not ok:
            util.log("listener", "heartbeat push failed: " + msg)
    peers = dict(heartbeats if net else {})
    all_hb = dict(LAST_HB)
    all_hb.update(heartbeats)
    all_hb[me] = hb
    view = {"me": me, "written": now, "hb": all_hb, "alerts": alerts.compute_alerts(hb, peers, now)}
    util.save_json(util.state_dir() / "view.json", view)
    return view


# ---------- main loop ----------

def _sync_step():
    ok, msg = gitops.sync_main()
    GIT_ERROR["msg"] = None if ok else msg
    if not ok:
        util.log("listener", "sync failed: " + msg)


def _loop_once(me):
    global LAST_HB
    now = util.now()
    settings = _step("settings", config.settings, default=config.DEFAULT_ROUTING["settings"])
    routing = _step("routing", config.routing, default=config.DEFAULT_ROUTING)
    workers = _step("workers", lambda: config.workers(me), default=CTX["workers"])
    CTX["workers"] = workers
    _step("states", lambda: _sync_states(workers))
    (util.state_dir() / "listener.alive").touch()
    net = bool(_step("net", heal.net_ok, default=False))
    heartbeats = {}
    if net:
        _step("sync", _sync_step)
        heartbeats = _step("read heartbeats", gitops.read_heartbeats, default=LAST_HB) or {}
        _step("usage", lambda: _refresh_usage(me, workers, settings, now))
    _step("usage blocks", lambda: _apply_usage(workers, settings.get("low_usage_pct", 3), now))
    if net:
        _step("retry", lambda: _retry_blocked(workers, now))
        _step("moves", lambda: _handle_moves(me, workers, heartbeats, now, net))
        _step("dispatch", lambda: _dispatch(me, workers, routing))
        _step("dead machines", lambda: _move_dead_machines(me, workers, heartbeats, settings, now, net))
    view = _step("publish", lambda: _publish(me, workers, heartbeats, settings, net, now))
    if net:
        LAST_HB = heartbeats or LAST_HB
    if view:
        _step("notify", lambda: alerts.maybe_notify(view["alerts"]))
    if CTX["hash"] != code_hash() and _busy_count() == 0:
        util.log("listener", "code changed, exiting for restart")
        return 3
    return None


def _shutdown_workers():
    for name, st in STATES.items():
        if st["state"] != "busy":
            continue
        st["shutdown"] = True
        holder = st["holder"]
        holder["abort"] = True
        proc = holder.get("proc")
        if proc is not None:
            util.kill_pid(proc.pid, tree=True)
        task = st.get("task")
        if task:
            _step("remove worktree", lambda t=task: gitops.remove_worktree(t["id"]))
            util.event("task %s stopped by swarm down" % task["id"])


def _run():
    global _sock
    _sock = util.single_instance("listener")
    if _sock is None:
        return 0
    me = config.machine()
    pid_file = util.state_dir() / "listener.pid"
    pid_file.write_text(str(os.getpid()))
    CTX["hash"] = code_hash()
    CTX["version"] = CTX["hash"]
    util.log("listener", "started pid %d version %s" % (os.getpid(), CTX["hash"]))
    try:
        while not down_flag():
            code = _loop_once(me)
            if code is not None:
                return code
            interval = _step("interval", lambda: config.settings().get("heartbeat_sec", 60), default=60)
            _sleep(interval)
        util.log("listener", "down flag seen, stopping")
        return 0
    finally:
        _step("shutdown", _shutdown_workers)
        try:
            pid_file.unlink()
        except OSError:
            pass


def main():
    sys.exit(_run())
