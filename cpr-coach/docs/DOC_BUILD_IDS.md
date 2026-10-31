# Build ids and APK hashes named in the docs

Snapshot: every `cpr-coach/docs/*.md` file on branch `cpr-coach` at `adf9409` (60 files), read with `git archive` into `../ref-t261031-0533-fab7`. 24 of those files name a short id or APK hash; there are 55 occurrences, all listed below. Checked 2026-10-31. No app code was changed and no phone or Gradle was used.

Method: every 7-40 character hex token in every doc was extracted, then looked up with `git cat-file` and `git merge-base --is-ancestor <id> e1b489f`. `e1b489f` is the newest commit on the branch that changes app code; the three commits after it (`c8af1a7`, `5c53210`, `adf9409`) touch only `docs/`. In the table, **older** means the id resolves to an ancestor of `e1b489f`, and **same build** means `e1b489f` itself.

Two named ids do not resolve here: `4e46cfd8` is not in the fetched history, and `385ca73d1a95` is an AOSP framework commit cited as an external source, not a commit of this repo. `3779864a6984` is a truncated APK sha1, not a commit.

## Ids

| Doc | Line | Id | What the doc says it is | Vs e1b489f |
|---|---:|---|---|---|
| `CALL_AUDIO_NOTES.md` | 7 | `385ca73d1a95` | AOSP frameworks/base commit cited for `AudioDeviceBroker.topCommunicationRouteClient()` call routing | external, not this repo |
| `CLAIMS_CHECK.md` | 4 | `4e46cfd8` | `FETCH_HEAD` of branch cpr-coach that the claims check was made against | not in repo |
| `CLIP_CHECK.md` | 14 | `7e79f9fde6cd12726a54a9e10bfc1b0f74e1e2e5` | cpr-coach source commit read by `git archive` for the clip transcription check | older |
| `CLIP_CHECK_0400.md` | 5 | `f99a1bca04143dd431b5db2ea4f333c5025082cf` | cpr-coach source for the 04:04 clip check, extracted to `../ref-t261031-0356-b784` | older |
| `CLIP_COVERAGE.md` | 13 | `f9481181cb7ec1732552fd4987c3b591c80822ac` | cpr-coach commit whose clip coverage was checked | older |
| `GUARD_STUDY.md` | 9 | `967e155b99f078e8713877db667854feed47a780` | origin/cpr-coach reference commit fetched and archived for the guard study | older |
| `GUARD_STUDY.md` | 15 | `967e155b99f078e8713877db667854feed47a780` | the same reference commit, passed as `--reference` in the reproduction command | older |
| `LABELS_CHECK.md` | 4 | `f4b59a1fc6a6048812d5bcf6663aac70c55e870a` | cpr-coach source commit for the labels.csv check | older |
| `NEXT_TEST_0700.md` | 3 | `e1b489f` | the build under test in the short phone test sheet | same build |
| `NEXT_TEST_0700.md` | 3 | `3779864a6984` | APK sha1 of build e1b489f | sha1 of same build |
| `NIGHT_DECISIONS.md` | 6 | `f99a1bc` | commit that made posture limits relative to the rescuer's own first good pushing | older |
| `NIGHT_DECISIONS.md` | 7 | `1c3aa4d` | commit for the rule that a phrase may repeat only after 3 s clear | older |
| `NIGHT_DECISIONS.md` | 8 | `a904d2d` | commit behind rate outranking posture in Emergency | older |
| `NIGHT_DECISIONS.md` | 9 | `e50a11a` | commit where rejected frames keep "person in view" for 1.5 s | older |
| `NIGHT_DECISIONS.md` | 10 | `7e09b77` | commit that installed the fresh English countdown take | older |
| `NIGHT_DECISIONS.md` | 10 | `ccb61e0` | commit that gave Malayalam and Odia the same new countdown take | older |
| `NIGHT_DECISIONS.md` | 13 | `4dfb3b6` | pause-rule commit (Keep going waits for stillness and re-arms) | older |
| `NIGHT_DECISIONS.md` | 13 | `63f3fe4` | pause-rule commit (per-phrase steady times; Keep going survives a short LOST) | older |
| `NIGHT_DECISIONS.md` | 14 | `63f3fe4` | commit that added per-phrase steady times | older |
| `NIGHT_DECISIONS.md` | 15 | `f0d6188` | commit that holds a confirmed lean through a short side-on flicker | older |
| `NIGHT_DECISIONS.md` | 16 | `63f3fe4` | "Build 63f3fe4 is built and installs automatically as soon as the phone is seen again" | older |
| `NIGHT_DECISIONS.md` | 17 | `88234d0` | commit that stops rejected frames republishing a stale rate, elbows or posture | older |
| `NIGHT_DECISIONS.md` | 18 | `fcb4cc6` | commit that made scorecard pauses tolerate moving flicker | older |
| `NIGHT_DECISIONS.md` | 19 | `e1b489f` | "Install target is now e1b489f" | same build |
| `NIGHT_DECISIONS.md` | 19 | `63f3fe4` | a build replaced by e1b489f; never reached the phone | older |
| `NIGHT_DECISIONS.md` | 19 | `fcb4cc6` | a build replaced by e1b489f; never reached the phone | older |
| `RATE_ALT_METHOD.md` | 5 | `f1abec2519e232ca020e6743edc241fd92e10f31` | cpr-coach reference revision for the alternative rate-method replay | older |
| `RATE_BENT_LEAN.md` | 11 | `e50a11a985738d4c6aa90849b13b6b0f9dac63a2` | cpr-coach reference revision for the bent/lean rate study; the doc says no app code was changed | older |
| `RATE_CHANGE_NOTE.md` | 3 | `53761f4` | cpr-coach revision the rate-change note was confirmed against | older |
| `RATE_FPS_STUDY.md` | 3 | `53761f456da7` | reference revision for the frame-rate simulation | older |
| `RATE_REAL_2.md` | 7 | `6892ced` | source revision for the replay of all ten CSVs | older |
| `REAL_TRACE_1.md` | 13 | `738a27399c090e4f58db6fe8945de5a1b4b528bc` | source revision inspected for the first real wrist trace (named twice: code span and link) | older |
| `RECORDINGS_13_15.md` | 3 | `4e6cfd8c20f542f3057465b58990675574545b14` | cpr-coach commit the recordings 13-15 facts were measured from | older |
| `REPLAY_13_15.md` | 3 | `64de3c3ccac3693c58ab44a686ce21edfc3ba3b0` | tested commit (origin/cpr-coach) for the 13-15 replay | older |
| `REVIEW_COACH_VOICE.md` | 1 | `bf38170` | the commit reviewed, in the title | older |
| `REVIEW_COACH_VOICE.md` | 3 | `bf38170` | commit that changed `CoachPolicy.kt`, the review scope | older |
| `REVIEW_COACH_VOICE.md` | 76 | `bf38170` | commit said to coach while paused with bent elbows or leaning | older |
| `REVIEW_COACH_VOICE_2.md` | 3 | `1c3aa4d` | one of the commits after which `CoachPolicy.kt` was reviewed | older |
| `REVIEW_COACH_VOICE_2.md` | 3 | `4dfb3b6` | one of the commits after which `CoachPolicy.kt` was reviewed | older |
| `REVIEW_COACH_VOICE_2.md` | 3 | `63f3fe4` | one of the commits after which `CoachPolicy.kt` was reviewed | older |
| `REVIEW_LANDMARK_GUARD.md` | 3 | `c578e2f` | origin/cpr-coach commit reviewed (named again in the `git show` command) | older |
| `REVIEW_POSTURE_BASELINE.md` | 3 | `f99a1bc` | commit introducing the personal-baseline posture judge, under review | older |
| `REVIEW_POSTURE_BASELINE.md` | 202 | `f99a1bc` | commit before which `LEAN_ENTER` was 0.28 | older |
| `REVIEW_POSTURE_HOLD.md` | 3 | `f0d6188921cd56cdf4fa8bba20c1bbdd4591edbc` | commit whose `PostureJudge.kt` hold is reviewed | older |
| `REVIEW_SEEN_HOLD.md` | 3 | `e50a11a985738d4c6aa90849b13b6b0f9dac63a2` | commit reviewed together with its parent diff | older |
| `REVIEW_SEEN_HOLD.md` | 3 | `e50a11a` | short form of the same commit in the `git show` command | older |
| `STATUS_DRAFT.md` | 10 | `e50a11a` | source commit for the ghost-wrist rejection row | older |
| `STATUS_DRAFT.md` | 10 | `c578e2f` | source commit for the ghost-wrist rejection row | older |
| `STATUS_DRAFT.md` | 11 | `6cf3409` | source commit for the bent-elbows row | older |
| `STATUS_DRAFT.md` | 12 | `d35f114` | source commit for the lean row | older |
| `STATUS_DRAFT.md` | 13 | `4dfb3b6` | source commit for the pause / Keep going row | older |
| `STATUS_DRAFT.md` | 13 | `1c3aa4d` | source commit for the pause / Keep going row | older |
| `STATUS_DRAFT.md` | 14 | `4100605` | source commit for the machine clip-check row | older |
| `STATUS_DRAFT.md` | 16 | `a904d2d` | source commit for the Emergency instruction word row | older |
| `STATUS_DRAFT.md` | 16 | `17d20eb` | source commit for the Emergency instruction word row | older |

