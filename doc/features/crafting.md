# Feature: Crafting

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../Admin.md)

Adds **custom crafting items** that are not food by themselves — condiments and seasonings. Config file `features/crafting.yml`. Crafting is a **server-level** feature, open to all players whenever `base.enabled` is `true` (like [Cooking](cooking.md)).

**How it's controlled**
- Enabled/disabled at the **server** level via `base.enabled`. No permission and no per-player toggle.
- `base.worlds`, if set, restricts the feature to those worlds.

## Recipes & items

Items defined here join the **shared custom-item registry**, so recipes in any crafting feature (e.g. Cooking) can reference them by id.

| Item | Screenshot base | Recipe | Yields | Notes |
|---|---|---|---|---|
| Salt | SUGAR | Smelt `WATER_BUCKET` (SMELT) | 9 | Smelting a water bucket yields salt; Minecraft returns the empty bucket |

## Config layout

```yaml
base:
  enabled: true
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  items:
    salt:
      material: SUGAR
      name: "Salt"
      lore: [ "White crystalline seasoning." ]
  recipes:
    salt:
      type: SMELT
      ingredient: WATER_BUCKET
      result: salt
      amount: 9
      experience: 0.35
      # smelting a water bucket yields 9 salt; the empty bucket is returned

messages:
  feature-off: "<red>Crafting is currently off for you."
```

`SMELT` recipes take a single `ingredient` (a vanilla `Material` or a registered custom item id) and produce `result`. `experience` (default `0.35`) and a furnace `cooking-time` (default `200`) can be set. Because items are registered in the **shared** registry, `salt` is usable as an ingredient in any Cooking recipe.

| Key | Type | Default | Description |
|---|---|---|---|
| `items.<id>.material` | Material | — | Base material the item is shown as. |
| `items.<id>.name` | string | id | Display name. |
| `items.<id>.lore` | string list | — | Description lines (shown in grey). |
| `recipes.<id>.type` | `SHAPELESS`/`SHAPED`/`SMELT` | `SHAPELESS` | Crafting recipe kind. |
| `recipes.<id>.ingredient` | id / material | — | SMELT: the single ingredient smelted in a furnace. |
| `recipes.<id>.result` | id or `material:<M>` | — | Item id to output, or a vanilla material. |
| `recipes.<id>.amount` | int | `1` | How many of `result` the recipe yields. |
| `recipes.<id>.experience` | double | `0.35` | Furnace XP granted (SMELT only). |
| `recipes.<id>.cooking-time` | int | `200` | Furnace cook ticks (SMELT only). |

## Notes & limits
- Crafting items carry the same `ci` plugin tag as cooking items, so they're matched exactly (e.g. `salt` is distinct from vanilla `SUGAR`).
- Disabling `base.enabled` removes the recipes and hides the items in cooking recipes that reference them.

**Cooldown:** none (by default).
