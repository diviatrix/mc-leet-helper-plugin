# Permissions

Permissions come from four sources across the five plugins:

1. **Static admin permissions** (`leet.admin.*`) — declared in **LeetCore's** `plugin.yml`, gate `/leeta`. Documented in [Admin.md](Admin.md).
2. **Runtime feature permissions** (`leet.feat.<id>`) — registered at startup by the owning plugin for each gated feature.
3. **Static gameplay permissions** — command-specific nodes declared in LeetCore's `plugin.yml`.
4. **No permission at all** — Crafting is server-wide when `base.enabled` is true.

Each gameplay feature's `leet.feat.*` permission is documented in its own feature document (index: [features/](features/)).

## Static admin permissions (LeetCore)

Declared in `leet-core/src/main/resources/plugin.yml`. Documented in [Admin.md](Admin.md).

## Runtime feature permissions (dynamic)

Feature permissions are **not** declared in any `plugin.yml` (unlike the admin `/leeta` permissions). Instead, the owning plugin's feature registers them at runtime on every startup via `Bukkit.getPluginManager().addPermission()`, using each feature's `base.permission` node and `base.default-permission`. Every gated feature permission follows the pattern `leet.feat.<id>` (e.g. `leet.feat.double_jump`), and all fall back to `false`.

- Each owning feature page defines its own permission nodes, defaults and command gates. Use [the feature index](features/README.md) to find that page.

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
| Vanity | Vanity | `leet.feat.vanity` | `false` |
| Interaction | LeetInteraction | `leet.feat.interaction` | `false` |
| Sign create (one node per sign type) | LeetInteraction | `leet.interaction.sign.create.<type>` — e.g. `leet.interaction.sign.create.sell`, `leet.interaction.sign.create.warp` | `op` |
| Sign use (one node per sign type) | LeetInteraction | `leet.interaction.sign.use.<type>` — e.g. `leet.interaction.sign.use.disposal`, `leet.interaction.sign.use.quest` | `true` |
| Home teleport | LeetCore | `leet.home` | `true` |
| Set home | LeetCore | `leet.sethome` | `true` |
