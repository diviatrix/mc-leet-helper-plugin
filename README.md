# LeetHelper

A **Paper 26.2** plugin providing modular gameplay features. Each feature has its own on-disk YAML config, its own permission node, per-world whitelisting, optional cooldowns, and an optional per-use Vault economy cost.

Licensed under **CC0 1.0** (public domain) — see [LICENSE](LICENSE).

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Permissions](doc/permissions.md)
- [Commands](#commands)
  - [/leeta](#leeta)
  - [/back](#back)
  - [/leet](#leet)
- [Storage & Economy](#storage--economy)
- [Feature docs (doc/features/)](doc/features/)
- [Development](#development)
  - [Building & Testing](doc/BUILDING.md)
  - [Architecture](doc/ARCHITECTURE.md)
- [Troubleshooting (Admin)](doc/Admin.md#troubleshooting)
- [Known Limitations (Admin)](doc/Admin.md#known-limitations)
- [License](#license)

---

## Overview

LeetHelper registers eight gameplay features plus one admin command.

| Feature | ID | Description |
|---|---|---|
| [Double Jump](doc/features/double-jump.md) | `double_jump` | Mid-air double jump with configurable velocity and cooldown |
| [Durability](doc/features/durability.md) | `durability` | Configurable durability multiplier for whitelisted tools/equipment |
| [Auto Crop](doc/features/auto-crop.md) | `auto_crop` | Auto-harvest nearby mature crops when breaking one |
| [Back](doc/features/back.md) | `back` | Teleport back to your death location, with optional cost and cooldown |
| [Tree Feller](doc/features/tree-feller.md) | `tree_feller` | Felling a log drops the whole connected tree |
| [Fall Damage](doc/features/fall-damage.md) | `fall_damage` | Negates all fall damage for eligible players |
| [XP](doc/features/xp.md) | `xp` | Bonus vanilla XP for mining, woodcutting, crops, fishing, building, and killing |
| [Skills](doc/features/skills.md) | `skills` | A skill tree (Stamina + 8 passive skills) leveled up by spending XP points |

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

1. **Download the jar** from the [GitHub Releases](https://github.com/diviatrix/mc-leet-helper-plugin/releases) page (e.g. `leet-helper-1.1.3.jar`). Building from source is optional — see [Building from Source](doc/BUILDING.md).
2. **Copy the jar** into your server's `plugins/` folder:

   ```bash
   cp leet-helper-1.1.3.jar /path/to/server/plugins/
   ```

3. **Start the server.** On first launch the plugin creates its data folder and writes default configuration files:

   ```
   plugins/LeetHelper/
   ├── config.yml                 # Global settings (log level, schema version)
   ├── data.db                    # SQLite database (Back feature persistence)
   └── features/
       ├── double_jump.yml
       ├── durability.yml
       ├── auto_crop.yml
       ├── back.yml
       ├── tree_feller.yml
       ├── fall_damage.yml
       ├── xp.yml
       └── skills.yml
   ```

4. **Configure to taste** — edit the files inside `plugins/LeetHelper/features/`. Restart the server for changes to take effect (there is **no reload command**; `base.enabled` toggles are the only thing that can be changed live, via `/leeta toggle`).

> **Updating the plugin:** on startup the global `config.yml` and every feature config are merged against the bundled defaults. Any **new key** introduced by a newer plugin version (e.g. `require-hoe`) is automatically added to your existing configs while all your other values are preserved. No manual copying needed.

---

## Permissions

See **[doc/permissions.md](doc/permissions.md)** — the admin `/leeta` permissions (`leet.admin.*`) declared statically in `plugin.yml`, and the dynamic per-feature `leet.feat.*` permissions registered at runtime.

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

### /skills

Opens the skill-tree GUI (Stamina in the center; the eight passive skills unlock around it once Stamina reaches its max level). Leveling skills spends **vanilla XP points** (`player.getTotalExperience()`).

| Command | Permission | Description |
|---|---|---|
| `/skills` | `leet.feat.skills` | Open the skill tree (see [Feature: Skills](doc/features/skills.md)) |

In the tree, click a skill to see its description and a Level Up button; confirm to spend XP and advance a level. The GUI is player-only.

### /leet

Player-side feature toggles. Each player can turn supported features **off for themselves** (it's an off-switch — it never grants or revokes access). Persisted per-player in the SQLite `kv_store`, so preferences survive restarts.

| Subcommand | Permission | Description |
|---|---|---|
| `/leet` or `/leet list` | any `leet.feat.<id>` | Show your current ON/OFF status for each feature you have |
| `/leet dj` | `leet.feat.double_jump` | Toggle **Double Jump** on/off for yourself |
| `/leet crop` | `leet.feat.auto_crop` | Toggle **Auto Crop** on/off for yourself |
| `/leet tree` | `leet.feat.tree_feller` | Toggle **Tree Feller** on/off for yourself |
| `/leet fall` | `leet.feat.fall_damage` | Toggle **Fall Damage** on/off for yourself |
| `/leet xp` | `leet.feat.xp` | Toggle **XP** on/off for yourself |
| `/leet skills` | `leet.feat.skills` | Toggle **Skills** on/off for yourself |

**Permission model** — `/leet` is permission-gated by the underlying feature permissions:
- The command is only available to players who have at least **one** `leet.feat.<id>` permission. If a player has **none**, `/leet` reports `No permission.` and does nothing (including `list`, and no tab completion).
- Tab completion and the status list only show the features the player is actually permissioned for.
- Toggling a feature still checks that feature's permission (e.g. `leet.feat.double_jump`); without it, `/leet <sub>` is declined.
- Because these features default to `false`, `/leet` is **not** available out of the box — a player must be granted at least one feature permission first (see [Permissions](doc/permissions.md)). Grant e.g. `leet.feat.double_jump`, `leet.feat.auto_crop`, `leet.feat.tree_feller`, or `leet.feat.fall_damage` in your permission plugin to unlock the corresponding `/leet` subcommands.

**How the toggle applies:** a player's off-toggle adds a layer inside `AbstractFeature.check()` (server enabled → base permission → personal toggle → world whitelist). When off, the feature stops firing for that player only; other players and the rest of the config are unaffected.

---

## Storage & Economy

The storage layers (runtime in-memory and persistent SQLite `data.db`) and the optional Vault income integration are covered in the **[Architecture](doc/ARCHITECTURE.md)** doc.

---

## Development

- **[Building & Testing](doc/BUILDING.md)** — prerequisites, Gradle commands, the output artifact, and how the version is single-sourced.
- **[Architecture](doc/ARCHITECTURE.md)** — project structure, the feature model, config handling, storage, and the permission/Vault internals.


## License

Licensed under **CC0 1.0 (Creative Commons — Public Domain)**. See [LICENSE](LICENSE).
