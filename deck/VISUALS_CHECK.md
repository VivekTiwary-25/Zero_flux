# Visuals check (v3)

All art is hand-written SVG in `build2.js` (sources saved in `assets3/*.svg`), turned into 2x PNGs with sharp.
No AI-generated images, no photos, no faces (the head is a plain circle), no app screenshots.
Style: flat 3-4 px strokes, white and teal on transparent, no gradients, no glow. Amber is not used in the art.
Every placed drawing carries a small "Illustration" label.

Codex (Luna) was not used: there was not enough time before the 11:50 stop to check usage and test image output,
so the art was drawn as vector line art instead.

## Technique check: the three teaching points

| Drawing | Lower half of breastbone | Arms straight | Shoulders over hands | Result |
|---|---|---|---|---|
| `rescuer.png` (cover) | Not shown: a cushion stands in for the chest, so placement can't be judged | Yes: shoulder, elbow and wrist on one vertical line (x = 400) | Yes: shoulder joint directly above the hands (same x) | Pass for what it shows; it makes no placement claim |
| `rescuer_guide.png` (slide 9) | Not shown (same as above) | Yes, same geometry | Yes, plus a dashed vertical guide through shoulder and hands | Pass for what it shows |
| `chest.png` (slide 9) | Yes: breastbone runs y 120 to 330; its lower half (y 225 to 330) is teal; the stacked hands are centred at y 277, inside the lower half | n/a (top view) | n/a (top view) | Pass |

Slide 9 shows the two drawings together, so all three points appear on one slide. Nothing failed, so nothing was dropped.

## What the slide claims about the app (checked against v2 text)
- Arms straight: "The app measures this". v2 slides 5 and 10 say it measures elbow bend / elbow angle.
- Hand placement: "One camera can't see this". v2 slide 10 lists "Exact hand placement" under ONE CAMERA CAN'T.
- Shoulders over hands: "Not measured by the app". v2 slide 10's IT MEASURES list is rate, elbow angle, pauses, tracking quality.

## Chart numbers (slide 3)
Only the numbers given in the brief: India 9.8% / 1.4% (Bhat et al., Indian Heart Journal 2021, one hospital, 205 patients);
US 40.2% (AHA Heart Disease and Stroke Statistics 2023, over 356,000 arrests) / 9.1% (AHA, 2021 US data, EMS-treated adult
non-traumatic arrests). Title check: 9.8 / 40.2 = 0.24, about a quarter. The source line says the two are not measured the same way.

## Render and lint
- Every slide exported from PowerPoint at 1600x900 and looked at: no overflow, no overlaps, nothing stretched.
- lint_deck.py: 1 error, 10 warnings (same as the v2 baseline; the error is the deliberate dash on the rhythm-line gap, now slide 12).
- visual_check.py: 0 warnings, 3 info.
