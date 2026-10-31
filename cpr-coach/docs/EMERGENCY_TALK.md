# Emergency speech timing

## Change

Emergency builds CoachPolicy with rate steadiness 1.5 s, cooldown 4 s, and praise interval 10 s. New policy options enable correction-clear Good, repeated Good, continuous observation while playback is busy, and a relock guard counted inside rate steadiness. Their defaults preserve Practise.

EmergencyHost selects a 0.5 s rate-word hold through EmergencyCoach.rateHoldMs. The existing holdMs default remains 1 s for posture and existing standalone consumers; the diagnostic selects the same rate setting as the host. LEAVE_POSTURE_MS remains 500 ms. No pace-counting, protected engine, dialer, call-button, or screen-layout changes.

EmergencyCoach.nextClip chooses corrections first, then alternates existing keep_going and em_push clips while pushing after at least 8 s of completed-clip silence. Playback supplies its actual completion timestamp and permits speech only with no active clip or queued scripted step. Fillers do not enter CoachPolicy, change lastPhrase, or acknowledge a recovery cue. A separate Emergency line clock includes fillers in the 4 s adaptive-speech cooldown.

## Why voice was unreliable

EmergencyHost previously called nextCorrection only while voiceIdle. Policy could not observe rate/posture changes, PAUSED reads, or recovery rearming during any clip. A correction beginning inside a clip started its steady timer only after playback ended; a condition changing during a clip was invisible to repeat/recovery bookkeeping. The Kannada opening occupies 12.785 s, versus 7.547 s in English.

The host now observes policy every 250 ms. canSpeak gates emission, not observation; no correction is queued for later playback. The current reading must still want it when playback becomes available. Scripted opening/AED/Continue/Swap clips retain queue priority and never overlap adaptive speech.

The old PAUSED relock guard excluded rate candidates for 1 s before starting the 3 s steady timer. Emergency now starts its 1.5 s timer immediately on BEATING, but still forbids rate speech until the 1 s guard expires. These clocks overlap rather than add. A regression with PAUSED at 1.000 s and BEATING from 1.250 s speaks at 2.750 s, not 3.500 s. Practise retains the old guard and defaults.

Good previously waited for praiseEveryMs after a correction, then steadiness; lastPhrase filtering also prevented consecutive Good lines. Emergency acknowledges a cleared spoken faster/slower/elbows/shoulders correction at the next eligible idle read, respecting cooldown and requiring an in-range beat with no remaining correction. Subsequent on-pace Good lines use a 10 s start-to-start interval without an additional steady wait.

## Replay method

Baseline: fetched origin/cpr-coach e663bcf9 before production edits. Final upstream fetch: 14d9e513, whose Practise gate and locked baselines were integrated without committing or switching branches.

EmergencyTimelineTest replayed recordings 10–16 through real LiveAnalyzer, EmergencyCoach, and EmergencySequence at 250 ms, before and after. CPR_TRACE selected all seven files; CPR_CLIP_LANGUAGE=en,kn selected actual WAV lengths. The replay models EmergencyPlayback's single-clip queue and callbacks, with audio ready at recording start; no device/model-loading latency is included. Times below are recording-relative seconds. Lines/minute includes opening steps, reminders, recovery, corrections, praise, and fillers; denominator is the full recording duration.

