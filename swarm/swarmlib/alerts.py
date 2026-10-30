"""Human-actionable alerts. Wording follows DESIGN.md exactly."""
from . import config, notify, util


def make(kind, machine, worker, text, action):
    return {"key": "%s:%s:%s" % (kind, machine, worker or "-"), "type": kind, "machine": machine,
            "worker": worker, "text": text, "action": action, "since": util.now()}


def login_command(w):
    """Command the human must run to log this worker in again."""
    if w.get("profile"):
        return "swarm login %s" % w.get("name")
    if w.get("harness") == "claude":
        return "claude auth login"
    provider = w.get("usage_provider") or (w.get("model") or "").split("/")[0]
    return "omp login %s" % provider


def offline(machine):
    return make("offline", machine, None, "%s lost internet" % machine,
                "Check Wi-Fi/cable on %s. swarm resumes by itself." % machine)


def pc_down(machine, age, note=None):
    text = "%s is asleep or off (last heartbeat %s ago)" % (machine, util.fmt_age(age))
    if note:
        text += "; its tasks moved to %s" % note
    return make("pc_down", machine, None, text,
                "Wake %s / check power and internet, then run: swarm up" % machine)


def tokens(machine, w, resets_at, target):
    name = w.get("name")
    text = "%s/%s is out of tokens (resets %s)" % (machine, name, util.fmt_reset(resets_at))
    action = "Nothing to do; its queued tasks moved to %s" % (target or "nobody (no free worker)")
    return make("tokens", machine, name, text, action)


def logged_out(machine, w):
    name = w.get("name")
    return make("logged_out", machine, name, "%s/%s is logged out" % (machine, name),
                "On %s run: %s" % (machine, login_command(w)))


def key_expired(machine, w):
    name = w.get("name")
    return make("key_expired", machine, name, "%s/%s: OpenCode API key expired" % (machine, name),
                "Create a new API key in OpenCode Console, then on %s run: %s"
                % (machine, login_command(w)))


def bad_model(machine, w):
    name = w.get("name")
    return make("bad_model", machine, name,
                "%s/%s: model %s not found" % (machine, name, w.get("model")),
                "Edit swarm/config/machines/%s.json (model name) or run: swarm remove %s" % (machine, name))


def no_agent(machine, w):
    name = w.get("name")
    return make("no_agent", machine, name,
                "%s/%s: agent program not found by the swarm" % (machine, name),
                "On %s, check that '%s' runs in a terminal, then run: swarm fix" % (machine, w.get("harness")))


def task_failed(machine, record):
    name = "%s/%s" % (machine, record.get("worker"))
    text = "Task %s (%s) failed on %s: %s" % (record.get("id"), record.get("title"), name, record.get("what"))
    if record.get("human"):
        action = "Human needed: on %s run: swarm fix" % machine
    else:
        action = "No human needed: the boss will review and requeue it"
    return make("task_failed", machine, record.get("id"), text, action)


def boss_low(machine, pct, window, resets_at):
    return make("boss_low", machine, None,
                "Boss Claude is at %s%% (%s) (resets %s)" % (pct, window, util.fmt_reset(resets_at)),
                "Switch to Vachana's Claude: /logout, /login")


def git_stuck(machine, diagnosis):
    return make("git_stuck", machine, None, "%s cannot sync with GitHub: %s" % (machine, diagnosis),
                "On %s run: swarm fix" % machine)


def listener_down(machine):
    return make("listener_down", machine, None, "swarm listener not running on %s" % machine,
                "On %s run: swarm up" % machine)


def for_problem(problem, machine, w, resets_at=None, target=None):
    """Alert for a blocked worker, or None (offline has its own alert)."""
    if problem == "tokens":
        return tokens(machine, w, resets_at, target)
    if problem == "logged_out":
        return logged_out(machine, w)
    if problem == "key_expired":
        return key_expired(machine, w)
    if problem == "bad_model":
        return bad_model(machine, w)
    if problem == "no_agent":
        return no_agent(machine, w)
    return None


def compute_alerts(own_hb, peers, now):
    """own alerts + fresh peers' alerts + pc_down for stale peers, de-duplicated by key."""
    stale = config.settings().get("peer_stale_sec", 180)
    me = own_hb.get("machine")
    result = list(own_hb.get("alerts", []))
    for machine, hb in (peers or {}).items():
        if machine == me:
            continue
        age = now - hb.get("ts", 0)
        if age < stale:
            result += hb.get("alerts", [])
        else:
            alert = pc_down(machine, age)
            alert["since"] = hb.get("ts", now)
            result.append(alert)
    seen = set()
    unique = []
    for a in result:
        if a["key"] not in seen:
            seen.add(a["key"])
            unique.append(a)
    return unique


def maybe_notify(alerts):
    me = config.machine()
    boss = config.routing().get("boss", {}).get("machine")
    renotify = config.settings().get("renotify_min", 30) * 60
    path = util.state_dir() / "notified.json"
    notified = util.load_json(path, {}) or {}
    now = util.now()
    current = {}
    for a in alerts:
        mine = a.get("machine") == me or me == boss or a.get("type") == "pc_down"
        if not mine:
            continue
        last = notified.get(a["key"])
        if last is None or now - last >= renotify:
            notify.notify("swarm", "%s -> %s" % (a["text"], a["action"]))
            last = now
        current[a["key"]] = last
    util.save_json(path, current)
