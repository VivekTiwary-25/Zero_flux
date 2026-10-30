# Labels check

Machine check of `tools/testdata/labels.csv` against the recordings it names.
Source commit `f4b59a1fc6a6048812d5bcf6663aac70c55e870a` on branch `cpr-coach`, read via `git archive` into a folder outside the checkout.

Times are seconds from the first row of the recording.
`PostureReplayTest` writes the replay's `t_s` as `(t_ms - t_ms of first row) / 1000`, and `tools/posture_report.py` matches labels against that `t_s`, so the checks below use the same base.
The three recordings start on a device uptime clock (`t_ms` near 226737341, 231787566 and 231874055), so the subtraction matters.
"A row inside the segment" means `start <= t <= end`, matching the label filter in `posture_report.py`.
"With a pose" means `shoulder_x` is not empty; the replay test turns such rows into a null verdict (`PostureReplayTest.kt:33`), so a pose-less row inside a segment can only come out silent.
The check ran on the extracted copy, with this command from `cpr-coach/`:

    python - <<'PY'
    import csv, os
    td = "tools/testdata"
    rows = [l for l in open(td + "/labels.csv", encoding="utf-8-sig") if l.strip() and not l.lstrip().startswith("#")]
    cache = {}
    for l in csv.DictReader(rows):
        f, s, e = l["file"], float(l["start_s"]), float(l["end_s"])
        p = os.path.join(td, f)
        if f not in cache:
            c = list(csv.DictReader(open(p, encoding="utf-8-sig"))) if os.path.isfile(p) else []
            t0 = int(c[0]["t_ms"]) if c else 0
            cache[f] = [((int(r["t_ms"]) - t0) / 1000.0, bool(r["shoulder_x"].strip())) for r in c]
        rec = cache[f]
        seg = [q for t, q in rec if s <= t <= e]
        print("%-22s %6.1f-%-5.1f %-9s %-16s exists=%s start<end=%s inside=%s rows=%d pose=%d (%d%%)" % (
            f, s, e, l["label"], l["source"], os.path.isfile(p), s < e,
            bool(rec) and 0 <= s and e <= rec[-1][0], len(seg), sum(seg),
            round(100 * sum(seg) / len(seg)) if seg else 0))
    PY

A second run of the same code on a temporary label file (a missing file name, `start == end`, `end` past the recording, and a window with no pose) reported all four defects and no false pass. So the checks can fail.

| File | Segment (s) | Label | Source | File exists | start < end | Inside recording (length) | Rows | With pose | Pose share |
|---|---|---|---|---|---|---|---|---|---|
| real-cph2467-12.csv | 18.0-29.0 | bent | team_unconfirmed | yes | yes | yes (52.26 s) | 247 | 247 | 100% |
| real-cph2467-15.csv | 3.5-32.0 | straight | pictures | yes | yes | yes (71.45 s) | 654 | 654 | 100% |
| real-cph2467-15.csv | 32.3-34.0 | unknown | none | yes | yes | yes (71.45 s) | 38 | 38 | 100% |
| real-cph2467-15.csv | 34.5-37.3 | straight | pictures | yes | yes | yes (71.45 s) | 60 | 60 | 100% |
| real-cph2467-15.csv | 37.4-38.0 | unknown | none | yes | yes | yes (71.45 s) | 14 | 14 | 100% |
| real-cph2467-15.csv | 38.2-43.4 | bent | pictures | yes | yes | yes (71.45 s) | 116 | 116 | 100% |
| real-cph2467-15.csv | 44.0-53.4 | straight | pictures | yes | yes | yes (71.45 s) | 204 | 204 | 100% |
| real-cph2467-15.csv | 54.0-61.5 | bent | pictures | yes | yes | yes (71.45 s) | 158 | 158 | 100% |
| real-cph2467-15.csv | 65.0-69.5 | leaning | team_unconfirmed | yes | yes | yes (71.45 s) | 100 | 100 | 100% |
| real-cph2467-14.csv | 3.0-24.0 | straight | pictures | yes | yes | yes (63.19 s) | 455 | 455 | 100% |

All 10 rows pass: every file is in `tools/testdata`, every `start` is below its `end`, every segment ends before the recording ends, and every row in every segment carries a pose.

## Other checks

- Labels use only the four values the file allows (`straight`, `bent`, `leaning`, `unknown`).
- `unknown` rows all have `source=none`.
- Segments in the same file do not overlap. The gaps between segments of -15 are unlabelled: 32.0-32.3, 34.0-34.5, 37.3-37.4, 38.0-38.2, 43.4-44.0, 53.4-54.0 and 61.5-65.0 s.
- Timestamps inside the three recordings are unique and sorted, so no row is counted twice.
- The slowest row spacing inside a labelled segment is 0.10 s, and the row rate is 21-23 rows per second, so a 100% pose share is not an artefact of very few rows.

## Two things the file does not say

- `source=pictures` is used on 6 of the 10 rows, but the header comment lists only `human`, `team_unconfirmed` and `none`. `tools/posture_report.py` groups every source other than `team_unconfirmed` as "HUMAN-LABELLED", so those 6 rows are printed as human labels even though the file itself says a coding assistant read them from saved frames and no person checked them. Any agreement printed under HUMAN-LABELLED therefore rests partly on unchecked rows, while `docs/POSTURE_VALIDATION.md` calls only the `team_unconfirmed` block provisional. Either the header should list `pictures` and the report should give it its own group, or those rows should say `team_unconfirmed`.
- The comment block names recordings 13-15 as the ones with saved frames, but only 14 and 15 have label rows. The line "Recordings real-cph2467-1..9 and the others have no labels at all and are not listed" does hold: -10, -11, -13, `real-rmx3392-1.csv` and `negative-cph2467-nobody.csv` have no rows either.

## Pose gaps outside the labels

`real-cph2467-14.csv` has no pose at all from 33.20 s to 52.45 s and from 52.66 s to 63.19 s, and `real-cph2467-13.csv` has 39 rows without a pose spread over 36.04-44.45 s. The labelled -14 segment (3-24 s) sits wholly inside the posed part, so the 100% share is real, but fewer than 1% of the rows of -14 after 33 s carry a pose.
