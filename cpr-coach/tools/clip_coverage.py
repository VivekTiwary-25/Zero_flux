#!/usr/bin/env python3
"""Check that every spoken CPR Coach clip key has a wav in every language.

Usage:
    python clip_coverage.py [--root CPR_COACH_DIR]
    python clip_coverage.py --self-check

Stdlib only. Default root is the parent of this file (the cpr-coach directory).

Keys come from the Phrase enum in engine/CoachPolicy.kt, em_* string literals
in engine/EmergencyCoach.kt and EmergencyHost.kt, and literal play("...") calls
under app/src/main/java. em_* clip keys in engine/EmergencySequence.kt are
included too: those clips are played through audio.play(key), not play("...").

A cell is OK when manifest.json lists the key and <key>.wav exists and is not
empty. NOT IN MANIFEST means the key is absent from that language manifest.
MISSING WAV means the key is listed and the wav is missing or empty.
EmergencyWord and em_step_* values are on-screen string keys, not voice clips.
They are listed with role screen and do not fail the process exit code.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from pathlib import Path


OK = "OK"
MISSING_WAV = "MISSING WAV"
NOT_IN_MANIFEST = "NOT IN MANIFEST"

PLAYED = "played"
SCREEN = "screen"

JAVA_ROOT = Path("app/src/main/java")
CLIPS_ROOT = Path("app/src/main/assets/clips")
CONTRACT = JAVA_ROOT / "com/zeroflux/cprcoach/ui/Contract.kt"


def eprint(message: str) -> None:
    print(message, file=sys.stderr)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def strip_kotlin_comments(text: str) -> str:
    """Drop // and /* */ comments. Leave string literals intact."""
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            out.append(" ")
            i = n if end < 0 else end + 2
            continue
        if text.startswith("//", i):
            end = text.find("\n", i)
            out.append("\n" if end >= 0 else "")
            i = n if end < 0 else end
            continue
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            if end < 0:
                out.append(text[i:])
                break
            out.append(text[i : end + 3])
            i = end + 3
            continue
        if text[i] == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(text[i:j])
            i = j
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def brace_body(text: str, open_index: int) -> str:
    depth = 0
    i = open_index
    while i < len(text):
        char = text[i]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[open_index + 1 : i]
        i += 1
    raise ValueError("unclosed brace")


def enum_body(text: str, name: str) -> str:
    match = re.search(rf"enum\s+class\s+{name}\b[^{{]*\{{", text)
    if match is None:
        raise ValueError(f"enum class {name} not found")
    return brace_body(text, match.end() - 1)


def quoted_em(text: str) -> list[str]:
    return re.findall(r'"(em_[A-Za-z0-9_]+)"', text)


def find_under(root: Path, name: str) -> Path:
    matches = sorted(path for path in root.rglob(name) if path.is_file())
    if not matches:
        raise FileNotFoundError(f"missing {name} under {root}")
    if len(matches) > 1:
        joined = ", ".join(str(path) for path in matches)
        raise FileNotFoundError(f"more than one {name}: {joined}")
    return matches[0]


