import sys
import time

from . import autostart, config, gitops, harness, tasks, usage, util


class Report:
    def __init__(self):
        self.failed = 0

    def ok(self, name, detail=""):
        print("PASS %s — %s" % (name, detail))

    def fail(self, name, detail, fix):
        self.failed += 1
        print("FAIL %s — %s (fix: %s)" % (name, detail, fix))

    def info(self, text):
        print("INFO " + text)

    def check(self, name, good, detail, fix):
        (self.ok if good else lambda n, d: self.fail(n, d, fix))(name, detail)
        return good


def _basics(rep):
    v = sys.version_info
    rep.check("python", v >= (3, 10), "%d.%d.%d" % v[:3], "install Python 3.10 or newer")
    git = util.run(["git", "--version"])
    rep.check("git", git.ok, git.out.strip() or git.err.strip(), "install git")


def _config(rep):
    try:
        machine = config.machine()
    except SystemExit:
        rep.fail("config", "this machine is not set up", "run: swarm setup")
        return None
    repo = config.repo_dir()
    rep.check("config", (repo / ".git").exists(), "machine %s, repo %s" % (machine, repo),
              "run: swarm setup")
    return machine


def _tools(rep, workers):
    for w in workers:
        if w.get("harness") == "fake":
            rep.ok("tool " + w["name"], "fake harness")
            continue
        exe = util.which(w["harness"])
        rep.check("tool " + w["name"], bool(exe), exe or "%s not found" % w["harness"],
                  "install %s and make sure it is on PATH" % w["harness"])


def _network(rep, machine):
    from . import heal
    if not rep.check("net", heal.net_ok(), "github.com reachable", "check Wi-Fi/cable, then run: swarm fix"):
        return False
    res = gitops.fetch_all()
    if not rep.check("fetch", res.ok, "fetched origin", "run: swarm fix"):
        return False
    stamp = int(time.time())
    ok, msg = gitops.push_heartbeat(machine, {"machine": machine, "ts": stamp, "selftest": True})
    if ok:
        gitops.fetch_all()
        back = gitops.read_heartbeats().get(machine, {})
        ok = back.get("ts") == stamp
        msg = "pushed and read back" if ok else "read back differs"
    rep.check("heartbeat", ok, msg, "run: swarm fix")
    return True


def _usage(rep, workers):
    for w in workers:
        try:
            u = usage.usage_for(w)
        except Exception as e:
            rep.fail("usage " + w["name"], repr(e), "run: omp usage --json")
            continue
        pct = u["remaining_pct"]
        rep.ok("usage " + w["name"], "unknown" if pct is None else "%d%% left (%s)" % (pct, u["label"]))


def _agents(rep, workers):
    for w in workers:
        if not w.get("enabled", True) or w.get("harness") == "fake":
            continue
        ok, msg = harness.quick_test(w)
        rep.check("agent " + w["name"], ok, msg, harness.login_hint(w))


def _fake_task(rep, machine):
    tid = "selftest-%s-%s" % (machine, time.strftime("%y%m%d%H%M%S"))
    worker = {"name": "selftest", "harness": "fake", "model": "fake/fake"}
    path = util.state_dir() / (tid + ".md")
    task = {"id": tid, "title": "selftest", "kind": "routine", "body": "Write fake_output.txt.\n"}
    path.write_text(tasks.render(task), encoding="utf-8", newline="\n")
    try:
        res = tasks.run_task(worker, path, {})
        if res["status"] != "done":
            rep.fail("fake task", "%s: %s" % (res["status"], res["problem"]), "run: swarm fix")
            return
        remote = gitops.git("ls-remote", "origin", "refs/heads/task/" + tid)
        rep.check("fake task", "task/" + tid in remote.out, "branch task/%s reached GitHub" % tid,
                  "check push rights to the repo; run: swarm fix")
    finally:
        gitops.delete_remote_branch("task/" + tid)
        tasks.forget_done(tid)
        path.unlink()


def _services(rep):
    rep.info("listener: %s" % ("running" if util.instance_running("listener") else "not running (swarm up)"))
    rep.info("watchdog: %s" % ("running" if util.instance_running("watchdog") else "not running (swarm up)"))
    try:
        rep.info("autostart: %s" % autostart.status())
    except Exception as e:
        rep.info("autostart: unknown (%r)" % e)


def run_tests(agents=True):
    rep = Report()
    _basics(rep)
    machine = _config(rep)
    if machine is None:
        return 1
    workers = config.workers(machine)
    rep.info("%d worker(s) configured" % len(workers))
    _tools(rep, workers)
    if _network(rep, machine):
        _usage(rep, workers)
        if agents:
            _agents(rep, workers)
        _fake_task(rep, machine)
    _services(rep)
    print("\n%s" % ("All good." if not rep.failed else "%d check(s) failed." % rep.failed))
    return 1 if rep.failed else 0


def test_cmd(args):
    return run_tests(agents=not args.no_agents)


def inject_cmd(args):
    what = args.what
    rest = args.rest
    fake = util.state_dir() / "fake_usage.json"
    if what == "lock":
        lock = config.repo_dir() / ".git" / "index.lock"
        lock.write_text("stale")
        print("Created %s" % lock)
    elif what == "tokens":
        if not rest:
            print("usage: swarm inject tokens <worker> [pct]")
            return 1
        data = util.load_json(fake, {})
        data[rest[0]] = int(rest[1]) if len(rest) > 1 else 0
        util.save_json(fake, data)
        print("%s now reports %d%% left" % (rest[0], data[rest[0]]))
    elif what == "clear":
        if fake.exists():
            fake.unlink()
        print("Removed fake usage.")
    return 0
