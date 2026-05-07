# Semion TD Minecraft Mini Game Plan

## Goal

Build a Minecraft Fabric server-side mini game inspired by StarCraft II Semion Tower Defense.

The target game is not a normal cooperative tower defense. It is a four-team competitive income TD where each player defends a lane inside their team, sends monsters to other teams, grows their economy, and wins with their team by being the final surviving team.

## Current Project Context

- Platform: Minecraft Fabric server mod
- Minecraft version: 1.21.8
- Java version: 21
- Expected players: 5:5:5:5, four teams, up to 20 players
- Current mod environment: server-side
- Existing useful dependencies:
  - Fabric API
  - Polymer
  - Fantasy
  - Map Templates
  - Stimuli
  - Sidebar API
  - DialogUtils

## Confirmed Game Rules

- The game has four teams.
- Each team can have up to five players.
- Each player has a lane to defend.
- Victory condition: a team wins when every other team is eliminated.
- A team is eliminated when its final-lane boss monster dies.
- The game keeps the Semion 2 style mineral, gas, and income structure, but final names will be changed later.
- Starting mineral is 200.
- Starting gas is 50.
- Starting income is 0.
- There is no builder-worker entity equivalent.
- The old builder-worker role is replaced by a numeric gas production value.
- Gas production increases the amount of gas gained per second.
- Gas production settings must be configurable.
- Gas has a round-based storage cap.
- When a player summons a monster, that monster is sent to a random living team other than the sender's team.
- Dead teams are removed from the random target pool.
- Kills grant mineral.
- At each round payout, players gain mineral equal to their income.
- Round payout happens after all non-eliminated teams have cleared the current round or have been eliminated.
- Monster waves must be configurable from config files.
- Configured monster waves spawn per player lane.
- From round 20 onward, infinite rounds can use one repeated monster type.
- Simultaneous elimination has no special tie rule; one server tick still resolves teams in deterministic iteration order.
- Tower-produced defender entities reset at the next round instead of persisting across rounds.
- Summon monster limits are owned by the summoning tower implementation, not by the global game loop.
- Monsters that reach the boss position stay active and fight the team boss through normal entity combat.
- If a wave runs past the 90-second timeout, remaining lane monsters and lane towers are forced toward the final defense area.

## Working Currency Names

The final names are not decided yet. Until then, implementation can use neutral internal names.

```text
mineral     // Tower construction, tower upgrades, and defense growth
gas         // Monster summoning and gas-production growth
income      // Mineral payout amount given at round payout
gasPerSec   // Gas gained per second
```

Expected player-owned economy:

```text
PlayerEconomy
  mineral
  gas
  income
  gasPerSec
  gasProductionUpgradeCount
```

Expected team-owned state:

```text
TeamState
  teamId
  members
  lanes
  boss
  eliminated
```

Initial values:

```text
startingMineral = 200
startingGas = 50
startingIncome = 0
```

Gas storage cap:

```text
gasCap(round) = 500 + 2 * ((round - 1) * 20) + 10
```

Examples:

```text
round 1  -> 510
round 2  -> 550
round 20 -> 1270
```

The formula above assumes `2((round - 1) * 20)` means multiplication by 2. If the intended formula is different, this should be changed before implementation.

Gas production upgrade config:

```text
GasProductionConfig
  initialGasPerSec
  maxUpgradeCount
  initialUpgradeCost
  upgradeCostIncrease
  gasPerSecIncrease
```

## Map Config

`map.json` controls the MapTemplate resource and the region marker names.

Default config:

```text
templateId = "semion-td:arena"
origin = 0, 80, 0
timeOfDay = 6000

regions.teamSpawn = "semion:team_spawn"
regions.laneSpawn = "semion:lane_spawn"
regions.lanePath = "semion:lane_path"
regions.laneWaypoint = "semion:lane_waypoint"
regions.bossSpawn = "semion:boss_spawn"
```

The template should be available as:

```text
data/semion-td/map_template/arena.nbt
```

Korean map authoring guide:

```text
docs/map-creation-guide.ko.md
```

## Economy Model

The current planned model is mixed economy.

Shared by team:

- Lane group state
- Player lane ownership inside the team
- Final boss monster state
- Elimination state
- Incoming summoned monsters

Owned by each player:

- Mineral
- Gas
- Income
- Gas per second
- Towers
- Job selection, when jobs are added

Economy events:

```text
monster killed
  -> killing player or tower owner gains mineral

round payout
  -> each player gains mineral equal to that player's income

gas tick
  -> each player gains gas according to gasPerSec
  -> gas cannot exceed gasCap(currentRound)

monster summoned
  -> summoning player spends gas
  -> summoning player gains income according to the summoned monster definition

gas production upgraded
  -> player spends configured currency
  -> player's gasPerSec increases
  -> player's upgrade count increases
  -> upgrade is rejected when maxUpgradeCount is reached
```

Open decision:

- Decide whether kill mineral goes to the tower owner, the whole team, or the player who owns the killing tower.
- Current recommendation: tower owner gets the kill mineral. Team-wide sharing can be added later if needed.

## Team And Elimination Model

The game is not eliminated by a simple life counter. Each team has a final-lane boss monster.

Each team can have up to five player lanes. Each team should get its own Fantasy runtime world from the same MapTemplate, and each lane should be resolved from that team's world regions rather than from player position.

Team membership should be mirrored through the vanilla Minecraft scoreboard team system. Semion uses these vanilla team names:

```text
semion_red
semion_blue
semion_green
semion_yellow
semion_spectator
```

Internal `TeamId` remains the authoritative gameplay enum, but visible team color/name membership is applied to the vanilla team system. This lets operators use normal Minecraft team tools before or during setup.

