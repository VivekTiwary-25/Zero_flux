#!/usr/bin/env python3
"""Transcribe CPR Coach clips and compare them to the phrase lists.

English uses Whisper tiny.en. Every other supported language uses
IndicConformer (NeMo CTC). Both run through sherpa-onnx. The script also
checks that each clip is mono 16-bit PCM, is not clipped, is not silent,
and lasts between 0.2 and 8 seconds.

This is a machine check of what the voice says. It does not replace a
person listening.
"""

from __future__ import annotations

import argparse
import gc
import json
import struct
import sys
import unicodedata
import wave
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

# Phrase-file order used by the clip generator and CLIP_STATUS.md.
LANGUAGES = ("en", "hi", "kn", "bn", "or", "gu", "mr", "te", "ta", "ml")
WHISPER_LANGUAGES = frozenset({"en"})
INDIC_LANGUAGES = frozenset({"hi", "gu", "mr", "ta", "te", "or", "bn", "kn", "ml"})
KEY_ORDER = (
    "lang_name",
    "countdown",
    "push_faster",
    "slow_down",
    "elbows_straight",
    "keep_going",
    "good",
    "session_done",
    "em_call",
    "em_hands",
    "em_push",
    "em_swap",
    "em_aed",
    "em_continue",
)

# Flag when character error rate is above this value (3/10). Equal is not a flag.
CER_FLAG_NUMERATOR = 3
CER_FLAG_DENOMINATOR = 10
# Inclusive duration window required of every clip, in seconds.
MIN_SECONDS_NUMERATOR = 1
MIN_SECONDS_DENOMINATOR = 5
MAX_SECONDS = 8
# 16-bit PCM rails. A sample on either rail is clipping.
PCM16_MIN = -32768
PCM16_MAX = 32767

WHISPER_DIR = Path(r"D:\projects\sih\iTantra\app\src\main\assets")
INDIC_DIR = Path(r"D:\projects\sih\iTantra\model-staging\indicconformer")
# Matches SpeechRecognizerManager: two threads was the measured phone setting
# for these same model files.
RECOGNIZER_THREADS = 2


