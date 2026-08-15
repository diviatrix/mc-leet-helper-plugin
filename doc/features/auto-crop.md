# Feature: Auto Crop

> Common `base:`/`messages:` config layout and control model: [README](../README.md#common-feature-config-structure) · Admin: [Admin.md](../Admin.md) · All features: [index](README.md)

Auto-harvests nearby mature crops when a player breaks one. Config file `features/_auto_crop.yml`.

**Permissions**
- **Node:** `leet.feat.auto_crop` · **default:** `false` (nobody).
- Grant the node (e.g. LuckPerms) to allow auto-harvesting; it also unlocks the `/leet crop` personal off-toggle. The node alone is not enough — `base.enabled`, the permission, and `base.worlds` must all pass.
- Set `base.default-permission` in `_auto_crop.yml` to `true` (everyone) or `op` (ops only) to change the out-of-box default. Nodes are registered at startup, so permission config changes require a **restart**.

**Behavior**
1. A player breaks a block (`BlockBreakEvent`).
2. If the broken block is in `materials` (and, if `require-mature` is `true`, it is fully grown), the feature scans a cube.
3. The cube spans `-radius`..`+radius` on all three axes around the broken block (excluding the source block itself).
4. Every nearby block of the **same material** (and, if enabled, the **same maturity**) is broken with `breakNaturally(tool)`, using the player's main-hand item.

If `require-hoe` is `true`, the cube scan only happens when the player is harvesting with a hoe in their hand — otherwise only the single broken crop is removed (default vanilla behavior).

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
  materials:
    - WHEAT
    - CARROTS
    - POTATOES
    - BEETROOTS
    - NETHER_WART
    - COCOA
    - SWEET_BERRY_BUSH

messages: {}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `radius` | int | `3` | Cube half-size around the broken block. Values > 5 are **clamped to 5**. |
| `require-mature` | bool | `true` | Only break fully grown crops. Maturity uses `Ageable` block data (`age == maximumAge`). |
| `require-hoe` | bool | `false` | Only run the cube scan while the player is holding a hoe (any of wooden/stone/iron/golden/diamond/netherite). With no hoe, only the single broken crop is removed. |
| `materials` | list of Material names | wheat, carrots, potatoes, etc. | Crop materials to auto-harvest. Invalid names are skipped with a warning. |

> Radius scans a cube `-radius`..`+radius` on each axis → `(2×radius+1)³ − 1` candidate blocks (e.g. radius 3 = 342 candidates). Lower the radius on lag-heavy worlds. The scan is performed on the server thread.

**Cooldown:** none.