Roster and team assignment flow:

```text
/semiontd create
  -> creates Semion game arenas
  -> ensures vanilla scoreboard teams exist

/semiontd testmode true
  -> enables 1v1 test selection mode

/team join semion_red <player>
  -> optional vanilla pre-assignment by an operator

/semiontd join RED
  -> records vanilla team preference for the next start
  -> does not immediately add the player to the active match

/semiontd autojoin
  -> scans current online players
  -> builds a start roster according to current match mode
  -> respects existing semion_* vanilla team membership when possible
  -> assigns selected active players to gameplay teams
  -> assigns non-selected players to semion_spectator

/semiontd start
  -> builds the same roster plan from current online players
  -> locks the roster
  -> only selected players become active Semion players
  -> non-selected online players remain spectators
```

Roster rules:

- Mid-game join is blocked after the roster is locked.
- Only players selected at start can play in the current match.
- Players left out of the start roster are spectators for that match.
- `TEST` mode always selects exactly `1v1` if at least two players are online.
- `NORMAL` mode avoids `1v1` and one-player teams.
- `NORMAL` mode uses all online players when possible, up to 20 active players.
- `NORMAL` mode chooses the smallest active team count that keeps every team at or below 5 players.
- Teams are then distributed as evenly as possible for that team count.
- Two-player teams are only used when a 3+ player split is impossible, such as `4 -> 2/2` or `5 -> 3/2`.
- Players above the 20 active-player cap are assigned to `semion_spectator`.

Examples:

```text
2 players, TEST    -> 1v1
4 players, NORMAL  -> 2/2
6 players, NORMAL  -> 3/3
8 players, NORMAL  -> 4/4
9 players, NORMAL  -> 5/4
10 players, NORMAL -> 5v5
12 players, NORMAL -> 4/4/4
16 players, NORMAL -> 4/4/4/4
20 players, NORMAL -> 5/5/5/5
```

The final boss is the team's life. Leaked enemies that reach the final defense line fight the boss directly. The system does not need a special damage-resolution shortcut for this part; it only needs to let the normal combat happen and detect boss death.

Elimination flow:

```text
team boss alive
  -> team can defend
  -> team can receive random summoned monsters
  -> team players can summon monsters

team boss dies
  -> team is eliminated
  -> team is removed from summon target pool
  -> team lanes stop receiving new summoned monsters
  -> remaining team-owned lane monsters are cleaned up or converted according to final design

only one team remains alive
  -> remaining team wins
```

Boss requirements:

- A boss monster class is required.
- Boss monsters are tied to the team's lane group or final lane area.
- Boss death is authoritative and must be processed only once.
- A boss can have different targeting, immunity, or combat rules from normal monsters.
- The boss attacks enemies that reached the final defense line.
- Enemies that reached the final defense line attack the boss.
- Boss health should be visible to the team and probably to all teams.

Initial boss class design:

```text
BossMonster
  teamId
  maxHealth
  health
  phase
  eliminatedOnDeath
```

Potential boss states:

```text
SPAWNING
ALIVE
DEAD
REMOVED
```

Important rule:

- `ALIVE -> DEAD` is the only transition that can eliminate a team.

## Map, Region, And Lane Model

Each team arena should be created as a separate server-side runtime world with Fantasy, then filled by placing the same MapTemplate. The MapTemplate metadata should be the authoritative source for lane geometry and gameplay anchors inside one team's world.

MapTemplate supports named `TemplateRegion` entries. Each region has:

```text
marker
bounds
data
```

The planned region model:

```text
semion:team_spawn
  no required data

semion:lane_spawn
  data.lane = 1..5

semion:lane_path
  data.lane = 1..5

semion:lane_waypoint
  data.lane = 1..5
  data.order = 0..n

semion:boss_spawn
  no required data
```

Region rules:

- Team identity is not stored in region data. The Fantasy world instance is the team context.
- Each lane has one `semion:lane_spawn`.
- Each lane has one `semion:lane_path`.
- `semion:lane_path` is both the lane/combat area and the tower placement area.
- `semion:lane_waypoint` is optional and defines scripted movement points for lanes that need turns.
- Spawn monsters at `semion:lane_spawn`.
- Move monsters from `semion:lane_spawn` through ordered `semion:lane_waypoint` points, then to `semion:boss_spawn`.
- Spawn the team boss at `semion:boss_spawn`.
- Restrict player tower placement to the bounds of that lane's `semion:lane_path`.

Creator convention for the current map shape:

- Build each team map as an H-like arena.
- Treat the 12 o'clock side as the lane side.
- Treat the 6 o'clock side as the boss position.
- Connect each lane physically toward the boss side in the map layout.
- For 11, 1, 5, and 7 o'clock lanes, add `semion:lane_waypoint` regions at the turn points.

This keeps the creator workflow simple: one team map template can be authored once, then copied into each team's runtime world. Players see monsters enter a consistent lane, fight inside the same space where towers are placed, and leak toward the visible boss position.

## Monster Summon Model

Summoned monsters are the offensive side of the game.

Summon flow:

```text
player selects monster
-> check player gas
-> spend gas
-> increase player income
-> find living teams except sender team
-> choose one target team randomly
-> choose one active lane in the target team
-> enqueue or spawn monster in that lane
```

Target team selection:

```text
eligibleTeams = aliveTeams - senderTeam
targetTeam = random(eligibleTeams)
```

Rules:

- Sender team is never eligible.
- Eliminated teams are never eligible.
- If there is no eligible team, the game should already be ending or ended.
- Initial implementation should use pure random targeting.
- Weighted targeting can be considered later if pure random creates bad pacing.

Monster definition:

