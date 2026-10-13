# SemionTD Builder and Tower Implementation Reference

This reference describes the current SemionTD builder/job and production-tower architecture for Minecraft 26.3, Fabric, and Java 25. Re-read the named symbols before changing them; paths and signatures can move.

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
| Builder contract | `job/SemionJob.java` | immutable identity, economy and permissions; common public event entrypoints |
| Builder registration | `job/JobRegistry.java` | built-in singleton registration and lookup |
| Match lifecycle | `game/SemionGame.java`, `job/SemionJob.java`, `job/*TowerJob.java` | event hooks overridden per job and ordered shutdown |
| Lane lifecycle | `game/PlayerLane.java` | existing boundaries around reset, wave callbacks and tower teardown |
| Reward lifecycle | `game/EconomyService.java` | kill reward calculation and `onMonsterKilled` callback |
| Tower definition | `tower/TowerType.java` | stable ID, display metadata, core stats, visual, upgrade options |
| Family definition | `tower/<family>/*Towers.java` | tower constants, tiers/roles, description templates |
| Family catalog | `tower/<family>/*TowerCatalogs.java` | runtime-resolved entries, factories, starters, upgrade edges |
| Global catalog | `tower/ProductionTowerCatalogs.java` | clears and rebuilds all family registrations |
| Placement/upgrade | `tower/ProductionTowerService.java` | shared validation, currency spend, construction, replacement, actions |
| Runtime tower | `tower/Tower.java`, `EntityBackedTower.java`, `ProductionTower.java`, `SupportTower.java` | lifecycle, stats, combat hooks, entity backing, active support execution |
| Balance schema/defaults | `config/TowerBalanceConfig.java`, `config/BundledBalanceDefaults.java`, `src/main/resources/semiontd/balance-defaults/` | packaged defaults, code fallback, merge, validation, tower/upgrade/ability schemas |
| Config loading | `config/SemionConfigLoader.java` | read, migrate, merge, validate, write, last-known-good fallback |
| Runtime balance | `config/TowerBalanceRuntime.java` | resolved stats, ability access, upgrade costs, rendered descriptions |
| Combat entity | `entity/tower/SemionTowerEntity.java` | entity state, attacks, timed effects, runtime stat synchronization |
| Target goal | `entity/tower/goal/TowerAttackMonsterGoal.java` | candidate search, custom/fallback selection, forced targeting |
| Area effects | `api/area/*`, `tower/area/*` | filtered lane-aware area actions and damage attribution |
| Player details | `ui/SemionTowerInteractionService.java`, `ui/SemionDialogService.java` | tower detail dialog and visible changing state |
| Web export | `web/WebCatalogExporter.java` | resolved tower/builders/upgrades/abilities/descriptions/visuals |
| Unit tests | `src/test/java/kim/biryeong/semiontd` | pure state, config, catalog, damage, descriptions, export |
| Fabric GameTests | `src/gametest/java/kim/biryeong/semiontd` | live entity, lane, lifecycle, placement, upgrade, dialog, VFX behavior |

### Choose references by the actual contract

