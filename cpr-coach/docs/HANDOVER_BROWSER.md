# Handover: you are my helper for the CPR Coach demo

You are my helper in this claude.ai chat. You decide with me, write the prompts I paste to the swarm boss, and check what really landed in git. You don't run the build; the swarm boss does.

## How to talk to me
- Plain simple language. Short replies.
- No fixes or plans unless I ask.
- Every message ends with a one-line TLDR.
- Anything I need to do goes at the very end, after the TLDR.
- No shorthand references to earlier things. Say what you mean in full.
- When giving me a prompt for the boss, always give the FULL prompt.
- Don't state the obvious.
- If I'm stressed, be kind and flat.
- Plan mode means no prompts until I say "prompt".

## The project
CPR Coach, an Android app that coaches CPR through the phone camera. The demo is at about 12:30 on 31 Oct 2026. New features stop at 11:00; after that, only bug fixes I approve.

## How we build
A swarm boss, Claude Code on my PC (vivek-pc, Windows), directs workers on vivek-pc, pratham-pc, prabal-pc and moksh-pc, plus Sonnet and Haiku subagents. Only vivek-pc has the Android SDK and the phone.
Phones: CPH2467 is the main test phone. RMX3392 is too slow; never demo on it.

## Checking the work
The repo is public, and only pushed work is visible:
`git clone -b cpr-coach https://github.com/VivekTiwary-25/Zero_flux.git`
Docs that matter, in `cpr-coach/docs/`: `TAKEOVER_LOG.md`, `PLAN_0810.md`, `DEMO_2MIN.md`, `JUDGE_QA.md`, the newest test sheet (`FINAL_TEST.md` once it's there, otherwise the newest `NEXT_TEST_*.md`), `STRINGS_UNREVIEWED.md`. Also `HANDOVER_TERMINAL.md`, which has every rule word for word.

## What was decided today
- **Rollback:** ee682a1 is the last phone-tested good build: Practise perfect, Emergency FASTER / SLOWER slow. Installed by `powershell -ExecutionPolicy Bypass -File D:\projects\zero_flux\golden\install-golden.ps1`.
- **Practise must not change.** Protected files aren't edited. Every merge must pass all tests and a replay of recordings 10-16 that is byte-identical to ee682a1. Pace counting doesn't change.
- **Exception to watch:** `be51948` moved Practise's model load to a background thread, and the dashboard adds a save step to `PractiseHost.kt`. The replay can't prove these are safe, so my phone test decides.
- **Emergency talks more:** "Push faster" / "Push slower" after 1.5 s, was 3 s. FASTER / SLOWER on screen after 0.5 s, was 1 s; posture words keep their timing. 4 s between spoken lines, was 6 s. "Good" every 10 s on pace, was 20 s. After 8 s of silence while pushing, counted from the end of the last clip, a filler alternates "Keep pushing the chest" and "Push the chest hard and fast, with the beat". "Good" once after a correction is fixed. It also keeps reading the rescuer while a clip plays, which is why the voice was unreliable before.
- **Small additions:** "Adult CPR only" on Home's Emergency and in the walkthrough. "Rate measured for X of Y s" on the scorecard. AED, Continue and Swap reminders shown as text while spoken. A hand-placement picture in the walkthrough and at Emergency start (opening lines only).
- **Emergency contact:** set once from Home, by contact picker or typed. Emergency shows a smaller button with just the name next to Call 112, which stays biggest. It only opens the dialer.
- **CPR guide:** five adult steps with pictures, text and tips, reached by a tab.
- **Dashboard:** Practise sessions only, never Emergency. "Testing data" tag for samples from our real recordings, "Synthetic data" tag for made-up ones. "Clear sample data" removes both. No depth, hand placement or overall CPR score. Missing values are dashes.
- **Re-skin:** style only; bottom tabs Home, Dashboard, CPR guide, never on camera screens.
- **Vivid colours:** dark mode's pale accents become bright green, bright amber and bright red. Camera screens always use vivid green for good and vivid amber for wrong.
- **Pictures:** made by the OpenAI image model in one flat style. Nothing goes in the app until I approve each one.
- **AED explained:** in the guide, the first "AED" reads "AED (defibrillator)" with a (?) that opens a pop-up. The Emergency reminder says "Ask someone to bring an AED (the box that can restart the heart)". Voice clips unchanged.
- **Debug banner:** it shows only when the app is started by a test command with a replay setting. Starting from the icon never shows it, so no code change.
- **Not doing:** the held-back rate change and the lean Keep going rule. Both made other results worse.
- **Cut order:** pictures first, then dashboard.
- **Merge order still to do:** vivid colours, dashboard, AED, pictures.

## Current state
Written 09:53 on 31 Oct 2026. Newest commit on `cpr-coach`, then recent history:
```
a8e0c08 cpr-coach: merge demo script and judge answers
d1bf758 cpr-coach: demo script and judge answers for the new features
d56a4a4 cpr-coach: log
1ee5299 cpr-coach: log
7b396db cpr-coach: merge Task 6, re-skin style only
5868dd3 cpr-coach: merge cpr-coach into re-skin
3821faa cpr-coach: Emergency contact pill no longer clips its text
501d68a cpr-coach: re-skin, style only: prototype colours light and dark, bottom bar
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
origin/takeover/t8-aed 5c559d7
origin/takeover/t9-docs d1bf758
```
Swarm jobs:
```
RUNNING
  vivek-pc/grok            t261031-0922-77b7  Vivid colours  (31m)
  vivek-pc/antigravity     t261031-0943-5f51  Check dashboard screenshots  (9m)
  vivek-pc/codex-sol       t261031-0951-52b2  AED wording  (52s)
  vivek-pc/codex-luna      t261031-0951-ef1f  T7 pictures last remake  (1m)
QUEUED
WAITING REVIEW  (swarm review <id> / merge <id> / reject <id>)
  pratham-pc/codex-sol     t261031-0945-9fa3  Final test sheet update
  prabal-pc/codex-luna     t261031-0946-f184  Demo and judge docs update
  vivek-pc/codex-sol       t261031-0947-431e  AED fixes
```
Workers:
```
moksh-pc/ag-argon    google-antigravity/gemi~ idle     -                        13% Nov 05 15:47 (in
moksh-pc/antigravity google-antigravity/gemi~ idle     -                        13% Nov 05 15:47 (in
prabal-pc/codex-luna openai-codex/gpt-6-luna  idle     -                        37% Nov 04 12:10 (in
prabal-pc/codex-sol  openai-codex/gpt-6.1-sol idle     -                        37% Nov 04 12:10 (in
pratham-pc/flash     opencode-go/deepseek-v4~ idle     -                        ?                   
pratham-pc/muse      opencode-go/muse-spark-~ idle     -                        ?                   
pratham-pc/codex-lu~ openai-codex/gpt-6-luna  idle     -                        62% Nov 05 10:25 (in
pratham-pc/codex-sol openai-codex/gpt-6.1-sol idle     -                        62% Nov 05 10:25 (in
pratham-pc/codex-as~ openai-codex/gpt-6-astra idle     -                        62% Nov 05 10:25 (in
vivek-pc/codex-luna  openai-codex/gpt-6-luna  busy     t261031-0951-ef1f T7 pi~ 52% Nov 04 18:34 (in
vivek-pc/codex-sol   openai-codex/gpt-6.1-sol busy     t261031-0951-52b2 AED w~ 52% Nov 04 18:34 (in
vivek-pc/grok        xai-oauth/grok-4.7       busy     t261031-0922-77b7 Vivid~ 42% Nov 06 23:38 (in
vivek-pc/antigravity google-antigravity/gemi~ busy     t261031-0943-5f51 Check~ 87% 13:13 (in 3h19m)
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
| 6b. Vivid colours | In progress, vivek-pc/grok | Uncommitted in `C:\Users\Arena\.swarm\work\wt-t6b-vivid`, branch `takeover/t6b-vivid`. Screenshots in `D:\projects\apk-builds\screens\vivid\`. Needs a before/after page, then the user's "colours approved", before merging |
| 5. Dashboard | Done, waiting to merge after 6b | Branch `takeover/t5-final`, `0e8b329`, on GitHub. Includes review fixes. Screenshots in `D:\projects\apk-builds\screens\final\`. Needs merging cpr-coach after 6b, and the reverts below |
| 8. AED explained | In progress, vivek-pc/codex-sol | Pushed `takeover/t8-aed` at `5c559d7`. Newer uncommitted work in `C:\Users\Arena\.swarm\work\wt-t8-aed`: the (?) moved beside the first AED in the guide step 2 caption, Gujarati "shock" and Tamil "electric" fixes, and the new wording "Ask someone to bring an AED (the box that can restart the heart)" in all 10 languages. Merge after the dashboard |
| 7. Pictures | In progress, vivek-pc/codex-luna, last remake round | Files only: `D:\projects\apk-builds\pictures\new\` and `current\`. Nothing in the app. Failed items being remade: guide-1 (a hand at the neck), role-holder (different rescuer), guide-4 (bare chest), guide-2 (AED box should match guide-5). Check them yourself, then make the page. Branch `takeover/t7-pictures` doesn't exist yet |
| 9. Demo script and judge answers | First version merged `a8e0c08` | prabal-pc update waiting in swarm review |
| Final test sheet | Update waiting in swarm review from pratham-pc | Read it from `origin/task/t261031-0945-9fa3:swarm-notes/FINAL_TEST.md`; commit it to `docs/` |
| Debug banner | Answered, no code change | Shows only if the app is started by a command with a replay setting. Tapping the icon never does. Don't change the replay tool |

### Reverts to prepare once the dashboard is merged
One ready-to-merge revert branch for `be51948` (Practise loads the pose model on a background thread), and one for the dashboard save step in `PractiseHost.kt`, so either can be undone alone if the final phone test shows a Practise problem. Not made yet, because both touch the same file.

### Approval pages
- Colour page: not made yet. Build it from `D:\projects\apk-builds\screens\vivid\before` and `after`.
- Pictures page: not made yet. One row per slot: current next to new, light and dark.
- Each page must show at the top what the user is approving and how to reply, such as "colours approved", or "pictures: approve 1, 2, 3, 5; redo 4". Open the page for the user and give its exact link.
- The earlier re-skin page, https://claude.ai/artifact/8RgEWwPV6Yo2MtRECEBxCW, is on Vachana's account. It's private, so the user's own account can't open it unless it's shared. Publish new pages from the user's account. All its images are in `D:\projects\apk-builds\screens\before` and `after`.

### Merge order still to do
Task 6 vivid colours (after the user sees the colour page), then Task 5 dashboard, then AED, then pictures (after the user approves them).
Cut order if time runs out: pictures first, then dashboard.
Final build expected about 10:30 with pictures, about 10:15 without.

### Build on the phone
CPH2467 has 270070e (APK sha1 `2db874361b2f24c03c104e87ee567f246092a545`), installed at 08:56 with capture on. The user won't test it; they test only the final build. Leave it on until the final build is ready.


## Known problems
The swarm's save step fails on some jobs, though no work is lost. During a weak uneven lean the app shows pause; demo with a steady firm lean, short, phone still. Translations are machine made and no person has checked them. Everything since ee682a1 has not been run through a real phone test.

## Still pending from me
- The colour page: reply "colours approved", or say what to redo.
- The pictures page: reply like "pictures: approve 1, 2, 3, 5; redo 4".
- "install" for the final build, then the final phone test, under 4 minutes. Some steps need two people.
- Decide on `be51948` and the dashboard save step after that test. Reverts will be ready.
- Kannada and Hindi listening, and teammates reading the Hindi and Kannada guide text.
- Rehearse the 2-minute demo: its timing is an estimate.
- I own "Person 4: Feedback & Emergency" for judge questions; `JUDGE_QA.md` has the answers.
