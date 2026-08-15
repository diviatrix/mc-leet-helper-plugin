# LeetHelper — Admin

The `/leeta` admin command and the `leet.admin.*` permission nodes that gate it.

> Feature-facing permissions (`leet.feat.*`) are documented per feature — see the
> [feature docs index](features/README.md).

---

## /leeta

The admin command for managing features. Entry point for `list`, `toggle`, and `info`
subcommands, with tab completion for subcommands and feature IDs.

| Subcommand | Permission | Description |
|---|---|---|
| `/leeta` | (base command) | Prints usage: `/leeta <list\|toggle\|info>` |
| `/leeta list` | `leet.admin` | Lists all features with ON/OFF status |
| `/leeta toggle <id>` | `leet.admin.toggle` | Toggles a feature on/off and **persists** `base.enabled` to its YAML |
| `/leeta info <id>` | `leet.admin` | Shows the feature's ID, permission node, and current status |

**On toggle:** `FeatureManager.toggle()` disables the feature (unregisters listeners),
re-enables it if it was off, and writes the new state back to `base.enabled` in the
feature's YAML — so the toggle survives a restart. A toggle does **not** reload the rest
of the config; config-file edits still need a restart.

---

## Permissions

Declared statically in `plugin.yml` (Bukkit-native, `op` by default). They control the
`/leeta` management command. `leet.admin` automatically inherits its three children
(`list`, `toggle`, `info`) via its `children` block, so granting `leet.admin` alone is
enough for an operator.

| Permission | Default | Description |
|---|---|---|
| `leet.admin` | op | Full admin access to `/leeta`. Automatically inherits `leet.admin.list`, `leet.admin.toggle`, and `leet.admin.info` via its `children` block. |
| `leet.admin.list` | op | Use `/leeta list`. |
| `leet.admin.toggle` | op | Use `/leeta toggle <id>`. |
| `leet.admin.info` | op | Use `/leeta info <id>`. |

- These are **Bukkit native** permissions — they integrate with your permission plugin
  (LuckPerms, PEX, GroupManager, ...) and are never bypassed by the plugin itself.
- The `/leeta` command itself is registered with `permission: leet.admin`, so non-ops
  without `leet.admin` never reach the executor.
- Because these are static `plugin.yml` declarations, editing them requires a server
  restart to take effect.