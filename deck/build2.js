// CPR Coach deck v2 — built to DECK.md (anti-slop rules), checked with the PowerPoint skill's linter.
const pptxgen = require("pptxgenjs");
const sharp = require("sharp");
const fs = require("fs");
let applyTheme = async () => console.log("apply_theme.js not found: theme colours fall back to Office defaults for scheme colours");
try { ({ applyTheme } = require(process.env.APPLY_THEME || "./apply_theme.js")); } catch (e) {}

const HEX = { ink: "0E1418", text: "EEF2F5", muted: "93A3B5", hair: "2A3642", teal: "2DD4BF", amber: "FBBF24", red: "FF3B5C" };
const THEME = {
  name: "CPR Coach Ink",
  headFontFace: "Cambria",
  bodyFontFace: "Calibri",
  colors: { dk1: HEX.ink, lt1: HEX.text, dk2: "151D23", lt2: HEX.muted, accent1: HEX.teal, accent2: HEX.red, accent3: HEX.amber, accent4: HEX.hair, accent5: "1A242C", accent6: "5EEAD4", hlink: "5EEAD4", folHlink: HEX.muted },
};
const MONO = "Consolas", INDIC = "Nirmala UI", HEAD = "Cambria";
const W = 13.333, M = 0.75, CW = W - 2 * M;

// ---------- the signature: push rhythm line ----------
// segments: [{type:'flat'|'wave'|'gap', len(px), color, period(px), amp(0..1)}]
function rhythmSvg(w, h, segs) {
  const b = h / 2;
  let x = 0, out = "";
  for (const s of segs) {
    if (s.type === "gap") { x += s.len; continue; }
    let d = `M${x.toFixed(1)} ${b}`;
    for (let i = 1; i <= s.len; i += 2) {
      const xx = x + i;
      let y = b;
      if (s.type === "wave") {
        const ph = (i % s.period) / s.period; // push down fast, recoil slower
        const v = ph < 0.42 ? Math.sin((ph / 0.42) * Math.PI / 2) : Math.cos(((ph - 0.42) / 0.58) * Math.PI / 2);
        y = b + (s.amp * h * 0.45) * (v * 2 - 1) * (s.fadeIn ? Math.min(1, i / (s.period * 1.5)) : 1);
      } else if (s.type === "flat") {
        y = b + Math.sin(i * 0.9) * 0.6 + Math.sin(i * 0.23) * 0.8;
      }
      d += ` L${xx.toFixed(1)} ${y.toFixed(1)}`;
    }
    out += `<path d='${d}' fill='none' stroke='#${s.color}' stroke-width='${s.stroke || 4}' stroke-linecap='round' stroke-linejoin='round' ${s.opacity ? `stroke-opacity='${s.opacity}'` : ""}/>`;
    x += s.len;
  }
  return `<svg xmlns='http://www.w3.org/2000/svg' width='${w}' height='${h}'>${out}</svg>`;
}
async function line(file, w, h, segs) {
  await sharp(Buffer.from(rhythmSvg(w, h, segs))).png().toFile(file);
}

