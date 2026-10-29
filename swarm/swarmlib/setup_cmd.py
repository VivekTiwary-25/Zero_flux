import json
import os
import platform
import socket
import stat
import sys
from pathlib import Path

from . import autostart, config, gitops, selftest, util

FALLBACK_URL = "https://github.com/VivekTiwary-25/Zero_flux.git"


def _tty():
    return sys.stdin.isatty()


def _ask(prompt, default):
    if not _tty():
        return default
    answer = input("%s [%s]: " % (prompt, default)).strip()
    return answer or default


def _repo_url():
    res = util.run(["git", "remote", "get-url", "origin"], cwd=str(util.code_dir()))
    return res.out.strip() if res.ok and res.out.strip() else FALLBACK_URL


def _clone(url):
    target = util.home() / "repo"
    if (target / ".git").exists():
        print("Using existing clone %s" % target)
        return True
    target.parent.mkdir(parents=True, exist_ok=True)
    print("Cloning %s into %s ..." % (url, target))
    res = util.run(["git", "clone", url, str(target)])
    if not res.ok:
        print("Clone failed: %s" % (res.err or res.out).strip())
    return res.ok


def _check_remote():
    repo = str(config.repo_dir())
    res = util.run(["git", "ls-remote", "origin", "HEAD"], cwd=repo)
    if res.ok or util.is_windows() or not util.which("gh"):
        return res.ok
    print("git cannot reach GitHub; running: gh auth setup-git")
    util.run(["gh", "auth", "setup-git"])
    return util.run(["git", "ls-remote", "origin", "HEAD"], cwd=repo).ok


def _set_identity(machine):
    repo = str(config.repo_dir())
    for key, fallback in (("user.name", "swarm-" + machine),
                          ("user.email", "swarm-%s@users.noreply.github.com" % machine)):
        glob = util.run(["git", "config", "--global", key]).out.strip()
        util.run(["git", "config", key, glob or fallback], cwd=repo)


def _load_presets():
    presets = config.presets()
    if presets:
        return presets
    return util.load_json(util.code_dir() / "config" / "presets.json", {})


def _seen_providers():
    omp = util.which("omp")
    if not omp:
        return set()
    res = util.run([omp, "usage", "--json"], timeout=30)
    try:
        data = json.loads(res.out)
    except ValueError:
        return set()
    return {r.get("provider") for r in data.get("reports", [])}


def _choose_workers(args, presets):
    if args.workers:
        names = [n.strip() for n in args.workers.split(",") if n.strip()]
        unknown = [n for n in names if n not in presets]
        if unknown:
            print("Unknown worker preset(s): %s" % ", ".join(unknown))
            return None
        return names
    seen = _seen_providers()
    names = list(presets)
    ticked = [n for n in names if presets[n].get("harness") != "fake"
              and presets[n].get("usage_provider") in seen]
    if args.yes or not _tty():
        return ticked
    print("\nWorkers available (* = you are logged in to its provider):")
    for i, n in enumerate(names, 1):
        p = presets[n]
        print("  %2d. [%s] %-12s %s" % (i, "*" if n in ticked else " ", n, p["model"]))
    default = ",".join(str(names.index(n) + 1) for n in ticked)
    answer = input("Numbers to use, comma separated [%s]: " % default).strip() or default
    chosen = []
    for part in answer.split(","):
        if part.strip().isdigit() and 1 <= int(part) <= len(names):
            chosen.append(names[int(part) - 1])
    return chosen


def _save_machine(machine, presets, names):
    def apply_fn(repo):
        cfg = config.machine_cfg(machine)
        keep = [w for w in cfg.get("workers", []) if w.get("profile")]
        cfg["machine"] = machine
        cfg["os"] = platform.system().lower()
        cfg["workers"] = [dict(presets[n]) for n in names] + keep
        config.save_machine_cfg(cfg)

    return gitops.commit_to_main(apply_fn, "swarm: setup %s" % machine)


def _on_path(directory):
    parts = os.environ.get("PATH", "").split(os.pathsep)
    return str(directory).lower() in [p.rstrip("\\/").lower() for p in parts]


def _install_shim():
    if os.environ.get("SWARM_HOME"):
        print("Test instance: skipping PATH shim.")
        return
    bindir = Path.home() / ".local" / "bin"
    bindir.mkdir(parents=True, exist_ok=True)
    script = util.home() / "repo" / "swarm" / "swarm.py"
    if util.is_windows():
        shim = bindir / "swarm.cmd"
        shim.write_bytes(('@"%s" "%s" %%*\r\n' % (sys.executable, script)).encode("utf-8"))
    else:
        shim = bindir / "swarm"
        shim.write_text('#!/bin/sh\nexec python3 "%s" "$@"\n' % script, newline="\n")
        shim.chmod(shim.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    print("Wrote %s" % shim)
    if not _on_path(bindir):
        if util.is_windows():
            print('Add it to PATH (then open a new terminal):\n  setx PATH "%%PATH%%;%s"' % bindir)
        else:
            print('Add it to PATH (then open a new terminal):\n  echo \'export PATH="$HOME/.local/bin:$PATH"\' >> ~/.bashrc')


def setup(args):
    default_machine = socket.gethostname().lower()
    machine = args.machine or _ask("Machine name", default_machine)
    url = _repo_url()
    if not _clone(url):
        return 1
    util.save_json(util.home() / "local.json", {
        "machine": machine, "repo_dir": str(util.home() / "repo"), "repo_url": url})
    _set_identity(machine)
    if not _check_remote():
        print("Cannot reach GitHub with this clone. Fix git credentials, then run: swarm setup")
        return 1
    presets = _load_presets()
    names = _choose_workers(args, presets)
    if names is None:
        return 1
    ok, msg = gitops.sync_main()
    if not ok:
        print("Could not sync main: %s" % msg)
        return 1
    ok, msg = _save_machine(machine, presets, names)
    if not ok:
        print("Could not save machine config: %s" % msg)
        return 1
    hb_ok, hb_msg = gitops.push_heartbeat(machine, {"machine": machine, "ts": util.now(), "workers": []})
    print("Heartbeat: %s" % ("pushed" if hb_ok else hb_msg))
    _install_shim()
    if not args.no_autostart:
        autostart.install()
        print("Autostart installed (%s)." % autostart.status())
    print("\nMachine %s set up with workers: %s" % (machine, ", ".join(names) or "(none)"))
    if args.no_test:
        return 0
    print("")
    rc = selftest.run_tests(agents=True)
    print("\nNext: swarm up")
    return rc
