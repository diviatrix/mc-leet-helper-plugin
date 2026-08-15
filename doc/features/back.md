# Feature: Back

> Common `base:`/`messages:` config layout and control model: [README](../README.md#common-feature-config-structure) · Admin: [Admin.md](../Admin.md) · All features: [index](README.md)

Teleports players to their last death location. **Persistent** via SQLite — survives server restarts. Config file `features/_back.yml`.

**Permissions**
- **Node:** `leet.feat.back` · **default:** `false` (nobody).
- Grant the node (e.g. LuckPerms) to have death locations saved **and** to use `/back` (the command is registered with `permission: leet.feat.back`). The node alone is not enough — `base.enabled`, the permission, and `base.worlds` must all pass.
- Set `base.default-permission` in `_back.yml` to `true` (everyone) or `op` (ops only) to change the out-of-box default. Nodes are registered at startup, so permission config changes require a **restart**.

**Behavior — on death**
1. Player dies → `check()` (enabled + permission + world).
2. Death location (world, x, y, z, yaw, pitch, timestamp) is serialized to JSON.
3. Stored in SQLite.
4. `death-location-saved` message is sent.

**Behavior — on `/back`**
1. Loads the death location from SQLite.
2. Checks, **in order**:
   - A saved location exists.
   - The location has not expired (`max-age` seconds since the timestamp).
   - Taught world matches — you **must** still be in the same world as the death location.
   - The cooldown (persistent, SQLite) has elapsed.
   - If `cost > 0`, the player has sufficient funds (Vault); otherwise blocked + message.
3. If `cost > 0`, the cost is deducted via Vault.
4. Player is teleported.
5. Cooldown is saved to SQLite; the saved death location is deleted.
6. `teleport` message is sent.

```yaml
base:
  enabled: true
  permission: leet.feat.back
  default-permission: false
  worlds: []
  cooldown: 60
  message-type: ACTION_BAR

feature:
  max-age: 300     # Seconds before a death location expires
  cost: 0.0        # Vault economy cost per use (0.0 = free)

messages:
  death-location-saved: "<green>Death location saved! Use /back to return."
  teleport: "<green>Teleported to your death location."
  cooldown-active: "<red>Cooldown active! Wait <time> seconds."
  expired: "<red>Your death location has expired."
  insufficient-funds: "<red>Insufficient funds! Cost: <cost>"
  wrong-world: "<red>You must be in the same world as your death location."
  no-location: "<red>No death location found."
```

| Key | Type | Default | Description |
|---|---|---|---|
| `max-age` | int | `300` | Seconds before a death location expires. Expired locations are deleted. |
| `cost` | double | `0.0` | Vault economy cost per use. `0` or any value `≤ 0` = free (cost is only applied when `> 0`). |

| Message | Placeholders | Sent when |
|---|---|---|
| `death-location-saved` | — | Death location stored |
| `teleport` | — | Teleport succeeded |
| `cooldown-active` | `<time>` | Cooldown still active (remaining seconds) |
| `expired` | — | Death location older than `max-age` |
| `insufficient-funds` | `<cost>` | Cost set and player lacks balance |
| `wrong-world` | — | Player in a different world than death location |
| `no-location` | — | No saved location, or permission/world blocked |

**Restrictions & notes**
- **Cross-world teleportation is not allowed** — you must be in the world where you died.
- Cooldown is **persistent** (survives restarts) and stored in SQLite, separate from the runtime cooldown used by other features.
- There is **no admin bypass** — cooldown, cost and `max-age` apply equally to everyone.
- `cost` requires Vault with a running economy provider. Without Vault, cost is silently skipped (no charge, no check).