Hex tokens that are not ids are left out: the three device uptime values in `LABELS_CHECK.md:8` (`226737341`, `231787566`, `231874055`), the experiment seed in `POSTURE_GEOMETRY.md:5` (`20261031`), the two debug traces in `REVIEW_LANDMARK_GUARD.md:170,174` (`38529596`, `00004172329113`), and the SHA-256 of the input trace in `REAL_TRACE_1.md:12` (a 64-character file hash, not a git hash or an APK sha1).

## Docs that send the reader to a build older than e1b489f

Criterion: the doc names a build and tells the reader to install it or to test it on a phone. Under this criterion:

| Doc | Where | Build named | Older? | Note |
|---|---|---|---|---|
| `NIGHT_DECISIONS.md` | line 16 | `63f3fe4` | **yes** | **Marked.** "Build 63f3fe4 is built and installs automatically as soon as the phone is seen again." Line 19 of the same file supersedes it: "Install target is now e1b489f ... Replaces 63f3fe4 and fcb4cc6, which never reached the phone". The current instruction in the file is e1b489f; the older install line is left in place. |
| `NEXT_TEST_0700.md` | line 3 | `e1b489f` | no | The phone test sheet is written for the newest build (`e1b489f`, APK sha1 `3779864a6984`), so it is current. |
| `PHONE_TEST.md` | Install section, line 23 | none | cannot tell | Tells the reader to install and run a full phone checklist, but names no build. Its last edit (`7d2b25e`) predates e1b489f, so it cannot be tied to any build. |
| `INSTALL.md` | steps 3-7 | none | cannot tell | Build and install steps, no build id. Last edit `86a1280`, older than e1b489f. |
| `DEMO_SCRIPT.md` | line 10 | none | cannot tell | "Install the debug build on both", no build id. Last edit `c19c58e`, older than e1b489f. |
| `DEMO_TEST_5MIN.md` | whole file | none | cannot tell | Live behaviour check with no build id. Last edit `401f8c8`, older than e1b489f. |
| `ACCURACY_TEST.md` | whole file | none | cannot tell | Counted-run procedure with no build id. Last edit `5c3680a`, older than e1b489f. |
| `CONTROLLED_TEST.md` | whole file | none | cannot tell | Controlled test procedure with no build id. Last edit `c666bae`, older than e1b489f. |
| `FILMING.md` | whole file | none | cannot tell | Real-person filming procedure with no build id. Last edit `36485a3`, older than e1b489f. |

