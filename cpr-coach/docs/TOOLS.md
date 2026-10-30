# Tools

Scripts live in `cpr-coach/tools/`; run Python from `cpr-coach`.

## Python

- `analyze_trace.py` — Summarizes rate and windows in session or lab CSV traces. No usage text; help: `analyze_trace.py csv [--count N --seconds S]` (pass both options together).
- `bench_pose.py` — Benchmarks lite, full and heavy MediaPipe pose models on a debug phone build. Usage: `python tools/bench_pose.py --serial R58M123ABC --tap 540,1400` or `python tools/bench_pose.py --download-only`.
- `clip_coverage.py` — Checks that every spoken clip key has a non-empty wav in every language. Usage: `python clip_coverage.py [--root CPR_COACH_DIR]` or `python clip_coverage.py --self-check`.
- `clipgen/check_clips.py` — Transcribes clips (Whisper for English, IndicConformer otherwise) and compares them to the phrase lists. No usage text; help: `check_clips.py [--clips CLIPS] [--phrases PHRASES] [--whisper WHISPER] [--indic INDIC] [--lang LANG] [--markdown MARKDOWN] [--checkpoint CHECKPOINT] [--resume]`.
- `clipgen/generate_clips.py` — Generates normalized, validated speech clips from the phrase files. No usage text; help: `generate_clips.py --models MODELS --out OUT [--lang LANG] [--dry-run]`.
- `clipgen/listen_clips.py` — Plays generated clips for a human reviewer and records the review (Windows only). No usage text; help: `listen_clips.py --lang LANG --name NAME`.
- `compare_signals.py` — Compares trace signals in six-second windows. Usage: `python compare_signals.py trace.csv [--count N --seconds S]`.
- `frame_sheet.py` — Makes labelled contact sheets from debug frame dumps. Usage: `python frame_sheet.py <traces_dir> <session_id> <out_dir> [--from S] [--to S] [--cols 4] [--every N]`.
- `guard_study.py` — Replays the landmark guard sweep with plain Python and emits the sweep as JSON. Usage: `python3 cpr-coach/tools/guard_study.py --data-dir PATH/TO/testdata`.
- `make_test_trace.py` — Writes a synthetic replay CSV; its values are not measurements. Usage: `python3 tools/make_test_trace.py synthetic-replay.csv --rate 110`.
- `phone_ui_test.py` — Exercises CPR Coach screens through adb without touching the dialer. No usage text; help: `phone_ui_test.py --serial SERIAL --out DIR [--adb ADB]`.
- `posture_report.py` — Reports posture judge results, label agreement and fire/clear times. Usage: `python tools/posture_report.py [--replay DIR] [--labels FILE]`.
- `session_report.py` — Summarizes timing, pose availability, side and device metrics in a session trace. No usage text; help: `session_report.py trace [--logcat LOGCAT]`.

## PowerShell

- `phone-smoke.ps1` — Checks startup with permissions granted, then revoked. No usage text; example: `.\phone-smoke.ps1` or `.\phone-smoke.ps1 -Serial <device-serial>`.
- `pull-traces.ps1` — Pulls session traces from a phone and summarizes them, optionally with frame sheets. No usage text; example: `.\pull-traces.ps1 [-Serial <serial>] [-Out <dir>] [-Frames] [-Latest N]`.
- `push-models.ps1` — Copies the speech models to a phone's app files folder, or only copies Whisper assets. No usage text; example: `.\push-models.ps1 [-Serial <serial>] [-ModelDir <dir>] [-CopyWhisper]`.
