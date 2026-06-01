# ELO Patch Prework Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** ELO/TrueSkill 계열 본 패치를 넣기 전에, 최신 `master` 기준으로 rating 기능이 안전하게 얹힐 수 있는 선행 기반 작업을 만든다.

**Architecture:** 본 패치에서 바로 rating 계산을 넣지 않는다. 먼저 경기 식별자, 팀 탈락 순위, 결과 모델 확장, 팀 크기 보정 정책, 테스트 fixture를 준비해 이후 `SemionRatingProfile`/`SemionRatingStore`/rating update가 중복 적용이나 5팀 결과 정보 손실 없이 들어갈 수 있게 한다.

**Tech Stack:** Java 21/Fabric/Gradle/GameTest, existing Semion TD game/progression/config/ui packages.

---

## Current repository state

- Repository: `/Users/qf/IdeaProjects/steve-td`
- Branch after pull: `master`
- Remote: `origin https://github.com/biryeongtrain/steve-td.git`
- Updated by fast-forward from `60291df` to `06b57f1`
- Latest commit: `06b57f1 fix(tower): update Legion Tower parrot variant to green`
- Pull changed only `src/main/java/kim/biryeong/semiontd/tower/legion/LegionTowers.java`

## Confirmed current code facts

- No rating/ELO implementation files exist yet under `src/main/java/kim/biryeong/semiontd`.
- No telemetry implementation files exist yet under `src/main/java/kim/biryeong/semiontd`.
- `SemionGame.matchResult()` currently emits only `winningTeams` and participant-level `winner` booleans.
- `MatchResult` currently has no `matchId`, `startedAt`, `endedAt`, team placement, or idempotency key.
- `ProgressionService.applyMatchResult(...)` currently applies rewards directly with no match-id duplicate guard.
- `SemionGame` already has clear hooks for elimination tracking:
  - `announcedEliminations`
  - `killBoss(...)`
  - `tick(...)` detecting `team.eliminated()`
  - `handleTeamEliminated(...)`
  - `checkVictory()`

---

## Prework principles

1. Do not introduce rating math in this prework patch.
2. Preserve existing behavior and UI unless explicitly extending data models in a backward-compatible way.
3. Add tests around every new model invariant before adding rating functionality.
4. Keep fallback semantics: existing `winner` boolean and `winningTeams` must remain available until the ELO patch migrates consumers.
5. Make future rating update idempotency possible before any rating writes exist.
6. Treat team-size imbalance as a first-class policy decision before using `sum(mu)` in matchmaking.

---

## Task 1: Add stable match identity to `MatchResult`

**Objective:** Ensure every finalized match can be uniquely identified before progression/rating/telemetry writes.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Design:**

Add to `MatchResult`:

```java
UUID matchId
long startedAtEpochMillis
long endedAtEpochMillis
```

Add to `SemionGame`:

```java
private UUID matchId = UUID.randomUUID();
private long startedAtEpochMillis;
private long endedAtEpochMillis;
```

Set `matchId` and `startedAtEpochMillis` when roster locks in `start(...)`. Set `endedAtEpochMillis` once when `checkVictory()` transitions to `ENDED`.

**Important:** `matchResult()` must return the same `matchId` every time for the same finished game.

**Verification:**

```bash
./gradlew compileJava compileGametestJava --console=plain
./gradlew runGameTest --console=plain
```

Expected: compile succeeds and existing participant/progression tests still pass.

**Commit message:**

```bash
git commit -m "feat(game): add stable match identity to results"
```

---

## Task 2: Introduce team result model without changing rating

**Objective:** Represent team placement data in the result model while keeping current winner/loser behavior intact.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/game/TeamMatchResult.java`
- Create: `src/main/java/kim/biryeong/semiontd/game/MatchResultGroup.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Model draft:**

```java
public enum MatchResultGroup {
    WIN_GROUP,
    LOSS_GROUP,
    DRAW_OR_UNRATED
}
```

```java
public record TeamMatchResult(
        TeamId teamId,
        int placement,
        MatchResultGroup resultGroup,
        double placementWeight,
        int eliminatedRound,
        long eliminatedTick,
        double bossDamageTaken
) {}
```

**Rules for this prework patch:**

