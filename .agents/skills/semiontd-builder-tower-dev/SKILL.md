---
name: semiontd-builder-tower-dev
description: "Implement, extend, review, debug, or balance SemionTD builders/jobs and production tower families in steve-td. Use when work touches SemionJob, JobRegistry, TowerType, ProductionTowerCatalogs, TowerBalanceConfig or TowerBalanceRuntime, tower runtime hooks, area effects and VFX, tower dialogs, web catalog export, or unit and Fabric GameTest coverage."
---

# SemionTD Builder and Tower Development

Implement the smallest complete builder or tower change that fits the repository's current architecture. Treat source, applicable balance configuration, player-visible UI, web export, and executable tests as one delivery surface. The current checkout targets Minecraft 26.3 / Java 25. Confirm the exact dependencies and tasks in the build before using an example.

## Operating contract

1. Locate the repository root by `gradlew` and `src/main/java/kim/biryeong/semiontd`; do not assume a personal absolute path.
2. Read repository instructions before running commands. Use `rtk` when the repository instructions require it; otherwise use the underlying command directly.
3. Inspect the current working tree before editing. Preserve unrelated and pre-existing changes.
4. Use CodeGraph first when an existing, current `.codegraph/` index is available. Use it to narrow the flow, then inspect exact source before editing.
5. Read [references/implementation.md](references/implementation.md) before implementing or reviewing a builder/tower family. Use its section routing for narrower balance, UI, or test work.
6. Prefer an existing sibling pattern and shared runtime service over a new abstraction or a one-family parallel pipeline.

## Establish authority before designing

Use this precedence:

1. Current checked-out source and tests define APIs, lifecycle order, persistence, and integration points.
2. The active server's `config/semion-td/tower_balance.json` and `economy.json` define deployed balance when the task concerns live balance.
3. `TowerBalanceConfig.defaultConfig()` loads `src/main/resources/semiontd/balance-defaults/tower_balance.json` through `BundledBalanceDefaults`, with code defaults as fallback. Neither is proof of current live values. An explicit request to synchronize published patches determines the requested new defaults; it does not authorize overwriting operator overrides.
4. This skill explains the architecture but never overrides newer source.

If the active server configuration is unavailable or intentionally deleted, say that conclusions use packaged defaults. Do not recreate a deleted operational file, silently substitute an old snapshot, or start an operational server. For an approved published-patch synchronization, retain the source URL, applied status, date/version and units in the existing balance contract fixture; verify both packaged defaults and runtime loading.

## Workflow

### 1. Bound the requested slice

State the mechanic, affected builders/towers, required persistence, player-visible output, and validation level. Separate implementation correctness from balance evaluation. Do not invent adjacent content.

### 2. Trace the real flow

Follow the relevant path end to end:

- builder lifecycle: `SemionGame` / `EconomyService` -> `SemionJob` hooks overridden in each `*TowerJob`; lane boundaries live in `PlayerLane` (`resetForRound`, `markWaveStarted`, `clearTowers`);
- tower availability: family constants -> family catalog -> `ProductionTowerCatalogs` -> `ProductionTowerService`;
- combat: `SemionTowerEntity` / `TowerAttackMonsterGoal` -> `Tower` hooks -> shared damage and area-effect APIs;
- balance: `TowerBalanceConfig` -> `SemionConfigLoader` -> `TowerBalanceRuntime` -> resolved catalog entries;
- visibility: tower interaction -> `SemionDialogService`, descriptions, VFX, and `WebCatalogExporter`;
- verification: focused unit tests -> Fabric GameTests -> remapped artifact when delivery requires it.

Inspect every caller before changing a shared hook or service.

### 3. Choose the closest complete sibling

