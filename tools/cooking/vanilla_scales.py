#!/usr/bin/env python3
"""Vanilla ingredient -> result food scale table.

For each vanilla food recipe, list the raw ingredients and the result, then
compute the ingredient-value sum and the ratio result/ingredients (per unit).

Sources a food values from export/vanilla_food.json (already exported from the
server jar). This table is the basis for reasoning about how vanilla cooking
scales value, so we can apply the same idea to our custom dishes.
"""
import json

V = json.load(open("export/vanilla_food.json"))

def fval(material):
    if material in V:
        return V[material]["nutrition"], V[material]["saturation"]
    return 0, 0.0  # not a food (wheat, milk, mushroom, sugar, egg, bowl, ...)

# Vanilla food recipes: (label, [(ingredient, qty), ...], result, result_qty)
# result_qty = how many result items come from the whole recipe grid.
RECIPES = [
    ("smelt beef",              [("BEEF", 1)],                 "COOKED_BEEF",        1),
    ("smelt porkchop",          [("PORKCHOP", 1)],             "COOKED_PORKCHOP",    1),
    ("smelt chicken",           [("CHICKEN", 1)],              "COOKED_CHICKEN",     1),
    ("smelt cod",               [("COD", 1)],                  "COOKED_COD",         1),
    ("smelt salmon",            [("SALMON", 1)],               "COOKED_SALMON",      1),
    ("smelt mutton",            [("MUTTON", 1)],               "COOKED_MUTTON",      1),
    ("smelt rabbit",            [("RABBIT", 1)],               "COOKED_RABBIT",      1),
    ("smelt potato",            [("POTATO", 1)],               "BAKED_POTATO",       1),
    ("smelt kelp",              [("KELP", 1)],                 "DRIED_KELP",         1),
    ("bread (wheat x3)",        [("WHEAT", 3)],                "BREAD",              1),
    ("cookie",                  [("WHEAT", 2), ("COCOA_BEANS", 1), ("EGG", 1)], "COOKIE", 8),
    ("mushroom stew",           [("BROWN_MUSHROOM", 1), ("RED_MUSHROOM", 1), ("BOWL", 1)], "MUSHROOM_STEW", 1),
    ("beetroot soup",           [("BEETROOT", 6), ("BOWL", 1)], "BEETROOT_SOUP",    1),
    ("rabbit stew",             [("COOKED_RABBIT", 1), ("CARROT", 1), ("BAKED_POTATO", 1), ("BROWN_MUSHROOM", 1), ("BOWL", 1)], "RABBIT_STEW", 1),
    ("pumpkin pie",             [("PUMPKIN", 1), ("SUGAR", 1), ("EGG", 1)], "PUMPKIN_PIE", 1),
    ("golden carrot",           [("CARROT", 1), ("GOLD_NUGGET", 8)], "GOLDEN_CARROT", 1),
    ("golden apple",            [("APPLE", 1), ("GOLD_INGOT", 8)], "GOLDEN_APPLE", 1),
    ("suspicious/mushroom",     [("BROWN_MUSHROOM", 1), ("RED_MUSHROOM", 1), ("BOWL", 1)], "SUSPICIOUS_STEW", 1),
    ("melon (block->4 slices)", [("MELON", 1)],                "MELON_SLICE",        4),
    ("honey (bottle drink)",    [("HONEY_COMB", 3)],           "HONEY_BOTTLE",       1),
    ("chorus fruit (drop)",     [("CHORUS_PLANT", 1)],         "CHORUS_FRUIT",       1),
    ("rotten flesh (zombie)",   [("ZOMBIE_DROP", 1)],          "ROTTEN_FLESH",       1),
]