```text
SummonMonsterType
  id
  displayName      // editable from SummonDisplayNames
  gasCost
  incomeGain
  tier             // T1 through T5
  roles            // RUSH, TANK, SWARM, DISRUPTOR, SUPPORT, SIEGE
  maxHealth
  armor
  resistance
  attackDamage
  attackKind
  damageType       // PHYSICAL, MAGIC, TRUE
  entityTypeId
  blockbenchModelId
  dimensions
  mineralReward
  abilityActivations // PASSIVE, CONDITIONAL, COOLDOWN
```

Monster instance:

```text
Monster
  type
  targetTeam
  targetLane
  lanePosition
  health
  armor
  resistance
  damageType
  summonTier
  summonRoles
  state
  ownerPlayer
  senderTeam
```

Confirmed summon design policy:

- Summon monsters are ability-focused rather than pure stat upgrades.
- Authoring guide: `docs/summon-authoring-guide.ko.md`
- Summon display names are centralized in `SummonDisplayNames` so visible names can be renamed without hunting through each summon class.
- Roles:
  - `RUSH`: fast pressure, low defense.
  - `TANK`: high armor or high resistance, designed to draw fire and protect support units.
  - `SWARM`: many weak bodies, anti-single-target pressure.
  - `DISRUPTOR`: indirect low-tier disruption, high-tier tower debuffs.
  - `SUPPORT`: monster healing, shielding, speed, or defense support.
  - `SIEGE`: slow high-threat boss/final-line pressure.
- Price tiers:
  - `T1`: 10-35 gas, high income efficiency, simple abilities.
  - `T2`: 40-80 gas, tactical units and low-tier support/disruptor cooldowns.
  - `T3`: 90-160 gas, composition core and normal cooldown ability access.
  - `T4`: 180-320 gas, high-threat specialist units.
  - `T5`: 350+ gas, decisive late-game pressure.
- Income efficiency falls as tier and combat utility rise.
- Low-tier `SUPPORT` and `DISRUPTOR` may use cooldown abilities, but their effects should stay modest.
- Same buff/debuff effect uses only the strongest active source. Different effect types can coexist.
- Global effect caps:
  - tower attack-speed reduction: max 40%
  - tower range reduction: max 30%
  - monster damage reduction: max 35%
  - monster movement-speed bonus: max 30%
  - healing, shield, and target-priority manipulation use only the strongest active source.

Confirmed summon targeting policy:

```text
targetScore = laneProgress * 100 + rolePriority + threatBonus
```

Role priorities:

```text
SWARM      0
RUSH       5
SIEGE      15
SUPPORT    35
TANK       45
DISRUPTOR  45
```

Additional rule:

- `SIEGE` gains +30 target score at 80% lane progress or later.
- `TANK` intentionally outranks `SUPPORT` at the same lane progress so tank summons can screen support summons.

Confirmed damage policy:

- `PHYSICAL` damage is reduced by `armor`.
- `MAGIC` damage is reduced by `resistance`.
- `TRUE` damage ignores both.

Monster states:

```text
SPAWNING
ALIVE
DEAD
REACHED_BOSS
REMOVED
```

## Lane And Defense Model

Each team owns up to five player lanes.

Lane responsibilities:

- Spawn incoming summoned monsters.
- Spawn configured round-wave monsters.
- Move monsters along the lane.
- Hold player towers.
- Move remaining tower-produced entities and tower summons to the final defense line after the player lane is cleared.
- Detect when leaked monsters reach the final defense line.

Initial lane model:

```text
PlayerLane
  teamId
  laneId
  ownerPlayer
  laneRegion
  summonedMonsterSpawnQueue
  waveMonsterSpawnQueue
  activeMonsters
  towers
```

Team lane group model:

```text
TeamLaneGroup
  teamId
  lanes
  finalDefenseLine
  boss
```

Pathing recommendation:

- Use scripted lane progress from `semion:lane_spawn` through ordered `semion:lane_waypoint` points to `semion:boss_spawn` instead of vanilla mob pathfinding.
- This is more predictable for TD gameplay and easier to balance.

## Tower Model

Towers are defensive objects owned by players.

Tower responsibilities:

- Default behavior is to attack monsters in the owner's player lane.
- If the current target is outside the tower's attack range but still inside its acquire range, the tower can reposition toward that target.
- Towers are still static-style defense entities in the sense that they do not use free pathfinding or lane roaming; they only make direct combat reposition steps when needed.
- Some towers can produce entities directly.
- Some towers can produce summons or temporary combat entities.
- Spend player mineral when built or upgraded.
- Award kill mineral to the tower owner, unless later changed.

Tower definitions are hardcoded in Java. Wave monsters and economy values are config-driven, but tower behavior and tower classes are code-defined so each tower can have distinct Minecraft-specific behavior.

Initial tower categories:

- `DirectTower`: directly attacks enemies.
- `ProducerTower`: produces persistent or semi-persistent defender entities.
- `SummonerTower`: creates temporary summoned combat entities.
- `SupportTower`: applies buffs, slows, debuffs, or other support effects.

Tower definition:

```text
TowerType
  id
  displayName
  mineralCost
  range
  damage
  attackIntervalTicks
  targetingMode
  upgradePath
```

Tower instance:

```text
Tower
  type
  ownerPlayer
  teamId
  laneId
  position
  cooldownTicks
  level
```

Test tower upgrade model:

```text
test_direct
  -> sniper
  -> guard
```

Rules:

- Test tower upgrades are branch-based evolutions, not flat stat increments.
- Upgrading replaces the live tower entity with a new tower type at the same tile.
- The base test tower exposes its own upgrade options.
- Upgrade cost is paid in mineral.
- Initial operator-facing flow:
  - `/semiontd tower upgrades`
  - `/semiontd tower upgrade <id>`

