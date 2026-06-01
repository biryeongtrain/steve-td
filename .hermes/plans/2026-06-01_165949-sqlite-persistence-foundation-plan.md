# SQLite Persistence Foundation Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Use DB/persistence review subagents for schema, transaction, and Fabric packaging checkpoints.

**Goal:** Replace the abandoned MongoDB direction with a small, opt-in SQLite persistence foundation that keeps FILE as the default and prepares match/progression/rating persistence for later ELO/TrueSkill work.

**Architecture:** Introduce SQLite as a second persistence backend behind existing repository interfaces, then move repository construction into a backend/factory seam instead of hard-coding file repositories inside `SemionGameManager` and `ProgressionService`. Use TSID-backed `MatchId` values as domain/idempotency identifiers, but keep SQLite as a thin storage layer without explicit schema-level constraints; store `MatchResult` as JSON initially to avoid overfitting schema before rating math lands.

**Tech Stack:** Java 21, Fabric/Loom Gradle, Gson, SQLite JDBC (`org.xerial:sqlite-jdbc`), Hypersistence TSID (`io.hypersistence:hypersistence-tsid:2.1.4`), existing GameTest suite.

---

## Current Repo Facts Confirmed

- Repository: `/Users/qf/IdeaProjects/steve-td`
- Current branch: `master`
- Current HEAD: `c9ae6e4`
- Current worktree state at planning time:
  ```text
  ## master...origin/master
  ?? .codegraph/
  ```
- CodeGraph index exists in `.codegraph/`; do not commit it unless explicitly requested.
- Existing persistence-related files:
  - `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceConfig.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackendType.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackend.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/FilePersistenceBackend.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/AppliedMatchRepository.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/FileAppliedMatchRepository.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/MatchResultRepository.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/FileMatchResultRepository.java`
  - `src/main/java/kim/biryeong/semiontd/persistence/RatingRepository.java`
- Existing rating model:
  - `src/main/java/kim/biryeong/semiontd/rating/SemionRatingProfile.java`
- Existing config already loads `persistence.json`, but runtime wiring does not yet use `configs.persistence()` meaningfully.
- `SemionGameManager.configure()` currently directly constructs `FileMatchResultRepository` and `ProgressionService` from paths.
- `ProgressionService` currently directly constructs `SemionProgressionStore` and default `FileAppliedMatchRepository` from paths.
- `build.gradle` currently has no SQLite/JDBC/TSID/MongoDB dependency.
- Existing validation command used in this project:
  ```bash
  ./gradlew test gametest --no-daemon
  ```

---

## Autoresearch Iteration Log

### Iteration 1 — Hypothesis: implement MongoDB or generic external DB first

**Hypothesis:** A broad external persistence abstraction with MongoDB and SQLite support would best serve long-term rating needs.

**Evidence checked:** Existing `SemionPersistenceConfig` already has `MONGODB`, `mongodbUri`, `mongodbDatabase`, and `externalDbRequired`, but no runtime usage or dependency. User explicitly decided to abandon MongoDB.

**Decision:** Reject. MongoDB increases configuration/ops burden and introduces unused config surface. New work must not add MongoDB implementation/dependencies.

### Iteration 2 — Hypothesis: add SQLite repositories only, leave wiring as-is

**Hypothesis:** Implement `SqliteMatchResultRepository`, `SqliteAppliedMatchRepository`, and `SqliteRatingRepository` without touching manager/service wiring.

**Evidence checked:** `SemionGameManager.configure()` directly creates `FileMatchResultRepository`; `ProgressionService` directly creates `SemionProgressionStore`. If this remains, SQLite code would be uncallable in real gameplay and only testable in isolation.

**Decision:** Partially reject. Repository implementations are needed, but a backend/factory seam must be included so `backend=SQLITE` actually changes runtime behavior.

### Iteration 3 — Hypothesis: fully convert progression profiles and match result application into one transaction immediately

**Hypothesis:** Make SQLite the full transaction owner for match result + progression profile + applied marker in this PR.

**Evidence checked:** Domain subagent identified the strongest long-term direction: transactionally combine `match_results`, `applied_matches`, progression, and later rating. However, converting `SemionProgressionStore` to a new repository interface plus transaction helper can make the PR large and risky.

