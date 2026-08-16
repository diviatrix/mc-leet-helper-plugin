# Feature: Double Jump

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../Admin.md)

Allows a mid-air double jump. Config file `features/_double_jump.yml`.

**Permissions**
- **Node:** `leet.feat.double_jump` · **default:** `false` (nobody).
- Grant the node (e.g. LuckPerms) to allow the double jump; it also unlocks the `/leet dj` personal off-toggle. The node alone is not enough — `base.enabled`, the permission, and `base.worlds` must all pass.
- Set `base.default-permission` in `_double_jump.yml` to `true` (everyone) or `op` (ops only) to change the out-of-box default. Nodes are registered at startup, so permission config changes require a **restart**.

**Behavior**
1. Player on the ground → flight is enabled for them automatically.
2. Player double-taps space (`PlayerToggleFlightEvent`) → the flight toggle is cancelled, flight disabled, and a velocity vector is applied in the player's look direction.
   - Horizontal velocity = look direction × `horizontal-multiplier`.
   - Vertical velocity = fixed `vertical-multiplier`.
3. The runtime cooldown starts.
4. When the player lands (or enters a vehicle), flight is re-enabled.

**Fall damage is no longer part of Double Jump** — it has its own feature and `/leet` toggle (see [Feature: Fall Damage](fall-damage.md)).

**Limits:** skipped entirely for Creative and Spectator game modes. The movement check is **block-level only** — it only re-enables flight when the player's block position changes (a performance optimization).

```yaml
base:
  enabled: true
  permission: leet.feat.double_jump
  default-permission: false
  worlds: []
  cooldown: 1
  message-type: ACTION_BAR

feature:
  horizontal-multiplier: 0.25  # Forward/sideways velocity multiplier
  vertical-multiplier: 1.0     # Upward velocity
  cost: 0                      # Vault economy cost per jump (0 = free)

messages:
  insufficient-funds: "<red>Insufficient funds! Cost: <cost>"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `horizontal-multiplier` | double | `0.25` | Horizontal (look-direction) velocity multiplier |
| `vertical-multiplier` | double | `1.0` | Fixed upward velocity on jump |
| `cost` | double | `0.0` | Vault economy cost per use. `0` or any value `≤ 0` = free (cost is only applied when `> 0`). |
| `insufficient-funds` | message | — | Sent when the player lacks funds for the `cost`; the jump is blocked |

**Cooldown:** runtime only (in-memory), lost on restart. Default `1` second. When on cooldown, the jump is skipped (no velocity) but the flight-toggle event is still cancelled.
