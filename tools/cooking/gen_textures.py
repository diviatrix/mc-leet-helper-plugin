"""Render all `leet:` item textures from `.px` grid files.

A `.px` file is a tiny text format suited for hand-editing 16x16 pixel art.
See `tools/cooking/README.md` for the grammar and `tools/cooking/px.py` for the
parser. Every item defined in `plugins/LeetCrafting/features/crafting.yml`
that has a custom texture must appear both in this list AND as a `.px` file
under `tools/cooking/textures/` — otherwise the on-disk `*.png` will fall
behind the source-of-truth.

Run from the repo root:

    python3 tools/cooking/gen_textures.py
"""
import os

from _common import OUT, make
from px import read_px


TEXTURES_DIR = os.path.join(os.path.dirname(__file__), "textures")


def render(name, path):
    palette, pixels = read_px(path)
    def draw(px):
        for y, row in enumerate(pixels):
            for x, color in enumerate(row):
                px[x, y] = color
    make(name, draw)


# Every item in features/crafting.yml with a custom texture. To add a new item:
# 1. Drop its `*.px` file under tools/cooking/textures/.
# 2. Append its id to this list.
NAMES = [
    "salt",
    "dough",
    "croissant",
    "borsh",
    "pelmeni",
    "instant-noodle",
    "ramen",
    "banh-mi",
    "milk-porridge",
    "creamy-mushroom-soup",
    "chicken-skewers",
    "charlotte",
    "pretzel",
    "beef-jerky",
    "chicken-jerky",
    "hamon",
    "potato-chips",
    "dry-salmon",
    "dry-cod",
    "chocolate-bar",
    "chocolate-piece",
]


for name in NAMES:
    path = os.path.join(TEXTURES_DIR, name + ".px")
    if not os.path.isfile(path):
        print("MISSING", path)
        continue
    render(name, path)

print("done")