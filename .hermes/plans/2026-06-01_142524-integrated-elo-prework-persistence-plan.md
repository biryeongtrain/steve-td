# Integrated ELO Prework & Persistence Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Semion TD의 ELO/TrueSkill 계열 rating 패치를 안전하게 넣기 전에, 최신 `master` 기준으로 match identity, team placement, idempotency, persistence abstraction, DB-ready 저장 구조를 통합적으로 준비한다.

**Architecture:** 본 계획은 rating math 자체를 구현하지 않는다. 먼저 경기 결과가 rating에 필요한 정보를 잃지 않도록 `MatchResult`/team placement를 확장하고, 중복 적용을 막을 `matchId`/applied-match 구조를 만든다. 동시에 현재 JSON 파일 저장소를 유지하되, 이후 SQLite 또는 MongoDB를 hook할 수 있도록 persistence/repository abstraction을 선제 도입한다.

**Tech Stack:** Java 21, Fabric, Gradle/Loom, GameTest, Gson JSON file store, future SQLite JDBC or MongoDB sync driver.

---

## Current repository state

Repository:

```text
/Users/qf/IdeaProjects/steve-td
```

Branch and remote after pull:

```text
branch: master
remote: origin https://github.com/biryeongtrain/steve-td.git
HEAD: 06b57f1 fix(tower): update Legion Tower parrot variant to green
```

Latest pull result:

```text
60291df..06b57f1  master -> origin/master
Fast-forward
src/main/java/kim/biryeong/semiontd/tower/legion/LegionTowers.java | 2 +-
```

Current local untracked plan files:

```text
.hermes/plans/2026-06-01_141826-elo-prepatch-plan.md
.hermes/plans/2026-06-01_142524-integrated-elo-prework-persistence-plan.md
```

---

## Confirmed code facts

### Current result/rating state

- There is no current ELO/rating implementation under `src/main/java/kim/biryeong/semiontd`.
- There is no current match telemetry implementation under `src/main/java/kim/biryeong/semiontd`.
- `MatchResult` currently contains:

```java
List<MatchParticipantResult> participants
Set<UUID> spectatorIds
Set<TeamId> winningTeams
int finalRound
```

- `SemionGame.matchResult()` currently derives `winningTeams` from living teams and emits per-player `winner` booleans.
- Current result model cannot distinguish 2nd/3rd/4th/5th place in 5-team games.

### Current persistence state

- `SemionProgressionStore` is JSON-file backed.
- It uses Gson and stores profile data in `profiles.json`.
- `ProgressionService.applyMatchResult(...)` writes rewards directly and has no duplicate `matchId` guard.
- `build.gradle` currently has no SQLite, JDBC, MongoDB, or connection-pool dependency.

### Useful current hooks

`SemionGame` already has hooks that are suitable for team placement tracking:

- `announcedEliminations`
- `killBoss(...)`
- `tick(...)` detecting eliminated teams
- `handleTeamEliminated(...)`
- `checkVictory()`

---

## High-level implementation strategy

1. **Create a prework branch.**
2. **Do not implement rating math yet.**
3. **Add stable match identity.**
4. **Add team placement result model.**
5. **Track team elimination order.**
6. **Define team-size normalization policy.**
7. **Introduce DB-ready persistence abstraction while keeping file backend as default.**
8. **Add idempotency seam through that persistence layer.**
9. **Decide whether to implement SQLite/MongoDB now or leave adapter hooks for a later PR.**
10. **Add GameTests for all ELO preconditions.**
11. **Run full compile/GameTest validation.**

Recommended branch:

```bash
git checkout -b feat/elo-prework-persistence
```

---

## Non-goals for this plan

Do **not** implement these in this prework patch:

- TrueSkill/ELO delta calculation.
- Rating-based team assignment.
- Leaderboard command.
- `/semiontd rating` command.
- Personal performance adjustment.
- Lane pressure/leak telemetry scoring.
- MongoDB URI secret management beyond config shape and redaction policy.

Those should happen after this prework is merged and green.

---

## Core design decisions

### Decision 1: Default persistence remains file-backed

Default backend should remain `FILE` because:

- It preserves current behavior.
- It avoids Fabric/Loom packaging risk from native SQLite JDBC or external MongoDB driver dependencies.
- It keeps the prework PR focused.

