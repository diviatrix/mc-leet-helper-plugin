#!/usr/bin/env python3
"""Apply the computed dish proposal to cooking.yml (hunger/saturation in `items.*`,
`amount` in `recipes.*`), preserving all comments/structure via targeted edits."""
import json, re, sys

PATH = "src/main/resources/features/cooking.yml"
prop = json.load(open("export/dish_proposal.json"))

# Pretzel is a fallback non-integer; normalise to whole numbers.
for k in prop:
    prop[k]["hunger"] = round(prop[k]["hunger"])
    prop[k]["saturation"] = round(prop[k]["saturation"])

lines = open(PATH).read().splitlines(keepends=True)

# Track current section + which item/recipe block we're inside.
out = []
section = None
block = None
for ln in lines:
    # section markers (2-space)
    m = re.match(r"^(  )(items|recipes|messages):\s*(#.*)?$", ln)
    if m:
        section = m.group(2)
        block = None
        out.append(ln)
        continue
    # item/recipe id (4-space)
    m = re.match(r"^    ([a-z0-9-]+):\s*(#.*)?$", ln)
    if m:
        block = m.group(1)
        out.append(ln)
        continue
    # within an item block, replace hunger/saturation
    if section == "items" and block in prop:
        m = re.match(r"^      hunger: .+$", ln)
        if m:
            out.append(f"      hunger: {prop[block]['hunger']}\n")
            continue
        m = re.match(r"^      saturation: .+$", ln)
        if m:
            out.append(f"      saturation: {prop[block]['saturation']}\n")
            continue
    # within a recipe block, replace amount
    if section == "recipes" and block in prop:
        m = re.match(r"^      amount: .+$", ln)
        if m:
            out.append(f"      amount: {prop[block]['amount']}\n")
            continue
    out.append(ln)

open(PATH, "w").writelines(out)
print("Applied proposal to", PATH)
for k, v in sorted(prop.items()):
    print(f"  {k}: amount={v['amount']} hunger={v['hunger']} sat={v['saturation']}")
