import os
import re
import subprocess
import sys
import tempfile

from . import heal, util

FAKE_SCRIPT = (
    "import os, sys\n"
    "from pathlib import Path\n"
    "fail = os.environ.get('SWARM_FAKE_FAIL')\n"
    "if fail:\n"
    "    print(fail, file=sys.stderr)\n"
    "    sys.exit(1)\n"
    "tid = os.environ.get('SWARM_TASK_ID', 'unknown')\n"
    "rep = Path('swarm/reports')\n"
    "rep.mkdir(parents=True, exist_ok=True)\n"
    "(rep / (tid + '.md')).write_text('The fake worker says hello (fake harness, no real change).\\n')\n"
    "print('OK')\n"
)

QUICK_PROMPT = "Reply with exactly the word OK and nothing else."
TASK_PROMPT = "Read SWARM_TASK.md in the current folder and do the task it describes."

PATTERNS = [
    ("key_expired", r"API key has expired"),
    ("bad_model", r"Model .* not found"),
    ("logged_out", r"not logged in|no api key|no credentials|please (run )?/?login"
                   r"|authentication_error|invalid api key|\b401\b|unauthorized"
                   r"|OAuth token (has )?expired"),
    ("tokens", r"rate.?limit|usage limit|quota|\b429\b|too many requests"
               r"|limit reached|out of credits|insufficient credits"),
]


def provider_of(worker):
    return worker.get("usage_provider") or worker.get("model", "").split("/")[0]


def profile_dir(worker):
    return util.home() / "profiles" / worker["profile"]


def env_for(worker):
    env = dict(os.environ)
    if worker.get("profile"):
        base = profile_dir(worker)
        if worker.get("harness") == "omp":
            env["PI_CODING_AGENT_DIR"] = str(base / "omp")
        elif worker.get("harness") == "claude":
            env["CLAUDE_CONFIG_DIR"] = str(base / "claude")
    return env


def login_cmd(worker):
    if worker.get("harness") == "claude":
        return [util.which("claude") or "claude", "auth", "login"]
    return [util.which("omp") or "omp", "login", provider_of(worker)]


def login_hint(worker):
    if worker.get("profile"):
        return "swarm login " + worker["name"]
    if worker.get("harness") == "claude":
        return "claude auth login"
    return "omp login " + provider_of(worker)


def agent_cmd(worker, prompt_text, cwd, timeout_s=2700):
    kind = worker.get("harness")
    if kind == "omp":
        minutes = max(1, int(timeout_s // 60))
        return [util.which("omp") or "omp", "-p", "--auto-approve", "--no-title",
                "--model", worker["model"], "--max-time", "%dm" % minutes,
                "--cwd", str(cwd), prompt_text]
    if kind == "claude":
        cmd = [util.which("claude") or "claude", "-p", "--dangerously-skip-permissions"]
        if worker.get("model"):
            cmd += ["--model", worker["model"]]
        return cmd + [prompt_text]
    if kind == "fake":
        return [sys.executable, "-c", FAKE_SCRIPT]
    raise ValueError("unknown harness: %s" % kind)


def run_agent(worker, cwd, prompt_text, timeout_s, env_extra=None, holder=None):
    env = env_for(worker)
    env.update(env_extra or {})
    try:
        cmd = agent_cmd(worker, prompt_text, cwd, timeout_s)
    except ValueError as e:
        return util.Result(127, "", str(e))
    kw = {}
    if util.is_windows():
        kw["creationflags"] = subprocess.CREATE_NO_WINDOW
    else:
        kw["start_new_session"] = True
    try:
        proc = subprocess.Popen(
            cmd, cwd=str(cwd), env=env, stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", errors="replace", **kw)
    except OSError as e:
        return util.Result(127, "", "cannot start agent: %s" % e)
    if holder is not None:
        holder["proc"] = proc
    try:
        out, err = proc.communicate(timeout=timeout_s)
    except subprocess.TimeoutExpired:
        util.kill_pid(proc.pid, tree=True)
        try:
            out, err = proc.communicate(timeout=10)
        except Exception:
            out, err = "", ""
        return util.Result(124, out or "", (err or "") + "\nagent timed out after %ds" % timeout_s)
    return util.Result(proc.returncode, out or "", err or "")


def classify(result):
    if result.code == 0:
        return None
    if result.code == 127:
        return "no_agent"
    text = (result.out or "") + "\n" + (result.err or "")
    for name, pattern in PATTERNS:
        if re.search(pattern, text, re.I):
            return name
    if not heal.net_ok():
        return "offline"
    return None


def quick_test(worker):
    with tempfile.TemporaryDirectory(prefix="swarm-qt-") as tmp:
        res = run_agent(worker, tmp, QUICK_PROMPT, 180)
    if res.code == 0 and "OK" in res.out:
        return True, "agent replied OK"
    problem = classify(res) or "failed"
    tail = (res.err or res.out).strip().splitlines()[-1:] or [""]
    return False, "%s (exit %s): %s" % (problem, res.code, tail[0][:200])