### Decision 2: Add DB-ready abstraction before adding rating writes

Game/rating code should not know whether data lives in JSON, SQLite, or MongoDB.

Introduce interfaces such as:

```java
public interface RatingRepository { ... }
public interface MatchResultRepository { ... }
public interface AppliedMatchRepository { ... }
```

Concrete adapters can be:

```text
FileRatingRepository
SqliteRatingRepository
MongoRatingRepository
```

But in the prework PR, only file/default adapter needs to be functional unless an external DB is immediately provided.

### Decision 3: SQLite is preferred embedded DB; MongoDB is preferred external DB

- **SQLite:** best default DB upgrade for a single Minecraft server.
- **MongoDB:** best when frosti provides an external DB hook, multiple servers, external dashboards, or remote analytics.

### Decision 4: Idempotency belongs to match/subsystem level, not player profile

Do not store unbounded `recentMatchIds` inside every player profile.

Use a separate applied-match index:

```text
(matchId, subsystem) unique
```

Subsystem examples:

```text
progression
rating
telemetry
```

---

## Task 1: Create prework branch and verify clean baseline

**Objective:** Start from the pulled latest `master` and ensure no unrelated changes are mixed into the implementation branch.

**Files:** None.

**Steps:**

```bash
git status --short --branch
git checkout -b feat/elo-prework-persistence
```

Expected before branch:

```text
## master...origin/master
?? .hermes/
```

Note: `.hermes/plans/` can either remain uncommitted or be committed as docs depending on team preference. If committing, include only the integrated plan file.

**Verification:**

```bash
git branch --show-current
```

Expected:

```text
feat/elo-prework-persistence
```

**Commit:** None yet.

---

## Task 2: Add stable match identity to `MatchResult`

**Objective:** Ensure every finalized match has a stable idempotency key before progression/rating/telemetry writes exist.

**Files:**

- Modify: `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Implementation sketch:**

Add fields to `MatchResult`:

```java
UUID matchId,
long startedAtEpochMillis,
long endedAtEpochMillis,
```

Add fields to `SemionGame`:

```java
private UUID matchId = UUID.randomUUID();
private long startedAtEpochMillis;
private long endedAtEpochMillis;
```

Set them as follows:

- Generate/reset `matchId` when `start(...)` successfully locks roster.
- Set `startedAtEpochMillis` at the same point.
- Set `endedAtEpochMillis` exactly once when `checkVictory()` transitions to `ENDED`.
- `matchResult()` must return the same `matchId` every time for the same finished match.

**Compatibility:**

If many tests construct `MatchResult` directly, add a convenience constructor that fills `matchId` and timestamps for tests. Prefer updating tests explicitly where possible.

**Tests:**

Add or update GameTest:

```text
matchResultKeepsStableMatchId
```

Expected behavior:

- Calling `matchResult()` twice after game end returns the same `matchId`.
- `startedAtEpochMillis > 0`.
- `endedAtEpochMillis >= startedAtEpochMillis`.

**Verification:**

```bash
./gradlew compileJava compileGametestJava --console=plain
```

**Commit:**

```bash
git add src/main/java/kim/biryeong/semiontd/game/MatchResult.java \
        src/main/java/kim/biryeong/semiontd/game/SemionGame.java \
        src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
git commit -m "feat(game): add stable match identity to results"
```

---

## Task 3: Add team result model while preserving current winner behavior

**Objective:** Make `MatchResult` capable of carrying team-level placement information without changing current result UI/progression semantics.

**Files:**

- Create: `src/main/java/kim/biryeong/semiontd/game/MatchResultGroup.java`
- Create: `src/main/java/kim/biryeong/semiontd/game/TeamMatchResult.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Implementation sketch:**

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
) {
    public TeamMatchResult {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(resultGroup, "resultGroup");
        if (placement <= 0) {
            throw new IllegalArgumentException("placement must be positive");
        }
    }
}
```

Add to `MatchResult`:

```java
List<TeamMatchResult> teamResults
```

**Initial compatibility behavior:**

If only current winner/loser data is known:

- winner teams get placement `1`, `WIN_GROUP`.
- all non-winning active teams get shared placement `2`, `LOSS_GROUP`.
- `winningTeams` and `MatchParticipantResult.winner()` remain unchanged.

**Tests:**

Add fixtures proving:

- Existing `winnerCount()` and `loserCount()` still work.
- Existing match result dialog can still rely on `winningTeams`.
- `teamResults` is immutable/copy-safe.

**Verification:**

```bash
./gradlew compileJava compileGametestJava --console=plain
```

**Commit:**

```bash
git add src/main/java/kim/biryeong/semiontd/game/MatchResultGroup.java \
        src/main/java/kim/biryeong/semiontd/game/TeamMatchResult.java \
        src/main/java/kim/biryeong/semiontd/game/MatchResult.java \
        src/main/java/kim/biryeong/semiontd/game/SemionGame.java \
        src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
