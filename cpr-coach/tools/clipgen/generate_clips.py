#!/usr/bin/env python3
"""Generate normalized, validated speech clips from the CPR Coach phrase files."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import wave
from array import array
from pathlib import Path

LANGUAGES = ("en", "hi", "kn", "bn", "or", "gu", "mr", "te", "ta", "ml")
PIPER = {
    "en": ("vits-piper-en_US-ryan-medium", "en_US-ryan-medium.onnx"),
    "hi": ("vits-piper-hi_IN-pratham-medium", "hi_IN-pratham-medium.onnx"),
    "ml": ("vits-piper-ml_IN-arjun-medium", "ml_IN-arjun-medium.onnx"),
}
MMS = {
    "bn": "vits-mms-ben",
    "or": "vits-mms-ory",
    "gu": "vits-mms-guj",
    "mr": "vits-mms-mar",
    "te": "vits-mms-tel",
    "kn": "vits-mms-kan",
    "ta": "vits-mms-tam",
}
PEAK = 10 ** (-3 / 20)
TRIM_THRESHOLD = 0.001


def read_phrases(path: Path) -> dict[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or any(not isinstance(k, str) or not isinstance(v, str) for k, v in data.items()):
        raise ValueError(f"Expected a flat string-to-string JSON object in {path}")
    return data


def mms_characters(tokens_path: Path) -> set[str]:
    chars = set()
    for line in tokens_path.read_text(encoding="utf-8").splitlines():
        if not line:
            continue
        token, separator, index = line.rpartition(" ")
        if separator and index.isdigit() and len(token) == 1:
            chars.add(token)
    return chars


def prepare_mms(text: str, allowed: set[str], language: str, key: str) -> str:
    if any(char.isdigit() for char in text):
        raise ValueError("MMS phrase contains digits")
    kept = []
    dropped = []
    for char in text:
        if char in allowed:
            kept.append(char)
        else:
            dropped.append(char)
    if dropped:
        print(f"  dropped {language}/{key}: {''.join(dropped)!r}")
    result = "".join(kept).strip()
    if not result:
        raise ValueError("MMS frontend removed the entire phrase")
    return result


def load_voice(language: str, models: Path):
    import sherpa_onnx

    if language in PIPER:
        folder, model_name = PIPER[language]
        voice_dir = models / folder
        model_path = voice_dir / model_name
        if not (voice_dir / "espeak-ng-data").is_dir():
            raise FileNotFoundError(f"Missing Piper espeak-ng-data: {voice_dir / 'espeak-ng-data'}")
        vits = sherpa_onnx.OfflineTtsVitsModelConfig(
            model=str(model_path), tokens=str(voice_dir / "tokens.txt"),
            data_dir=str(voice_dir / "espeak-ng-data"),
        )
        model_config = sherpa_onnx.OfflineTtsModelConfig(vits=vits)
        return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=model_config)), None
    voice_dir = models / MMS[language]
    tokens_path = voice_dir / "tokens.txt"
    vits = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=str(voice_dir / "model.onnx"), tokens=str(tokens_path),
    )
    model_config = sherpa_onnx.OfflineTtsModelConfig(vits=vits)
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=model_config)), mms_characters(tokens_path)


def normalize(samples) -> array:
    values = array("f", samples)
    if not values or any(not math.isfinite(value) for value in values):
        raise ValueError("TTS returned empty or non-finite audio")
    first = 0
    last = len(values)
    while first < last and abs(values[first]) < TRIM_THRESHOLD:
        first += 1
    while last > first and abs(values[last - 1]) < TRIM_THRESHOLD:
        last -= 1
    values = values[first:last]
    if not values:
        raise ValueError("TTS returned silence")
    peak = max(abs(value) for value in values)
    if peak == 0:
        raise ValueError("TTS returned silence")
    gain = PEAK / peak
    pcm = array("h")
    for value in values:
        scaled = max(-1.0, min(1.0, value * gain))
        pcm.append(round(scaled * 32767))
    return pcm


def write_clip(path: Path, pcm: array, sample_rate: int) -> float:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        output.writeframes(pcm.tobytes())
    duration = len(pcm) / sample_rate
    if not 0.2 <= duration <= 8:
        path.unlink(missing_ok=True)
        raise ValueError(f"duration {duration:.3f}s is outside 0.2–8 seconds")
    return duration


def generate(language: str, models: Path, output: Path, dry_run: bool) -> tuple[list[dict], list[str]]:
    phrase_path = Path(__file__).with_name(f"phrases_{language}.json")
    phrases = read_phrases(phrase_path)
    entries: list[dict] = []
    failures: list[str] = []
    try:
        tts, allowed = load_voice(language, models)
    except Exception as error:
        return entries, [f"voice initialization: {error}"]

    for key, text in phrases.items():
        try:
            input_text = prepare_mms(text, allowed, language, key) if allowed is not None else text
            if dry_run:
                entries.append({"code": language, "key": key, "text": text,
                                "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest()})
                continue
            audio = tts.generate(input_text)
            sample_rate = int(audio.sample_rate)
            if sample_rate <= 0:
                raise ValueError(f"invalid model sample rate {sample_rate}")
            pcm = normalize(audio.samples)
            duration = write_clip(output / language / f"{key}.wav", pcm, sample_rate)
            entries.append({"code": language, "key": key, "text": text,
                            "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest()})
            print(f"  {language}/{key}: {duration:.3f}s @ {sample_rate} Hz")
        except Exception as error:
            failures.append(f"{key}: {error}")
            print(f"  FAILED {language}/{key}: {error}", file=sys.stderr)
    if not dry_run:
        manifest_path = output / language / "manifest.json"
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        manifest_path.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return entries, failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", required=True, type=Path, help="Folder containing the vits-* voice folders")
    parser.add_argument("--out", required=True, type=Path, help="App assets/clips output directory")
    parser.add_argument("--lang", default=",".join(LANGUAGES), help="Comma-separated language codes")
    parser.add_argument("--dry-run", action="store_true", help="Check phrase/model inputs without synthesizing audio")
    args = parser.parse_args()
    languages = [code.strip() for code in args.lang.split(",") if code.strip()]
    if not languages or any(code not in LANGUAGES for code in languages):
        parser.error(f"--lang must contain codes from: {', '.join(LANGUAGES)}")
    failed = False
    for language in languages:
        print(f"{language}:")
        try:
            entries, errors = generate(language, args.models, args.out, args.dry_run)
        except Exception as error:
            entries, errors = [], [str(error)]
        print(f"  {len(entries)} {'phrases checked' if args.dry_run else 'clips generated'}, {len(errors)} failed")
        for error in errors:
            print(f"  ERROR: {error}", file=sys.stderr)
        failed |= bool(errors)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
