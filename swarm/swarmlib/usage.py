import json

from . import config, harness, util


def _cache_path():
    return util.state_dir() / "usage_cache.json"


def _fetch_reports(worker):
    omp = util.which("omp")
    if not omp:
        return None
    res = util.run([omp, "usage", "--json"], env=harness.env_for(worker), timeout=30)
    if res.code != 0:
        return None
    try:
        data = json.loads(res.out)
    except ValueError:
        return None
    reports = data.get("reports") if isinstance(data, dict) else data
    return reports if isinstance(reports, list) else None


def _reports_for(worker):
    key = worker.get("profile") or "default"
    cache = util.load_json(_cache_path(), {})
    entry = cache.get(key) or {}
    ttl = config.settings().get("usage_refresh_sec", 300)
    if entry.get("reports") is not None and util.now() - entry.get("ts", 0) < ttl:
        return entry["reports"]
    reports = _fetch_reports(worker)
    if reports is None:
        return entry.get("reports")
    cache[key] = {"ts": util.now(), "reports": reports}
    util.save_json(_cache_path(), cache)
    return reports


def _limits(reports, provider, match=""):
    found = []
    for rep in reports or []:
        if rep.get("provider") != provider:
            continue
        for lim in rep.get("limits") or []:
            label = lim.get("label") or ""
            if match and match.lower() not in label.lower():
                continue
            remaining = (lim.get("amount") or {}).get("remaining")
            if remaining is None:
                continue
            window = lim.get("window") or {}
            reset = window.get("resetsAt")
            found.append({"remaining_pct": int(round(remaining)),
                          "resets_at": reset / 1000 if reset else None,
                          "label": label, "window": window.get("id") or label})
    return found


def _lowest(limits):
    return min(limits, key=lambda x: x["remaining_pct"]) if limits else None


def _statusline(path):
    data = util.load_json(path, {})
    rates = data.get("rate_limits") or {}
    out = []
    for key, name in (("five_hour", "5h"), ("seven_day", "7d")):
        part = rates.get(key) or {}
        if part.get("used_percentage") is None:
            continue
        out.append({"window": name, "label": name,
                    "remaining_pct": int(round(100 - part["used_percentage"])),
                    "resets_at": part.get("resets_at")})
    return out


def _answer(limits):
    best = _lowest(limits)
    if not best:
        return {"remaining_pct": None, "resets_at": None, "label": "unknown"}
    return {"remaining_pct": best["remaining_pct"], "resets_at": best["resets_at"],
            "label": best["label"]}


def _claude_usage(worker):
    if worker.get("profile"):
        return _answer(_statusline(harness.profile_dir(worker) / "claude_statusline.json"))
    limits = _limits(_reports_for(worker), "anthropic")
    if not limits:
        limits = _statusline(util.state_dir() / "claude_statusline.json")
    return _answer(limits)


def usage_for(worker):
    data = util.load_json(util.state_dir() / "fake_usage.json", {})
    if data.get(worker["name"]) is not None:
        return {"remaining_pct": int(data[worker["name"]]), "resets_at": None,
                "label": "injected"}
    kind = worker.get("harness")
    if kind == "fake":
        return {"remaining_pct": 100, "resets_at": None, "label": "fake"}
    if kind == "claude":
        return _claude_usage(worker)
    limits = _limits(_reports_for(worker), harness.provider_of(worker),
                     worker.get("usage_match") or "")
    return _answer(limits)


def boss_usage():
    default = {"name": "boss", "harness": "omp", "model": "anthropic/x", "profile": None}
    limits = [x for x in _limits(_reports_for(default), "anthropic")
              if x["window"] in ("5h", "7d")]
    if not limits:
        limits = _statusline(util.state_dir() / "claude_statusline.json")
    return [{"window": x["window"], "remaining_pct": x["remaining_pct"],
             "resets_at": x["resets_at"]} for x in limits]
