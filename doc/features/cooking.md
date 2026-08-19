# Feature: Cooking

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../Admin.md)

Adds advanced **crafting recipes** for custom food items. Config file `features/cooking.yml`. Cooking is a **server-level** feature: when `base.enabled` is `true`, every recipe is available to **all** players — no skill, no permission, no per-player toggle. Dishes are placed on an already-edible base material and reapply custom hunger/saturation when eaten, so the combined dishes restore **more** than their raw parts.

**How it's controlled**
- Enabled/disabled at the **server** level via `base.enabled`. Turning the feature on gives everyone on the server every recipe; there is no per-recipe or per-player unlock.
- No permission is required, and there is no per-player `/leet` off-switch. `/leet cook` is an **info-only** subcommand that reports the server-side state.
- `base.worlds`, if set, restricts the feature to those worlds.

**Behavior**
- Recipes are registered as vanilla **crafting** recipes, so they show up in the recipe book like any other recipe.
- While the feature is enabled the recipe crafts for anyone; when it is disabled (or a world is excluded), the recipe simply shows **no result** in the crafting matrix — it is uncraftable.
- Dish items carry a plugin tag that the feature reads on eat: vanilla consumption is cancelled and the configured `hunger`/`saturation` are applied instead (base materials are chosen to be edible so the item can be eaten at all).
- Each dish shows a **custom icon** on the client from a tiny dish-only resource pack the plugin serves additively (see [Dish icons & resource pack](#dish-icons--resource-pack) below).

## Config layout

```yaml
base:
  enabled: true                         # server-level switch: ON = recipes for everyone
  worlds: []                            # optional world whitelist
  cooldown: 0
  message-type: ACTION_BAR
  # No `permission`/`default-permission`: cooking needs no permission node.

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
  recipes:
    dough:
      type: SHAPELESS
      ingredients:    # SHAPELESS = a flat list (vanilla Material or custom item id)
        - WHEAT
        - WHEAT
        - EGG
      result: dough   # a custom item id (see items) ...
      amount: 1
    bread:
      type: SHAPELESS
      ingredients: [ dough ]        # custom item ids match exactly (tag + name)
      result: material:BREAD        # ... or `material:<MATERIAL>` for a vanilla output
      amount: 1

messages:
  feature-off: "<red>Cooking is currently off for you."
```

A recipe ingredient is either a vanilla `Material` name (matched by material) or a **custom item id** (matched exactly by tag + display name, so e.g. `dough` is distinct from `WHEAT`). Invalid material/item names are skipped with a warning. `result` is a custom item id (built from `feature.items.<id>`) or `material:<MATERIAL>` for a vanilla output (e.g. the "second way to make bread").

### Recipes & items

| Item | Base material | Recipe (shapeless ingredients) | Hunger / Saturation |
|---|---|---|---|
| Dough | WHEAT | WHEAT ×2 + EGG | — (ingredient) |
| Bread * | BREAD (vanilla) | DOUGH | vanilla |
| Croissant | BREAD | DOUGH + DOUGH + SUGAR + MILK_BUCKET | 6 / 8 |
| Borsh | MUSHROOM_STEW | COOKED_BEEF + BAKED_POTATO + CARROT + BEETROOT + ALLIUM + BOWL | 8 / 12 |
| Pelmeni | MUSHROOM_STEW | DOUGH + COOKED_BEEF + BOWL | 7 / 9 |
| Instant Noodle | MUSHROOM_STEW | DOUGH ×3 + BOWL | 5 / 7 |
| Ramen | MUSHROOM_STEW | DOUGH + COOKED_CHICKEN + EGG + BOWL | 8 / 12 |
| Banh Mi | BREAD | BREAD + COOKED_CHICKEN + CARROT + ALLIUM | 7 / 10 |
| Milk Porridge | MUSHROOM_STEW | MILK_BUCKET + WHEAT + SUGAR + BOWL | 6 / 10 |
| Creamy Mushroom Soup | MUSHROOM_STEW | BROWN_MUSHROOM + RED_MUSHROOM + MILK_BUCKET + BOWL | 7 / 10 |
| Chicken Skewers | COOKED_CHICKEN | COOKED_CHICKEN ×2 + STICK | 8 / 11 |
| Charlotte | BREAD | WHEAT ×2 + SUGAR + EGG + APPLE | 6 / 8 |

\* The dough→BREAD recipe is a **second** (advanced) way to make normal vanilla bread, in addition to vanilla's wheat grid; **Banh Mi** uses vanilla BREAD.

| Key | Type | Default | Description |
|---|---|---|---|
| `items.<id>.material` | Material | — | Base material the item is shown as (and eaten as). Dishes should be an edible material. |
| `items.<id>.name` | string | id | Display name. |
| `items.<id>.hunger` | int | `0` | Hunger points restored on eat (`> 0` marks the item as a dish; `0` = plain ingredient). |
| `items.<id>.saturation` | int | `0` | Saturation restored on eat (only meaningful for dishes). |
| `items.<id>.lore` | string list | — | Description lines (shown in grey). |
| `recipes.<id>.type` | `SHAPELESS`/`SHAPED` | `SHAPELESS` | Crafting recipe kind. |
| `recipes.<id>.ingredients` | list / map | — | SHAPELESS: a flat list of materials/custom ids; SHAPED: a `shape` (3 rows of 3) plus a letter→ingredient map. |
| `recipes.<id>.result` | id or `material:<M>` | — | Custom item id to output, or a vanilla material for a vanilla output. |
| `recipes.<id>.amount` | int | `1` | How many of `result` the recipe yields. |

## Dish icons & resource pack

Each dish item carries a client-side **icon** driven by a `CustomModelData` value on the item. The icon comes from a tiny, **dish-only** resource pack generated at build time and bundled with the plugin. For each base material a cooking dish uses, the plugin overrides that material's item definition (`assets/minecraft/items/<base>.json`) with a `range_dispatch` on `custom_model_data` that routes the dish's value to the matching `leet:item/<id>` model/texture (`assets/leet/...`). This is the same approach Vane uses and is more reliable across client versions than a custom `item_model`. If a player declines the pack, the item falls back to its base material look.

Distribution is configured in **`config.yml`** (global):

```yaml
resource-pack:
  enabled: true   # master switch for distributing the dish icons
  port: 8043      # internal HTTP port, used when `url` is empty
  url: ""         # optional: host the bundled zip yourself and point here (no embedded server)
  prompt: ""      # optional text shown when the client is asked to install the pack
  require: false  # false = optional; declining leaves everyone else's textures intact
```

- With `url` set, the embedded server is skipped and `addResourcePack` points straight at your hosted copy.
- With `url` empty, the plugin runs a small internal HTTP endpoint. It derives the client-reachable address from the server's configured IP; when none is set it logs that `resource-pack.url` should be configured (e.g. the box can't expose a port, or the server is behind a proxy/port-forward).
- `require: true` makes the pack mandatory (players who decline are not allowed to join); leave it `false` to keep the pack optional and additive.

## Notes & limits
- Custom items are identified by a plugin tag (`ci`): the feature needs to recognize them for both crafting (ExactChoice ingredients like Dough) and eating. Manually-copied items that lose the tag/name won't be treated as custom.
- There is no permission to grant or revoke — disabling `base.enabled` is the only way to switch cooking off.
- Recipes registered when the feature enables are unregistered when it disables; since there is no per-player state, `base.enabled` fully controls registration.

**Cooldown:** none (by default).