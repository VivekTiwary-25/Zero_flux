"""Exercise CPR Coach screens through adb without interacting with the dialer."""
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.zeroflux.cprcoach"
CRASH_PATTERN = re.compile(r"FATAL|Caused by|Fatal signal|SIGSEGV|SIGABRT", re.IGNORECASE)
BOUNDS_PATTERN = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def default_adb() -> str:
    candidates = [
        Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe",
        Path.home() / "Android/Sdk/platform-tools/adb",
    ]
    return str(next((candidate for candidate in candidates if candidate.is_file()), Path(shutil.which("adb") or "adb")))


class PhoneTest:
    def __init__(self, adb_path: str, serial: str, out: Path):
        self.adb_path = adb_path
        self.serial = serial
        self.out = out
        self.failures = 0

    def adb(self, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [self.adb_path, "-s", self.serial, *args],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=60,
            check=False,
        )
        if check and result.returncode:
            raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
        return result

    def screenshot(self, name: str) -> None:
        result = subprocess.run(
            [self.adb_path, "-s", self.serial, "exec-out", "screencap", "-p"],
            capture_output=True,
            timeout=60,
            check=False,
        )
        if result.returncode:
            raise RuntimeError(f"screenshot failed: {result.stderr.decode(errors='replace').strip()}")
        (self.out / f"{name}.png").write_bytes(result.stdout)

    def nodes(self) -> list[dict[str, object]]:
        self.adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = self.adb("shell", "cat", "/sdcard/ui.xml").stdout
        try:
            root = ET.fromstring(xml[xml.index("<"):])
        except (ET.ParseError, ValueError):
            return []
        found = []
        for node in root.iter("node"):
            match = BOUNDS_PATTERN.fullmatch(node.get("bounds", ""))
            if match:
                x1, y1, x2, y2 = map(int, match.groups())
                found.append({
                    "text": node.get("text", ""),
                    "desc": node.get("content-desc", ""),
                    "click": node.get("clickable") == "true",
                    "box": (x1, y1, x2, y2),
                })
        return found

    def labels(self) -> list[str]:
        return [str(node["text"] or node["desc"]) for node in self.nodes() if node["text"] or node["desc"]]

    def top_package(self) -> str:
        output = self.adb("shell", "dumpsys", "activity", "activities").stdout
        match = re.search(r"topResumedActivity=\S+ \S+ (\S+?)/", output)
        return match.group(1) if match else "?"

    def tap(self, label: str, *, long_ms: int | None = None) -> bool:
        for node in self.nodes():
            if label == node["text"] or label == node["desc"]:
                x1, y1, x2, y2 = node["box"]  # type: ignore[misc]
                x, y = (x1 + x2) // 2, (y1 + y2) // 2
                if long_ms is None:
                    self.adb("shell", "input", "tap", str(x), str(y))
                else:
                    self.adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), str(long_ms))
                time.sleep(1.2)
                return True
        return False

    def alive(self) -> bool:
        return bool(self.adb("shell", "pidof", PACKAGE, check=False).stdout.strip())

    def record(self, name: str, *, expected: str | None = None, app_expected: bool = True) -> bool:
        time.sleep(1.2)
        self.screenshot(name)
        labels = self.labels()
        package = self.top_package()
        alive = self.alive()
        passed = alive and ((package == PACKAGE) if app_expected else True)
        if expected is not None:
            passed = passed and any(expected in label for label in labels)
        status = "PASS" if passed else "FAIL"
        print(f"{status} [{name}] alive={alive} top={package} :: {' | '.join(labels)[:300]}")
        if not passed:
            self.failures += 1
        return passed

    def require_tap(self, label: str, step: str) -> bool:
        if self.tap(label):
            print(f"PASS [{step}] tapped {label}")
            return True
        print(f"FAIL [{step}] control not found: {label}")
        self.failures += 1
        return False

    def crashes(self) -> list[str]:
        output = self.adb("logcat", "-d", "-b", "crash", check=False).stdout
        return [line for line in output.splitlines() if CRASH_PATTERN.search(line)]

    def run(self) -> int:
        self.out.mkdir(parents=True, exist_ok=True)
        print("WARNING: dialer screenshots may show personal contacts; keep --out outside the repo and never commit screenshots.")
        print("Dialer safety: the script only reads the dialer screen, then relaunches this app; it never presses anything in the dialer or places a call.")
        try:
            self.adb("logcat", "-c")
            self.adb("shell", "am", "force-stop", PACKAGE)
            self.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            self.adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
            time.sleep(3)
            self.record("01_home_en", expected="Emergency")

            self.require_tap("English", "02_picker_open")
            self.record("02_picker", expected="हिन्दी")
            self.require_tap("हिन्दी", "03_choose_hindi")
            self.record("03_home_hi", expected="इमरजेंसी")
            self.require_tap("हिन्दी", "03_picker_reopen")
            self.require_tap("ಕನ್ನಡ", "04_choose_kannada")
            self.record("04_home_kn", expected="ತುರ್ತು")
            self.require_tap("ಕನ್ನಡ", "04_picker_reopen")
            self.require_tap("English", "05_choose_english")
            self.record("05_home_en_again", expected="Emergency")

            self.require_tap("Emergency", "06_open_emergency")
            self.record("06_emergency", expected="Call 112")
            self.require_tap("Call 112", "07_open_dialer")
            time.sleep(1.5)
            self.screenshot("07_dialer")
            package = self.top_package()
            screen = " ".join(self.labels())
            passed = package != PACKAGE and "112" in screen.replace(" ", "")
            print(f"{'PASS' if passed else 'FAIL'} [07_dialer] top={package} shows 112: {'112' in screen.replace(' ', '')} :: {screen[:200]}")
            if not passed:
                self.failures += 1
            # Nothing is pressed in the dialer: bring the app back to the front by launching it.
            self.adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
            time.sleep(1.5)
            self.record("08_back_from_dialer", expected="Start compressions")
            self.require_tap("Start compressions", "09_start_compressions")
            self.record("09_compressing")
            time.sleep(8)
            self.record("10_compressing_later")
            service = "MetronomeService" in self.adb("shell", "dumpsys", "activity", "services", PACKAGE).stdout
            print(f"{'PASS' if service else 'FAIL'} [10_metronome_service] running={service}")
            if not service:
                self.failures += 1
            # A long press is the stop gesture; it is only issued on the app's own screen.
            if not self.tap("Hold to stop", long_ms=1400):
                self.failures += 1
                print("FAIL [11_stop_compressions] control not found: Hold to stop")
            self.record("11_after_stop", expected="Emergency")
            service = "MetronomeService" in self.adb("shell", "dumpsys", "activity", "services", PACKAGE).stdout
            print(f"{'PASS' if not service else 'FAIL'} [11_metronome_stopped] running={service}")
            if service:
                self.failures += 1

            self.require_tap("Practise", "12_open_practise")
            if "Skip" not in self.labels():
                self.tap("?", long_ms=None)
            if "Skip" in self.labels():
                self.record("12_walkthrough", expected="Skip")
                self.require_tap("Skip", "13_skip_walkthrough")
            else:
                print("FAIL [12_walkthrough] walkthrough Skip was not found")
                self.failures += 1
            self.record("13_camera", expected="Point the camera at the rescuer")
        except (OSError, subprocess.SubprocessError, RuntimeError) as error:
            print(f"FAIL [tool] {error}")
            self.failures += 1
        crash_lines = self.crashes()
        if crash_lines:
            print("FAIL [logcat crash]")
            for line in crash_lines:
                print(f"  {line}")
            self.failures += 1
        else:
            print("PASS [logcat crash] no matching crash lines")
        return 1 if self.failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Exercise CPR Coach UI through adb without interacting with the dialer.")
    parser.add_argument("--serial", required=True, help="adb device serial")
    parser.add_argument("--out", required=True, type=Path, help="screenshot directory (keep outside the repository)")
    parser.add_argument("--adb", default=default_adb(), help="adb executable (default: Android SDK adb or PATH)")
    args = parser.parse_args()
    return PhoneTest(args.adb, args.serial, args.out).run()


if __name__ == "__main__":
    sys.exit(main())
