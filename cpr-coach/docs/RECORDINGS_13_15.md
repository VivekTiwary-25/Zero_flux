# What recordings 13-15 contain

Measured from `tools/testdata` on cpr-coach `4e6cfd8c20f542f3057465b58990675574545b14`, read with `git archive` into `../ref-t261031-0418-93d1`.

Duration is the last `t_ms` minus the first. A frame is one CSV row. A row has a pose when `shoulder_x` is not empty, the rule `docs/LABELS_CHECK.md` uses. Latency is the `latency_ms` column. Frame gap is the difference between consecutive `t_ms`, not the separate `frame_gap_ms` column. Labelled segments are the rows of `tools/testdata/labels.csv` for that file, in seconds from the first row.

The header comment of `labels.csv` dates all three to 31 Oct 2026, about 02:55-03:00, CPH2467, side-on, frames saved every 0.5 s.

## Command

From `cpr-coach/` in the extracted copy. Stdlib Python only.

```python
import csv, os, statistics

td = "tools/testdata"
lines = [l for l in open(td + "/labels.csv", encoding="utf-8-sig")
         if l.strip() and not l.lstrip().startswith("#")]
labels = list(csv.DictReader(lines))

for n in ("13", "14", "15"):
    name = "real-cph2467-%s.csv" % n
    rows = list(csv.DictReader(open(os.path.join(td, name), encoding="utf-8-sig")))
    t = [int(r["t_ms"]) for r in rows]
    pose = [bool(r["shoulder_x"].strip()) for r in rows]
    latency = [int(r["latency_ms"]) for r in rows]
    gaps = [b - a for a, b in zip(t, t[1:])]
    segments = ["%s-%s s %s (%s)" % (l["start_s"], l["end_s"], l["label"], l["source"])
                for l in labels if l["file"] == name]
    print("### real-cph2467-%s.csv\n" % n)
    print("| Measure | Value |")
    print("|---|---|")
    print("| Duration | %.2f s |" % ((t[-1] - t[0]) / 1000))
    print("| Frames (rows) | %d |" % len(rows))
    print("| Rows with a pose | %d (%.1f%%) |" % (sum(pose), 100 * sum(pose) / len(rows)))
    print("| Median latency_ms | %g |" % statistics.median(latency))
    print("| Median frame gap | %g ms |" % statistics.median(gaps))
    print("| Labelled segments | %s |" % ("; ".join(segments) if segments else "none"))
    print()
```

Its output is the three tables below.

### real-cph2467-13.csv

| Measure | Value |
|---|---|
| Duration | 44.45 s |
| Frames (rows) | 898 |
| Rows with a pose | 859 (95.7%) |
| Median latency_ms | 58 |
| Median frame gap | 46 ms |
| Labelled segments | none |

### real-cph2467-14.csv

| Measure | Value |
|---|---|
| Duration | 63.19 s |
| Frames (rows) | 1401 |
| Rows with a pose | 729 (52.0%) |
| Median latency_ms | 49 |
| Median frame gap | 44 ms |
| Labelled segments | 3-24 s straight (pictures) |

### real-cph2467-15.csv

| Measure | Value |
|---|---|
| Duration | 71.45 s |
| Frames (rows) | 1597 |
| Rows with a pose | 1597 (100.0%) |
| Median latency_ms | 56 |
| Median frame gap | 43.5 ms |
| Labelled segments | 3.5-32 s straight (pictures); 32.3-34 s unknown (none); 34.5-37.3 s straight (pictures); 37.4-38 s unknown (none); 38.2-43.4 s bent (pictures); 44-53.4 s straight (pictures); 54-61.5 s bent (pictures); 65-69.5 s leaning (team_unconfirmed) |

## Context

- 13 is a different rescuer (checked shirt).
- 14 has a sit-up rest at about 24.7-33.5 s and the phone dropped at 33.5 s; 15 is the labelled straight/bent/lean run.