(async () => {
  fs.mkdirSync("assets2", { recursive: true });
  // 150 px per inch. 110/min at an implied 2.4 s per inch-ish: period 66px.
  await line("assets2/cover.png", 2000, 150, [
    { type: "flat", len: 760, color: HEX.muted, opacity: 0.55 },
    { type: "wave", len: 1240, color: HEX.teal, period: 66, amp: 0.9, fadeIn: true },
  ]);
  await line("assets2/flat.png", 1770, 60, [{ type: "flat", len: 1770, color: HEX.muted, opacity: 0.6 }]);
  await line("assets2/pipeline.png", 1770, 120, [{ type: "wave", len: 1770, color: HEX.teal, period: 66, amp: 0.55, stroke: 3, opacity: 0.55 }]);
  await line("assets2/gap.png", 1770, 110, [
    { type: "wave", len: 700, color: HEX.teal, period: 66, amp: 0.8 },
    { type: "gap", len: 370 },
    { type: "wave", len: 700, color: HEX.teal, period: 66, amp: 0.8 },
  ]);
  await line("assets2/close.png", 1150, 110, [
    { type: "wave", len: 380, color: HEX.amber, period: 96, amp: 0.8 },
    { type: "wave", len: 770, color: HEX.teal, period: 66, amp: 0.8 },
  ]);

  const pres = new pptxgen();
  pres.layout = "LAYOUT_WIDE";
  pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
  pres.title = "CPR Coach — Anvathon 2026";
  pres.author = "Team Zero Flux";
  const C = pres.SchemeColor;
  const TEXT = C.background1, MUTED = C.background2, TEAL = C.accent1, RED = C.accent2, AMBER = C.accent3, HAIR = C.accent4;

  pres.defineSlideMaster({
    title: "INK",
    background: { color: HEX.ink },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: M, y: 0.6, w: CW, h: 1.5, fontFace: HEAD, fontSize: 40, bold: true, color: TEXT, margin: 0, valign: "top", align: "left" }, text: "" } },
    ],
    slideNumber: { x: 12.05, y: 6.92, w: 0.55, h: 0.3, fontFace: MONO, fontSize: 12, color: MUTED, align: "right" },
  });
  pres.defineSlideMaster({
    title: "INK_BARE",
    background: { color: HEX.ink },
    objects: [{ placeholder: { options: { name: "title", type: "title", x: M, y: 1.2, w: 8, h: 1.4, fontFace: HEAD, fontSize: 80, bold: true, color: TEXT, margin: 0, valign: "top", align: "left" }, text: "" } }],
  });

  const tb = (s, text, o) => s.addText(text, { isTextBox: true, margin: 0, valign: "top", fontSize: 20, color: TEXT, ...o });
  const img = (s, path, x, y, w, h, alt = "") => s.addImage({ path, x, y, w, h, altText: alt });
  const hair = (s, x, y, w, h) => s.addShape(pres.shapes.LINE, { x, y, w, h, line: { color: HAIR, width: 1 } });
  const slide = (title, section, master = "INK") => { const s = pres.addSlide({ masterName: master, sectionTitle: section }); s.addText(title, { placeholder: "title" }); return s; };
  const source = (s, t) => tb(s, t, { x: M, y: 6.92, w: 10.6, h: 0.3, fontSize: 12, color: MUTED });

  // 1 COVER
  pres.addSection({ title: "Opening" });
  {
    const s = slide("CPR Coach", "Opening", "INK_BARE");
    tb(s, "A phone that watches your CPR and tells you what to fix.", { x: M, y: 2.75, w: 7.6, h: 1.0, fontSize: 28, color: MUTED });
    img(s, "assets2/cover.png", 0, 4.1, W, 1.0, "A flat line that turns into a steady push rhythm");
    tb(s, "110 /min", { x: 11.1, y: 3.65, w: 1.5, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: TEAL, align: "right" });
    tb(s, [{ text: "Team Zero Flux", options: { bold: true, color: TEXT } }, { text: "   Vivek · Pratham · Prabal · Moksh", options: { color: MUTED } }], { x: M, y: 6.3, w: 7.0, h: 0.4, fontSize: 18 });
    tb(s, "ANVATHON 2026 · OPEN INNOVATION", { x: 8.0, y: 6.34, w: 4.58, h: 0.35, fontFace: MONO, fontSize: 14, color: MUTED, align: "right" });
    s.addNotes("CPR Coach is a phone app that watches you do CPR on a practice cushion and tells you, out loud, what to fix. The line on screen is the push rhythm the app reads: flat when nobody is pushing, steady at 110 a minute when someone is.");
  }

  // 2 BIG NUMBER
  pres.addSection({ title: "Problem" });
  {
    const s = slide("Only 1 in 10 people who collapsed got CPR from someone nearby", "Problem");
    tb(s, "9.8%", { x: M - 0.05, y: 2.3, w: 6.0, h: 2.6, fontFace: MONO, fontSize: 150, bold: true, color: AMBER, fit: "none" });
    tb(s, [
      { text: "of 205 cardiac-arrest patients at one Indian hospital got CPR from a bystander.", options: { breakLine: true } },
      { text: " ", options: { breakLine: true, fontSize: 10 } },
      { text: "1.4% ", options: { bold: true, fontFace: MONO } },
      { text: "left hospital alive.", options: {} },
    ], { x: 7.0, y: 2.75, w: 5.55, h: 1.9, fontSize: 24 });
    img(s, "assets2/flat.png", M, 5.45, CW, 0.4, "A flat line: nobody is pushing");
    tb(s, "0 /min", { x: 11.3, y: 5.02, w: 1.28, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: MUTED, align: "right" });
    source(s, "Bhat et al., Indian Heart Journal, 2021 · one tertiary hospital, 205 patients");
    s.addNotes("In a 2021 Indian Heart Journal study of 205 cardiac-arrest patients at one hospital, fewer than one in ten got CPR from anyone nearby, and 1.4 percent left hospital alive. The American Heart Association says immediate CPR can double or triple the chance of survival. The flat line is what most of these patients got.");
  }

  // 3 TWO NUMBERS
  {
    const s = slide("Under 2 in 100 people say they know CPR, and the skill fades in months", "Problem");
    tb(s, "<2%", { x: M - 0.05, y: 2.45, w: 5.4, h: 1.9, fontFace: MONO, fontSize: 120, bold: true, color: AMBER, fit: "none" });
    tb(s, "of about 1 lakh people surveyed in 20 Indian cities said they knew CPR", { x: M, y: 4.45, w: 5.2, h: 1.1, fontSize: 22 });
    hair(s, 6.67, 2.6, 0, 3.0);
    tb(s, [{ text: "3", options: { fontSize: 120 } }, { text: " months", options: { fontSize: 40 } }], { x: 7.2, y: 2.45, w: 5.4, h: 1.9, fontFace: MONO, bold: true, color: AMBER, fit: "none" });
    tb(s, "is how soon CPR skills can start to fade after a course", { x: 7.25, y: 4.45, w: 5.2, h: 1.1, fontSize: 22 });
    source(s, "Lybrate survey, 2016 · AHA 2025 Guidelines, Part 12: Resuscitation Education Science");
    s.addNotes("Very few people say they know CPR, and the ones who learn it lose the skill fast: the 2025 AHA guidelines say skills can start fading within about three months. So training once isn't enough. People need to practise, and practice only helps if something tells you what you're doing wrong.");
  }

  // 4 TABLE
  {
    const s = slide("Feedback makes practice stick, but today it needs a sensor manikin", "Problem");
    tb(s, "AHA 2025 recommends feedback devices in CPR training, including for lay rescuers (Class 1).", { x: M, y: 2.2, w: CW, h: 0.45, fontSize: 20, color: MUTED });
    const none = { type: "none" };
    const rule = { pt: 1, color: HEX.hair };
    const cell = (text, o = {}) => ({ text, options: { border: [none, none, rule, none], fill: { color: HEX.ink }, ...o } });
    const head = ["", "Sees your technique", "Corrects you live", "What you need"].map((t) => cell(t, { fontFace: MONO, fontSize: 14, bold: true, color: HEX.muted }));
    const row = (a, b, c, d) => [cell(a, { bold: true }), cell(b, { color: HEX.muted }), cell(c, { color: HEX.muted }), cell(d, { color: HEX.muted })];
    const us = ["CPR Coach", "Rate, elbows, pauses", "Spoken, 10 languages", "A phone"].map((t, i) => cell(t, { bold: true, color: i === 0 ? HEX.teal : HEX.text, border: [none, none, none, none] }));
    s.addTable([head, row("Video tutorial", "No", "No", "A screen"), row("Metronome app", "No", "Beat only", "A phone"), row("Feedback manikin", "Yes", "Yes", "A sensor manikin"), us], {
      x: M, y: 3.0, w: CW, colW: [3.0, 3.05, 3.05, 2.73], rowH: [0.55, 0.72, 0.72, 0.72, 0.8],
      fontSize: 20, color: HEX.text, valign: "middle", margin: [0.05, 0.1, 0.05, 0],
    });
    s.addNotes("Feedback during training is a top-grade recommendation in the 2025 AHA guidelines. But a video can't see you, a metronome only gives a beat, and real feedback today needs a sensor manikin. CPR Coach gives feedback on rate, elbows and pauses with just a phone.");
  }

  // 5 IMAGE-LED
  pres.addSection({ title: "Solution" });
  {
    const s = slide("It watches your arms and says what to fix", "Solution");
    img(s, "assets/phone.png", M - 0.1, 2.0, 7.9, 4.4, "Illustration of the app: side view of a rescuer's arm joints, a rate of 96 per minute and the spoken prompt Push faster");
    tb(s, "Illustration · values are examples, not measurements", { x: M, y: 6.5, w: 7.5, h: 0.3, fontSize: 12, color: MUTED, italic: true });
    const steps = [["01", "Watch", "the camera sees shoulder, elbow and wrist"], ["02", "Count", "rate, elbow bend and pauses"], ["03", "Speak", "one correction, in your language"]];
    steps.forEach(([n, h, d], i) => {
      const y = 2.15 + i * 1.45;
      tb(s, n, { x: 8.95, y: y + 0.08, w: 0.6, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: TEAL });
      tb(s, h, { x: 9.6, y, w: 3.0, h: 0.5, fontFace: HEAD, fontSize: 28, bold: true });
      tb(s, d, { x: 9.6, y: y + 0.55, w: 3.0, h: 0.75, fontSize: 18, color: MUTED });
    });
    s.addNotes("The phone sits at the rescuer's side. It watches the arm joints, counts the push rate, elbow bend and pauses, and says one short correction at a time in the rescuer's language. Here it has seen 96 pushes a minute, below the 100 to 120 target, so it says push faster.");
  }

  // 6 FLOW ON THE RHYTHM LINE
  {
    const s = slide("Camera to voice, every step runs on the phone", "Solution");
    img(s, "assets2/pipeline.png", M, 3.45, CW, 0.8, "");
    const nodes = [["Camera", "phone held side-on"], ["Pose", "MediaPipe finds the joints"], ["Rhythm", "rate, elbow angle, pauses"], ["Rules", "one correction at a time"], ["Voice", "recorded clips and a 110/min beat"]];
    const step = CW / 5;
    nodes.forEach(([h, d], i) => {
      const x = M + i * step;
      s.addShape(pres.shapes.OVAL, { x: x + 0.02, y: 3.73, w: 0.24, h: 0.24, fill: { color: TEAL }, line: { color: TEAL, width: 0 } });
      tb(s, h, { x, y: 2.75, w: step - 0.2, h: 0.55, fontFace: HEAD, fontSize: 28, bold: true });
      tb(s, d, { x, y: 4.45, w: step - 0.35, h: 0.8, fontSize: 18, color: MUTED });
    });
    tb(s, "No internet permission. Nothing is uploaded.", { x: M, y: 5.75, w: CW, h: 0.45, fontSize: 22, bold: true, color: TEAL });
    tb(s, "Kotlin · Jetpack Compose · CameraX · MediaPipe Pose · sherpa-onnx (Whisper, IndicConformer) · Silero VAD", { x: M, y: 6.3, w: CW, h: 0.3, fontFace: MONO, fontSize: 12, color: MUTED });
    s.addNotes("Five stages, all on the phone. CameraX frames go to MediaPipe's pose model. Our Kotlin code turns the wrist's up-and-down motion into a rate, and works out elbow angle and pauses. Rules wait for a steady reading, then play one pre-recorded clip over a 110 a minute beat. The app has no internet permission.");
  }

  // 7 SPLIT COMPARISON
  {
    const s = slide("Practise teaches you; Emergency starts the beat in one tap", "Solution");
    hair(s, 6.67, 2.5, 0, 4.0);
    const cols = [
      { x: M, c: TEAL, k: "PRACTISE", h: "Learn with feedback", pts: ["2-minute session, live readings", "One spoken correction at a time", "Scorecard shows how long rate was really measured"], tag: "Ran on a phone: 7 real recordings" },
      { x: 7.2, c: RED, k: "EMERGENCY", h: "Act in seconds", pts: ["Beat and spoken steps start at once", "Never waits for the camera", "Call 112 opens the dialer; AED reminder at 30 s"], tag: "Ran on a phone: steps, beat, dialer" },
    ];
    cols.forEach((c) => {
      tb(s, c.k, { x: c.x, y: 2.5, w: 5, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: c.c, charSpacing: 3 });
      tb(s, c.h, { x: c.x, y: 2.9, w: 5.4, h: 0.6, fontFace: HEAD, fontSize: 30, bold: true });
      tb(s, c.pts.map((p, i) => ({ text: p, options: { breakLine: i < c.pts.length - 1 } })), { x: c.x, y: 3.75, w: 5.2, h: 2.1, fontSize: 21, paraSpaceAfter: 14 });
      tb(s, c.tag, { x: c.x, y: 6.1, w: 5.4, h: 0.38, fontFace: MONO, fontSize: 16, color: c.c });
    });
    s.addNotes("Practise is where you learn: a two-minute session with live readings, one spoken correction at a time, and a scorecard that says how long the rate was actually measured. Emergency is one tap: the beat and spoken steps start immediately and never wait for the camera. Call 112 only opens the dialer, and there's an AED reminder at 30 seconds.");
  }

  // 8 TYPOGRAPHIC
  {
    const s = slide("It coaches in ten Indian languages, each in its own script", "Solution");
    const langs = ["English", "हिन्दी", "ಕನ್ನಡ", "मराठी", "ગુજરાતી", "বাংলা", "ଓଡ଼ିଆ", "தமிழ்", "తెలుగు", "മലയാളം"];
    const cw = CW / 5;
    langs.forEach((l, i) => {
      tb(s, l, { x: M + (i % 5) * cw, y: 2.55 + Math.floor(i / 5) * 1.35, w: cw, h: 0.9, fontSize: 32, wrap: false, fontFace: i === 0 ? HEAD : INDIC, bold: i === 0, color: i === 0 ? TEAL : TEXT, fit: "none" });
    });
    tb(s, "English is checked. The other nine are machine-translated and need native-speaker review.", { x: M, y: 5.6, w: CW, h: 0.45, fontSize: 20, color: MUTED });
    s.addNotes("The picker lists ten languages, each in its own script, and coaching clips exist for all ten. English is the language we've checked; the other nine were machine-translated and still need native-speaker review before we'd call them ready.");
  }

  // 9 DIVIDER
  pres.addSection({ title: "Demo" });
  {
    const s = slide("Live demo", "Demo", "INK_BARE");
    tb(s, [
      { text: "Guide", options: { color: TEXT } }, { text: "  →  ", options: { color: MUTED } },
      { text: "Practise", options: { color: TEAL } }, { text: "  →  ", options: { color: MUTED } },
      { text: "Emergency", options: { color: RED } }, { text: "  →  ", options: { color: MUTED } },
      { text: "Crew summary", options: { color: TEXT } },
    ], { x: M, y: 2.85, w: CW, h: 0.6, fontFace: MONO, fontSize: 26, bold: true });
    tb(s, "Rescuer pushes a cushion and listens. Holder keeps the phone side-on.", { x: M, y: 3.85, w: 10, h: 0.45, fontSize: 22, color: MUTED });
    tb(s, "Cushion only, never a person. No real call is placed.", { x: M, y: 4.45, w: 10, h: 0.45, fontSize: 22, color: RED, bold: true });
    s.addNotes("Two-minute demo: the CPR guide and its AED explainer, Practise on a cushion so you can hear the corrections, then Emergency: one tap, beat and steps, Call 112 opens the dialer and we cancel it. Hold to stop shows the summary for the ambulance crew. A dash there means not measured, not zero.");
  }

  // 10 TWO PLAIN LISTS + GAP LINE
  pres.addSection({ title: "Evidence" });
  {
    const s = slide("It shows a dash, never a guess, for anything it can't see", "Evidence");
    const lists = [
      { x: M, c: TEAL, k: "IT MEASURES", items: ["Push rate", "Elbow angle", "Pauses", "Tracking quality"] },
      { x: 7.2, c: MUTED, k: "ONE CAMERA CAN'T", items: ["Compression depth", "Force and full recoil", "Exact hand placement", "Clinical effectiveness"] },
    ];
    lists.forEach((l) => {
      tb(s, l.k, { x: l.x, y: 2.45, w: 5, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: l.c, charSpacing: 3 });
      tb(s, l.items.map((t, i) => ({ text: t, options: { breakLine: i < l.items.length - 1 } })), { x: l.x, y: 2.9, w: 5.3, h: 1.9, fontSize: 22, paraSpaceAfter: 8, color: l.c === MUTED ? MUTED : TEXT });
    });
    img(s, "assets2/gap.png", M, 4.95, CW, 0.735, "A push rhythm line with a gap where the camera loses the arms");
    tb(s, "—", { x: 5.6, y: 4.9, w: 2.1, h: 0.8, fontFace: MONO, fontSize: 40, bold: true, color: TEXT, align: "center" });
    tb(s, "Hand-counted accuracy runs come next. Until then, we quote no accuracy figures.", { x: M, y: 6.05, w: CW, h: 0.45, fontSize: 20, color: AMBER });
    s.addNotes("The app estimates rate, elbow angle, pauses, and how good its tracking is. One camera can't measure depth, force, recoil or exact hand placement, so we don't pretend to. When it loses sight of the arms, it shows a dash instead of guessing. Hand-counted accuracy runs are next; until they're done we don't quote an accuracy number.");
  }

  // 11 INLINE PROOF NUMBERS
  {
    const s = slide("399 tests and 7 real recordings\ngate every change", "Evidence");
    const nums = [["399", "unit tests passing, 0 failing"], ["7", "real phone recordings must replay byte for byte"], ["2", "Android phones for on-device testing"], ["14", "AI agents on 4 PCs, one lead reviewing every diff"]];
    const cw = CW / 4;
    nums.forEach(([v, d], i) => {
      const x = M + i * cw;
      if (i > 0) hair(s, x - 0.2, 2.35, 0, 2.6);
      tb(s, v, { x, y: 2.2, w: cw - 0.4, h: 1.3, fontFace: MONO, fontSize: 80, bold: true, color: i === 3 ? TEXT : TEAL, fit: "none" });
      tb(s, d, { x, y: 3.65, w: cw - 0.5, h: 1.2, fontSize: 19, color: MUTED });
    });
    tb(s, "Built in 24 hours by four people and an AI agent swarm. No change merges without passing both gates.", { x: M, y: 5.6, w: CW, h: 0.8, fontSize: 22 });
    s.addNotes("We built this in 24 hours with a swarm of AI coding agents across four PCs: one lead that reads every diff and thirteen workers. Nothing merges unless all unit tests pass, 399 right now, and seven real phone recordings replay through Practise with byte-identical output.");
  }

  // 12 CLOSE WITH ACTION
  pres.addSection({ title: "Close" });
  {
    const s = slide("Next: count it by hand,\nthen test it with CPR trainers", "Close");
    tb(s, "Then pilot it with NSS units, first-aid clubs and schools.", { x: M, y: 2.25, w: 8.0, h: 0.5, fontSize: 24, color: MUTED });
    img(s, "assets2/close.png", M, 3.3, 7.67, 0.73, "A slow amber rhythm corrected into a steady teal rhythm");
    s.addImage({ path: "assets/qr.png", x: 9.85, y: 2.15, w: 2.73, h: 2.73, altText: "QR code linking to github.com/VivekTiwary-25/Zero_flux" });
    tb(s, "Scan for the code and test records", { x: 9.2, y: 5.0, w: 3.6, h: 0.35, fontSize: 18, align: "center" });
    tb(s, "github.com/VivekTiwary-25/Zero_flux", { x: 9.0, y: 5.42, w: 3.95, h: 0.3, fontFace: MONO, fontSize: 12, color: MUTED, align: "center" });
    tb(s, [{ text: "Not a medical device. ", options: { bold: true, color: RED } }, { text: "In an emergency, call 112 (108 for an ambulance) and start CPR.", options: { color: TEXT } }], { x: M, y: 4.75, w: 8.2, h: 0.9, fontSize: 20 });
    tb(s, [{ text: "Team Zero Flux", options: { bold: true, color: TEXT } }, { text: "   Vivek · Pratham · Prabal · Moksh", options: { color: MUTED } }], { x: M, y: 6.3, w: 8, h: 0.4, fontSize: 18 });
    s.addNotes("Next, we count it by hand to get a real accuracy number, then put it in front of certified CPR trainers, then pilot it with NSS units, first-aid clubs and schools. The code and our test records are open: scan the code. And the line that matters most: this is not a medical device. In a real emergency, call 112 or 108 and start CPR. Thank you.");
  }

  const out = "CPR_Coach_Anvathon_2026_v2.pptx";
  await pres.writeFile({ fileName: out });
  await applyTheme(out, THEME);
  console.log("wrote", out);
})();
