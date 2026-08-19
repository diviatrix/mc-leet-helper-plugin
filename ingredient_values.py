#!/usr/bin/env python3
"""Average hunger/saturation value per ingredient, derived from the vanilla
server exports + vanilla product recipes.

Rules:
  - Edible ingredient  -> its exported value (nutrition, saturation).
  - Non-edible ingredient -> derives from its vanilla PRODUCT(S). If a product
    is made ONLY from non-edible inputs, the product's total value is split
    evenly across the non-edible input *item-counts* used, giving a per-item
    value. If the ingredient has a clear dedicated product (wheat->bread,
    mushroom->stew) we use the natural recipe share.

Outputs export/ingredient_values.json = { <MATERIAL>: {hunger, saturation} }
"""
import json

V = json.load(open("export/vanilla_food.json"))

# Non-edible ingredients used in our recipes, and the vanilla product they map to.
# key: ingredient -> product recipe reference (label in vanilla_scales.py).
PRODUCT_FOR = {
    "WHEAT": "bread (wheat x3)",
    "BROWN_MUSHROOM": "mushroom stew",
    "RED_MUSHROOM": "mushroom stew",
    "SUGAR": None,          # part of cookie/pie; split handled below
    "EGG": None,
    "MILK_BUCKET": None,
    "COCOA_BEANS": None,
    "BOWL": None,
    "ALLIUM": None,
    "STICK": None,
    "KELP": "smelt kelp",
    "MELON": "melon (block->4 slices)",
    "PUMPKIN": "pumpkin pie",
}

# Recipes (label-> (ingredients[(mat,qty)], result, qty)) identical to scales.
RECIPES = {
    "smelt kelp": [([("KELP",1)], "DRIED_KELP", 1)],
    "bread (wheat x3)": [([("WHEAT",3)], "BREAD", 1)],
    "cookie": [([("WHEAT",2),("COCOA_BEANS",1),("EGG",1)], "COOKIE", 8)],
    "mushroom stew": [([("BROWN_MUSHROOM",1),("RED_MUSHROOM",1),("BOWL",1)], "MUSHROOM_STEW", 1)],
    "pumpkin pie": [([("PUMPKIN",1),("SUGAR",1),("EGG",1)], "PUMPKIN_PIE", 1)],
    "beetroot soup": [([("BEETROOT",6),("BOWL",1)], "BEETROOT_SOUP", 1)],
    "melon (block->4 slices)": [([("MELON",1)], "MELON_SLICE", 4)],
}

def fval(mat, qty=1):
    if mat in V:
        return V[mat]["nutrition"]*qty, V[mat]["saturation"]*qty
    return 0, 0.0

values = {}
for mat, vals in V.items():
    values[mat] = {"hunger": vals["nutrition"], "saturation": vals["saturation"]}

# Accumulate derived values per ingredient across all its products, then average.
acc = {}   # mat -> list of (h,s) shares
for label, list_of_recipe in RECIPES.items():
    for ings, result, rqty in list_of_recipe:
        rh, rs = fval(result)
        rh_pi, rs_pi = rh / rqty, rs / rqty
        non_ed = [(m, q) for m, q in ings if m not in V]
        tot_items = sum(q for _, q in non_ed)
        if tot_items == 0:
            continue
        share_h = rh_pi / tot_items
        share_s = rs_pi / tot_items
        for m, q in non_ed:
            acc.setdefault(m, [])
            acc[m].append((share_h, share_s))

for m, shares in acc.items():
    h = sum(s[0] for s in shares) / len(shares)
    s = sum(s[1] for s in shares) / len(shares)
    values[m] = {"hunger": round(h, 4), "saturation": round(s, 4),
                 "derived_from": "non-edible product avg"}

# Ingredients that still have no value: apply the average non-edible product
# value per item (computed earlier) as a sensible fallback? No - leave structural
# items (bowl, salt, stick, allium, milk) as 0: they are containers/seasoning.

with open("export/ingredient_values.json", "w") as f:
    json.dump(values, f, indent=2, default=str)

print(f"{'ingredient':16}{'hunger':>8}{'saturation':>10}  source")
for mat in sorted(values):
    v = values[mat]
    src = v.get("derived_from", "vanilla-food")
    print(f"{mat:16}{v['hunger']:>8}{v['saturation']:>10}  {src}")