**Decision:** Adopt in staged form. This plan introduces a `ProgressionRepository` seam and SQLite implementation, but keeps FILE default and defers any complex migration/replay tooling. SQLite must still use transaction boundaries for its own write operations and duplicate applied markers.

### Iteration 4 — Final synthesis

**Chosen plan:**
1. Drop MongoDB from the future direction and either remove or safely legacy-fallback unsupported Mongo config.
2. Add SQLite JDBC and prove packaging/runtime via tests.
3. Add SQLite backend/schema/repositories.
4. Add backend factory/wiring so `backend=SQLITE` is opt-in and real.
5. Keep rating math, leaderboard, matchmaking, and migration out of scope.
6. Add tests proving FILE default, SQLite round-trips, duplicate applied-match behavior, and no committed DB artifacts.

---

## Non-Goals

Do **not** implement these in this SQLite foundation patch:

- ELO/TrueSkill rating delta calculation.
- Rating-based matchmaking or leaderboard commands.
- MongoDB adapter, MongoDB dependency, MongoDB connection-string handling.
- Automatic migration from existing `profiles.json`, `match-results.json`, or `progression-applied-matches.json` into SQLite.
- Async DB worker/thread pool.
- Connection pool.
- Multi-server shared SQLite file support.
- Large migration framework. Use `PRAGMA user_version` for now.
- Changing default backend from `FILE` to `SQLITE`.
- Committing `.codegraph/` or generated `.db`, `.db-wal`, `.db-shm` files.

---

## Architecture Decisions

### Backend policy

- `FILE` remains the default backend.
- `SQLITE` is opt-in via `persistence.json`.
- MongoDB is no longer a planned backend.
- Existing legacy Mongo-shaped configs should not crash the server; safest behavior is warning + FILE fallback through existing config parse fallback or explicit compatibility handling.

### Path policy

- `sqlitePath` default: `semiontd.db`.
- Relative `sqlitePath` resolves relative to the mod config directory, e.g. `config/semion-td/semiontd.db`.
- Absolute `sqlitePath` is used as-is.
- Parent directories are created by the backend.

### SQLite pragmas

Run at connection initialization:

```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA busy_timeout = 5000;
```

Do not enable `PRAGMA foreign_keys`; the schema intentionally avoids foreign-key constraints and keeps domain validation in the mod/repository layer.

### Schema policy

Use `PRAGMA user_version = 1` for initial schema version. Keep `MatchResult` as `payload_json` initially, plus a few query/audit columns. This avoids premature schema churn before rating math is finalized.

Use a single TSID-backed match identifier rather than separate `id` and `match_uuid` columns. `MatchResult` should own the identifier at construction time so FILE and SQLite backends see the same domain/idempotency key. Generate IDs with `io.hypersistence:hypersistence-tsid` and store the 64-bit TSID value as SQLite `INTEGER`; do not keep UUID-v4 as the match identifier.

Do not encode gameplay/domain invariants as explicit database constraints. The mod/repository layer is responsible for validating positive IDs, required fields, preventing duplicate match results, allowing only one active game, validating rating/progression values, and enforcing applied-match idempotency. SQLite should provide columns plus non-unique lookup indexes only; avoid `NOT NULL`, `PRIMARY KEY`, `FOREIGN KEY`, `CHECK`, and `UNIQUE` constraints in the initial schema.

Initial schema:

```sql
CREATE TABLE IF NOT EXISTS match_results (
    match_id INTEGER,
    started_at_epoch_millis INTEGER,
    ended_at_epoch_millis INTEGER,
    final_round INTEGER,
    payload_json TEXT,
    created_at_epoch_millis INTEGER
);

CREATE TABLE IF NOT EXISTS applied_matches (
    match_id INTEGER,
    subsystem TEXT,
    applied_at_epoch_millis INTEGER
);

CREATE TABLE IF NOT EXISTS rating_profiles (
    player_id TEXT,
    last_known_name TEXT,
    games_played INTEGER,
    wins INTEGER,
    losses INTEGER,
    mu REAL,
    sigma REAL,
    display_elo INTEGER,
    updated_at_epoch_millis INTEGER
);

CREATE INDEX IF NOT EXISTS idx_match_results_match_id
    ON match_results(match_id);

CREATE INDEX IF NOT EXISTS idx_match_results_ended_at
    ON match_results(ended_at_epoch_millis);

CREATE INDEX IF NOT EXISTS idx_applied_matches_match_subsystem
    ON applied_matches(match_id, subsystem);

CREATE INDEX IF NOT EXISTS idx_applied_matches_subsystem
    ON applied_matches(subsystem);

CREATE INDEX IF NOT EXISTS idx_rating_profiles_player_id
    ON rating_profiles(player_id);

CREATE INDEX IF NOT EXISTS idx_rating_profiles_display_elo
    ON rating_profiles(display_elo DESC);
```

