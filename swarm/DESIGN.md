# swarm design (source of truth for code)

Python 3.10+ standard library only. Windows + Linux. Code lives in `swarm/`:

```
swarm/swarm.py            entry: sys.path.insert(0, dir of this file); from swarmlib.cli import main; sys.exit(main())
swarm/swarmlib/*.py       package (modules below)
swarm/config/routing.json shared routing + settings (in repo, editable)
swarm/config/presets.json known worker presets (in repo)
swarm/config/machines/<machine>.json   per-machine worker list (in repo, written by setup/add/remove)
swarm/queue/<machine>/<worker>/<id>.md task queue (on main, written by boss)
swarm/reports/<id>.md     task report (written on task branch, arrives on main on merge)
swarm/setup.cmd, swarm/setup.sh, swarm/bin/swarm.cmd, swarm/bin/swarm
```

## Paths (util.py)
- `SWARM_HOME` env or `~/.swarm` → `home()`.
- `home()/local.json` = `{"machine": "vivek-pc", "repo_dir": "<abs>", "repo_url": "<url>"}` (written by setup).
- `home()/repo` = dedicated clone used by ALL automation (default repo_dir). Never the human's clone.
- `home()/state/` runtime files: `view.json`, `listener.pid`, `watchdog.pid`, `listener.alive` (mtime = last loop), `down` (flag file: if present, watchdog does not start listener), `notified.json`, `events.jsonl`, `done.json`, `fake_usage.json` (test injection), `listener.log`, `watchdog.log`, `usage_cache.json`.
- `home()/work/<task id>/` = git worktrees for tasks. `home()/profiles/<name>/` = isolated logins of borrowed accounts.
- Code dir = directory containing the running `swarm.py` (`code_dir()`); spawning listener/watchdog uses `[python, code_dir()/swarm.py, "listener"]`.

## util.py (shared helpers, used by everyone)
```
home() -> Path ; state_dir() -> Path (mkdir) ; code_dir() -> Path ; is_windows() -> bool
now() -> float (time.time())
load_json(path, default) -> obj (never raises; default on missing/corrupt)
save_json(path, obj) (atomic: write tmp then os.replace)
run(cmd: list, cwd=None, env=None, timeout=None, input=None) -> Result(code:int, out:str, err:str)
    # never raises; FileNotFoundError -> code 127; timeout -> code 124 (only use timeouts for NON-git commands)
    # on Windows pass creationflags=CREATE_NO_WINDOW; text mode utf-8 errors=replace
which(name) -> str|None   (shutil.which; on Windows also try name+'.exe', '.cmd')
log(name, msg)  append "YYYY-mm-dd HH:MM:SS msg" to state_dir()/<name>.log (rotate at 1 MB -> .1)
event(text, machine=None) append {"ts","machine","text"} to events.jsonl ; recent_events(n=10) -> list
fmt_age(seconds) -> "45s" | "3m" | "2h05m" ; fmt_clock(epoch) -> "14:30" local (add date if not today: "Oct 31 14:30")
fmt_reset(epoch) -> "14:30 (in 2h05m)"
spawn_detached(cmd: list, log_path) -> pid   # Windows: pythonw if available, DETACHED_PROCESS|CREATE_NEW_PROCESS_GROUP|CREATE_NO_WINDOW; Linux: start_new_session=True; stdout/err -> log
pid_alive(pid) -> bool ; kill_pid(pid, tree=True)  # Windows: taskkill /PID /T /F ; Linux: SIGTERM then SIGKILL after 5 s
single_instance(name) -> socket|None  # bind 127.0.0.1:<port>, port = 47000 + (crc32(str(home())+name) % 2000); None if taken. Keep socket object alive.
instance_running(name) -> bool  # try connect to that port
```

