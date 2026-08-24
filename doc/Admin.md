# LeetHelper Administration

This document describes the administration model only. Feature-specific commands, permissions, configuration, setup and troubleshooting belong to the canonical feature pages under [doc/features/](features/README.md).

## Administration Model

LeetCore owns the `/leeta` command and the shared feature registry. The other plugins register their features and contribute their own administration subcommands through LeetCore.

| Plugin | Administration surface |
|---|---|
| LeetCore | `/leeta` feature registry and shared administration |
| LeetSkills | Skill feature registered in the shared registry |
| LeetCrafting | Crafting feature and custom-item registry |
| LeetVanity | Vanity feature registered in the shared registry |
| LeetInteraction | Interaction feature plus binding subcommands |

Use [features/README.md](features/README.md) to select the owning plugin and then follow that feature's page. That page is the source of truth for what to grant, edit, run and test.

## Admin Permission

The base administrator permission is `leet.admin`, declared statically by LeetCore and defaulting to operators. Its static child nodes are:

| Permission | Default | Purpose |
|---|---|---|
| `leet.admin` | `op` | Access to the `/leeta` command and administrator operations |
| `leet.admin.list` | `op` | List registered features |
| `leet.admin.toggle` | `op` | Toggle a registered feature's server-level enabled state |
| `leet.admin.info` | `op` | Inspect a registered feature |
| `leet.admin.reload` | `op` | Use reload operations contributed by plugins |

`leet.admin` inherits the listed child permissions through `plugin.yml`. Permission changes to static command declarations require a server restart.

## `/leeta` Approach

`/leeta` is a router for registered administration operations. Its exact subcommands depend on the plugins that are currently enabled.

| Operation | General purpose | Owner documentation |
|---|---|---|
| `list` | Inspect registered feature IDs and server state | [Feature index](features/README.md) |
| `info <feature-id>` | Inspect one feature's ID, permission and state | The selected feature page |
| `toggle <feature-id>` | Change a feature's server-level `base.enabled` value | The selected feature page |
| `reload <group>` | Reload a plugin-owned group when supported | The owning plugin's feature page |
| Plugin-contributed subcommands | Perform plugin-owned administration | The owning plugin's feature page |

`/leeta toggle` changes only the server-level enable switch. It does not grant permissions, change player toggles, or reload unrelated configuration. Feature pages document whether a particular feature supports live toggling or requires a restart after edits.

## Operational Rules

1. Install all five LeetHelper jars from the same release.
2. Ensure LeetCore enables before dependent plugins.
3. Grant `leet.admin` to trusted administrators.
4. Select the owning feature page before changing permissions or configuration.
5. Back up YAML files and SQLite databases before upgrades or resets.
6. Never delete a database as a YAML configuration reset; databases contain player data.
7. Test changes in game with the exact player permission and world where the feature will run.

## Storage Ownership

| Database | Owner | Player or feature data |
|---|---|---|
| `plugins/LeetCore/data.db` | LeetCore | Core feature state, homes and built-in economy data |
| `plugins/LeetSkills/data.db` | LeetSkills | Skill levels and skill toggles |
| `plugins/LeetInteraction/data.db` | LeetInteraction | Bindings, quest state, reputation and interaction state |

Deleting one of these files deletes the data owned by that plugin. See the relevant feature page before resetting anything.

## Troubleshooting Approach

1. Confirm Paper `26.2+` and Java `25+`.
2. Confirm all five jars are installed and enabled.
3. Check the owning plugin's startup log.
4. Run `/leeta list` and `/leeta info <feature-id>`.
5. Check the feature page for its permission, world list, personal toggle and configuration path.
6. Verify Vault only when the feature uses money.
7. Back up and regenerate only the affected YAML file when a default reset is explicitly required.

Feature-specific diagnostics are intentionally kept with the feature so this document does not become a second, conflicting reference.