Optional in this PR only if needed for progression repository conversion:

```sql
CREATE TABLE IF NOT EXISTS progression_profiles (
    player_id TEXT,
    last_known_name TEXT,
    payload_json TEXT,
    updated_at_epoch_millis INTEGER
);

CREATE INDEX IF NOT EXISTS idx_progression_profiles_player_id
    ON progression_profiles(player_id);
```

### Duplicate match result policy

For `match_results`, prefer idempotent behavior:

- `saveMatchResult` for the same `match_id` should not create duplicates.
- It may either no-op if payload already exists or replace with equivalent latest payload; choose one behavior and test it.
- Recommended for audit safety: implement repository-layer `SELECT` before `INSERT` and preserve first payload. Do not rely on `INSERT OR IGNORE`, because the schema intentionally has no unique constraint to trigger ignore behavior.

### Applied match policy

`applied_matches(match_id, subsystem)` is the logical idempotency key enforced by the repository/mod layer, not by a database primary key. First `markApplied()` returns `true`; subsequent calls for same pair return `false`; same match with different subsystem returns `true`.

Long-term ideal:

```text
BEGIN IMMEDIATE
  INSERT applied_matches(match_id, subsystem, applied_at)
  if conflict: rollback/no-op
  perform subsystem state updates
COMMIT
```

This patch should at minimum implement repository-level atomicity for SQLite `markApplied` and document the transaction pattern for the rating PR.

---

## Proposed Files

### Create

- `src/main/java/kim/biryeong/semiontd/game/MatchId.java`
- `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackends.java`
- `src/main/java/kim/biryeong/semiontd/persistence/SemionRepositories.java`
- `src/main/java/kim/biryeong/semiontd/progression/ProgressionRepository.java`
- `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java`
- `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteSchema.java`
- `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteMatchResultRepository.java`
- `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteAppliedMatchRepository.java`
- `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteRatingRepository.java`
- Optional if converting progression fully in this PR:
  - `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteProgressionRepository.java`

### Modify

- `build.gradle`
- `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- `src/main/java/kim/biryeong/semiontd/persistence/MatchResultRepository.java`
- `src/main/java/kim/biryeong/semiontd/persistence/AppliedMatchRepository.java`
- `src/main/java/kim/biryeong/semiontd/persistence/FileMatchResultRepository.java`
- `src/main/java/kim/biryeong/semiontd/persistence/FileAppliedMatchRepository.java`
- `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceConfig.java`
- `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackendType.java`
- `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackend.java`
- `src/main/java/kim/biryeong/semiontd/persistence/FilePersistenceBackend.java`
- `src/main/java/kim/biryeong/semiontd/progression/SemionProgressionStore.java`
- `src/main/java/kim/biryeong/semiontd/progression/ProgressionService.java`
- `src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java`
- `src/main/java/kim/biryeong/semiontd/SemionTd.java`
- `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

---

## Task Plan

### Task 1: Lock down MongoDB removal policy and preserve FILE default

**Objective:** Remove MongoDB from the planned runtime surface while guaranteeing the default backend remains FILE.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackendType.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceConfig.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Step 1: Add/adjust failing tests**

Extend existing `defaultPersistenceBackendIsFile` and add a legacy Mongo-shaped config test if Mongo fields/enum are removed.

Test intent:

```java
@GameTest(template = EMPTY_STRUCTURE)
public void legacyMongoPersistenceConfigFallsBackToFile(GameTestHelper context) {
    // write persistence.json containing backend=MONGODB and mongodb fields
    // SemionConfigLoader.load(...)
    // assert configs.persistence().backend() == SemionPersistenceBackendType.FILE
    // assert no exception escapes
}
```

**Step 2: Run targeted GameTest compile**

Run:

```bash
./gradlew compileGametestJava --no-daemon
```