PowerShell from cpr-coach:

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:CPR_TRACE=((10..16 | ForEach-Object { "tools/testdata/real-cph2467-$_.csv" }) -join ',')
$env:CPR_CLIP_LANGUAGE='en,kn'
.\gradlew.bat testDebugUnitTest --tests '*EmergencyTimelineTest*' --rerun-tasks
Remove-Item Env:CPR_TRACE
.\tools\check_practise.ps1
```

The comma-separated trace batch must target EmergencyTimelineTest: the separate Practise TraceTimelineTest accepts only one path. Full baseline/after diagnostics are under app/build/emergency-before/ and app/build/emergency-timeline-real-cph2467-N-LANGUAGE.txt. Each includes exact reads, screen stretches, spoken start/end times, previous clip completion, and silence.

## Spoken lines per minute

| Recording | English lines before → after | English /min before → after | Kannada lines before → after | Kannada /min before → after |
|---|---:|---:|---:|---:|
| 10 | 6 → 9 | 7.284 → 10.925 | 6 → 8 | 7.284 → 9.711 |
| 11 | 5 → 7 | 9.252 → 12.953 | 5 → 6 | 9.252 → 11.103 |
| 12 | 7 → 10 | 8.037 → 11.481 | 6 → 9 | 6.889 → 10.333 |
| 13 | 7 → 9 | 9.449 → 12.148 | 6 → 9 | 8.099 → 12.148 |
| 14 | 7 → 9 | 6.647 → 8.546 | 7 → 8 | 6.647 → 7.596 |
| 15 | 9 → 14 | 7.557 → 11.756 | 9 → 12 | 7.557 → 10.077 |
| 16 | 12 → 20 | 5.841 → 9.735 | 12 → 18 | 5.841 → 8.761 |

## FASTER / SLOWER screen onset

Screen results are identical for both audio languages. Shared events arrive 0.5 s sooner except recording 12 at 29.25 s, where the unchanged posture-exit rule already permitted the transition after 0.5 s.

| Recording | Word | Before onset(s) | After onset(s) | Sooner |
|---|---|---|---|---|
| 10 | SLOWER | 37.50 | 37.00 | 0.50 |
| 11 | neither | — | — | — |
| 12 | SLOWER | 8.75, 29.25, 37.75 | 8.25, 29.25, 37.25 | 0.50, 0, 0.50 |
| 13 | FASTER | 10.00 | 9.50 | 0.50 |
| 14 | neither | — | — | — |
| 15 | FASTER | 37.50 | 4.50, 37.00 | new early event; 0.50 for shared event |
| 16 | FASTER | — | 98.75 | new brief event |

The lower screen hold exposes two additional short rate events: recording 15 shows FASTER from 4.50–6.00 s; recording 16 from 98.75–99.00 s. Neither is spoken. These are measured detector events, not independently established clinical ground truth; the screen still has a faster response/brief-word tradeoff.

## Rate voice onset

Only faster/slower lines are listed. A dash means no matching spoken rate line, not zero delay. Recordings 11, 14, and 16 have none in either language, before or after.

| Recording / event | English before → after | English sooner | Kannada before → after | Kannada sooner |
|---|---|---|---|---|
| 10 SLOWER | — → 38.00 | newly spoken | — → 38.00 | newly spoken |
| 12 first SLOWER | 10.75 → 9.25 | 1.50 | — → 13.00 | newly spoken after opening |
| 12 later SLOWER | — → 38.25 | newly spoken | — → 38.25 | newly spoken |
| 13 FASTER | 12.00 → 10.50 | 1.50 | 16.00 → 13.00 | 3.00 |
| 15 FASTER | — → 38.00 | newly spoken | — → 38.00 | newly spoken |

All 28 before/after language-recording timelines report zero stacked clips and zero rate voice lines inconsistent with the current BEATING/off-target reading. This checks engine consistency, not whether the recorded rate is medically correct. The replay rejects speech while a clip is active or queued, including opening, AED, Continue, and Swap. The new brief screen events above do not pass the voice hold.

## Kannada silence, measured from clip END

Durations below are WAV sample counts rounded up to playback milliseconds, not guessed text timings.

| Clip | English seconds | Kannada seconds |
|---|---:|---:|
| em_call | 2.601 | 4.314 |
| em_hands | 2.171 | 3.614 |
| em_push | 2.775 | 4.857 |
| keep_going | 1.161 | 1.465 |
| good | 1.231 | 2.629 |
| em_aed | 2.798 | 5.253 |
| em_continue | 2.102 | 4.126 |
| em_swap | 2.346 | 4.609 |

The recordings contain eight English fillers and two Kannada fillers. The Kannada fillers start at recording 13 / 23.750 s (previous clip ended 15.640 s; silence 8.110 s) and recording 16 / 78.250 s (previous clip ended 66.965 s; silence 11.285 s). Recovery keep_going clips during stillness are not counted as fillers. No recorded filler violates the 8 s rule.

Those recordings do not reach a second Kannada filler, so EmergencyTalkTest additionally runs sustained moving/no-locked-rate input with actual English and Kannada assets, including the opening lengths. This exercises alternating fillers without periodic Good preempting them:

| Language / filler | Start | Previous clip end | Silence | Clip duration |
|---|---:|---:|---:|---:|
| en keep_going | 15.750 | 7.547 | 8.203 | 1.161 |
| en em_push | 25.000 | 16.911 | 8.089 | 2.775 |
| en keep_going | 36.000 | 27.775 | 8.225 | 1.161 |
| kn keep_going | 21.000 | 12.785 | 8.215 | 1.465 |
| kn em_push | 30.500 | 22.465 | 8.035 | 4.857 |
| kn keep_going | 43.500 | 35.357 | 8.143 | 1.465 |
| kn em_push | 53.000 | 44.965 | 8.035 | 4.857 |
| kn keep_going | 66.000 | 57.857 | 8.143 | 1.465 |

The Kannada em_push starting at 30.500 s ends at 35.357 s. The next filler starts at 43.500 s: 13 s after its start, with 8.143 s of actual silence. An 8 s start-based clock would have restarted at 38.500 s instead. Assertions require every filler to leave 8.000–8.249 s silence on the 250 ms polling clock and verify alternation.

## Verification and test changes

- Final check_practise.ps1: 372 tests; 367 passed, 0 failed, 5 skipped. All seven Practise recordings byte-identical to ee682a1; protected files, default settings, existing Practise tests, recordings, and locked baseline bytes unchanged.
- EmergencyTimelineTest changed to mirror continuous observation, select the host's rate hold, and expose real English/Kannada clip timing for before/after evidence.
- New EmergencyTalkTest covers rate/posture hold separation, observation during busy playback, stale-correction rejection, relock timing, all four correction-clear Good paths, periodic praise, actual-WAV filler silence/alternation, busy/lost/still suppression, and recovery after a keep_going filler.
- No other existing tests changed, including PauseVoiceReplayTest and CoachReplayTest.
- Importing upstream baseline files initially converted their bytes to CRLF before their new -text attributes took effect. Restoring the files directly from origin/cpr-coach corrected that setup issue; the locked hashes and replay now pass. No baseline was regenerated.
- No phone, adb, installation, or on-device audio check. Android callback/device latency remains unmeasured. No commits, pushes, or branch switches, per the task's final rules.