rows = []
for label, ings, result, rqty in RECIPES:
    ih = sum(fval(m)[0] * q for m, q in ings)
    isat = sum(fval(m)[1] * q for m, q in ings)
    rh, rsat = fval(result)
    rh_per, rsat_per = rh / rqty, rsat / rqty
    r_h = (rh_per / ih) if ih > 0 else None
    r_s = (rsat_per / isat) if isat > 0 else None
    note = ""
    if ih == 0 and rh > 0:
        note = "  <-- all input 0-value, result has food: vanilla grants baseline"
    rows.append({
        "recipe": label,
        "ingredients": " + ".join(f"{m.lower()}{'x'+str(q) if q>1 else ''}" for m, q in ings),
        "ing_input_hunger": round(ih, 3),
        "ing_input_sat": round(isat, 3),
        "result_per_item_hunger": round(rh_per, 3),
        "result_per_item_sat": round(rsat_per, 3),
        "hunger_ratio_result_per_input": (round(r_h, 3) if r_h is not None else None),
        "sat_ratio_result_per_input": (round(r_s, 3) if r_s is not None else None),
        "note": note.strip(),
    })

with open("export/vanilla_recipe_scales.json", "w") as f:
    json.dump({"recipes": rows}, f, indent=2)

# Average ratios over the FINITE (non-null) rows, so we get the typical vanilla
# cooking multiplier to apply to our custom dishes.
finite = [r for r in rows if r["hunger_ratio_result_per_input"] is not None]
avg_h = sum(r["hunger_ratio_result_per_input"] for r in finite) / len(finite) if finite else 0
avg_s = sum(r["sat_ratio_result_per_input"] for r in finite) / len(finite) if finite else 0

# For the NULL (infinite) rows, the inputs are all non-edible (0 value) so there
# is no per-input ratio. Instead we note the output's value PER ITEM, and average
# those to characterize "value of a product made purely from non-edibles".
nulls = [r for r in rows if r["hunger_ratio_result_per_input"] is None]
avg_null_h = sum(r["result_per_item_hunger"] for r in nulls) / len(nulls) if nulls else 0
avg_null_s = sum(r["result_per_item_sat"] for r in nulls) / len(nulls) if nulls else 0

print(f"{'recipe':20}{'inputs(H,S)':>14}{'result/item(H,S)':>20}{'ratio(H)':>10}{'ratio(S)':>10}  note")
for r in rows:
    ih, is_ = r["ing_input_hunger"], r["ing_input_sat"]
    rh, rs = r["result_per_item_hunger"], r["result_per_item_sat"]
    rh_ratio = r["hunger_ratio_result_per_input"]
    rs_ratio = r["sat_ratio_result_per_input"]
    tag = "  <-- non-edible inputs (no ratio)"
    print(f"{r['recipe']:20}{(str(ih)+'/'+str(is_)):>14}{(str(rh)+'/'+str(rs)):>20}"
          f"{(str(rh_ratio) if rh_ratio is not None else '-'):>10}"
          f"{(str(rs_ratio) if rs_ratio is not None else '-'):>10}{tag if rh_ratio is None else ''}")

print()
print(f"FINITE (valid input-ratio) rows: {len(finite)} of {len(rows)} total")
print(f"NULL-ratio (non-edible-input) rows: {len(nulls)} of {len(rows)} total")
print(f"AVERAGE vanilla hunger ratio       = {avg_h:.3f}  (result / ingredient hunger)")
print(f"AVERAGE vanilla saturation ratio   = {avg_s:.3f}  (result / ingredient saturation)")
print()
print("--- null-ratio products: value per OUTPUT item ---")
for r in nulls:
    print(f"  {r['recipe']:28} -> {r['result_per_item_hunger']}/{r['result_per_item_sat']} per item")
print(f"  {'AVERAGE non-edible product per item':28} -> {avg_null_h:.3f}/{avg_null_s:.3f} per item")

rs_json = {"recipes": rows,
           "average_ratio": {"hunger": round(avg_h, 4), "saturation": round(avg_s, 4),
                             "finite_rows": len(finite), "total_rows": len(rows)},
           "null_ratio_average_per_item": {"hunger": round(avg_null_h, 4), "saturation": round(avg_null_s, 4),
                                           "null_rows": len(nulls)}}
import json as _j
_j.dump(rs_json, open("export/vanilla_recipe_scales.json", "w"), indent=2)
