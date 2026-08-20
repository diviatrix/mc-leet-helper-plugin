# LeetHelper — Admin

The `/leeta` admin command and the `leet.admin.*` permission nodes that gate it.

`/leeta` is provided by **LeetCore**, but because it browses LeetCore's **shared
feature registry**, it manages features from **all three** plugins (LeetCore's seven
standalone features, LeetSkills' `skills`, LeetCrafting's `crafting`/`cooking`).

> Feature-facing permissions (`leet.feat.*`) are documented per feature — see the
> [feature docs index](features/).

---

## /leeta

The admin command for managing features and giving custom items. Entry point for
`list`, `toggle`, `info`, and `give` subcommands, with tab completion for
subcommands, feature IDs, and item IDs. It lists and toggles features from every
plugin that registers into LeetCore's shared registry, and its `/leeta give` hands
out any custom item in the item registry (owned by LeetCrafting).

| Subcommand | Permission | Description |
|---|---|---|
| `/leeta` | (base command) | Prints usage: `/leeta <list\|toggle\|info\|give>` |
| `/leeta list` | `leet.admin` | Lists all features with ON/OFF status |
| `/leeta toggle <id>` | `leet.admin.toggle` | Toggles a feature on/off and **persists** `base.enabled` to its YAML |
| `/leeta info <id>` | `leet.admin` | Shows the feature's ID, permission node, and current status |
| `/leeta give <item-id> [amount] [player]` | `leet.admin` | Gives a **custom item** (built from the shared custom-item registry, so it carries the correct `ci` tag + `leet:item/<id>` texture). Defaults to the sender if a player, stacked to 64 |

**On toggle:** `FeatureManager.toggle()` disables the feature (unregisters listeners),
re-enables it if it was off, and writes the new state back to `base.enabled` in the
feature's YAML — so the toggle survives a restart. A toggle does **not** reload the rest
of the config; config-file edits still need a restart.

**On give:** `/leeta give` hands out any registered custom item (e.g. `salt`, `dough`,
`croissant`). This is the supported way to obtain custom items for testing — a plain
vanilla `/give` will not produce them, because they only exist as tagged custom items
inside the item registry (which LeetCrafting builds and registers with LeetCore).

---

## Permissions

Declared statically in **LeetCore's** `plugin.yml` (Bukkit-native, `op` by default). They control the
`/leeta` management command. `leet.admin` automatically inherits its three children
(`list`, `toggle`, `info`) via its `children` block, so granting `leet.admin` alone is
enough for an operator.

| Permission | Default | Description |
|---|---|---|
| `leet.admin` | op | Full admin access to `/leeta`. Automatically inherits `leet.admin.list`, `leet.admin.toggle`, and `leet.admin.info` via its `children` block. Also unlocks `/leeta give`. |
| `leet.admin.list` | op | Use `/leeta list`. |
| `leet.admin.toggle` | op | Use `/leeta toggle <id>`. |
| `leet.admin.info` | op | Use `/leeta info <id>`. |

- These are **Bukkit native** permissions — they integrate with your permission plugin
  (LuckPerms, PEX, GroupManager, ...) and are never bypassed by the plugin itself.
- The `/leeta` command itself is registered with `permission: leet.admin`, so non-ops
  without `leet.admin` never reach the executor.
- Because these are static `plugin.yml` declarations, editing them requires a server
  restart to take effect.

---

## Configuration

The **LeetCore** global `config.yml` lives in its data folder (`plugins/LeetCore/config.yml`):

```yaml
config-version: 1
log-level: INFO
```

