# SemionTD Builder and Tower Implementation Reference

This reference describes the current SemionTD builder/job and production-tower architecture for Minecraft 1.21.8, Fabric, and Java 21. Re-read the named symbols before changing them; paths and signatures can move.

## Contents

1. [Architecture map](#1-architecture-map)
2. [Discovery and preflight](#2-discovery-and-preflight)
3. [Builder and job lifecycle](#3-builder-and-job-lifecycle)
4. [Tower family and catalog construction](#4-tower-family-and-catalog-construction)
5. [Runtime state, combat, and targeting](#5-runtime-state-combat-and-targeting)
6. [Area effects, visuals, and VFX](#6-area-effects-visuals-and-vfx)
7. [Balance configuration and reload](#7-balance-configuration-and-reload)
8. [Descriptions, dialogs, and timed effects](#8-descriptions-dialogs-and-timed-effects)
9. [Web catalog ownership](#9-web-catalog-ownership)
10. [Testing and delivery](#10-testing-and-delivery)
11. [Failure patterns and completion checklists](#11-failure-patterns-and-completion-checklists)

## 1. Architecture map

The main package is `src/main/java/kim/biryeong/semiontd`.

| Concern | Current authority | Purpose |
|---|---|---|
| Builder contract | `job/SemionJob.java` | immutable identity, lifecycle, economy, tower permission, reward/summon hooks |
| Builder registration | `job/JobRegistry.java` | built-in singleton registration and lookup |
| Match lifecycle | `game/SemionGame.java` | selection, match/round callbacks, elimination, runtime shutdown |
| Reward lifecycle | `game/EconomyService.java` | kill reward calculation and `onMonsterKilled` callback |
| Tower definition | `tower/TowerType.java` | stable ID, display metadata, core stats, visual, upgrade options |
| Family definition | `tower/<family>/*Towers.java` | tower constants, tiers/roles, description templates |
| Family catalog | `tower/<family>/*TowerCatalogs.java` | runtime-resolved entries, factories, starters, upgrade edges |
| Global catalog | `tower/ProductionTowerCatalogs.java` | clears and rebuilds all family registrations |
| Placement/upgrade | `tower/ProductionTowerService.java` | shared validation, currency spend, construction, replacement, actions |
| Runtime tower | `tower/Tower.java`, `EntityBackedTower.java`, `ProductionTower.java`, `SupportTower.java` | lifecycle, stats, combat hooks, entity backing, active support execution |
| Balance schema | `config/TowerBalanceConfig.java` | source defaults, merge, validation, tower/upgrade/ability schemas |
| Config loading | `config/SemionConfigLoader.java` | read, migrate, merge, validate, write, last-known-good fallback |
| Runtime balance | `config/TowerBalanceRuntime.java` | resolved stats, ability access, upgrade costs, rendered descriptions |
| Combat entity | `entity/tower/SemionTowerEntity.java` | entity state, attacks, timed effects, runtime stat synchronization |
| Target goal | `entity/tower/goal/TowerAttackMonsterGoal.java` | candidate search, custom/fallback selection, forced targeting |
| Area effects | `api/area/*`, `tower/area/*` | filtered lane-aware area actions and damage attribution |
| Player details | `ui/SemionTowerInteractionService.java`, `ui/SemionDialogService.java` | tower detail dialog and visible changing state |
| Web export | `web/WebCatalogExporter.java` | resolved tower/builders/upgrades/abilities/descriptions/visuals |
| Unit tests | `src/test/java/kim/biryeong/semiontd` | pure state, config, catalog, damage, descriptions, export |
| Fabric GameTests | `src/gametest/java/kim/biryeong/semiontd` | live entity, lane, lifecycle, placement, upgrade, dialog, VFX behavior |

Use `OceanTowers` / `OceanTowerCatalogs` as a family with placement/resource behavior, `VillagerAdvTowerJob` / `VillagerAdvStates` as keyed state examples, and the closest current family for the mechanic being added. Never assume a family is exemplary in every dimension.

## 2. Discovery and preflight

### Confirm repository and toolchain

From the candidate repository root, verify:

```text
git status --short
test -f gradlew
test -d src/main/java/kim/biryeong/semiontd
test -f gradle.properties
```

Read `AGENTS.md` and inherited instructions. The current project targets Minecraft 1.21.8 and Java 21, but use the checked-out `gradle.properties` and build files as authority.

If `.codegraph/` exists, check its status before relying on it. Refresh only if allowed and actually stale. Ask CodeGraph narrow questions about builder registration, catalog flow, combat hooks, balance resolution, and test coverage; inspect the resulting source directly.

### Preserve the working tree

Treat every uncommitted file not created by the current task as user-owned. Before editing:

- identify changed and untracked files;
- avoid broad formatting or generated-source rewrites;
- do not reset, delete, or overwrite unrelated work;
- keep each changed line traceable to the requested builder/tower behavior.

### Determine the validation class

| Change | Minimum verification |
|---|---|
| pure formula or keyed state | focused JUnit test, then `test` |
| catalog, config merge, description, web export | focused JUnit test, then `test` |
| placement, upgrade, entity, attack, lifecycle, team lane, dialog, VFX | focused JUnit where useful, then `test` and `runGameTest` |
| resources, metadata, distributable mod | relevant suites plus `remapJar` or `build`; inspect the artifact when packaging matters |
| live balance or reload | source tests plus active-config inspection and a real `/semiontd reload` smoke check when the server is available |

## 3. Builder and job lifecycle

### SemionJob contract

`SemionJob` currently owns immutable builder metadata and overridable hooks:

- identity: `id`, `displayName`, `description`;
- lifecycle: `onSelected`, `onMatchStarted`, `onRoundStarted`, `onRoundEnded`, `onEliminated`;
- starting economy modifiers: mineral, gas, income, and gas-per-second values;
- summons: permission, modifiers, and lifecycle hooks;
- tower access: `canUseTower`;
- catalog ownership: `includesTowerInCatalog`;
- rewards: kill reward modifier and `onMonsterKilled`.

`JobContext` contains a non-null `SemionGame` and `SemionPlayer`.

### Lifecycle ordering

The current selection path applies starting economy and invokes `job.onSelected(...)` before `team.addPlayer(...)`. Therefore:

- do not query a lane or team attachment from `onSelected` unless the exact current caller guarantees it;
- defer lane-dependent initialization to `onMatchStarted`, a later hook, or a lane service;
- keep `onRoundStarted` and `onRoundEnded` safe for active, non-eliminated participants only;
- release player-scoped resources on `onEliminated` and on game/runtime shutdown.

`EconomyService` invokes `onMonsterKilled` when the reward is credited. Put reward-linked builder behavior there instead of duplicating kill detection inside towers.

### Registration

Add one immutable built-in job instance to `JobRegistry.registerBuiltIns()`. Jobs are globally registered singleton objects. Never put mutable per-player, per-match, or per-round fields directly on a job.

Use stable lowercase resource IDs. Changing a job or tower ID can break configs, persistence, web consumers, and recorded actions; treat ID changes as migrations.

### Mutable builder state

Use a keyed service such as `VillagerAdvStates` when state outlives one tower instance or is shared across a player's family. Key by the narrowest stable identity, normally player UUID, and define explicit operations rather than exposing a mutable map.

Required cleanup depends on lifetime:

1. clear stale state at `onMatchStarted`;
2. clear the player entry at `onEliminated`;
3. clear it from `SemionGame.closeRuntimeState()` if shutdown can occur without elimination;
4. test a second match or player reuse so leaked state is observable.

If state belongs to one tower and must survive upgrades, prefer `TowerDataKey<T>` for immutable/simple values. `Tower.copyFrom` shallow-copies the data map. Override `copyRuntimeStateFrom` for custom fields or mutable values that need an independent copy.

### Tower permission versus web ownership

`canUseTower(context, towerType)` answers runtime permission. `includesTowerInCatalog(towerType)` answers stable builder ownership for export and discovery. Its default delegates to `canUseTower(null, towerType)`, which is insufficient when permission depends on lane or match state.

Override `includesTowerInCatalog` when:

- `canUseTower` needs non-null runtime context;
- special temporary towers should be usable but not exported;
- a family has context-sensitive unlocks;
- delegating would make zero or multiple builders claim a tower.

## 4. Tower family and catalog construction

### TowerType definitions

`TowerType` is the immutable definition for:

- stable ID and display/category/description;
- placement mineral cost and maximum health;
- range, damage, attack interval, and aggro priority;
- `EntityVisual` or another supported visual description;
- upgrade options.

Core numeric validation occurs in `TowerType`. Use `ProductionTowerDefinitions.tower(...)` or the current sibling helper instead of duplicating builder boilerplate.

Keep family structure explicit:

- `<Family>Towers` declares constants and `all()`;
- tier/role predicates make ownership and tests readable;
- higher-tier types remain registered but are not starters;
- description templates are registered beside their types;
- visuals are data on the type unless the runtime state genuinely changes them.

`aggroPriority` controls how monsters prioritize the tower. It is not the tower's monster-targeting rule.

### Catalog factory contract

The catalog factory receives the resolved `TowerType`, owner/player identity, team, lane, original grid position, and current grid position. Preserve both positions. Movement/final-defense and upgrades depend on their distinction.

Family registration should follow this order:

```java
TowerType resolved = TowerBalanceRuntime.resolve(TYPE);
catalog.registerStarter(resolved, factory); // tier one only
catalog.register(resolvedHigherTier, tier, factory);
// Register every endpoint first, then link edges.
catalog.linkUpgrade(FROM.id(), UPGRADE_ID, TO.id(),
        TowerBalanceRuntime.upgradeCost(FROM.id(), UPGRADE_ID));
```

Use the current exact signatures rather than copying this illustrative snippet blindly.

### Global catalog wiring

`ProductionTowerCatalogs.reloadBuiltIns(config)` applies runtime balance, clears the catalog, ensures jobs exist, and registers every family. Add the family there exactly once. Reload tests should prove:

- the builder is registered;
- expected starter count changes intentionally;
- every tower type has one catalog entry;
- every upgrade endpoint resolves;
- every custom tower factory creates the intended runtime subclass.

### Shared placement and upgrades

`ProductionTowerService.placeTower` already checks prepare phase, lane position, occupancy, job permission, tower limit, and mineral spend. It only allows `starter()` entries.

Keep family placement on this path. Add a narrow family-specific predicate only for a real spatial rule, as the current Ocean water-placement check does. Do not build a second currency, occupancy, or action-recording pipeline.

The upgrade path:

1. resolves the selected option and target catalog entry;
2. checks job permission and runtime requirements;
3. spends the option's upgrade cost;
4. constructs the target with original and current positions;
5. calls `upgradedTower.copyFrom(previous, mineralCost)`;
6. replaces the tower, refreshes effects, and records the action.

Consequences:

- upgrade prices belong to the edge, not the destination placement price;
- upgrade IDs are stable configuration keys and may differ from target tower IDs;
- requirements should be expressed through the existing upgrade requirement surface;
- tests must verify retained state, statistics, sell value/economy, and position where relevant.

## 5. Runtime state, combat, and targeting

### Choose the narrowest base class

| Base | Use when |
|---|---|
| `ProductionTower` | ordinary entity-backed tower with the shared basic attack behavior |
| `EntityBackedTower` | custom entity-backed behavior that does not fit the production convenience class |
| `SupportTower` | cooldown-driven active support action through `execute(PlayerLane)` |
| `Tower` | only when no entity-backed behavior is needed and the current architecture supports it |

Default an ordinary basic-attack implementation to `ProductionTower`. Use the lower-level `EntityBackedTower` only when a concrete production-base behavior must be omitted or replaced; name that conflict before choosing it.

`EntityBackedTower` means a `SemionTowerEntity` represents the tower. It does not imply that `Tower.execute` performs basic attacks. Basic attacking is handled by the entity and `TowerAttackMonsterGoal`.

### Lifecycle hooks

Inspect current implementations before overriding:

- placement/removal/death: `onPlaced`, `onRemoved`, `onDeath`, `notifyDeath`;
- state/visual sync: `onStateChanged`;
- waves/rounds: `onWaveStarted`, `resetForRound`, `finishRoundReset`;
- movement: `moveToFinalDefense`;
- ticks/actions: `tick`, `execute`;
- team events: nearby monster/tower death hooks.

`PlayerLane.markWaveStarted()` marks each tower and invokes `onWaveStarted` before shared trait/resonance captures. Put mechanics that snapshot a wave's roster or links in the correct wave-start hook and test ordering.

`PlayerLane` already fans nearby monster and tower deaths across the appropriate notification lanes/team group. Reuse those hooks; do not scan every world entity after a death.

### Stat hooks

Use the existing stat pipeline for effects and abilities:

- attack: `modifyAttackDamage`, attack interval adjustments, final damage bonuses;
- range/movement: range and movement adjustment hooks;
- health/defense: base/final maximum-health effects and incoming-damage modifiers;
- outgoing damage: resolved attack/outgoing/applied-damage hooks.

When a runtime change affects entity combat stats, trigger the existing state/stat refresh path. Do not cache a derived stat in two places.

### Target selection

`TowerAttackMonsterGoal` gathers eligible candidates and delegates to the tower's custom selection hook. If custom selection returns empty, shared priority/distance fallback applies.

- override `selectAttackTarget` for a real candidate-selection rule;
- override `supportsForcedAttackTargeting` only when the mechanic intentionally participates in forced targeting;
- implement `selectForcedAttackTarget` consistently with that declaration;
- preserve final-defense and lane filters already enforced by the goal.

Do not reinterpret `aggroPriority` as tower targeting.

### Damage and kill attribution

Use `Tower.damageTargetResult(...)` or the current shared equivalent. This preserves:

- physical versus magic damage type;
- outgoing and applied-damage modifiers;
- round physical/magic statistics;
- source/owner attribution;
- kill hooks and team notification;
- shared VFX behavior.

Do not call `target.hurt(...)` directly for a tower mechanic.

Choose the callback by the value required:

| Requirement | Hook |
|---|---|
| attack was attempted | legacy `onAttack` behavior, only when actual outcome is irrelevant |
| actual resolved outgoing/dealt values | `onAttackResolved` |
| attributed kill | `onKill` or the builder reward hook, depending on ownership |
| nearby monster/tower death | lane-propagated nearby-death hooks |

Use `onAttackResolved` for lifesteal, stacking from dealt damage, overkill-sensitive behavior, or effects that must not trigger on zero dealt damage.

## 6. Area effects, visuals, and VFX

### Area-effect API

Use `SemionTdApi.areaEffects()` with `MonsterAreaEffectRequest` or `TowerAreaEffectRequest`. The service already enforces server-thread/lane/owner filters, collects outcomes, and emits shared area VFX.

Tower target modes currently include:

- `REGISTERED`: registered logical towers;
- `ENTITIES`: live tower entities;
- `REGISTERED_AND_CLONES`: registered towers plus clone-like entities.

Choose the mode from gameplay semantics. Do not compensate for a wrong mode with a manual world scan.

Use `TowerAreaDamage` for area damage. It routes each hit through the same damage/attribution pipeline as direct tower damage and preserves damage type, statistics, kill propagation, and relevant basic splash behavior.

### Visual definitions

Prefer an existing `EntityVisual`, special visual builder, or `BlockDisplayVisual` on `TowerType`. If state changes the visual at runtime:

1. override `Tower.visual()` only when necessary;
2. make the state transition explicit;
3. call `onStateChanged()` so the entity synchronizes;
4. add a GameTest or runtime assertion for the transition.

### VFX

Use `TowerVfxService` for attacks, secondary attacks, magic hits, area effects, and supported special events. It owns recipients, budgets, and fallback behavior.

Reuse an `AreaVfxStyles` value such as splash, pulse, corpse explosion, buff, debuff, dragon breath, or none before inventing a new renderer.

Every new builder/job must define its own `BuilderPalette` entry with a coherent primary color, accent color, vanilla fallback particle, and enhanced-client particle. Route every tower in the family to that palette in `TowerVfxService.paletteFor(...)`; do not accept `DEFAULT` fallback or another builder's palette as finished visual identity.

Verify that ordinary attacks and the builder's secondary, area, support, and special events consume the routed palette through the production `TowerVfxService` or area-effect path. A palette enum entry without routing or a production consumer is incomplete. Update `TowerVfxGameTest` to cover the family mapping and representative events, and add a deterministic debug command when the builder introduces a custom effect.

## 7. Balance configuration and reload

### Data ownership

`TowerBalanceConfig` currently groups:

- `towers`: core placement stats by tower ID;
- `upgradeCosts`: directed upgrade-edge prices;
- `abilities`: family/tower behavior parameters;
- special nested configuration such as clone queue or Villager ADV settings;
- `schemaVersion`.

Use core tower stats only for universal `TowerType` fields. Put mechanic-specific values under `abilities` with stable keys. Family-global values may use a stable family config ID rather than pretending to be a tower.

### Defaults and merge behavior

For every new value:

1. add a source default;
2. add it through the family defaults helper;
3. include it in `withMissingDefaults` behavior;
4. validate its semantic constraints;
5. test both an empty/default config and a partial existing config.

`withMissingDefaults` must preserve user-configured values while adding missing towers, costs, abilities, and nested defaults. Do not replace an existing map wholesale.

### Upgrade costs

Read prices with `TowerBalanceRuntime.upgradeCost(fromTowerId, upgradeId)`. The lookup supports a directed `from -> upgradeId` key and a legacy upgrade-ID fallback. Never substitute `target.mineralCost()`.

### Runtime values and descriptions

`TowerBalanceRuntime.resolve(type)` applies core stats and renders registered description templates. Runtime behavior should read abilities through the typed accessors such as `ability`, `abilityInt`, and `abilityTicks` rather than reparsing config.

Validate rules that generic non-negative checks cannot express:

- ratios and percentages stay within their intended interval;
- tick durations and counts are integral/positive where required;
- min/max or tier thresholds are ordered;
- radii and cooldowns are meaningful;
- denominators and logarithmic/curve parameters cannot produce invalid math.

Reuse an existing shared math helper when its semantics match. Do not add a configurable formula engine for one ability.

### Load, migrate, and reload

`SemionConfigLoader.loadOrCreateTowerBalance` reads JSON, performs known migrations, merges defaults, rejects unsupported newer schemas, validates, and writes merged additions. Parse or validation failure logs the problem and returns the last-known-good tower balance.

When changing schema:

- avoid a schema bump if adding optional keys that `withMissingDefaults` can backfill safely;
- bump and migrate only when old data changes meaning or shape;
- keep migration idempotent;
- test older/partial/newer/invalid inputs;
- prove invalid reload does not replace valid runtime data.

`/semiontd reload` reconfigures the built-in catalog and refreshes active game tower types and summon-shop data. A reloadable ability should not be copied once into a long-lived field unless an explicit refresh hook updates it.

### Live balance authority

For balance review or rebalance work, locate the actual server instance and read:

- `config/semion-td/tower_balance.json`;
- `config/semion-td/economy.json` when starting resources or income matter.

Report which config was used. If no active instance is available, label all numbers as source defaults. Keep implementation verification separate from payback, DPS, wave-clear, or economy conclusions.

## 8. Descriptions, dialogs, and timed effects

### Config-driven descriptions

Register numeric descriptions with `TowerDescriptionRegistry.registerTemplate`. `TowerDescriptionTemplate` supports:

- `{ability.key:format}` for the current tower/config ID;
- `{ability.config_id.key:format}` for a cross-config value;
- stat placeholders for mineral cost, max health, range, damage, attack interval/timing, and aggro priority;
- simple `*` and `/` expressions;
- formats including integer, percent, seconds, blocks, attack damage, health, aggro, range, attack speed, sell price, and generic number.

Use the exact current parser and format names. Keep formulas in config/runtime code; descriptions should render values, not become a second rules engine.

Every configured description test should resolve the type through `TowerBalanceRuntime.resolve(...)` and assert that no `{ability.` or other placeholder remains.

### Player-visible runtime state

The player path is:

```text
right-click tower
  -> SemionTowerInteractionService
  -> SemionDialogService.showTowerDetails
```

The detail dialog already shows current health, damage, attack speed, range, aggro, timed effects, runtime detail lines, sell price, description, and upgrades.

Use `Tower.runtimeDetailLines()` for changing mechanic state such as stacks, stored resource, next threshold, active mode, or wave snapshot. Keep lines short and directly actionable. Do not expose only a backend counter when the mechanic affects player decisions.

### Timed effects

`TimedEffectSet` supports three distinct ownership models:

| API model | Semantics | Typical use |
|---|---|---|
| unsourced `apply` | strongest magnitude wins; equal magnitude refreshes duration | one generic temporary buff/debuff |
| sourced `apply` / `refresh` | one contribution per source; contributions sum | multiple aura providers or tower-specific sources |
| persistent source effect | remains until replaced/removed | trait or persistent attachment |

Choose intentionally. Using unsourced effects for multiple auras silently loses stacking; using sourced effects without stable source IDs leaks or duplicates contributions.

`SemionTowerEntity` exposes timed-effect application/refresh/persistent APIs and refreshes combat stats for relevant changes. Maximum-health changes also require correct runtime/entity health synchronization.

If adding a new player-visible timed-effect type:

1. add the enum/type;
2. wire it into the correct stat calculation;
3. add a Korean/display label in the tower timed-effect dialog path;
4. test stacking, refresh/expiry, and visible output;
5. test upgrade/respawn behavior if the effect should survive either.

Do not assume persistent effects survive upgrades automatically: an upgrade creates a new runtime tower/entity. Verify the existing copy/refresh path or add the smallest explicit transfer.

## 9. Web catalog ownership

`WebCatalogExporter.snapshot()` exports resolved tower data including builder IDs, upgrade graph/costs, abilities, descriptions, and visuals.

For every registered catalog tower, it requires exactly one job where `includesTowerInCatalog(type)` is true. Zero owners and multiple owners are both errors.

For every new or changed family, verify:

- all public towers have exactly one builder;
- runtime-only/special towers are included or excluded intentionally;
- upgrade targets and costs serialize correctly;
- resolved descriptions have no placeholders;
- visual data remains serializable;
- `WebCatalogExporterTest` passes.

This check catches registration bugs that the in-game placement menu may not expose.

## 10. Testing and delivery

### Unit-test patterns

Family catalog tests should normally:

1. bootstrap the Minecraft registry/testing environment as current siblings do;
2. reset `TowerBalanceRuntime` and reload `ProductionTowerCatalogs` in setup/cleanup;
3. assert the job owns exactly the intended tower IDs;
4. assert starter and higher-tier classification;
5. assert the full upgrade graph and directed costs;
6. instantiate entries and assert custom runtime subclasses;
7. assert default and partial-config merging;
8. resolve descriptions and reject unresolved placeholders;
9. test family state/formula boundaries.

Useful focused suites include:

- `TimedEffectSetTest` for effect ownership and stacking;
- `TowerDamagePipelineTest` for damage and attribution;
- `TowerRuntimeDetailsTest` for player-visible mechanic lines;
- `WebCatalogExporterTest` for unique ownership and export integrity;
- current family `*TowerCatalogTest` files for registration/config conventions.

### GameTest patterns

Use:

- `SemionLifecycleGameTest` for builder lifecycle and economy hooks;
- `SemionParticipantGameTest` for built-in registration, starter counts, placement, upgrades, dialogs, match/round/team behavior;
- current builder-specific GameTests for effects or multi-lane behavior;
- `TowerVfxGameTest` for palette, attack, area, and special-event visuals.

When adding a built-in builder, update any explicit built-in builder list and expected starter count intentionally. Do not weaken counts into `>=` just to make a new registration pass.

### Commands

Run focused tests while iterating, then the complete required commands:

```text
./gradlew test --console=plain --no-daemon
./gradlew runGameTest --console=plain --no-daemon
./gradlew remapJar --console=plain --no-daemon
git diff --check
```

Use the repository-required wrapper, such as `rtk`, without changing the underlying validation intent.

`build.gradle` may load required local runtime patch jars from `run/mods`. If GameTest fails with a hard dependency/no-candidate error for those mods, inspect the declared runtime dependency and available patch artifact. Treat this as an environment/install blocker; do not change gameplay code or dependency metadata merely to hide it.

### Runtime smoke checks

When a server is available and the request includes live behavior:

1. install only the intended artifact/config;
2. start or reload the real runtime;
3. run `/semiontd reload` and inspect errors;
4. open the relevant builder/tower UI;
5. place, upgrade, and exercise the mechanic;
6. verify visible state, damage/economy/stat effects, and cleanup;
7. report concrete logs, test counts, or observed values.

Compilation is not a smoke test.

## 11. Failure patterns and completion checklists

### Common failure patterns

| Symptom | Likely cause | Correct surface |
|---|---|---|
| tower missing from placement | job permission, starter flag, or global catalog registration absent | job + family/global catalogs |
| web export says zero/multiple builders | unstable/default catalog ownership | `includesTowerInCatalog` |
| upgrade price equals target placement cost | wrong price source | directed runtime upgrade cost |
| state resets on upgrade | custom/mutable runtime state not copied or refreshed | `Tower.copyFrom` / `copyRuntimeStateFrom` / state service |
| state leaks into later match | singleton job owns mutable fields or keyed state not cleared | state service + lifecycle cleanup |
| AoE misses attribution/stats | direct entity damage or manual scan | area-effect API + `TowerAreaDamage` |
| ability triggers on blocked/zero damage | attempted-attack hook used | `onAttackResolved` |
| config reload changes descriptions but not behavior | value cached outside runtime access/refresh path | runtime accessor or explicit refresh |
| dialog omits mechanic state | backend-only counter | `runtimeDetailLines` or timed-effect label |
| raw `{ability...}` appears | template not registered/resolved or bad key/format | description registry/runtime resolve |
| GameTest dependency failure before tests | required `run/mods` patch jar absent | runtime installation, not gameplay code |

### New builder/family completion checklist

- [ ] Stable builder ID and immutable `SemionJob` registered.
- [ ] Lifecycle order reviewed; keyed mutable state has complete cleanup.
- [ ] Stable tower IDs, tiers, roles, visuals, and descriptions defined.
- [ ] Only intended tier-one towers registered as starters.
- [ ] Every catalog endpoint registered before upgrade links.
- [ ] Upgrade IDs/costs use runtime directed lookup.
- [ ] Shared placement, damage, targeting, area-effect, VFX, and state-copy paths reused.
- [ ] Dedicated `BuilderPalette` entry exists, every family tower resolves to it, and production attack/area/special VFX visibly consume it.
- [ ] Source defaults, partial merge, validation, and reload behavior covered.
- [ ] Changing state appears in tower details; new timed effects have labels.
- [ ] Exactly one web builder owns every exported tower.
- [ ] Focused tests, full unit suite, required GameTests, and packaging checks pass.
- [ ] Live config used for balance claims, or source-default assumption stated.

### Existing family change checklist

- [ ] Stable IDs and legacy config keys preserved or migrated.
- [ ] Every caller of the changed hook/service inspected.
- [ ] Upgrade/state transfer and reload behavior rechecked.
- [ ] Existing configured values remain authoritative after merge.
- [ ] Player UI and web export reflect the new behavior.
- [ ] One regression test fails before the fix and passes after it where practical.
- [ ] No unrelated source or formatting changes included.
