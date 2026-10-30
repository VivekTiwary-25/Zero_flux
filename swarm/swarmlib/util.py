"""Shared helpers: paths, json, subprocess, logging, formatting, processes."""
import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import zlib
from collections import namedtuple
from pathlib import Path

CREATE_NO_WINDOW = 0x08000000
DETACHED_PROCESS = 0x00000008
CREATE_NEW_PROCESS_GROUP = 0x00000200


class Result(namedtuple("Result", "code out err")):
    __slots__ = ()

    @property
    def ok(self):
        return self.code == 0


def is_windows():
    return os.name == "nt"


def home():
    env = os.environ.get("SWARM_HOME")
    return Path(env) if env else Path.home() / ".swarm"


def state_dir():
    p = home() / "state"
    p.mkdir(parents=True, exist_ok=True)
    return p


def code_dir():
    """Directory containing swarm.py (the parent of the swarmlib package)."""
    return Path(__file__).resolve().parent.parent


def now():
    return time.time()


def python_exe():
    """python used for background processes (pythonw on Windows if present)."""
    if is_windows():
        pyw = Path(sys.executable).with_name("pythonw.exe")
        if pyw.exists():
            return str(pyw)
    return sys.executable


# ---------- json ----------

def load_json(path, default=None):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def save_json(path, obj):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp%d" % os.getpid())
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
    os.replace(tmp, path)


# ---------- subprocess ----------

def _text(value):
    if value is None:
        return ""
    if isinstance(value, bytes):
        value = value.decode("utf-8", errors="replace")
    return value.replace("\r\n", "\n")


def run(cmd, cwd=None, env=None, timeout=None, input=None):
    # bytes mode: text mode would translate LF to CRLF on Windows stdin (breaks git mktree)
    kwargs = dict(cwd=str(cwd) if cwd else None, env=env, capture_output=True, timeout=timeout)
    if input is None:
        kwargs["stdin"] = subprocess.DEVNULL
    else:
        kwargs["input"] = input.encode("utf-8") if isinstance(input, str) else input
    if is_windows():
        kwargs["creationflags"] = CREATE_NO_WINDOW
    try:
        p = subprocess.run([str(c) for c in cmd], **kwargs)
        return Result(p.returncode, _text(p.stdout), _text(p.stderr))
    except FileNotFoundError as e:
        return Result(127, "", str(e))
    except subprocess.TimeoutExpired as e:
        return Result(124, _text(e.stdout), _text(e.stderr) + "\ntimeout")
    except Exception as e:
        return Result(1, "", repr(e))


_login_path_done = False
_login_path_lock = threading.Lock()


def _import_login_path():
    """A systemd service has no shell PATH, so tools like omp (and the runtime it needs) can't be found."""
    global _login_path_done
    # Workers start together. The others must wait here, or they look for omp before PATH is ready.
    with _login_path_lock:
        if _login_path_done:
            return
        marker = "__SWARM_PATH__"
        home = Path.home()
        extra = [str(home / d) for d in (".local/bin", ".bun/bin", ".npm-global/bin", ".cargo/bin", ".omp/bin")]
        extra.append("/usr/local/bin")
        try:
            res = subprocess.run(["bash", "-lic", "printf '%s%s\\n' " + marker + " \"$PATH\""],
                                 stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=15)
            for line in res.stdout.splitlines():
                if line.startswith(marker):
                    extra = line[len(marker):].split(":") + extra
        except (OSError, subprocess.SubprocessError):
            pass
        current = os.environ.get("PATH", "").split(":")
        os.environ["PATH"] = ":".join(dict.fromkeys(current + [d for d in extra if d]))
        _login_path_done = True


def which(name):
    found = shutil.which(name)
    if found:
        return found
    if is_windows():
        for ext in (".exe", ".cmd"):
            found = shutil.which(name + ext)
            if found:
                return found
        return None
    _import_login_path()
    return shutil.which(name)


# ---------- logging and events ----------

def log(name, msg):
    try:
        path = state_dir() / (name + ".log")
        if path.exists() and path.stat().st_size > 1_000_000:
            os.replace(path, str(path) + ".1")
        stamp = time.strftime("%Y-%m-%d %H:%M:%S")
        with open(path, "a", encoding="utf-8") as f:
            f.write("%s %s\n" % (stamp, msg))
    except Exception:
        pass


