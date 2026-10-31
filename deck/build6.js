// CPR Coach deck v6 (v5 with real app screenshots): five slides, built on build2.js (v4) helpers. Same DECK.md rules.
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

  // ---------- v3 line art: flat strokes, teal/white on transparent, no faces ----------
  // Side view, kneeling rescuer: shoulder, elbow and wrist stacked on one vertical (x = 400).
  // The teal dots are the three joints the app tracks.
  const rescuerSvg = (guide) => `<svg xmlns='http://www.w3.org/2000/svg' width='600' height='400' viewBox='0 0 600 400'>
    <g fill='none' stroke-linecap='round' stroke-linejoin='round'>
      <path d='M40 362 L560 362' stroke='#${HEX.muted}' stroke-width='3' stroke-opacity='0.6'/>
      <rect x='335' y='322' width='180' height='40' stroke='#${HEX.text}' stroke-width='3'/>
      ${guide ? `<path d='M400 92 L400 352' stroke='#${HEX.text}' stroke-width='3' stroke-dasharray='6 10' stroke-opacity='0.55'/>` : ""}
      <path d='M192 344 L210 356 L325 352 L285 235 L400 160' stroke='#${HEX.text}' stroke-width='4'/>
      <path d='M400 160 L414 147' stroke='#${HEX.text}' stroke-width='4'/>
      <circle cx='426' cy='126' r='24' stroke='#${HEX.text}' stroke-width='4'/>
      <path d='M400 160 L400 316' stroke='#${HEX.teal}' stroke-width='4'/>
      <path d='M384 318 L420 318 M388 311 L414 311' stroke='#${HEX.teal}' stroke-width='4'/>
    </g>
    <g fill='#${HEX.teal}'><circle cx='400' cy='160' r='7'/><circle cx='400' cy='238' r='7'/><circle cx='400' cy='308' r='7'/></g>
  </svg>`;
  // Top view of a chest: breastbone from the notch (y 120) to its lower tip (y 330); the lower half (y 225-330) is teal
  // and the stacked hands are centred on it (y 277).
  const chestSvg = `<svg xmlns='http://www.w3.org/2000/svg' width='500' height='500' viewBox='0 0 500 500'>
    <g fill='none' stroke-linecap='round' stroke-linejoin='round'>
      <path d='M210 50 L210 95 Q160 100 110 120 Q85 135 85 170 L92 362 M408 362 L415 170 Q415 135 390 120 Q340 100 290 95 L290 50' stroke='#${HEX.text}' stroke-width='3'/>
      <path d='M235 170 Q180 172 125 195 M265 170 Q320 172 375 195 M235 210 Q180 214 120 240 M265 210 Q320 214 380 240 M238 330 Q170 334 100 352 M262 330 Q330 334 400 352' stroke='#${HEX.muted}' stroke-width='3' stroke-opacity='0.5'/>
      <path d='M250 120 L250 225' stroke='#${HEX.text}' stroke-width='3'/>
      <path d='M250 225 L250 330' stroke='#${HEX.teal}' stroke-width='4'/>
      <path d='M232 225 L268 225' stroke='#${HEX.teal}' stroke-width='3'/>
      <ellipse cx='250' cy='277' rx='44' ry='54' stroke='#${HEX.text}' stroke-width='4'/>
      <ellipse cx='255' cy='270' rx='39' ry='49' stroke='#${HEX.text}' stroke-width='3' stroke-opacity='0.7'/>
    </g>
  </svg>`;
  const art = (file, svg) => sharp(Buffer.from(svg), { density: 144 }).png().toFile(file); // 2x
  fs.mkdirSync("assets3", { recursive: true });
  for (const [f, s] of [["rescuer.svg", rescuerSvg(false)], ["rescuer_guide.svg", rescuerSvg(true)], ["chest.svg", chestSvg]]) {
    fs.writeFileSync(`assets3/${f}`, s);
    await art(`assets3/${f.replace(".svg", ".png")}`, s);
  }

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
  // v4 FILM slides: a full-bleed story still from NARRATIVE.md under an ink veil, so white text stays readable.
  // A missing still falls back to plain ink, so the deck always builds.
  const filmPath = (shot) => [`assets3/${shot}_soft.jpg`, `assets3/${shot}.jpg`].find((f) => fs.existsSync(f)); // only approved stills (see IMAGES_CHECK.md)
  const slide = (title, section, master = "INK", film) => {
    const s = pres.addSlide({ masterName: master, sectionTitle: section });
    const still = film && filmPath(film.shot);
    if (still) {
      s.background = { path: still };
      s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: W, h: 7.5, fill: { color: HEX.ink, transparency: film.veil }, line: { type: "none" } });
    }
    s.addText(title, { placeholder: "title" });
    if (still) tb(s, "Illustration", { x: 10.9, y: 0.18, w: 1.68, h: 0.3, fontSize: 12, color: MUTED, italic: true, align: "right" });
    s.film = !!still;
    return s;
  };
  const source = (s, t) => tb(s, t, { x: M, y: 6.92, w: 10.6, h: 0.3, fontSize: 12, color: MUTED });

  // 1 COVER

  // 1 COVER
  pres.addSection({ title: "Opening" });
  {
    const s = slide("CPR Coach", "Opening", "INK_BARE", { shot: "platform", veil: 70 });
    tb(s, "A phone that watches your CPR and tells you what to fix.", { x: M, y: 2.75, w: 7.6, h: 1.0, fontSize: 28, color: TEXT });
    img(s, "assets2/cover.png", 0, 4.1, W, 1.0, "A flat line that turns into a steady push rhythm");
    tb(s, "110 /min", { x: 11.1, y: 3.65, w: 1.5, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: TEAL, align: "right" });
    tb(s, [{ text: "Team Zero Flux", options: { bold: true, color: TEXT } }, { text: "   Vivek · Pratham · Prabal · Moksh", options: { color: MUTED } }], { x: M, y: 6.3, w: 7.0, h: 0.4, fontSize: 18 });
    tb(s, "ANVATHON 2026 · OPEN INNOVATION", { x: 8.0, y: 6.34, w: 4.58, h: 0.35, fontFace: MONO, fontSize: 14, color: MUTED, align: "right" });
    s.addNotes("CPR Coach is a phone app that watches you do CPR on a practice manikin and tells you, out loud, what to fix. The line is the push rhythm the app reads: flat when nobody is pushing, steady at 110 a minute when someone is.");
  }

  // 2 PROBLEM: native chart
  pres.addSection({ title: "Problem" });
  {
    const s = slide("India's bystander CPR rate is a quarter of the US rate", "Problem");
    s.addChart(pres.charts.BAR, [
      { name: "India", labels: ["Bystander CPR", "Survival to hospital discharge"], values: [9.8, 1.4] },
      { name: "US", labels: ["Bystander CPR", "Survival to hospital discharge"], values: [40.2, 9.1] },
    ], {
      x: M - 0.1, y: 1.95, w: 8.4, h: 3.75, barDir: "col", barGrouping: "clustered", barGapWidthPct: 70, barOverlapPct: -10,
      chartColors: [HEX.amber, HEX.muted], showLegend: false,
      showValue: true, showSerName: true, dataLabelFormatCode: '0.0"%"', dataLabelPosition: "outEnd",
      dataLabelFontFace: "+mn-lt", dataLabelFontSize: 18, dataLabelColor: HEX.text, dataLabelFontBold: true,
      catAxisLabelFontFace: "+mn-lt", catAxisLabelFontSize: 18, catAxisLabelColor: HEX.text, catAxisLineShow: true, catAxisLineColor: HEX.hair,
      valAxisHidden: true, valAxisMaxVal: 46, valAxisMinVal: 0, valGridLine: { style: "none" }, catGridLine: { style: "none" },
      plotArea: { fill: { color: HEX.ink } }, fill: HEX.ink,
      altText: "Bar chart. Bystander CPR: India 9.8%, US 40.2%. Survival to hospital discharge: India 1.4%, US 9.1%.",
    });
    tb(s, [
      { text: "India", options: { bold: true, color: AMBER, breakLine: true } }, { text: "one hospital, 205 patients", options: { color: MUTED, breakLine: true } },
      { text: " ", options: { breakLine: true, fontSize: 8 } },
      { text: "US", options: { bold: true, color: MUTED, breakLine: true } }, { text: "national registry", options: { color: MUTED, breakLine: true } },
      { text: " ", options: { breakLine: true, fontSize: 10 } },
      { text: "Fewer people nearby start CPR, and fewer patients leave hospital alive.", options: { color: TEXT } },
    ], { x: 9.35, y: 2.3, w: 3.23, h: 3.4, fontSize: 20 });
    tb(s, [
      { text: "Karnataka is moving: ", options: { bold: true, color: TEAL } },
      { text: "in July 2025 the Health Minister announced CPR training for school and college students, teachers and gym trainers." },
    ], { x: M, y: 5.78, w: 8.3, h: 0.66, fontSize: 18 });
    tb(s, [
      { text: "India: Bhat et al., Indian Heart Journal 2021, one tertiary hospital, 205 patients. Karnataka: NewKerala, 10 July 2025.", options: { breakLine: true } },
      { text: "The two sources are not measured the same way: one hospital vs a national registry." },
    ], { x: M, y: 6.5, w: 5.4, h: 0.85, fontSize: 12, color: MUTED });
    tb(s, "US: American Heart Association, Heart Disease and Stroke Statistics 2023. Bystander CPR: over 356,000 out-of-hospital arrests. Survival: 2021 US data, EMS-treated adult non-traumatic arrests.", { x: 6.45, y: 6.5, w: 5.4, h: 0.72, fontSize: 12, color: MUTED });
    s.addNotes("In an Indian Heart Journal study of 205 cardiac-arrest patients at one hospital, 9.8 percent got CPR from a bystander and 1.4 percent left hospital alive. The American Heart Association's US figures are 40.2 percent and 9.1 percent. The two sources are not measured the same way, one hospital against a national registry, so read it as a gap, not a precise ratio. Under 2 in 100 people in one survey said they knew CPR, and skills can start to fade within about three months of a course. Karnataka is moving: in July 2025 the Health Minister announced CPR training for school and college students, teachers and gym trainers (NewKerala, 10 July 2025).");
  }

  // 3 WHAT IT IS: real app screenshots from a phone run
  pres.addSection({ title: "Solution" });
  {
    const s = slide("It watches your arms and says what to fix", "Solution");
    // Screenshots keep their own aspect ratio (about 2.5:1); the contact-name button is blurred in the two Emergency shots.
    img(s, "assets4/practise.jpg", M, 1.85, 7.0, 7.0 * 487 / 1200, "App screenshot, Practise: 103 per minute, elbow angle 172 degrees, Elbows straight");
    tb(s, "Practise: 103 pushes a minute, elbows straight", { x: M, y: 4.74, w: 7.0, h: 0.35, fontSize: 18, color: TEAL });
    img(s, "assets4/em-good.jpg", M, 5.18, 3.42, 3.42 * 479 / 1199, "App screenshot, Emergency at 0:19: joints tracked in teal, Call 112 button");
    img(s, "assets4/em-amber.jpg", M + 3.58, 5.18, 3.42, 3.42 * 478 / 1199, "App screenshot, Emergency at 0:39: joints shown in amber because the posture needs fixing");
    tb(s, [{ text: "Emergency: ", options: { bold: true, color: RED } }, { text: "joints turn " }, { text: "amber", options: { color: AMBER } }, { text: " when posture needs fixing" }], { x: M, y: 6.6, w: 7.0, h: 0.35, fontSize: 18 });
    const R = 8.3, RW = 4.28;
    [["Watch", "the camera finds shoulder, elbow and wrist"], ["Count", "push rate, elbow angle and pauses"], ["Speak", "one correction at a time, out loud"]].forEach(([h, d], i) => {
      tb(s, [{ text: h + "  ", options: { bold: true, fontFace: HEAD, color: TEAL } }, { text: d }], { x: R, y: 1.85 + i * 0.66, w: RW, h: 0.62, fontSize: 18 });
    });
    img(s, "assets4/manikin.jpg", R, 3.85, 1.2, 1.6, "Photo of our home-made manikin: a T-shirt over a padded bundle");
    tb(s, "All you need: a phone and a home-made manikin.", { x: R + 1.35, y: 3.9, w: RW - 1.35, h: 1.1, fontSize: 20, bold: true });
    tb(s, [{ text: "Measures rate, elbows and shoulders over hands. ", options: { color: TEXT } }, { text: "Can't measure depth, force or hand placement.", options: { color: MUTED } }], { x: R, y: 5.52, w: RW, h: 0.95, fontSize: 18 });
    tb(s, "Coaches in 10 languages, fully offline.", { x: R, y: 6.52, w: RW, h: 0.36, fontSize: 18, color: TEAL });
    s.addNotes("These are screenshots from the real app on a phone this morning. In Practise it has counted 103 pushes a minute and says elbows straight; the line on the arm is the elbow angle it measured. In Emergency the joints are teal while posture is fine and turn amber when it needs fixing, with Call 112 always on screen. All you need is a phone and a home-made manikin: ours is a T-shirt over a padded bundle. It counts the push rate, elbow angle and pauses, checks shoulders over hands, and says one correction at a time. One camera can't measure depth, force or exact hand placement, and when it loses the arms it shows a dash instead of guessing. Coaching clips exist in ten languages; English is checked and the other nine are machine-translated and still need native-speaker review. Everything runs on the phone with no internet.");
  }

  // 3b FLOW ON THE RHYTHM LINE (v4 slide 7)
  {
    const s = slide("Camera to voice, every step runs on the phone", "Solution");
    img(s, "assets2/pipeline.png", M, 3.45, CW, 0.8, "A steady push rhythm line joining the five stages");
    const nodes = [["Camera", "phone held side-on"], ["Pose", "MediaPipe finds the joints"], ["Rhythm", "rate, elbow angle, pauses"], ["Rules", "one correction at a time"], ["Voice", "recorded clips and a 110/min beat"]];
    const step = CW / 5;
    nodes.forEach(([h, d], i) => {
      const x = M + i * step;
      s.addShape(pres.shapes.OVAL, { x: x + 0.02, y: 3.73, w: 0.24, h: 0.24, fill: { color: TEAL }, line: { color: TEAL, width: 0 } });
      tb(s, h, { x, y: 2.75, w: step - 0.2, h: 0.55, fontFace: HEAD, fontSize: 28, bold: true });
      tb(s, d, { x, y: 4.45, w: step - 0.35, h: 0.8, fontSize: 18, color: MUTED });
    });
    tb(s, "No internet permission. Nothing is uploaded.", { x: M, y: 5.75, w: CW, h: 0.45, fontSize: 22, bold: true, color: TEAL });
    tb(s, "Kotlin · Jetpack Compose · CameraX · MediaPipe Pose · sherpa-onnx (Whisper, IndicConformer) · Silero VAD", { x: M, y: 6.3, w: 6.0, h: 0.45, fontFace: MONO, fontSize: 12, color: MUTED });
    s.addNotes("Five stages, all on the phone. CameraX frames go to MediaPipe's pose model. Our Kotlin code turns the wrist's up-and-down motion into a rate, and works out elbow angle and pauses. Rules wait for a steady reading, then play one pre-recorded clip over a 110 a minute beat. The app has no internet permission.");
  }

  // 4 PROOF
  pres.addSection({ title: "Evidence" });
  {
    const s = slide("Built in 24 hours: 596 commits", "Evidence", "INK", { shot: "kneel", veil: 70 });
    tb(s, "FROM", { x: M, y: 2.2, w: 3, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: AMBER, charSpacing: 3 });
    tb(s, "Our first real run on a phone crashed mid-session, and the recording was lost.", { x: M, y: 2.6, w: 7.4, h: 1.0, fontSize: 24 });
    tb(s, "TO", { x: M, y: 3.9, w: 3, h: 0.35, fontFace: MONO, fontSize: 16, bold: true, color: TEAL, charSpacing: 3 });
    tb(s, "A working prototype: the crash fixed, camera load capped, and heat and battery logged on every run.", { x: M, y: 4.3, w: 7.4, h: 1.0, fontSize: 24 });
    hair(s, 8.6, 2.25, 0, 3.6);
    [["399", "tests passing, 0 failing"], ["7", "real phone recordings must replay byte for byte"]].forEach(([v, d], i) => {
      tb(s, v, { x: 9.0, y: 2.1 + i * 1.95, w: 3.6, h: 1.0, fontFace: MONO, fontSize: 60, bold: true, color: TEAL, fit: "none" });
      tb(s, d, { x: 9.0, y: 3.1 + i * 1.95, w: 3.6, h: 0.7, fontSize: 18, color: MUTED });
    });
    s.addNotes("596 commits on the cpr-coach branch, built with four people and a swarm of AI coding agents across four PCs. The first time real motion went through the app on a phone, the rate code crashed mid-session when every correlation peak was negative, and that recording was lost. We fixed the crash so a failed reading shows no number instead of killing the session, made recordings save as they go, capped pose input near 15 frames a second at 640 by 480, and added an adaptive frame gap with thermal and battery logging on every trace. Today 399 unit tests pass, with 5 skipped, and seven real phone recordings must replay with byte-identical output before anything merges.");
  }

  // 5 DEMO AND NEXT
  pres.addSection({ title: "Close" });
  {
    const s = slide("Live demo", "Close", "INK_BARE", { shot: "phone", veil: 70 });
    tb(s, [{ text: "Next: ", options: { bold: true, color: TEAL } }, { text: "hand-counted accuracy runs, review by CPR trainers, college pilots." }], { x: M, y: 2.85, w: 8.2, h: 0.9, fontSize: 24 });
    tb(s, [{ text: "Not a medical device. ", options: { bold: true, color: RED } }, { text: "In an emergency, call 112 (108 for an ambulance) and start CPR.", options: { color: TEXT } }], { x: M, y: 4.3, w: 8.2, h: 0.9, fontSize: 22 });
    s.addImage({ path: "assets/qr.png", x: 9.85, y: 2.0, w: 2.73, h: 2.73, altText: "QR code linking to github.com/VivekTiwary-25/Zero_flux" });
    tb(s, "Code and test records", { x: 9.2, y: 4.85, w: 3.6, h: 0.35, fontSize: 18, align: "center" });
    tb(s, "github.com/VivekTiwary-25/Zero_flux", { x: 9.0, y: 5.27, w: 3.95, h: 0.3, fontFace: MONO, fontSize: 12, color: TEXT, align: "center" });
    tb(s, [{ text: "Team Zero Flux", options: { bold: true, color: TEXT } }, { text: "   Vivek · Pratham · Prabal · Moksh", options: { color: MUTED } }], { x: M, y: 6.3, w: 8, h: 0.4, fontSize: 18 });
    s.addNotes("Demo: Practise on the manikin so you can hear the corrections, then Emergency: one tap, beat and spoken steps, Call 112 opens the dialer and we cancel it. Manikin only, never a person; no real call is placed. Next we count it by hand to get a real accuracy number, then put it in front of certified CPR trainers, then pilot it in colleges. This is not a medical device: in a real emergency, call 112 or 108 and start CPR.");
  }

  const out = process.env.DECK_OUT || "CPR_Coach_Anvathon_2026_v6.pptx";
  await pres.writeFile({ fileName: out });
  await applyTheme(out, THEME);
  console.log("wrote", out);
})();
