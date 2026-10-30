"""Make labelled contact sheets from CPR Coach debug frame dumps.

Usage:
    python frame_sheet.py <traces_dir> <session_id> <out_dir> \
        [--from S] [--to S] [--cols 4] [--every N]

Requires Python 3 and Pillow (pip install Pillow). Reads session-<id>.csv and
frames-<id>/<t_ms>.jpg. Times and inclusive --from/--to limits are seconds
relative to the first CSV row. Frames are sorted numerically, then filtered,
then sampled with --every. Nearest-row ties use the earlier timestamp.
Each sheet holds at most 36 frames, in native-size cells with black label
bars, and is saved at JPEG quality 85 as <id>_<first>-<last>.jpg (seconds,
three decimal places). Printed paths identify the sheets actually written.
Input files are only parsed as CSV or decoded as JPEG; nothing is executed.
"""

import argparse
from bisect import bisect_left
import csv
import math
from pathlib import Path
import sys
import warnings

from PIL import Image, ImageDraw, ImageFont


JOINTS = ("hip", "shoulder", "elbow", "wrist")
COLORS = ("magenta", "yellow", "cyan", "red")
MAX_FRAMES = 36
LABEL_HEIGHT = 38


def warn(message):
    print("warning: " + message, file=sys.stderr)


def number(value):
    """Return a finite CSV number, or None for missing/invalid data."""
    try:
        result = float(value)
    except (ValueError, TypeError):
        return None
    return result if math.isfinite(result) else None


def load_trace(path):
    rows = []
    origin = None
    with path.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        required = {"t_ms", "side", "side_on"}
        required.update(joint + axis for joint in JOINTS for axis in ("_x", "_y"))
        if not required.issubset(reader.fieldnames or []):
            raise ValueError("CSV is missing columns: " + ", ".join(sorted(required - set(reader.fieldnames or []))))
        for line, row in enumerate(reader, start=2):
            try:
                timestamp = int(row["t_ms"])
            except (ValueError, TypeError):
                warn(f"{path}:{line}: invalid t_ms; skipping row")
                continue
            if origin is None:
                origin = timestamp
            rows.append((timestamp, row))
    if not rows:
        raise ValueError(f"{path}: no valid CSV rows")
    rows.sort(key=lambda entry: entry[0])
    return origin, [entry[0] for entry in rows], [entry[1] for entry in rows]


def nearest_row(timestamp, times, rows):
    index = bisect_left(times, timestamp)
    if index == len(times) or (index > 0 and timestamp - times[index - 1] <= times[index] - timestamp):
        index -= 1
    return rows[index]


def elbow_angle(points, aspect):
    shoulder, elbow, wrist = points[1:]
    ux, uy = (shoulder[0] - elbow[0]) * aspect, shoulder[1] - elbow[1]
    vx, vy = (wrist[0] - elbow[0]) * aspect, wrist[1] - elbow[1]
    if math.hypot(ux, uy) == 0 or math.hypot(vx, vy) == 0:
        return None
    return math.degrees(math.atan2(abs(ux * vy - uy * vx), ux * vx + uy * vy))


def label_frame(image, row, seconds):
    width, height = image.size
    tile = Image.new("RGB", (width, height + LABEL_HEIGHT), "black")
    tile.paste(image, (0, 0))
    draw = ImageDraw.Draw(tile)
    font = ImageFont.load_default()
    points = [(number(row.get(joint + "_x")), number(row.get(joint + "_y"))) for joint in JOINTS]
    if any(x is None or y is None for x, y in points):
        lines = (f"{seconds:.3f}s  NO POSE",)
    else:
        pixels = [(x * width, y * height) for x, y in points]
        draw.line(pixels, fill="white", width=3)
        for (x, y), color in zip(pixels, COLORS):
            # Clip dots to the photograph, not the label bar.
            if 0 <= x < width and 0 <= y < height:
                draw.ellipse((max(0, x - 4), max(0, y - 4), min(width - 1, x + 4), min(height - 1, y + 4)), fill=color)
        # Restore the bar in case an off-image segment crossed it.
        draw.rectangle((0, height, width, height + LABEL_HEIGHT), fill="black")
        angle = elbow_angle(points, width / height)
        angle_text = "n/a" if angle is None else f"{angle:.1f} deg"
        visibilities = [number(row.get(joint + "_vis")) for joint in JOINTS]
        visibility = min(visibilities) if all(v is not None for v in visibilities) else number(row.get("min_vis"))
        vis_text = "n/a" if visibility is None else f"{visibility:.2f}"
        side = row.get("side") or "?"
        side_on = row.get("side_on") or "?"
        lines = (f"{seconds:.3f}s  elbow={angle_text}", f"min_vis={vis_text}  side={side}  side_on={side_on}")
    for index, text in enumerate(lines):
        draw.text((4, height + 3 + index * 16), text, fill="white", font=font)
    return tile


