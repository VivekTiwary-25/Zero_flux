import sys
import time

from . import autostart, config, gitops, heal, util


def _down_flag():
    return util.state_dir() / "down"


def _read_pid(name):
    try:
        return int((util.state_dir() / (name + ".pid")).read_text().strip())
    except (OSError, ValueError):
        return None


def _wait(predicate, seconds=20):
    end = time.time() + seconds
    while time.time() < end:
        if predicate():
            return True
        time.sleep(0.5)
    return predicate()


def start_watchdog():
    if util.instance_running("watchdog"):
        return "watchdog already running"
    if autostart.status() == "enabled" and autostart.start():
        return "watchdog started by autostart"
    cmd = [sys.executable, str(util.code_dir() / "swarm.py"), "watchdog"]
    pid = util.spawn_detached(cmd, util.state_dir() / "watchdog.out")
    return "watchdog started (pid %s)" % pid


def up(args=None):
    flag = _down_flag()
    if flag.exists():
        flag.unlink()
    status = autostart.status()
    if status == "missing":
        autostart.install()
    elif status == "disabled":
        autostart.enable()
    print(start_watchdog())
    if _wait(lambda: util.instance_running("listener")):
        print("Listener is running.")
    else:
        print("Listener did not start within 20 s. See %s" % (util.state_dir() / "listener.log"))
    from . import dashboard
    dashboard.status_cmd()
    return 0 if util.instance_running("listener") else 1


def down(args=None):
    _down_flag().write_text("down")
    if _wait(lambda: not util.instance_running("listener")):
        print("Listener stopped.")
    else:
        print("Listener did not stop in 20 s; killing it.")
    for name in ("watchdog", "listener"):
        pid = _read_pid(name)
        if pid and util.pid_alive(pid):
            util.kill_pid(pid, tree=True)
            print("Stopped %s (pid %s)." % (name, pid))
    print("swarm is down on this machine. Start again with: swarm up")
    return 0


def fix(args=None):
    problems = 0
    if not heal.net_ok():
        print("No internet connection. Check Wi-Fi/cable; swarm resumes by itself.")
        return 1
    print("Internet: ok")
    repo = config.repo_dir()
    for action in heal.prepare_repo(repo):
        print("Repaired: %s" % action)
    ok, msg = gitops.sync_main()
    if not ok:
        print("Sync failed (%s); running repair ladder..." % msg)
        ok, msg = heal.ladder(msg, lambda: gitops.sync_main()[0])
    print("Sync with GitHub: %s" % ("ok" if ok else "FAILED - " + str(msg)))
    problems += 0 if ok else 1
    if util.instance_running("listener"):
        print("Listener is running (it pushes heartbeats itself).")
    else:
        machine = config.machine()
        hok, hmsg = gitops.push_heartbeat(machine, {"machine": machine, "ts": util.now(), "workers": []})
        print("Heartbeat: %s" % ("pushed" if hok else hmsg))
        if not _down_flag().exists():
            print(start_watchdog())
        else:
            print("swarm is down on purpose (run: swarm up)")
    return 1 if problems else 0
