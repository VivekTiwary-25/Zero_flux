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
- **Demo build and freeze:** a99cd63 is the demo build; it passed the team's phone tests, and the team tried it in Hindi, Kannada, English and Gujarati. Code freeze until the demo, unless I report a bug and say "fix it". Golden install: `powershell -ExecutionPolicy Bypass -File D:\projects\zero_flux\golden\install-golden.ps1`. Older golden: ee682a1 in `golden\previous\ee682a1\`.
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
Written 11:55 on 31 Oct 2026. Newest commit on `cpr-coach`, then recent history:
```
b20cadd cpr-coach: handover files and log
aa712ef cpr-coach: handover files and log
a99cd63 cpr-coach: merge logo and Practice spelling
3efcf10 cpr-coach: log
735cdeb cpr-coach: CPR coach mark on Home, Practice spelling on screen
999b53b cpr-coach: look-only check list for a729293
0a37fa7 cpr-coach: handover files and log
a729293 cpr-coach: merge AED help button colour
```
Takeover branches on GitHub:
```
origin/takeover/revert-dashboard-save 4c56067
origin/takeover/revert-model-load fb999d5
origin/takeover/t0-replay-check 3d43225
origin/takeover/t1-emergency-talk 91785d2
origin/takeover/t10-depth 61f6da7
origin/takeover/t10b-colour a5caace
origin/takeover/t11-brand 735cdeb
origin/takeover/t12-docs 336fdeb
origin/takeover/t2-snappy be51948
origin/takeover/t3-small-additions 317dbaa
origin/takeover/t4-cpr-guide b8f054c
origin/takeover/t4-final f544d4e
origin/takeover/t4b-strings 30193d9
origin/takeover/t5-dashboard db95ba5
origin/takeover/t5-final 0e8b329
origin/takeover/t6-reskin 5868dd3
origin/takeover/t6b-vivid af421fa
origin/takeover/t6c-polish 5d18d84
origin/takeover/t7-pictures 04fe4e6
origin/takeover/t7b-role-size 7ff754b
origin/takeover/t8-aed 70fb804
origin/takeover/t9-docs d1bf758
origin/takeover/t9b-docs 71b13b9
```
Swarm jobs:
```
RUNNING
  pratham-pc/codex-luna    t261031-1152-9e2a  deck image: dawn  (2m)
QUEUED
WAITING REVIEW  (swarm review <id> / merge <id> / reject <id>)
  pratham-pc/codex-luna    t261031-1152-9e2a  deck image: dawn
```
Workers:
```
moksh-pc/ag-argon    google-antigravity/gemi~ idle     -                        13% Nov 05 15:47 (in
moksh-pc/antigravity google-antigravity/gemi~ idle     -                        13% Nov 05 15:47 (in
prabal-pc/codex-luna openai-codex/gpt-6-luna  idle     -                        36% Nov 04 12:10 (in
prabal-pc/codex-sol  openai-codex/gpt-6.1-sol idle     -                        36% Nov 04 12:10 (in
pratham-pc/flash     opencode-go/deepseek-v4~ idle     -                        ?                   
pratham-pc/muse      opencode-go/muse-spark-~ idle     -                        ?                   
pratham-pc/codex-lu~ openai-codex/gpt-6-luna  busy     t261031-1152-9e2a deck ~ 62% Nov 05 10:25 (in
pratham-pc/codex-sol openai-codex/gpt-6.1-sol idle     -                        62% Nov 05 10:25 (in
pratham-pc/codex-as~ openai-codex/gpt-6-astra idle     -                        62% Nov 05 10:25 (in
vivek-pc/codex-luna  openai-codex/gpt-6-luna  idle     -                        32% 14:11 (in 2h15m)
vivek-pc/codex-sol   openai-codex/gpt-6.1-sol idle     -                        32% 14:11 (in 2h15m)
vivek-pc/grok        xai-oauth/grok-4.7       idle     -                        35% Nov 06 23:38 (in
vivek-pc/antigravity google-antigravity/gemi~ idle     -                        82% 13:13 (in 1h17m)
```

**Code freeze on. a99cd63 is the demo build and new golden, passed the team's phone tests. The phone has a99cd63.** Everything below is history.

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
| 7. Pictures | Merged | `b3e66f7` |
| 9. Demo script and judge answers | Merged | `6ca1b17` |
| Final test sheet | Merged, needs a final check at build time | `docs/FINAL_TEST.md`, `6ca1b17` |
| Debug banner | Answered, no code change | Shows only if the app is started by a command with a replay setting. Tapping the icon never does. Don't change the replay tool |