Initial targeting mode:

- Target the enemy with the highest target score.
- Current target score starts from lane progress and adds role-based threat for summoned attack monsters.

Reason:

- This keeps the tower defense expectation that near-leaking enemies are dangerous, while still letting tanks, disruptors, supports, and siege units affect targeting.

## Round Model

The game has configured rounds. Each round has a preparation/summon phase, a lane wave phase, a final defense phase if needed, and a post-round income payout.

Round responsibilities:

- Run player placement and attack monster summon time for about 20-30 seconds.
- Spawn configured monster waves into each player lane.
- Track whether each active player lane has cleared its wave.
- Move surviving tower-produced entities and tower summons from cleared lanes to the team's final defense line during the current round.
- Let leaked monsters keep moving along their lane when the lane fails to clear.
- Let monsters that reach the boss position fight the final boss through normal entity combat.
- After the 90-second wave timeout, force remaining lane monsters and lane towers toward the final defense area.
- Wait until every non-eliminated team has cleared the round or has been eliminated.
- Trigger income payout.
- Keep all registered attack summons available; there is no tier unlock gate in the current policy.
- From round 20 onward, switch to an infinite repeated monster wave if configured.

Round payout:

```text
round payout
  -> after every non-eliminated team has cleared or died
  -> each living player's mineral += player.income
```

Round phase flow:

```text
PREPARE_AND_SUMMON
  -> players place/upgrade towers
  -> players summon attack monsters
  -> lasts about 20-30 seconds

LANE_WAVE
  -> configured wave monsters spawn in each active player lane
  -> towers, tower-produced entities, and tower summons defend each lane
  -> after the 90-second wave timeout, remaining monsters and lane towers are forced toward final defense

LANE_CLEARED
  -> if a lane clears all wave monsters, remaining tower-produced entities and tower summons move to the team's final defense line

LANE_LEAKED
  -> if a lane fails to clear, remaining enemy monsters continue moving along the lane

FINAL_DEFENSE
  -> monsters that reach the boss position remain active and attack the team boss
  -> the team boss attacks and can kill reached monsters
  -> the system detects boss death and team elimination

ROUND_WAIT
  -> teams that cleared wait until all other living teams also clear or die

ROUND_PAYOUT
  -> living players gain mineral equal to income
  -> next round starts
```

Open decisions:

- Exact per-tower summon limit values and cooldown rules for summoning towers.

Current recommendation:

- Keep round payout simple first.
- Configured automatic waves are part of the core game.
- Configured wave monsters and summoned attack monsters share the runtime `Monster` model, but summoned monsters add role, tier, and ability metadata.
- Boss-reaching monsters should use normal entity combat rather than instant lane-resolution damage.
- Summoning tower limits should stay local to tower definitions so special towers can have different caps.

## Wave Config Model

Monster waves must be editable without changing Java code.

Recommended config files:

```text
config/semion-td/waves.json
config/semion-td/economy.json
```

`waves.json` should define the monsters that spawn in each player lane for each round.

Round 1-19 can be explicitly configured. Round 20 and later can use one infinite repeated monster type.

Wave monster fields:

```text
WaveMonsterEntry
  id
  health
  armor
  attackDamage
  attackKind        // MELEE or RANGED
  entityType        // vanilla or modded entity id, such as minecraft:zombie
  blockbenchModelId // optional Blockbench visual model identifier
  dimensions        // optional gameplay hitbox: { "width": 0.6, "height": 1.95 }
  count
```

Visual selection rule:

- If `blockbenchModelId` is set, load the model through Blockbench Import Library (BIL) and attach a `LivingEntityHolder` to the runtime entity.
- Otherwise use `entityType`.
- A config entry must provide at least one of `entityType` or `blockbenchModelId`.
- `dimensions` is explicit gameplay data. It is not inferred from a Blockbench model and defaults to `0.6 x 1.95` when omitted.
- Runtime combat entities expose `idle`, `walk`, and `attack` animation states so Blockbench animation playback can be connected at the visual layer.

Draft `waves.json` shape:

```json
{
  "rounds": [
    {
      "round": 1,
      "lanes": {
        "default": [
          {
            "id": "basic_melee_1",
            "health": 35,
            "armor": 0,
            "attackDamage": 4,
            "attackKind": "MELEE",
            "entityType": "minecraft:zombie",
            "blockbenchModelId": null,
            "dimensions": { "width": 0.6, "height": 1.95 },
            "count": 12
          }
        ],
        "lane_1": [],
        "lane_2": [],
        "lane_3": [],
        "lane_4": [],
        "lane_5": []
      }
    }
  ],
  "infiniteFromRound": 20,
  "infinite": {
    "lanes": {
      "default": [
        {
          "id": "infinite_melee",
          "health": 400,
          "armor": 8,
          "attackDamage": 25,
          "attackKind": "MELEE",
          "entityType": "minecraft:husk",
          "blockbenchModelId": null,
          "dimensions": { "width": 0.6, "height": 1.95 },
          "count": 30
        }
      ]
    }
  }
}
```

Lane config rule:

- `default` applies to every active player lane.
- `lane_1` through `lane_5` can override or extend the default, depending on final implementation.
- Initial recommendation: if a specific lane key is present and non-empty, use that lane list instead of `default`.

Draft `economy.json` shape:

```json
{
  "startingMineral": 200,
  "startingGas": 50,
  "startingIncome": 0,
  "gasCap": {
    "base": 500,
    "roundOffsetMultiplier": 2,
    "roundOffsetStep": 20,
    "flatBonus": 10
  },
  "gasProduction": {
    "initialGasPerSec": 1,
    "maxUpgradeCount": 20,
    "initialUpgradeCost": 50,
    "upgradeCostIncrease": 25,
    "gasPerSecIncrease": 1
  }
}
```

