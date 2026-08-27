# Feature: Interaction

**Plugin:** LeetInteraction (`leet-interaction-<v>.jar`) · **Feature id:** `interaction` ·
**Permission:** `leet.feat.interaction` (default `false`) · **Sign permissions:** per type —
`leet.interaction.sign.create.<type>` (default `op`) and `leet.interaction.sign.use.<type>`
(default `true`); see the sign table below.

The canonical configuration is `plugins/LeetInteraction/features/interaction.yml`. Definition files are loaded from `plugins/LeetInteraction/definitions/*.yml`.

Bundled definitions copied on first start:

| File | Definition ID | Example |
|---|---|---|
| `warp_npc.yml` | `warp_spawn` | Teleport NPC with message and sound |
| `shopkeeper.yml` | `shopkeeper` | NPC that sells wheat for money |
| `quest_blacksmith.yml` | `quest_blacksmith` | Repeatable item-and-money quest |

Start with the setup sections in this document for NPCs, quests, signs, blocks and kits.

The interaction plugin is a **trigger → engine → action** system built on **LeetCore's shared
reactor** (see [Architecture](../../ARCHITECTURE.md#the-reactor)). A *definition* (a YAML file in
`plugins/LeetInteraction/definitions/`) describes gating conditions and an ordered action list. A
*trigger* (a sign, a bound NPC entity, or a bound block) fires the definition at a player, and
core's reactor runs conditions / cooldown / cost checks before executing the actions. Generic
actions and conditions live in core; this plugin contributes the domain ones (`kit`, `open-chest`,
`quest`, `reputation`).

---

## Bindings

| Surface | How to bind | Persistence |
|---|---|---|
| Sign | `[interact] <definition-id>` on lines 1–2 | Sign text |
| NPC | Spawn any vanilla entity, look at it, `/leeta bind <definition-id>` | Entity PDC (saves with the entity); the entity's default behavior (e.g. villager trades) is cancelled |
| Block | Look at any block (button, lever, plate, command block, ...), `/leeta bind <definition-id>` | `plugins/LeetInteraction/data.db`; fires on right-click and pressure-plate PHYSICAL |

`/leeta unbind` (looking at the same entity/block) clears a binding.
`/leeta bindings` lists every NPC/block/chest binding with its definition
(missing definitions are flagged).
`/leeta reload interact` reloads definitions and chest bindings from disk;
`/leeta reload core` reloads core's `rules/*.yml`.

### NPC setup

NPCs are ordinary Minecraft entities. LeetInteraction does not spawn them. Create an entity first, then bind a definition while looking at it:

```text
/leeta reload interact
/leeta bind warp_spawn
```

The entity must be within 6 blocks. The definition ID must be present in one of the loaded `definitions/*.yml` files. A bound entity stores its definition ID on the entity and its normal interaction is cancelled. For example, a bound villager will not open its normal trade window.

The bundled `warp_npc.yml` is a complete teleport example, and `shopkeeper.yml` is a complete economy action example.

### Quest NPC setup

A quest NPC needs a definition with both an action and a `quest:` section:

```yaml
id: iron_errand
actions:
  - type: quest
    quest: iron_errand
quest:
  name: "Iron Errand"
  description: "Bring 5 iron ingots."
  repeatable: true
  cooldown: 3600
  requirements:
    items:
      - { item: "material:IRON_INGOT", amount: 5 }
  rewards:
    items:
      - { item: "material:DIAMOND", amount: 1 }
```

First interaction accepts the quest. After the requirements are collected, the next interaction opens a confirmation GUI. Confirming consumes requirements and grants rewards. Player quest state and reputation are stored in `plugins/LeetInteraction/data.db`.

## Classic text signs

Every sign type has its own **create** and **use** permission (both registered at runtime,
restart required after config changes):

- **Create** — `leet.interaction.sign.create.<type>`, default `op`. Checked when the sign is
  written: a player without it produces an inert, non-colorized sign.
- **Use** — `leet.interaction.sign.use.<type>`, default `true`. Checked on right-click, on top
  of the `leet.feat.interaction` feature gate and world/toggle checks; denial sends the
  `sign-no-permission` message.

Out of the box every sign is usable by anyone who passes the feature gate. Restrict a sign
type by granting/denying its `.use` node (e.g. LuckPerms: deny `leet.interaction.sign.use.disposal`
to keep everyone out of disposal signs while leaving the other types open).

The `Lines` column below describes sign rows 2, 3, and 4 after the bracket tag. Row 4 is always the optional price row for signs that charge money.

| Sign | Lines | Behavior | Create permission | Use permission |
|---|---|---|---|---|
| `[Sell]` | amount · item · price | Removes `amount` of item from your inventory, pays `price` (Vault) | `leet.interaction.sign.create.sell` | `leet.interaction.sign.use.sell` |
| `[Buy]` | amount · item · price | Charges `price`, gives `amount` of item | `leet.interaction.sign.create.buy` | `leet.interaction.sign.use.buy` |
| `[Free]` | amount · item · price | Opens a 54-slot inventory filled with stacks of the configured item; row 2 defaults to a full stack and can override the amount per slot | `leet.interaction.sign.create.free` | `leet.interaction.sign.use.free` |
| `[Enchant]` | enchantment · level · price | Enchants your main-hand item (unsafe levels allowed) | `leet.interaction.sign.create.enchant` | `leet.interaction.sign.use.enchant` |
| `[Repair]` | — · `hand`/`all` · price | Repairs the main-hand item by default; line 3 accepts `hand` or `all` | `leet.interaction.sign.create.repair` | `leet.interaction.sign.use.repair` |
| `[Kit]` | kit name · — · price | Gives `feature.kits.<name>` (list of item specs + per-kit cooldown) | `leet.interaction.sign.create.kit` | `leet.interaction.sign.use.kit` |
| `[Warp]` | warp name · — · price | Teleports to `feature.warps.<name>` | `leet.interaction.sign.create.warp` | `leet.interaction.sign.use.warp` |
| `[Weather]` | `clear`, `rain`, or `thunder` · — · price | Changes the current world's weather. `[Weater]` is accepted as an alias. | `leet.interaction.sign.create.weather` | `leet.interaction.sign.use.weather` |
| `[Time]` | time value · — · price | Sets the current world's time (`day`, `noon`, `sunset`, `night`, `midnight`, `sunrise`, or ticks) | `leet.interaction.sign.create.time` | `leet.interaction.sign.use.time` |
| `[Heal]` | amount/`full` · — · price | Heals the player and clears fire ticks | `leet.interaction.sign.create.heal` | `leet.interaction.sign.use.heal` |
| `[Disposal]` | — · — · price | Opens a 54-slot trash inventory; items left inside vanish | `leet.interaction.sign.create.disposal` | `leet.interaction.sign.use.disposal` |
| `[Chest]` | `#id` · — · price | On top of a chest: binds the chest (single or double) to the id. Anywhere else: opens that chest's live inventory remotely | `leet.interaction.sign.create.chest` | `leet.interaction.sign.use.chest` |
| `[Quest]` | quest id · — · price | Accept / turn in the quest (see below) | `leet.interaction.sign.create.quest` | `leet.interaction.sign.use.quest` |
| `[interact]` | definition-id · — · price | Runs the full definition through the engine | `leet.interaction.sign.create.interact` | `leet.interaction.sign.use.interact` |

Example enchant sign:

```text
[Enchant]
sharpness
5
100
```

This enchants the item in the player's main hand with Sharpness V and charges 100. Leave row 4 blank for a free enchant. Enchantment names accept modern keys such as `sharpness` or `minecraft:sharpness`, plus common legacy Bukkit names such as `DAMAGE_ALL`.

Admins can flip the out-of-box defaults globally in `interaction.yml`
(`feature.signs.create-permission-default`, default `op`; `feature.signs.use-permission-default`,
default `true`; each accepts `true`/`false`/`op`), then fine-tune per type with a permission
plugin.

Every sign has its own per-player, per-sign cooldown configured at `feature.signs.cooldowns.<type>`; every sign defaults to `1` second. Set an individual value to `0` to disable that sign's cooldown.

Item specs: `material:STONE_SWORD` (vanilla), `item:<custom-id>` (custom item from
LeetCrafting's registry), or a bare `STONE_SWORD`.

## Definition file format

```yaml
id: warp_spawn
conditions:
  cost: 0            # optional per-use Vault charge
  cooldown: 5        # seconds between uses per player
  permission: ""     # optional extra permission node
  reputation: 0      # optional minimum reputation
actions:             # executed in order
  - type: teleport
    location: "world,0.5,64,0.5,0,0"
  - type: message
    text: "<green>Welcome to spawn!"
```

Available action types: `teleport`, `give-items`, `take-items`, `sell`, `buy`,
`enchant`, `kit`, `open-disposal`, `open-chest` (chest registry id), `run-command`
(`commands:` list, `%player%` placeholder, optional `as-player: true`), `message`,
`sound`, `give-exp`, `reputation`, `quest`, and `skill-level-up` (when LeetSkills
is loaded). Conditions: `cost`, `cooldown`, `permission`, plus predicate
conditions `world`, `chance`, `has-item`, `reputation` and `skill-level`.
Generic actions (teleport, give-items, ...) can also be used from core's
`rules/*.yml` event rules (`triggers: [join|death|block-break|consume-item]`).

## Quests

A definition may carry a `quest:` section (see the bundled
`definitions/quest_blacksmith.yml`). Flow:

1. First click: the NPC/sign describes the quest and **accepts** it.
2. Click with all requirements in inventory: a confirm GUI opens.
3. Confirm: requirements are consumed and rewards granted.

Requirements: `items:` (item specs + amounts), `money:`, `reputation:`.
Rewards: `items:`, `money:`, `exp:` (points), `reputation:`, `commands:`.
`repeatable: true` + `cooldown:` (seconds) allow re-runs after the cooldown.

Quest state and reputation are stored per player in `plugins/LeetInteraction/data.db`.

### Quest sign example

Use the definition ID on line 2. Line 3 is unused and line 4 is an optional sign-use price:

```text
[Quest]
quest_blacksmith


```

The sign references the same quest definition used by an NPC. The player must pass the `leet.feat.interaction` feature gate and `leet.interaction.sign.use.quest`. Creating the sign requires `leet.interaction.sign.create.quest`.

## Admin Setup Checklist

1. Install LeetCore and LeetInteraction from the same release.
2. Grant the administrator `leet.admin`.
3. Edit or create a definition in `plugins/LeetInteraction/definitions/`.
4. Run `/leeta reload interact`.
5. Bind an entity or block while looking at it from no more than 6 blocks away.
6. Verify the binding with `/leeta bindings`.

`/leeta bind`, `/leeta unbind` and `/leeta bindings` are Interaction-owned admin operations. Definition changes can be loaded with `/leeta reload interact`; sign permission-default changes require a restart. Back up `plugins/LeetInteraction/data.db` before resetting bindings or quest state.

## Admin Setup

1. Install LeetCore and LeetInteraction from the same release.
2. Grant the administrator `leet.admin`.
3. Edit or create a definition in `plugins/LeetInteraction/definitions/`.
4. Run `/leeta reload interact`.
5. Bind an entity or block while looking at it from no more than 6 blocks away.
6. Verify the binding with `/leeta bindings`.

`/leeta bind`, `/leeta unbind` and `/leeta bindings` are Interaction-owned admin operations. Definition changes can be loaded with `/leeta reload interact`; sign permission-default changes require a restart. Backup `plugins/LeetInteraction/data.db` before resetting bindings or quest state.

## Config

`plugins/LeetInteraction/features/interaction.yml` — standard `base.*` gating plus
`feature.signs/npcs/blocks/quests/chests` capability toggles, `feature.signs.cooldowns`,
`kits:` and `warps:`. All player-facing feedback uses the `messages:` section
(MiniMessage) delivered per `base.message-type`.

Requires LeetCore; economy actions require Vault + an economy provider.
