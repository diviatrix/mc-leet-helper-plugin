# Feature: Fall Damage

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../Admin.md)

**Owning plugin:** LeetCore · Config file `plugins/LeetCore/features/fall_damage.yml`.

Negates all fall damage for eligible players, as a standalone feature **independent of Double Jump**.

**Permissions**
- **Node:** `leet.feat.fall_damage` · **default:** `false` (nobody).
- Grant the node (e.g. LuckPerms) to give a player fall-damage immunity; it also unlocks the `/leet fall` personal off-toggle. The node alone is not enough — `base.enabled`, the permission, and `base.worlds` must all pass.
- Set `base.default-permission` in `fall_damage.yml` to `true` (everyone) or `op` (ops only) to change the out-of-box default. Nodes are registered at startup, so permission config changes require a **restart**.

**Behavior**
1. A player takes fall damage (`EntityDamageEvent`, cause `FALL`).
2. If the player passes the feature checks (enabled + `leet.feat.fall_damage` permission + personal `/leet` toggle + world), the fall damage is cancelled entirely.

There are no feature-specific config options **except the per-use `cost`** — the feature is otherwise controlled by `base.enabled`, the `leet.feat.fall_damage` permission, and the personal `/leet fall` toggle. A non-zero `cost` charges the player for each fall that is negated and blocks the negation (damage applies) when they lack funds.

```yaml
base:
  enabled: true
  permission: leet.feat.fall_damage
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  cost: 0              # Vault economy cost per negated fall (0 = free)

messages:
  insufficient-funds: "<red>Insufficient funds! Cost: <cost>"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `cost` | double | `0.0` | Vault economy cost per negated fall. `0` or any value `≤ 0` = free. |
| `insufficient-funds` | message | — | Sent when the player lacks funds for the `cost`; the fall damage is **not** negated |

**Cooldown:** none.
