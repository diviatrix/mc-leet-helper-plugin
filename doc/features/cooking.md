# Feature: Cooking

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../Admin.md)

Adds advanced **crafting recipes** for custom food items, unlocked one-at-a-time by the **Cook** skill. Config file `features/cooking.yml`. Each recipe is gated per-player: a recipe only produces its result once the player's **Cook** level reaches the recipe's required level (each level = one recipe). Dishes are placed on an already-edible base material and reapply custom hunger/saturation when eaten, so the combined dishes restore **more** than their raw parts.

**Permissions**
- **Node:** `leet.feat.cooking` · **default:** `false` (nobody).
- Grant the node (e.g. LuckPerms) to craft cooking recipes and to eat dishes with their custom nutrition; it also unlocks the `/leet cook` personal off-toggle. Holding the node **auto-maxes the Cook skill** (it counts as already acquired, exactly like the other feature-bound skills), so all recipes are available. A player **without** `leet.feat.cooking` but **with** `leet.feat.skills` can still see and level **Cook** as a normal skill with XP (see [Skills](skills.md)) — but only their leveled recipes become craftable, and crafting still needs the cooking node. Nodes are registered at startup, so permission config changes require a **restart**.

**Behavior**
- Recipes are registered as vanilla **crafting** recipes, so they show up in the recipe book like any other recipe.
- For a player who lacks the cooking permission (or whose `cook` skill level is below the recipe's `level`), the recipe simply shows **no result** in the crafting matrix — it is uncraftable until unlocked.
- Dish items carry a plugin tag that the feature reads on eat: vanilla consumption is cancelled and the configured `hunger`/`saturation` are applied instead (base materials are chosen to be edible so the item can be eaten at all).

## The Cook skill
Defined in `features/skills.yml` and wired into the tree in `features/skill-tree.yml`:
- **Prerequisite:** `farmer` level 10 (set in `skill-tree.yml` `requires.cook`).
- **Max level 12** — one recipe unlocks per level (see recipe `level` below). It is a pure gating skill with no passive, so it carries `lore` only (no effects).

## Config layout

```yaml
base:
  enabled: true
  permission: leet.feat.cooking
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  items:            # custom items the recipes produce / consume
    dough:
      material: WHEAT
      name: "Dough"
      lore: [ "Flour and egg, mixed and ready." ]
    croissant:
      material: BREAD
      name: "Croissant"
      hunger: 6      # ONLY dish items set hunger/saturation (food)
      saturation: 8
      lore: [ "Flaky, buttery bakery fresh." ]
  recipes:           # one recipe per Cook level
    dough:
      type: SHAPELESS
      level: 1        # needs Cook level 1
      ingredients:    # SHAPELESS = a flat list (vanilla Material or custom item id)
        - WHEAT
        - WHEAT
        - EGG
      result: dough   # a custom item id (see items) ...
      amount: 1
    bread:
      type: SHAPELESS
      level: 2
      ingredients: [ dough ]        # custom item ids match exactly (tag + name)
      result: material:BREAD        # ... or `material:<MATERIAL>` for a vanilla output
      amount: 1

messages:
  recipe-locked: "<red>You need <level> Cook level to craft this."
```

A recipe ingredient is either a vanilla `Material` name (matched by material) or a **custom item id** (matched exactly by tag + display name, so e.g. `dough` is distinct from `WHEAT`). Invalid material/item names are skipped with a warning. `result` is a custom item id (built from `feature.items.<id>`) or `material:<MATERIAL>` for a vanilla output (e.g. the "second way to make bread").

### Recipes & items

| Item | Base material | Recipe (shapeless ingredients) | Hunger / Saturation | Cook level |
|---|---|---|---|---|
| Dough | WHEAT | WHEAT ×2 + EGG | — (ingredient) | 1 |
| Bread * | BREAD (vanilla) | DOUGH | vanilla | 2 |
| Croissant | BREAD | DOUGH + DOUGH + SUGAR + MILK_BUCKET | 6 / 8 | 3 |
| Borsh | MUSHROOM_STEW | COOKED_BEEF + BAKED_POTATO + CARROT + BEETROOT + ALLIUM + BOWL | 8 / 12 | 4 |
| Pelmeni | MUSHROOM_STEW | DOUGH + COOKED_BEEF + BOWL | 7 / 9 | 5 |
| Instant Noodle | MUSHROOM_STEW | DOUGH + DOUGH + WHEAT + BOWL | 5 / 7 | 6 |
| Ramen | MUSHROOM_STEW | DOUGH + COOKED_CHICKEN + EGG + BOWL | 8 / 12 | 7 |
| Banh Mi | BREAD | BREAD + COOKED_CHICKEN + CARROT + ALLIUM | 7 / 10 | 8 |
| Milk Porridge | MUSHROOM_STEW | MILK_BUCKET + WHEAT + SUGAR + BOWL | 6 / 10 | 9 |
| Creamy Mushroom Soup | MUSHROOM_STEW | BROWN_MUSHROOM + RED_MUSHROOM + MILK_BUCKET + BOWL | 7 / 10 | 10 |
| Chicken Skewers | COOKED_CHICKEN | COOKED_CHICKEN ×2 + STICK | 8 / 11 | 11 |
| Charlotte | BREAD | WHEAT ×2 + SUGAR + EGG + APPLE | 6 / 8 | 12 |

\* The dough→BREAD recipe is a **second** (advanced) way to make normal vanilla bread, in addition to vanilla's wheat grid; **Banh Mi** uses vanilla BREAD.

| Key | Type | Default | Description |
|---|---|---|---|
| `items.<id>.material` | Material | — | Base material the item is shown as (and eaten as). Dishes should be an edible material. |
| `items.<id>.name` | string | id | Display name. |
| `items.<id>.hunger` | int | `0` | Hunger points restored on eat (`> 0` marks the item as a dish; `0` = plain ingredient). |
| `items.<id>.saturation` | int | `0` | Saturation restored on eat (only meaningful for dishes). |
| `items.<id>.lore` | string list | — | Description lines (shown in grey). |
| `recipes.<id>.type` | `SHAPELESS`/`SHAPED` | `SHAPELESS` | Crafting recipe kind. |
| `recipes.<id>.level` | int | `1` | Required **Cook** skill level to craft (one recipe per level). |
| `recipes.<id>.ingredients` | list / map | — | SHAPELESS: a flat list of materials/custom ids; SHAPED: a `shape` (3 rows of 3) plus a letter→ingredient map. |
| `recipes.<id>.result` | id or `material:<M>` | — | Custom item id to output, or a vanilla material for a vanilla output. |
| `recipes.<id>.amount` | int | `1` | How many of `result` the recipe yields. |

## Notes & limits
- Custom items are identified by a plugin tag (`ci`): the feature needs to recognize them for both crafting (ExactChoice ingredients like Dough) and eating. Manually-copied items that lose the tag/name won't be treated as custom.
- **Waive/revoke restrictions with the permission:** a player without `leet.feat.cooking` cannot craft or custom-eat dishes (the base material's vanilla nutrition would still apply if the item reached them another way).
- Recipes registered when the feature enables are unregistered when it disables (toggling `/leet cook` per-player keeps the recipes registered; only the per-player gating changes).

**Cooldown:** none (by default).