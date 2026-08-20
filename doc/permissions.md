# Permissions

Permissions come from **three sources** across the three plugins:

1. **Static admin permissions** (`leet.admin.*`) — declared in **LeetCore's** `plugin.yml`, gate `/leeta`. Documented in [Admin.md](Admin.md).
2. **Runtime feature permissions** (`leet.feat.<id>`) — registered at startup by the owning plugin for each gated feature.
3. **No permission at all** — Crafting is server-wide (open to all when `base.enabled` is true) and Skills gates via a single runtime node.

Each gameplay feature's `leet.feat.*` permission is documented in its own feature document (index: [features/](features/)).

## Static admin permissions (LeetCore)

Declared in `leet-core/src/main/resources/plugin.yml`. Documented in [Admin.md](Admin.md).

## Runtime feature permissions (dynamic)

Feature permissions are **not** declared in any `plugin.yml` (unlike the admin `/leeta` permissions). Instead, the owning plugin's feature registers them at runtime on every startup via `Bukkit.getPluginManager().addPermission()`, using each feature's `base.permission` node and `base.default-permission`. Every gated feature permission follows the pattern `leet.feat.<id>` (e.g. `leet.feat.double_jump`), and all fall back to `false`.

- **LeetCore** registers nodes for its seven standalone features (`double_jump`, `durability`, `auto_crop`, `back`, `tree_feller`, `fall_damage`, `xp`) — one per feature, plus the static `leet.feat.back` command permission, from the config's `base.permission`.
- **LeetSkills** registers a single `leet.feat.skills` node (default `false`). `/skills` has **no static command permission** — access is gated entirely by this same node at runtime (`SkillsCommand` checks `skillsFeature.appliesTo(player)`).
- **LeetCrafting** registers **no** feature permission — Crafting declares no `base.permission` key, and `registerPermission()` skips a node when the config lacks the key. It is server-wide.

`base.default-permission` maps to a Bukkit default:
- `true` → `PermissionDefault.TRUE` (every player)
- `op` → `PermissionDefault.OP` (ops only)
- `false` → `PermissionDefault.FALSE` (nobody, the default)

**How permission checks happen:** checks use Bukkit's `player.hasPermission(permission)` everywhere. Feature permissions are moderately standard Bukkit permission nodes, so they integrate with LuckPerms, PEX, GroupManager, etc. Even with Vault installed, the plugin does **not** route permission lookups through Vault's `Permission` provider — the Vault permission provider is resolved at startup but currently unused.

> **Restart required for permission changes:** because feature permissions are registered once at startup, editing `base.permission` or `base.default-permission` requires a server restart (or replugin) to take effect.

## Per-feature permission summary

| Feature | Plugin | Node | Default |
|---|---|---|---|
| Double Jump | LeetCore | `leet.feat.double_jump` | `false` |
| Durability | LeetCore | `leet.feat.durability` | `false` |
| Auto Crop | LeetCore | `leet.feat.auto_crop` | `false` |
| Back | LeetCore | `leet.feat.back` | `false` |
| Tree Feller | LeetCore | `leet.feat.tree_feller` | `false` |
| Fall Damage | LeetCore | `leet.feat.fall_damage` | `false` |
| XP | LeetCore | `leet.feat.xp` | `false` |
| Skills | LeetSkills | `leet.feat.skills` | `false` |
| Crafting | LeetCrafting | — (none) | server-wide |
