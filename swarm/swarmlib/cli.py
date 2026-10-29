import argparse
import sys


def _build_parser():
    p = argparse.ArgumentParser(prog="swarm", description="Run a swarm of AI coding workers across PCs.")
    sub = p.add_subparsers(dest="cmd", metavar="<command>")

    s = sub.add_parser("setup", help="set up this machine")
    s.add_argument("--machine")
    s.add_argument("--workers", help="comma separated preset names")
    s.add_argument("--yes", "-y", action="store_true", help="accept defaults")
    s.add_argument("--no-autostart", action="store_true")
    s.add_argument("--no-test", action="store_true")

    sub.add_parser("up", help="start swarm on this machine")
    sub.add_parser("down", help="stop swarm on this machine")
    sub.add_parser("status", help="one-shot dashboard")
    sub.add_parser("watch", help="live dashboard")
    sub.add_parser("fix", help="repair git state and restart")

    s = sub.add_parser("test", help="self test")
    s.add_argument("--no-agents", action="store_true")

    s = sub.add_parser("add", help="add a borrowed account as a worker")
    s.add_argument("--harness", choices=["omp", "claude"], default="omp")
    s.add_argument("--name")
    s.add_argument("--model")
    s.add_argument("--provider")

    s = sub.add_parser("remove", help="remove a worker")
    s.add_argument("name")
    s.add_argument("--force", action="store_true")

    s = sub.add_parser("login", help="log a worker in again")
    s.add_argument("worker")

    s = sub.add_parser("queue", help="queue a task")
    s.add_argument("target", help="machine/worker or routine|hard|second")
    s.add_argument("file", help="task file or - for stdin")
    s.add_argument("--title")
    s.add_argument("--kind")
    s.add_argument("--files")

    sub.add_parser("tasks", help="list tasks")

    s = sub.add_parser("review", help="show a finished task")
    s.add_argument("id")
    s.add_argument("--full", action="store_true")

    s = sub.add_parser("merge", help="merge a finished task")
    s.add_argument("id")

    s = sub.add_parser("reject", help="reject a finished task")
    s.add_argument("id")
    s.add_argument("--requeue", metavar="TARGET")
    s.add_argument("--note")

    s = sub.add_parser("autostart", help="manage start at login")
    s.add_argument("action", choices=["on", "off", "status"])

    sub.add_parser("listener", help="internal: run the listener loop")
    sub.add_parser("watchdog", help="internal: run the watchdog loop")

    s = sub.add_parser("inject", help="test helpers")
    s.add_argument("what", choices=["lock", "tokens", "clear"])
    s.add_argument("rest", nargs="*", help="tokens: <worker> [pct]")
    return p


def _autostart(args):
    from . import autostart
    if args.action == "on":
        if autostart.status() in ("missing", "unsupported"):
            autostart.install()
        autostart.enable()
    elif args.action == "off":
        autostart.disable()
    print("autostart: %s" % autostart.status())
    return 0


def _run_loop(module_name):
    module = __import__("swarmlib." + module_name, fromlist=["main"])
    rc = module.main()
    return rc if isinstance(rc, int) else 0


def _handlers():
    def lazy(module, func):
        def call(args):
            mod = __import__("swarmlib." + module, fromlist=[func])
            return getattr(mod, func)(args)
        return call

    def lazy0(module, func):
        def call(args):
            mod = __import__("swarmlib." + module, fromlist=[func])
            return getattr(mod, func)()
        return call

    return {
        "setup": lazy("setup_cmd", "setup"),
        "up": lazy("lifecycle", "up"),
        "down": lazy("lifecycle", "down"),
        "fix": lazy("lifecycle", "fix"),
        "status": lazy0("dashboard", "status_cmd"),
        "watch": lazy0("dashboard", "watch_cmd"),
        "test": lazy("selftest", "test_cmd"),
        "inject": lazy("selftest", "inject_cmd"),
        "add": lazy("accounts", "add"),
        "remove": lazy("accounts", "remove"),
        "login": lazy("accounts", "login"),
        "queue": lazy("boss", "queue"),
        "tasks": lazy("boss", "tasks_cmd"),
        "review": lazy("boss", "review"),
        "merge": lazy("boss", "merge"),
        "reject": lazy("boss", "reject"),
        "autostart": _autostart,
        "listener": lambda args: _run_loop("listener"),
        "watchdog": lambda args: _run_loop("watchdog"),
    }


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    parser = _build_parser()
    args = parser.parse_args(argv)
    if not args.cmd:
        parser.print_help()
        return 0
    try:
        rc = _handlers()[args.cmd](args)
    except KeyboardInterrupt:
        return 130
    return rc if isinstance(rc, int) else 0