## config.py
```
local() -> dict           # home()/local.json, {} if missing
machine() -> str          # local()["machine"] or raise SystemExit("Run: swarm setup")
repo_dir() -> Path        # local()["repo_dir"] or home()/repo
routing() -> dict         # repo_dir()/swarm/config/routing.json merged over DEFAULT_ROUTING
settings() -> dict        # routing()["settings"]
machine_cfg(m=None) -> dict  # repo_dir()/swarm/config/machines/<m>.json ; {"machine":m,"os":..,"workers":[]} if missing
workers(m=None) -> list[dict]
save_machine_cfg(cfg) -> None  # writes file in repo_dir (caller commits via gitops.commit_to_main)
presets() -> dict
```
Worker dict: `{"name":"codex-luna","harness":"omp"|"claude"|"fake","model":"openai-codex/gpt-6-luna","profile":null|"<name>","usage_provider":"openai-codex","usage_match":"","kinds":["routine"],"enabled":true}`.
`profile` set ⇒ isolated login dir `home()/profiles/<profile>/` (omp: env `PI_CODING_AGENT_DIR=<dir>/omp`; claude: env `CLAUDE_CONFIG_DIR=<dir>/claude`). `profile` null ⇒ the PC owner's normal login.

DEFAULT_ROUTING / routing.json:
```json
{"boss": {"machine": "vivek-pc", "note": "Vivek's Opus in Claude Code. Backup: Vachana's Claude on same PC (/logout, /login)."},
 "routes": {
   "routine": ["openai-codex/gpt-6-luna", "*/claude-haiku-5-5", "*/deepseek-v4.1-flash", "xai-oauth/*"],
   "hard":    ["opencode-go/gemini-4-argon", "openai-codex/gpt-6.1-sol", "openai-codex/gpt-6.1-terra", "*/deepseek-v4-pro"],
   "second":  ["google-antigravity/*", "*/gemini-4-argon"]},
 "never_models": ["*astra*"],
 "settings": {"heartbeat_sec": 60, "peer_stale_sec": 180, "peer_dead_sec": 600, "task_timeout_min": 45,
              "low_usage_pct": 3, "boss_low_pct": 10, "usage_refresh_sec": 300, "renotify_min": 30}}
```
Route entries are fnmatch patterns against worker["model"]; earlier = preferred. A worker matching `never_models` is never given work and `swarm add` refuses it.

