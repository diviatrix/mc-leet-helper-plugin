#!/usr/bin/env python3
"""One-time export #2: the full list of edible vanilla foods, mapped to their
Bukkit Material ids, PLUS which vanilla materials our recipes use that ARE foods
vs which have NO direct food value (those need derived-product scaling).

Reads the previously exported vanilla_food.json (source of truth). Writes
export/vanilla_foods.json with:
  {
    "foods": { "<MATERIAL>": {"nutrition":.., "saturation":..} },
    "all_food_materials": [ ... ],        // every material id that is food
  }
"""
import json

SRC = "export/vanilla_food.json"
OUT = "export/vanilla_foods.json"

data = json.load(open(SRC))

# Map FoodProperties field name -> Bukkit Material id. Most are identical; a few
# need explicit aliases to match Bukkit's Material enum / item names.
ALIAS = {
    "BAKED_POTATO": "BAKED_POTATO",
    "BEETROOT_SOUP": "BEETROOT_SOUP",
    "CHORUS_FRUIT": "CHORUS_FRUIT",
    "COOKED_MUTTON": "COOKED_MUTTON",
    "COOKED_RABBIT": "COOKED_RABBIT",
    "ENCHANTED_GOLDEN_APPLE": "ENCHANTED_GOLDEN_APPLE",
    "GOLDEN_APPLE": "GOLDEN_APPLE",
    "GOLDEN_CARROT": "GOLDEN_CARROT",
    "HONEY_BOTTLE": "HONEY_BOTTLE",
    "MELON_SLICE": "MELON_SLICE",
    "MUSHROOM_STEW": "MUSHROOM_STEW",
    "POISONOUS_POTATO": "POISONOUS_POTATO",
    "PUMPKIN_PIE": "PUMPKIN_PIE",
    "RABBIT_STEW": "RABBIT_STEW",
    "ROTTEN_FLESH": "ROTTEN_FLESH",
    "SPIDER_EYE": "SPIDER_EYE",
    "SUSPICIOUS_STEW": "SUSPICIOUS_STEW",
    "SWEET_BERRIES": "SWEET_BERRY_BUSH",   # material for the berry? keep SWEET_BERRIES
    "TROPICAL_FISH": "TROPICAL_FISH",
    "GLOW_BERRIES": "GLOW_BERRIES",
}

foods = {}
for field, vals in data.items():
    mat = ALIAS.get(field, field)
    foods[mat] = {"nutrition": vals["nutrition"], "saturation": vals["saturation"]}

# Sweet berries: the food item is SWEET_BERRIES (consumable), the material for the
# bush is SWEET_BERRY_BUSH. Output the edible item id as-is.
foods["SWEET_BERRIES"] = data["SWEET_BERRIES"]

out = {
    "foods": dict(sorted(foods.items())),
    "all_food_materials": sorted(foods.keys()),
}
with open(OUT, "w") as f:
    json.dump(out, f, indent=2)

print(f"Exported {len(foods)} edible foods -> {OUT}")
print("all_food_materials:", ", ".join(sorted(foods)))
