# LeetHelper

LeetHelper is a set of five cooperating Paper plugins for Minecraft servers. It adds configurable player abilities, skills, custom food, vanity mechanics, economy and teleport commands, interactive signs, NPCs, bound blocks, chests and quests.

This README is the starting point. Use the canonical feature pages for exact commands, configuration and setup procedures.

## Documentation

| Need | Document |
|---|---|
| Install the plugins | [Installation and upgrade guide](doc/INSTALLATION.md) |
| Find administration commands | [Admin guide](doc/Admin.md) |
| Grant access | [Permissions reference](doc/permissions.md) |
| Understand plugin ownership and storage | [Architecture](doc/ARCHITECTURE.md) |
| Build from source | [Building](doc/BUILDING.md) |
| Configure the crafting resource pack | [Crafting feature](doc/features/crafting/crafting.md) |
| Browse feature details | [Feature index](doc/features/README.md) |

## What Is Included

All five jars are designed to be installed together.

| Plugin | Required dependency | Main contents | Data/config folder |
|---|---|---|---|
| `LeetCore` | None | Shared API, SQLite storage, Vault economy, commands, seven core features | `plugins/LeetCore/` |
| `LeetSkills` | `LeetCore` | XP skill tree and `/skills` | `plugins/LeetSkills/` |
| `LeetCrafting` | `LeetCore` | Custom food, recipes and resource pack | `plugins/LeetCrafting/` |
| `LeetVanity` | `LeetCore` | Connected doors, sitting and `/dance` | `plugins/LeetVanity/` |
| `LeetInteraction` | `LeetCore` | Signs, definitions, NPCs, bound blocks, chests and quests | `plugins/LeetInteraction/` |

`Vault` is optional for non-economic features. It is required for balances, payments, paid signs and feature costs. When Vault is present, LeetCore supplies a low-priority SQLite-backed economy if no higher-priority economy provider is installed.

## Requirements

| Component | Requirement |
|---|---|
| Server | Paper `26.2` or newer |
| Java | Java `25` or newer |
| Plugins | All five LeetHelper jars, matching versions |
| Optional economy | Vault plus an economy provider, unless the built-in LeetCore provider is sufficient |

Spigot and CraftBukkit are not supported. The dependent plugins disable themselves when LeetCore is unavailable.

## First Installation

1. Stop the server.
2. Copy all five jars into the server `plugins/` directory.
3. Optionally install Vault and an economy plugin.
4. Start the server once and wait for all plugins to enable.
5. Grant permissions using [permissions.md](doc/permissions.md).
6. Edit the generated files under each plugin's data folder.
7. Restart the server, or use the supported `/leeta reload <group>` command where documented.
8. Set the server spawn with `/setspawn`; create warps with `/leeta warp add <name>`.

The first start creates configuration and database files. Existing configuration values are preserved while missing bundled keys are merged on startup. Do not copy the entire default file over an existing configuration unless you intend to discard local changes.

## Command Families

| Family | Commands | Owner |
|---|---|---|
| Administration | `/leeta ...` | LeetCore |
| Teleport | `/spawn`, `/setspawn`, `/home`, `/sethome`, `/warp` | LeetCore |
| Economy | `/bal`, `/pay`, `/leeta eco ...` | LeetCore and Vault |
| Personal feature switches | `/leet ...` | LeetCore |
| Death return | `/back` | LeetCore |
| Skills | `/skills` | LeetSkills |
| Vanity | `/dance` | LeetVanity |
| Interaction administration | `/leeta bind`, `/leeta unbind`, `/leeta bindings` | LeetInteraction through LeetCore |

The complete syntax and permissions are in the owning plugin's feature page under [doc/features/](doc/features/README.md).

## Feature Summary

| Plugin | Feature | ID | Default permission |
|---|---|---|---|
| LeetCore | Double Jump | `double_jump` | `false` |
| LeetCore | Durability | `durability` | `false` |
| LeetCore | Auto Crop | `auto_crop` | `false` |
| LeetCore | Back | `back` | `false` |
| LeetCore | Tree Feller | `tree_feller` | `false` |
| LeetCore | Fall Damage | `fall_damage` | `false` |
| LeetCore | XP | `xp` | `false` |
| LeetSkills | Skills | `skills` | `false` |
| LeetCrafting | Crafting | `crafting` | server-wide when enabled |
| LeetVanity | Vanity hub | `vanity` | `false` |
| LeetInteraction | Interaction | `interaction` | `false` |

Feature permissions are separate from command permissions. A player may have `/spawn` without having Double Jump. Use `/leeta list`, `/leeta info <id>` and `/leet list` to inspect state.

Feature configuration, reload instructions and reset procedures are documented in the owning feature page. `/leeta toggle` changes only a feature's `base.enabled` value.

## Storage

| File | Contents |
|---|---|
| `plugins/LeetCore/data.db` | Home locations, `/leet` toggles, death locations, cooldowns and built-in economy data |
| `plugins/LeetSkills/data.db` | Player skill levels and skill toggles |
| `plugins/LeetInteraction/data.db` | Bindings, quest state, reputation and interaction cooldowns |
| `plugins/LeetCore/config.yml` | Spawn and admin warp locations plus global settings |

Back up databases before upgrades or migrations. Deleting a database deletes the player data stored in it.

## Development

```text
./gradlew build
```

Use [BUILDING.md](doc/BUILDING.md) for module builds and [ARCHITECTURE.md](doc/ARCHITECTURE.md) before changing cross-plugin APIs or persistence.

## License

CC0 1.0. See [LICENSE](LICENSE).