Expected before implementation: fail if tests reference removed/changed behavior.

**Step 3: Implement config policy**

Recommended record:

```java
public record SemionPersistenceConfig(
        SemionPersistenceBackendType backend,
        String sqlitePath
) {
    public SemionPersistenceConfig {
        backend = backend == null ? SemionPersistenceBackendType.FILE : backend;
        sqlitePath = Objects.requireNonNullElse(sqlitePath, "semiontd.db");
    }

    public static SemionPersistenceConfig defaultConfig() {
        return new SemionPersistenceConfig(SemionPersistenceBackendType.FILE, "semiontd.db");
    }
}
```

Recommended enum:

```java
public enum SemionPersistenceBackendType {
    FILE,
    SQLITE
}
```

If legacy `MONGODB` parse fallback is too noisy or brittle, keep `MONGODB` temporarily but mark it unsupported in factory and immediately fallback to FILE with a warning. Do not implement MongoDB.

**Step 4: Verify**

Run:

```bash
./gradlew compileJava compileGametestJava --no-daemon
```

Expected: success.

**Step 5: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackendType.java \
  src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceConfig.java \
  src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
rtk proxy git commit -m "refactor: narrow persistence config to file and sqlite"
```

---

### Task 2: Add SQLite JDBC + TSID dependencies and prove packaging compiles

**Objective:** Add SQLite and Hypersistence TSID dependencies using Fabric/Loom-compatible packaging and verify they compile before writing backend code.

**Files:**
- Modify: `build.gradle`

**Step 1: Add dependency**

Recommended pattern, matching existing included dependencies:

```gradle
implementation include("org.xerial:sqlite-jdbc:3.47.2.0")
implementation include("io.hypersistence:hypersistence-tsid:2.1.4")
```

Before implementation, verify latest stable/pinned version if desired. Pin a concrete version; do not use dynamic `+`.

**Step 2: Verify dependency resolution**

Run:

```bash
./gradlew dependencies --configuration runtimeClasspath --no-daemon
./gradlew compileJava --no-daemon
```

Expected: dependency resolves and compile succeeds.

**Step 3: Commit**

```bash
rtk proxy git add build.gradle
rtk proxy git commit -m "build: include sqlite and tsid runtimes"
```

---

### Task 2.5: Replace match UUID with TSID-backed MatchId

**Objective:** Merge the previous `id`/`match_uuid` idea into one domain identifier: `MatchId`, backed by a Hypersistence TSID 64-bit integer. `MatchResult` should no longer generate `UUID.randomUUID()` for match identity.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/game/MatchId.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/MatchResult.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/MatchResultRepository.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/AppliedMatchRepository.java`
- Modify: file repository implementations/tests that currently assume `UUID matchId`

**Implementation guidance:**

```java
public record MatchId(long value) {
    public MatchId {
        if (value <= 0) {
            throw new IllegalArgumentException("matchId must be positive");
        }
    }

    public static MatchId generate() {
        return new MatchId(TSID.Factory.getTsid().toLong());
    }
}
```

- Verified Hypersistence TSID 2.1.4 exposes `io.hypersistence.tsid.TSID` and `TSID.Factory.getTsid()`; there is no `TsidCreator` class in this artifact.
- Keep player IDs as `UUID`; this change is only for match identity.
- Repository APIs should accept `MatchId` or `long` consistently; prefer `MatchId` at domain/repository boundaries and unwrap to `long` only inside SQLite binding.
- File persistence may serialize `MatchId` as a number or string, but it must round-trip without losing the 64-bit value.
- Add tests that generate multiple `MatchId`s, assert positivity/uniqueness in a small sample, and assert `MatchResult` JSON/file/SQLite round-trips preserve the same ID.

**Verify:**

```bash
./gradlew test gametest --no-daemon
```

Expected: success.

---

### Task 3: Introduce repository composition seam

**Objective:** Let backends provide repositories, so gameplay code stops constructing file repositories directly.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/SemionRepositories.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackend.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/FilePersistenceBackend.java`
- Create or modify: `src/main/java/kim/biryeong/semiontd/progression/ProgressionRepository.java`
- Modify: `src/main/java/kim/biryeong/semiontd/progression/SemionProgressionStore.java`

**Step 1: Define `ProgressionRepository`**

```java
public interface ProgressionRepository {
    SemionPlayerProfile getOrCreateProfile(UUID playerId, String playerName);

