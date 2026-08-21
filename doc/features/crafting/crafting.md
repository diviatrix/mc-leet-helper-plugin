# Feature: Crafting

**Owning plugin:** LeetCrafting · Config file `plugins/LeetCrafting/features/crafting.yml`.

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

Adds **custom crafting items** — non-food condiments (e.g. Salt) and **food dishes** (Croissant, Ramen, Borsh, Jerky, Chocolate, …). All of it is one server-level feature: when `base.enabled` is `true`, every recipe is available to **all** players — no skill, no permission, no per-player toggle. Dish items are placed on an already-edible base material and reapply custom hunger/saturation when eaten, so the combined dishes restore more than their raw parts.

**How it's controlled**
- Enabled/disabled at the **server** level via `base.enabled`. Turning it on gives everyone every recipe; there is no per-recipe or per-player unlock.
- No permission is required. There is **no** `/leet` subcommand for crafting — toggle it with `/leeta toggle crafting` or by editing `base.enabled` in `crafting.yml` (restart required).
- `base.worlds`, if set, restricts the feature to those worlds.

**Behavior**
- Recipes are registered as vanilla **crafting** recipes, so they show up in the recipe book like any other recipe. The engine supports **SHAPELESS**, **SHAPED** (3x3) and **SMELT** (furnace) recipes.
- While the feature is enabled the recipe crafts for anyone; when it is disabled (or a world is excluded), the recipe simply shows **no result** in the crafting matrix — it is uncraftable.
- Dish items carry a plugin tag that the feature reads on eat: vanilla consumption is cancelled and the configured `hunger`/`saturation` are applied instead (base materials are chosen to be edible so the item can be eaten at all).
- Each item shows a **custom icon** on the client from the resource pack owned by LeetCrafting — see [Resource Pack Distribution](../../resource-pack.md).

## Recipes & items

