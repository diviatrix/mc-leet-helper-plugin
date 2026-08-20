"""Shared helpers for the texture-generation scripts.

Run scripts from the repo root so the relative `OUT` path resolves to
`src/main/resources/resource_pack/assets/leet/textures/item` (the path the
plugin's resource_pack builder reads). All scripts in this folder should
import `OUT` and `make` from here instead of redefining them.
"""
from PIL import Image
import os

OUT = "src/main/resources/resource_pack/assets/leet/textures/item"
os.makedirs(OUT, exist_ok=True)


def make(name, draw):
    """Render a 16x16 RGBA image using the supplied `draw(px)` callback."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    draw(px)
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)