Gas cap config interpretation:

```text
base + roundOffsetMultiplier * ((round - 1) * roundOffsetStep) + flatBonus
```

## Tower-Produced Entities And Summons

Defender entities are not a separate direct-purchase system. In this plan, `DefenderEntity` is a shared runtime term for entities produced through a player's towers and summons created by towers.

This includes:

- Entities produced by a tower.
- Summoned combat entities produced by a tower.
- Temporary or persistent tower-created defenders.

Defender entity responsibilities:

- Help defend a player's lane during the lane wave.
- Survive after lane clear if not killed.
- Move to the team's final defense line after that player lane is cleared.
- Help cover the final defense area during the current round.
- Reset and be removed when the next round starts.

Initial model:

```text
DefenderEntity
  ownerPlayer
  sourceTower
  teamId
  currentLaneId
  state
  health
  attackDamage
  attackKind
  persistent
```

Potential states:

```text
DEFENDING_LANE
MOVING_TO_FINAL_DEFENSE
DEFENDING_FINAL_LINE
DEAD
REMOVED
```

## Game Loop

Tick-based loop:

```text
every server tick:
  - advance current round phase
  - during prepare phase, accept placement and summon actions
  - during wave phase, spawn scheduled wave monsters
  - move lane monsters
  - run tower attacks when cooldown allows
  - process monster deaths
  - process lane clear state
  - move surviving tower-produced entities and tower summons from cleared lanes to final defense line
  - process monsters reaching the final defense line
  - let final defense combat continue normally
  - process boss deaths
  - check team elimination
  - check whether all living teams cleared or died
  - check victory

every second:
  - add gas from gasPerSec

after all living teams cleared or died:
  - advance round
  - pay mineral equal to income
  - start the next prepare phase
```

Player actions:

```text
build tower
upgrade tower
sell tower
summon monster
upgrade gas production
select job, later
use job skill, later
```

## Initial Implementation Milestones

### Milestone 0: Build And Bootstrap

Purpose:

- Make the mod compile and load.
- Add only minimal initialization.

Implementation items:

- Fix current unfinished entrypoint.
- Add constants and logger.
- Add command registration entrypoint.
- Add server tick hook.

Completion criteria:

- `compileJava` passes.
- Server can load the mod.
- A debug command can confirm the mod is active.

### Milestone 1: Team And Economy Core

Purpose:

- Establish the 5:5:5:5 competition model and mixed currency model.

Implementation items:

- `SemionGame`
- `SemionTeam`
- `SemionPlayer`
- `PlayerEconomy`
- Team assignment command or debug setup
- Gas-per-second tick
- Round income payout
- Gas cap by current round
- Gas production upgrade config

Completion criteria:

- Four teams can exist.
- Up to five players can be assigned per team.
- Each player has mineral, gas, income, and gasPerSec.
- Gas increases every second.
- Gas cannot exceed the current round cap.
- Mineral increases by income at round payout.
- Starting values are mineral 200, gas 50, income 0.

### Milestone 2: Boss And Elimination

Purpose:

- Implement the actual team elimination condition.

Implementation items:

- `BossMonster`
- Boss health and death handling
- Team elimination state
- Victory check

Completion criteria:

- Each team has one boss.
- Killing a boss eliminates that team.
- Eliminated teams are excluded from future summon targeting.
- Last remaining team wins.

### Milestone 3: Monster Summoning

Purpose:

- Implement the Semion-style offensive economy loop.

Implementation items:

- `MonsterType`
- `Monster`
- `SummonShop`
- Random living enemy team selector
- Gas cost and income gain
- Spawn into one active player lane in the target team

Completion criteria:

- Player can spend gas to summon a monster.
- Summoning increases the player's income.
- Monster is sent to a random living enemy team.
- Monster is assigned to an active lane inside the target team.
- Eliminated teams are not targeted.

### Milestone 4: Configured Round Waves And Lane Movement

Purpose:

- Make configured waves and summoned monsters actually threaten player lanes.

Implementation items:

- `PlayerLane`
- `TeamLaneGroup`
- `WaveConfig`
- `RoundWave`
- `WaveMonsterEntry`
- Waypoint path movement
- Monster reaches boss/final lane logic
- Monster cleanup
- `waves.json` loading
- Infinite round behavior from round 20

Completion criteria:

- Round-wave monsters spawn from config.
- Wave config can define per-lane health, armor, attack damage, attack kind, visual entity/model, and count.
- Round 20 and later can repeatedly spawn one configured infinite monster type.
- Summoned monsters move through the target lane.
- Monsters can reach the final lane/boss area.
- Cleared lanes send surviving tower-produced entities and tower summons to the final defense line.
- Teams that cleared wait until all living teams have cleared or died.
- Monster death and removal are processed once.

### Milestone 5: Tower Core

Purpose:

- Add the defensive loop.

Implementation items:

- `TowerType`
- `Tower`
- Tower placement
- Tower targeting by lane progress
- Tower damage
- Kill mineral reward

Completion criteria:

- Player can spend mineral to build a tower.
- Tower attacks monsters in its owner's player lane.
- Kills grant mineral according to the current reward rule.
- Towers do not attack eliminated-team lanes.

### Milestone 6: Content Expansion

Purpose:

- Add enough content for a playable prototype.

Initial monster roles:

- Cheap income monster
- Fast monster
- Tank monster
- Armor monster
- Boss-pressure monster

Initial tower roles:

- Basic single-target tower
- Fast attack tower
- Area tower
- Slow tower
- Armor-piercing tower
- Entity-producing tower
- Summon-producing tower