    SemionPlayerProfile putProfile(UUID playerId, SemionPlayerProfile profile);

    Optional<Map<UUID, MatchProgressionReward>> recordMatch(
            MatchResult matchResult,
            ProgressionConfig progressionConfig
    );
}
```

Make `SemionProgressionStore implements ProgressionRepository`.

**Step 2: Define `SemionRepositories`**

```java
public record SemionRepositories(
        MatchResultRepository matchResults,
        AppliedMatchRepository appliedMatches,
        ProgressionRepository progression,
        RatingRepository ratings
) {
    public SemionRepositories {
        Objects.requireNonNull(matchResults, "matchResults");
        Objects.requireNonNull(appliedMatches, "appliedMatches");
        Objects.requireNonNull(progression, "progression");
        Objects.requireNonNull(ratings, "ratings");
    }
}
```

If no file rating repository exists yet, either create a simple JSON `FileRatingRepository` in this task or defer `ratings` from `SemionRepositories` until Task 6. Prefer creating `FileRatingRepository` only if needed to avoid nulls.

**Step 3: Extend backend interface**

```java
public interface SemionPersistenceBackend extends AutoCloseable {
    SemionPersistenceBackendType type();
    void initialize();
    SemionRepositories repositories();
    @Override
    void close();
}
```

**Step 4: Update `FilePersistenceBackend`**

It should construct:

- `FileMatchResultRepository(configDir.resolve("match-results.json"))`
- `FileAppliedMatchRepository(configDir.resolve("progression-applied-matches.json"))`
- `SemionProgressionStore(configDir.resolve("profiles.json"))`
- file or in-memory rating repository depending on whether `FileRatingRepository` is added.

**Step 5: Verify**

Run:

```bash
./gradlew compileJava --no-daemon
```

Expected: success.

**Step 6: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/persistence \
  src/main/java/kim/biryeong/semiontd/progression
rtk proxy git commit -m "refactor: expose persistence repositories from backend"
```

---

### Task 4: Refactor services/managers to use injected repositories

**Objective:** Remove hard-coded file repository construction from `ProgressionService` and `SemionGameManager`.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/progression/ProgressionService.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java`

**Step 1: Refactor `ProgressionService` constructor**

Preferred constructor:

```java
public ProgressionService(
        ProgressionConfig progressionConfig,
        ProgressionRepository store,
        AppliedMatchRepository appliedMatchRepository
) {
    this.progressionConfig = progressionConfig;
    this.store = Objects.requireNonNull(store, "store");
    this.appliedMatchRepository = Objects.requireNonNull(appliedMatchRepository, "appliedMatchRepository");
}
```

Keep the path-based constructor temporarily if tests or call sites still need it, but route it through file repositories.

**Step 2: Refactor `SemionGameManager.configure`**

Add a `SemionRepositories` parameter or a separate `configurePersistence` method. Recommended to avoid bloating the existing config method:

```java
public void configurePersistence(SemionRepositories repositories) {
    this.matchResultRepository = repositories.matchResults();
    this.progressionService = new ProgressionService(
            progressionConfig,
            repositories.progression(),
            repositories.appliedMatches()
    );
}
```

If `progressionConfig` changes on reload, ensure `ProgressionService` is rebuilt with the latest config and same backend repositories.

**Step 3: Preserve FILE behavior**

Existing file paths must remain equivalent:

- `profiles.json`
- `match-results.json`
- `progression-applied-matches.json`
- `build_guides.json`

**Step 4: Verify**

Run:

```bash
./gradlew compileJava compileGametestJava --no-daemon
```

Expected: success.

**Step 5: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/progression/ProgressionService.java \
  src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java
rtk proxy git commit -m "refactor: inject persistence repositories into game services"
```

---

### Task 5: Add backend factory and runtime wiring

