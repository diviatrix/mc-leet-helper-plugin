# Feature: Auto Crop

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

**Owning plugin:** LeetCore · Config file `plugins/LeetCore/features/auto_crop.yml`.

Auto-harvests nearby mature crops when a player breaks one.

**Permissions**

See [Feature permissions](../../ARCHITECTURE.md#feature-permissions) for the gating rules, default-deny behavior, and restart caveat.

| Node | Default | Notes |
|---|---|---|
| `leet.feat.auto_crop` | `false` | Also unlocks the `/leet crop` personal off-toggle |

**Player command:** `/leet crop` toggles Auto Crop off or on for the current player. It never grants permission.

**Behavior**
1. A player breaks a block (`BlockBreakEvent`).
2. If the broken block is in `materials` (and, if `require-mature` is `true`, it is fully grown), the feature scans a horizontal square.
3. The square spans `-radius`..`+radius` on the x and z axes around the broken block, **one block high** (same Y level as the broken crop; the source block itself is excluded).
4. Every nearby block of the **same material** (and, if enabled, the **same maturity**) is broken with `breakNaturally(tool)`, using the player's main-hand item.

If `require-hoe` is `true`, the square scan only happens when the player is harvesting with a hoe in their hand — otherwise only the single broken crop is removed (default vanilla behavior).

**Silk Touch** is respected (with a Silk Touch tool, crops drop as blocks rather than items).

**Respects protection plugins:** every adjacent crop is also broken through a `BlockBreakEvent`, so claim/region plugins (GriefPrevention, WorldGuard, ...) are consulted per block — protected crops inside a claim/region are **skipped** rather than force-harvested.

```yaml
base:
  enabled: true
  permission: leet.feat.auto_crop
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  radius: 3              # 1 – 5 (hard-capped at 5)
  require-mature: true   # Only harvest fully grown crops
  require-hoe: false     # Only scan/break nearby crops when holding a hoe
  cost: 0                # Vault economy cost per harvest (0 = free)
  materials:
    - WHEAT
    - CARROTS
    - POTATOES
    - BEETROOTS
    - NETHER_WART
    - COCOA
    - SWEET_BERRY_BUSH

messages:
  insufficient-funds: "<red>Insufficient funds! Cost: <cost>"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `radius` | int | `3` | Square half-size (horizontal x/z) around the broken block. Values > 5 are **clamped to 5**. |
| `require-mature` | bool | `true` | Only break fully grown crops. Maturity uses `Ageable` block data (`age == maximumAge`). |
| `require-hoe` | bool | `false` | Only run the square scan while the player is holding a hoe (any of wooden/stone/iron/golden/diamond/netherite). With no hoe, only the single broken crop is removed. |
| `cost` | double | `0.0` | Vault economy cost per trigger (one broken crop → one batch harvest). `0` or any value `≤ 0` = free. |
| `insufficient-funds` | message | — | Sent when the player lacks funds for the `cost`; the batch harvest is blocked (only the single broken crop is removed). |
| `materials` | list of Material names | wheat, carrots, potatoes, etc. | Crop materials to auto-harvest. Invalid names are skipped with a warning. |

> Radius scans a horizontal square `-radius`..`+radius` on the x/z axes at one block high → `(2×radius+1)² − 1` candidate blocks (e.g. radius 3 = 48 candidates). Lower the radius on lag-heavy worlds. The scan is performed on the server thread.

**Cooldown:** none.
