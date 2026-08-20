# LeetHelper

A bundle of **Paper 26.2** plugins providing modular gameplay features, split into three cooperating jars: **LeetCore**, **LeetSkills**, and **LeetCrafting**. Each feature has its own on-disk YAML config, its own permission node, per-world whitelisting, optional cooldowns, and an optional per-use Vault economy cost.

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Resource pack (item icons)](doc/resource-pack.md)
- [Permissions](doc/permissions.md)
- [Commands](#commands)
  - [/leeta](#leeta)
  - [/back](#back)
  - [/leet](#leet)
  - [/skills](#skills)
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

LeetHelper is delivered as **three jars** that must be deployed together. Each plugin owns its own features and data folder; LeetSkills and LeetCrafting bind to LeetCore through a shared service API (see [Architecture](doc/ARCHITECTURE.md)).

| Plugin | Jar | Package | What it provides |
|---|---|---|---|
| **LeetCore** | `leet-core-<v>.jar` | `com.leet.core` | Shared infrastructure (storage, item registry, feature registry, GUI, Vault) + the **7 standalone features** + the cross-plugin commands (`/leeta`, `/back`, `/leet`) |
| **LeetSkills** | `leet-skills-<v>.jar` | `com.leet.skills` | The **Skills** feature — an XP-spent skill tree (`/skills`) |
| **LeetCrafting** | `leet-crafting-<v>.jar` | `com.leet.crafting` | The **Crafting** feature — custom foods and condiment items, plus the item resource pack |

### Features by plugin

LeetCore registers **seven** standalone features:

| Feature | ID | Description |
|---|---|---|
| [Double Jump](doc/features/double-jump.md) | `double_jump` | Mid-air double jump with configurable velocity and cooldown |
| [Durability](doc/features/durability.md) | `durability` | Configurable durability multiplier for whitelisted tools/equipment |
| [Auto Crop](doc/features/auto-crop.md) | `auto_crop` | Auto-harvest nearby mature crops when breaking one |
| [Back](doc/features/back.md) | `back` | Teleport back to your death location, with optional cost and cooldown |
| [Tree Feller](doc/features/tree-feller.md) | `tree_feller` | Felling a log drops the whole connected tree |
| [Fall Damage](doc/features/fall-damage.md) | `fall_damage` | Negates all fall damage for eligible players |
| [XP](doc/features/xp.md) | `xp` | Bonus vanilla XP for mining, woodcutting, crops, fishing, building, and killing |

LeetSkills registers the **Skills** feature: a skill tree (Traveler + 8 passive skills + advanced skills) leveled up by spending XP points.

LeetCrafting registers the **Crafting** feature: a single server-level domain that bundles custom food items (Salt and ~20 dishes) and their recipes into one feature.

Admin features are managed with the `/leeta` command (`list`, `toggle`, `info`, `give`); full per-feature details (config keys, limitations, permissions) are in **[doc/features/](doc/features/)**.

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | Paper **26.2**+ (Bundled API jar is compiled against `26.2`). Spigot/CraftBukkit are **not** supported. |
| All three jars | **LeetCore, LeetSkills, LeetCrafting** must be present together. LeetSkills and LeetCrafting soft-depend on LeetCore and disable themselves if it's absent. |
| Java | **25+** — the JVM your server runs the plugins on. (Build/toolchain details, which also target Java 25, are in [Building from Source](doc/BUILDING.md).) |
| Vault | Optional. Only needed for feature per-use costs. The plugins work fully without it. |

---

## Installation

1. **Download the three jars** (`.jar` for each of LeetCore, LeetSkills, LeetCrafting) from the [GitHub Releases](https://github.com/diviatrix/mc-leet-helper-plugin/releases).
  Building from source is optional — see [Building from Source](doc/BUILDING.md).
2. **Copy all three jars** into your server's `plugins/` folder.
3. **Start the server.** LeetCore loads first (it provides the shared services); LeetSkills and LeetCrafting then bind to it. On first launch each plugin creates its own data folder and writes default configuration files:

   ```
   plugins/LeetCore/
   ├── config.yml                 # Global settings (log level, schema version)
   ├── data.db                    # SQLite database (Back death locations, /leet toggles)
   └── features/
       ├── double_jump.yml
       ├── durability.yml
       ├── auto_crop.yml
       ├── back.yml
       ├── tree_feller.yml
       ├── fall_damage.yml
       └── xp.yml

   plugins/LeetSkills/
   ├── data.db                    # SQLite database (skill levels + per-player skill toggles)
   └── features/
       ├── skills.yml             # Skill definitions
       └── skill-tree.yml         # Tree topology (ring/advanced/slots + requires)

   plugins/LeetCrafting/
   ├── config.yml                 # Global settings + resource-pack.* distribution
   └── features/
       └── crafting.yml           # Custom food and condiment items, with all recipes
   ```

4. **Configure to taste** — edit the files inside each plugin's `Plugins/<Name>/features/` folder. Restart the server for changes to take effect (there is **no reload command**; `base.enabled` toggles are the only thing that can be changed live, via `/leeta toggle`).

5. **(Optional) Configure the item resource pack** — LeetCrafting ships a tiny additive `leet:` item-texture pack for the crafting items. By default it runs an embedded HTTP server on port 8043; for remote players (or behind FRPC/proxies) you'll want to set `resource-pack.url`. The full operational guide — including the `/craft-pack.zip` path-routing rule and the FRPC setup — is in **[Resource Pack Distribution](doc/resource-pack.md)**.

> **Updating:** on startup each plugin merges its global `config.yml` and every feature config against the bundled defaults. Any **new key** introduced by a newer version is automatically added to your existing configs while all your other values are preserved. No manual copying needed.
>
> **Skill data note:** skill levels/toggles live in `plugins/LeetSkills/data.db`. Upgrading from the pre-split single plugin (whose data was in `plugins/LeetHelper/data.db`) requires a **manual, one-off migration** to retain player skill progress. Run it **once from the server root** so it finds the default paths:
>
> ```bash
> python3 tools/migration/migrate_skills_1_4_1-1.5.0.py
> ```
>
> Only skill levels are copied; all other data is per-plugin and not carried over.

---

## Permissions

See **[doc/permissions.md](doc/permissions.md)** — the admin `/leeta` permissions (`leet.admin.*`) declared statically in LeetCore's `plugin.yml`, and the dynamic per-feature `leet.feat.*` permissions registered at runtime (a role the standalone features, Skills, and Crafting each opt into or out of).

---

## Commands

### /leeta

Admin command (provided by **LeetCore**) for managing features and giving custom items. Requires the `leeta` command permission (`leet.admin`, op by default). Full reference in [doc/Admin.md](doc/Admin.md).

| Subcommand | Permission | Description |
|---|---|---|
| `/leeta` | (base command) | Prints usage: `/leeta <list\|toggle\|info\|give>` |
| `/leeta list` | `leet.admin` | Lists all features with ON/OFF status |
| `/leeta toggle <id>` | `leet.admin.toggle` | Toggles a feature on/off and **persists** `base.enabled` to its YAML |
| `/leeta info <id>` | `leet.admin` | Shows the feature's ID, permission node, and current status |
| `/leeta give <item-id> [amount] [player]` | `leet.admin` | Gives a registered **custom item** (built from the shared custom-item registry, so it carries the correct `ci` tag + `leet:item/<id>` texture) |

Tab completion is provided for subcommands, feature IDs, and item IDs.

**On toggle:** `FeatureManager.toggle()` disables the feature (unregisters listeners), re-enables it if it was off, and writes the new state back to `base.enabled` in the feature's YAML file — so the toggle survives a restart. A toggle does **not** reload the rest of the config; config file edits still need a restart.

`/leeta` works across **all three plugins**: LeetSkills and LeetCrafting contribute their features into LeetCore's shared registry, so they appear in `/leeta list`, can be toggled by `/leeta toggle`, and their items are grabbable via `/leeta give`.

### /back

| Command | Permission | Description |
|---|---|---|
| `/back` | `leet.feat.back` | Teleports the player to their last death location (see [Feature: Back](doc/features/back.md)) |

This command is player-only (the console receives a "This command can only be used by players." message). On success/failure, feedback is delivered via the Back feature's `message-type`.

### /leet

Player-side feature control (provided by **LeetCore**). Each player can turn supported features **off for themselves** (an off-switch — it never grants or revokes access). Persisted per-player in LeetCore's SQLite `kv_store`, so preferences survive restarts. A server-level feature with no per-player toggle is **Crafting** — it is controlled at the server level by `base.enabled` and is not exposed through `/leet`.

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
- Because these toggle features default to `false`, their `/leet` subcommands need a granted permission — but **Crafting needs no permission**: it is controlled at the server level by `base.enabled` (see [Feature: Crafting](doc/features/crafting.md)).
- `/leet skills` is shown/toggled **only when** the skills feature is registered by its owning plugin (LeetSkills); `/leet` degrades gracefully if that plugin isn't loaded.

**How the toggle applies:** a player's off-toggle adds a layer inside `AbstractFeature.check()` (server enabled → base permission → personal toggle → world whitelist). When off, the feature stops firing for that player only; other players and the rest of the config are unaffected. Crafting is the exception — it bypasses the permission/toggle layers and enables for the whole server.

### /skills

Provided by **LeetSkills**. Opens the skill-tree GUI (Traveler in the center; the ring skills unlock once Traveler reaches max level, and advanced skills unlock around the tree once a ring skill hits the required level). Leveling skills spends **vanilla XP points** (`player.getTotalExperience()`).

| Command | Permission | Description |
|---|---|---|
| `/skills` | `leet.feat.skills` | Open the skill tree (see [Feature: Skills](doc/features/skills.md)) |

In the tree, click a skill to see its description and a Level Up button; confirm to spend XP and advance a level. The GUI is player-only. There is **no** static command permission for `/skills`; access is gate entirely by the runtime `leet.feat.skills` node (default-denied).

---

## Storage & Economy

- **LeetCore** — runtime (in-memory) + persistent SQLite `plugins/LeetCore/data.db`. Holds the `/leet` per-player toggles and the Back feature's death locations / persistent cooldowns. Resolves the optional Vault economy and passes it to features that declare a `cost`.
- **LeetSkills** — its own SQLite `plugins/LeetSkills/data.db`, holding per-player skill **levels** and skill toggles.
- **LeetCrafting** — no database; it owns only the item domain (item registry + recipes) and the served item resource pack.

See the **[Architecture](doc/ARCHITECTURE.md)** doc for the storage and Vault internals.

---

## Development

- **[Building & Testing](doc/BUILDING.md)** — prerequisites, Gradle commands, the three output jars, and how the version is single-sourced.
- **[Architecture](doc/ARCHITECTURE.md)** — the three-plugin project layout, the `CoreApi` service seam, the composable feature roles, config handling, storage split, and the permission/Vault internals.

## License

Licensed under **CC0 1.0 (Creative Commons — Public Domain)**. See [LICENSE](LICENSE).