**Objective:** Make `configs.persistence()` actually select FILE or SQLITE at runtime, while keeping FILE default.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackends.java`
- Modify: `src/main/java/kim/biryeong/semiontd/SemionTd.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java` if needed for backend close/reload behavior

**Step 1: Create factory**

```java
public final class SemionPersistenceBackends {
    public static SemionPersistenceBackend create(Path configDir, SemionPersistenceConfig config) {
        SemionPersistenceConfig effective = config == null ? SemionPersistenceConfig.defaultConfig() : config;
        return switch (effective.backend()) {
            case SQLITE -> new SqlitePersistenceBackend(configDir, effective.sqlitePath());
            case FILE -> new FilePersistenceBackend(configDir);
        };
    }
}
```

If `SqlitePersistenceBackend` does not exist yet, create a temporary stub in Task 7 instead and keep factory compile until then. Prefer moving this task after Task 7 if needed.

**Step 2: Wire in `SemionTd`**

Store backend as field:

```java
private SemionPersistenceBackend persistenceBackend;
```

During initialize:

```java
persistenceBackend = SemionPersistenceBackends.create(configDir, configs.persistence());
persistenceBackend.initialize();
```

Pass repositories to manager:

```java
gameManager.configurePersistence(persistenceBackend.repositories());
```

Register close:

```java
ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
    if (persistenceBackend != null) {
        persistenceBackend.close();
    }
});
```

**Step 3: Verify**

Run:

```bash
./gradlew compileJava --no-daemon
```

Expected: success.

**Step 4: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/SemionTd.java \
  src/main/java/kim/biryeong/semiontd/persistence/SemionPersistenceBackends.java
rtk proxy git commit -m "feat: select persistence backend from config"
```

---

### Task 6: Add SQLite schema/backend skeleton

**Objective:** Add SQLite connection lifecycle, pragmas, schema initialization, and idempotent initialization.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java`
- Create: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteSchema.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Step 1: Write failing test**

Add GameTest:

```java
@GameTest(template = EMPTY_STRUCTURE)
public void sqliteBackendCreatesDatabaseFile(GameTestHelper context) {
    // tempDir
    // new SqlitePersistenceBackend(tempDir, "semiontd.db")
    // initialize
    // assert db exists
    // close
    // initialize again or create new backend, assert no failure
}
```

**Step 2: Implement backend**

Key behavior:

- Resolve DB path from `configDir` + `sqlitePath`.
- Open `jdbc:sqlite:<path>` via `DriverManager`.
- Apply pragmas.
- Run `SqliteSchema.initialize(connection)`.
- Build `SemionRepositories` from SQLite repositories once those exist; until then, this task can initialize repositories in later tasks.
- `close()` closes connection and logs warning instead of throwing runtime exceptions.

**Step 3: Implement schema**

Use `CREATE TABLE IF NOT EXISTS` and `PRAGMA user_version`.

**Step 4: Verify**

Run:

```bash
./gradlew test gametest --no-daemon
```

Expected: success.

**Step 5: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/persistence/sqlite \
  src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
rtk proxy git commit -m "feat: add sqlite persistence backend skeleton"
```

---

### Task 7: Add SQLite applied-match repository

**Objective:** Implement SQLite applied-match idempotency in repository code using `(match_id, subsystem)` as the logical key, without adding a database primary key/unique constraint.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteAppliedMatchRepository.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Step 1: Write failing tests**

Add tests equivalent to file repository behavior:

- `sqliteAppliedMatchRepositoryRejectsDuplicateSubsystem`
- `sqliteAppliedMatchRepositorySeparatesSubsystems`
- `sqliteAppliedMatchRepositoryPersistsAfterReopen`

**Step 2: Implement repository**

Behavior:

```java
@Override
public synchronized boolean hasApplied(MatchId matchId, String subsystem) { ... }

@Override
public synchronized boolean markApplied(MatchId matchId, String subsystem, long appliedAtEpochMillis) {
    // SELECT by match_id + subsystem first.
    // If present, return false.
    // Otherwise INSERT into applied_matches and return true.
    // This relies on the mod's current single-server/synchronized usage, not DB constraints.
}
```

Validation should match `FileAppliedMatchRepository.key(...)`: non-null match ID, non-blank subsystem.

**Step 3: Verify**

Run:

```bash
./gradlew test gametest --no-daemon
```

Expected: success.

**Step 4: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteAppliedMatchRepository.java \
  src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java \
  src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