def _events_path():
    return state_dir() / "events.jsonl"


def _my_machine():
    try:
        return load_json(home() / "local.json", {}).get("machine") or "-"
    except Exception:
        return "-"


def event(text, machine=None):
    try:
        path = _events_path()
        row = {"ts": now(), "machine": machine or _my_machine(), "text": text}
        if path.exists() and path.stat().st_size > 1_000_000:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()[-500:]
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        with open(path, "a", encoding="utf-8") as f:
            f.write(json.dumps(row) + "\n")
    except Exception:
        pass


def recent_events(n=10):
    """Last n events, oldest first."""
    rows = []
    try:
        lines = _events_path().read_text(encoding="utf-8", errors="replace").splitlines()
    except Exception:
        return rows
    for line in lines[-n:]:
        try:
            rows.append(json.loads(line))
        except Exception:
            pass
    return rows


# ---------- formatting ----------

def fmt_age(seconds):
    s = max(0, int(seconds))
    if s < 60:
        return "%ds" % s
    if s < 3600:
        return "%dm" % (s // 60)
    return "%dh%02dm" % (s // 3600, (s % 3600) // 60)


def fmt_clock(epoch):
    t = time.localtime(epoch)
    if time.strftime("%Y%m%d", t) == time.strftime("%Y%m%d"):
        return time.strftime("%H:%M", t)
    return time.strftime("%b %d %H:%M", t)


def fmt_reset(epoch):
    if not epoch:
        return "-"
    left = epoch - now()
    if left <= 0:
        return "%s (now)" % fmt_clock(epoch)
    return "%s (in %s)" % (fmt_clock(epoch), fmt_age(left))


# ---------- processes ----------

def spawn_detached(cmd, log_path):
    cmd = [str(c) for c in cmd]
    kwargs = {}
    if is_windows():
        if cmd[0] == sys.executable:
            cmd[0] = python_exe()
        kwargs["creationflags"] = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW
    else:
        kwargs["start_new_session"] = True
    Path(log_path).parent.mkdir(parents=True, exist_ok=True)
    with open(log_path, "ab") as out:
        p = subprocess.Popen(cmd, stdin=subprocess.DEVNULL, stdout=out,
                             stderr=subprocess.STDOUT, close_fds=True, **kwargs)
    return p.pid


def pid_alive(pid):
    try:
        pid = int(pid)
    except Exception:
        return False
    if pid <= 0:
        return False
    if is_windows():
        import ctypes
        kernel = ctypes.windll.kernel32
        handle = kernel.OpenProcess(0x1000, False, pid)
        if not handle:
            return False
        code = ctypes.c_ulong()
        kernel.GetExitCodeProcess(handle, ctypes.byref(code))
        kernel.CloseHandle(handle)
        return code.value == 259
    try:
        os.kill(pid, 0)
        return True
    except PermissionError:
        return True
    except OSError:
        return False


def kill_pid(pid, tree=True):
    try:
        pid = int(pid)
        if is_windows():
            cmd = ["taskkill", "/PID", str(pid), "/F"]
            if tree:
                cmd.insert(3, "/T")
            run(cmd, timeout=30)
            return
        os.kill(pid, signal.SIGTERM)
        for _ in range(50):
            if not pid_alive(pid):
                return
            time.sleep(0.1)
        os.kill(pid, signal.SIGKILL)
    except Exception:
        pass


# ---------- single instance ----------

def _port(name):
    return 47000 + (zlib.crc32((str(home()) + name).encode("utf-8")) % 2000)


def _accept_forever(sock):
    while True:
        try:
            conn, _ = sock.accept()
            conn.close()
        except Exception:
            return


def single_instance(name):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        if is_windows() and hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        sock.bind(("127.0.0.1", _port(name)))
        sock.listen(5)
    except OSError:
        sock.close()
        return None
    threading.Thread(target=_accept_forever, args=(sock,), daemon=True).start()
    return sock


def instance_running(name):
    try:
        with socket.create_connection(("127.0.0.1", _port(name)), timeout=1):
            return True
    except OSError:
        return False
