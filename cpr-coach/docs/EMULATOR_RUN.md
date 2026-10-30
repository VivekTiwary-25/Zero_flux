# Emulator run — 2026-10-30

## Setup

Fetched the latest `cpr-coach` branch into the reference copy, copied the three `tiny.en-*` Whisper assets from the read-only iTantra assets, set `local.properties` to the installed Android SDK, and built `assembleDebug` successfully. Installed and launched `com.zeroflux.cprcoach` only on AVD `itantra34` (`emulator-5554`); no real phone was targeted. The fetched reference copy did not contain `docs/EMULATOR_RUN.md`, so this requested run record was created here rather than copied from it.

## Walkthrough

- **Home:** opened directly in English; Emergency and Practise buttons, language chip and mic control were visible.
- **Language picker:** selected Hindi; the chip, Emergency and Practise labels changed to Hindi. Selected English again; Home labels returned to English.
- **Practise walkthrough:** opening Practise entered the camera screen. The camera `?` opened the walkthrough. Viewed all three cards: live feedback/two people; rescuer and holder roles; teal/amber meanings and Start. Start entered camera framing.
- **Camera/framing:** framing displayed “Point the camera at the rescuer” and a Start control that remained unavailable. The virtual camera showed a bright multicolour test pattern rather than a person, so framing readiness and pose feedback could not be exercised. Camera content was landscape within the portrait emulator window. The `?` opened walkthrough cards; Back returned to Home.
- **Emergency:** opened Emergency; Call 112 and Start compressions were visible. Started compressions and waited over 40 seconds. The displayed coaching text advanced from “Call 112. Put it on speaker.” to “Push hard and fast, with the beat.” The emulator run did not establish that audible speech or the metronome could be heard.
- **Call 112:** tapped Call 112; Android’s dialer keypad opened with `112` filled in. Did not press Call. Back returned through the dialer screens to CPR Coach.
- **Stop:** long-pressed “Hold to stop”; CPR Coach returned directly to Home.
- **Mic:** tapped Home’s mic control and left it silent. Its listening ring appeared, then disappeared after several seconds, consistent with auto-mute. No spoken input or recognized transcript was produced/confirmed.
- **Back and shutdown:** pressed Back on Home; Android launcher appeared. Shut down the AVD.

## Crashes and issues

No `FATAL EXCEPTION`, native fatal-signal, or CPR Coach ANR markers appeared in emulator logcat. No clipped or overlapping text was observed. The virtual-camera test pattern prevented any meaningful framing/readiness check; actual audio output was not verified. An emulator proves nothing about pose accuracy, audio on a real device, or real-phone behavior.
