# tools/cooking

One-off and maintenance Python tooling for the Cooking/Crafting feature. All
scripts are written against **repo-root-relative** paths and must be run **from
the repo root**:

```bash
python3 tools/cooking/<script>.py
```

They read/write `export/*.json` and `src/main/resources/features/cooking.yml` /
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
   `src/main/resources/features/cooking.yml` (hunger/saturation under `items.*`).

## Texture generation

- `gen_textures.py` — generate the base item textures.
- `gen_food_textures.py` — generate the food/item textures.

Both write PNGs into
`src/main/resources/resource_pack/assets/leet/textures/item`.
