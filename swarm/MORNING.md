# Good morning, Vivek

swarm is built and tested on vivek-pc. Right now it is **stopped** (`swarm down`) and **autostart is disabled**, so nothing is running. The full test log is in `swarm/TEST_RESULTS.md`.

## What works (tested tonight on vivek-pc)
- `swarm setup`, `test`, `up`, `down`, `status`, `watch`, `fix`, `queue`, `tasks`, `review`, `merge`, `reject`.
- Heartbeats go only to `hb/vivek-pc`; main gets no heartbeat commits. The listener and watchdog run in the interactive session (Session 1), not Session 0.
- A real task through OMP + Codex Luna: queued, done in about 25 s, pushed to `task/<id>`, reviewed, merged.
- Quick login tests passed for codex-luna, codex-sol, grok and antigravity.
- Two machines on one PC (a fake "test-pc2"): each saw the other. When test-pc2 stopped, a red "asleep or off" alert appeared and its queued task moved to another worker by itself.
- Breaking things on purpose:
  - A stale `index.lock` was cleared automatically.
  - After I killed the listener, the watchdog brought it back in 13 s.
  - A fake out-of-tokens state gave a red alert with the reset time and where the tasks moved.
  - A fake offline state gave "lost internet. Check Wi-Fi/cable."
  - A fake expired OpenCode key gave the key alert with the login command.
- Bugs found and fixed during testing:
  - Making the scheduled task needed admin; setup now uses `Register-ScheduledTask`.
  - A CRLF bug broke the heartbeat file.

## What is untested or cut
- **Real Linux is untested.** WSL Fedora would not start ("Failed to attach disk ... Access is denied"), and I did not change its file permissions. The Linux parts (setup.sh, systemd unit, notify-send, pgrep) were only checked on paper. Pratham's PC is the first real Linux run.
- Popups: the toast command ran without errors, but nobody was awake to see one.
- `swarm add` / `remove` for borrowed accounts were not run, because they open a real login. Try them with Vachana's or someone's account in the morning.
- OpenCode Go usage % may show as `?`, because omp may not report limits for API-key providers.
- Small known issues:
  - `swarm tasks` can briefly list a task as both running and waiting for review.
  - The dashboard's "recent done" list was empty in one run.

## Your Claude usage now
- 5-hour: **67% left** (resets about 05:49).
- 7-day: **56% left** (resets Sun 19:29). I started at 59%, so tonight used about 3 points. The 45% floor was never close.

## Commands per PC (repo already cloned, run from the repo folder)
**vivek-pc (Windows, already set up):**
```
git pull
swarm up
swarm watch
```
Then open Claude Code in the repo and say: "You are boss. Read swarm/BOSS.md."

**pratham-pc (Linux):**
```
git pull
sh swarm/setup.sh --machine pratham-pc --workers argon,flash,haiku,dspro
swarm up
swarm watch
```
If setup says `~/.local/bin` is not on PATH, run the one line it prints.

**prabal-pc (Linux):**
```
git pull
sh swarm/setup.sh --machine prabal-pc --workers argon,flash,haiku,codex-luna,codex-sol
swarm up
swarm watch
```

**moksh-pc (Windows):**
```
git pull
swarm\setup.cmd --machine moksh-pc --workers ag-argon
swarm up
swarm watch
```
If setup's test says "model not found" for ag-argon, run setup again with `--workers antigravity`.

**Any PC with a red alert:** do the action it shows. Anything git-shaped: `swarm fix`.
**Borrowed account:** `swarm add` (it opens the normal login). To give it back: `swarm remove <name>`.

## You need to decide
1. **One stray file is at the repo root:** `fake_output.txt`. An early test merge put it there, outside `swarm/`, before I fixed the fake worker. My rules didn't let me remove files outside `swarm/`, so please run: `git rm fake_output.txt && git commit -m "Remove test file" && git push`.
2. **Main has about 40 test commits** (queue, move, merge) and 5 test reports in `swarm/reports/`. They are harmless. Keep them, or delete the reports.
3. **Popups** go to the PC with the problem, to the boss PC, and to every PC for "PC down". If that's too noisy, tell the boss to change it in `swarm/swarmlib/alerts.py`.
4. **Model choices** are in `swarm/config/routing.json` and `presets.json`. The Antigravity worker uses gemini-3.8-flash, the best Gemini I saw there; edit it if Moksh's account has Argon.