git commit -m "feat(game): add team match result model"
```

---

## Task 4: Track team elimination order in `SemionGame`

**Objective:** Capture 5-team placement signal before ELO calculation uses it.

**Files:**

- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Implementation sketch:**

Add internal record:

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

Reset in `start(...)`:

```java
eliminationOrder.clear();
announcedEliminations.clear();
```

Record once per eliminated team. Use the same guard as `announcedEliminations.add(team.id())` to avoid duplicate records.

Placement logic:

1. Living teams at end are top placement.
2. Eliminated teams are ranked reverse elimination order.
3. First eliminated gets worst placement.
4. Last eliminated gets best non-winning placement.
5. Same tick ties can use deterministic `TeamId` order for now.

**Tests:**

Add GameTest for 5 active teams:

```text
fiveTeamMatchResultOrdersEliminatedTeams
```

Expected:

```text
1st = remaining living team
2nd = last eliminated team
3rd = previous eliminated team
4th = previous eliminated team
5th = first eliminated team
```

**Verification:**

```bash
./gradlew compileJava compileGametestJava --console=plain
```

**Commit:**

```bash
git commit -m "feat(game): track team elimination order"
```

---

## Task 5: Define rating team-size policy before rating matchmaking

**Objective:** Prevent future rating matchmaking from incorrectly comparing 5-person teams and 4-person teams using raw `sum(mu)` only.

**Files:**

- Create: `src/main/java/kim/biryeong/semiontd/game/TeamSizeBalancePolicy.java`
- Modify: `docs/matchmaking-trueskill2-plan.ko.md`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Implementation sketch:**

```java
public enum TeamSizeBalancePolicy {
    ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION,
    PREFER_EQUAL_SIZE_FOR_RATED_MATCHES
}
```

**Recommended default:**

```text
ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION
```

Reason:

- Current `ParticipantSelectionService` supports uneven rosters such as 5v4 or 5/4/4/4/4.
- Blocking uneven rosters would change gameplay/queue behavior too early.

**Documentation update:**

Add to `docs/matchmaking-trueskill2-plan.ko.md`:

- Raw `sum(mu)` is directly comparable only for equal team sizes.
- Uneven teams need both total power and normalized per-player average.
- Future match quality should include team-size penalty or normalized score.

**Verification:**

Existing participant selection tests must still pass:

- 9 players -> 5/4
- 21 players -> 5/4/4/4/4
- 25 players -> 5/5/5/5/5

**Commit:**

```bash
git commit -m "docs: define team size policy for rating matchmaking"
```

---

## Task 6: Introduce persistence backend config and abstraction

**Objective:** Make file/SQLite/MongoDB a configuration-level decision while keeping file storage as the working default.

**Files:**

- Create: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackendType.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceConfig.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackend.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/FilePersistenceBackend.java`
- Modify: `src/main/java/kim/biryeong/semiontd/config/SemionConfigLoader.java`
- Modify: `docs/matchmaking-trueskill2-plan.ko.md`
- Test: config/default loading tests, either in `SemionParticipantGameTest.java` or a focused config test class if available.

**Implementation sketch:**

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

Interface draft:

```java
public interface SemionPersistenceBackend {
    SemionPersistenceBackendType type();
    void initialize();
    void close();
}
```

File backend draft:

```java
public final class FilePersistenceBackend implements SemionPersistenceBackend {
    private final Path configDir;

    public FilePersistenceBackend(Path configDir) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
    }

    @Override
    public SemionPersistenceBackendType type() {
        return SemionPersistenceBackendType.FILE;
    }

    @Override
    public void initialize() {
        // no-op for current JSON stores
    }

    @Override
    public void close() {
        // no-op
    }
}
```

