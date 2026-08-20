# tools/cooking

One-off and maintenance Python tooling for the Crafting feature. All scripts
are written against **repo-root-relative** paths and must be run **from the
repo root**:

```bash
python3 tools/cooking/<script>.py
```

They read/write `export/*.json` and `src/main/resources/features/crafting.yml` /
`src/main/resources/resource_pack/assets/leet/textures/item`, so keep the working
directory at the repository root (not inside `tools/cooking/`).

## Food-value pipeline (in dependency order)

1. `export_vanilla_food.py` — parse the server's `export/FOODS_raw.txt` into
   `export/vanilla_food.json` (food -> nutrition/saturation). One-time.
2. `export_vanilla_foods.py` — from `vanilla_food.json` produce the fuller
   `export/vanilla_foods.json`.
3. `ingredient_values.py` — derive per-ingredient average hunger/saturation into
   `export/ingredient_values.json`.
4. `vanilla_scales.py` — ingredient -> result scale table
   `export/vanilla_recipe_scales.json`.
5. `calc_dishes.py` — compute proposed hunger+saturation for every dish from the
   values above -> `export/dish_proposal.json` (+ a review table).
6. `apply_dish_values.py` — write the accepted dish proposal back into
   `src/main/resources/features/crafting.yml` (hunger/saturation under `items.*`).

## Texture generation

`gen_textures.py` reads every `*.px` file from `tools/cooking/textures/` and
writes a 16×16 PNG to `src/main/resources/resource_pack/assets/leet/textures/item/`.

Adding a texture is a one-file edit:

1. Create `tools/cooking/textures/<name>.px` (format below).
2. Add `"<name>"` to `NAMES` in `gen_textures.py`.
3. Run `python3 tools/cooking/gen_textures.py`.

### The `.px` format

A 16×16 pixel-art grid keyed through a small palette — designed to be readable
and hand-editable in any text editor. Files end in `.px` and live in
`tools/cooking/textures/`.

```
# name: salt
# base: 0,0,0,0                # default RGBA for the '.' character (transparent)
# palette:                      # any number of palette entries
#   W = 235,235,235,255
#   E = 200,200,200,255
................
.W..W...W...W...
................
....             # 16 rows of exactly 16 characters each
................
```

- The header is `#`-prefixed lines; recognized directives are `# name:`,
  `# base:`, and `# palette:` (the palette block contains `# key = r,g,b,a`
  entries — one per line).
- The body is **exactly 16 lines of exactly 16 characters** (whitespace-only
  separators between header and body are ignored).
- Each body character is a palette key. The literal `.` resolves through `# base:`
  when present, otherwise it must be a palette key like any other.
- The parser is `tools/cooking/px.py::read_px(path) -> (palette, pixels)`;
  add a test or extend it there if you add new directives.

Modules used by the pipeline: `_common.py` (shared `OUT` path + `make()` PNG
writer) and `px.py` (format parser).