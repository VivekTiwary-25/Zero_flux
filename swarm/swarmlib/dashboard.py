"""Terminal dashboard: reads view.json and prints it."""
import os
import sys
import time

from . import util

RED, GREEN, YELLOW, BOLD, DIM, RESET = "\x1b[31m", "\x1b[32m", "\x1b[33m", "\x1b[1m", "\x1b[2m", "\x1b[0m"
STALE_VIEW_SEC = 180
STALE_HB_SEC = 180


def _paint(text, code, color):
    return code + text + RESET if color else text


def _cut(text, width):
    text = str(text if text is not None else "-")
    return text if len(text) <= width else text[:max(1, width - 1)] + "~"


def _row(cells, widths, color_codes, color):
    parts = []
    for cell, width, code in zip(cells, widths, color_codes):
        padded = _cut(cell, width).ljust(width)
        parts.append(_paint(padded, code, color) if code else padded)
    return " ".join(parts).rstrip()


def _header(cells, widths, color):
    return _row(cells, widths, [BOLD] * len(cells), color)


def _age(ts, now):
    return util.fmt_age(now - ts) if ts else "-"


def _review_line(view, color):
    waiting = max([hb.get("waiting_review") or 0 for hb in (view.get("hb") or {}).values()] or [0])
    if not waiting:
        return []
    return [_paint("%d finished task(s) waiting for the boss to review (swarm tasks)" % waiting, YELLOW, color)]


def _alerts_block(view, color):
    found = view.get("alerts") or []
    if not found:
        return [_paint("No alerts", GREEN, color)] + _review_line(view, color)
    lines = [_paint("ALERTS (%d)" % len(found), RED + BOLD, color)]
    for a in found:
        lines.append(_paint("! %s → %s" % (a.get("text"), a.get("action")), RED, color))
    return lines + _review_line(view, color)


def _machines_block(view, now, color):
    widths = [16, 9, 10, 5, 12, 12, 9]
    lines = [_paint("MACHINES", BOLD, color),
             _header(["machine", "os", "heartbeat", "net", "last pull ok", "last push ok", "version"],
                     widths, color)]
    for machine in sorted(view.get("hb") or {}):
        hb = view["hb"][machine]
        git = hb.get("git") or {}
        age = now - hb.get("ts", 0)
        fresh = age < STALE_HB_SEC
        net = "ok" if hb.get("net_ok") else "DOWN"
        codes = [None, None, GREEN if fresh else RED, GREEN if net == "ok" else RED, None, None, None]
        cells = [machine, hb.get("os") or "-", util.fmt_age(age), net, _age(git.get("last_pull_ok"), now),
                 _age(git.get("last_push_ok"), now), hb.get("version") or "-"]
        lines.append(_row(cells, widths, codes, color))
    return lines


STATE_COLORS = {"idle": GREEN, "busy": YELLOW, "blocked": RED, "disabled": DIM}


def _remaining(w):
    pct = w.get("remaining_pct")
    if pct is None:
        return "?"
    text = "%s%%" % pct
    if w.get("resets_at"):
        text += " " + util.fmt_reset(w["resets_at"])
    return text


def _task_text(w):
    task = w.get("task")
    if not task:
        return "-"
    return "%s %s" % (task.get("id"), task.get("title") or "")


def _workers_block(view, color):
    widths = [20, 24, 8, 24, 24, 12]
    lines = [_paint("WORKERS", BOLD, color),
             _header(["machine/worker", "model", "state", "task", "remaining / resets", "problem"],
                     widths, color)]
    for machine in sorted(view.get("hb") or {}):
        for w in view["hb"][machine].get("workers") or []:
            state = w.get("state") or "-"
            cells = ["%s/%s" % (machine, w.get("name")), w.get("model") or "-", state, _task_text(w),
                     _remaining(w), w.get("problem") or "-"]
            codes = [None, None, STATE_COLORS.get(state), None, None, RED if w.get("problem") else None]
            lines.append(_row(cells, widths, codes, color))
    return lines


def _recent_events(view, limit=10):
    seen = {}
    for machine, hb in (view.get("hb") or {}).items():
        for e in hb.get("events") or []:
            key = (e.get("ts"), e.get("machine"), e.get("text"))
            seen[key] = e
    rows = sorted(seen.values(), key=lambda e: e.get("ts") or 0, reverse=True)
    return rows[:limit]


def _events_block(view, color):
    lines = [_paint("RECENT EVENTS", BOLD, color)]
    rows = _recent_events(view)
    if not rows:
        lines.append("  (none)")
    for e in rows:
        line = "  %-12s %-12s %s" % (util.fmt_clock(e.get("ts") or 0), e.get("machine") or "-", e.get("text"))
        lines.append(_cut(line, 118))
    return lines


def _recent_done(view, limit=8):
    rows = []
    for machine, hb in (view.get("hb") or {}).items():
        for d in hb.get("done") or []:
            rows.append((d.get("ts") or 0, machine, d))
    rows.sort(key=lambda r: r[0], reverse=True)
    return rows[:limit]


def _done_block(view, color):
    lines = [_paint("RECENT DONE", BOLD, color)]
    rows = _recent_done(view)
    if not rows:
        lines.append("  (none)")
    for ts, machine, d in rows:
        ok = d.get("ok", True)
        mark = _paint("ok  ", GREEN, color) if ok else _paint("FAIL", RED, color)
        line = "  %s %-20s %-30s %s" % (mark, d.get("id"), "%s/%s" % (machine, d.get("worker")),
                                        util.fmt_clock(ts))
        lines.append(line)
    return lines


def render(view, now, color):
    me = view.get("me") or "?"
    written = view.get("written") or 0
    clock = time.strftime("%H:%M:%S", time.localtime(now))
    head = "swarm @ %s — refreshed %s (data written %s ago)" % (me, clock, util.fmt_age(now - written))
    blocks = [[_paint(head, BOLD, color)], _alerts_block(view, color), _machines_block(view, now, color),
              _workers_block(view, color), _events_block(view, color), _done_block(view, color)]
    return "\n\n".join("\n".join(b) for b in blocks)


# ---------- commands ----------

def _load_view():
    return util.load_json(util.state_dir() / "view.json", None) or {}


def _banner(view, now, color):
    problems = []
    running = util.instance_running("listener")
    if not running or (util.state_dir() / "down").exists():
        problems.append("Listener not running on this PC → run: swarm up")
    elif view and now - (view.get("written") or 0) > STALE_VIEW_SEC:
        problems.append("Data is older than 3 min → run: swarm fix")
    return [_paint(p, RED + BOLD, color) for p in problems]


def _utf8_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def status_cmd():
    _utf8_stdout()
    now = util.now()
    view = _load_view()
    color = sys.stdout.isatty()
    if color and util.is_windows():
        os.system("")
    lines = _banner(view, now, color)
    if view:
        lines.append(render(view, now, color))
    else:
        lines.append("No data yet (listener has not written view.json).")
    print("\n".join(lines))
    return 1 if view.get("alerts") else 0


def watch_cmd():
    _utf8_stdout()
    if util.is_windows():
        os.system("")
    try:
        while True:
            now = util.now()
            view = _load_view()
            lines = _banner(view, now, True)
            lines.append(render(view, now, True) if view else "No data yet (listener has not written view.json).")
            sys.stdout.write("\x1b[2J\x1b[H" + "\n".join(lines) + "\n")
            sys.stdout.flush()
            time.sleep(3)
    except KeyboardInterrupt:
        return 0