Completion criteria:

- At least three summonable monsters.
- At least three buildable towers.
- Basic economy pacing is testable.

### Milestone 7: Jobs

Purpose:

- Add player identity and strategic differentiation.

Initial job concepts:

- Builder: cheaper towers or upgrade discount
- Quartermaster: better summon income or summon discounts
- Refiner: better gas production scaling
- Commander: temporary team defense buffs
- Disruptor: stronger sent monsters or targeting manipulation

Completion criteria:

- Player can select a job.
- Job has at least one passive effect.
- Job state is cleared when the game ends.

## Suggested Internal Class Names

Keep the early implementation game-object oriented and avoid heavy enterprise-style naming.

Recommended early names:

```text
SemionGame
SemionTeam
SemionPlayer
PlayerEconomy
PlayerLane
TeamLaneGroup
MonsterType
Monster
BossMonster
TowerType
Tower
DirectTower
ProducerTower
SummonerTower
SupportTower
DefenderEntity
SummonShop
IncomeTable
GasUpgrade
WaveConfig
RoundWave
WaveMonsterEntry
Job
```

Avoid starting with overly broad names such as:

```text
CombatSystem
EconomySystem
PlacementSystem
GameLifecycleSystem
```

These can be introduced later only if the implementation grows enough to justify them.

## Implementation Status

### 2026-04-23 Skeleton Pass

Implemented first compile-safe skeleton:

- Fixed Fabric entrypoint bootstrap.
- Added config model and default config generation:
  - `economy.json`
  - `wave.json`
  - `map.json`
  - `progression.json`
  - legacy `waves.json` is still accepted when `wave.json` does not exist
- Added game state model:
  - `SemionGame`
  - `SemionGameManager`
  - `SemionTeam`
  - `SemionPlayer`
  - `PlayerEconomy`
  - `PlayerLane`
  - `TeamLaneGroup`
- Added round phase skeleton:
  - `WAITING`
  - `PREPARE_AND_SUMMON`
  - `LANE_WAVE`
  - `ROUND_PAYOUT`
  - `ENDED`
- Added monster, boss, and defender runtime models:
  - `entity.monster.Monster`
  - `entity.monster.SemionMonsterEntity`
  - `entity.boss.BossMonster`
  - `entity.defender.DefenderEntity`
- Added Polymer-backed server entity type:
  - registered custom `semion-td:monster`
  - marks the entity type as Polymer server-side only
  - wave monsters, summoned attack monsters, and towers now carry either vanilla `entityType` or `blockbenchModelId`
  - wave monsters and summoned attack monsters can declare per-monster gameplay hitbox dimensions
  - vanilla `entityType` is used as the client-visible Polymer replacement
  - Blockbench visuals keep their model id and attach through BIL `AnimatedEntity`/`LivingEntityHolder` when a `.bbmodel` or `.ajmodel` resource exists
  - monster and tower entities expose `IDLE`, `WALK`, and `ATTACK` animation state transitions from their AI goals
- Added MapTemplate/Fantasy arena skeleton:
  - `map.json` default config generation
  - creates one Fantasy temporary runtime world per team
  - loads and places the configured MapTemplate into each team world
  - parses team-world MapTemplate regions into arena layout data
  - binds player lanes to `semion:lane_spawn`, one `semion:lane_path`, optional ordered `semion:lane_waypoint`, and `semion:boss_spawn`
  - treats `semion:lane_path` as both the lane/combat area and tower placement area
  - spawns Polymer monster entities in the target team's arena world at lane spawn regions
  - moves monster entities along scripted lane waypoint paths from lane spawn to boss spawn
  - does not keep a separate leaked-monster final-defense tracking list
- Added hardcoded tower base classes:
  - `Tower`
  - `DirectTower`
  - `ProducerTower`
  - `SummonerTower`
  - `SupportTower`
- Added debug command root:
  - `/semiontd create`
  - `/semiontd testmode <enabled>`
  - `/semiontd join <team>`
  - `/semiontd autojoin`
  - `/semiontd start`
  - `/semiontd status`
  - `/semiontd ui`
- Added roster selection architecture:
  - `MatchMode`
  - `StartCandidate`
  - `AssignedParticipant`
  - `ParticipantSelectionPlan`
  - `ParticipantSelectionService`
  - start-time roster lock with spectator tracking
  - only selected start participants become active `SemionPlayer` entries
  - inactive teams stay out of victory and summon targeting
  - normal mode now uses the smallest team count that keeps teams at or under 5 players, then distributes players as evenly as possible
  - example compositions now include `5/4`, `4/4/4`, and `4/4/4/4`
- Added start placement handling:
  - `StartPlacement`
  - active players are teleported into their team Fantasy world at `team_spawn` with lane-based offsets
  - spectators are moved into spectator mode and teleported above the first active team arena spawn
- Added boss entity handling:
  - registered Polymer-backed `semion-td:boss`
  - added `SemionBossEntity`
  - active teams now spawn a boss entity at `boss_spawn` on match start
  - runtime `BossMonster` health is synchronized with the spawned boss entity reference
  - boss entity cleanup is tied to team elimination and deactivation
- Replaced direct monster `setPos` lane movement with goal-driven movement:
  - added `LaneFollowGoal` for waypoint-to-boss path traversal
  - monsters now follow lane waypoints through AI goals instead of per-tick position forcing
  - added `BossAttackLaneMonsterGoal` so bosses actively attack nearby lane monsters
  - lane runtime state now syncs from live entity position and health
