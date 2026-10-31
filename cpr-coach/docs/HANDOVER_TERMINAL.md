# Handover: you are the new swarm boss for CPR Coach

You are Claude Code on the user's own Claude account, on this PC (vivek-pc, Windows). You take over as swarm boss from a session on Vachana's account. Everything you need is below.

## How to talk to the user
- Plain simple language. Short replies.
- No fixes or plans unless the user asks.
- Every message ends with a one-line TLDR.
- Anything the user needs to do goes at the very end, after the TLDR.
- No shorthand references to earlier things. Say what you mean in full.
- Message the user only for: an approval point, a merge, a revert, a worker problem you can't fix, your usage below 20%, anything that won't make 11:00, and a status line every 30 minutes (what merged, who is busy, expected final build time).

## The project
CPR Coach, an Android app that coaches CPR through the camera. Repo github.com/VivekTiwary-25/Zero_flux (public), branch `cpr-coach`, app in `cpr-coach/`. Main checkout: `D:\projects\Zero_flux`.
The demo is at about 12:30 on 31 Oct 2026. **New features stop at 11:00.** After 11:00, only bug fixes the user approves.
At 11:00: stop all new feature work, finish or drop half-done jobs (cut pictures first, then dashboard, if not merged), build the final APK, write READY TO INSTALL with hash, sha1 and the test sheet path, and wait for the user.

## Rules, from the user's prompts
1. "Practise behaviour must stay exactly as it is in ee682a1. Do not edit PractiseSession, LiveAnalyzer, RateEstimator, PoseEngine, PostureJudge, ElbowGate, LandmarkGuard, PushDetector, SessionMetrics, OneEuroFilter or LoadGovernor. CoachPolicy may only gain new constructor parameters whose DEFAULT values equal today's values, so Practise is unchanged."
2. "Proof Practise is unchanged, after every merge: all existing tests pass with no Practise test edited, AND a replay of recordings 10-16 through the Practise path gives byte-identical coach phrases, times, rates and scorecards to ee682a1. If it ever differs, revert that merge." Run the gate from `cpr-coach/`: `powershell -NoProfile -ExecutionPolicy Bypass -File tools\check_practise.ps1`. It must end `GATE PASSED`. It takes about a minute.
3. "Pace counting does not change: the 6 s rate window and every RateEstimator setting stay as they are."
4. "UI re-skin is STYLE ONLY: colours, fonts, corner rounding, card styling, icons. Every existing screen keeps its layout, element positions, order, behaviour and navigation. The Practise and Emergency camera screens keep exactly their current layout (skeleton, elbow angle, shoulders-over-hands line, rate, beat dots, big word, buttons in the same places)."
5. "Names stay short as now: 'Practise' and 'Emergency'."
6. "Call buttons only ever open the dialer. Nothing auto-calls. Call 112 stays the biggest call button and always visible."
7. "Each task is developed on its own branch, merged into cpr-coach only after rules 1-3 pass, and pushed right after each merge. No force-push."
8. "Never install on the phone without asking. When a build is ready, write 'READY TO INSTALL <hash> <APK sha1>' with a test sheet, and wait for me to say 'install'. Keep every built APK in D:\projects\apk-builds\ named by hash."
9. "ee682a1 and D:\projects\zero_flux\golden\ stay the rollback." Never delete or overwrite them.
10. No two workers edit the same file.
11. Vivid colours, as the user wrote: "Dark mode: keep the prototype's backgrounds, text and card style, but use vivid accents: a bright green, about as vivid as the old #2DD4BF, for main buttons, the selected tab and 'good'; bright amber (around #FBBF24) for corrections; a bright red for Emergency and Call 112. Text on buttons must stay readable (WCAG AA contrast)." "Light mode: check main buttons and the selected tab don't look dull either; adjust only if they do, same contrast rule." "Camera screens (Practise and Emergency): ALWAYS use the vivid green for good and the vivid amber for wrong on the skeleton, the shoulders-over-hands line and the big word, whatever the phone's light or dark setting, so good and wrong stay easy to tell apart on the camera picture. Layout unchanged."
12. Pictures, as the user wrote: "flat clean line art with soft fills, our theme greens with a touch of skin tone, plain light background, no text inside the picture, the same rescuer and the same patient in every scene, an adult patient lying on their back on a firm floor, the rescuer kneeling beside the chest." "hands only on the centre of the chest (lower half of the breastbone), never anywhere else on the body; arms straight; shoulders directly above the hands; two hands, five fingers each; the patient's head, chest and legs clearly readable; nothing that could look odd or rude; no logos or brand names. Step 4 must clearly show pushing on the chest." Check every picture yourself before showing it. "Do NOT put any picture into the app until I reply with which ones I approve. After I approve, swap the pictures only, no logic changes, and send before/after screenshots."
13. Dashboard: Emergency sessions are not saved or shown. "Testing data" and "Synthetic data" tags on every row and chart that uses them. Never show depth, hand placement or an overall CPR quality score. A missing value shows as a dash, never 0.
14. Don't change pace counting. Don't apply the held-back rate change or the lean Keep going rule: `docs/RECORDING_16.md` has the numbers.

