# 2-minute look-only check

For build **a729293** (APK sha1 `bf410daddf4e64e070fb97743d334ae6d0482f85`), CPH2467. Alone, no pushing. Do it once with the phone in dark mode, once in light. Don't call anyone: cancel the dialer every time it opens. Start the app from its icon.

| Seconds | Screen | Do | Look for | OK? |
|---|---|---|---|---|
| 15 | Home | Tap Emergency contact, close it. Change language, change back | Buttons look raised, with a glow and a lighter top. They squish a little when pressed. Panels: frosted in dark, soft raised in light. Text readable | [ ] |
| 20 | CPR guide | Tabs 1-5, Previous, Next. On step 2 tap the (?) and close it | Next and (?) are easy to read. Pictures unchanged | [ ] |
| 15 | Dashboard | Scroll to the end, open a session, Back | Panels and tags readable, no broken words | [ ] |
| 20 | Practise | Open it, Skip through the walkthrough and Roles, reach camera setup, Back | Walkthrough buttons raised. **Camera setup looks as it did on b3e66f7** | [ ] |
| 15 | Practise scorecard | If one shows after a short Stop, tap its buttons | Same raised buttons, readable text | [ ] |
| 20 | Emergency | Open it. Look, then hold Stop | **Camera screen looks as it did on b3e66f7**, including Call 112. Handover screen has the new raised buttons | [ ] |

Nothing should have moved or changed size on any screen.

Rollback: `powershell -ExecutionPolicy Bypass -File D:\projects\zero_flux\golden\install-golden.ps1`, or `D:\projects\apk-builds\b3e66f7.apk`.
