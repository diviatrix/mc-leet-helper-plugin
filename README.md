# LeetHelper

A **Paper 26.2** plugin providing modular gameplay features. Each feature has its own on-disk YAML config, its own permission node, per-world whitelisting, optional cooldowns, and (for the Back feature) optional Vault economy integration.

Licensed under **CC0 1.0** (public domain) — see [LICENSE](LICENSE).

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Building from Source](#building-from-source)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
  - [config.yml](#configyml)
  - [Common Feature Config Structure](#common-feature-config-structure)
  - [Log Levels](#log-levels)
  - [Message Delivery Types](#message-delivery-types)
  - [Per-Feature Configs](#per-feature-configs)
- [Permissions](#permissions)
  - [Admin Permissions](#admin-permissions)
  - [Feature Permissions](#feature-permissions)
- [Commands](#commands)
  - [/leeta](#leeta)
  - [/back](#back)
- [Features](#features)
  - [Feature docs (doc/features/)](doc/features/README.md)
- [Storage](#storage)
- [Vault / Economy Integration](#vault--economy-integration)
- [Troubleshooting](#troubleshooting)
- [Known Limitations](#known-limitations)
- [License](#license)

---

## Overview

LeetHelper registers seven gameplay features plus one admin command.

| Feature | ID | Description |
|---|---|---|
| Double Jump | `double_jump` | Mid-air double jump with configurable velocity and cooldown |
| Durability | `durability` | Configurable durability multiplier for whitelisted tools/equipment |
| Auto Crop | `auto_crop` | Auto-harvest nearby mature crops when breaking one |
| Back | `back` | Teleport back to your death location, with optional cost and cooldown |
| Tree Feller | `tree_feller` | Felling a log drops the whole connected tree |
| Fall Damage | `fall_damage` | Negates all fall damage for eligible players |
| XP | `xp` | Bonus vanilla XP for mining, woodcutting, crops, fishing, building, and killing |

Admin features are managed with the `/leeta` command (`list`, `toggle`, `info`).

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | Paper **26.2**+ (Bundled API jar is compiled against `26.2`). Spigot/CraftBukkit are **not** supported. |
| Java | **26+** (the build toolchain targets Java 26). Run your server on a JVM that supports the compiled bytecode. |
| Vault | Optional. Only needed for the Back economy cost. The plugin works fully without it. |

> Java runtime vs. build JDK: the Gradle build uses a Java 26 toolchain, and the plugin bytecode targets Java 26. Use a Java 26 (or later) runtime on your server when running the plugin.

---

## Installation

1. **Build or obtain the jar** — see [Building from Source](#building-from-source). The build produces `build/libs/leet-helper-1.1.1.jar`.
2. **Copy the jar** into your server's `plugins/` folder:

   ```bash
   cp build/libs/leet-helper-1.1.1.jar /path/to/server/plugins/
   ```

3. **Start the server.** On first launch the plugin creates its data folder and writes default configuration files:

   ```
   plugins/LeetHelper/
   ├── config.yml                 # Global settings (log level, schema version)
   ├── data.db                    # SQLite database (Back feature persistence)
   └── features/
       ├── _double_jump.yml
       ├── _durability.yml
       ├── _auto_crop.yml
       ├── _back.yml
       ├── _tree_feller.yml
       ├── _fall_damage.yml
       └── _xp.yml
   ```

4. **Configure to taste** — edit the files inside `plugins/LeetHelper/features/`. Restart the server for changes to take effect (there is **no reload command**; `base.enabled` toggles are the only thing that can be changed live, via `/leeta toggle`).

> **Updating the plugin:** on startup the global `config.yml` and every feature config are merged against the bundled defaults. Any **new key** introduced by a newer plugin version (e.g. `require-hoe`) is automatically added to your existing configs while all your other values are preserved. No manual copying needed.

---

## Building from Source

### Prerequisites

- **JDK 26+** (the Gradle toolchain requires it)
- **Gradle 9.7** (a wrapper is included — you only need to invoke `./gradlew`)
- **Internet connection** on the first build (downloads the Paper dev bundle)

### Commands

```bash
# Full build (compiles and produces the jar)
./gradlew build

# Clean + build
./gradlew clean build

# Just compile, skip packaging
./gradlew compileJava
```

**Output artifact:** `build/libs/leet-helper-1.1.1.jar`

> **First build note:** the paperweight plugin downloads and runs a Paper server JAR to produce the remapped API (~40s). Subsequent builds are cached and faster.

**What the build does:** the `build.gradle.kts` uses `io.papermc.paperweight.userdev` (v2.0.0-beta.21) with `paperDevBundle("26.2.build.+")`. The Vault API is included as a `compileOnly` dependency (JitPack `com.github.MilkBowl:VaultAPI:1.7.1`).

### Expectations & Verification

There is no unit-test suite or test plugin wired into the Gradle build (`gradle.properties` enables Gradle configuration-cache only). Verification is manual on a Paper 26.2 server — see the per-feature behavior notes and [Troubleshooting](#troubleshooting).

---

## Project Structure

```
src/main/
  java/com/leet/helper/
    HelperPlugin.java            # Plugin lifecycle, Vault setup, dynamic permission registration
    feature/
      AbstractFeature.java       # Base class: config, permissions, cooldowns, messages
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

---

## Configuration

### config.yml

Global, top-level settings.

```yaml
config-version: 1
log-level: INFO
```

| Key | Type | Description |
|---|---|---|
| `config-version` | integer | Schema version of `config.yml`. On startup, any keys missing from the on-disk file are auto-added from the bundled default while preserving existing values (see [Automatic config merging on update](#automatic-config-merging-on-update)) for `config.yml`). |
| `log-level` | `OFF`, `INFO`, `DEBUG` | Logging verbosity. See [Log Levels](#log-levels). |

### Log Levels

| Level | What is logged |
|---|---|
| `OFF` | Only critical errors (SEVERE), e.g. storage failures, feature-enable exceptions |
| `INFO` | Startup, no-Vault notice, feature enable failures, invalid-whitelist warnings, config errors |
| `DEBUG` | Reserved for fine-grained diagnostics; currently no extra DEBUG output is emitted beyond INFO |

The `log-level` is read from `config.yml`, though most feature-related messages are logged at the `INFO`/`WARNING`/`SEVERE` level regardless.

> **Console prefix & color:** startup and status messages (e.g. `[LeetHelper] Initializing LeetHelper v1.1.1`, `[LeetHelper] Enabled 4/4 feature(s).`, the Vault status) are sent to the console via the console sender with a green `[LeetHelper]` prefix. These colored lines appear in the live console but color codes are stripped from `logs/latest.log`. The automatically-printed Paper line `[LeetHelper] Enabling LeetHelper v1.1.1` and the plugin-logger `[LeetHelper]` WARN/SEVERE lines come from Paper's logger and are not recolored.

> **Renaming a plugin (`name` in `plugin.yml`):** the data folder and all file paths follow the plugin's display name (now `plugins/LeetHelper/`). If you previously ran under the old name (`plugins/HelperPlugin/`), move those files across to keep existing configs and the SQLite `data.db`.

### Common Feature Config Structure

All features use the same layout. Feature configs live in `features/_<id>.yml`.

```yaml
base:
  enabled: true                    # Kill switch. false = feature fully off (no listeners).
  permission: leet.feat.<id>  # Permission node controlling access
  default-permission: false        # true | op | false  (Bukkit permission default)
  worlds: []                       # Empty = all worlds. Non-empty = whitelist of world names.
  cooldown: 0                      # Seconds between uses. 0 = no cooldown.
  message-type: ACTION_BAR         # ACTION_BAR | CHAT | TITLE

feature:
  # Feature-specific settings (see per-feature sections)

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

#### Automatic config merging on update

Every config — the global `config.yml` **and** each feature file under `features/` — is merged against the bundled default at startup. Any default keys that are missing from the on-disk file are added (and the file saved), while the server admin's existing values are left untouched. Consequences:

- Updating the jar automatically brings new options (e.g. `require-hoe`) into existing configs.
- Deleting a key yourself will NOT persist — it is restored from the default on the next start.
- Removing a key is done by overriding its value (or by setting it to a value equivalent to the default), not by deleting it.

---

### Per-Feature Configs

Full per-feature behavior, config files, and key tables live in **[doc/features/](doc/features/README.md)** — one document per feature.

| Feature | Config file | Reference |
|---|---|---|
| Double Jump | `_double_jump.yml` | [feature-double-jump](doc/features/double-jump.md) |
| Durability | `_durability.yml` | [feature-durability](doc/features/durability.md) |
| Auto Crop | `_auto_crop.yml` | [feature-auto-crop](doc/features/auto-crop.md) |
| Back | `_back.yml` | [feature-back](doc/features/back.md) |
| Tree Feller | `_tree_feller.yml` | [feature-tree-feller](doc/features/tree-feller.md) |
| Fall Damage | `_fall_damage.yml` | [feature-fall-damage](doc/features/fall-damage.md) |
| XP | `_xp.yml` | [feature-xp](doc/features/xp.md) |

---

## Permissions

Admin permissions for `/leeta` are in **[doc/Admin.md](doc/Admin.md)**. Each gameplay feature's `leet.feat.*` permission is documented in its own feature document (index: [doc/features/README.md](doc/features/README.md)).

### Admin Permissions (`/leeta`)

Declared statically in `plugin.yml`. They control access to the `/leeta` command. Full reference in [doc/Admin.md](doc/Admin.md).

| Permission | Default | Description |
|---|---|---|
| `leet.admin` | op | Full admin access **to commands** (children included). |
| `leet.admin.list` | op | Use `/leeta list` |
| `leet.admin.toggle` | op | Use `/leeta toggle` |
| `leet.admin.info` | op | Use `/leeta info` |

`leet.admin` automatically includes the three children (`list`, `toggle`, `info`) via its `children` map.

### Command-Facing Permissions

Declared in `plugin.yml` on the commands themselves:

| Permission | Default | Found on |
|---|---|---|
| `leet.admin` | op | `leeta` command (base command requires it) |
| `leet.feat.back` | (dynamic) | `back` command |

### Feature Permissions (dynamic)

Feature permissions are **not** declared in `plugin.yml`. Instead, `HelperPlugin` registers them at runtime from each feature's config: on every startup it calls `Bukkit.getPluginManager().addPermission()` with the node from `base.permission` and the default from `base.default-permission`. Each node is documented in its feature doc's **Permissions** section.

| Permission | Default (from config) | Feature doc |
|---|---|---|
| `leet.feat.double_jump` | false | [Double Jump](doc/features/double-jump.md) |
| `leet.feat.durability` | false | [Durability](doc/features/durability.md) |
| `leet.feat.auto_crop` | false | [Auto Crop](doc/features/auto-crop.md) |
| `leet.feat.back` | false | [Back](doc/features/back.md) |
| `leet.feat.tree_feller` | false | [Tree Feller](doc/features/tree-feller.md) |
| `leet.feat.fall_damage` | false | [Fall Damage](doc/features/fall-damage.md) |
| `leet.feat.xp` | false | [XP](doc/features/xp.md) |

`base.default-permission` maps to a Bukkit default:
- `true` → `PermissionDefault.TRUE` (every player)
- `op` → `PermissionDefault.OP` (ops only)
- `false` → `PermissionDefault.FALSE` (nobody)

**How permission checks happen:** checks use Bukkit's `player.hasPermission(permission)` everywhere. Feature permissions are moderately standard Bukkit permission nodes, so they integrate with LuckPerms, PEX, GroupManager, etc. Even with Vault installed, the plugin does **not** route permission lookups through Vault's `Permission` provider — the Vault permission provider is resolved at startup but currently unused.

> **Restart required for permission changes:** because feature permissions are registered once at startup, editing `base.permission` or `base.default-permission` requires a server restart (or replugin) to take effect.

---

## Commands

### /leeta

Admin command for managing features. Requires the `leeta` command permission (`leet.admin`, op by default). Full reference in [doc/Admin.md](doc/Admin.md).

| Subcommand | Permission | Description |
|---|---|---|
| `/leeta` | (base command) | Prints usage: `/leeta <list\|toggle\|info>` |
| `/leeta list` | `leet.admin` | Lists all features with ON/OFF status |
| `/leeta toggle <id>` | `leet.admin.toggle` | Toggles a feature on/off and **persists** `base.enabled` to its YAML |
| `/leeta info <id>` | `leet.admin` | Shows the feature's ID, permission node, and current status |

Tab completion is provided for subcommands and feature IDs.

**On toggle:** `FeatureManager.toggle()` disables the feature (unregisters listeners), re-enables it if it was off, and writes the new state back to `base.enabled` in the feature's YAML file — so the toggle survives a restart. A toggle does **not** reload the rest of the config; config file edits still need a restart.

### /back

| Command | Permission | Description |
|---|---|---|
| `/back` | `leet.feat.back` | Teleports the player to their last death location (see [Feature: Back](doc/features/back.md)) |

This command is player-only (the console receives a "This command can only be used by players." message). On success/failure, feedback is delivered via the Back feature's `message-type`.

### /leet

Player-side feature toggles. Each player can turn supported features **off for themselves** (it's an off-switch — it never grants or revokes access). Persisted per-player in the SQLite `kv_store`, so preferences survive restarts.

| Subcommand | Description |
|---|---|
| `/leet` or `/leet list` | Show your current ON/OFF status for each feature you have |
| `/leet dj` | Toggle **Double Jump** on/off for yourself |
| `/leet crop` | Toggle **Auto Crop** on/off for yourself |
| `/leet tree` | Toggle **Tree Feller** on/off for yourself |
| `/leet fall` | Toggle **Fall Damage** on/off for yourself |
| `/leet xp` | Toggle **XP** on/off for yourself |

**Permission model** — `/leet` is permission-gated by the underlying feature permissions:
- The command is only available to players who have at least **one** `leet.feat.<id>` permission. If a player has **none**, `/leet` reports `No permission.` and does nothing (including `list`, and no tab completion).
- Tab completion and the status list only show the features the player is actually permissioned for.
- Toggling a feature still checks that feature's permission (e.g. `leet.feat.double_jump`); without it, `/leet <sub>` is declined.
- Because these features default to `false`, `/leet` is **not** available out of the box — a player must be granted at least one feature permission first (see [Feature Permissions](#feature-permissions-dynamic)). Grant e.g. `leet.feat.double_jump`, `leet.feat.auto_crop`, `leet.feat.tree_feller`, or `leet.feat.fall_damage` in your permission plugin to unlock the corresponding `/leet` subcommands.

**How the toggle applies:** a player's off-toggle adds a layer inside `AbstractFeature.check()` (server enabled → base permission → personal toggle → world whitelist). When off, the feature stops firing for that player only; other players and the rest of the config are unaffected.

---

## Features

Detailed behavior, config keys, and limitations for each feature are documented in **[doc/features/](doc/features/README.md)** — one document per feature:

- [Double Jump](doc/features/double-jump.md) — mid-air double jump
- [Durability](doc/features/durability.md) — durability multiplier on whitelisted items
- [Auto Crop](doc/features/auto-crop.md) — batch crop harvesting
- [Back](doc/features/back.md) — death teleportation
- [Tree Feller](doc/features/tree-feller.md) — whole-tree felling
- [Fall Damage](doc/features/fall-damage.md) — fall-damage immunity
- [XP](doc/features/xp.md) — bonus XP from actions

---

## Storage

`StorageManager` provides two storage layers.

### Runtime (in-memory)

- Backed by nested map: `Map<featureId, Map<key, Map<uuid, Long>>>`.
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
- Used for **Back** death locations (JSON payloads), persistent cooldowns, and per-player `/leet` feature toggles (`feature_id` = feature, `key` = `user-toggle`, `value` = `true`/`false`; absent = enabled).
- Uses Bukkit's bundled SQLite JDBC (`jdbc:sqlite:...`). No external driver needed.

> **Backups:** `data.db` is written by the plugin. Backing it up preserves saved death locations and player toggle preferences. Deleting it clears all of that state.

---

## Vault / Economy Integration

Vault is an **optional soft dependency** (`softdepend: [Vault]`). The plugin detects it at startup and resolves the Vault `Economy` provider; it works entirely without Vault.

| Area | Without Vault | With Vault (economy provider) |
|---|---|---|
| Economy (`/back` cost) | Cost is silently skipped — free teleports, no balance checks | Cost checked and deducted per `/back` use |
| Permissions | Uses Bukkit `player.hasPermission()` | Stills uses Bukkit `player.hasPermission()` (the Vault `Permission` provider is resolved but **not used**) |

Notes:
- Only the **Back** feature uses the economy.
- `cost` is charged only when `feature.cost > 0`.
- If the player lacks funds, the `insufficient-funds` message is shown and the teleport is blocked.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Plugin doesn't load on start | Server is not Paper 26.2+, or the JVM is older than Java 26. Check console for a version mismatch. |
| Feature config changes have no effect | Feature configs are read at startup; there is **no reload command**. Restart the server. |
| `/leeta` not recognized / "unknown command" | The `leeta` command permission (`leet.admin`) is `op` by default — grant it or run as op. |
| Durability whitelist warnings at startup | `Invalid material in durability whitelist:` — an entry in the on-disk `features/_durability.yml` whitelist is not a valid `Material` name (e.g. leftover `STEEL_*` or `HELMET`) and is being ignored. Remove it or use the correct enum name (see the note in [Durability](doc/features/durability.md)). |
| `/back` cost not charged | Vault is not installed, or no economy provider is registered. Without Vault the cost feature is silently disabled. |
| Death locations reset on restart | The `data.db` file was deleted/moved, or the SQLite connection failed to initialize (SEVERE log). |
| `data.db` not created | Check the startup logs for `Failed to initialize SQLite`. The plugin degrades gracefully (Back feature won't persist). |
| DoubleJump not triggering | Check game mode (Creative/Spectator excluded), `double_jump` cooldown (1s default), or the permission/world whitelist. |

---

## Known Limitations

- **No reload command** — config file changes require a restart. Only `/leeta toggle` can change `base.enabled` live.
- **`config-version` is informational only** — the merge adds missing keys regardless of the version value; it never removes or rewrites existing keys.
- **Vault permission provider is unused** — permission checks are Bukkit-native even with Vault installed.
- **No admin bypass** for Back cooldown/cost/max-age.
- **No bStats** — sends zero analytics/metrics telemetry.
- **Auto Crop scan is server-thread** — large radii can be expensive on busy worlds.
- **No unit tests** — verification is manual on a Paper server.

---

## License

Licensed under **CC0 1.0 (Creative Commons — Public Domain)**. See [LICENSE](LICENSE).