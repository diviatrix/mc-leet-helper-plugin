#!/usr/bin/env python3
"""One-time export: parse the server's vanilla FoodProperties from FOODS_raw.txt
into a reusable vanilla_food.json (food -> {nutrition, saturation}).

Run once; the produced JSON is the single source of truth for later scripts.
Saturation here is the ACTUAL hunger-saturation restored = nutrition * satModifier
(what the game uses). Export commits raw nutrition + satModifier too for reference.
"""
import re, json

SRC = "export/FOODS_raw.txt"
OUT = "export/vanilla_food.json"

lines = open(SRC).read().splitlines()

iconmap = {"iconst_0":0,"iconst_1":1,"iconst_2":2,"iconst_3":3,"iconst_4":4,"iconst_5":5}

def intval(s):
    # strip the bytecode offset prefix "N: <op>"
    m = re.match(r"^\s*\d+:\s*(.*)$", s)
    if m:
        s = m.group(1).strip()
    if s in iconmap:
        return iconmap[s]
    m = re.match(r"^(bipush|sipush)\s+(-?\d+)$", s)
    return int(m.group(2)) if m else None

entries = []          # (food, nutrition, satModifier)
pending_field = None
last_nutr = None
for i, l in enumerate(lines):
    fm = re.search(r"putstatic\s+#\d+\s+// Field ([A-Z_]+):", l)
    if fm:
        # A field name anchors the NEXT block; but putstatic closes the PREVIOUS
        # built FoodProperties. We handle closing on the next nutrition block.
        pending_field = fm.group(1)
        # The block just finished uses last_nutr + last_sat captured before.
        continue
    if "nutrition:(I)" in l:
        nutr = None
        for k in range(i, i - 5, -1):
            v = intval(lines[k])
            if v is not None:
                nutr = v
                break
        last_nutr = nutr
        continue
    if "saturationModifier:(F)" in l:
        m = re.search(r"// float ([\d.]+)f", lines[i - 1])
        sat = float(m.group(1)) if m else None
        # This saturationModifier belongs to the field whose putstatic comes next.
        # find the next putstatic field name
        field = None
        for k in range(i, min(len(lines), i + 40)):
            mm = re.search(r"putstatic\s+#\d+\s+// Field ([A-Z_]+):", lines[k])
            if mm:
                field = mm.group(1)
                break
        if field and last_nutr is not None and sat is not None:
            entries.append([field, last_nutr, sat])
        continue

# Also handle stew()-based soups (BEETROOT_SOUP, MUSHROOM_STEW, RABBIT_STEW) — their
# FoodProperties are stew(int nutrition): nutrition = given, satModifier = 0.6.
stew_re = re.search(r"stew:\(I\)Lnet/minecraft/world/food/FoodProperties\$Builder;", lines[0] if lines else "")
# map stew ints -> field by scanning "stew(int)" pushes followed by putstatic
for i, l in enumerate(lines):
    sm = re.search(r"invokestatic\s+#\d+\s+// Method .*stew:\(I\)", l)
    if sm:
        nutr = None
        for k in range(i, i - 5, -1):
            v = intval(lines[k])
            if v is not None:
                nutr = v
                break
        field = None
        for k in range(i, min(len(lines), i + 60)):
            mm = re.search(r"putstatic\s+#\d+\s+// Field ([A-Z_]+):", lines[k])
            if mm:
                field = mm.group(1)
                break
        if field and nutr is not None and not any(e[0] == field for e in entries):
            entries.append([field, nutr, 0.6])

data = {}
for field, nutr, satmod in entries:
    real = round(nutr * satmod, 3)
    data[field] = {"nutrition": nutr, "saturation": real, "saturation_modifier": satmod}

with open(OUT, "w") as f:
    json.dump(data, f, indent=2)

print(f"Exported {len(data)} foods -> {OUT}")
for k in sorted(data):
    d = data[k]
    print(f"  {k:24} nutr={d['nutrition']:>3}  sat={d['saturation']:>6}")
