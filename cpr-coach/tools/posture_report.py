"""Posture judge report: share of good/issue/null per recording, agreement with labels, fire/clear times.

Run the unit tests first (they replay the judge and write app/build/posture_replay/*.csv):
    ./gradlew.bat testDebugUnitTest      (in cpr-coach)
    python tools/posture_report.py [--replay DIR] [--labels FILE]

Agreement is counted on labelled segments only. 'unknown' rows are ignored. Rows with
source=team_unconfirmed are reported separately because they are provisional.
A frame agrees when: straight -> good is true; bent -> ELBOWS_BENT; leaning -> SHOULDERS_NOT_OVER_HANDS.
Null frames count as "silent" (neither agree nor disagree) and are reported on their own.
"""
import argparse
import csv
import os
import sys

ISSUE_FOR = {"bent": "ELBOWS_BENT", "leaning": "SHOULDERS_NOT_OVER_HANDS"}


def read_rows(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        return [{"t": float(r["t_s"]), "good": r["good"], "issue": r["issue"]} for r in csv.DictReader(f)]


def read_labels(path):
    if not os.path.exists(path):
        return []
    with open(path, newline="", encoding="utf-8-sig") as f:
        lines = [ln for ln in f if ln.strip() and not ln.lstrip().startswith("#")]
    return [
        {"file": r["file"], "start": float(r["start_s"]), "end": float(r["end_s"]),
         "label": r["label"], "source": r["source"]}
        for r in csv.DictReader(lines)
    ]


def agrees(label, row):
    if label == "straight":
        return row["good"] == "true"
    return row["issue"] == ISSUE_FOR.get(label)


def fmt(v):
    return "never" if v is None else "%.1fs" % v


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--replay", default=os.path.join(here, "..", "app", "build", "posture_replay"))
    ap.add_argument("--labels", default=os.path.join(here, "testdata", "labels.csv"))
    a = ap.parse_args()
    if not os.path.isdir(a.replay):
        sys.exit("No replay output in %s: run ./gradlew.bat testDebugUnitTest first." % a.replay)
    files = sorted(f for f in os.listdir(a.replay) if f.endswith(".csv"))
    data = {f: read_rows(os.path.join(a.replay, f)) for f in files}

    print("%-28s %6s %6s %6s   bent  lean  notSideOn" % ("file", "good", "issue", "null"))
    for f, rows in data.items():
        n = len(rows)
        good = sum(r["good"] == "true" for r in rows)
        iss = sum(r["issue"] != "" for r in rows)

        def share(k):
            return 100 * sum(r["issue"] == k for r in rows) / n

        print("%-28s %5.0f%% %5.0f%% %5.0f%%  %4.0f%% %4.0f%% %4.0f%%" % (
            f, 100 * good / n, 100 * iss / n, 100 * (n - good - iss) / n,
            share("ELBOWS_BENT"), share("SHOULDERS_NOT_OVER_HANDS"), share("NOT_SIDE_ON")))

    labels = [l for l in read_labels(a.labels) if l["label"] != "unknown" and l["file"] in data]
    if not labels:
        print("\nNo labelled segments: no agreement can be reported (expected until a labelled capture exists).")
        return
    for provisional, title in ((False, "HUMAN-LABELLED"), (True, "TEAM-UNCONFIRMED (provisional)")):
        group = [l for l in labels if (l["source"] == "team_unconfirmed") == provisional]
        if not group:
            continue
        print("\n%s segments" % title)
        tot = agree = silent = 0
        for l in group:
            rows = [r for r in data[l["file"]] if l["start"] <= r["t"] <= l["end"]]
            ag = sum(agrees(l["label"], r) for r in rows)
            nul = sum(r["good"] == "" and r["issue"] == "" for r in rows)
            tot += len(rows)
            agree += ag
            silent += nul
            first = next((r["t"] - l["start"] for r in rows if agrees(l["label"], r)), None)
            after = [r for r in data[l["file"]] if r["t"] >= l["end"]]
            clr = None
            if l["label"] != "straight":
                clr = next((r["t"] - l["end"] for r in after if r["issue"] == ""), None)
            print("  %s %.0f-%.0fs %-8s agree %3.0f%%  silent %3.0f%%  first agree after %s  clear after end %s" % (
                l["file"], l["start"], l["end"], l["label"], 100 * ag / max(1, len(rows)),
                100 * nul / max(1, len(rows)), fmt(first), fmt(clr)))
        print("  total frames %d: agree %.0f%%, silent %.0f%%, other %.0f%%" % (
            tot, 100 * agree / max(1, tot), 100 * silent / max(1, tot), 100 * (tot - agree - silent) / max(1, tot)))


if __name__ == "__main__":
    main()
