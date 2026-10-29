# AGENT_SETUP.md: set up swarm on this PC (for an AI agent or a human)

Goal: this PC joins the swarm. It runs a listener and a watchdog that start at login. It pushes a heartbeat to `hb/<machine>` every ~60 s and runs tasks the boss queues for its workers.

Requirements: git, Python 3.10+, and OMP (`omp`) logged in to this PC's accounts. Linux also needs `gh` logged in.

## Steps
1. Go to the repo root (the clone of VivekTiwary-25/Zero_flux).
2. Pick the machine name and workers. Names already in use: vivek-pc, pratham-pc, prabal-pc, moksh-pc.
   Worker presets are in `swarm/config/presets.json`:
   - vivek-pc: `codex-luna,codex-sol,grok,antigravity`
   - pratham-pc: `argon,flash,haiku,dspro`
   - prabal-pc: `argon,flash,haiku,codex-luna,codex-sol`
   - moksh-pc: `ag-argon`
   Only pick workers whose provider is logged in. Check with `omp usage` (and on OpenCode Go PCs, `omp models opencode-go`).
3. Run setup, without prompts:
   - Windows: `swarm\setup.cmd --machine <name> --workers <list> --yes`
   - Linux:   `sh swarm/setup.sh --machine <name> --workers <list> --yes`
4. Setup ends with `swarm test`. Every line must be PASS. For a FAIL, follow the `fix:` hint, then run `swarm test` again.
5. Run `swarm up`, then `swarm status`. This machine should show a fresh heartbeat.

## Don'ts
- Don't edit files outside `swarm/`. Don't force-push anything except what swarm itself does (`hb/*`).
- Don't log accounts in or out. If a login is missing, tell the human the exact command `swarm test` printed.
- To borrow someone's account, a human runs `swarm add`; it opens that tool's normal login.