- Do not calculate ELO deltas yet.
- `placementWeight` can be deterministic placeholder data used by future rating code.
- Preserve `winningTeams` and `MatchParticipantResult.winner()`.
- If only `winningTeams` is known, create winner team results as placement `1` and all other active teams as shared placement `2`.

**Verification:**

Add GameTest fixture proving:

- 2-team result still maps to winner/loss correctly.
- 5-team result can carry `TeamMatchResult` list.
- Existing match result dialog still uses `winningTeams` successfully.

**Commit message:**

```bash
git commit -m "feat(game): add team match result model"
```

---

## Task 3: Track team elimination order in `SemionGame`

**Objective:** Capture 2nd/3rd/4th/5th place signals before ELO patch uses them.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Design:**

Add an internal record near `SemionGame`:

```java
private record TeamEliminationRecord(
        TeamId teamId,
        int round,
        long tick,
        double bossDamageTaken
) {}
```

Add field:

```java
private final List<TeamEliminationRecord> eliminationOrder = new ArrayList<>();
```

Reset it in `start(...)` with `announcedEliminations`.

Record once inside `handleTeamEliminated(...)` or immediately before calling it, using `announcedEliminations.add(team.id())` as duplicate guard.

`matchResult()` should build placements as:

1. Living teams at game end get top placement.
2. Eliminated teams are ordered by latest elimination last-survivor semantics:
   - first eliminated = worst placement
   - last eliminated = best non-winning placement
3. Ties can initially use deterministic tie-break by `TeamId` order if same tick.

**Verification:**

Add GameTest that manually eliminates teams in order and asserts:

```text
5th = first eliminated
4th = second eliminated
3rd = third eliminated
2nd = fourth eliminated
1st = remaining living team
```

**Commit message:**

```bash
git commit -m "feat(game): track team elimination order"
```

---

## Task 4: Define rating team-size policy before matchmaking rating math

**Objective:** Prevent future `teamMu = sum(player.mu)` from unfairly favoring 5-person teams over 4-person teams.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/game/TeamSizeBalancePolicy.java`
- Modify: `docs/matchmaking-trueskill2-plan.ko.md`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Recommended policy for prework:**

Add enum:

```java
public enum TeamSizeBalancePolicy {
    ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION,
    PREFER_EQUAL_SIZE_FOR_RATED_MATCHES
}
```

Do not wire it into config yet unless needed. Document the chosen default in the plan doc.

**Recommended default:** `ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION` for compatibility with current 9-player, 21-player behavior.

Update `docs/matchmaking-trueskill2-plan.ko.md` with a section explaining:

- Raw `sum(mu)` is only directly comparable when team sizes are equal.
- For uneven teams, rating matchmaking must compare both total team power and normalized per-player average.
- Phase 1 matchmaking should include a team-size penalty or normalized score before selecting candidate plans.

**Verification:**

Add a small pure Java helper test or GameTest asserting current participant selection shapes are unchanged.

**Commit message:**

```bash
git commit -m "docs: define team size policy for rating matchmaking"
```

---

## Task 5: Decide and scaffold persistence backend abstraction

**Objective:** Decide whether rating/progression/telemetry should stay file-backed or move behind a DB-ready repository interface before the ELO patch.

**Context:** Current progression persistence is JSON-file based in `SemionProgressionStore` using Gson and `profiles.json`. `build.gradle` currently has no JDBC, SQLite, MongoDB, or connection-pool dependency.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackend.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceConfig.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/FilePersistenceBackend.java`
- Modify: `src/main/java/kim/biryeong/semiontd/config/SemionConfigLoader.java`
- Modify: `docs/matchmaking-trueskill2-plan.ko.md`
- Test: config/default loading tests in `SemionParticipantGameTest.java` or a focused config test class if available

**Recommended architecture:**

Use a repository/port interface first, with the current JSON store as the default adapter. Do not hard-code SQLite/Mongo APIs directly into game logic.

```java
public enum SemionPersistenceBackendType {
    FILE,
    SQLITE,
    MONGODB
}
```

```java
public record SemionPersistenceConfig(
        SemionPersistenceBackendType backend,
        String sqlitePath,
        String mongodbUri,
        String mongodbDatabase,
        boolean externalDbRequired
) {
    public static SemionPersistenceConfig defaultConfig() {
        return new SemionPersistenceConfig(
                SemionPersistenceBackendType.FILE,
                "semiontd.db",
                "",
                "semiontd",
                false
        );
    }
}
```