- Added vanilla scoreboard team bridge:
  - creates `semion_red`, `semion_blue`, `semion_green`, `semion_yellow`, and `semion_spectator`
  - `/semiontd join <team>` now stores next-start team preference through vanilla scoreboard teams
  - `/semiontd autojoin` and `/semiontd start` build the active roster from online players and mode rules
  - extras beyond the active-cap or test-mode roster are assigned to `semion_spectator`
  - changing test mode is blocked once the roster is locked
- Added first summon loop:
  - `SummonShop`
  - `SummonMonsterType`
  - `SummonRegistry`
  - `SummonContext`
  - `SummonResult`
  - default `GruntSummon`
  - summon definitions are code classes, not `summons.json` entries
  - summon classes can override monster creation and post-summon hooks for special abilities
  - summon classes can define a vanilla entity visual or Blockbench model id
  - summon definitions now carry `SummonTier`, `SummonRole`, `DamageType`, `resistance`, and ability activation metadata
  - registered baseline summon content now covers rush, swarm, armor tank, resistance tank, disruptor, support, and siege roles
  - runtime summon monsters keep role/tier metadata for target-priority scoring
  - target priority now uses lane progress plus role priority, with tank summons outranking support summons at equal progress
  - physical, magic, and true damage handling exists on runtime monsters
  - summon only allowed during `PREPARE_AND_SUMMON`
  - spends gas
  - increases income
  - targets a random living enemy team
  - assigns a random active target lane
- Added debug commands:
  - `/semiontd economy`
  - `/semiontd profile`
  - `/semiontd job list`
  - `/semiontd job current`
  - `/semiontd job select <id>`
  - `/semiontd tower test`
  - `/semiontd tower upgrades`
  - `/semiontd tower upgrade <id>`
  - `/semiontd gasup`
  - `/semiontd summon <id>`
  - `/semiontd summons`
  - `/semiontd killboss <team>`
- Added first real buildable tower implementation:
  - hardcoded `test_direct` tower type
  - placement only during `PREPARE_AND_SUMMON`
  - placement uses the executing player's current block position
  - placement is restricted to the player's lane `semion:lane_path` bounds
  - placement spends mineral and rejects overlapping tower positions
  - placement spawns a real server-side Polymer tower entity
  - that tower entity attacks nearby monsters in the same lane through its own goal
- Added first lane-defense retaliation loop:
  - lane monsters can now acquire nearby lane defense entities as combat targets
  - the current real lane defense entity is the Polymer test tower entity
  - killed tower entities are removed from the owning lane state
- Added round-state tower transitions:
  - when a lane finishes clearing all of its monsters, that lane's towers are immediately reassigned into the lane's `semion:final_defense_tower` slot region
  - final defense slots are read from region bounds and ordered by distance to `boss_spawn`
  - when the next round enters prepare phase, towers reset back to their original lane positions
  - tower round reset restores health and clears cooldown state
- Added tower aggro priority:
  - `TowerType` now carries `aggroPriority`
  - monsters choose lane defense targets by `aggroPriority desc`, then distance
  - this lets a tower intentionally become the preferred enemy target
- Updated test tower combat movement:
  - if a target is outside attack range but inside target acquire range, the Polymer test tower entity now repositions horizontally toward that target
  - tower movement keeps `noGravity` so the tower remains a controlled server-side defense entity instead of falling like a normal mob
  - attack execution now falls back to direct health reduction when vanilla mob-damage hooks do not apply reliably in the server-side TD context
- Added branch-based test tower evolution:
  - `TowerType` now carries explicit `upgradeOptions`
  - base test tower can evolve into `test_sniper` or `test_guard`
  - upgrade replaces the runtime tower and Polymer tower entity in place
  - `/semiontd tower upgrades` lists available evolutions for the tower at the player's current block
  - `/semiontd tower upgrade <id>` applies the chosen evolution during prepare phase
- Added first job framework:
  - `SemionJob`
  - `JobRegistry`
  - default `semion-td:recruit` job
  - pre-start job selection
  - starting economy, tower, summon, kill reward, and lifecycle modifier hooks
- Added match progression persistence:
  - `ProgressionService`
  - per-player profile store at `profiles.json`
  - win/loss/play reward application after match end
  - match result snapshots include winner state, team, kills, income, summons, and kill minerals
- Added first status UI layer:
  - DisplayHud-based in-match status HUD
  - DialogUtils status dialog through `/semiontd ui`
  - DialogUtils match result dialog at match end
- Added current game-policy enforcement:
  - eliminated teams immediately discard active and queued lane monsters without kill rewards
  - summoned attack monsters never target eliminated teams
  - all registered attack summons are available without round-based tier unlock
  - tower-produced defender entities are removed on next-round reset
  - monsters reaching the boss position remain active and fight the boss through normal entity combat
  - bosses can attack and kill reached monsters
  - 90-second wave timeout forces remaining lane monsters and lane towers toward the final defense area

Verified:

- Gradle wrapper files are present and executable:
  - `gradlew`
  - `gradlew.bat`
  - `gradle/wrapper/gradle-wrapper.jar`
  - `gradle/wrapper/gradle-wrapper.properties`