rtk proxy git commit -m "feat: add sqlite applied match repository"
```

---

### Task 8: Add SQLite match-result repository

**Objective:** Save and reload `MatchResult` via SQLite with no duplicate rows and no schema overreach.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteMatchResultRepository.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Step 1: Write failing tests**

Add tests:

- `sqliteMatchResultRepositoryRoundTripsMatchResult`
- `sqliteMatchResultRepositoryPersistsAfterReopen`
- `sqliteMatchResultRepositoryDoesNotDuplicateMatchId`

Reuse existing match result builder/helper around the current `match-results.json` test if available.

**Step 2: Implement repository**

Use Gson with pretty printing disabled or enabled consistently.

`saveMatchResult`:

- require non-null `MatchResult`.
- query by `match_id` first; if a row already exists, preserve the first payload and return without inserting a duplicate.
- store `match_id`, `started_at_epoch_millis`, `ended_at_epoch_millis`, `final_round`, `payload_json`, `created_at_epoch_millis`.

`findMatchResult`:

- query by `match_id`.
- parse `payload_json` into `MatchResult`.
- invalid JSON should log warning and return `Optional.empty()`.

**Step 3: Verify**

Run:

```bash
./gradlew test gametest --no-daemon
```

Expected: success.

**Step 4: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteMatchResultRepository.java \
  src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java \
  src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
rtk proxy git commit -m "feat: add sqlite match result repository"
```

---

### Task 9: Add SQLite rating repository without rating math

**Objective:** Implement passive `RatingRepository` storage for existing `SemionRatingProfile`, preparing later ELO/TrueSkill work without computing deltas.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteRatingRepository.java`
- Possibly create: `src/main/java/kim/biryeong/semiontd/persistence/FileRatingRepository.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Step 1: Write failing tests**

Add:

- `sqliteRatingRepositoryRoundTripsProfile`
- `sqliteRatingRepositoryPersistsAfterReopen`
- `sqliteRatingRepositoryRejectsInvalidProfileViaDomainRecord`

**Step 2: Implement repository**

`saveProfile(UUID, SemionRatingProfile)` performs upsert into `rating_profiles`.

`findProfile(UUID)` returns `Optional<SemionRatingProfile>`.

**Step 3: Verify**

Run:

```bash
./gradlew test gametest --no-daemon
```

Expected: success.

**Step 4: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteRatingRepository.java \
  src/main/java/kim/biryeong/semiontd/persistence/FileRatingRepository.java \
  src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java \
  src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
rtk proxy git commit -m "feat: add sqlite rating profile repository"
```

---

### Task 10: Add SQLite progression repository or explicitly defer it

**Objective:** Decide whether progression profile persistence moves to SQLite in this PR or remains file-backed until a transaction-focused follow-up.

**Recommended decision:** Implement `ProgressionRepository` seam now, but defer full SQLite progression migration only if task size grows too much. If `backend=SQLITE` is marketed as full backend, implement `SqliteProgressionRepository`; if not, document that only match/applied/rating repository foundations are SQLite-backed.

**Files if implementing:**
- Create: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteProgressionRepository.java`
- Modify: `src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`

**Step 1: Write failing tests**

- `sqliteProgressionRepositoryRoundTripsProfile`
- `sqliteProgressionRepositoryRecordsMatchRewards`
- `sqliteProgressionRepositoryPersistsAfterReopen`

**Step 2: Implement using JSON payload**

Use `progression_profiles` with `payload_json` of `SemionPlayerProfile`.

**Step 3: Verify**

Run:

```bash
./gradlew test gametest --no-daemon
```

Expected: success.

**Step 4: Commit**

```bash
rtk proxy git add src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqliteProgressionRepository.java \
  src/main/java/kim/biryeong/semiontd/persistence/sqlite/SqlitePersistenceBackend.java \
  src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
rtk proxy git commit -m "feat: add sqlite progression repository"
```

---

### Task 11: Add SQLite backend integration test through config selection

**Objective:** Prove `persistence.json` with `backend=SQLITE` selects SQLite backend and real repositories.

**Files:**
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`
- Modify: factory/backend files if gaps are found

**Step 1: Write failing test**

Test intent:

```java
@GameTest(template = EMPTY_STRUCTURE)
public void sqlitePersistenceConfigSelectsSqliteBackend(GameTestHelper context) {
    // temp configDir
    // write persistence.json with { "backend": "SQLITE", "sqlitePath": "test.db" }
    // load configs
    // SemionPersistenceBackends.create(tempDir, configs.persistence())
    // initialize
    // assert backend.type() == SQLITE
    // assert test.db exists
    // assert repositories work minimally
}
```

**Step 2: Verify**

Run:

```bash
./gradlew test gametest --no-daemon
```

Expected: success.

**Step 3: Commit**

```bash
rtk proxy git add src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java \
  src/main/java/kim/biryeong/semiontd/persistence
