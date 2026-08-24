# Feature Documentation

The pages below are the canonical feature documents. Each page contains the feature's behavior, permissions, commands, configuration and setup procedures. All feature documentation lives inside the owning plugin folder. There are no separate per-plugin README, command or how-to files.

Every gated feature shares the `base:`/`messages:` config layout, control model, and `leet.feat.<id>` permission lifecycle. See [common feature configuration](../ARCHITECTURE.md#common-feature-config-layout) and [feature permissions](../ARCHITECTURE.md#feature-permissions).

## LeetCore

| Feature | ID | Permission | Config | Document |
|---|---|---|---|---|
| Double Jump | `double_jump` | `leet.feat.double_jump` | `LeetCore/features/double_jump.yml` | [Double Jump](core/double-jump.md) |
| Durability | `durability` | `leet.feat.durability` | `LeetCore/features/durability.yml` | [Durability](core/durability.md) |
| Auto Crop | `auto_crop` | `leet.feat.auto_crop` | `LeetCore/features/auto_crop.yml` | [Auto Crop](core/auto-crop.md) |
| Back | `back` | `leet.feat.back` | `LeetCore/features/back.yml` | [Back](core/back.md) |
| Tree Feller | `tree_feller` | `leet.feat.tree_feller` | `LeetCore/features/tree_feller.yml` | [Tree Feller](core/tree-feller.md) |
| Fall Damage | `fall_damage` | `leet.feat.fall_damage` | `LeetCore/features/fall_damage.yml` | [Fall Damage](core/fall-damage.md) |
| XP | `xp` | `leet.feat.xp` | `LeetCore/features/xp.yml` | [XP](core/xp.md) |
| Teleport | `teleport` | command-specific nodes | `LeetCore/config.yml` | [Teleport](core/teleport.md) |
| Economy | `economy` | command-specific nodes | `LeetCore/data.db` | [Economy](core/economy.md) |

LeetCore also owns shared storage, the feature registry, generic GUI, Vault integration, and `/leeta`, `/back`, `/home`, `/sethome`, and `/leet`.

## LeetSkills

| Feature | ID | Permission | Config | Document |
|---|---|---|---|---|
| Skills | `skills` | `leet.feat.skills` | `LeetSkills/features/skills.yml` and `skill-tree.yml` | [Skills](skills/skills.md) |

Skills opens with `/skills` and persists player levels in `LeetSkills/data.db`.

## LeetCrafting

| Feature | ID | Permission | Config | Document |
|---|---|---|---|---|
| Crafting | `crafting` | None; server-wide | `LeetCrafting/features/crafting.yml` | [Crafting](crafting/crafting.md) |

LeetCrafting also owns the custom item resource pack. See the [Crafting](crafting/crafting.md) feature document.

## LeetVanity

| Feature | ID | Permission | Config | Document |
|---|---|---|---|---|
| Vanity | `vanity` | `leet.feat.vanity` plus capability permissions | `LeetVanity/features/vanity.yml` | [Vanity](vanity/vanity.md) |

Vanity includes connected doors, sitting, and `/dance`.

## LeetInteraction

| Feature | ID | Permission | Config | Document |
|---|---|---|---|---|
| Interaction | `interaction` | `leet.feat.interaction` plus sign permissions | `LeetInteraction/features/interaction.yml` and `definitions/*.yml` | [Interaction](interaction/interaction.md) |

Interaction includes signs, definitions, NPCs, bound blocks, chests, quests and reputation.