### Approval points: wait for the user, keep everyone else busy
- The colour before/after page, before vivid colours merge.
- The picture comparison page, before any picture goes into the app.
- "install" for any build on the phone.

### Worker checks every 5 minutes
"Check every worker (vivek-pc, pratham-pc, prabal-pc, moksh-pc, and each tool on them) and your Sonnet/Haiku subagents. Any worker that is idle AND has usage left gets the next job from the queue at once. A worker out of usage is noted with its reset time and given work the minute it resets. If a job fails or a worker stalls for more than 10 minutes, take the job back and give it to another worker. Never give a worker a job that edits a file another worker is already editing. Log each check as one line in docs/TAKEOVER_LOG.md: time, who is busy on what, who is idle and why."
"Do not invent new app features. If the whole queue is taken, give idle workers report-only reviews of what others just finished."
Script for this: `python -I D:\projects\apk-builds\boss-tools\watch.py`, run in the background. It logs every 5 minutes and exits to wake you when a task is waiting for review or at a status time.

### Job queue, in priority order
1. Task 6 vivid colours and the colour page. 2. Pictures and their page. 3. Dashboard merge. 4. AED and its translations. 5. Debug banner: done, keep out of the demo by starting from the icon. 6. The one final phone test sheet, under 4 minutes, marking which steps need two people. 7. `docs/DEMO_2MIN.md` for the new features, kept at 2 minutes. 8. `docs/JUDGE_QA.md`: synthetic data and tags, AI-made pictures and their checks, what an AED is, unreviewed translations, the emergency contact never auto-calling, the lean pause. 9. Report-only reviews: medical meaning of translated CPR guide and AED text, text contrast on every re-skinned screen in light and dark, every merged branch against the rules. 10. Screenshot checks of every merged screen in light and dark on the emulator.

## How the swarm works here
- Commands, in PowerShell: `swarm status`, `swarm tasks`, `swarm queue vivek-pc/codex-sol FILE --title "..." --files "swarm-notes/x.md"`, `swarm review ID`, `swarm reject ID`. **Don't use `swarm merge`:** it merges to main. Reject the review once you've taken the work.
- Workers can't commit. Tell them: "git worktree add ../wt-NAME -b takeover/NAME origin/cpr-coach", and leave changes uncommitted. Their folders are in `C:\Users\Arena\.swarm\work\wt-*`. You commit there, push the branch, run the gate, merge to `cpr-coach` with `--no-ff`, gate again, push.
- Their reports: `git show origin/task/<id>:swarm-notes/<file>`.
- Only vivek-pc has the Android SDK, the phone and the emulator (`itantra34`). Tell workers: never touch phone 557d9cdd, always `adb -s emulator-5554`, never a bare `adb`. Only one worker at a time may use the emulator.
- Use Codex workers first for real code. flash and muse get trivial jobs only. Loading every worker at once empties an account fast.
- Task sheets used so far, as examples: `D:\projects\apk-builds\boss-tools\tasks\`.
- Build: from `cpr-coach`, set `$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"`, run `.\gradlew.bat assembleDebug`, then copy `app\build\outputs\apk\debug\app-debug.apk` to `D:\projects\apk-builds\<hash>.apk`.
- Install, only after "install": check the APK sha1, then `adb -s 557d9cdd install -r <apk>`. Check the sha1 on the phone from `pm path` and `sha1sum`, then start with capture on: `adb -s 557d9cdd shell am start -S -W -n com.zeroflux.cprcoach/.MainActivity --ez capture_frames true`.
- Pull traces: `cpr-coach/tools/pull-traces.ps1 -Frames -Latest N -Out DIR`.