> **LeetCrafting** has its own `config.yml` too (`plugins/LeetCrafting/config.yml`) with the
> same `config-version`/`log-level` plus the `resource-pack.*` distribution settings (see the
> [Cooking](features/cooking.md#dish-icons--resource-pack) feature doc). **LeetSkills** has no
> global `config.yml` — its configuration lives entirely in `features/skills.yml` and
> `features/skill-tree.yml`.

| Key | Type | Description |
|---|---|---|
| `config-version` | integer | Schema version of `config.yml`. On startup, any keys missing from the on-disk file are auto-added from the bundled default while preserving existing values (see [Automatic config merging](ARCHITECTURE.md#automatic-config-merging-backfill)). |
| `log-level` | `OFF`, `INFO`, `DEBUG` | Logging verbosity. See [Logging](#logging). |

---

## Logging

The `log-level` key (see [Configuration](#configuration)) selects a verbosity level:

| Level | What is logged |
|---|---|
| `OFF` | Only critical errors (SEVERE), e.g. storage failures, feature-enable exceptions |
| `INFO` | Startup, no-Vault notice, feature enable failures, invalid-whitelist warnings, config errors |
| `DEBUG` | Reserved for fine-grained diagnostics; currently no extra DEBUG output is emitted beyond INFO |

The `log-level` is read from `config.yml`, though most feature-related messages are logged at the `INFO`/`WARNING`/`SEVERE` level regardless.

> **Console prefix & color:** startup and status messages (e.g. `[LeetCore] Initializing LeetCore v<version>`, `[LeetCore] Enabled 7/7 core feature(s).`, the Vault status) are sent to the console via the console sender with a green `[LeetCore]` prefix. These colored lines appear in the live console but color codes are stripped from `logs/latest.log`. The automatically-printed Paper line `[LeetCore] Enabling LeetCore v<version>` and the plugin-logger `[LeetCore]` WARN/SEVERE lines come from Paper's logger and are not recolored. LeetSkills and LeetCrafting log under their own plugin names (`[LeetSkills]`, `[LeetCrafting]`).

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Plugin(s) don't load on start | Server is not Paper 26.2+, or the JVM is older than Java 25. Check console for a version mismatch. |
| Skills / Crafting don't load | **LeetCore is missing.** LeetSkills and LeetCrafting `softdepend` on it and disable themselves if it isn't loaded. Deploy all three jars together (LeetCore first). |
| Feature config changes have no effect | Feature configs are read at startup; there is **no reload command**. Restart the server. |
| `/leeta` not recognized / "unknown command" | The `leeta` command permission (`leet.admin`) is `op` by default — grant it or run as op. |
| Durability whitelist warnings at startup | `Invalid material in durability whitelist:` — an entry in the on-disk `features/durability.yml` (in `plugins/LeetCore/features/`) whitelist is not a valid `Material` name (e.g. leftover `STEEL_*` or `HELMET`) and is being ignored. Remove it or use the correct enum name (see the note in [Durability](features/durability.md)). |
| Feature cost not charged | Vault is not installed, or no economy provider is registered. Without Vault the cost feature is silently disabled. |
| Death locations reset on restart | LeetCore's `plugins/LeetCore/data.db` file was deleted/moved, or the SQLite connection failed to initialize (SEVERE log). |
| Skill levels reset on restart | LeetSkills' `plugins/LeetSkills/data.db` file was deleted/moved, or the SQLite connection failed to initialize (SEVERE log). |
| `data.db` not created | Check the startup logs for `Failed to initialize SQLite`. The plugin degrades gracefully (Back feature won't persist). |
| DoubleJump not triggering | Check game mode (Creative/Spectator excluded), `double_jump` cooldown (1s default), or the permission/world whitelist. |

---

## Known Limitations

- **All three jars must be deployed together** — LeetSkills and LeetCrafting disable themselves without LeetCore; missing any jar removes its features.
- **No reload command** — config file changes require a restart. Only `/leeta toggle` can change `base.enabled` live.
- **`config-version` is informational only** — the merge adds missing keys regardless of the version value; it never removes or rewrites existing keys.
- **Vault permission provider is unused** — permission checks are Bukkit-native even with Vault installed.
- **No admin bypass** for feature cooldowns/costs (e.g. Back cooldown/cost/max-age).
- **No bStats** — sends zero analytics/metrics telemetry.
- **Auto Crop scan is server-thread** — large radii can be expensive on busy worlds.
- **No unit tests** — verification is manual on a Paper server.