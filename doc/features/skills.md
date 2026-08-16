# Feature: Skills

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../Admin.md)

A **skill tree** opened with `/skills`. Stamina sits at the center; the eight passive skills (**lumberjack, miner, builder, farmer, animalist, fisherman, warrior, explorer**) appear around it once Stamina reaches its max level (10). Players spend **vanilla XP points** (`player.getTotalExperience()` / `giveExp(-cost)`) to level skills up. Config file `features/skills.yml`. All skills are passive — none adds an active ability.

**Permissions**
- **Node:** `leet.feat.skills` · **default:** `false` (nobody).
- Grant the node (e.g. LuckPerms) to open `/skills` and to have passive effects apply; it also unlocks the `/leet skills` personal off-toggle. The node alone is not enough — `base.enabled`, the permission, `base.worlds` and the player toggle must all pass.
- Set `base.default-permission` in `skills.yml` to `true` (everyone) or `op` (ops only) to change the out-of-box default. Nodes are registered at startup, so permission config changes require a **restart**.

**UX flow**
- `/skills` → skill tree (Stamina centered; ring of 8 only when Stamina is maxed). A bottom-center **Exit** button (door) or `ESC` closes the skills menu.
- Click a skill → detail screen (description + Level Up). Hover any skill shows name, `Level x/10`, and the XP cost of the next level.
- Click Level Up → if you have enough total XP, an **Apply / Back** confirm screen; Apply deducts XP and advances one level. When you can't afford the next level, the Level Up button shows a red **X** instead of the potion (and clicking it sends the `insufficient-xp` message and stays).

**Leveling**
- Each skill starts at 0 and maxes at `max-level` (10). Per-skill `exp` list: entry *i* = XP to go from level *i* to *i+1* (a table per skill in `skills.yml`).
- Levels are persisted per-player in the SQLite `kv_store` (key `levels`, closing a Gson map of skill id → level), so they survive restarts.

## Passive effects

| Skill | What it does | Effects (`effects.<id>`, values per level) |
|---|---|---|
| **Stamina** | Hunger drains slower and HP regens faster | `hunger` (10), `regen` (10) — level 10 = 2× food duration, 2× regen |
| **Lumberjack** | Bonus log / apple / golden-apple drops; level 10 fells whole trees | `extra-block` (10), `apple` (1), `golden-apple` (0.1), `tree-feller` (unlock 10); `logs` |
| **Miner** | Chance the mined block drops a second one | `extra-block` (2.5 → 25% at 10); `blocks` |
| **Farmer** | Extra crop + bonus seed drops; level 10 auto-harvests the field | `extra-drop` (5), `seed` (1), `auto-crop` (unlock 10); `crops` |
| **Builder** | Chance a placed block is not consumed from the inventory | `no-consume` (2.5 → 25% at 10); `blocks` (wood & stone by default) |
| **Animalist** | Bonus wool/milk; level 10 gives a chance of an extra baby when breeding | `extra-gather` (2.5), `breed` (unlock 10, value 10); |
| **Fisherman** | Extra catch + higher-tier catch chances | `extra-catch` (2.5), `quality` (1); `bonus-items`, `quality-items` |
| **Warrior** | Flat damage taken reduction | `damage-reduction` (2); `ignored-causes`, capped at `feature.defense.max-pct` |
| **Explorer** | Faster walk speed; level 10 nullifies fall damage | `speed` (2), `fall-nullify` (unlock 10) |

**Generic effect model** — every skill is a named body of `effects`, so the skill tree and the passive engine share the same data. Each effect is either:
- a **numeric** `per-level` increment: current value at level N = `N × per-level` (shown on the detail screen as `+N×x%`); or
- a level-gated **unlock** `unlock-at: N` (an optional `unlock-value` supplies the flat percentage, e.g. the animalist extra-baby 10%). Its detail icon shows "Unlocks at level N" until active.

The detail screen renders every effect as its own icon with its current modifier; locked skills show a glinting emerald lock.

### Level-10 unlocks vs. the standalone features
The two level-10 unlocks that overlap existing features share their logic and **stand down when the matching feature is active for you**, so effects don't stack:
- **Lumberjack 10** fells whole trees (via `TreeFellerUtil`), but only while the **Tree Feller** feature (`tree_feller`) is **not** toggled on for you. When it is, lumberjack 10 just caps the drop chances.
- **Farmer 10** auto-harvests the field (via `AutoCropUtil`, radius from `feature.auto-crop`), but only while the **Auto Crop** feature is **not** toggled on for you.

## Example config

