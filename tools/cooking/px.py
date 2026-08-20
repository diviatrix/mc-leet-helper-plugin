"""Parser for `.px` 16x16 pixel-grid files.

Grammar:

    # comments start with '#' and may appear before the body
    # base: R,G,B,A              # transparent default color (optional)
    # palette:
    #   <char> = R,G,B,A          # one entry per palette key
    #   ...

    <16 lines of exactly 16 characters each>

Each character in the body resolves through the palette. The literal '.'
defaults to the base color if `base:` is set; otherwise it must be a palette
key. Whitespace and blank lines between the palette and body are ignored.
"""
import re

PALETTE_LINE = re.compile(r"^\s*([^\s=])\s*=\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*$")
COMMENT_PALETTE_LINE = re.compile(r"^#\s*([^\s=])\s*=\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*$")


def read_px(path):
    with open(path, "r") as f:
        lines = f.read().splitlines()

    palette = {}
    base = None
    body_start = None
    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            # Comments — recognized directives only.
            low = stripped.lower()
            if low.startswith("# base:"):
                base = _parse_color(stripped[len("# base:"):].strip())
            continue
        # First non-comment line is the body start.
        body_start = i
        break

    if body_start is None:
        raise ValueError(f"{path}: empty .px file")

    # Walk back to find the palette block — it's any '# key = r,g,b,a' lines
    # immediately before body_start (whitespace-only lines in between are OK).
    i = body_start - 1
    while i >= 0:
        s = lines[i].strip()
        if not s:
            i -= 1
            continue
        m = COMMENT_PALETTE_LINE.match(s)
        if m:
            key = m.group(1)
            palette[key] = (int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5)))
            i -= 1
            continue
        break

    body = [line for line in lines[body_start:] if line.strip() != ""]
    if len(body) != 16:
        raise ValueError(f"{path}: body must be exactly 16 non-empty lines (got {len(body)})")
    rows = []
    for y, line in enumerate(body):
        if len(line) != 16:
            raise ValueError(f"{path}: line {y} must be exactly 16 chars (got {len(line)}): {line!r}")
        row = []
        for x, ch in enumerate(line):
            if ch == "." and base is not None:
                row.append(base)
            elif ch in palette:
                row.append(palette[ch])
            else:
                raise ValueError(f"{path}:{y+1}:{x+1}: char {ch!r} is not in the palette")
        rows.append(tuple(row))
    return palette, tuple(rows)


def _parse_color(s):
    parts = [int(p) for p in s.split(",")]
    if len(parts) != 4:
        raise ValueError(f"expected R,G,B,A; got {s!r}")
    return tuple(parts)