import fnmatch
import shutil
import subprocess
import sys

from . import config, gitops, harness, util


def _find(name):
    for w in config.workers():
        if w["name"] == name:
            return w
    return None


def _ask(prompt):
    if sys.stdin.isatty():
        return input(prompt).strip()
    return ""


def _run_interactive(cmd, env):
    try:
        return subprocess.call(cmd, env=env)
    except OSError as e:
        print("Cannot run %s: %s" % (cmd[0], e))
        return 127


def _save_worker(worker):
    machine = config.machine()

    def apply_fn(repo):
        cfg = config.machine_cfg(machine)
        cfg["workers"] = [w for w in cfg.get("workers", []) if w["name"] != worker["name"]]
        cfg["workers"].append(worker)
        config.save_machine_cfg(cfg)

    return gitops.commit_to_main(apply_fn, "swarm: add worker %s/%s" % (machine, worker["name"]))


def _drop_worker(name):
    machine = config.machine()

    def apply_fn(repo):
        cfg = config.machine_cfg(machine)
        cfg["workers"] = [w for w in cfg.get("workers", []) if w["name"] != name]
        config.save_machine_cfg(cfg)

    return gitops.commit_to_main(apply_fn, "swarm: remove worker %s/%s" % (machine, name))


def add(args):
    harness_name = args.harness or "omp"
    model = args.model or _ask("Model (e.g. openai-codex/gpt-6-luna): ")
    if not model:
        print("A model is required: swarm add --model <provider/model>")
        return 1
    never = config.routing().get("never_models", [])
    if any(fnmatch.fnmatch(model, pat) for pat in never):
        print("Model %s is on the never_models list; refusing." % model)
        return 1
    name = args.name or _ask("Worker name: ") or "%s-%d" % (harness_name, len(config.workers()) + 1)
    if _find(name) and not _find(name).get("profile"):
        print("Worker %s already exists without a separate login; pick another name." % name)
        return 1
    provider = args.provider or model.split("/")[0]
    worker = {"name": name, "harness": harness_name, "model": model, "profile": name,
              "usage_provider": provider, "usage_match": "", "kinds": [], "enabled": True}
    for sub in ("omp", "claude"):
        (harness.profile_dir(worker) / sub).mkdir(parents=True, exist_ok=True)
    print("Logging in to %s (profile %s). Follow the prompts." % (provider, name))
    code = _run_interactive(harness.login_cmd(worker), harness.env_for(worker))
    if code != 0:
        print("Login exited with code %s. Run 'swarm add' again with --name %s to retry." % (code, name))
        return 1
    print("Testing the worker (this makes one tiny request)...")
    ok, msg = harness.quick_test(worker)
    if not ok:
        print("Test failed: %s\nNot registered. Fix the login and run: swarm add --name %s --model %s" % (msg, name, model))
        return 1
    ok, msg = _save_worker(worker)
    if not ok:
        print("Could not save the worker: %s" % msg)
        return 1
    util.event("added worker %s/%s (%s %s)" % (config.machine(), name, harness_name, model))
    print("Added worker %s/%s." % (config.machine(), name))
    return 0


def remove(args):
    worker = _find(args.name)
    if not worker:
        print("No worker named %s on this machine." % args.name)
        return 1
    if not worker.get("profile") and not args.force:
        print("%s uses your normal login, not a borrowed one. To only unregister it: swarm remove %s --force"
              % (args.name, args.name))
        return 1
    if worker.get("profile"):
        if worker.get("harness") == "claude":
            _run_interactive([util.which("claude") or "claude", "auth", "logout"], harness.env_for(worker))
        shutil.rmtree(harness.profile_dir(worker), ignore_errors=True)
    ok, msg = _drop_worker(args.name)
    if not ok:
        print("Could not unregister the worker: %s" % msg)
        return 1
    util.event("removed worker %s/%s (%s %s)" % (config.machine(), args.name,
                                                  worker.get("harness"), worker.get("model")))
    print("Removed worker %s." % args.name)
    return 0


def login(args):
    worker = _find(args.worker)
    if not worker:
        print("No worker named %s on this machine." % args.worker)
        return 1
    if worker.get("profile"):
        for sub in ("omp", "claude"):
            (harness.profile_dir(worker) / sub).mkdir(parents=True, exist_ok=True)
    return _run_interactive(harness.login_cmd(worker), harness.env_for(worker))