```yaml
base:
  enabled: true
  permission: leet.feat.skills
  default-permission: false
  worlds: []
  cooldown: 0
  message-type: ACTION_BAR

feature:
  gui:
    title: "Skills"
    rows: 6            # 4-9; the skill ring needs at least 4
  tree-feller:
    max-blocks: 100    # lumberjack level-10 cap
  auto-crop:
    radius: 3          # farmer level-10 harvest radius (≤5)
    require-mature: true
  defense:
    max-pct: 50        # warrior reduction cap
  skills:
    stamina:
      name: "Stamina"
      icon: GOLDEN_APPLE
      max-level: 10
      exp: [50, 75, 110, 160, 220, 300, 400, 520, 660, 820]
      effects:
        hunger:
          name: "Hunger drain slow-down"
          icon: BREAD
          per-level: 10
        regen:
          name: "Health regen boost"
          icon: APPLE
          per-level: 10
      lore:
        - "Reduces hunger drain"
        - "Speeds health regeneration"
    lumberjack:
      name: "Lumberjack"
      icon: OAK_SAPLING
      max-level: 10
      exp: [80, 110, 150, 200, 260, 330, 410, 500, 600, 710]
      logs:
        - OAK_LOG
        - SPRUCE_LOG
      effects:
        extra-block:
          name: "Bonus log drop"
          icon: OAK_LOG
          per-level: 10
        tree-feller:
          name: "Whole-tree feller"
          icon: DIAMOND_AXE
          unlock-at: 10
      lore:
        - "+10%/level bonus log drop"
  # ... miner, farmer, builder, animalist, fisherman, warrior, explorer ...

messages:
  feature-off: "<red>Skills are currently off for you."
  insufficient-xp: "<red>Not enough XP (need <needed>)."
  max-level: "<green><skill> is already at max level."
  level-up: "<green><skill> is now level <level>!<reset> (-<cost> XP)"
```

## Common keys

| Key | Type | Default | Description |
|---|---|---|---|
| `gui.title` | string | `Skills` | Inventory title. |
| `gui.rows` | int (4–9) | `6` | Inventory height in rows. |
| `tree-feller.max-blocks` | int | `100` | Hard cap on logs felled in one lumberjack-10 swing. |
| `auto-crop.radius` | int (≤5) | `3` | Farmer level-10 auto-harvest radius. |
| `auto-crop.require-mature` | bool | `true` | Only harvest mature crops at farmer level 10. |
| `defense.max-pct` | int | `50` | Cap on warrior damage reduction. |
| `<skill>.name` | string | id | Display name. |
| `<skill>.icon` | Material | — | GUI icon. |
| `<skill>.max-level` | int | `10` | Max level. |
| `<skill>.exp` | int list (10) | — | Per-level XP cost table. |
| `<skill>.lore` | string list | — | Short description shown on hover / detail. |
| `<skill>.effects.<id>` | section | — | A single effect; see below. |
| `<skill>.effects.<id>.name` | string | id | Effect label on its detail icon. |
| `<skill>.effects.<id>.icon` | Material | — | Effect's detail icon. |
| `<skill>.effects.<id>.per-level` | double | `0` | Numeric increment: current value = `level × per-level`. |
| `<skill>.effects.<id>.unlock-at` | int | `0` | If > 0, a level-gated unlock instead of a numeric effect. |
| `<skill>.effects.<id>.unlock-value` | double | `0` | Flat % granted once `unlock-at` is reached (e.g. animalist `breed`). |
| `<skill>.logs/.blocks/.crops` | Material list | — | Materials the gathering/placement passives act on. |
| `<skill>.bonus-items/.quality-items` | Material list | — | Fisherman bonus / upgraded catch pools. |
| `<skill>.ignored-causes` | DamageCause list | — | Damage causes the warrior skill leaves alone. |

The generic `effects` list is the single source of truth for both the skill tree's detail screen (each effect becomes its own icon with the current modifier) and the passive engine. Invalid material/damage-cause names are skipped with a warning.

## Feedback & currency
- Level-up / insufficient-XP feedback goes through the generic `messages` + `base.message-type` system (ACTION_BAR by default). Placeholders: `level-up` uses `<skill>`, `<level>`, `<cost>`; `insufficient-xp` uses `<needed>`.
- Skills spend **XP points**, not levels or money — the same pool the [XP feature](xp.md) grants. There is **no** Vault `cost` for leveling.

## Limits
- The 8 outer skills stay **locked** until Stamina reaches `max-level` (10).
- Stamina regen is approximated by a repeating task that adds `regen × level / 100` HP per second (regen effect value / 100) when you're in the natural-regen food range (hunger ≥ 18); it does not interfere with vanilla regen.
- Explorer's speed boost sets your walk speed to `0.2 × (1 + speed × level / 100)` and persists while the skill is active; it overrides potion-based speed while equipped.

**Cooldown:** none (by default).