## Current state
Auto-updated section, then the boss's notes.

Written 10:01 on 31 Oct 2026. Newest commit on `cpr-coach`, then recent history:
```
88fa66f cpr-coach: merge AED explained, keeping dashboard and AED strings
0e362b1 cpr-coach: log
12799d5 cpr-coach: merge 5 Dashboard
ce82009 cpr-coach: log
032a232 cpr-coach: merge Vivid colours, approved by the user
e076540 cpr-coach: log
70fb804 cpr-coach: AED help beside the first AED, new Emergency wording, Gujarati and Tamil fixes
8a3531c cpr-coach: handover files and log
```
Takeover branches on GitHub:
```
origin/takeover/t0-replay-check 3d43225
origin/takeover/t1-emergency-talk 91785d2
origin/takeover/t2-snappy be51948
origin/takeover/t3-small-additions 317dbaa
origin/takeover/t4-cpr-guide b8f054c
origin/takeover/t4-final f544d4e
origin/takeover/t4b-strings 30193d9
origin/takeover/t5-dashboard db95ba5
origin/takeover/t5-final 0e8b329
origin/takeover/t6-reskin 5868dd3
origin/takeover/t6b-vivid af421fa
origin/takeover/t8-aed 70fb804
origin/takeover/t9-docs d1bf758
origin/takeover/t9b-docs 71b13b9
```
Swarm jobs:
```
RUNNING
  prabal-pc/codex-sol      t261031-0955-4dbc  Contrast check  (5m)
  vivek-pc/codex-luna      t261031-0959-e135  T7 two pictures remake  (2m)
QUEUED
WAITING REVIEW  (swarm review <id> / merge <id> / reject <id>)
  vivek-pc/antigravity     t261031-0943-5f51  Check dashboard screenshots
```
Workers:
```
moksh-pc/ag-argon    google-antigravity/gemi~ idle     -                        13% Nov 05 15:47 (in
moksh-pc/antigravity google-antigravity/gemi~ idle     -                        13% Nov 05 15:47 (in
prabal-pc/codex-luna openai-codex/gpt-6-luna  idle     -                        36% Nov 04 12:10 (in
prabal-pc/codex-sol  openai-codex/gpt-6.1-sol busy     t261031-0955-4dbc Contr~ 36% Nov 04 12:10 (in
pratham-pc/flash     opencode-go/deepseek-v4~ idle     -                        ?                   
pratham-pc/muse      opencode-go/muse-spark-~ idle     -                        ?                   
pratham-pc/codex-lu~ openai-codex/gpt-6-luna  idle     -                        62% Nov 05 10:25 (in
pratham-pc/codex-sol openai-codex/gpt-6.1-sol idle     -                        62% Nov 05 10:25 (in
pratham-pc/codex-as~ openai-codex/gpt-6-astra idle     -                        62% Nov 05 10:25 (in
vivek-pc/codex-luna  openai-codex/gpt-6-luna  busy     t261031-0959-e135 T7 tw~ 51% Nov 04 18:34 (in
vivek-pc/codex-sol   openai-codex/gpt-6.1-sol idle     -                        51% Nov 04 18:34 (in
vivek-pc/grok        xai-oauth/grok-4.7       idle     -                        39% Nov 06 23:38 (in
vivek-pc/antigravity google-antigravity/gemi~ idle     -                        86% 13:13 (in 3h11m)
```

### Tasks
| Task | Status | Where |
|---|---|---|
| 0. Practise replay check and gate | Merged | `3d43225`; `tools/check_practise.ps1` |
| 1. Emergency talks more | Merged | `69817ed`; evidence in `docs/EMERGENCY_TALK.md` |
| 2. Snappier feel | Merged | `325457e`, includes `be51948`; `docs/SNAPPY.md`. After times need a phone |
| 3. Small additions | Merged | `eae6c9d` |
| 4. CPR guide | Merged | `270070e`; translation corrections `0c30a81` |
| 6. Re-skin, style only | Merged | `7b396db`. Merged before the vivid colour request came |
| 6b. Vivid colours | Merged, approved by the user | `032a232` |
| 5. Dashboard | Merged | `12799d5` |
| 8. AED explained | Merged | `88fa66f` |
| 7. Pictures | Page published, waiting for the user's reply; rows 1 and 2 being remade by vivek-pc/codex-luna as guide-1-v2.png and guide-2-v2.png | Files only: `D:\projects\apk-builds\pictures\new\`. Page: https://claude.ai/artifact/VCRrK1CFovjSHD8Zqp1rjs . After approval: branch `takeover/t7-pictures`, swap pictures only, no logic changes, before/after screenshots, then gate and merge |
| 9. Demo script and judge answers | Merged | `6ca1b17` |
| Final test sheet | Merged, needs a final check at build time | `docs/FINAL_TEST.md`, `6ca1b17` |
| Debug banner | Answered, no code change | Shows only if the app is started by a command with a replay setting. Tapping the icon never does. Don't change the replay tool |

