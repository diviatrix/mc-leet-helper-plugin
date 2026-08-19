#!/usr/bin/env python3
"""Compute proposed hunger+saturation for every custom cooking dish from the
vanilla-derived ingredient values (export/ingredient_values.json) and the REAL
recipe ingredient lists (read manually, no yaml parser needed).

Rule (vanilla approach, no arbitrary 1.5x):
    dish_hunger = round( sum(ingredient_hunger) )
    dish_sat     = round( sum(ingredient_sat) )
then apply the vanilla cooking multiplier on TOP:
    dish *= (H_MULT, S_MULT)   # average result/ingredient ratio = 2.356 / 7.527

Custom-item ingredients (ramen uses instant-noodle; chocolate-piece uses
chocolate-bar) are resolved from the computed values (fixed point).
Non-edible-only dishes floor to NON_EDIBLE so they stay an edible meal.
Outputs export/dish_proposal.json + a review table.
"""
import json, math

ING = json.load(open("export/ingredient_values.json"))
H_MULT = 2.356
S_MULT = 7.527
NON_EDIBLE = (4.075, 1.528)

def ih(mat):
    v = ING.get(mat); return v["hunger"] if v else 0.0
def isat(mat):
    v = ING.get(mat); return v["saturation"] if v else 0.0

# Our dishes: result_id -> list of ingredient ids/materials (with qty).
DISHES = {
    "croissant":            {"dough":2, "SUGAR":2, "MILK_BUCKET":1},
    "borsh":                {"BEEF":2, "BEETROOT":1, "POTATO":1, "CARROT":1, "BOWL":1, "ALLIUM":1},
    "pelmeni":              {"dough":3, "BEEF":1, "PORKCHOP":1, "ALLIUM":2, "BOWL":1},
    "instant-noodle":       {"dough":2, "EGG":1},
    "ramen":                {"instant-noodle":2, "EGG":1, "PORKCHOP":1, "DRIED_KELP":1, "CHICKEN":1, "BOWL":1},
    "banh-mi":              {"BREAD":1, "COOKED_CHICKEN":1, "ALLIUM":1},
    "milk-porridge":        {"MILK_BUCKET":1, "WHEAT":1, "SUGAR":1, "BOWL":1},
    "creamy-mushroom-soup": {"BROWN_MUSHROOM":1, "RED_MUSHROOM":1, "MILK_BUCKET":1, "BOWL":1},
    "chicken-skewers":      {"COOKED_CHICKEN":2, "STICK":1},
    "charlotte":            {"dough":3, "APPLE":1, "SUGAR":2, "EGG":1},
    "pretzel":              {"dough":2, "salt":1},
    "beef-jerky":           {"BEEF":2, "salt":1},
    "chicken-jerky":        {"CHICKEN":2, "salt":1},
    "hamon":                {"PORKCHOP":6, "salt":3},
    "potato-chips":         {"POTATO":3, "salt":1},
    "dry-salmon":           {"SALMON":3, "salt":1},
    "dry-cod":              {"COD":3, "salt":1},
    "chocolate-bar":        {"COCOA_BEANS":3, "MILK_BUCKET":1, "SUGAR":2},
    "chocolate-piece":      {"chocolate-bar":1},
}

# Dishes that are a SPLIT of another dish (value = source / pieces), not scaled.
SPLIT_OF = {"chocolate-piece": ("chocolate-bar", 8)}

computed = {}
proposal = {}
TARGET_PER_ITEM_HUNGER = 6   # aim ~6 hunger per crafted piece; split into more if bigger

while True:
    changed = False
    for dish, ings in DISHES.items():
        if dish in SPLIT_OF:
            src, pieces = SPLIT_OF[dish]
            base = computed.get(src)
            if base is None:
                continue
            ph = max(1, round(base["hunger"] / pieces))
            ps = max(1, round(base["saturation"] / pieces))
            new = {"hunger": ph, "saturation": ps, "amount": 1}
        else:
            th = ts = 0.0
            has_food = False
            for ing, qty in ings.items():
                if ing in computed:
                    _h, _s = computed[ing]["hunger"] * computed[ing].get("amount", 1), computed[ing]["saturation"] * computed[ing].get("amount", 1)
                else:
                    _h, _s = ih(ing), isat(ing)
                th += _h * qty; ts += _s * qty
                if _h > 0 or _s > 0:
                    has_food = True
            if not has_food:
                ph = NON_EDIBLE[0]; ps = NON_EDIBLE[1]; amount = 1
            else:
                scaled_h = th * H_MULT
                scaled_s = ts * S_MULT
                amount = max(1, math.ceil(scaled_h / TARGET_PER_ITEM_HUNGER))
                ph = max(1, round(scaled_h / amount))
                ps = max(1, round(scaled_s / amount))
            new = {"hunger": ph, "saturation": ps, "amount": amount}
        if dish not in computed or computed[dish] != new:
            computed[dish] = new
            changed = True
    if not changed:
        break

# normalize: amount should reflect total hunger, ensure not degenerate
with open("export/dish_proposal.json", "w") as f:
    json.dump(computed, f, indent=2)

print(f"{'dish':24}{'amount':>7}{'hunger/pc':>10}{'satur/pc':>10}   (total H x amount)")
for dish, ings in DISHES.items():
    c = computed[dish]
    print(f"{dish:24}{c['amount']:>7}{c['hunger']:>10}{c['saturation']:>10}   (x amount = {c['hunger']*c['amount']})")
print()
print(f"target ~{TARGET_PER_ITEM_HUNGER} hunger/piece; split dishes break bars into pieces")