So `NIGHT_DECISIONS.md` is the only doc that names a build older than e1b489f together with an install action, and it corrects itself later in the same file. Its current install instruction (line 19) and the only build-specific test sheet (`NEXT_TEST_0700.md`) both point at e1b489f, so no doc's live instruction sends a reader to an older build. The docs with install or test steps but no build id cannot be dated from their contents. `EMULATOR_RUN.md` records an emulator install and launch of an unnamed build; it gives no build id and no instruction to test.

Docs that name an older revision next to a test, but do not install an APK, are reports or study reproductions: `REPLAY_13_15.md` ("Tested commit `64de3c3`", older), `LABELS_CHECK.md` (source `f4b59a1`, older), `RECORDINGS_13_15.md` (source `4e6cfd8`, older), `RATE_REAL_2.md` (source `6892ced`, older), `RATE_BENT_LEAN.md` (replay against `e50a11a`, older), `RATE_FPS_STUDY.md` and `RATE_CHANGE_NOTE.md` (`53761f4`, older), `RATE_ALT_METHOD.md` (`f1abec2`, older), `GUARD_STUDY.md` (study `--reference 967e155`, older), and the `CLIP_CHECK*.md` / `CLIP_COVERAGE.md` machine checks (`7e79f9f`, `f99a1bc`, `f948118`, older). `CLAIMS_CHECK.md` checked the docs against `4e46cfd8`, which does not resolve here, so its revision cannot be placed against e1b489f. If "test" is read to include those source replays, they are the additional marked docs; none of them is a phone build.
