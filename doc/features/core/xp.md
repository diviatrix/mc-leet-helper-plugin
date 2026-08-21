# Feature: XP

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

**Owning plugin:** LeetCore · Config file `plugins/LeetCore/features/xp.yml`.

Grants bonus **vanilla** XP for various in-game actions (no custom entity or XP pool — it adds directly to the player's XP bar via `player.giveExp`). **Effort-based balance:** the easiest, spammable action (building) pays a baseline of 1 XP per block, while the more-effortful jobs (mining, woodcutting, fishing, killing) pay more per action — all adjustable per material/mob below.

**Permissions**
- See [Feature permissions](../../ARCHITECTURE.md#feature-permissions) for the gating rules, default-deny behavior, and restart caveat. Node: `leet.feat.xp` · default `false`. The node also unlocks the `/leet xp` personal off-toggle.

**Behavior**

| Action | Event | Configuration |
|---|---|---|
| Mining | `BlockBreakEvent` | `mining.materials` (Material → XP map) |
| Woodcutting | `BlockBreakEvent` | `woodcutting.materials` (logs/stems → XP map) |
| Crops | `BlockBreakEvent` | `crops.materials` (crop → XP map) |
| Fishing | `PlayerFishEvent` (`CAUGHT_FISH`) | `fishing.amount` |
| Building | `BlockPlaceEvent` | `building.amount` |
| Killing | `EntityDeathEvent` | `killing.amount` (fallback) + `killing.mobs` (per-mob map) |

A player earns XP from a category **only** when the triggered event matches one of its listed materials (or, for fishing/building, when the action fires at all). Unlisted blocks/mobs give nothing. Block breaks are routed to exactly one category (crops → woodcutting → mining).

```yaml
base:
  enabled: true
  permission: leet.feat.xp
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  mining:
    materials:
      COAL_ORE: 1
      IRON_ORE: 5
      GOLD_ORE: 10
      DIAMOND_ORE: 25
      ANCIENT_DEBRIS: 20
      # ... only ores award XP (stone/stone-variants/raw-ore blocks give nothing)
  woodcutting:
    materials:
      OAK_LOG: 1
      SPRUCE_LOG: 1
      BIRCH_LOG: 1
      # ... all log/stem types
  crops:
    materials:
      WHEAT: 2
      CARROTS: 2
      POTATOES: 2
      BEETROOTS: 2
      NETHER_WART: 2
      COCOA: 2
      SWEET_BERRY_BUSH: 1
  fishing:
    amount: 5
  building:
    amount: 0
  killing:
    amount: 8
    mobs:
      ZOMBIE: 5
      SKELETON: 7
      SPIDER: 7
      CREEPER: 12
      ENDERMAN: 15

messages:
  xp-gained: "<green>+<amount> XP<reset> (<action>)"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `mining.materials` | map of Material → int | coal 1; copper/redstone 2; iron/lapis 5; gold/nether-gold 10; ancient debris 20; diamond/emerald 25 | XP per mined block. Only **ores** (and ancient debris) award — stone/stone-variants and raw-ore blocks give nothing. Invalid names skipped with a warning. |
| `woodcutting.materials` | map of Material → int | all logs + crimson/warped stems = 1 each | XP per felled log/stem. Invalid names skipped with a warning. |
| `crops.materials` | map of Material → int | wheat, carrots, potatoes, beetroots, nether wart, cocoa = 2; sweet berry 1 | XP per harvested crop. |
| `fishing.amount` | int | `5` | XP per successful catch. |
| `building.amount` | int | `0` | XP per block placed (set to 0 to disable building XP). |
| `killing.amount` | int | `8` | XP fallback per mob killed (used for any mob not in `mobs`). |
| `killing.mobs` | map of EntityType → int | zombies 5, skeletons/spiders 7, piglins 10, creepers 12, endermen 15 | XP per specific mob type. |
| `placed-tracking` | string | `memory` | How to remember blocks a player placed so a later break gives no XP. `memory` = in-memory only (lost on restart). `persistent` = SQLite, survives restarts (still bounded to ~1 hour). |

**Feedback:** after earning XP, the `xp-gained` message is delivered via `base.message-type` (ACTION_BAR by default). The placeholders are `<amount>` (XP gained) and `<action>` (the action label, e.g. `Mining`, `Fishing`, `Killing`). To make XP **silent**, remove/empty the `xp-gained` message template — a missing or empty template produces no message, while XP is still granted.

**Notes & limits**
- Uses vanilla `player.giveExp`, so XP respects the vanilla level curve and can level the player up.
- Per-material amounts keep the XP economy fully controllable; only listed materials award anything.
- **Player-placed blocks give no break XP** — mining/woodcutting/crops XP is skipped for a block the player themselves placed (tracked from `BlockPlaceEvent`). Placing still gives its own building XP. Choose how to store that memory with `feature.placed-tracking` (`memory` by default, or `persistent` for SQLite).
- No cooldown by default; set `base.cooldown > 0` to throttle it if desired (applies to every grant uniformly).

> **Placement tracking caveat (both backends):** placed-block markers survive for **~1 hour** and are dropped on break. Block edits made by non-`BlockPlaceEvent` tools (e.g. WorldEdit) are not tracked and may still award break XP when broken. The only difference between `memory` and `persistent` is restart survival: a `persistent` marker placed just before a restart is still honored after it; an in-memory one is not.

**Cooldown:** none (by default).