**Default policy:**

- Default remains `FILE` for local/server simplicity and backward compatibility.
- SQLite is the preferred first DB backend because it is embedded, easy to ship, and enough for one Minecraft server.
- MongoDB should be supported via config as an external backend only if the operator provides a URI.
- Game code should depend on repository interfaces, not DB-specific classes.

**Acceptance:**

- Config can represent `FILE`, `SQLITE`, and `MONGODB` without enabling DB code yet.
- Existing startup remains file-backed and compatible.
- The ELO patch can later add `RatingRepository` adapters without changing `SemionGameManager` flow.

**Commit message:**

```bash
git commit -m "feat(persistence): scaffold db backend configuration"
```

---

## Task 6: Add progression idempotency seam without changing storage behavior yet

**Objective:** Prepare for rating/progression duplicate protection without risking existing profile schema.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/progression/ProgressionService.java`
- Modify: `src/main/java/kim/biryeong/semiontd/progression/SemionProgressionStore.java`
- Test: relevant GameTest/progression tests in `SemionParticipantGameTest.java`

**Design options:**

Preferred minimal prework:

- Add method stub/structure:

```java
public boolean hasAppliedMatch(UUID matchId) { ... }
public void markMatchApplied(UUID matchId) { ... }
```

But keep it internal/no-op only if changing persisted schema is too risky.

Better if feasible:

- Add an applied match index through the chosen persistence abstraction.
- For `FILE`, use a separate file next to `profiles.json`:

```text
config/semion-td/progression-applied-matches.json
```

- For future SQLite, use a table:

```sql
CREATE TABLE IF NOT EXISTS applied_matches (
  match_id TEXT PRIMARY KEY,
  applied_at_epoch_millis INTEGER NOT NULL,
  subsystem TEXT NOT NULL
);
```

- For future MongoDB, use a collection with unique compound index:

```text
applied_matches unique(matchId, subsystem)
```

Avoid adding an unbounded `recentMatchIds` field to every player profile in the prework patch.

**Acceptance:**

- Same `MatchResult.matchId()` can be detected as already applied.
- Existing profile load/save remains compatible.
- No rating logic yet.

**Commit message:**

```bash
git commit -m "feat(progression): add match application idempotency seam"
```

---

## Task 7: Select first DB backend implementation strategy

**Objective:** Choose the concrete backend path before adding rating writes.

**Files:**
- Modify: `build.gradle` only if implementing SQLite or MongoDB in this prework branch
- Modify/Create: `src/main/java/kim/biryeong/semiontd/persistence/*`
- Modify: `docs/matchmaking-trueskill2-plan.ko.md`

**Option A: File-first, DB-ready interfaces only**

Use this if the ELO patch should land quickly and external DB is not yet provided.

Pros:
- Lowest risk.
- No new runtime dependency/shading problem.
- Works on existing servers immediately.

Cons:
- Later migration needed for SQLite/Mongo.

**Option B: SQLite first**

Use this if the server should have durable queryable rating history without requiring external infra.

Potential dependency:

```gradle
include implementation("org.xerial:sqlite-jdbc:<version>")
```

But verify Fabric/Loom packaging before committing this. Native SQLite JDBC artifacts can increase jar size and may need runtime compatibility checks.

Suggested schema:

```sql
CREATE TABLE IF NOT EXISTS rating_profiles (
  player_id TEXT PRIMARY KEY,
  last_known_name TEXT NOT NULL,
  games_played INTEGER NOT NULL,
  wins INTEGER NOT NULL,
  losses INTEGER NOT NULL,
  mu REAL NOT NULL,
  sigma REAL NOT NULL,
  display_elo INTEGER NOT NULL,
  peak_display_elo INTEGER NOT NULL,
  placement_games_remaining INTEGER NOT NULL,
  last_delta_elo INTEGER NOT NULL,
  last_played_at_epoch_millis INTEGER NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS match_results (
  match_id TEXT PRIMARY KEY,
  started_at_epoch_millis INTEGER NOT NULL,
  ended_at_epoch_millis INTEGER NOT NULL,
  final_round INTEGER NOT NULL,
  raw_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rating_changes (
  match_id TEXT NOT NULL,
  player_id TEXT NOT NULL,
  old_mu REAL NOT NULL,
  old_sigma REAL NOT NULL,
  new_mu REAL NOT NULL,
  new_sigma REAL NOT NULL,
  old_display_elo INTEGER NOT NULL,
  new_display_elo INTEGER NOT NULL,
  delta_display_elo INTEGER NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY (match_id, player_id)
);
```

**Option C: MongoDB external backend**

Use this if frosti provides an external DB URI and wants central storage across multiple server instances or external dashboards.

Potential dependency:

```gradle
implementation("org.mongodb:mongodb-driver-sync:<version>")
```

Config should never log the full URI. Redact credentials in logs.

Suggested collections:

```text
rating_profiles
match_results
rating_changes
applied_matches
match_telemetry_events
```

Indexes:

```text
rating_profiles: unique playerId, displayElo desc
rating_changes: unique(matchId, playerId), playerId + createdAt
match_results: unique matchId, endedAt
applied_matches: unique(matchId, subsystem)
match_telemetry_events: matchId + eventSeq
```

**Recommendation:**

For the prework PR: implement Option A plus config shape for Option B/C. Start actual storage with JSON file + repository interface. If the operator confirms an external DB URI or dashboard requirement, implement MongoDB adapter as a separate PR. If not, implement SQLite adapter before large telemetry is enabled.

**Commit message if only documenting/choosing:**

```bash
git commit -m "docs: choose db-ready persistence strategy for ratings"
```

**Commit message if SQLite adapter is implemented:**

```bash
git commit -m "feat(persistence): add sqlite rating storage adapter"
```

**Commit message if MongoDB adapter is implemented:**

```bash
git commit -m "feat(persistence): add mongodb rating storage adapter"
```

---

## Task 8: Add match result tests around future rating invariants

**Objective:** Lock down the behavior the ELO patch will rely on.

**Files:**
- Modify: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Test cases:**

1. `matchResultKeepsStableMatchId`
2. `fiveTeamMatchResultOrdersEliminatedTeams`
3. `winningTeamsRemainBackwardCompatible`
4. `teamResultsExposeWinAndLossGroups`
5. `progressionDoesNotDoubleApplySameMatchId` if Task 5 implements real idempotency

**Verification:**

```bash
./gradlew compileJava compileGametestJava --console=plain
./gradlew runGameTest --console=plain
```

**Commit message:**

```bash
git commit -m "test(game): cover match result rating preconditions"
```

---

## Task 9: Final prework validation

**Objective:** Ensure the branch is safe before the actual ELO patch begins.

**Commands:**

```bash
./gradlew compileJava compileGametestJava --console=plain
./gradlew runGameTest --console=plain
```

**Manual checks:**

- `git diff master...HEAD` contains no rating delta math.
- Existing `/semiontd profile` output is unchanged unless intentionally documented.
- Match result dialog still shows winner/reward correctly.
- `docs/matchmaking-trueskill2-plan.ko.md` clearly calls out team-size normalization and idempotency.

**Commit message if only docs/test cleanup remains:**

```bash
git commit -m "chore: validate elo prework baseline"
```

---

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| `MatchResult` constructor changes break many tests | Add overloaded constructors or update call sites in one small task. |
| Progression idempotency changes existing profile schema | Prefer a separate applied-match index file or a no-op seam first. |
| Elimination order is wrong for simultaneous elimination | Use deterministic `TeamId` tie-break in prework; document true tie policy for ELO patch. |
| Team-size policy blocks current uneven rosters | Default to normalization policy, not equal-size-only. |
| Prework accidentally changes gameplay | Keep all rating math absent and verify existing GameTests. |

---

## Open questions before implementation

1. Should applied-match idempotency live under `config/semion-td/` with progression profiles or under `run/semiontd/` with runtime match artifacts?
2. Should `MatchResult.matchId` be generated at roster lock or at game creation? Recommended: roster lock.
3. For admin `/semiontd end`, should the match be `DRAW_OR_UNRATED` or rated using current living teams? Recommended for prework: preserve current behavior and only add a later config switch.
4. For simultaneous team elimination, should shared placement be supported now or deferred? Recommended: deterministic tie-break now, shared placement later if needed.

---

## Recommended execution order

1. Create branch from updated `master`:

```bash
git checkout -b feat/elo-prework
```

2. Execute Tasks 1-9 with one commit per task.
3. Push branch and open PR.
4. Only after this PR is green, start the actual ELO patch on top of it.
