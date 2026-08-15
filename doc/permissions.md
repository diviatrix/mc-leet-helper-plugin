# HelperPlugin — Permissions

Complete reference for every permission node, its default, and the rules that connect permissions to features.

---

## Table of Contents

- [Quick summary](#quick-summary)
- [Full permission list](#full-permission-list)
  - [Admin permissions (static)](#admin-permissions-static)
  - [Feature permissions (dynamic)](#feature-permissions-dynamic)
  - [Command-facing permissions](#command-facing-permissions)
- [How feature permissions are registered](#how-feature-permissions-are-registered)
- [The three-level control model](#the-three-level-control-model)
- [Gate order (what the plugin actually checks)](#gate-order-what-the-plugin-actually-checks)
- [/leet command permission logic](#leet-command-permission-logic)
- [Permission vs Vault](#permission-vs-vault)
- [Integration with LuckPerms / PEX](#integration-with-luckperms--pex)
- [Common defaults & how to lock down](#common-defaults--how-to-lock-down)

---

## Quick summary

| Permission | Default | Controls |
|---|---|---|
| `helper.admin` | op | `/helper` command (includes children below) |
| `helper.admin.list` | op | `/helper list` |
| `helper.admin.toggle` | op | `/helper toggle` |
| `helper.admin.info` | op | `/helper info` |
| `leet.feat.double_jump` | false | Player can use Double Jump (and `/leet dj`) |
| `leet.feat.durability` | false | Durability multiplier applies |
| `leet.feat.auto_crop` | false | Player can use Auto Crop (and `/leet crop`) |
| `leet.feat.back` | false | Death locations saved + `/back` command |
| `leet.feat.tree_feller` | false | Player can use Tree Feller (and `/leet tree`) |
| `leet.feat.fall_damage` | false | Player can use Fall Damage immunity (and `/leet fall`) |
| `leet.feat.xp` | false | Player can earn bonus XP (and `/leet xp`) |

- Feature defaults come from each feature's YAML (`base.default-permission`), **not** from `plugin.yml`.
- Admin defaults are hardcoded in `plugin.yml` (`op`).
- The `/leet` command has no command-level permission (anyone may attempt it); its availability is gated in-code by the feature permissions (see below).

---

## Full permission list

### Admin permissions (static)

Declared in `plugin.yml`. They control the `/helper` management command.

| Permission | Default | Description |
|---|---|---|
| `helper.admin` | op | Full admin access to `/helper`. Automatically inherits `helper.admin.list`, `helper.admin.toggle`, and `helper.admin.info` via its `children` block. |
| `helper.admin.list` | op | Use `/helper list`. |
| `helper.admin.toggle` | op | Use `/helper toggle <id>`. |
| `helper.admin.info` | op | Use `/helper info <id>`. |

Notes:
- `helper.admin` alone is enough for an operator — the children are implied.
- These are **Bukkit native** permissions: they integrate with your permission plugin and are never bypassed by the plugin itself.

### Feature permissions (dynamic)

Declared **at runtime**, not in `plugin.yml`. Each is registered from its feature's config on startup (see below). The default comes from `base.default-permission`.

| Permission | ID | Default | What it gates |
|---|---|---|---|
| `leet.feat.double_jump` | `double_jump` | false | Double Jump events fire for the player (`PlayerToggleFlightEvent` / `PlayerMoveEvent`) and `/leet dj` is available. |
| `leet.feat.durability` | `durability` | false | Durability multiplier applies to the player's items. |
| `leet.feat.auto_crop` | `auto_crop` | false | Auto-harvest fires for the player (`BlockBreakEvent`) and `/leet crop` is available. |
| `leet.feat.back` | `back` | false | Death location is saved on death **and** `/back` works. (Single node controls both — see [Gate order](#gate-order-what-the-plugin-actually-checks) below.) |
| `leet.feat.tree_feller` | `tree_feller` | false | Whole-tree felling fires for the player (`BlockBreakEvent`) and `/leet tree` is available. |
| `leet.feat.fall_damage` | `fall_damage` | false | Fall-damage immunity fires for the player (`EntityDamageEvent`, cause `FALL`) and `/leet fall` is available. |
| `leet.feat.xp` | `xp` | false | Bonus XP is granted to the player for mining/woodcutting/crops/fishing/building/killing and `/leet xp` is available. |

Feature-default → Bukkit default mapping:

| `base.default-permission` | Bukkit `PermissionDefault` | Effect |
|---|---|---|
| `true` | `PermissionDefault.TRUE` | Everyone (any user) |
| `op` | `PermissionDefault.OP` | Operators only |
| `false` | `PermissionDefault.FALSE` | Nobody (must be explicitly granted) |

### Command-facing permissions

These appear in `plugin.yml` under `commands:` and are enforced by Bukkit before the command executor even runs.

| Command | Command-level permission | Notes |
|---|---|---|
| `/helper` | `helper.admin` | Non-ops without `helper.admin` never reach the executor. |
| `/back` | `leet.feat.back` | Player without the node is blocked at the command layer; inside, `BackFeature.check()` re-verifies permission. |
| `/leet` | *(none)* | Any player can attempt it. Availability is enforced **in-code** against the feature permissions (see [gate order](#gate-order-what-the-plugin-actually-checks)). |

---

## How feature permissions are registered

In `HelperPlugin.onEnable()` → `registerFeaturePermissions()`, after features are enabled:

1. Iterate every registered feature.
2. Read `feature.permission()` (from `base.permission`, default `leet.feat.<id>`) and `feature.getDefaultPermission()` (from `base.default-permission`, default `true`).
3. Map the default string to a Bukkit `PermissionDefault` (`true`/`op`/`false`).
4. Call `Bukkit.getPluginManager().addPermission(new Permission(node, default))`.

Because this happens once per startup, **changing `base.permission` or `base.default-permission` requires a server restart** (there is no reload command). `/helper toggle` changes only `base.enabled`, not permissions.

---

## The three-level control model

Permissions are one of **three** independent on/off switches per feature:

1. **`base.enabled`** — server-wide kill switch (config). `false` = feature's listeners are not registered at all. Managed by `/helper toggle`.
2. **`base.default-permission` / the feature permission** — who is allowed to use the feature. Admin-controlled.
3. **`base.worlds`** — world whitelist (empty = all worlds).

Plus a **fourth, player-level layer** added by the `/leet` command: a per-player off-toggle (applies to Double Jump, Auto Crop, Tree Feller, Fall Damage, and XP).

---

## Gate order (what the plugin actually checks)

Every feature event handler calls `AbstractFeature.check(player)`. It returns `true` (feature acts) **only if all** of the following pass, in order:

```
1. base.enabled            → server-wide kill switch
2. player.hasPermission(permission)   → feature permission
3. player personal /leet toggle       → off (stored "false") blocks
4. world whitelist         → player's world must be in base.worlds (if non-empty)
```

If any fails, the handler returns without acting. So a feature permission is **necessary but not sufficient** — the server toggle, the player's personal toggle, and the world all matter too.

- **Double Jump** handler checks also exclude Creative/Spectator game modes.
- **Back** uses `check()` both to decide whether to save a death location and whether `/back` may teleport (its command-level permission de-duplicates this).

---

## /leet command permission logic

`LeetCommand` gates itself entirely on the feature permissions:

- **No feature permissions at all** → `/leet` (and `/leet list`, and its tab completion) are dead: it replies `No permission.` and does nothing.
- **Only permissioned features are offered** — the status list and tab completion include only the features whose `leet.feat.<id>` the player holds.
- **Per-subcommand re-check** — `/leet dj` without `leet.feat.double_jump`, or `/leet crop` without `leet.feat.auto_crop` (likewise `/leet tree`, `/leet fall`, `/leet xp`), is declined with `No permission.`.

The toggle itself is a personal **off-switch** nested under the base permission: toggling on never grants the permission, toggling only disables the feature for that player. Storage is per-player in the SQLite `kv_store` (key `user-toggle`, value `true`/`false`; absent = enabled).

---

## Permission vs Vault

**All permission checks are Bukkit-native** (`player.hasPermission`). The plugin resolves Vault's `Permission` provider at startup (`vaultPermission`), but **never uses it**. Vault only drives the Back feature's economy cost. Whether Vault is installed does not affect any permission.

---

## Integration with LuckPerms / PEX

Because every node is a standard Bukkit permission:

- Use the exact node names above in LuckPerms, PEX, GroupManager, etc. (e.g. `leet.feat.double_jump true`).
- Dynamically registered nodes appear in the permission plugin's tree after startup.
- To grant/deny beyond the config default, set the node explicitly on a group/user; that overrides the `default-permission` default.
- `helper.admin` inherits its children automatically, so granting `helper.admin` alone is enough for ops.

---

## Common defaults & how to lock down

| Goal | Action |
|---|---|
| Give a player/admin `/helper` | Grant `helper.admin` (or run as op). |
| Make a feature ops-only | Set `base.default-permission: op` in `features/_<id>.yml`, restart. |
| Disable a feature for everyone server-wide | `/helper toggle <id>` (persists `base.enabled: false`). |
| Restrict `/leet` entirely | Not granted by default (all features default to `false`); simply grant no feature permissions. `/leet` then auto-hides/refuses for every player. |
| Let a player turn a feature off just for themselves | Grant them `/leet` access (i.e. the feature permission) and tell them `/leet <alias>` (Double Jump `dj`, Auto Crop `crop`, Tree Feller `tree`, Fall Damage `fall`, XP `xp`). |
| Block cross-world use | Set `base.worlds` to the allowed world names. |

> Restart required after editing permission-related config (`plugin.yml` edits need a restart; feature YAML edits need a restart because perms register at startup).