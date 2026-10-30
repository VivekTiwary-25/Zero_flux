#!/usr/bin/env python3
"""Listen to generated CPR Coach clips and record a human review."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Callable

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parents[2]
CLIPS_DIR = PROJECT_DIR / "app" / "src" / "main" / "assets" / "clips"
REVIEWS_DIR = PROJECT_DIR / "docs" / "clip_reviews"


def load_phrases(path: Path) -> dict[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or any(not isinstance(k, str) or not isinstance(v, str) for k, v in data.items()):
        raise ValueError(f"Expected a JSON object of phrase keys and text in {path}")
    return data


def load_reviews(path: Path, language: str, reviewer: str) -> dict:
    if not path.exists():
        return {"language": language, "reviewer": reviewer, "clips": {}}
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or not isinstance(data.get("clips"), dict):
        raise ValueError(f"Invalid review file: {path}")
    return data


def save_reviews(path: Path, reviews: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(reviews, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def review_language(
    language: str,
    reviewer: str,
    clips_dir: Path,
    phrases_dir: Path,
    review_path: Path,
    play: Callable[[Path], None],
    input_fn: Callable[[str], str] = input,
    output_fn: Callable[[str], None] = print,
) -> dict[str, int]:
    translated = load_phrases(phrases_dir / f"phrases_{language}.json")
    english = load_phrases(phrases_dir / "phrases_en.json")
    audio_dir = clips_dir / language
    clips = sorted(audio_dir.glob("*.wav"), key=lambda path: path.stem)
    if not clips:
        raise FileNotFoundError(f"No WAV clips found in {audio_dir}")

    reviews = load_reviews(review_path, language, reviewer)
    results = reviews["clips"]
    counts = {"good": 0, "bad": 0, "skipped": 0}
    for clip in clips:
        key = clip.stem
        old = results.get(key)
        if isinstance(old, dict) and old.get("status") in counts:
            counts[old["status"]] += 1
            output_fn(f"Already reviewed: {key} ({old['status']})")
            continue

        while True:
            output_fn(f"\nKey: {key}")
            output_fn(f"Expected ({language}): {translated.get(key, '[no phrase text]')}")
            output_fn(f"English: {english.get(key, '[no English meaning]')}")
            play(clip)
            answer = input_fn("g = good, b = bad, r = replay, s = skip: ").strip().lower()
            if answer == "r":
                continue
            if answer == "s":
                results[key] = {"status": "skipped"}
            elif answer == "g":
                results[key] = {"status": "good"}
            elif answer == "b":
                note = input_fn("Short note about the clip: ").strip()
                corrected = input_fn("Corrected wording: ").strip()
                results[key] = {"status": "bad", "note": note, "corrected_text": corrected}
            else:
                output_fn("Choose g, b, r, or s.")
                continue
            save_reviews(review_path, reviews)
            counts[results[key]["status"]] += 1
            break

    output_fn(f"Summary: {counts['good']} good, {counts['bad']} bad, {counts['skipped']} skipped.")
    output_fn(f"Review saved to {review_path}")
    return counts


def safe_reviewer_name(name: str) -> str:
    # Keep output confined to clip_reviews on every platform.
    normalized = re.sub(r"[^A-Za-z0-9_-]+", "_", name.strip()).strip("_")
    if not normalized:
        raise ValueError("--name must contain at least one letter or number")
    return normalized


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lang", required=True, help="language code (for example hi)")
    parser.add_argument("--name", required=True, help="reviewer's name")
    args = parser.parse_args(argv)

    if sys.platform != "win32":
        print("Audio playback is supported on Windows only (uses winsound).", file=sys.stderr)
        return 1

    try:
        import winsound

        reviewer = safe_reviewer_name(args.name)
        review_path = REVIEWS_DIR / f"{args.lang}-{reviewer}.json"

        def play(path: Path) -> None:
            winsound.PlaySound(str(path), winsound.SND_FILENAME)

        review_language(
            args.lang,
            reviewer,
            CLIPS_DIR,
            SCRIPT_DIR,
            review_path,
            play,
        )
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Could not review clips: {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nStopped. Completed answers are saved; run the command again to resume.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
