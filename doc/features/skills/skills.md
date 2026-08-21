# Feature: Skills

**Owning plugin:** LeetSkills · Config files `plugins/LeetSkills/features/skills.yml` and `plugins/LeetSkills/features/skill-tree.yml`. Skill levels/toggles persist in LeetSkills' `plugins/LeetSkills/data.db`.

> Each skill that duplicates a standalone feature (`smith`↔`durability`, `tree-feller`↔`tree_feller`, `auto-crop`↔`auto_crop`, `fall-nullify`↔`fall_damage`, `double-jump`↔`double_jump`) declares that binding in `skills.yml` with `binds-feature: <feature-id>` (and `toggleable: true` where the player may toggle it via `/leet`). The skill *ignores the feature's enabled state* and keys off the feature's **permission**: if a player holds `leet.feat.<feature>`, the skill shows as **already acquired** and the feature is the only provider of the effect (the skill won't also fire — no double application). A player without that permission can still level the skill with XP and get the effect from the skill. Either way, exactly one thing fires per effect.

> Common `base:`/`messages:` config layout and control model: [ARCHITECTURE.md](../../ARCHITECTURE.md#common-feature-config-layout) · Admin: [Admin.md](../../Admin.md)

A **skill tree** opened with `/skills`. **Traveler** sits at the center. Around it are the eight **ring skills** (**lumberjack, miner, smith, farmer, animalist, fisherman, warrior, explorer**), each unlocked once Traveler reaches its max level (10). Around the tree are the **advanced skills** in a lower tier, each unlocked once its ring skill reaches level 10 (three are 1-level — Tree Feller, Fall Nullify, Double Jump; **Auto Crop** spans 3 — its radius equals its level; **Gardener**, **Breeder**, **Lucky Catch**, **Swimmer** and **Diver** span 10). Players spend **vanilla XP points** (`player.getTotalExperience()` / `giveExp(-cost)`) to level skills up.

The feature is split across **two config files**:
- `features/skills.yml` — the **skill definitions**: each skill's name, icon, `max-level`, `exp` costs, effects, and materials.
- `features/skill-tree.yml` — the **tree topology**: which skills sit in the ring vs the advanced tier, and each skill's prerequisite (`requires`/`require-level`).

Separating them lets you reshape the tree (reorder skills, drop `double-jump` or `tree-feller`, add your own, rewire prerequisites) in `skill-tree.yml` without touching a skill's definition; removing a skill from the tree simply leaves that effect to the matching standalone feature.

**Permissions**
- **Node:** `leet.feat.skills` · **default:** `false` (nobody), registered at runtime by LeetSkills.
- `/skills` has **no static command permission** — access is gated entirely by this same `leet.feat.skills` node at runtime (`SkillsCommand` checks `skillsFeature.appliesTo(player)`), so there's a single source of truth for the node (default-denied).
- Grant the node (e.g. LuckPerms) to open `/skills` and to have passive effects apply; it also unlocks the `/leet skills` personal off-toggle. The node alone is not enough — `base.enabled`, the permission, `base.worlds` and the player toggle must all pass.
- Set `base.default-permission` in `skills.yml` to `true` (everyone) or `op` (ops only) to change the out-of-box default. Nodes are registered at startup, so permission config changes require a **restart**.

**UX flow**
- `/skills` → skill tree (Traveler centered; ring of 8 once Traveler is maxed, advanced skills around the tree). A bottom-center **Exit** button (door) or `ESC` closes the skills menu.
- Click a skill → detail screen (description + Level Up). Hover any skill shows name, `Level x/10`, and the XP cost of the next level.
- Click Level Up → if you have enough total XP, an **Apply / Back** confirm screen; Apply deducts XP and advances one level. When you can't afford the next level, the Level Up button shows a red **X** instead of the potion (and clicking it sends the `insufficient-xp` message and stays).
- Skills whose prerequisite isn't met appear as a glinting emerald lock showing **"Reach `<skill>` level N"**.

**Leveling**
- Each skill starts at 0 and maxes at `max-level`. Per-skill `exp` list: entry *i* = XP to go from level *i* to *i+1* (a table per skill in `skills.yml`). Advanced skills have `max-level: 1` and a one-entry `exp` table.
- A skill cannot be leveled until its prerequisite is met (`requires` in `skill-tree.yml`); beyond the GUI lock, `levelUp` refuses with the `locked` message.
- Levels are persisted per-player in the skills plugin's SQLite `kv_store` (`plugins/LeetSkills/data.db`, key `levels`, a Gson map of skill id → level), so they survive restarts.

## The skill-tree config (`features/skill-tree.yml`)

```yaml
tree:
  ring:                # ring skills shown around Traveler, in display order
    - lumberjack
    - miner
    - smith
    - ...
  advanced:            # advanced skills shown in the lower band, in display order
    - tree-feller
    - auto-crop
    - ...
  requires:            # prerequisite per skill (omit a skill => no prerequisite)
    lumberjack: { skill: stamina, level: 10 }
    tree-feller: { skill: lumberjack, level: 10 }
    double-jump: { skill: fall-nullify, level: 1 }
  slots:               # GUI slot per advanced skill (0..53 in a 9x6 inventory)
    tree-feller: 10
    auto-crop: 20
    ...
```

- `ring` / `advanced` are **ordered lists of skill ids** (each must have a matching `feature.skills.<id>` definition in `skills.yml`). Traveler is always the center and root. Reorder to change display order; remove an id to pull that skill out of the tree (its definition stays, so it can be added back). Unknown ids are skipped.
- `requires` gives each skill the prerequisite it needs to unlock/level: reach `level` of `skill`. A skill with no entry has no prerequisite (open). Referencing `stamina` (the Traveler skill's id) is how the ring skills get gated at level 10; the advanced skills reference their ring skill at 10.
- `slots` maps each advanced skill id to its GUI slot on the tree screen. **Every id in `advanced` needs a slot here**, or it will not render (and the plugin logs a warning at load). This is config-driven so a newly-added advanced skill either renders or logs loudly — never silently disappears.

## Ring skills (outer tier, unlocked by Traveler level 10)

Each ring skill is given a Traveler-10 prerequisite (`requires: stamina level 10`) in `skill-tree.yml`.

| Skill | What it does | Effects (`effects.<id>`, values per level) |
|---|---|---|
| **Traveler** | Hunger drains slower and HP regens faster | `hunger` (5), `regen` (5) — up to 50% at level 10 |
| **Lumberjack** | Chance **+1** bonus log when you fell a (whole) tree | `extra-block` (10); `logs`. Level 10 unlocks the **Tree Feller** and **Gardener** skills |
| **Miner** | Chance the mined block drops a second one | `extra-block` (2.5 → 25% at 10); `blocks` |
| **Smith** | Chance a tool/armor takes no durability damage from a hit | `durability` (5 → 50% at 10) |
| **Farmer** | Extra crop + bonus seed drops | `extra-drop` (5), `seed` (1); `crops`. Level 10 unlocks the **Auto Crop** skill |
| **Animalist** | Bonus wool/milk drops | `extra-gather` (2.5). Level 10 unlocks the **Breeder** skill |
| **Fisherman** | Chance of a bonus catch | `extra-catch` (2.5 → 25% at 10); `bonus-items`. Level 10 unlocks the **Lucky Catch** skill |
| **Warrior** | Chance your attacks deal double damage | `crit` (2.5 → 25% at 10) |
| **Explorer** | Faster walk speed | `speed` (2); level 10 unlocks the **Fall Nullify** and **Swimmer** skills |

## Advanced skills (lower tier, prerequisite-gated)

Advanced skills are `max-level: 1`; buying them costs its single `exp` entry and once held the passive is always on. **Auto Crop** (3 levels, radius = level), and **Gardener**, **Breeder**, **Lucky Catch**, **Swimmer**, **Diver** (10 levels) are the exceptions: ring-skill-gated skills that span multiple levels while sitting in the same lower tier.

| Skill | Unlock prerequisite | What it does |
|---|---|---|
| **Tree Feller** | lumberjack level 10 | Fell a whole tree with one swing (via `TreeFellerUtil`, cap `feature.tree-feller.max-blocks`) |
| **Gardener** | lumberjack level 10 | Chance a felled tree drops an apple / golden apple, once per tree (2.5–25% / 0.5–5%) |
| **Auto Crop** | farmer level 10 | Auto-harvest a field of nearby crops (via `AutoCropUtil`); harvest radius = skill level (1–3) |
| **Fall Nullify** | explorer level 10 | Take no damage from falling |
| **Double Jump** | fall-nullify level 1 | Launch again in mid-air |
| **Swimmer** | explorer level 10 | Move faster in water (5 → 50% at 10) |
| **Diver** | swimmer level 10 | Hold your breath longer underwater (10 → 100% at 10, i.e. 2×) |
| **Breeder** | animalist level 10 | Chance an extra baby drops when breeding (2.5 → 25%) |
| **Lucky Catch** | fisherman level 10 | Chance a catch is upgraded to a higher-tier item (1 → 10%) |

**Generic effect model** — every ring skill is a named body of `effects`, so the skill tree and the passive engine share the same data. Each effect is either:
- a **numeric** `per-level` increment: current value at level N = `N × per-level` (shown on the detail screen as `+N×x%`); or
- a level-gated **unlock** `unlock-at: N` (an optional `unlock-value` supplies the flat percentage). Its detail icon shows "Unlocks at level N" until active.

The detail screen renders every effect as its own icon with its current modifier; the tree hover shows each effect's **current value + `desc`** (e.g. Smith at level 10 → `50% chance a tool takes no durability damage`). Locked skills show a glinting emerald lock.

### Skills vs. the standalone features
Skills that duplicate a standalone feature (**Smith**↔`durability`, **Tree Feller**↔`tree_feller`, **Auto Crop**↔`auto_crop`, **Fall Nullify**↔`fall_damage`, **Double Jump**↔`double_jump`) ignore the feature's `base.enabled` entirely and key off the feature's **permission**. The duplication is declared **in config**, not hard-coded: each such skill sets `binds-feature: <feature-id>` in its `skills.yml` definition, and `toggleable: true` where the player may also toggle it via `/leet`.
- If the player holds `leet.feat.<feature>`, the skill is shown as **already acquired** (maxed, not purchasable) and the feature is the single provider of the effect — the skill's own passive does not fire for that player.
- Otherwise the skill is a normal purchasable skill: level it with XP and the skill itself provides the effect.

Because ownership switches on the permission, exactly one path ever fires per effect — no double invocation. A prerequisite that is a feature-granted skill (e.g. Double Jump needs Fall Nullify) counts as met when the player holds that feature's permission. At load time a config check (`validateBindings`) warns if a `binds-feature` references a feature that isn't registered.

## Example config

`features/skills.yml` (definitions — a skill has no notion of where it sits in the tree):

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
    max-blocks: 100    # tree-feller skill cap
  auto-crop:
    require-mature: true
  double-jump:
    horizontal-multiplier: 0.25
    vertical-multiplier: 1.0
  skills:
    stamina:
      name: "Traveler"
      icon: GOLDEN_APPLE
      max-level: 10
      exp: [50, 75, 110, 160, 220, 300, 400, 520, 660, 820]
      effects:
        hunger:
          name: "Hunger drain slow-down"
          desc: "slower hunger drain"
          icon: BREAD
          per-level: 5
        regen:
          name: "Health regen boost"
          desc: "faster health regen"
          icon: APPLE
          per-level: 5
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
          desc: "bonus log drop"
          icon: OAK_LOG
          per-level: 10
    smith:
      name: "Smith"
      icon: ANVIL
      max-level: 10
      exp: [80, 110, 150, 200, 260, 330, 410, 500, 600, 710]
      effects:
        durability:
          name: "Ignore tool damage"
          desc: "chance a tool takes no durability damage"
          icon: ANVIL
          per-level: 5
    tree-feller:
      name: "Tree Feller"
      icon: OAK_LOG
      max-level: 1
      exp: [500]
      binds-feature: tree_feller   # ties to the standalone feature; toggleable where set
      toggleable: true
      lore:
        - "Fell a whole tree with one swing."
  # ... miner, farmer, fisherman, warrior, explorer, auto-crop, fall-nullify,
  #     double-jump, breeder, lucky-catch, gardener ...

messages:
  feature-off: "<red>Skills are currently off for you."
  insufficient-xp: "<red>Not enough XP (need <needed>)."
  locked: "<red><skill> is locked. Reach <required> level <require-level> to unlock."
  max-level: "<green><skill> is already at max level."
  level-up: "<green><skill> is now level <level>!<reset> (-<cost> XP)"
```

`features/skill-tree.yml` (topology — which skills, in what order, with what prerequisite):

```yaml
tree:
  ring:
    - lumberjack
    - miner
    - smith
    - farmer
    - animalist
    - fisherman
    - warrior
    - explorer
  advanced:
    - tree-feller
    - auto-crop
    - fall-nullify
    - double-jump
    - breeder
    - lucky-catch
    - gardener
    - swimmer
    - diver
  requires:
    lumberjack: { skill: stamina, level: 10 }
    miner: { skill: stamina, level: 10 }
    smith: { skill: stamina, level: 10 }
    farmer: { skill: stamina, level: 10 }
    animalist: { skill: stamina, level: 10 }
    fisherman: { skill: stamina, level: 10 }
    warrior: { skill: stamina, level: 10 }
    explorer: { skill: stamina, level: 10 }
    tree-feller: { skill: lumberjack, level: 10 }
    gardener: { skill: lumberjack, level: 10 }
    auto-crop: { skill: farmer, level: 10 }
    fall-nullify: { skill: explorer, level: 10 }
    double-jump: { skill: fall-nullify, level: 1 }
    breeder: { skill: animalist, level: 10 }
    lucky-catch: { skill: fisherman, level: 10 }
    swimmer: { skill: explorer, level: 10 }
    diver: { skill: swimmer, level: 10 }
```

## Common keys

### skills.yml

| Key | Type | Default | Description |
|---|---|---|---|
| `gui.title` | string | `Skills` | Inventory title. |
| `gui.rows` | int (4–9) | `6` | Inventory height in rows. |
| `tree-feller.max-blocks` | int | `100` | Hard cap on logs felled by one Tree Feller swing. |
| `auto-crop.require-mature` | bool | `true` | Only harvest mature crops with Auto Crop (harvest radius always = skill level 1–3). |
| `double-jump.horizontal-multiplier` | double | `0.25` | Horizontal push of a Double Jump launch. |
| `double-jump.vertical-multiplier` | double | `1.0` | Vertical push of a Double Jump launch. |
| `<skill>.name` | string | id | Display name. |
| `<skill>.icon` | Material | — | GUI icon. |
| `<skill>.max-level` | int | `10` | Max level (1 for advanced skills). |
| `<skill>.exp` | int list | — | Per-level XP cost table (one entry for 1-level skills). |
| `<skill>.binds-feature` | string | — | If set, this skill duplicates a standalone feature id; the skill keys off that feature's permission (see [Skills vs. the standalone features](#skills-vs-the-standalone-features)). |
| `<skill>.toggleable` | bool | `false` | Only meaningful with `binds-feature`: allow the player to also toggle this skill via `/leet`. |
| `<skill>.lore` | string list | — | Description for skills with no effects (e.g. the 1-level advanced skills). Effect skills show each effect's current value instead. |
| `<skill>.effects.<id>` | section | — | A single effect; see below. |
| `<skill>.effects.<id>.name` | string | id | Effect label on its detail icon. |
| `<skill>.effects.<id>.desc` | string | name | Short value-free phrase shown with the effect's current % on the skill tree. |
| `<skill>.effects.<id>.icon` | Material | — | Effect's detail icon. |
| `<skill>.effects.<id>.per-level` | double | `0` | Numeric increment: current value = `level × per-level`. |
| `<skill>.effects.<id>.unlock-at` | int | `0` | If > 0, a level-gated unlock instead of a numeric effect. |
| `<skill>.effects.<id>.unlock-value` | double | `0` | Flat % granted once `unlock-at` is reached. |
| `<skill>.logs/.blocks/.crops` | Material list | — | Materials the gathering/placement passives act on. |
| `<skill>.bonus-items/.quality-items` | Material list | — | Bonus-catch pool (Fisherman) / higher-tier catch pool (Lucky Catch). |

### skill-tree.yml

| Key | Type | Default | Description |
|---|---|---|---|
| `tree.ring` | string list | — | Ordered ring skills around Traveler. |
| `tree.advanced` | string list | — | Ordered advanced skills below the ring (mostly 1-level, but **Breeder** and **Lucky Catch** span 10). |
| `tree.slots.<advanced-id>` | int (0–53) | — | GUI slot for each advanced skill on the 9×6 tree screen. **Required for every id in `tree.advanced`** or it won't render (a warning is logged). |
| `tree.requires.<skill>.skill` | string | — | Prerequisite skill id for `<skill>` (absent = no prerequisite). |
| `tree.requires.<skill>.level` | int | `1` | Min level of the prerequisite skill needed to unlock `<skill>`. |

The generic `effects` list is the single source of truth for both the skill tree's detail screen (each effect becomes its own icon with the current modifier) and the passive engine. Invalid material/damage-cause names are skipped with a warning.

## Feedback & currency
- Level-up / insufficient-XP feedback goes through the generic `messages` + `base.message-type` system (ACTION_BAR by default). Placeholders: `level-up` uses `<skill>`, `<level>`, `<cost>`; `insufficient-xp` uses `<needed>`; `locked` uses `<skill>`, `<required>`, `<require-level>`.
- Skills spend **XP points**, not levels or money — the same pool the [XP feature](../core/xp.md) grants. There is **no** Vault `cost` for leveling.

## Limits
- Ring skills stay **locked** until Traveler reaches `max-level` (10); each advanced skill stays locked until its `requires` skill reaches `require-level`.
- Traveler regen is approximated by a repeating task that adds `regen × level / 100` HP per second (regen effect value / 100) when you're in the natural-regen food range (hunger ≥ 18); it does not interfere with vanilla regen.
- Explorer's speed boost sets your walk speed to `0.2 × (1 + speed × level / 100)` and persists while the skill is active; it overrides potion-based speed while equipped.

**Cooldown:** none (by default).