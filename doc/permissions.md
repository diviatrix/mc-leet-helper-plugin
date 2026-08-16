# Permissions

Admin permissions for `/leeta` (`leet.admin.*`) are declared statically in `plugin.yml` and documented in [Admin.md](Admin.md). Each gameplay feature's `leet.feat.*` permission is documented in its own feature document (index: [features/](features/)).

## Feature Permissions (dynamic)

Feature permissions are **not** declared in `plugin.yml` (unlike the admin `/leeta` permissions, which are declared statically — see [Admin.md](Admin.md)). Instead, `Core` registers them at runtime on every startup via `Bukkit.getPluginManager().addPermission()`, using each feature's `base.permission` node and `base.default-permission`. Every feature permission follows the pattern `leet.feat.<id>` (e.g. `leet.feat.double_jump`), and all default to `false`.

`base.default-permission` maps to a Bukkit default:
- `true` → `PermissionDefault.TRUE` (every player)
- `op` → `PermissionDefault.OP` (ops only)
- `false` → `PermissionDefault.FALSE` (nobody, the default)

**How permission checks happen:** checks use Bukkit's `player.hasPermission(permission)` everywhere. Feature permissions are moderately standard Bukkit permission nodes, so they integrate with LuckPerms, PEX, GroupManager, etc. Even with Vault installed, the plugin does **not** route permission lookups through Vault's `Permission` provider — the Vault permission provider is resolved at startup but currently unused.

> **Restart required for permission changes:** because feature permissions are registered once at startup, editing `base.permission` or `base.default-permission` requires a server restart (or replugin) to take effect.