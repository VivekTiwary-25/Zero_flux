"""Optional Claude Code statusLine: shows remaining usage and saves it for swarm.

Enable (opt-in) in the boss's Claude settings.json:
  "statusLine": {"type": "command", "command": "python <repo>/swarm/tools/statusline.py"}
"""
import json
import os
import sys
import time
from pathlib import Path


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        print("swarm: no data")
        return
    limits = data.get("rate_limits") or {}
    five = (limits.get("five_hour") or {}).get("used_percentage")
    week = (limits.get("seven_day") or {}).get("used_percentage")
    home = Path(os.environ.get("SWARM_HOME") or Path.home() / ".swarm")
    out = home / "state" / "claude_statusline.json"
    if os.environ.get("CLAUDE_CONFIG_DIR"):
        out = Path(os.environ["CLAUDE_CONFIG_DIR"]).parent / "claude_statusline.json"
    try:
        out.parent.mkdir(parents=True, exist_ok=True)
        tmp = out.with_suffix(".tmp")
        tmp.write_text(json.dumps({"ts": time.time(), "rate_limits": limits}), encoding="utf-8")
        os.replace(tmp, out)
    except OSError:
        pass
    parts = []
    if five is not None:
        parts.append(f"5h {100 - float(five):.0f}% left")
    if week is not None:
        parts.append(f"7d {100 - float(week):.0f}% left")
    print(" | ".join(parts) or "swarm")


if __name__ == "__main__":
    main()
