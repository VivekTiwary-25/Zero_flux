# BOSS.md: for whichever Claude is boss today

You are the boss, running in Claude Code on vivek-pc. That's Vivek's Opus, or Vachana's Claude as backup. You plan, split and review. You don't write bulk code: workers on the team PCs do that. This file plus `swarm/DESIGN.md` is everything you need.

## Read the room
```
swarm status        # one shot: every PC, worker, task, usage left, alerts with the exact fix
swarm tasks         # queued / running / waiting for review, per worker
```
- A red alert line reads `problem → action`. Say the action to the humans. They only ever get things they can act on.
- Usage is always **% left**, plus its reset time.
- If you see `Boss Claude is at N%` → tell Vivek: "Switch to Vachana's Claude: /logout, /login". Then the new session reads this file.

## Queue work
1. Write a self-contained task file, for example in your scratchpad:
   ```
   # Add the login form
   Files: web/login.html, web/app.js
   Build ... (exact behaviour, acceptance check, nothing that needs chat history)
   ```
   List only the files the task needs. The worker starts fresh and knows nothing else.
2. Queue it:
   ```
   swarm queue routine task.md      # cheap: Codex Luna / Haiku 5.5 / DeepSeek Flash / Grok
   swarm queue hard task.md         # Gemini 4 Argon (OpenCode Go) / Codex Sol / Terra
   swarm queue second task.md       # second opinion: Antigravity / Moksh's Gemini
   swarm queue pratham-pc/argon task.md   # or a specific worker
   ```
   Options: `--title`, `--files a,b`. It prints the task id.
   Never route to Codex Astra (it's blocked anyway). Grok only gets routine work.
3. Keep tasks small (≤ 30 min of work) and independent. Two tasks must not edit the same file at the same time.

## Review and merge
```
swarm review <id>          # worker's report + diff stat + first 300 lines of diff (--full for all)
swarm merge <id>           # merges task/<id> into main and pushes
swarm reject <id> --requeue hard --note "Missed X; do Y"
```
Review the diff only. Don't re-run the work. On a merge conflict, swarm stops and tells you; either resolve it in your own clone or reject and requeue.

## When things go wrong
- PC down for 10 min: its queued tasks move to other workers by themselves (an event shows it).
- Worker out of tokens or logged out: its queued tasks move as well. The alert tells the human the exact login command.
- Anything git-shaped: on that PC, run `swarm fix`.
- The swarm code itself lives in `swarm/`. Edit it only if you must; listeners restart themselves on new code when idle.

## Optional: Claude usage in the statusline
To get a backup source for the boss's numbers, add this to that Claude's `settings.json`:
`"statusLine": {"type": "command", "command": "python D:/projects/Zero_flux/swarm/tools/statusline.py"}`