Use the [registered-builder implementation comparison](../../../docs/production-tower-catalog.ko.md#빌더별-구현-방식-선택) to choose a family with matching state ownership, lifetime and trigger, then compare a simpler family. The comparison covers every registered builder and Default, with actual code, tests and limitations. No family is the default model for every responsibility. Reuse current helpers for damage, timed effects, area effects, VFX, descriptions, state transfer and config merging. Preserve the chosen family's ordering, reset/copy and failure contracts; do not copy its class count, stale values or unrelated game rules. Responsibility separation is a design choice, and performance claims require measurements under identical inputs.

### 4. Keep responsibilities and tests together

Name new classes by package/category/specific responsibility, for example `ocean/OceanTowerRuntimeTest` and `villager/VillagerTowerAugmentCombatTest`. Keep family behavior and its JUnit/GameTest tests in the matching `tower/<family>` package. Keep actual cross-family contracts in the responsible common package; do not duplicate them into each family.

Reuse `GameTestParticipantFixture`, `AugmentCombatFixture` or `AugmentControllerFixture` only when the test needs that setup. Register concrete GameTest classes in `src/gametest/resources/fabric.mod.json`; preserve annotations, invocation interfaces, source sets and task discovery when moving a test. Keep helper bases unregistered. Check method identity and registration before and after moves.

Keep catalog data, family state, combat hooks and UI presentation separate where they have distinct lifetimes or callers. Split a class for a concrete responsibility, not to meet a line limit. A responsibility-only move is not a performance improvement.

### 5. Implement the whole integration slice

For a new builder family, normally cover:

- immutable `SemionJob` definition and `JobRegistry` registration (override only the event hooks the builder needs);
- stable tower IDs, tier/role grouping, visuals, descriptions, and upgrade graph;
- runtime tower classes only where behavior differs from the base classes;
- family catalog registration and top-level `ProductionTowerCatalogs` wiring;
- defaults, upgrade costs, abilities, merge behavior, and runtime validation;
- state ownership and cleanup when state outlives a tower instance;
- dialog/runtime details, timed-effect labels, a dedicated `BuilderPalette` entry with production VFX routing, and web ownership;
- unit and GameTest assertions for the actual risk surface.

Do not create a custom placement, targeting, damage, scan, or visual-effect pipeline if the shared pipeline can express the mechanic.

### 6. Verify reload and compatibility

Confirm missing config keys are backfilled without overwriting configured values, invalid config preserves last-known-good runtime data, and `/semiontd reload` resolves active catalog/tower types correctly. Preserve stable IDs and legacy upgrade-cost lookup when changing an existing family.

### 7. Validate at the correct depth

Run the smallest focused test first, then the required suite:

```text
./gradlew test --console=plain --no-daemon
./gradlew runGameTest --console=plain --no-daemon
./gradlew remapJar --console=plain --no-daemon
git diff --check
```

Use the full `test runGameTest remapJar` gate for implemented gameplay changes. `remapJar` is this 26.3 build's compatibility task for the distributable output; do not transplant a legacy Loom remapping setup. The configured GameTest task creates a fresh isolated world. Keep unrelated operating-server mods out of the default suite while retaining required runtime dependencies.

Prefix with `rtk` where required. Run `runGameTest` for entity, placement, upgrade, lifecycle, team-lane, dialog, or VFX behavior. Run `remapJar` or `build` when a distributable artifact or packaged resources are part of the request.

## Non-negotiable invariants

- Treat jobs as shared objects. Keep job metadata, permissions, economy modifiers and event hook overrides together in the `*TowerJob`; do not add a separate lifecycle class per builder. Keep mutable match/player state in keyed services and preserve the all-participant pre/post-lane shutdown phases.
- Do not assume the lane is attached inside `SemionJob.onSelected`; selection runs before `team.addPlayer`. There is no deselection hook, and reconnect does not replay selection or match-start hooks. Preserve the six `PlayerLane` boundaries around temporary-copy removal, tower wave callbacks and tower teardown; do not move actual wave-start behavior into the preparation-phase round hook.
- Make every exported tower belong to exactly one builder through `includesTowerInCatalog`; context-sensitive jobs must not rely only on `canUseTower`.
- Register only tier-one choices as starters. Register all entries before linking upgrade edges.
- Resolve catalog types through `TowerBalanceRuntime` and price upgrades through `TowerBalanceRuntime.upgradeCost(from, upgradeId)`, not the target tower's placement cost.
- Use `ProductionTower` as the default base for ordinary basic-attack towers. Drop to `EntityBackedTower` only when the production base behavior conflicts with the mechanic.
- Preserve original/current positions and call the shared `copyFrom` path on upgrade. Add `copyRuntimeStateFrom` only for custom fields or mutable state that the typed data map cannot safely copy.
- Route damage through tower/shared damage APIs. Route AoE through `SemionTdApi.areaEffects()` and `TowerAreaDamage`; do not call entity damage directly or hand-scan the world.
- Use `onAttackResolved` when a mechanic depends on actual dealt damage or kill outcome. Use `onWaveStarted` for wave snapshots whose ordering matters.
- Give every new builder/job its own `BuilderPalette` entry and route all of its tower IDs through `TowerVfxService.paletteFor(...)`. Do not leave a new builder on `DEFAULT` or reuse another builder's palette. The palette must be exercised by production attack, secondary, area, or special VFX; an enum-only entry is incomplete.
- Render config-driven numeric descriptions with `TowerDescriptionRegistry` templates. Expose changing state through `runtimeDetailLines()` or the timed-effect dialog path.
- Keep `WebCatalogExporter.snapshot()` valid and verify that descriptions contain no unresolved placeholders.
- Preserve ordered lane snapshots, callback-time membership checks, FIFO spawn order and directed upgrade prices when optimizing. Count collection construction, invalidation and memory costs; compare unchanged, responsibility-only and algorithm-improved stages under identical inputs before claiming MSPT or TPS gains.
- Do not declare success from compilation alone. Report the exact checks run, their result, and any environment-only blocker.

## Completion report

Report only:

1. the player-visible and architectural outcome;
2. changed files or installed artifact path;
3. exact test, GameTest, reload, and packaging evidence;
4. live-config assumptions or blockers that remain.


하이퍼 캐리형(엔드·흑마법사·마왕) 작업은 `references/implementation.md`의 하이퍼 캐리형 빌더 공통 계약을 따른다. 특성·증강 제한, 생명력 흡수, 체력·피해의 선형 구간 및 스케일 연결과 검증은 필수다. 미확정 수치를 임의로 만들지 말고 기존 구현의 미이행 항목을 명시한다.
