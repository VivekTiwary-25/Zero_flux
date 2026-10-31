# v5 inputs: where each came from

- Footage frames (slide 3): D:\projects\zero_flux\cpr-coach\tools\traces\run-1031d\sheets\1793425778763_18.937-37.306.jpg (daylight run, 31 Oct 2026, newest run).
  Three cells cropped: 21.043 s elbow 173.1 deg, 28.375 s elbow 174.7 deg, 33.136 s elbow 179.9 deg. Drawn by tools\frame_sheet.py from the app's debug frames and session CSV.
- Home-made manikin: no separate photo found in zero_flux, the branches, Downloads/Pictures/Desktop, or the Claude Code logs. It is visible in the footage frames (cloth bundle on cardboard).
- Video proof of the app working: no video file found anywhere searched (zero_flux, D:\, user folders, logs). Left out.
- Crash story (slide 4): commit ce1f7f8 "cpr-coach: fix the mid-session crash, and cut camera load" (RateEstimator crashed on real motion, recording lost, pose capped near 15 fps at 640x480; "Heat and speed are not yet proven on a phone"), then 27d4314 LoadGovernor with thermal and battery logging. No evidence found of the phone overheating or shutting down, so the slide says "crashed", not "overheated".
- 596 commits: git rev-list --count origin/cpr-coach at 12:26. First commit 29 Oct 23:36, last 31 Oct 11:55.
- 399 tests (0 failed, 5 skipped): cpr-coach/docs/TAKEOVER_LOG.md, last row (11:09, a99cd63).
- Karnataka line: text and source (NewKerala, 10 July 2025) as given by the user.