All custom items live in `feature.items`; `feature.recipes` defines how to craft them. `amount` sets how many pieces each craft produces (a batch's total food value is spread across the pieces). Some dishes require **Salt**, a non-food custom item also defined here.

### Non-food items

| Item | Screenshot base | Recipe | Yields | Notes |
|---|---|---|---|---|
| Salt | SUGAR | Smelt `WATER_BUCKET` (SMELT) | 9 | Smelting a water bucket yields salt; Minecraft returns the empty bucket |

### Dishes (food)

| Item | Base | Recipe | Yields | Hunger / Sat |
|---|---|---|---|---|
| Dough | WHEAT | WHEAT ×2 + EGG | 1 | — (ingredient) |
| Bread * | BREAD (vanilla) | DOUGH | 1 | vanilla |
| Croissant | BREAD | `DDD/SMS/...` (D=dough, S=sugar, M=milk) | 6 | 2 / 2 |
| Borsh | BREAD | `BRB/PPP/CWA` (B=beef, R=beetroot, P=potato, C=carrot, W=bowl, A=allium) | 6 | 6 / 9 |
| Pelmeni | BREAD | `DDD/BPB/AWA` (D=dough, B=beef, P=pork, A=allium, W=bowl) | 8 | 3 / 4 |
| Instant Noodle | BREAD | `DED/.../...` (D=dough, E=egg) | 3 | 2 / 1 |
| Ramen | BREAD | `IEI/PKC/.W.` (I=instant noodle, E=egg, P=pork, K=dried kelp, C=raw chicken, W=bowl) | 8 | 4 / 6 |
| Banh Mi | BREAD | `BCA/.../...` (B=bread, C=cooked chicken, A=allium) | 6 | 4 / 6 |
| Milk Porridge | BREAD | `MWS/.B./...` (M=milk, W=wheat, S=sugar, B=bowl) | 4 | 4 / 6 |
| Creamy Mushroom Soup | BREAD | `BRK/.W./...` (B=brown mushroom, R=red mushroom, K=milk, W=bowl) | 4 | 4 / 6 |
| Chicken Skewers | COOKED_CHICKEN | `CCS/.../...` (C=cooked chicken, S=stick) | 4 | 4 / 4 |
| Charlotte | BREAD | `DAD/SES/...` (D=dough, A=apple, S=sugar, E=egg) | 2 | 6 / 6 |
| Pretzel | BREAD | DOUGH ×2 + SALT | 2 | 3 / 3 |
| Beef Jerky | COOKED_BEEF | RAW BEEF ×2 + SALT | 3 | 5 / 5 |
| Chicken Jerky | COOKED_CHICKEN | RAW CHICKEN ×2 + SALT | 3 | 4 / 4 |
| Jamón | BREAD | `PPP/SSS/PPP` (P=raw pork, S=salt) | 8 | 4 / 6 |
| Potato Chips | BREAD | POTATO ×3 + SALT | 4 | 3 / 2 |
| Dry Salmon | COOKED_SALMON | RAW SALMON ×3 + SALT | 4 | 5 / 2 |
| Dry Cod | COOKED_COD | RAW COD ×3 + SALT | 4 | 4 / 2 |
| Chocolate Bar | BREAD | `CCC/SMS/CCC` (C=cocoa, S=sugar, M=milk) | 1 | 16 / 16 |
| Chocolate Piece | BREAD | CHOCOLATE BAR (break it) | 8 | 2 / 2 |

\* The Dough→BREAD recipe is a **second** (advanced) way to make normal vanilla bread, in addition to vanilla's wheat grid.

## Config layout

```yaml
base:
  enabled: true                         # server-level switch: ON = recipes for everyone
  worlds: []                            # optional world whitelist
  cooldown: 0
  message-type: ACTION_BAR
  # No `permission`/`default-permission`: crafting needs no permission node.

feature:
  items:            # custom items the recipes produce / consume
    salt:
      material: SUGAR
      name: "Salt"
      lore: [ "White crystalline seasoning." ]
    dough:
      material: WHEAT
      name: "Dough"
      lore: [ "Flour and egg, mixed and ready." ]
    croissant:
      material: BREAD
      name: "Croissant"
      hunger: 2      # only dish items set hunger/saturation (food)
      saturation: 2
      lore: [ "Flaky, buttery bakery fresh." ]
  recipes:
    salt:             # SMELT (furnace) recipes use a single `ingredient`
      type: SMELT
      ingredient: WATER_BUCKET
      result: salt
      amount: 9
      experience: 0.35
    dough:
      type: SHAPELESS
      ingredients:
        - WHEAT
        - WHEAT
        - EGG
      result: dough
      amount: 1
    bread:
      type: SHAPELESS
      ingredients: [ dough ]
      result: material:BREAD        # ... or `material:<MATERIAL>` for a vanilla output
      amount: 1
    croissant:        # SHAPED: 3 rows of exactly 3 chars + a letter->ingredient map
      type: SHAPED
      shape: [ "DDD", "SMS", "..." ]
      ingredients:
        D: dough
        S: SUGAR
        M: MILK_BUCKET
      result: croissant
      amount: 6
```

A recipe ingredient is either a vanilla `Material` name (matched by material) or a **custom item id** (matched exactly by tag + display name, so e.g. `dough` is distinct from `WHEAT`, and `salt` is distinct from `SUGAR`). Invalid material/item names are skipped with a warning. `result` is a custom item id (built from `feature.items.<id>`) or `material:<MATERIAL>` for a vanilla output.

| Key | Type | Default | Description |
|---|---|---|---|
| `items.<id>.material` | Material | — | Base material the item is shown as (and eaten as). Dishes should be an edible material. |
| `items.<id>.name` | string | id | Display name. |
| `items.<id>.hunger` | int | `0` | Hunger points restored on eat (`> 0` marks the item as a dish; `0` = plain ingredient like Dough or Salt). |
| `items.<id>.saturation` | int | `0` | Saturation restored on eat (only meaningful for dishes). |
| `items.<id>.lore` | string list | — | Description lines (shown in grey). |
| `recipes.<id>.type` | `SHAPELESS`/`SHAPED`/`SMELT` | `SHAPELESS` | Crafting recipe kind. |
| `recipes.<id>.ingredients` | list / map | — | SHAPELESS: a flat list; SHAPED: a `shape` (3 rows of 3) plus a letter→ingredient map. |
| `recipes.<id>.ingredient` | id / material | — | SMELT: the single ingredient smelted in a furnace. |
| `recipes.<id>.result` | id or `material:<M>` | — | Custom item id to output, or a vanilla material for a vanilla output. |
| `recipes.<id>.amount` | int | `1` | How many of `result` the recipe yields. |
| `recipes.<id>.experience` | double | `0.35` | Furnace XP granted (SMELT only). |
| `recipes.<id>.cooking-time` | int | `200` | Furnace cook ticks (SMELT only). |

## Notes & limits

- Crafting items carry the same `ci` plugin tag, so they're matched exactly (e.g. `salt` is distinct from vanilla `SUGAR`, `dough` from `WHEAT`).
- Disabling `base.enabled` removes the recipes and hides the items in any recipe that references them.
- Recipes registered when the feature enables are unregistered when it disables; `base.enabled` fully controls registration.
- There is no permission to grant or revoke — disabling `base.enabled` is the only way to switch crafting off.

**Cooldown:** none (by default).