**Important:** Do not add SQLite/MongoDB dependencies in this task unless Task 9 chooses to implement an adapter now.

**Verification:**

- Default config loads `FILE`.
- Existing server initialization still passes `profiles.json` path to current progression store.
- No behavior change yet.

**Commit:**

```bash
git commit -m "feat(persistence): scaffold db backend configuration"
```

---

## Task 7: Define repository ports for rating/match/idempotency

**Objective:** Establish where ELO patch will write data without committing to JSON/SQLite/MongoDB implementation details.

**Files:**

- Create: `src/main/java/kim/biryeong/semiontd/persistence/AppliedMatchRepository.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/MatchResultRepository.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/RatingRepository.java`
- Create: `src/main/java/kim/biryeong/semiontd/rating/SemionRatingProfile.java` only if needed as a pure data model with no rating math.
- Test: focused unit/GameTest coverage for file repository no-op/default behavior.

**Interface sketches:**

```java
public interface AppliedMatchRepository {
    boolean hasApplied(UUID matchId, String subsystem);
    boolean markApplied(UUID matchId, String subsystem, long appliedAtEpochMillis);
}
```

```java
public interface MatchResultRepository {
    void saveMatchResult(MatchResult matchResult);
    Optional<MatchResult> findMatchResult(UUID matchId);
}
```

```java
public interface RatingRepository {
    Optional<SemionRatingProfile> findProfile(UUID playerId);
    SemionRatingProfile saveProfile(UUID playerId, SemionRatingProfile profile);
}
```

**Guideline:**

If `SemionRatingProfile` is introduced here, it should be a passive record only. Do not implement update formulas or rating delta calculations in this plan.

**Commit:**

```bash
git commit -m "feat(persistence): add rating repository ports"
```

---

## Task 8: Add progression idempotency seam

**Objective:** Prepare progression/rating duplicate protection using `matchId` and `AppliedMatchRepository`.

**Files:**

- Modify: `src/main/java/kim/biryeong/semiontd/progression/ProgressionService.java`
- Modify: `src/main/java/kim/biryeong/semiontd/progression/SemionProgressionStore.java` only if file-backed applied-match index is implemented here.
- Create: `src/main/java/kim/biryeong/semiontd/persistence/FileAppliedMatchRepository.java` if implementing file index now.
- Test: progression duplicate-apply test.

**Preferred file-backed structure:**

Create a separate JSON file rather than embedding match ids in profiles:

```text
config/semion-td/progression-applied-matches.json
```

Example JSON shape:

```json
{
  "progression": ["match-uuid-1", "match-uuid-2"],
  "rating": [],
  "telemetry": []
}
```

or map shape:

```json
{
  "match-uuid-1:progression": 1780290000000
}
```

Prefer map shape for easier timestamp storage.

**SQLite equivalent for future adapter:**

```sql
CREATE TABLE IF NOT EXISTS applied_matches (
  match_id TEXT NOT NULL,
  subsystem TEXT NOT NULL,
  applied_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY (match_id, subsystem)
);
```

**MongoDB equivalent for future adapter:**

```text
collection: applied_matches
unique index: { matchId: 1, subsystem: 1 }
```

**Progression behavior:**

`ProgressionService.applyMatchResult(...)` should:

1. Check `hasApplied(matchResult.matchId(), "progression")`.
2. If already applied, return current reward/profile view without incrementing counts again, or return an explicit empty/no-op result.
3. If not applied, apply rewards and mark applied.

**Important open choice:**

The return type currently returns `Map<UUID, MatchProgressionReward>`. If duplicate apply returns an empty map, UI may miss reward lines on retry. If duplicate apply reconstructs reward view, more work is needed. For prework, document and test the chosen behavior.

**Commit:**

```bash
git commit -m "feat(progression): add match application idempotency seam"
```

---

## Task 9: Choose and optionally implement first DB adapter

**Objective:** Decide whether the prework branch stops at DB-ready interfaces or includes an actual SQLite/MongoDB adapter.

**Files if documenting only:**

- Modify: `docs/matchmaking-trueskill2-plan.ko.md`

**Files if implementing SQLite:**

