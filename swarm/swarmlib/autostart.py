"""Start the watchdog at login: Windows scheduled task or Linux systemd user service."""
import os
import sys
from pathlib import Path

from . import util

UNIT = "swarm-watchdog"


def _task_name():
    name = UNIT
    if os.environ.get("SWARM_HOME"):
        machine = util.load_json(util.home() / "local.json", {}).get("machine")
        if machine:
            name += "-" + machine
    return name


def _watchdog_command():
    script = str(util.code_dir() / "swarm.py")
    return '"%s" "%s" watchdog' % (util.python_exe(), script)


def _unit_path():
    return Path.home() / ".config" / "systemd" / "user" / (UNIT + ".service")


def _has_systemd():
    return bool(util.which("systemctl"))


def _systemctl(*args):
    return util.run(["systemctl", "--user"] + list(args), timeout=60)


def _install_windows_ps():
    ps = ("$u=[Environment]::UserName;"
          "$a=New-ScheduledTaskAction -Execute '%s' -Argument '\"%s\" watchdog';"
          "$t=New-ScheduledTaskTrigger -AtLogOn -User $u;"
          "$p=New-ScheduledTaskPrincipal -UserId $u -LogonType Interactive -RunLevel Limited;"
          "$s=New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries "
          "-ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable;"
          "Register-ScheduledTask -TaskName '%s' -Action $a -Trigger $t -Principal $p -Settings $s -Force "
          "-ErrorAction Stop | Out-Null" % (util.python_exe(), util.code_dir() / "swarm.py", _task_name()))
    return util.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", ps], timeout=90).ok


def install():
    if util.is_windows():
        r = util.run(["schtasks", "/Create", "/TN", _task_name(), "/TR", _watchdog_command(),
                      "/SC", "ONLOGON", "/IT", "/RL", "LIMITED", "/F"], timeout=60)
        if r.ok:
            return True
        # schtasks ONLOGON needs admin; a logon trigger for the current user does not.
        return _install_windows_ps()
    if not _has_systemd():
        return False
    text = ("[Unit]\nDescription=swarm watchdog\n\n[Service]\n"
            "ExecStart=%s %s watchdog\nRestart=always\nRestartSec=10\n\n"
            "[Install]\nWantedBy=default.target\n" % (sys.executable, util.code_dir() / "swarm.py"))
    path = _unit_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    _systemctl("daemon-reload")
    _systemctl("import-environment", "DISPLAY", "WAYLAND_DISPLAY", "DBUS_SESSION_BUS_ADDRESS")
    return _systemctl("enable", UNIT).ok


def uninstall():
    if util.is_windows():
        return util.run(["schtasks", "/Delete", "/TN", _task_name(), "/F"], timeout=60).ok
    if not _has_systemd():
        return False
    _systemctl("disable", "--now", UNIT)
    try:
        _unit_path().unlink()
    except OSError:
        pass
    _systemctl("daemon-reload")
    return True


def enable():
    if util.is_windows():
        return util.run(["schtasks", "/Change", "/TN", _task_name(), "/ENABLE"], timeout=60).ok
    if not _has_systemd():
        return False
    _systemctl("import-environment", "DISPLAY", "WAYLAND_DISPLAY", "DBUS_SESSION_BUS_ADDRESS")
    return _systemctl("enable", UNIT).ok


def disable():
    if util.is_windows():
        return util.run(["schtasks", "/Change", "/TN", _task_name(), "/DISABLE"], timeout=60).ok
    if not _has_systemd():
        return False
    return _systemctl("disable", UNIT).ok


def start():
    if util.is_windows():
        return util.run(["schtasks", "/Run", "/TN", _task_name()], timeout=60).ok
    if not _has_systemd():
        return False
    return _systemctl("start", UNIT).ok


def status():
    if util.is_windows():
        r = util.run(["schtasks", "/Query", "/TN", _task_name(), "/FO", "CSV", "/NH"], timeout=60)
        if not r.ok:
            return "missing"
        return "disabled" if "disabled" in r.out.lower() else "enabled"
    if not _has_systemd():
        return "unsupported"
    if not _unit_path().exists():
        return "missing"
    out = _systemctl("is-enabled", UNIT).out.strip()
    return "enabled" if out == "enabled" else "disabled"
