"""Beep and popup on this PC. Never raises."""
import base64
import os
import sys
from xml.sax.saxutils import escape

from . import util

APP_ID = r"{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\WindowsPowerShell\v1.0\powershell.exe"


def _beep_windows():
    import winsound
    winsound.Beep(880, 250)
    winsound.Beep(880, 250)


def _beep_linux():
    sys.stdout.write("\a")
    sys.stdout.flush()
    sound = "/usr/share/sounds/freedesktop/stereo/bell.oga"
    if util.which("paplay") and os.path.exists(sound):
        util.run(["paplay", sound], timeout=10)
    elif util.which("canberra-gtk-play"):
        util.run(["canberra-gtk-play", "-i", "bell"], timeout=10)


def _ps_quote(text):
    return "'" + text.replace("'", "''") + "'"


def _toast_script(title, text):
    xml = ('<toast><visual><binding template="ToastGeneric"><text>%s</text><text>%s</text>'
           '</binding></visual></toast>' % (escape(title), escape(text)))
    return "\n".join([
        "$ErrorActionPreference = 'SilentlyContinue'",
        "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, "
        "ContentType=WindowsRuntime] | Out-Null",
        "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, "
        "ContentType=WindowsRuntime] | Out-Null",
        "$xml = New-Object Windows.Data.Xml.Dom.XmlDocument",
        "$xml.LoadXml(%s)" % _ps_quote(xml),
        "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml)",
        "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier(%s).Show($toast)"
        % _ps_quote(APP_ID),
    ])


def _popup_windows(title, text):
    encoded = base64.b64encode(_toast_script(title, text).encode("utf-16-le")).decode("ascii")
    util.run(["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", encoded],
             timeout=30)


def _popup_linux(title, text):
    if util.which("notify-send"):
        util.run(["notify-send", "-u", "critical", "-a", "swarm", title, text], timeout=10)


def notify(title, text):
    if os.environ.get("SWARM_NO_POPUP") == "1":
        return
    try:
        if util.is_windows():
            _beep_windows()
        else:
            _beep_linux()
    except Exception:
        pass
    try:
        if util.is_windows():
            _popup_windows(title, text)
        else:
            _popup_linux(title, text)
    except Exception:
        pass
