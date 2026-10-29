"""Watchdog: keeps the listener running unless the down flag is set."""
import sys
import time

from . import util

LOOP_SEC = 15
STALE_ALIVE_SEC = 300
SPAWN_GRACE_SEC = 120


def _paths():
    state = util.state_dir()
    return state / "down", state / "listener.alive", state / "listener.pid"


def _spawn_listener():
    cmd = [sys.executable, str(util.code_dir() / "swarm.py"), "listener"]
    pid = util.spawn_detached(cmd, util.state_dir() / "listener.log")
    util.log("watchdog", "spawned listener pid %s" % pid)
    return pid


def _listener_pid():
    try:
        return int((util.state_dir() / "listener.pid").read_text().strip())
    except Exception:
        return None


def _alive_age(alive, pidfile):
    for path in (alive, pidfile):
        try:
            return time.time() - path.stat().st_mtime
        except OSError:
            continue
    return 0


def _check(last_spawn):
    down, alive, pidfile = _paths()
    if down.exists():
        return last_spawn
    if not util.instance_running("listener"):
        _spawn_listener()
        util.event("watchdog restarted listener")
        return time.time()
    if time.time() - last_spawn < SPAWN_GRACE_SEC:
        return last_spawn
    if _alive_age(alive, pidfile) > STALE_ALIVE_SEC:
        pid = _listener_pid()
        util.log("watchdog", "listener stuck (alive file old), killing pid %s" % pid)
        if pid:
            util.kill_pid(pid, tree=True)
        time.sleep(2)
        _spawn_listener()
        util.event("watchdog restarted stuck listener")
        return time.time()
    return last_spawn


def main():
    sock = util.single_instance("watchdog")
    if sock is None:
        return 0
    (util.state_dir() / "watchdog.pid").write_text(str(__import__("os").getpid()))
    util.log("watchdog", "started")
    last_spawn = 0
    while True:
        try:
            last_spawn = _check(last_spawn)
        except Exception as e:
            util.log("watchdog", "error: %r" % e)
        time.sleep(LOOP_SEC)
