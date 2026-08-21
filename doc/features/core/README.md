# LeetCore Features

LeetCore registers **seven** standalone features, each a player-gated mechanic with its own config file under `plugins/LeetCore/features/` and its own `leet.feat.<id>` permission.

| Feature | ID | Config file | Document |
|---|---|---|---|
| Double Jump | `double_jump` | `double_jump.yml` | [double-jump.md](double-jump.md) |
| Durability | `durability` | `durability.yml` | [durability.md](durability.md) |
| Auto Crop | `auto_crop` | `auto_crop.yml` | [auto-crop.md](auto-crop.md) |
| Back | `back` | `back.yml` | [back.md](back.md) |
| Tree Feller | `tree_feller` | `tree_feller.yml` | [tree-feller.md](tree-feller.md) |
| Fall Damage | `fall_damage` | `fall_damage.yml` | [fall-damage.md](fall-damage.md) |
| XP | `xp` | `xp.yml` | [xp.md](xp.md) |

LeetCore also provides the shared infrastructure the other plugins build on: the feature registry, storage, generic GUI, Vault integration, and the `/leeta`, `/back`, and `/leet` commands. See [ARCHITECTURE.md](../../ARCHITECTURE.md).