def rel(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix()


class KeySet:
    def __init__(self) -> None:
        self.order: list[str] = []
        self.role: dict[str, str] = {}
        self.sources: dict[str, list[str]] = {}

    def add(self, key: str, role: str, source: str) -> None:
        if key not in self.sources:
            self.order.append(key)
            self.role[key] = role
            self.sources[key] = [source]
            return
        if source not in self.sources[key]:
            self.sources[key].append(source)
        if role == PLAYED:
            self.role[key] = PLAYED


def phrase_keys(path: Path) -> list[str]:
    text = strip_kotlin_comments(read_text(path))
    body = enum_body(text, "Phrase")
    keys = re.findall(r"[A-Z][A-Z0-9_]*\s*\(\s*\"([^\"\\$]+)\"\s*\)", body)
    if not keys:
        raise ValueError(f"no Phrase keys in {path}")
    return keys


def play_literals(java_root: Path) -> list[tuple[str, str]]:
    found: list[tuple[str, str]] = []
    pattern = re.compile(r"(?<![\w])play\s*\(\s*(?:key\s*=\s*)?\"([^\"\\$]+)\"")
    files = sorted(java_root.rglob("*.kt")) + sorted(java_root.rglob("*.java"))
    for path in files:
        text = strip_kotlin_comments(read_text(path))
        for key in pattern.findall(text):
            found.append((key, path.as_posix()))
    return found


def host_em_keys(path: Path) -> tuple[list[str], list[str]]:
    """Return (playback keys, screen keys) from EmergencyHost.kt."""
    text = strip_kotlin_comments(read_text(path))
    played: list[str] = []
    screen: list[str] = []
    seen_played: set[str] = set()
    seen_screen: set[str] = set()
    branched: set[str] = set()
    for left, right in re.findall(
        r'"(em_[A-Za-z0-9_]+)"\s*->\s*"(em_[A-Za-z0-9_]+)"',
        text,
    ):
        branched.add(left)
        branched.add(right)
        if left not in seen_played:
            played.append(left)
            seen_played.add(left)
        if right not in seen_screen:
            screen.append(right)
            seen_screen.add(right)
    for key in quoted_em(text):
        if key in branched:
            continue
        if key not in seen_played:
            played.append(key)
            seen_played.add(key)
    return played, screen


def coach_em_keys(path: Path) -> tuple[list[str], list[str]]:
    """Return (other em_* keys, EmergencyWord em_* keys)."""
    text = strip_kotlin_comments(read_text(path))
    word_body = enum_body(text, "EmergencyWord")
    screen = []
    seen_screen: set[str] = set()
    for key in re.findall(r"[A-Z][A-Z0-9_]*\s*\(\s*\"(em_[A-Za-z0-9_]+)\"\s*\)", word_body):
        if key not in seen_screen:
            screen.append(key)
            seen_screen.add(key)
    played = []
    seen_played: set[str] = set()
    for key in quoted_em(text):
        if key in seen_screen or key in seen_played:
            continue
        played.append(key)
        seen_played.add(key)
    return played, screen


def sequence_em_keys(path: Path) -> list[str]:
    text = strip_kotlin_comments(read_text(path))
    keys: list[str] = []
    seen: set[str] = set()
    for key in quoted_em(text):
        if key not in seen:
            keys.append(key)
            seen.add(key)
    return keys


def languages(path: Path) -> list[str]:
    text = strip_kotlin_comments(read_text(path))
    body = enum_body(text, "AppLanguage")
    codes = re.findall(r"[A-Z][A-Z0-9_]*\s*\(\s*\"([a-z]{2})\"\s*,", body)
    if not codes:
        raise ValueError(f"no AppLanguage codes in {path}")
    return codes


def collect(root: Path) -> tuple[KeySet, list[str], list[str]]:
    java_root = root / JAVA_ROOT
    if not java_root.is_dir():
        raise FileNotFoundError(f"missing {java_root}")
    notes: list[str] = []
    keys = KeySet()

    policy = find_under(java_root, "CoachPolicy.kt")
    if policy.parent.name != "engine":
        raise FileNotFoundError(f"CoachPolicy.kt is not under engine/: {policy}")
    for key in phrase_keys(policy):
        keys.add(key, PLAYED, "Phrase")

    for key, _path in play_literals(java_root):
        keys.add(key, PLAYED, "play()")

    sequence_path = java_root / "com/zeroflux/cprcoach/engine/EmergencySequence.kt"
    if sequence_path.is_file():
        for key in sequence_em_keys(sequence_path):
            keys.add(key, PLAYED, "EmergencySequence.kt")
    else:
        notes.append("EmergencySequence.kt not found; em_aed, em_continue, and em_swap were not read from it")

    host = find_under(java_root, "EmergencyHost.kt")
    host_played, host_screen = host_em_keys(host)
    for key in host_played:
        keys.add(key, PLAYED, "EmergencyHost.kt")
    for key in host_screen:
        keys.add(key, SCREEN, "EmergencyHost.kt")

    coach = find_under(java_root, "EmergencyCoach.kt")
    if coach.parent.name != "engine":
        raise FileNotFoundError(f"EmergencyCoach.kt is not under engine/: {coach}")
    coach_played, coach_screen = coach_em_keys(coach)
    for key in coach_played:
        keys.add(key, PLAYED, "EmergencyCoach.kt")
    for key in coach_screen:
        keys.add(key, SCREEN, "EmergencyCoach.kt")

    contract = root / CONTRACT
    if not contract.is_file():
        found = list(java_root.rglob("Contract.kt"))
        if len(found) != 1:
            raise FileNotFoundError(f"missing {contract}")
        contract = found[0]
    codes = languages(contract)
    return keys, codes, notes


def manifest_keys(path: Path) -> set[str]:
    data = json.loads(read_text(path))
    if not isinstance(data, list):
        raise ValueError(f"{path} is not a list")
    keys: set[str] = set()
    for item in data:
        if isinstance(item, dict) and isinstance(item.get("key"), str):
            keys.add(item["key"])
    return keys


def wav_names(directory: Path) -> dict[str, int]:
    """Exact filename -> size, for *.wav entries only."""
    found: dict[str, int] = {}
    if not directory.is_dir():
        return found
    for entry in directory.iterdir():
        if entry.is_file() and entry.name.endswith(".wav"):
            found[entry.name] = entry.stat().st_size
    return found


def cell_status(in_manifest: bool, wav_size: int | None) -> str:
    if not in_manifest:
        return NOT_IN_MANIFEST
    if wav_size is None or wav_size <= 0:
        return MISSING_WAV
    return OK


def load_languages(
    clips_root: Path,
    codes: list[str],
) -> tuple[dict[str, set[str]], dict[str, dict[str, int]], list[str]]:
    manifests: dict[str, set[str]] = {}
    wavs: dict[str, dict[str, int]] = {}
    notes: list[str] = []
    for code in codes:
        folder = clips_root / code
        manifest_path = folder / "manifest.json"
        if not folder.is_dir():
            notes.append(f"{code}: clips folder missing")
            manifests[code] = set()
            wavs[code] = {}
            continue
        wavs[code] = wav_names(folder)
        if not manifest_path.is_file():
            notes.append(f"{code}: manifest.json missing")
            manifests[code] = set()
            continue
        try:
            manifests[code] = manifest_keys(manifest_path)
        except (OSError, ValueError, json.JSONDecodeError) as error:
            notes.append(f"{code}: manifest.json unreadable ({error})")
            manifests[code] = set()
    return manifests, wavs, notes


def extra_clips(
    codes: list[str],
    required: set[str],
    manifests: dict[str, set[str]],
    wavs: dict[str, dict[str, int]],
) -> list[str]:
    names: set[str] = set()
    for code in codes:
        names.update(manifests[code])
        names.update(name[:-4] for name in wavs[code])
    lines: list[str] = []
    for key in sorted(names - required):
        in_manifest = [code for code in codes if key in manifests[code]]
        has_wav = [
            code
            for code in codes
            if wavs[code].get(f"{key}.wav", 0) > 0
        ]
        missing_manifest = [code for code in codes if code not in in_manifest]
        missing_wav = [code for code in codes if code not in has_wav]
        lines.append(
            f"{key}: manifest in {', '.join(in_manifest) or 'none'}"
            f"; wav in {', '.join(has_wav) or 'none'}"
            + (f"; manifest missing in {', '.join(missing_manifest)}" if missing_manifest else "")
            + (f"; wav missing in {', '.join(missing_wav)}" if missing_wav else "")
        )
    return lines


def render(
    root: Path,
    keys: KeySet,
    codes: list[str],
    grid: dict[tuple[str, str], str],
    notes: list[str],
    extras: list[str],
) -> str:
    header = ["key", "role", *codes]
    rows = [header, ["---"] * len(header)]
    for key in keys.order:
        rows.append([f"`{key}`", keys.role[key], *[grid[(key, code)] for code in codes]])
    lines = [
        f"root: {root}",
        f"languages ({len(codes)}): {', '.join(codes)}",
        f"keys: {len(keys.order)}",
        "",
        "| " + " | ".join(rows[0]) + " |",
        "| " + " | ".join(rows[1]) + " |",
    ]
    for row in rows[2:]:
        lines.append("| " + " | ".join(row) + " |")
    lines.append("")
    for role in (PLAYED, SCREEN):
        role_keys = [key for key in keys.order if keys.role[key] == role]
        cells = [grid[(key, code)] for key in role_keys for code in codes]
        lines.append(
            f"{role}: {len(role_keys)} keys, {len(cells)} cells, "
            f"OK {cells.count(OK)}, "
            f"MISSING WAV {cells.count(MISSING_WAV)}, "
            f"NOT IN MANIFEST {cells.count(NOT_IN_MANIFEST)}"
        )
    played_gaps = [
        f"{key}/{code} {grid[(key, code)]}"
        for key in keys.order
        if keys.role[key] == PLAYED
        for code in codes
        if grid[(key, code)] != OK
    ]
    if played_gaps:
        lines.append("played gaps: " + "; ".join(played_gaps))
        lines.append("result: played clips incomplete")
    else:
        lines.append("played gaps: none")
        lines.append("result: played clips complete")
    if notes:
        lines.append("")
        lines.append("notes:")
        lines.extend(f"- {note}" for note in notes)
    lines.append("")
    if extras:
        lines.append("clips not required by the app sources:")
        lines.extend(f"- {line}" for line in extras)
    else:
        lines.append("clips not required by the app sources: none")
    lines.append("")
    return "\n".join(lines)


def check(root: Path) -> tuple[str, int]:
    keys, codes, notes = collect(root)
    clips_root = root / CLIPS_ROOT
    if not clips_root.is_dir():
        raise FileNotFoundError(f"missing {clips_root}")
    if len(codes) != 10:
        notes.append(f"AppLanguage has {len(codes)} codes, not 10")
    manifests, wavs, load_notes = load_languages(clips_root, codes)
    notes.extend(load_notes)
    grid: dict[tuple[str, str], str] = {}
    for key in keys.order:
        for code in codes:
            size = wavs[code].get(f"{key}.wav")
            grid[(key, code)] = cell_status(key in manifests[code], size)
    extras = extra_clips(codes, set(keys.order), manifests, wavs)
    text = render(root, keys, codes, grid, notes, extras)
    played_bad = any(
        grid[(key, code)] != OK
        for key in keys.order
        if keys.role[key] == PLAYED
        for code in codes
    )
    return text, (1 if played_bad else 0)


def write_fixture(root: Path) -> None:
    java = root / JAVA_ROOT / "com/zeroflux/cprcoach"
    engine = java / "engine"
    ui = java / "ui"
    engine.mkdir(parents=True)
    ui.mkdir(parents=True)
    (engine / "CoachPolicy.kt").write_text(
        'enum class Phrase(val key: String) {\n'
        '    KEEP_GOING("keep_going"),\n'
        '    GOOD("good"),\n'
        '}\n',
        encoding="utf-8",
    )
    (engine / "EmergencyCoach.kt").write_text(
        'enum class EmergencyWord(val key: String) {\n'
        '    FASTER("em_word_faster"),\n'
        '}\n',
        encoding="utf-8",
    )
    (engine / "EmergencySequence.kt").write_text(
        'const val AED = "em_aed"\n',
        encoding="utf-8",
    )
    (java / "EmergencyHost.kt").write_text(
        'stepKey = when (key) {\n'
        '    "em_call" -> "em_step_call"\n'
        '}\n',
        encoding="utf-8",
    )
    (engine / "PractiseSession.kt").write_text(
        'audio.play("countdown")\n'
        '// audio.play("commented_out")\n'
        '/* audio.play("blocked") */\n',
        encoding="utf-8",
    )
    (ui / "Contract.kt").write_text(
        'enum class AppLanguage(val code: String, val nativeName: String) {\n'
        '    EN("en", "English"),\n'
        '    HI("hi", "Hindi"),\n'
        '}\n',
        encoding="utf-8",
    )
    en = root / CLIPS_ROOT / "en"
    hi = root / CLIPS_ROOT / "hi"
    en.mkdir(parents=True)
    hi.mkdir(parents=True)
    (en / "manifest.json").write_text(
        json.dumps(
            [
                {"key": "keep_going"},
                {"key": "good"},
                {"key": "countdown"},
                {"key": "em_aed"},
            ]
        ),
        encoding="utf-8",
    )
    (en / "keep_going.wav").write_bytes(b"RIFF")
    (en / "countdown.wav").write_bytes(b"RIFF")
    (en / "em_aed.wav").write_bytes(b"RIFF")
    (en / "em_call.wav").write_bytes(b"RIFF")
    (en / "good.wav").write_bytes(b"")
    (hi / "keep_going.wav").write_bytes(b"RIFF")


def self_check() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        write_fixture(root)
        keys, codes, _notes = collect(root)
        expected = [
            ("keep_going", PLAYED),
            ("good", PLAYED),
            ("countdown", PLAYED),
            ("em_aed", PLAYED),
            ("em_call", PLAYED),
            ("em_step_call", SCREEN),
            ("em_word_faster", SCREEN),
        ]
        got = [(key, keys.role[key]) for key in keys.order]
        if got != expected:
            raise SystemExit(f"self-check keys {got} != {expected}")
        if "commented_out" in keys.role or "blocked" in keys.role:
            raise SystemExit("self-check collected a commented play() key")
        if codes != ["en", "hi"]:
            raise SystemExit(f"self-check languages {codes}")
        text, code = check(root)
        if code != 1:
            raise SystemExit("self-check expected exit 1")
        required = {
            ("keep_going", "en"): OK,
            ("good", "en"): MISSING_WAV,
            ("countdown", "en"): OK,
            ("em_aed", "en"): OK,
            ("em_call", "en"): NOT_IN_MANIFEST,
            ("em_step_call", "en"): NOT_IN_MANIFEST,
            ("em_word_faster", "en"): NOT_IN_MANIFEST,
            ("keep_going", "hi"): NOT_IN_MANIFEST,
            ("em_aed", "hi"): NOT_IN_MANIFEST,
        }
        for (key, lang), status in required.items():
            needle = f"| `{key}` |"
            row = next(line for line in text.splitlines() if line.startswith(needle))
            cells = [part.strip() for part in row.strip("|").split("|")]
            index = 2 if lang == "en" else 3
            if cells[index] != status:
                raise SystemExit(f"self-check {key}/{lang} cell {cells[index]} != {status}\n{row}")
        if "commented_out" in text or "blocked" in text:
            raise SystemExit("self-check printed a commented key")
    print("self-check: ok")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parent.parent,
        help="cpr-coach directory (default: parent of this script)",
    )
    parser.add_argument("--self-check", action="store_true", help="run the built-in status checks")
    return parser.parse_args()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parse_args()
    if args.self_check:
        self_check()
        return 0
    try:
        text, code = check(args.root.resolve())
    except (OSError, ValueError, FileNotFoundError) as error:
        eprint(f"error: {error}")
        return 2
    sys.stdout.write(text)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
