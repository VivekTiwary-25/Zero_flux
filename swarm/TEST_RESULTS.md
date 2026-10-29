# swarm integration test results (vivek-pc, Windows 11, Python 3.12, 2026-10-30)

| # | Test | Result | What was seen |
|---|---|---|---|
| 1 | setup vivek-pc | PASS (after fix) | local.json, ~/.swarm/repo clone, vivek-pc.json on origin/main, hb/vivek-pc branch, swarm.cmd shim all OK. Scheduled task was NOT created at first. Fix: `schtasks /SC ONLOGON` is denied without admin; autostart.py now falls back to PowerShell Register-ScheduledTask (logon trigger for the current user, Interactive, no time limit, runs on battery). |
| 1 | autostart task interactive | PASS | LogonType Interactive; pythonw processes ran in Session# 1 |
| 2 | test --no-agents | PASS (after fix) | Heartbeat read-back failed: util.run sent stdin in text mode, Windows turned LF into CRLF, `git mktree` made a file named `heartbeat.json\r`. Fix: stdin is sent as bytes. Fake task end to end pushed and deleted task/selftest-*. |
| 3 | up, status, watch | PASS | status showed fresh heartbeat, workers with remaining % and reset times; watch renders (6 s run). Listener and watchdog in Session# 1. |
| 4 | peer watching (2 instances) | PASS | Both dashboards showed both machines. Task queued to test-pc2/fake finished (branch task/<id>), review and merge OK. |
| 4 | pc_down and task move | PASS | After test-pc2 stopped, vivek-pc showed red "test-pc2 is asleep or off ... its tasks moved to vivek-pc/fake" and moved the task (event "moved task ... (pc down)"). |
| 5a | stale lock | PASS | `inject lock` cleared within one loop, event "cleared stale index.lock", no alert left. |
| 5b | kill listener | PASS | Watchdog restarted it in 13 s, event "watchdog restarted listener". |
| 5c | out of tokens | PASS | Red "vivek-pc/codex-sol is out of tokens (resets 02:56 ...) -> queued tasks moved to vivek-pc/fake"; `inject clear` removed the alert. |
| 5d | offline | PASS | SWARM_PROBE_HOST=10.255.255.1: "test-pc2 lost internet -> Check Wi-Fi/cable on test-pc2. swarm resumes by itself." |
| 5e | key expired | PASS | SWARM_FAKE_FAIL: "test-pc2/fake: OpenCode API key expired -> Create a new API key ..., then run: omp login fake" (login hint says `omp login fake` for the fake harness; cosmetic). Task moved to vivek-pc/fake and finished. |
| 5 | popup/beep | PASS (partial) | Toast script runs with exit 0 and notified.json dedupe works; I did not see the screen. |
| 6 | real task via omp + codex-luna | PASS | Queued 02:44:44, branch pushed by 02:45:07 (about 25 s). Merged. Report text: "Changed only this report; no implementation files were modified. Checked swarm/DESIGN.md and confirmed these first commands: `swarm setup`. `swarm up` `swarm status` Nothing is left for this task." |
| 7 | Linux path | PASS (static only) | `sh -n setup.sh bin/swarm` OK; with is_windows patched, autostart writes a systemd user unit and calls daemon-reload, import-environment, enable; notify uses canberra-gtk-play and notify-send; heal uses pgrep. A real Linux run is untested (no WSL). |
| 8 | test with agents | PASS | codex-luna, codex-sol, grok, antigravity all replied OK (25 s total); fake task PASS. |

## Other bugs fixed
- Committed `__pycache__` by accident during testing; removed from the index and added swarm/.gitignore.
- `swarm up` on a SWARM_HOME test instance used the shared scheduled task, which runs without that env; it now spawns the watchdog directly and does not install a task.
- The fake harness wrote `fake_output.txt` into the task worktree root, so `swarm merge` put it at the repo root of main. The fake harness now writes only swarm/reports/<id>.md. The file `fake_output.txt` already merged into main is NOT removed (outside swarm/); delete it by hand.
- Added test hooks: SWARM_SETTINGS_OVERRIDE env or `<SWARM_HOME>/settings_override.json` to lower settings.

## Notes and known gaps
- `swarm tasks` can show a task as both RUNNING and WAITING REVIEW for a moment (stale heartbeat data).
- RECENT DONE in the dashboard was empty while done.json had entries for selftest tasks.
- Test merges added five swarm/reports/*.md files to main.
