# LeetHelper

A **Paper 26.2** plugin providing modular gameplay features. Each feature has its own on-disk YAML config, its own permission node, per-world whitelisting, optional cooldowns, and an optional per-use Vault economy cost.

Licensed under **CC0 1.0** (public domain) — see [LICENSE](LICENSE).

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Permissions](#permissions)
  - [Admin Permissions](#admin-permissions-leeta)
  - [Feature Permissions](#feature-permissions-dynamic)
- [Commands](#commands)
  - [/leeta](#leeta)
  - [/back](#back)
- [Feature docs (doc/features/)](doc/features/)
- [Development](#development)
  - [Building & Testing](doc/BUILDING.md)
  - [Architecture](doc/ARCHITECTURE.md)
- [Troubleshooting (Admin)](doc/Admin.md#troubleshooting)
- [Known Limitations (Admin)](doc/Admin.md#known-limitations)
- [License](#license)

---

## Overview

LeetHelper registers seven gameplay features plus one admin command.

| Feature | ID | Description |
|---|---|---|
| [Double Jump](doc/features/double-jump.md) | `double_jump` | Mid-air double jump with configurable velocity and cooldown |
| [Durability](doc/features/durability.md) | `durability` | Configurable durability multiplier for whitelisted tools/equipment |
| [Auto Crop](doc/features/auto-crop.md) | `auto_crop` | Auto-harvest nearby mature crops when breaking one |
| [Back](doc/features/back.md) | `back` | Teleport back to your death location, with optional cost and cooldown |
| [Tree Feller](doc/features/tree-feller.md) | `tree_feller` | Felling a log drops the whole connected tree |
| [Fall Damage](doc/features/fall-damage.md) | `fall_damage` | Negates all fall damage for eligible players |
| [XP](doc/features/xp.md) | `xp` | Bonus vanilla XP for mining, woodcutting, crops, fishing, building, and killing |

Admin features are managed with the `/leeta` command (`list`, `toggle`, `info`); full per-feature details (config keys, limitations, permissions) are in **[doc/features/](doc/features/)**.

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | Paper **26.2**+ (Bundled API jar is compiled against `26.2`). Spigot/CraftBukkit are **not** supported. |
| Java | **25+** — the JVM your server runs the plugin on. (Build/toolchain details, which also target Java 25, are in [Building from Source](doc/BUILDING.md).) |
| Vault | Optional. Only needed for feature per-use costs. The plugin works fully without it. |

---

## Installation

1. **Download the jar** from the [GitHub Releases](https://github.com/diviatrix/mc-leet-helper-plugin/releases) page (e.g. `leet-helper-1.1.2.jar`). Building from source is optional — see [Building from Source](doc/BUILDING.md).
2. **Copy the jar** into your server's `plugins/` folder:

   ```bash
   cp leet-helper-1.1.2.jar /path/to/server/plugins/
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

## Development

- **[Building & Testing](doc/BUILDING.md)** — prerequisites, Gradle commands, the output artifact, and how the version is single-sourced.
- **[Architecture](doc/ARCHITECTURE.md)** — project structure, the feature model, config handling, storage, and the permission/Vault internals.

## Permissions

Admin permissions for `/leeta` are in **[doc/Admin.md](doc/Admin.md)**. Each gameplay feature's `leet.feat.*` permission is documented in its own feature document (index: [doc/features/](doc/features/)).

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

Feature permissions are **not** declared in `plugin.yml`. Instead, `HelperPlugin` registers them at runtime on every startup via `Bukkit.getPluginManager().addPermission()`, using each feature's `base.permission` node and `base.default-permission`. Every feature permission follows the pattern `leet.feat.<id>` (e.g. `leet.feat.double_jump`), and all default to `false`. Each feature doc lists its exact node — see the **Permissions** section of each feature in the [feature list](#overview).

`base.default-permission` maps to a Bukkit default:
- `true` → `PermissionDefault.TRUE` (every player)
- `op` → `PermissionDefault.OP` (ops only)
- `false` → `PermissionDefault.FALSE` (nobody, the default)

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

## Storage & Economy

The storage layers (runtime in-memory and persistent SQLite `data.db`) and the optional Vault income integration are covered in the **[Architecture](doc/ARCHITECTURE.md)** doc.

---

## License

Licensed under **CC0 1.0 (Creative Commons — Public Domain)**. See [LICENSE](LICENSE).