def save_sheet(tiles, session, out_dir, columns):
    columns = min(columns, len(tiles))
    cell_width = max(tile.width for _, tile in tiles)
    cell_height = max(tile.height for _, tile in tiles)
    sheet = Image.new("RGB", (columns * cell_width, math.ceil(len(tiles) / columns) * cell_height), "black")
    try:
        for index, (_, tile) in enumerate(tiles):
            sheet.paste(tile, ((index % columns) * cell_width, (index // columns) * cell_height))
        start, end = tiles[0][0], tiles[-1][0]
        path = out_dir / f"{session}_{start:.3f}-{end:.3f}.jpg"
        sheet.save(path, "JPEG", quality=85)
        print(path)
    finally:
        sheet.close()
        for _, tile in tiles:
            tile.close()


def make_sheets(args):
    origin, times, rows = load_trace(args.traces_dir / f"session-{args.session_id}.csv")
    frames = []
    for path in (args.traces_dir / f"frames-{args.session_id}").iterdir():
        if path.suffix.lower() != ".jpg" or not path.is_file():
            continue
        try:
            timestamp = int(path.stem)
        except ValueError:
            warn(f"{path}: invalid frame timestamp; skipping image")
            continue
        seconds = (timestamp - origin) / 1000
        if args.start is not None and seconds < args.start:
            continue
        if args.end is not None and seconds > args.end:
            continue
        frames.append((timestamp, path))
    frames.sort(key=lambda entry: (entry[0], entry[1].name))
    args.out_dir.mkdir(parents=True, exist_ok=True)
    tiles = []
    written = False
    for timestamp, path in frames[::args.every]:
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("error", Image.DecompressionBombWarning)
                with Image.open(path, formats=["JPEG"]) as source:
                    source.load()
                    image = source.convert("RGB")
        except (OSError, ValueError, Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
            warn(f"{path}: unreadable image; skipping ({error})")
            continue
        seconds = (timestamp - origin) / 1000
        try:
            tile = label_frame(image, nearest_row(timestamp, times, rows), seconds)
        finally:
            image.close()
        tiles.append((seconds, tile))
        if len(tiles) == MAX_FRAMES:
            save_sheet(tiles, args.session_id, args.out_dir, args.cols)
            written = True
            tiles = []
    if tiles:
        save_sheet(tiles, args.session_id, args.out_dir, args.cols)
        written = True
    if not written:
        warn("no readable frames matched the selection")


def positive_integer(value):
    result = int(value)
    if result < 1:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return result


def finite_seconds(value):
    result = number(value)
    if result is None:
        raise argparse.ArgumentTypeError("must be finite seconds")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("traces_dir", type=Path)
    parser.add_argument("session_id")
    parser.add_argument("out_dir", type=Path)
    parser.add_argument("--from", dest="start", type=finite_seconds)
    parser.add_argument("--to", dest="end", type=finite_seconds)
    parser.add_argument("--cols", type=positive_integer, default=4)
    parser.add_argument("--every", type=positive_integer, default=1)
    args = parser.parse_args()
    if args.session_id in (".", "..") or any(char in args.session_id for char in '/\\:'):
        parser.error("session_id must be a single filename component")
    if args.start is not None and args.end is not None and args.start > args.end:
        parser.error("--from must not exceed --to")
    try:
        make_sheets(args)
    except (OSError, ValueError, csv.Error) as error:
        parser.exit(1, f"error: {error}\n")


if __name__ == "__main__":
    main()
