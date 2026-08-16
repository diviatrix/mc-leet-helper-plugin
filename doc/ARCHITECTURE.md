# Architecture

How LeetHelper is structured and works internally. For end-user configuration, permissions, and commands see [README](../README.md).

Contents:

- [Project Structure](#project-structure)
- [Feature Model](#feature-model)
- [Config Handling](#config-handling)
- [Permission Model](#permission-model)
- [Storage](#storage)
- [Vault / Economy Integration](#vault--economy-integration)

---

## Project Structure

```
src/main/
  java/com/leet/helper/
    HelperPlugin.java            # Plugin lifecycle, Vault setup, dynamic permission registration
    feature/
      AbstractFeature.java       # Base class: config, permissions, cooldowns, messages, cost
      FeatureManager.java        # Feature registry, enable/disable/toggle, toggle persistence
      DoubleJumpFeature.java     # Double jump implementation
      DurabilityFeature.java     # Durability multiplier implementation
      AutoCropFeature.java       # Auto crop harvest implementation
      BackFeature.java           # Death-back teleport implementation
      TreeFellerFeature.java     # Whole-tree felling implementation
      FallDamageFeature.java     # Fall-damage immunity implementation
      XpFeature.java             # Bonus XP for actions implementation
    command/
      HelperCommand.java         # /leeta list|toggle|info (+ tab completion)
      BackCommand.java           # /back
      LeetCommand.java           # /leet player feature toggles (+ tab completion)
    storage/
      StorageManager.java        # Runtime (in-memory) + persistent (SQLite) storage
    util/
      MiniMessageUtil.java       # MiniMessage helpers
  resources/
    plugin.yml                   # Plugin metadata, command & admin permission declarations
    config.yml                   # Global config
    features/
      _double_jump.yml
      _durability.yml
      _auto_crop.yml
      _back.yml
      _tree_feller.yml
      _fall_damage.yml
      _xp.yml
```

`HelperPlugin` boots the plugin, resolves Vault (Economy + Permission providers, both optional), and registers the feature permissions dynamically. `FeatureManager` owns the feature registry and the enable/disable/toggle lifecycle.

---

## Feature Model

Every gameplay feature extends `AbstractFeature`. The base class provides the shared life-cycle and the common behaviour used by all event handlers:

- **Config loading** — `loadConfig()` reads `base.*` and per-feature settings, then calls the subclass's `loadFeatureConfig(cfg)` for its own keys. `enable()`/`disable()` register/unregister the event listeners.
- **Gating** — `check(player)` enforces, in order: server `base.enabled` → the feature's `leet.feat.<id>` permission (`player.hasPermission`) → the player's personal `/leet` toggle → the `base.worlds` whitelist. All must pass.
- **Cooldowns** — `checkCooldown`/`setCooldown` use runtime storage for most features; `BackFeature` has its own persistent-cooldown helpers.
- **Messages** — `sendMessage(player, key, ...)` resolves a template from the feature's `messages` and delivers it per `base.message-type` (action bar, chat, or title) using MiniMessage.
- **Per-use cost** — `chargeUse(player)` applies the optional Vault `feature.cost` (see [Vault / Economy Integration](#vault--economy-integration)); all features except XP charge per use. Cost semantics: multi-block features charge once per trigger action (Auto Crop / Tree Feller), passive features per event (Durability / Fall Damage); Back charges per `/back`.

`FeatureManager.toggle()` disables a feature, re-enables it if it was off, and persists `base.enabled` back to the feature's on-disk YAML (survives a restart). It does **not** reload the rest of the config.

### Multi-block breakers and protection plugins

`AutoCropFeature` and `TreeFellerFeature` break many blocks per action. They run at `EventPriority.MONITOR` (so a protection plugin's cancellation of the original break is already visible) and route each additional block through `AbstractFeature.breakIfAllowed(player, block, tool)`, which fires a per-block `BlockBreakEvent` so claim/region plugins (GriefPrevention, WorldGuard, ...) are consulted per block. Protected blocks are skipped rather than force-broken.

---

## Config Handling

### Common feature config layout

Each feature config (`features/_<id>.yml`) shares the same layout:

```yaml
base:
  enabled: true                    # Kill switch. false = feature fully off (no listeners).
  permission: leet.feat.<id>       # Permission node controlling access
  default-permission: false        # true | op | false  (Bukkit permission default)
  worlds: []                       # Empty = all worlds. Non-empty = whitelist of world names.
  cooldown: 0                      # Seconds between uses. 0 = no cooldown.
  message-type: ACTION_BAR         # ACTION_BAR | CHAT | TITLE

feature:
  # Feature-specific settings (see per-feature sections)
  cost: 0                          # Optional per-use Vault cost (all features except XP)

messages:
  # key: "MiniMessage formatted string"
```

#### Three-level control per feature

Each feature has three independent on/off controls:

1. **`base.enabled`** — server-wide kill switch. When `false`, the feature's event listeners are **not registered** at all.
2. **`base.default-permission`** — the Bukkit default for the configured permission. Defaults to `false` (nobody can use the feature until you grant the node in your permission plugin, e.g. LuckPerms). `true` = everyone, `op` = ops only.
3. **`base.worlds`** — per-world whitelist. If non-empty, the feature only works in the listed world names. Empty list = works everywhere.

All three are checked by `check(player)` at the start of every relevant event or command. *All* must pass for the feature to act.

### Message Delivery Types

Messages are rendered with [MiniMessage](https://docs.advntr.dev/minimessage/format.html) and delivered according to `base.message-type`:

| Value | Delivery |
|---|---|
| `ACTION_BAR` | Sent to the player's action bar (default) |
| `CHAT` | Sent to chat |
| `TITLE` | Shown as a title (200ms fade-in, 2s stay, 500ms fade-out) |

A missing or empty message template silently produces no message.

### Per-Feature Configs

Each feature's config file and its reference doc:

| Feature | Config file | Reference |
|---|---|---|
| Double Jump | `_double_jump.yml` | [feature-double-jump](features/double-jump.md) |
| Durability | `_durability.yml` | [feature-durability](features/durability.md) |
| Auto Crop | `_auto_crop.yml` | [feature-auto-crop](features/auto-crop.md) |
| Back | `_back.yml` | [feature-back](features/back.md) |
| Tree Feller | `_tree_feller.yml` | [feature-tree-feller](features/tree-feller.md) |
| Fall Damage | `_fall_damage.yml` | [feature-fall-damage](features/fall-damage.md) |
| XP | `_xp.yml` | [feature-xp](features/xp.md) |

### Automatic config merging (backfill)

Every config — the global `config.yml` **and** each feature file — is merged against the bundled default at startup (`mergeMissingKeys`). Any default keys missing from the on-disk file are added (and the file saved), while the server admin's existing values are preserved. Consequences:

- Updating the jar automatically brings new options (e.g. `require-hoe`, `cost`) into existing configs.
- Deleting a key yourself will **not** persist — it is restored from the default on the next start.
- Removing a key is done by overriding its value, not by deleting it.

---

## Permission Model

- **Admin permissions** (`leet.admin`, `leet.admin.list|toggle|info`) are declared statically in `plugin.yml` and gate `/leeta`.
- **Feature permissions** (`leet.feat.<id>`) are **not** in `plugin.yml`. `HelperPlugin` registers them at runtime on every startup via `Bukkit.getPluginManager().addPermission()` using the node from `base.permission` and the default from `base.default-permission`. Hence **config changes to permissions require a restart.**
- **Checks** use Bukkit's `player.hasPermission(permission)` everywhere. Even with Vault installed, the plugin does **not** route permission lookups through Vault's `Permission` provider — that provider is resolved at startup but unused.

Because feature permissions are denied by default and gated by `leet.feat.<id>`, every feature needs an explicit grant (e.g. LuckPerms) before it does anything. See [Feature Permissions](../README.md#feature-permissions-dynamic) in the README for the per-feature table and `/leet` model.

---

## Storage

`StorageManager` provides two storage layers.

### Runtime (in-memory)

- Backed by a nested map: `Map<featureId, Map<key, Map<uuid, Long>>>`.
- **Lost on server restart.**
- Used for **Double Jump** cooldowns.

### Persistent (SQLite)

- Database file: `plugins/LeetHelper/data.db`.
- Single table:

  ```
  kv_store(feature_id TEXT, key TEXT, uuid TEXT, value TEXT, updated_at INTEGER,
           PRIMARY KEY (feature_id, key, uuid))
  ```

- **Survives restarts.**
- Used for **Back** death locations (JSON payloads), persistent cooldowns, per-player `/leet` feature toggles (`feature_id` = feature, `key` = `user-toggle`, `value` = `true`/`false`; absent = enabled), and (for XP, when configured) placed-block tracking.
- Uses Bukkit's bundled SQLite JDBC (`jdbc:sqlite:...`). No external driver needed.

> **Backups:** `data.db` is written by the plugin. Backing it up preserves saved death locations and player toggle preferences. Deleting it clears all of that state.

---

## Vault / Economy Integration

Vault is an **optional soft dependency** (`softdepend: [Vault]`). The plugin detects it at startup and resolves the Vault `Economy` provider; it works entirely without Vault.

| Area | Without Vault | With Vault (economy provider) |
|---|---|---|
| Economy (`feature.cost`) | Cost is silently skipped — no charges, no balance checks | Cost checked and deducted per use for any feature with `feature.cost > 0` |
| Permissions | Uses Bukkit `player.hasPermission()` | Stills uses Bukkit `player.hasPermission()` (the Vault `Permission` provider is resolved but **not used**) |

Notes:

- Any feature can declare a per-use `feature.cost` (default `0` = free); all features except XP do.
- `cost` is charged only when `feature.cost > 0`.
- If the player lacks funds, the `insufficient-funds` message is shown and the use is blocked.

---

## Related docs

- [BUILDING.md](BUILDING.md) — how to compile and package
- [README](../README.md) — configuration, permissions, commands, and operational usage