rtk proxy git commit -m "test: verify sqlite backend selection"
```

---

### Task 12: Final validation and cleanup

**Objective:** Ensure implementation is safe, scoped, and ready for PR.

**Files:**
- Potential cleanup across touched files.

**Step 1: Run full validation**

```bash
./gradlew test gametest --no-daemon
./gradlew build --no-daemon
```

If dependency/runtime issues are suspected, also run:

```bash
./gradlew dependencies --configuration runtimeClasspath --no-daemon
```

**Step 2: Check generated artifacts**

```bash
rtk proxy git status --short --branch
```

Ensure no generated DB files are staged:

```text
*.db
*.db-wal
*.db-shm
.codegraph/
```

**Step 3: Review diff**

```bash
rtk proxy git diff --stat
rtk proxy git diff -- build.gradle src/main/java/kim/biryeong/semiontd/persistence src/main/java/kim/biryeong/semiontd/progression src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java src/main/java/kim/biryeong/semiontd/SemionTd.java src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
```

Check:

- FILE default preserved.
- SQLite opt-in only.
- No MongoDB dependency.
- No rating math.
- No generated DB artifacts.
- Backend close path exists.
- GameTest temp dirs used for DB tests.

**Step 4: Final commit if cleanup needed**

```bash
rtk proxy git add <intended files only>
rtk proxy git commit -m "test: validate sqlite persistence foundation"
```

---

## Acceptance Criteria

The implementation is complete only if all are true:

- `persistence.json` default remains `FILE`.
- MongoDB is not implemented and no MongoDB dependency exists.
- `backend=SQLITE` creates/opens the configured SQLite DB file.
- SQLite schema initializes idempotently.
- `SqliteAppliedMatchRepository` preserves duplicate-subsystem idempotency.
- `SqliteMatchResultRepository` can save/find a `MatchResult` after close/reopen.
- `SqliteRatingRepository` can save/find a `SemionRatingProfile` after close/reopen.
- If included, `SqliteProgressionRepository` can save/find profiles and record match rewards.
- `SemionTd` uses `configs.persistence()` to build the backend.
- `SemionGameManager` and `ProgressionService` no longer hard-code file repositories in the primary runtime path.
- SQLite backend is closed on server stopping.
- `./gradlew test gametest --no-daemon` passes.
- `./gradlew build --no-daemon` passes after SQLite dependency inclusion.
- No `.db`, `.db-wal`, `.db-shm`, or `.codegraph/` files are committed.

---

## Review Checklist for DB/Persistence Subagent

Run a read-only review against the final diff and answer PASS/FAIL for each:

1. Is SQLite opt-in and FILE default preserved?
2. Is MongoDB truly out of scope with no new dependency or live config path?
3. Are SQLite paths resolved relative to config dir unless absolute?
4. Are parent directories created before opening DB?
5. Are `WAL`, `synchronous=NORMAL`, and `busy_timeout` applied without enabling schema-level domain constraints?
6. Is schema initialization idempotent?
7. Does the schema avoid `NOT NULL`, `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, and `CHECK` constraints for domain invariants?
8. Does duplicate `markApplied` return `false` without throwing?
9. Are repository methods synchronized or otherwise safe for current single-server usage?
10. Does backend close release the JDBC connection?
11. Are temp-file DB tests using temp directories and cleaning by omission rather than repo paths?
12. Are generated DB/WAL files excluded from commits?
13. Does no rating math sneak into this patch?
14. Does `SemionGameManager.finishActiveGame()` still save match result before applying progression?
15. Is there an obvious later seam for `RatingService.applyMatchResult(...)`?

---

## Future Follow-Up After This Plan

Once this SQLite foundation is merged, the next feature plan should be:

```text
feat: add rating service skeleton and ELO baseline calculator
```

It should use:

- `RatingRepository`
- `applied_matches` subsystem `rating`
- `MatchResultRepository`
- later `rating_events` table for audit/replay

Do not start that until SQLite backend tests are green.
