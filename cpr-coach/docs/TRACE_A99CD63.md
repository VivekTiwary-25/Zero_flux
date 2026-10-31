Trace report, build a99cd63 (run-1031d). Replay checkout was aa712ef (origin/cpr-coach tip), not a99cd63. Report only; no code changed.

Sessions (start id: length, frames, rate, my guess of mode; guess only):
1793425778763: 64.0 s, 1202 frames, 18.8 fps, Emergency or Practise (guess: Emergency, fits call/hands/push lines and AED line at 30 s)
1793426010841: 12.6 s, 81 frames, 6.4 fps, aborted start (guess Emergency, only 3 lines)
1793426181939: 47.3 s, 930 frames, 19.7 fps (guess Emergency)
1793426269128: 36.7 s, 696 frames, 19.0 fps (guess Emergency)
1793426342062: 48.5 s, 945 frames, 19.5 fps (guess Emergency)
1793426727003: 13.7 s, 288 frames, 21.0 fps, no frames folder (guess Emergency)
Latency median 57-77 ms, p95 81-115 ms, max 104-320 ms. Fine. Phone thermal status 3 all session, battery 38.5-39.4 C.

Odd things:
- Frame rate is about 21 in the first 10 s and sags to 14-18 later in most sessions (s3 14.5 in last bucket, s4 13.7, s1 17.5). Recording 16 averaged 20.9.
- Session 2: no pose for 4.3-8.3 s (a 7.7 s gap in frames at 4.4 s). Pictures show a finger over the lens, then the phone swung round at the end. Looks like setup fumbling, not a real test.
- Session 1: PAUSED 30-38 s although pictures show steady pushing; replay played Keep going at 32 s (false). Then rate 124-140 with Slow down at 43.5 s, then PAUSED 47-55 s again with another Keep going at 49.5 s. Same rate-dropout pattern as RECORDING_16.
- Sessions 3-5: repeated short PAUSED holes of 2-6 s while pushing, each followed by Keep going at times (s3 24 s, 41.5 s; s4 5.75 s; s5 5.75 s, 42.5 s). Keep going at 5.75 s in s4 and s5 is likely a start-up false cue.
- Rate swings: 80-85 right after restart (s3 at 4 s, s5 at 26-31 s) then 125-141 (Slow down); big jumps, possibly estimator, not real.
- s5 ELBOWS_BENT shown 39-42 s while PAUSED; no elbow line spoken. Unchecked.
- No lost-pose stretch over 1 s except session 2. No stacked lines and no false rate lines in any Emergency replay.
- Crash pattern: none visible. Every CSV ends in a normal row sequence; sessions 2 and 6 are just short (12.6 s and 13.7 s) and could be user stops; I cannot tell from the CSV.

