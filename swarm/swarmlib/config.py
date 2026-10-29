"""Config files: local.json (per PC) and swarm/config/* (in the repo)."""
import copy
import json
import os
import platform
from pathlib import Path

from . import util

DEFAULT_ROUTING = {
    "boss": {"machine": "vivek-pc",
             "note": "Vivek's Opus in Claude Code. Backup: Vachana's Claude on same PC (/logout, /login)."},
    "routes": {
        "routine": ["openai-codex/gpt-6-luna", "*/claude-haiku-5-5", "*/deepseek-v4.1-flash", "xai-oauth/*"],
        "hard": ["opencode-go/gemini-4-argon", "openai-codex/gpt-6.1-sol", "openai-codex/gpt-6.1-terra",
                 "*/deepseek-v4-pro"],
        "second": ["google-antigravity/*", "*/gemini-4-argon"],
    },
    "never_models": ["*astra*"],
    "settings": {"heartbeat_sec": 60, "peer_stale_sec": 180, "peer_dead_sec": 600, "task_timeout_min": 45,
                 "low_usage_pct": 3, "boss_low_pct": 10, "usage_refresh_sec": 300, "renotify_min": 30},
}


def local():
    return util.load_json(util.home() / "local.json", {}) or {}


def machine():
    name = local().get("machine")
    if not name:
        raise SystemExit("Run: swarm setup")
    return name


def repo_dir():
    path = local().get("repo_dir")
    return Path(path) if path else util.home() / "repo"


def config_dir():
    return repo_dir() / "swarm" / "config"


def routing():
    result = copy.deepcopy(DEFAULT_ROUTING)
    user = util.load_json(config_dir() / "routing.json", {}) or {}
    for key, value in user.items():
        if isinstance(value, dict) and isinstance(result.get(key), dict):
            result[key].update(value)
        else:
            result[key] = value
    return result


def settings():
    result = dict(routing()["settings"])
    # test hook: SWARM_SETTINGS_OVERRIDE='{"peer_dead_sec": 60}'
    # (or the same json in <SWARM_HOME>/settings_override.json, which a scheduled task can see too)
    try:
        extra = json.loads(os.environ.get("SWARM_SETTINGS_OVERRIDE") or "{}")
        if isinstance(extra, dict):
            result.update(extra)
    except ValueError:
        pass
    extra = util.load_json(util.home() / "settings_override.json", {})
    if isinstance(extra, dict):
        result.update(extra)
    return result


def _os_name():
    return {"Windows": "windows", "Linux": "linux", "Darwin": "macos"}.get(platform.system(), "unknown")


def machine_cfg(m=None):
    m = m or machine()
    cfg = util.load_json(config_dir() / "machines" / (m + ".json"), None)
    if not isinstance(cfg, dict):
        cfg = {"machine": m, "os": _os_name(), "workers": []}
    cfg.setdefault("machine", m)
    cfg.setdefault("workers", [])
    return cfg


def workers(m=None):
    return machine_cfg(m)["workers"]


def save_machine_cfg(cfg):
    util.save_json(config_dir() / "machines" / (cfg["machine"] + ".json"), cfg)


def presets():
    return util.load_json(config_dir() / "presets.json", {}) or {}