def eprint(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def levenshtein(left: str, right: str) -> int:
    if left == right:
        return 0
    if not left:
        return len(right)
    if not right:
        return len(left)
    previous = list(range(len(right) + 1))
    for index, left_char in enumerate(left, 1):
        current = [index]
        for right_index, right_char in enumerate(right, 1):
            insert = current[right_index - 1] + 1
            delete = previous[right_index] + 1
            substitute = previous[right_index - 1] + (left_char != right_char)
            current.append(min(insert, delete, substitute))
        previous = current
    return previous[-1]


def normalize_for_cer(text: str) -> str:
    """NFC, delete Unicode punctuation, casefold, collapse whitespace."""
    normalized = unicodedata.normalize("NFC", text)
    kept = [
        char
        for char in normalized
        if not unicodedata.category(char).startswith("P")
    ]
    return " ".join("".join(kept).split()).casefold()


def above_cer_flag(edits: int, reference_length: int) -> bool:
    if reference_length <= 0:
        return True
    return edits * CER_FLAG_DENOMINATOR > reference_length * CER_FLAG_NUMERATOR


def format_rate(edits: int, reference_length: int) -> str:
    if reference_length <= 0:
        return "n/a"
    shown = (Decimal(edits) / Decimal(reference_length)).quantize(
        Decimal("0.0001"), rounding=ROUND_HALF_UP
    )
    return f"{edits}/{reference_length} ({shown})"


def format_seconds(frames: int, rate: int) -> str:
    shown = (Decimal(frames) / Decimal(rate)).quantize(
        Decimal("0.001"), rounding=ROUND_HALF_UP
    )
    return f"{shown}s ({frames}/{rate})"


def duration_outside(frames: int, rate: int) -> bool:
    if rate <= 0:
        return True
    too_short = frames * MIN_SECONDS_DENOMINATOR < rate * MIN_SECONDS_NUMERATOR
    too_long = frames > MAX_SECONDS * rate
    return too_short or too_long


def read_phrases(path: Path) -> dict[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or any(not isinstance(value, str) for value in data.values()):
        raise ValueError(f"{path} is not a JSON object of strings")
    return data


def inspect_wav(path: Path) -> dict:
    issues = []
    with wave.open(str(path), "rb") as handle:
        channels = handle.getnchannels()
        sample_width = handle.getsampwidth()
        rate = handle.getframerate()
        frames = handle.getnframes()
        comptype = handle.getcomptype()
        raw = handle.readframes(frames)
    expected_bytes = frames * channels * sample_width
    if comptype != "NONE":
        issues.append(f"compressed ({comptype})")
    if channels != 1:
        issues.append(f"{channels} channels")
    if sample_width != 2:
        issues.append(f"{sample_width * 8}-bit")
    if rate <= 0:
        issues.append("sample rate 0")
    if len(raw) != expected_bytes:
        issues.append("truncated")
    samples = []
    peak = None
    if sample_width == 2 and channels >= 1 and len(raw) >= 2 and len(raw) % (2 * channels) == 0:
        count = len(raw) // 2
        interleaved = struct.unpack("<" + "h" * count, raw[: count * 2])
        samples = interleaved[::channels]
        if samples:
            peak = max(abs(sample) for sample in samples)
            if any(sample == PCM16_MAX or sample == PCM16_MIN for sample in samples):
                issues.append("clipped")
            if all(sample == 0 for sample in samples):
                issues.append("silent")
        else:
            issues.append("silent")
    elif frames == 0 or not raw:
        issues.append("silent")
    if rate > 0 and duration_outside(frames, rate):
        issues.append(f"duration {format_seconds(frames, rate)}")
    floats = [sample / 32768.0 for sample in samples] if samples else []
    return {
        "channels": channels,
        "sample_width": sample_width,
        "rate": rate,
        "frames": frames,
        "peak": peak,
        "issues": issues,
        "samples": floats,
    }


def audio_note(info: dict) -> str:
    parts = []
    if info["channels"] == 1 and info["sample_width"] == 2:
        parts.append("mono 16-bit")
    if info["rate"] > 0 and info["frames"] >= 0:
        parts.append(format_seconds(info["frames"], info["rate"]))
        parts.append(f"{info['rate']} Hz")
    if info["peak"] is not None:
        parts.append(f"peak {info['peak']}")
    return ", ".join(parts)


def load_whisper(model_dir: Path):
    import sherpa_onnx

    encoder = model_dir / "tiny.en-encoder.int8.onnx"
    decoder = model_dir / "tiny.en-decoder.int8.onnx"
    tokens = model_dir / "tiny.en-tokens.txt"
    missing = [path for path in (encoder, decoder, tokens) if not path.is_file()]
    if missing:
        raise FileNotFoundError("Whisper files missing: " + ", ".join(str(path) for path in missing))
    return sherpa_onnx.OfflineRecognizer.from_whisper(
        encoder=str(encoder),
        decoder=str(decoder),
        tokens=str(tokens),
        language="en",
        task="transcribe",
        num_threads=RECOGNIZER_THREADS,
    )


def load_indic(model_dir: Path, language: str):
    import sherpa_onnx

    model = model_dir / f"{language}.onnx"
    tokens = model_dir / "languages" / language / "tokens.txt"
    weights = model_dir / "shared" / "encoder.weights.bin"
    missing = [path for path in (model, tokens, weights) if not path.is_file()]
    if missing:
        raise FileNotFoundError(
            f"IndicConformer files missing for {language}: "
            + ", ".join(str(path) for path in missing)
        )
    return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(model),
        tokens=str(tokens),
        num_threads=RECOGNIZER_THREADS,
    )


def transcribe(recognizer, samples: list[float], sample_rate: int) -> str:
    stream = recognizer.create_stream()
    stream.accept_waveform(sample_rate, samples)
    recognizer.decode_stream(stream)
    text = stream.result.text
    return text if isinstance(text, str) else ""


def ordered_keys(found: set[str]) -> list[str]:
    ordered = [key for key in KEY_ORDER if key in found]
    extras = sorted(found - set(ordered))
    return ordered + extras


def discover_languages(clips_dir: Path, phrases_dir: Path, requested: list[str] | None) -> list[str]:
    found = set()
    if clips_dir.is_dir():
        found.update(path.name for path in clips_dir.iterdir() if path.is_dir())
    found.update(path.stem.removeprefix("phrases_") for path in phrases_dir.glob("phrases_*.json"))
    if requested:
        unknown = [code for code in requested if code not in found and code not in LANGUAGES]
        if unknown:
            raise SystemExit("unknown language: " + ", ".join(unknown))
        return requested
    ordered = [code for code in LANGUAGES if code in found]
    extras = sorted(found - set(ordered))
    return ordered + extras


def check_language(
    language: str,
    clips_dir: Path,
    phrases_dir: Path,
    whisper_dir: Path,
    indic_dir: Path,
    recognizers: dict,
) -> list[dict]:
    phrase_path = phrases_dir / f"phrases_{language}.json"
    phrases = read_phrases(phrase_path) if phrase_path.is_file() else {}
    clip_dir = clips_dir / language
    clip_paths = {}
    if clip_dir.is_dir():
        clip_paths = {
            path.stem: path
            for path in sorted(clip_dir.glob("*.wav"))
        }
    keys = ordered_keys(set(phrases) | set(clip_paths))
    recognizer = None
    recognizer_error = ""
    if keys and any(key in clip_paths for key in keys):
        try:
            if language in WHISPER_LANGUAGES:
                recognizer = recognizers.get("en")
                if recognizer is None:
                    eprint(f"loading Whisper tiny.en for {language}")
                    recognizer = load_whisper(whisper_dir)
                    recognizers["en"] = recognizer
            elif language in INDIC_LANGUAGES:
                eprint(f"loading IndicConformer for {language}")
                recognizer = load_indic(indic_dir, language)
                recognizers["indic"] = recognizer
            else:
                recognizer_error = "no recogniser for this language"
        except Exception as exc:  # noqa: BLE001 — report the load failure, do not invent transcripts
            recognizer_error = str(exc)
            eprint(f"{language}: recogniser failed: {exc}")
    rows = []
    for key in keys:
        expected = phrases.get(key)
        clip_path = clip_paths.get(key)
        row = {
            "language": language,
            "key": key,
            "expected": expected,
            "heard": None,
            "edits": None,
            "reference_length": None,
            "audio": "",
            "issues": [],
        }
        if expected is None:
            row["issues"].append("missing from phrase list")
        if clip_path is None:
            row["issues"].append("missing clip")
            rows.append(row)
            eprint(f"{language} {key}: missing clip")
            continue
        try:
            info = inspect_wav(clip_path)
        except Exception as exc:  # noqa: BLE001
            row["issues"].append(f"unreadable wav: {exc}")
            rows.append(row)
            eprint(f"{language} {key}: unreadable: {exc}")
            continue
        row["audio"] = audio_note(info)
        row["issues"].extend(info["issues"])
        if recognizer_error:
            row["issues"].append(recognizer_error)
        elif recognizer is None:
            row["issues"].append("no recogniser")
        elif not info["samples"] or info["rate"] <= 0:
            row["issues"].append("not transcribed")
        else:
            try:
                heard = transcribe(recognizer, info["samples"], info["rate"])
            except Exception as exc:  # noqa: BLE001
                row["issues"].append(f"transcribe failed: {exc}")
                heard = None
            row["heard"] = heard
        if expected is not None and row["heard"] is not None:
            reference = normalize_for_cer(expected)
            hypothesis = normalize_for_cer(row["heard"])
            row["edits"] = levenshtein(reference, hypothesis)
            row["reference_length"] = len(reference)
            if row["reference_length"] == 0:
                row["issues"].append("expected text empty after punctuation removal")
            elif above_cer_flag(row["edits"], row["reference_length"]):
                row["issues"].append("error rate above 0.3")
        elif expected is not None and row["heard"] is None and "missing clip" not in row["issues"]:
            row["issues"].append("no transcript")
        rows.append(row)
        rate = (
            format_rate(row["edits"], row["reference_length"])
            if row["edits"] is not None
            else "n/a"
        )
        eprint(f"{language} {key}: cer={rate} flagged={'yes' if row['issues'] else 'no'}")
    if language in INDIC_LANGUAGES:
        recognizers.pop("indic", None)
        gc.collect()
    return rows


def cell(value: str) -> str:
    return value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ").replace("\r", "")


def display_text(value: str | None, missing: str) -> str:
    if value is None:
        return missing
    if value == "":
        return "(empty)"
    return cell(value)


def flagged(row: dict) -> bool:
    return bool(row["issues"])


def render_markdown(rows: list[dict], meta: dict) -> str:
    lines = [
        "This is a machine check of what the voice says and does NOT replace a person listening.",
        "",
        "# Clip check",
        "",
        "Numbers below are measurements from this run. They are not estimates.",
        "",
        f"- Ran: {meta['ran_at']}",
        f"- sherpa-onnx: {meta['sherpa_version']}",
        f"- Clips: `{meta['clips']}`",
        f"- Phrases: `{meta['phrases']}`",
        f"- Whisper tiny.en int8: `{meta['whisper']}`",
        f"- IndicConformer: `{meta['indic']}`",
        f"- Languages measured: {', '.join(meta['languages']) if meta['languages'] else '(none)'}",
        "",
        "English clips are transcribed with Whisper tiny.en (`language=en`, `task=transcribe`).",
        "hi, kn, bn, or, gu, mr, te, ta and ml are transcribed with IndicConformer NeMo CTC",
        "(`<code>.onnx`, `languages/<code>/tokens.txt`, `shared/encoder.weights.bin`).",
        "The file's own sample rate is passed to the recogniser.",
        "",
        "Character error rate is the Levenshtein distance between expected and heard text",
        "after Unicode NFC, casefold, removal of characters whose Unicode category starts with P,",
        "and collapsing whitespace. The rate is edits divided by the number of characters in the",
        "normalized expected text, shown as an exact fraction and a 4-decimal rounding of that fraction.",
        "A row is flagged yes when that rate is above 0.3, when the expected phrase or clip is missing,",
        "when transcription fails, or when the clip is not mono 16-bit PCM, is clipped (a sample at",
        "-32768 or 32767), is silent (every sample is 0), or is outside 0.2 to 8 seconds.",
        "A rate equal to 0.3 is not flagged.",
        "",
        "| Language | Key | Expected text | Heard text | Error rate | Flagged | Audio |",
        "|---|---|---|---|---|---|---|",
    ]
    for row in rows:
        rate = (
            format_rate(row["edits"], row["reference_length"])
            if row["edits"] is not None and row["reference_length"] is not None
            else "n/a"
        )
        notes = row["audio"]
        if row["issues"]:
            issue_text = "; ".join(row["issues"])
            notes = f"{notes}; {issue_text}" if notes else issue_text
        lines.append(
            "| {language} | `{key}` | {expected} | {heard} | {rate} | {flag} | {notes} |".format(
                language=cell(row["language"]),
                key=cell(row["key"]),
                expected=display_text(row["expected"], "(missing from phrase list)"),
                heard=display_text(row["heard"], "(not transcribed)"),
                rate=rate,
                flag="yes" if flagged(row) else "no",
                notes=cell(notes) if notes else "",
            )
        )
    lines.extend(["", "## Summary", ""])
    lines.append("| Language | Rows | Flagged |")
    lines.append("|---|---|---|")
    by_language: dict[str, list[dict]] = {}
    for row in rows:
        by_language.setdefault(row["language"], []).append(row)
    total = 0
    total_flagged = 0
    for language, group in by_language.items():
        count = len(group)
        bad = sum(1 for row in group if flagged(row))
        total += count
        total_flagged += bad
        lines.append(f"| {language} | {count} | {bad} |")
    lines.append(f"| total | {total} | {total_flagged} |")
    lines.append("")
    return "\n".join(lines)


def write_checkpoint(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    serializable = []
    for row in rows:
        item = dict(row)
        serializable.append(item)
    path.write_text(json.dumps(serializable, ensure_ascii=False, indent=2), encoding="utf-8")


def load_checkpoint(path: Path) -> list[dict]:
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError(f"{path} is not a JSON list")
    return data


def self_check() -> None:
    if levenshtein("abc", "ab") != 1 or levenshtein("", "ab") != 2 or levenshtein("ab", "ab") != 0:
        raise SystemExit("Levenshtein self-check failed")
    if normalize_for_cer("Good, keep going") != "good keep going":
        raise SystemExit("punctuation self-check failed")
    if normalize_for_cer("there's") != "theres":
        raise SystemExit("apostrophe self-check failed")
    if normalize_for_cer("करें।") != "करें":
        raise SystemExit("danda self-check failed")
    if above_cer_flag(3, 10) or not above_cer_flag(4, 10):
        raise SystemExit("flag threshold self-check failed")
    if duration_outside(1, 5) or not duration_outside(0, 5) or duration_outside(40, 5) or not duration_outside(41, 5):
        raise SystemExit("duration self-check failed")


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--clips", type=Path, default=script_dir.parents[1] / "app" / "src" / "main" / "assets" / "clips")
    parser.add_argument("--phrases", type=Path, default=script_dir)
    parser.add_argument("--whisper", type=Path, default=WHISPER_DIR)
    parser.add_argument("--indic", type=Path, default=INDIC_DIR)
    parser.add_argument("--lang", default="", help="comma-separated language codes; default is en, hi, kn, then the rest")
    parser.add_argument("--markdown", type=Path, help="write CLIP_CHECK.md to this path")
    parser.add_argument("--checkpoint", type=Path, help="JSON checkpoint outside the repo, rewritten as rows finish")
    parser.add_argument("--resume", action="store_true", help="skip language/key pairs already in the checkpoint")
    return parser.parse_args()


def main() -> int:
    self_check()
    args = parse_args()
    requested = [code.strip() for code in args.lang.split(",") if code.strip()] or None
    languages = discover_languages(args.clips, args.phrases, requested)
    if not languages:
        eprint(f"no languages under {args.clips} or {args.phrases}")
        return 1
    done = load_checkpoint(args.checkpoint) if args.resume and args.checkpoint else []
    done_keys = {(row["language"], row["key"]) for row in done}
    rows = list(done)
    recognizers: dict = {}
    try:
        import sherpa_onnx

        sherpa_version = getattr(sherpa_onnx, "__version__", "unknown")
    except ImportError as exc:
        eprint(f"sherpa_onnx is not installed: {exc}")
        return 1
    for language in languages:
        if args.resume and all(
            (language, key) in done_keys
            for key in _language_keys(language, args.clips, args.phrases)
        ) and _language_keys(language, args.clips, args.phrases):
            eprint(f"skip {language}: checkpoint already has every row")
            continue
        fresh = check_language(language, args.clips, args.phrases, args.whisper, args.indic, recognizers)
        if args.resume:
            fresh = [row for row in fresh if (row["language"], row["key"]) not in done_keys]
        rows.extend(fresh)
        if args.checkpoint:
            write_checkpoint(args.checkpoint, rows)
    from datetime import datetime

    meta = {
        "ran_at": datetime.now().isoformat(timespec="seconds"),
        "sherpa_version": sherpa_version,
        "clips": str(args.clips),
        "phrases": str(args.phrases),
        "whisper": str(args.whisper),
        "indic": str(args.indic),
        "languages": languages,
    }
    markdown = render_markdown(rows, meta)
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(markdown, encoding="utf-8")
        eprint(f"wrote {args.markdown}")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        print(markdown)
    flagged_count = sum(1 for row in rows if flagged(row))
    eprint(f"rows={len(rows)} flagged={flagged_count}")
    return 0


def _language_keys(language: str, clips_dir: Path, phrases_dir: Path) -> list[str]:
    phrases = {}
    phrase_path = phrases_dir / f"phrases_{language}.json"
    if phrase_path.is_file():
        phrases = read_phrases(phrase_path)
    clip_dir = clips_dir / language
    clips = set()
    if clip_dir.is_dir():
        clips = {path.stem for path in clip_dir.glob("*.wav")}
    return ordered_keys(set(phrases) | clips)


if __name__ == "__main__":
    raise SystemExit(main())