## gitops.py (all git in repo_dir; never `git rebase`; never kill git)
- Env for every git call: `GIT_TERMINAL_PROMPT=0`, `GCM_INTERACTIVE=never`, and args `-c http.lowSpeedLimit=1000 -c http.lowSpeedTime=30` so git aborts itself on dead network. No subprocess timeout on git.
- `git_lock()` context manager: process-wide threading.RLock + inter-process lock file `state_dir()/git.lock` (O_CREAT|O_EXCL, contains pid; wait up to 180 s polling 0.5 s; steal if holder pid dead or file older than 10 min).
- `git(*args, cwd=None) -> Result` (cwd default repo_dir()).
- `fetch_all() -> Result`: `git fetch --prune origin +refs/heads/main:refs/remotes/origin/main +refs/heads/hb/*:refs/remotes/origin/hb/* +refs/heads/task/*:refs/remotes/origin/task/*`
- `sync_main() -> (ok, msg)`: under git_lock: heal.prepare_repo(); fetch_all(); if HEAD != origin/main: `git checkout -f main` (create from origin/main if missing) and `git reset --hard origin/main`. On failure run heal ladder and retry once. Records state `git_status.json`: last_pull_ok, last_push_ok, last_error.
- `commit_to_main(apply_fn, message) -> (ok, msg)`: under git_lock, up to 4 attempts: fetch origin main; checkout -f main; reset --hard origin/main; `apply_fn(repo_dir)` (writes/moves files; must be idempotent); `git add -A swarm`; if nothing staged return (True,"nothing to commit"); commit; `git push origin HEAD:refs/heads/main` (never force). Rejected ⇒ retry. Records last_push_ok.
- `push_heartbeat(machine, data: dict) -> (ok, msg)`: plumbing only, never touches index/worktree: `hash-object -w --stdin` (json) → `mktree` ("100644 blob <sha>\theartbeat.json\n") → `commit-tree <tree> -m "hb <machine>"` (no parent: branch is always 1 commit) → `git push -f origin <commit>:refs/heads/hb/<machine>`. `force_push` helper must assert ref starts with `refs/heads/hb/`.
- `read_heartbeats() -> dict[machine, dict]`: for-each-ref refs/remotes/origin/hb; `git show <ref>:heartbeat.json`.
- `remote_task_branches() -> set[id]` from refs/remotes/origin/task/*.
- Worktrees: `add_worktree(task_id) -> Path` (`git worktree add -B task/<id> <home()/work/<id>> origin/main`; prune first), `remove_worktree(task_id)` (`git worktree remove --force`, then rmtree leftovers, `git worktree prune`), `push_branch(task_id, cwd)` (`git push -u origin task/<id>`, non-force), `delete_remote_branch(name)` (only `task/*`).
- Identity: setup sets local `user.name`/`user.email` in the dedicated clone (copied from global config if set, else `swarm-<machine>` / `swarm-<machine>@users.noreply.github.com`).

## heal.py (self-healing ladder; runs BEFORE any alert)
```
net_ok(host=env SWARM_PROBE_HOST or "github.com", port=443, timeout=3) -> bool   # socket.create_connection
git_running() -> bool      # any git process on this machine (Windows: tasklist /FI "IMAGENAME eq git.exe" /NH; Linux: pgrep -x git)
clear_stale_locks(repo) -> list[str]  # .git/index.lock, HEAD.lock, packed-refs.lock, config.lock, shallow.lock, refs/**/*.lock, worktrees/*/index.lock
                                      # delete only if (not git_running()) or lock age > 600 s. Each removal -> util.event("cleared stale <file>")
abort_half_done(repo) -> list[str]    # rebase-merge/ or rebase-apply/ -> `git rebase --abort` (fallback: remove dirs); MERGE_HEAD -> `git merge --abort`; CHERRY_PICK_HEAD -> `git cherry-pick --abort`
prepare_repo(repo) -> list[str]       # clear_stale_locks + abort_half_done; returns actions done
ladder(problem: str, retry_fn) -> (ok, diagnosis)
   1 net_ok? no -> return (False, "offline")
   2 prepare_repo()
   3 retry_fn() up to 2 times with 5 s pause -> ok? return (True, "fixed: <actions>")
   4 repair agent: if config workers has one usable (enabled, not blocked) and no repair for same problem in last 30 min:
        run harness.run_agent(worker, cwd=repo_dir, prompt=REPAIR_PROMPT.format(diagnosis), timeout=600)
        REPAIR_PROMPT: "You are fixing the swarm's git clone at <repo>. Problem: <diagnosis>. Only repair git state (locks, half-done merges/rebases, detached HEAD, corrupt index via `git reset`). Do not edit tracked files, do not push, do not change code. Reply with one line saying what you did."
        then retry_fn() once -> ok? (True, "fixed by agent")
   5 return (False, diagnosis)   # caller turns this into a human alert
```

## notify.py
`notify(title, text)`: beep (Windows `winsound.Beep(880,250)` x2; Linux: print "\a" + best-effort `paplay /usr/share/sounds/freedesktop/stereo/bell.oga` or `canberra-gtk-play -i bell`) and popup (Windows: PowerShell `-NoProfile -WindowStyle Hidden` toast via `Windows.UI.Notifications.ToastNotificationManager` with AppId `{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\WindowsPowerShell\v1.0\powershell.exe`, text XML-escaped; Linux: `notify-send -u critical -a swarm`). Never raises. `SWARM_NO_POPUP=1` disables popup+beep (tests).

## alerts (alerts.py)
Alert dict: `{"key": "<type>:<machine>:<worker or ->", "type": <type>, "machine", "worker", "text", "action", "since": epoch}`. Types and EXACT human-actionable wording (fill placeholders):
| type | text | action |
|---|---|---|
| offline | `<machine> lost internet` | `Check Wi-Fi/cable on <machine>. swarm resumes by itself.` |
| pc_down | `<machine> is asleep or off (last heartbeat <age> ago)` | `Wake <machine> / check power and internet, then run: swarm up` |
| tokens | `<machine>/<worker> is out of tokens (resets <fmt_reset>)` | `Nothing to do; its queued tasks moved to <target or 'nobody (no free worker)'>` |
| logged_out | `<machine>/<worker> is logged out` | `On <machine> run: <login command>` |
| key_expired | `<machine>/<worker>: OpenCode API key expired` | `Create a new API key in OpenCode Console, then on <machine> run: <login command>` |
| bad_model | `<machine>/<worker>: model <model> not found` | `Edit swarm/config/machines/<machine>.json (model name) or run: swarm remove <worker>` |
| boss_low | `Boss Claude is at <n>% (<window>) (resets <fmt_reset>)` | `Switch to Vachana's Claude: /logout, /login` |
| git_stuck | `<machine> cannot sync with GitHub: <short diagnosis>` | `On <machine> run: swarm fix` |
| listener_down | `swarm listener not running on <machine>` | `On <machine> run: swarm up` |
Login commands: omp + no profile: `omp login <provider>`; omp + profile: `swarm login <worker>` ; claude + no profile: `claude auth login`; claude + profile: `swarm login <worker>`. (`swarm login <worker>` sets the profile env and runs the tool's login.)
`compute_alerts(own_hb, peers, now) -> list`: own_hb["alerts"] + every peer's own alerts (only if peer fresh) + pc_down for peers older than peer_stale_sec + boss_low (only computed on the boss machine, included in its heartbeat).
`maybe_notify(alerts)`: notify on this machine for alerts where alert machine == me, or I am routing.boss.machine, or type == pc_down; dedupe via notified.json (renotify after renotify_min).

## usage.py
`usage_for(worker) -> {"remaining_pct": int|None, "resets_at": epoch|None, "label": str}`:
- harness omp: `omp usage --json` with the worker's env (cache per env in usage_cache.json for usage_refresh_sec). JSON: `reports[]` each `{"provider", "limits":[{"label", "amount":{"remaining": <0-100 percent REMAINING>}, "window":{"resetsAt": <epoch ms>}}]}`. provider = worker.usage_provider or model prefix before "/". Filter limits by `usage_match` substring of label (case-insensitive) if set. Take the limit with min remaining. Missing provider ⇒ None (unknown).
- harness claude with no profile: same as omp provider "anthropic" (default env). Backup: `state_dir()/claude_statusline.json` (written by swarm/tools/statusline.py: statusline JSON `rate_limits.five_hour.used_percentage`, `rate_limits.seven_day.used_percentage`, remaining = 100 - used).
- claude with profile: statusline backup file `<profile>/claude_statusline.json` if present else None.
- fake: 100, unless fake_usage.json has `{ "<worker>": <pct> }` (test injection; also applies to every harness).
`boss_usage() -> list of {"window":"5h"|"7d","remaining_pct","resets_at"}` from anthropic report (default env) else statusline backup.
All percentages stored and shown as REMAINING.

## harness.py
```
env_for(worker) -> dict      # os.environ copy + profile env (see above)
login_cmd(worker) -> list    # command to run interactively for login (omp: ["omp","login",provider]; claude: ["claude","auth","login"])
login_hint(worker) -> str    # human string for alerts (see alerts)
agent_cmd(worker, prompt_text, cwd) -> list
   omp:    [omp, "-p", "--auto-approve", "--no-title", "--model", model, "--max-time", "<timeout>m", "--cwd", cwd, prompt_text]
   claude: [claude, "-p", "--dangerously-skip-permissions", (--model model if model), prompt_text]
   fake:   [sys.executable, "-c", FAKE_SCRIPT]  # writes swarm/reports/<id>.md and fake_output.txt; reads SWARM_TASK_ID env
run_agent(worker, cwd, prompt_text, timeout_s) -> Result   # agent processes MAY be killed on timeout (they are not git)
classify(result) -> None | "key_expired" | "logged_out" | "tokens" | "bad_model" | "offline"
   key_expired: "API key has expired"
   bad_model:   r"Model .* not found"
   logged_out:  not logged in|no api key|no credentials|please (run )?/?login|authentication_error|invalid api key|401|unauthorized|OAuth token (has )?expired
   tokens:      rate.?limit|usage limit|quota|429|too many requests|limit reached|out of credits|insufficient credits
   offline:     result failed and heal.net_ok() is False
quick_test(worker) -> (ok, msg)  # run_agent with prompt "Reply with exactly the word OK and nothing else." timeout 180 s; ok if code==0 and "OK" in out
```
Prompt to agents is passed as the last argv element. The full task text lives in `SWARM_TASK.md` at the worktree root; the argv prompt says: `Read SWARM_TASK.md in the current folder and do the task it describes.`

## tasks.py
Task file = front matter + markdown body:
```
---
id: t261030-0912-a3f1
title: Add login form
kind: routine
files: web/login.html, web/app.js
created: 2026-10-30 09:12
moved_from: pratham-pc/argon   (optional)
---
<self-contained instructions>
```
`parse(text) -> dict(meta..., body)`, `render(task) -> str`, `new_id() -> "t%y%m%d-%H%M-<4 hex>"`.
Queue dir: `repo/swarm/queue/<machine>/<worker>/<id>.md`. `queued(machine, worker) -> list[path]` sorted by name.
`pick_worker(kind, heartbeats, exclude=set()) -> "machine/worker"|None`: candidates from fresh heartbeats (age < peer_stale_sec): enabled, state != blocked, remaining_pct is None or > low_usage_pct, model not in never_models, model matches a route pattern of `kind` (fallback: any route). Sort by (pattern index, number queued+busy, -(remaining or 50)).
`move_tasks(src_machine, src_worker|None, heartbeats, reason) -> list[(id, target)]`: via commit_to_main; adds `moved_from:`; event "moved task <id> from A to B (<reason>)".
Runner `run_task(worker, path)` (in a worker thread):
 1. skip if id in done.json or in remote_task_branches().
 2. add_worktree(id); write SWARM_TASK.md = prompt template (below); env SWARM_TASK_ID.
 3. run_agent(timeout = task_timeout_min). 4. classify; if blocking class (key_expired/logged_out/tokens/bad_model/offline): remove worktree, mark worker blocked with that problem, return (task stays queued; listener moves it unless offline).
 5. delete SWARM_TASK.md; if swarm/reports/<id>.md missing write one: exit code + last 40 lines of output. `git add -A`; commit `task <id>: <title> (<machine>/<worker>)`; push_branch. 6. done.json append {id, worker, ok, ts}; event; remove_worktree.
Prompt template (SWARM_TASK.md):
```
# Task <id>: <title>
You are one worker in a team "swarm". Work only in this folder (a git checkout on branch task/<id>).
Files that matter: <files>. Read only what you need.

<body>

Rules:
- Do not run git commit, git push, or change branches. The swarm commits for you.
- Do not edit anything under swarm/ except your report.
- When done, write swarm/reports/<id>.md: 3-6 short sentences: what you changed, how you checked it, what is left.
```

## listener.py (one per PC, started by watchdog)
`main()`: `single_instance("listener")` else exit 0. Write pid. Loop until `down` flag exists:
 1. touch listener.alive. net = heal.net_ok(). If offline: own alert offline; write view; sleep; continue (no git).
 2. gitops.sync_main(); on failure heal.ladder → still failing ⇒ own alert git_stuck.
 3. Every usage_refresh_sec refresh usage for all workers; tokens alert when remaining_pct <= low_usage_pct (+ move that worker's queued tasks); boss machine: boss_usage → boss_low alert when any window <= boss_low_pct.
 4. Dispatch: for each enabled, non-blocked, idle worker of this machine: first queued task → thread run_task. Blocked workers: retry after resets_at (tokens) or every 15 min via quick_test (others); unblock on success.
 5. heartbeats = read_heartbeats(); if I am the alphabetically first machine among fresh ones: for every machine with age > peer_dead_sec that has queued tasks ⇒ move_tasks(m, None, ...) and own alert pc_down text gets "its tasks moved to ...".
 6. Build own heartbeat: `{"machine","os","ts","version":<code hash 8>,"pid","started","net_ok","git":{git_status.json},"workers":[{"name","harness","model","state":"idle|busy|blocked|disabled","task":{"id","title","started"}|null,"remaining_pct","resets_at","usage_label","problem"}],"alerts":[own alerts],"events":recent_events(10),"done":last 20 done,"queued":{worker: n}}`; push_heartbeat. Write view.json = `{"me": machine, "written": now, "hb": {machine: heartbeat (own fresh copy for me)}, "alerts": compute_alerts(...)}`. maybe_notify.
 7. If code hash changed and no worker busy ⇒ exit(3) (watchdog restarts with new code).
 8. Sleep heartbeat_sec in 1 s steps (exit early on down flag). On down: terminate running agent processes, remove their worktrees, exit 0.

## watchdog.py
`main()`: `single_instance("watchdog")` else exit 0. Loop every 15 s: if `down` flag ⇒ sleep. Else if not instance_running("listener") ⇒ spawn listener, event "watchdog restarted listener". Else if listener.alive older than 5 min ⇒ kill listener pid, spawn again. Log to watchdog.log.

## autostart.py
- Windows: task name `swarm-watchdog` (suffix `-<machine>` if SWARM_HOME is set). Install: `schtasks /Create /TN <name> /TR "\"<pythonw>\" \"<code_dir>\swarm.py\" watchdog" /SC ONLOGON /IT /RL LIMITED /F`. `enable`/`disable`: `schtasks /Change /TN <name> /ENABLE|/DISABLE`. `start`: `schtasks /Run /TN <name>` (puts processes in the interactive session even when started over SSH). `status()` -> "enabled"/"disabled"/"missing".
- Linux: `~/.config/systemd/user/swarm-watchdog.service` (ExecStart=<python3> <code_dir>/swarm.py watchdog, Restart=always, RestartSec=10, WantedBy=default.target); `systemctl --user daemon-reload`, `enable|disable`, `start`; also `systemctl --user import-environment DISPLAY WAYLAND_DISPLAY DBUS_SESSION_BUS_ADDRESS` for popups. No systemd ⇒ status "unsupported" and `swarm up` uses spawn_detached.

## CLI (cli.py) — `swarm <cmd>`
| cmd | does |
|---|---|
| setup [--machine M] [--workers a,b] [--yes] | interactive (or flags): clone dedicated repo to ~/.swarm/repo, local identity, `gh auth setup-git` on Linux if `git ls-remote` fails, choose workers from presets (pre-tick providers seen in `omp usage --json`), write+commit machine cfg, push heartbeat, install PATH shim + autostart, run test |
| up | remove down flag, enable autostart, start watchdog (autostart start or spawn), wait ≤20 s for listener, print status |
| down | create down flag, wait ≤20 s for listener to exit, kill watchdog + listener |
| status | one-shot dashboard (no clear); exit 1 if any alert |
| watch | full-screen dashboard every 3 s (reads view.json; shows its own refresh time; red banner if listener not running or view.json older than 3 min) |
| fix | heal ladder now: net, locks, half-done ops, sync_main, push heartbeat, start listener if down flag absent; prints each action |
| test [--no-agents] | PASS/FAIL lines: python, git, omp/claude present per worker, config, net, fetch, heartbeat push+read-back, usage per worker, quick_test per worker (unless --no-agents), fake task end-to-end (fake harness in a worktree on task/selftest-<machine>-<ts>, push, verify remote, delete remote branch), listener/watchdog/autostart status (info). Exit 1 on any FAIL |
| add [--harness omp|claude] [--name N] [--model M] [--provider P] | isolated profile dir, run login interactively, quick_test, register worker (commit cfg), event |
| remove <name> | logout (claude: `claude auth logout` with CLAUDE_CONFIG_DIR) + delete profile dir, unregister, event. Refuses workers without profile unless --force (then only unregisters) |
| login <worker> | run that worker's login with its env |
| queue <target> <file|-> [--title T] [--kind K] [--files a,b] | target = machine/worker or a kind (routine/hard/second ⇒ pick_worker). Adds front matter, commits to main. Prints id |
| tasks | queued / running / waiting-review / done per worker |
| review <id> [--full] | report + `git diff --stat` + diff (first 300 lines unless --full) of origin/main...origin/task/<id> |
| merge <id> | in dedicated clone via commit_to_main: `git merge --no-ff origin/task/<id>`; conflict ⇒ abort + tell boss; remove queue file; push; delete remote task branch |
| reject <id> [--requeue TARGET] [--note TEXT] | delete remote branch + queue file; optional requeue (new id, note appended) |
| autostart on|off|status | |
| listener / watchdog | internal: run loops |
| inject lock|tokens <worker> [pct]|clear | test helpers: create stale .git/index.lock in dedicated clone; set/clear fake_usage.json |