### Ready reverts, not merged
Merge only if the final phone test shows a Practise problem, and only on the user's say:
- `takeover/revert-model-load` `fb999d5`: undoes `be51948`, the Practise model load on a background thread.
- `takeover/revert-dashboard-save` `4c560676`: stops Practise saving to the dashboard and removes the sampling; the Dashboard screen and samples stay. Both passed the gate. Either may need updating to the newest `cpr-coach` first.

### Approval pages
- Colour page: https://claude.ai/artifact/P63RE6eRdDMqJgksdwJ1bM . Published from Vachana's account; if the user can't open it, republish `D:\projects\apk-builds\screens\vivid\` images from the user's account.
- Pictures page: https://claude.ai/artifact/VCRrK1CFovjSHD8Zqp1rjs , approved: all rows.
- Each page must show at the top what the user is approving and how to reply, such as "colours approved", or "pictures: approve 1, 2, 3, 5; redo 4". Open the page for the user and give its exact link.
- The earlier re-skin page, https://claude.ai/artifact/8RgEWwPV6Yo2MtRECEBxCW, is on Vachana's account. It's private, so the user's own account can't open it unless it's shared. Publish new pages from the user's account. All its images are in `D:\projects\apk-builds\screens\before` and `after`.

### Final build
`b3e66f7`, APK `D:\projects\apk-builds\b3e66f7.apk`, sha1 `debe7d84e51feda1d2dff54bf74a902f37a71156`, test sheet `cpr-coach/docs/FINAL_TEST.md`. Install runs by itself when the phone is plugged in and no session is running: `python -I D:\projects\apk-builds\boss-tools\install_when_ready.py b3e66f7 debe7d84e51feda1d2dff54bf74a902f37a71156`

### Build on the phone
CPH2467: see the install result in `docs/TAKEOVER_LOG.md`. Expected `b3e66f7` with capture on. The user runs `docs/FINAL_TEST.md` next. Start the app from the icon for the test.

### Button and panel depth, approved by the user at 10:33
Branch `takeover/t10-depth`, style only, on every screen except the Practise and Emergency camera screens. vivek-pc/codex-luna owns `ui/Theme.kt`. vivek-pc/antigravity takes screenshots to `D:\projects\apk-builds\screens\depth\`. Then a Sonnet review and contrast check. Send a before/after page link, don't wait, merge when checks pass, build, verify sha1, copy to apk-builds by hash, write READY TO INSTALL with a 2-minute look-only list, then wait.
The phone stays disconnected: no install and no adb against it until the user says install. This overrides the earlier automatic install.
Golden builds: only when the user says. On 'b3e66f7 passed': move ee682a1 with its own working install script to `D:\projects\zero_flux\golden\previous\ee682a1\`, put b3e66f7 in `golden\` with `install-golden.ps1`, `SHA1.txt`, `README.txt` updated, read-only, and test the script without the phone: it must stop with 'no phone'. On '<styling hash> passed': the same, moving b3e66f7 to `previous\b3e66f7\`. Never delete any golden or previous build.

### Styling build
`a729293`, `D:\projects\apk-builds\a729293.apk`, sha1 `bf410daddf4e64e070fb97743d334ae6d0482f85`. Merged. Installed on the CPH2467 at 10:59 after the user said install.

### Logo and spelling, asked by the user at about 11:00
Branch `takeover/t11-brand`, vivek-pc/codex-sol: 'CPR coach' mark top left on Home, and 'Practice' on screen in English. Merge when checks pass. Build, then wait for 'install'.

Newest build: `a99cd63`, sha1 `300e4a2f8793880b25b878257e497553a9460d30`, includes the logo and spelling; installed on the CPH2467 after the user said install.


## Known problems
The swarm's save step fails on some jobs, though no work is lost. During a weak uneven lean the app shows pause; demo with a steady firm lean, short, phone still. Translations are machine made and no person has checked them. Everything since ee682a1 has not been run through a real phone test.

## Still pending from me
- Rehearse the 2-minute demo: its timing is an estimate.
- Keep the app on a99cd63. Reinstall only with the golden command.
- I own "Person 4: Feedback & Emergency" for judge questions; `JUDGE_QA.md` has the answers.