Use the [registered-builder implementation comparison](../../../../docs/production-tower-catalog.ko.md#빌더별-구현-방식-선택) as the single detailed map of all registered builders and Default. It links current implementations and regression tests and distinguishes reusable boundaries from family-specific rules and unfinished work. Select a family with similar state ownership, lifetime and trigger, and compare a simpler family before introducing another abstraction. No builder is exemplary for every responsibility.

Keep these decisions explicit:

- Ownership: distinguish tower-local state, player/match services, target/source contributions and account persistence. Mutable objects with entity or lane references are not detached snapshots.
- Lifetime: inspect placement, upgrade copy, round preparation, wave start/end, death, reconnect and match shutdown. Reset only the fields whose lifetime ends at that boundary.
- Execution: preserve RNG consumption, encounter-order ties, callback membership checks, source attribution and failure/rollback ordering. Warlock's successful-sacrifice commit and End's partial-transfer rollback remain distinct contracts; other families retain their own rules.
- Presentation and configuration: views must not consume RNG or execute combat. Preserve inherited detail lines, typed values and reload semantics.
- Verification and cost: put pure state/geometry tests beside the implementation and keep server-backed tests for actual callers. Include collection construction, invalidation and memory costs when measuring; a controller, queue, cache or class extraction alone proves no performance improvement.

Family-local helpers are implementation details, not a public API for unrelated builders. Use common placement, damage, timed-effect and area-effect services first. Keep trivial hooks in the tower and extract only responsibilities with distinct callers, independently testable behavior or lifetimes. Follow the package/category/responsibility naming rule without forcing every family to have the same files.

The [hypercarry contract](#하이퍼-캐리형-빌더-공통-계약) below continues to apply, including trait/augment restrictions, actual-damage lifesteal and linear/logarithmic scaling. The comparison does not make unfinished common lifesteal integration complete.

## 2. Discovery and preflight

### Confirm repository and toolchain

From the candidate repository root, verify:

```text
git status --short
test -f gradlew
test -d src/main/java/kim/biryeong/semiontd
test -f gradle.properties
```

Read `AGENTS.md` and inherited instructions. The current project targets Minecraft 26.3 and Java 25, but use the checked-out `gradle.properties` and build files as authority.

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

`SemionJob` owns immutable builder metadata, economy/permission customization and public event entrypoints:

- identity: `id`, `displayName`, `description`;
- lifecycle entrypoints: `onSelected`, `onMatchStarted`, `onRoundStarted`, `onRoundEnded`, `onEliminated`, `onMatchClosed`;
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

There is no deselection hook. Reconnect does not replay selection or match-start hooks. Keep reconnection separate from starting a new participant or match.

`EconomyService` invokes `onMonsterKilled` when the reward is credited. Put reward-linked builder behavior there instead of duplicating kill detection inside towers.

### Event implementation and ordered cleanup

`SemionJob` keeps the eight existing public event entrypoints as empty defaults. Built-in job classes keep metadata, permissions, economy modifiers and their event behavior together: a `*TowerJob` overrides only the hooks it needs. Currently 23 jobs override at least one hook and 11 (including Default) use the empty defaults.

Register a new job in `JobRegistry`. Jobs are shared, so mutable player/match fields still belong in keyed family services. Family mechanics remain in their tower/services; the job hook connects those operations at the right boundary. For example, `PlantTowerJob.onRoundEnded` pays surviving owned towers' income, `ArmyTowerJob.onRoundEnded` completes service and discharge refunds, and `WarlockTowerJob.onMonsterKilled` records awakening progress once. End overrides no job hook; its tower transfer lifecycle remains separate.

| Hook | Current implementation boundary | Contract to preserve |
|---|---|---|
| `onSelected` | common dispatch to default empty behavior | starting economy applied; lane not attached yet |
| `onMatchStarted` | family implementation | reset/start match state, register team effects, install blueprints |
| `onRoundStarted` | family implementation | preparation-phase budgets, quests, shared storm roll; active non-eliminated teams only |
| `onRoundEnded` | family implementation | payouts, discharge, quest completion and next-round state in existing order |
| `onEliminated` | family implementation | all team members' owner state and team-effect cleanup after online cleanup/spectator transition |
| `onMatchClosed` | family implementation, followed by game shutdown phases | idempotent cleanup; Illager and Thunder also release active match state |
| `onSummonedMonster` | common dispatch to default empty behavior | preserve summon context and arguments |
| `onMonsterKilled` | Ancient City, Warlock and Demon Lord implementations | territory, awakening and combat experience rules; no duplicate credit |

`SemionGame` runs the existing single `DemonLordService.cleanupPlayer` for each online eliminated player, before spectator-team assignment and teleport. The subsequent loop invokes every team member's job `onEliminated`. Preserve these two scopes and their order.

`PlayerLane` keeps six separate boundaries inline: before and after temporary-copy removal in `resetForRound`, the wave preparation, pre-tower and post-tower blocks in `markWaveStarted`, and the block at the top of `clearTowers`. Preserve work between these blocks. In particular, tower wave callbacks and round-trait application run before the post-wave snapshots/reserves/Demon Lord combat transition. Preparation-phase `onRoundStarted` is not actual wave start.

`SemionGame` calls `VillagerAdvStates.onWaveStarted` / `onWaveCleared` as Villager ADV's existing whole-game callbacks. The disconnect handler in `Events` keeps the existing Gamble reveal, Demon Lord and Frost cleanup order; it does not reset the full job or replay match initialization on reconnect.

`SemionGame.closeRuntimeState()` orders participant `onMatchClosed`, Demon Lord online/offline cleanup, the pre-lane cleanup, team/lane/tower teardown, then the post-lane cleanup. Both phases process all participant UUIDs regardless of selected job. Before lanes: Villager ADV, Ancient City and Engineer. After lanes: complete the Atlantis state/pressure pass, then the Adversary/Mage/Future Agency/Queen/Hero/Army pass. Adversary installed-score reconciliation occurs during tower removal, so its cleanup cannot move before teardown. Do not duplicate these phase-owned cleanups inside a job hook.

`JobLifecycleTest` checks state ownership, repeated cleanup and single kill credit. `JobLifecycleRuntimeTest` covers Ancient City death caps/round reset/cleanup and cross-family Adversary state cleanup after tower removal. Keep the remaining family GameTests. Only Illager and Thunder gain previously missing match-close cleanup in this migration; moving existing event bodies does not authorize changing their rules.

### Registration

Add one immutable built-in job instance to `JobRegistry.registerBuiltIns()`. Jobs are shared objects. Never put mutable per-player, per-match, or per-round fields directly on them.

Use stable lowercase resource IDs. Changing a job or tower ID can break configs, persistence, web consumers, and recorded actions; treat ID changes as migrations.

### Mutable builder state

Use a keyed service such as `VillagerAdvStates` when state outlives one tower instance or is shared across a player's family. Key by the narrowest stable identity, normally player UUID, and define explicit operations rather than exposing a mutable map.

Required cleanup depends on lifetime:

1. clear stale state through the family lifecycle implementation at `onMatchStarted`;
2. clear the player entry at `onEliminated`;
3. assign idempotent shutdown cleanup to the family `onMatchClosed` or existing pre/post-lane phase according to state lifetime; do not duplicate phase-owned cleanup;
4. test repeated close, elimination then close and a second match/player reuse so missing cleanup or recreated state is observable.

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
ProductionTowerCatalog.registerStarter(resolved, factory);
ProductionTowerCatalog.register(resolvedHigherTier, factory, tier);
ProductionTowerCatalog.linkUpgrade(FROM, UPGRADE_ID, resolvedHigherTier.displayName(), resolvedHigherTier,
        TowerBalanceRuntime.upgradeCost(FROM, UPGRADE_ID));
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

1. add the packaged default in `src/main/resources/semiontd/balance-defaults/tower_balance.json`;
2. keep the corresponding family/code fallback coherent, where one exists;
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

### Published patch contracts

When the user requests published-patch synchronization, use the applied patch values, not proposals. Retain dated/versioned source evidence in `src/test/resources/balance/applied-patch-values.json`. `ConfigPublishedPatchContractTest` checks the seeded JSON and loaded runtime values, retired-card exclusion and preservation of an existing operator override. This complements family behavior tests; it does not replace them. Convert percentage ratios and tick durations explicitly. Do not restore retired content or a user-deleted operational configuration.

### Live balance authority

For balance review or rebalance work, locate the actual server instance and read:

- `config/semion-td/tower_balance.json`;
- `config/semion-td/economy.json` when starting resources or income matter.

Report which config was used. If no active configuration is available, label all numbers as packaged defaults and leave intentionally deleted operational files absent. Keep implementation verification separate from payback, DPS, wave-clear, or economy conclusions.

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

### Responsibility and test placement

New class names follow package/category/specific responsibility and tests end in `Test`. Examples are `tower/ocean/OceanTowerRuntimeTest`, `tower/villager/VillagerTowerAugmentCombatTest` and `tower/blueprint/BlueprintTowerModuleTest`. Existing legacy names are not a reason to rename unrelated files.

| Responsibility | Existing starting point |
|---|---|
| all-builder factory/ownership/upgrade contract | `src/test/.../tower/TowerBuilderCatalogContractTest` |
| one builder's pure formulas, configuration and catalog | `src/test/.../tower/<family>` |
| one builder's server/entity behavior | `src/gametest/.../tower/<family>`, such as `OceanTowerRuntimeTest` or `PlantTowerIntegrationTest` |
| one builder's augment behavior | family-local `*TowerAugmentCombatTest` / `*TowerAugmentSelectionTest` |
| genuinely shared participant/match rules | `gametest/SemionParticipantGameTest`, `SemionLifecycleGameTest` |
| cross-family targeting policy | `tower/TowerBuilderTargetPolicyTest` |
| shared augment mechanics | `augment/AugmentCombatGameTest`, `AugmentControllerGameTest` |
| VFX palette and event routing | `entity/tower/vfx/TowerVfxGameTest` |

The all-builder contract discovers non-default jobs through `JobRegistry` and checks each job's catalog entries and factories, both positions, directed upgrades and invested mineral transfer. It creates a Blueprint fixture because that catalog is dynamic. Extend the family-specific tests for mechanic boundaries; do not copy the full common catalog test into every builder package or freeze the current total as a permanent target.

### GameTest setup and registration

`GameTestParticipantFixture` implements `CustomTestMethodInvoker`, restores the default production catalog and income summons before each method, and shares arena/game/entity helpers. `AugmentCombatFixture` and `AugmentControllerFixture` serve their respective augment test setups. Inherit the fixture that matches the test; retain a local helper when only one family needs it. Do not introduce another fixture layer solely to deduplicate a few declarations.

Register concrete test classes in `src/gametest/resources/fabric.mod.json`. Preserve `@GameTest`, invocation interfaces, source sets and Gradle discovery during moves; abstract helpers are not entrypoints. Compare method identity and registration before/after extraction and confirm the full runtime report has no missing or duplicate cases. A class rename changes its test filter/report ID, so update those references too.

For chunk/player-dependent tests use the existing `RuntimeEnvironmentFixture` and `RuntimePlayerFixture` readiness conditions. Keep bounded asynchronous readiness and the actual combat/lifecycle assertions; do not replace them with an unconditional delay or a weakened expectation. Restore global catalogs/configs and close match state in cleanup. An opt-in performance test that reports `NOT_RUN` is not performance validation.

When adding a built-in builder, update any explicit built-in builder list and expected starter count intentionally. Do not weaken counts into `>=` just to make registration pass.

### Preserve semantics when optimizing

`PlayerLane` owns tower membership and ordered lifecycle dispatch. Its identity membership set avoids a linear list search for each ticked tower while retaining the ordered snapshot. Reuse lane mutation methods; do not modify a second membership structure from a family. Preserve the rule that removals during callbacks are observed and new additions wait for the next snapshot. Wave/summon FIFO queues avoid shifting remaining elements at each head removal; queue order and spawn timing are still gameplay contracts.

Keep family-specific resource distribution, RNG, target ties and rounding in their established order. Share a calculation only when inputs and lifetime match; a wave snapshot and a live per-tick value are different contracts. Cache invalidation must include placement, removal, upgrade, movement, death/revival, reload and match shutdown wherever those affect the cached result. Avoid world scans when the shared lane/area API already supplies eligible targets.

Separate responsibility-only changes from data-structure or algorithm changes. Measure real input sizes and call frequency, including snapshot allocation, index updates and cache rebuilds. Report average/amortized/worst complexity accurately and compare identical inputs before claiming MSPT/TPS improvement. A passing GameTest, shorter class or asymptotic bound alone is not measured gameplay performance.

### Commands

Run focused tests while iterating, then the complete required commands:

```text
./gradlew test --console=plain --no-daemon
./gradlew runGameTest --console=plain --no-daemon
./gradlew remapJar --console=plain --no-daemon
git diff --check
```

Use the repository-required wrapper, such as `rtk`, without changing the underlying validation intent.

`build.gradle` currently supplies required local compatibility projects and declared external mods through the Gradle dependency graph. Do not copy operating-server mods into `run/mods` as a test prerequisite. If GameTest fails before test execution with a missing dependency, inspect that graph and the available artifact. Treat an unavailable dependency as an environment/install blocker; do not change gameplay code or dependency metadata merely to hide it.

### Runtime smoke checks

Use an authorized isolated server and fresh test world. The configured `runGameTest` task creates an isolated directory under `build/run`; inspect its runtime dependencies rather than copying operating-server data. Do not start, restart, deploy to, or convert an operational server/world as an implied part of development. New explicit EULA/permission requirements remain a blocker.

Exercise reload, builder selection, placement, upgrade, damage/economy, visible state and cleanup for the changed mechanic. Distinguish unit tests, server-backed GameTests, actual client GPU rendering and real multiplayer. HUD/font/VFX appearance requires an actual client check at the repository's GUI-scale baseline and other supported sizes. Compilation or a mock-player connection does not verify rendering.

The complete gameplay gate remains `./gradlew test runGameTest remapJar --console=plain --no-daemon` (Windows: `.\gradlew.bat`). In this 26.3 build, `remapJar` is the existing distributable compatibility task; do not replace the non-remapping platform setup with a legacy example. Report unexecuted checks explicitly.

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
| GameTest dependency failure before tests | required declared module/artifact unavailable | Gradle runtime graph, not gameplay code |

### New builder/family completion checklist

- [ ] Stable builder ID, immutable `SemionJob` and explicit lifecycle implementation/no-op registration.
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


Entity-backed tests must declare a structure that contains every arena coordinate, including final-defense targets and hidden skill carriers. Use `RuntimeArenaFixture` for synchronous combat cases that need loaded entity sections before invocation; its bounded GameTest sequence checks `ServerLevel.areEntitiesActuallyLoadedAndTicking`, preserves the annotation timeout and reports reflective assertion failures as `GameTestAssertException` with their original cause. `FullChunkStatus.ENTITY_TICKING` alone does not establish entity-section readiness. Body and Demon Lord tests use the existing `combat_arena` structure. Keep setup, assertions and cleanup inside one invocation; do not leave raw `AssertionError` callbacks scheduled on server ticks. The test-only `RuntimeEnvironmentFixture` initializes missing Fabric server/registry/profile context for mock logins, and `RuntimePacketContextTest` exercises block-entity chunk serialization through both mock-player paths.


### 하이퍼 캐리형 빌더 공통 계약

엔드·흑마법사·마왕을 하이퍼 캐리형으로 분류한다. 분류는 기존 `SemionJob.isHyperCarry()`와 `JobRegistry`를 사용하며 영웅의 기존 지정 증강 효율 예외는 별도로 유지한다. 공통 제한 설명은 직업을 나열하지 않고 **하이퍼 캐리형**으로 표시한다. 알·진화·희생·전이 등 각 빌더의 고유 조건은 다른 빌더에 복사하지 않는다.

- 특성·증강 제한을 반드시 연결한다. 완강함은 핵심 전투체에 `CoreMaxHealthBonus`를 적용하고 일반 보조 타워에는 일반 수치를 유지한다. 마왕의 별도 체력 풀도 같은 축소 수치를 한 번만 적용하며 특성 동기화로 현재 체력을 채우지 않는다. 지정 증강의 강화·패널티는 20%이고 중첩 한도·발동 조건·지속 시간은 유지한다. 전용 증강의 직업 조건도 유지한다.
- 생명력 흡수는 실제 피해·기준 피해·성장 진행률을 연결하고 표시와 실제 회복이 같은 계산을 사용해야 한다. 엔드는 `DamageLifeSteal` 기준 피해 30, 흑마법사는 40을 사용한다. **마왕은 현재 피해 비례 회복 및 최대 체력 상한을 사용하는 별도 경로이며 공통 계산으로의 이행은 미완료다.** 마왕의 기준 피해·진행률·기존 패시브/제단 상한 관계는 승인된 수치가 없으므로 임의로 정하거나 기존 회복량을 바꾸지 않는다. 새 하이퍼 캐리형은 이 값을 확정하고 공통 경로를 검증하기 전 완료로 처리하지 않는다.
- 체력·피해의 성장 보너스는 선형 구간과 이후 스케일을 모두 정의해야 한다. `LogarithmicScaling`은 `x ≤ threshold`에서 x, 이후 `threshold + scale × log1p((x-threshold)/scale)`이다. 마왕의 레벨 체력 보너스는 체력 단위 threshold/scale 500/500, 피해 보너스는 비율 단위 0.5/0.5(50%/50%)를 사용한다. 경계에서 연속이며 배분 능력치의 기존 선형 증가는 별도로 유지한다. 체력 500/500도 최대 체력 전체가 아닌 레벨 원시 성장분(체력 포인트)만 제어한다. **마왕의 피해 0.5/0.5는 무차원 레벨 추가 배율만 제어하며, 기본 공격력 포인트나 최종 피해량의 임계값·스케일이 아니다.** 이를 엔드·흑마의 공격력 포인트 점감과 동일한 구현 완료로 간주하지 않는다. 마왕 본체의 별도 피해 포인트 점감은 `physicalDamageThreshold/Scale=100/100`, `magicDamageThreshold/Scale=100/50`이다. 레벨·투자·계약·스킬 조건·마왕 증강 배율을 합성한 뒤 대상별 개별 피해 발생마다 한 번 적용하고, 그 다음 대상의 받는 피해 보정과 방어를 계산한다. 기존 레벨 배율 0.5/0.5는 유지한다. 처형 TRUE 피해와 별도 소환수는 이 점감에서 제외한다. 통계에서는 TRUE도 물리 열에 포함되므로 물리 합계를 평타로 해석하지 않는다. 재시전에는 점감 전 원시 피해를 저장하고, 흡혈은 점감·방어 후 실제 피해와 기존 회당 상한을 사용한다. 흑마와 동급임을 입증한 값이 아닌 초기 튜닝안이며, 공통 흡혈 전환의 미정 사항과 별개다.

새 빌더 추가 시 분류 등록, 특성의 실제 체력 및 HUD 연결, 증강 후보·선택·리롤·지정 검증, 흡수 표시/실제 피해와 회복 일치, 성장 경계 전후와 중복 적용·무료 회복 방지 테스트를 확인한다. 전체 테스트 및 격리 클라이언트 표시 검증을 통과해야 한다.
