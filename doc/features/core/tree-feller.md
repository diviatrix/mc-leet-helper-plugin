# Feature: Tree Feller

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

**Owning plugin:** LeetCore · Config file `plugins/LeetCore/features/tree_feller.yml`.

Breaking one log automatically breaks the whole connected tree.

**Permissions**
- See [Feature permissions](../../ARCHITECTURE.md#feature-permissions) for the gating rules, default-deny behavior, and restart caveat. Node: `leet.feat.tree_feller` · default `false`. The node also unlocks the `/leet tree` personal off-toggle.

**Behavior**
1. A player breaks a log (`BlockBreakEvent`).
2. If the broken block's material is in `logs`, a breadth-first search collects every adjacent log block (6-directional: up/down + 4 horizontal) connected to it.
3. Each collected log (beyond the one already broken) is broken like a real break: it fires a `BlockBreakEvent` and drops with the player's main-hand item (`breakNaturally(tool)`, so Silk Touch and the tool's drop rates apply).

**Respects protection plugins:** every felled log is checked through a `BlockBreakEvent`, so GriefPrevention, WorldGuard, and similar claim/region plugins are consulted per block — logs inside a claim/region the player may not break are **skipped**. Similarly, if another plugin cancels the original break, nothing is felled.

The connected-component search means the whole trunk **and any branch/logs touching it** come down together, not just the single broken piece. The search stops as soon as `max-blocks` logs have been collected, capping the work so a giant or player-built log structure can't trigger an unbounded chain of block breaks (anti-lag / anti-abuse).

```yaml
base:
  enabled: true
  permission: leet.feat.tree_feller
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  logs:
    - OAK_LOG
    - SPRUCE_LOG
    - BIRCH_LOG
    - JUNGLE_LOG
    - ACACIA_LOG
    - DARK_OAK_LOG
    - MANGROVE_LOG
    - CHERRY_LOG
    - PALE_OAK_LOG
    - CRIMSON_STEM
    - WARPED_STEM
  max-blocks: 100
  cost: 0                # Vault economy cost per felled tree (0 = free)

messages:
  insufficient-funds: "<red>Insufficient funds! Cost: <cost>"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `logs` | list of Material names | oak/spruce/birch/jungle/acacia/dark-oak/mangrove/cherry/pale-oak logs + crimson/warped stems | Log materials treated as tree trunks. Invalid names are skipped with a warning. |
| `max-blocks` | int | `100` | Hard cap on how many logs the search will collect/break in one tree. Prevents breaking player-built log structures or giant trees from causing lag. |
| `cost` | double | `0.0` | Vault economy cost per trigger (one broken log → one tree fell). `0` or any value `≤ 0` = free. |
| `insufficient-funds` | message | — | Sent when the player lacks funds for the `cost`; the tree fell is blocked (only the single broken log is removed). |

**Cooldown:** none.