- `./gradlew --version` runs Gradle 9.2.1.
- `./gradlew build` succeeds with Gradle 9.2.1 on 2026-04-28.
- Fabric GameTest is configured through Loom `configureTests`.
- 54 required server-side Fabric GameTests pass through the `runGameTest` task during `./gradlew build`.
- GameTest logs are written under `build/run/gameTest/logs/`.
- Current verified GameTest coverage includes:
  - 1v1 selection in `TEST` mode
  - `4 -> 2/2`
  - `6 -> 3/3`
  - `8 -> 4/4`
  - `9 -> 5/4`
  - `12 -> 4/4/4`
  - `16 -> 4/4/4/4`
  - normal-mode rejection of one-player-team rosters
  - preferred-team preservation
  - vanilla scoreboard team registration
  - lane-based active spawn offset calculation
  - spectator spawn offset calculation
  - boss entity spawn on match start
  - boss entity cleanup on boss death
  - test tower placement success and mineral spending
  - test tower out-of-bounds placement rejection
  - test tower evolution success and mineral spending
  - chained test tower evolution to `deadeye` and `bastion`
  - test tower rejects unknown evolution id
  - test tower entity damages lane monsters
  - test tower moves toward an out-of-range monster
  - lane monster damages test tower entity
  - monster prefers higher aggro priority tower
  - cleared lane moves test tower to final defense
  - round reset returns test tower to its lane position
  - tower-produced defender entities reset and are removed on the next round
  - monsters reaching the boss position stay active, damage the boss through combat, and can be killed by the boss
  - 90-second wave timeout forces remaining lane monsters and lane towers toward final defense
  - synthetic arena exposes ordered 7x7 final defense slots
  - roster lock on game start
  - configured starting economy
  - gas tick and gas cap behavior
  - gas production upgrade cost and gas-per-second increase
  - round payout for living players only
  - eliminated players stop receiving economy ticks
  - summon gas spending, income gain, no-target refund, eliminated-team target exclusion, class-based summon registry defaults, no `summons.json` generation, custom summon class hooks, Blockbench summon visual preservation, role/tier catalog metadata, runtime summon combat metadata, damage-type defense handling, and summon target-priority scoring
  - wave monster Blockbench visual preservation and idle/walk animation state exposure
  - tower vanilla visual fallback, BIL Blockbench model id preservation, and attack animation state exposure
  - eliminated target teams discard active and queued lane monsters without kill rewards
  - wave monster kill reward for the tower owner
  - defender last-hit reward is paid once
  - boss and unknown deaths do not grant mineral
  - selected job starting economy modifiers
  - progression profile persistence
  - game manager finalizes ended matches and clears active game state

Known verification notes:

- Polymer/DialogUtils resource-pack generation can log vanilla client jar access errors in the local GameTest environment, but the server continues and all 54 required GameTests pass.
- A later `./gradlew tasks --all` attempt hit a sandbox permission error while opening the Gradle wrapper distribution lock file in the user Gradle cache. This did not affect the successful `./gradlew build` run.

Not implemented yet:

- Actual exported `data/semion-td/map_template/arena.nbt` asset.
- Actual exported `data/semion-td/map_template/lobby.nbt` asset.
- Full summon unit catalog. A class-based summon registry exists with full T1 role coverage and one T5 siege baseline, but the final T1-T5 production catalog is not complete.
- Full production tower catalog. Current player-facing tower gameplay still uses hardcoded test tower content.
- Full job catalog. The framework exists, but only the default `recruit` job is registered in production code.
- BIL/Blockbench visual binding for bosses and job/theme presentation. Monster, summon, and test tower entities now use BIL holders when model resources are present.
- Sidebar-style UI polish. DisplayHud and Dialog UI exist, but a final production UI pass is still needed.
- Full 20-player game flow testing.

## Open Design Questions

Some earlier questions have now been fixed by code. They are recorded here so future planning does not reopen them accidentally.

1. What are the final replacement names for mineral, gas, and income?
2. Which concrete producer/summoner tower types should create `DefenderEntity` or summoned entities?
3. What exact per-tower summon limits and cooldowns should summoning towers use?

Settled in current code:

- Prepare/summon phase duration is 25 seconds.
- Economy values are integer `long` values.
- Starting gas-per-second is `1`.
- Kill mineral currently goes to the tower or defender last-hit owner. Boss and unknown deaths do not grant mineral.
- Eliminated players become spectators for the current match.
- When a team is eliminated, active and queued monsters targeting that team's lanes are discarded without kill rewards.
- Wave monsters remain config-driven; summoned attack monsters are class-driven through `SummonRegistry`.
- Summoned attack monsters use the six confirmed roles: `RUSH`, `TANK`, `SWARM`, `DISRUPTOR`, `SUPPORT`, and `SIEGE`.
- Wave monsters and summoned attack monsters declare gameplay hitbox dimensions explicitly; Blockbench model scale is visual-only and does not drive server collision.
- Summoned attack monsters use five gas-price tiers from `T1` through `T5`.
- T1 now has baseline coverage for all six summon roles: `grunt`/`Zip Bun`, `skitter_swarm`/`Button Mites`, `quilt_guard`, `static_bobbin`, `button_nurse`, and `popper_pod`.
- The existing tank-like siege Blockbench model has been promoted to the T5 `siege_breaker` slot and uses `semion-td:summon/t5_siege`.
- Summon targeting uses lane progress plus role priority. `TANK` outranks `SUPPORT` at equal progress, and `SIEGE` gains extra priority near the boss line.
- Runtime monster damage supports `PHYSICAL`, `MAGIC`, and `TRUE`; armor reduces physical damage, resistance reduces magic damage, and true damage ignores both.
- Summoned monsters target a random living enemy team and then a random active lane on that team.
- All registered attack summons are available; there is no tier unlock gate in the current policy.
- Simultaneous elimination uses normal tick order and has no special tie-break rule.
- Tower-produced defender entities reset at the next round.
- Summon monster limits are managed by the summoning tower implementation.
- Monsters that reach the boss position remain active and fight the boss through normal entity combat.
- Bosses can attack and kill reached monsters.
- Wave timeout is currently 90 seconds and forces remaining lane monsters and lane towers toward the final defense area.
- `lane_N` wave entries are lane-specific overrides; default entries are used when a lane-specific entry is absent.
- Gas production upgrade currency is config-driven and defaults to mineral.
- Boss defaults are implemented in `BossMonster.defaultBoss`.
