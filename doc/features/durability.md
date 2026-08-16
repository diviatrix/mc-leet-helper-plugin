# Feature: Durability

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../Admin.md)

Modifies durability damage for **whitelisted** items. Config file `features/_durability.yml`.

**Permissions**
- **Node:** `leet.feat.durability` · **default:** `false` (nobody).
- Grant the node (e.g. LuckPerms) to apply the durability multiplier to a player's items. There is no `/leet` toggle for this feature — access is purely permission-driven. The node alone is not enough — `base.enabled`, the permission, and `base.worlds` must all pass.
- Set `base.default-permission` in `_durability.yml` to `true` (everyone) or `op` (ops only) to change the out-of-box default. Nodes are registered at startup, so permission config changes require a **restart**.

**Behavior**
1. A held/broken item takes durability damage (`PlayerItemDamageEvent`).
2. If the item's material is in `whitelist`, the damage is multiplied by `multiplier`, then clamped to at least `min-damage`.
3. Non-whitelisted items are unaffected.

**Ordering:** the multiplier applies **after** the Unbreaking enchantment has already reduced the damage value presented by the event (i.e. it multiplies the post-Unbreaking damage).

```yaml
base:
  enabled: true
  permission: leet.feat.durability
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  multiplier: 0.5     # 0.1 – 10.0
  min-damage: 1        # Minimum damage applied per hit
  cost: 0              # Vault economy cost per damaged event (0 = free)
  whitelist:
    - WOODEN_SWORD
    - WOODEN_SHOVEL
    - WOODEN_PICKAXE
    - WOODEN_AXE
    - WOODEN_HOE
    - STONE_SWORD
    - STONE_SHOVEL
    - STONE_PICKAXE
    - STONE_AXE
    - STONE_HOE
    - IRON_SWORD
    - IRON_SHOVEL
    - IRON_PICKAXE
    - IRON_AXE
    - IRON_HOE
    - GOLDEN_SWORD
    - GOLDEN_SHOVEL
    - GOLDEN_PICKAXE
    - GOLDEN_AXE
    - GOLDEN_HOE
    - DIAMOND_SWORD
    - DIAMOND_SHOVEL
    - DIAMOND_PICKAXE
    - DIAMOND_AXE
    - DIAMOND_HOE
    - NETHERITE_SWORD
    - NETHERITE_SHOVEL
    - NETHERITE_PICKAXE
    - NETHERITE_AXE
    - NETHERITE_HOE
    - TRIDENT
    - BOW
    - CROSSBOW
    - SHIELD
    - LEATHER_HELMET
    - IRON_HELMET
    - GOLDEN_HELMET
    - DIAMOND_HELMET
    - NETHERITE_HELMET
    - LEATHER_CHESTPLATE
    - IRON_CHESTPLATE
    - GOLDEN_CHESTPLATE
    - DIAMOND_CHESTPLATE
    - NETHERITE_CHESTPLATE
    - LEATHER_LEGGINGS
    - IRON_LEGGINGS
    - GOLDEN_LEGGINGS
    - DIAMOND_LEGGINGS
    - NETHERITE_LEGGINGS
    - LEATHER_BOOTS
    - IRON_BOOTS
    - GOLDEN_BOOTS
    - DIAMOND_BOOTS
    - NETHERITE_BOOTS
    - TURTLE_HELMET
    - ELYTRA

messages:
  insufficient-funds: "<red>Insufficient funds! Cost: <cost>"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `multiplier` | double | `0.5` | Damage multiplier. `0.5` = half damage (items last ~2×), `1.0` = vanilla, `2.0` = double damage |
| `min-damage` | int | `1` | Minimum damage per event (prevents 0/infinite-durability items) |
| `cost` | double | `0.0` | Vault economy cost per protected item-damage event. `0` or any value `≤ 0` = free. |
| `insufficient-funds` | message | — | Sent when the player lacks funds for the `cost`; the damage reduction is blocked (the item takes full damage) |
| `whitelist` | list of Material names | all tools & equipment | Only these materials are affected |

> **Note:** any entry that is not a valid Bukkit `Material` name is skipped with a `Invalid material in durability whitelist:` warning at load and has no effect. Only use exact enum names (e.g. `WOODEN_SWORD`, `DIAMOND_PICKAXE`) — generic names like `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS` (unprefixed armor slots) are not valid Materials and were removed from the defaults; use the material-specific forms (`LEATHER_HELMET`, `IRON_CHESTPLATE`, etc.) instead.

**Multiplier examples**
- `0.5` — items last ~2× longer
- `1.0` — vanilla behavior
- `2.0` — items break ~2× faster

**Cooldown:** none.
