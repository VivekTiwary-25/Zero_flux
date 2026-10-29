# Decisions (made overnight, no one awake to ask)

1. **Usage numbers.** `omp usage --json` gives `amount.remaining`, which is the percent LEFT. I checked it against the text view ("11.0% used" = remaining 89). swarm stores and shows remaining % everywhere.
2. **Budget at start (Oct 30, ~00:00):** Claude 5-hour 89% left, 7-day 59% left. The stop line is 45% weekly, so tonight could spend about 14 points of weekly.
3. **A dedicated clone does all the automation:** `~/.swarm/repo`. The listener never touches the clone people work in. It only fast-forwards with `reset --hard origin/main` and never rebases, so a killed git can't leave a half-done rebase there.
4. **Heartbeats use git plumbing** (hash-object, mktree, commit-tree, then force-push `hb/<machine>`). Each heartbeat branch is a single commit with no parent, so main stays clean and nothing touches the index or the working tree.
5. **Main gets only a few commits from swarm:** queued tasks, merges, machine config and task moves. All of them go through one function (`commit_to_main`): fetch, reset, apply, commit, normal push, and retry when the push is rejected. It never force-pushes.
6. **Every task runs in its own git worktree** (`~/.swarm/work/<id>`) on branch `task/<id>`. Agents never commit; the listener does. The task text goes in `SWARM_TASK.md`, which is deleted before the commit.
7. **Queues are on main**, at `swarm/queue/<machine>/<worker>/<id>.md`. Only the boss adds tasks. Only the elected mover (the alphabetically first machine with a fresh heartbeat) moves tasks off a dead PC. A worker's own PC moves that worker's tasks when it runs out of tokens or gets logged out. So no two PCs race for the same task.
8. **One instance per program** is enforced by binding a localhost port. The OS frees the port when the process dies, so stale pid files can't block a restart.
9. **"Never kill git":** git commands have no timeout. Instead each one runs with `http.lowSpeedLimit` and `GIT_TERMINAL_PROMPT=0`, so git gives up on its own when the network dies. Agent processes can be killed; git processes never are.
10. **Borrowed accounts** get an isolated dir in `~/.swarm/profiles/<name>`. OMP uses `PI_CODING_AGENT_DIR`, which the verified facts say acts like a fresh install. Claude Code uses `CLAUDE_CONFIG_DIR`. Remove = logout, then delete the dir. I chose this over `omp --profile` because I can't confirm how omp deletes a profile, while a dir we own is easy to delete.
11. **Harnesses:** OMP and Claude Code only, as the spec says. Codex runs through OMP's `openai-codex` provider. A "fake" harness exists only for `swarm test`.
12. **Routing** is a list of model patterns per kind in `swarm/config/routing.json`, so adding a PC needs no routing edit. `*astra*` is blocked everywhere. Grok appears only in `routine`.
13. **The boss is not a worker.** Vivek's Anthropic account is never auto-added as a worker, which protects the boss allowance.
14. **Claude statusline backup** is opt-in (`swarm/tools/statusline.py`, explained in BOSS.md). I did not edit your `~/.claude/settings.json`.
15. **The PATH shim** goes in `~/.local/bin` (already on PATH on vivek-pc). Setup never edits PATH or shell rc files; if the folder isn't on PATH, setup prints the command to add it.
16. **Code auto-updates:** the shim and autostart run code from the dedicated clone. When a pull changes swarm code, the listener restarts itself (exit 3, then the watchdog restarts it) once all workers are idle.
17. **Popups** show on the PC that has the problem, on the boss PC, and on every PC for "PC down" alerts. Each alert pops up once, then again every 30 min while it lasts.