### Reverts to prepare once the dashboard is merged
One ready-to-merge revert branch for `be51948` (Practise loads the pose model on a background thread), and one for the dashboard save step in `PractiseHost.kt`, so either can be undone alone if the final phone test shows a Practise problem. Not made yet, because both touch the same file.

### Approval pages
- Colour page: https://claude.ai/artifact/P63RE6eRdDMqJgksdwJ1bM . Published from Vachana's account; if the user can't open it, republish `D:\projects\apk-builds\screens\vivid\` images from the user's account.
- Pictures page: https://claude.ai/artifact/VCRrK1CFovjSHD8Zqp1rjs , published from Vachana's account. Waiting for the reply.
- Each page must show at the top what the user is approving and how to reply, such as "colours approved", or "pictures: approve 1, 2, 3, 5; redo 4". Open the page for the user and give its exact link.
- The earlier re-skin page, https://claude.ai/artifact/8RgEWwPV6Yo2MtRECEBxCW, is on Vachana's account. It's private, so the user's own account can't open it unless it's shared. Publish new pages from the user's account. All its images are in `D:\projects\apk-builds\screens\before` and `after`.

### Merge order still to do
Only pictures remain, after the user approves them.
Cut order if time runs out: pictures first, then dashboard.
Final build expected about 10:30 with pictures, about 10:15 without.

### Build on the phone
CPH2467 has 270070e (APK sha1 `2db874361b2f24c03c104e87ee567f246092a545`), installed at 08:56 with capture on. The user won't test it; they test only the final build. Leave it on until the final build is ready.


## Rollbacks
- `powershell -ExecutionPolicy Bypass -File D:\projects\zero_flux\golden\install-golden.ps1` puts back ee682a1, the last phone-tested good build. On Windows that folder is inside the repo; it is excluded from git, so never run `git clean -x` there. The script has been tested only against a fake adb, not on a real phone.
- `D:\projects\apk-builds\` has every build by hash, including `ee682a1.apk`.
- `D:\projects\apk-rollback\app-e1b489f.apk` is the older build.

## Known problems
- **Swarm save-step bug:** some jobs end "push failed" (grok, pratham-pc muse, prabal-pc luna earlier). `swarm fix` on that machine may help, but it restarts workers, so never run it while they're busy. Workers' own branches and reports still get through.
- **Lean pause:** during a weak, uneven lean the rate drops out and the app shows pause. Not fixed on purpose; see `docs/RECORDING_16.md`. Demo around it: steady firm lean, short, phone still.
- **Unreviewed translations:** `docs/STRINGS_UNREVIEWED.md`. Hindi and Kannada guide text came from teammates; the review flagged "lightly" on the shoulder tap in step 1, a clinical word for breastbone in Hindi, and "join" for interlock in Kannada. Left for teammates to decide.
- **Not yet run on a phone:** `be51948` (Practise model load on a background thread) and the dashboard save step in `PractiseHost.kt`. The replay can't prove either: it doesn't run PractiseHost, the camera, the model or threads. The user will decide after the final phone test. Same for all re-skin colours and everything since ee682a1.

## First commands
1. `git -C D:\projects\Zero_flux fetch origin`
2. `git -C D:\projects\Zero_flux status -sb`, and `git -C D:\projects\Zero_flux branch -r --list "origin/takeover/*" -v`
3. Read `D:\projects\Zero_flux\cpr-coach\docs\TAKEOVER_LOG.md` and `PLAN_0810.md`.
4. `swarm status` and `swarm tasks`, then read every report waiting in review.
5. Start the watcher in the background, and send the user a 5-line status.