- Modify: `build.gradle`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/*`
- Test: SQLite temp-file repository tests.

**Files if implementing MongoDB:**

- Modify: `build.gradle`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/mongodb/*`
- Test: config validation tests; integration test only if MongoDB is available.

### Option A: Stop at file-first, DB-ready interfaces

Recommended for the first prework PR.

Pros:

- Lowest risk.
- No new dependency packaging issue.
- Existing server keeps working.
- Easy to review.

Cons:

- DB adapter still needs a later PR.

### Option B: Implement SQLite first

Recommended if no external DB is available but persistent queryable storage is desired soon.

Potential dependency:

```gradle
include implementation("org.xerial:sqlite-jdbc:<version>")
```

Before committing, verify:

- Native SQLite JDBC packaging works under Fabric/Loom.
- Generated jar size is acceptable.
- Server starts in GameTest/runtime environment.

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

CREATE TABLE IF NOT EXISTS applied_matches (
  match_id TEXT NOT NULL,
  subsystem TEXT NOT NULL,
  applied_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY (match_id, subsystem)
);
```

### Option C: Implement MongoDB external backend

Recommended if frosti provides an external DB URI and wants centralized storage or dashboards.

Potential dependency:

```gradle
implementation("org.mongodb:mongodb-driver-sync:<version>")
```

Config rules:

- Never log the full MongoDB URI.
- Redact credentials in errors/logs.
- If `externalDbRequired=true` and connection fails, fail startup clearly.
- If `externalDbRequired=false` and connection fails, either fallback to file backend or disable rating writes explicitly. Do not silently pretend remote writes succeeded.

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
rating_profiles: unique(playerId), displayElo desc
match_results: unique(matchId), endedAt desc
rating_changes: unique(matchId, playerId), playerId + createdAt
applied_matches: unique(matchId, subsystem)
match_telemetry_events: matchId + eventSeq
```

### Recommendation for this project now

For the first prework PR:

```text
Implement Option A.
```

Then:

- If frosti provides MongoDB URI soon, implement MongoDB adapter as a separate PR before telemetry volume grows.
- If no external DB is provided, implement SQLite adapter before enabling large telemetry or leaderboard history.

**Commit if documenting only:**

```bash
git commit -m "docs: choose db-ready persistence strategy for ratings"
```

**Commit if SQLite adapter is implemented:**

```bash
git commit -m "feat(persistence): add sqlite rating storage adapter"
```

**Commit if MongoDB adapter is implemented:**

```bash
git commit -m "feat(persistence): add mongodb rating storage adapter"
```

---

## Task 10: Add match result and persistence precondition tests

**Objective:** Lock down invariants that the actual ELO patch will rely on.

**Files:**

- Modify: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`
- Or create focused test files if the project already supports them.

**Required tests:**

1. `matchResultKeepsStableMatchId`
2. `fiveTeamMatchResultOrdersEliminatedTeams`
3. `winningTeamsRemainBackwardCompatible`
4. `teamResultsExposeWinAndLossGroups`
5. `participantSelectionShapesRemainUnchanged`
6. `defaultPersistenceBackendIsFile`
7. `progressionDoesNotDoubleApplySameMatchId` if Task 8 implements real duplicate guard
8. `appliedMatchRepositorySeparatesSubsystems`

**Expected behavior examples:**

- Same match applied to `progression` twice does not increment `gamesPlayed` twice.
- Same match can be marked separately for `progression` and `rating`.
- A 5-team game produces ordered `TeamMatchResult` entries.
- Existing result UI can still read `winningTeams`.

**Verification:**

```bash
./gradlew compileJava compileGametestJava --console=plain
./gradlew runGameTest --console=plain
```

**Commit:**

```bash
git commit -m "test(game): cover elo prework invariants"
```

---

## Task 11: Update ELO planning docs with integrated storage plan

**Objective:** Keep `docs/matchmaking-trueskill2-plan.ko.md` aligned with the implementation prework.

**Files:**

- Modify: `docs/matchmaking-trueskill2-plan.ko.md`

**Add or update sections:**

1. Stable `matchId` requirement.
2. Team-size normalization policy.
3. Persistence backend choices:
   - FILE default
   - SQLite embedded option
   - MongoDB external option
4. Applied-match idempotency by `(matchId, subsystem)`.
5. Rating repository abstraction.
6. External DB credential redaction requirement.
7. DB migration path from `profiles.json` to future SQLite/MongoDB.

**Migration note to include:**

```text
Existing progression profiles remain in profiles.json until a migration command or startup migration is explicitly implemented. Rating data should start in the new repository layer, not by expanding SemionPlayerProfile.
```

**Commit:**

```bash
git commit -m "docs: integrate elo persistence prework plan"
```

---

## Task 12: Final validation

**Objective:** Ensure the prework branch is safe before the actual ELO patch begins.

**Commands:**

```bash
./gradlew compileJava compileGametestJava --console=plain
./gradlew runGameTest --console=plain
```

**Manual checks:**

```bash
git diff master...HEAD --stat
git diff master...HEAD -- src/main/java/kim/biryeong/semiontd/game
git diff master...HEAD -- src/main/java/kim/biryeong/semiontd/persistence
git diff master...HEAD -- docs/matchmaking-trueskill2-plan.ko.md
```

Check:

- No ELO/rating delta math is present.
- Existing profile/reward behavior remains compatible.
- Existing match result UI still works from `winningTeams`.
- Team placement data is available for future rating.
- Persistence backend defaults to file.
- DB credentials cannot be logged accidentally.
- GameTests pass.

**Commit if cleanup needed:**

```bash
git commit -m "chore: validate elo persistence prework baseline"
```

---

## Suggested PR structure

PR title:

```text
feat: prepare match results and persistence for ELO
```

PR body summary:

```markdown
## Summary
- Add stable match identity for future rating/progression idempotency
- Add team-level match result/placement model
- Track team elimination order for 5-team placement signals
- Define team-size policy before rating-based matchmaking
- Add persistence backend config/abstraction for file, SQLite, MongoDB
- Add applied-match idempotency seam
- Document DB strategy and ELO prework constraints

## Non-goals
- No ELO delta calculation yet
- No rating-based matchmaking yet
- No leaderboard/rating commands yet

## Test Plan
- [ ] ./gradlew compileJava compileGametestJava --console=plain
- [ ] ./gradlew runGameTest --console=plain
```

---

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---:|---|
| `MatchResult` constructor changes break tests | Medium | Add compatibility constructors or update call sites in one focused commit. |
| Team placement ordering wrong for simultaneous elimination | Medium | Use deterministic `TeamId` tie-break now; support shared placement later. |
| Progression duplicate apply return behavior is ambiguous | Medium | Document chosen behavior and test it. |
| SQLite JDBC native packaging fails under Fabric | High | Do not add SQLite dependency in first PR; add adapter in separate verified PR. |
| MongoDB URI leaks in logs | High | Redact credentials and avoid logging full config values. |
| Persistence abstraction becomes over-engineered | Medium | Keep interfaces minimal: applied match, rating profile, match result. |
| Existing JSON profiles need migration | Low for prework | Keep progression JSON as-is; start rating in separate repository. |
| File backend and DB backend drift | Medium | Define repository contract tests reused by all adapters. |

---

## Open questions for frosti

1. 외부 DB를 hook한다면 MongoDB가 맞는가, 아니면 PostgreSQL/MySQL 계열도 고려해야 하는가?
2. 외부 DB는 rating만 저장하면 되는가, 아니면 match telemetry까지 저장해야 하는가?
3. DB 연결 실패 시 서버 시작을 막아야 하는가?
   - `externalDbRequired=true`: fail startup
   - `false`: file fallback or rating disabled
4. 기존 `profiles.json` progression 데이터를 DB로 migration해야 하는가, 아니면 rating만 새 repository에서 시작하면 되는가?
5. leaderboard/dashboards가 필요하면 query pattern을 먼저 정해야 하는가?
6. 개인정보/닉네임 저장 정책이 필요한가? UUID만 필수 저장하고 name은 lastKnownName으로만 둘지 결정 필요.

---

## Recommended next move

1. Commit or keep this integrated plan under `.hermes/plans/`.
2. Create `feat/elo-prework-persistence` branch.
3. Implement Tasks 2-8 with file backend only.
4. Stop at Task 9 Option A unless frosti immediately provides an external DB URI.
5. Open PR and run GameTest.
6. After merge, start actual ELO patch with `RatingRepository` using the prepared interfaces.
