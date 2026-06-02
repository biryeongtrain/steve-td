# 100-Player ELO Simulation Test Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Add a deterministic JVM test that simulates 100 players playing 1000 rating games, then reports and validates the resulting ELO distribution against an auto-generated latent skill pool.

**Architecture:** Keep this as a pure domain test under `src/test/java`, not a GameTest, because it exercises rating math rather than Minecraft runtime wiring. The test will create a seeded synthetic player pool with latent true skill, simulate deterministic noisy match performances, feed placements into `EloRatingCalculator`, update in-memory `PlayerRatingProfile`s, and assert that final ELO distribution is sane and correlated with the latent skill distribution.

**Tech Stack:** Java 21, JUnit 5, Gradle, existing `EloRatingCalculator`, `RatingMatchInput`, `RatingParticipant`, `PlayerRatingProfile`, `TeamId`, `MatchId`.

---

## Confirmed Current Code Facts

- Current branch when this plan was written: `feat/multiteam-placement-elo`.
- Worktree was clean when this plan was written.
- CodeGraph sync result: already up to date.
- Current rating calculator: `src/main/java/kim/biryeong/semiontd/rating/EloRatingCalculator.java`.
- Current rating calculator supports placement-band target scores:
  - 2 teams: `1.00, 0.00`
  - 3 teams: `1.00, 0.50, 0.00`
  - 4 teams: `1.00, 0.90, 0.10, 0.00`
  - 5 teams: `1.00, 0.90, 0.50, 0.10, 0.00`
- Current unit test anchor: `src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java`.
- `PlayerRatingProfile` is immutable, so simulation must replace a player's profile after each `RatingAdjustment`.
- `RatingParticipant` can carry explicit `placement`, `placementWeight`, and current profile.
- `TeamId` has exactly five values: `RED`, `BLUE`, `GREEN`, `YELLOW`, `PURPLE`, so a 5-player free-for-all maps cleanly to one participant per team.

## Product Interpretation

User request:

> 100명의 플레이어들이 1000게임을 모의로 플레이했을 때 스킬분포가 어떻게 되는지. 실력풀은 알아서 조정.

Interpretation for this test:

1. Create 100 synthetic players.
2. Assign each player a deterministic latent true skill.
3. Run 1000 simulated games.
4. Use current ELO calculator exactly as production rating math would.
5. Produce distribution metrics that answer “skill/rating distribution이 어떻게 되는지.”
6. Assert statistical sanity without making the test too brittle.

## Non-goals

- Do not modify production rating logic for this test.
- Do not introduce TrueSkill/TrueSkill2.
- Do not add persistence or file output in the test.
- Do not make the simulation random across runs.
- Do not require GameTest runtime for this pure math simulation.
- Do not assert exact final ELO per player unless values are intentionally snapshotted after a first deterministic run.

---

## Simulation Design

### Player pool

Use 100 players with deterministic IDs and names:

```text
player-000 .. player-099
```

Generate latent true skill with a seeded Gaussian distribution:

```java
trueSkill = clamp(1500.0 + random.nextGaussian() * 220.0, 900.0, 2100.0)
```

Rationale:

- Centered on current initial ELO `1500`.
- `220` standard deviation gives a meaningful spread without making every game deterministic.
- Clamp avoids pathological outliers that dominate a 1000-game test.
- Seeded random makes the test reproducible.

Recommended constants:

```java
private static final int PLAYER_COUNT = 100;
private static final int GAME_COUNT = 1000;
private static final int PLAYERS_PER_GAME = 5;
private static final long PLAYER_POOL_SEED = 0x5EED_100L;
private static final long MATCHMAKING_SEED = 0x5EED_1000L;
private static final double TRUE_SKILL_MEAN = 1500.0;
private static final double TRUE_SKILL_STDDEV = 220.0;
private static final double TRUE_SKILL_MIN = 900.0;
private static final double TRUE_SKILL_MAX = 2100.0;
private static final double PERFORMANCE_NOISE_STDDEV = 170.0;
```

### Matchmaking

Use one-player-per-team 5-team matches:

```text
1000 games * 5 participants = 5000 player-game samples
average games per player = 50
```

For each game:

1. Pick 5 distinct players uniformly without replacement.
2. Assign them to `TeamId.RED`, `BLUE`, `GREEN`, `YELLOW`, `PURPLE`.
3. Compute performance:

```java
performance = trueSkill + random.nextGaussian() * PERFORMANCE_NOISE_STDDEV
```

4. Sort descending by performance.
5. Placement is sorted index + 1.
6. Winner flag is `placement == 1`.
7. Feed the 5 `RatingParticipant`s into `EloRatingCalculator.calculate(...)`.
8. Apply resulting profiles back into the simulation player map.

### Why fixed 5-team games?

This directly exercises the newest placement-band policy:

```text
1st positive, 2nd positive, 3rd neutral, 4th negative, 5th negative
```

A later test can add mixed 2/3/4/5 team schedules, but fixed 5-team is the simplest targeted answer to the current request.

### Distribution output

The test should compute and print a compact deterministic summary via `System.out.println(...)` so it appears in Gradle test logs when needed:

```text
Simulated ELO distribution after 100 players / 1000 games:
trueSkill: min=..., p10=..., p25=..., median=..., p75=..., p90=..., max=..., mean=..., stddev=...
finalElo:  min=..., p10=..., p25=..., median=..., p75=..., p90=..., max=..., mean=..., stddev=...
correlation(trueSkill, finalMu)=...
topTrueSkillQuartileAverageElo=...
bottomTrueSkillQuartileAverageElo=...
secondPlaceNegativeRate=...
```

Do not write this to a repo file from the test. If a persistent report is desired later, add a separate non-test tool or documentation artifact.

---

## Acceptance Criteria

The deterministic simulation test should assert these invariants:

1. Exactly 100 players exist at the end.
2. Total games played across profiles is exactly `1000 * 5 = 5000`.
3. Every player has at least one simulated game.
4. No final `mu` or `displayElo` is NaN/infinite.
5. Final ELO distribution is ordered sanely:
   - `min < p10 < median < p90 < max`
   - `stddev > 0`
6. Final ELO tracks latent true skill:
   - Pearson correlation between `trueSkill` and final `mu` should be at least `0.65`.
   - Top true-skill quartile average final ELO should exceed bottom true-skill quartile average final ELO by at least `120`.
7. The placement-band fairness goal remains true statistically:
   - 2nd-place ELO delta should be negative in only a small fraction of 2nd-place finishes.
   - Recommended threshold: `secondPlaceNegativeRate <= 0.05` for this seeded pool.
8. The test finishes quickly enough for normal unit tests.
   - 1000 games × 5 participants is tiny; expected runtime should be well below one second on the JVM after Gradle startup.

Thresholds are intentionally broad. This is a statistical regression test, not a golden snapshot for every player.

---

## Task 1: Create simulation test skeleton

**Objective:** Add a dedicated test class with constants, player model, and no production changes.

**Files:**
- Create: `src/test/java/kim/biryeong/semiontd/rating/EloRatingDistributionSimulationTest.java`

**Step 1: Create test class**

Skeleton:

```java
package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class EloRatingDistributionSimulationTest {
    private static final int PLAYER_COUNT = 100;
    private static final int GAME_COUNT = 1000;
    private static final int PLAYERS_PER_GAME = 5;
    private static final long PLAYER_POOL_SEED = 0x5EED_100L;
    private static final long MATCHMAKING_SEED = 0x5EED_1000L;
    private static final double TRUE_SKILL_MEAN = 1500.0;
    private static final double TRUE_SKILL_STDDEV = 220.0;
    private static final double TRUE_SKILL_MIN = 900.0;
    private static final double TRUE_SKILL_MAX = 2100.0;
    private static final double PERFORMANCE_NOISE_STDDEV = 170.0;

    @Test
    void simulatedHundredPlayerPoolConvergesTowardLatentSkillDistribution() {
        // implemented in later tasks
    }

    private record SimulatedPlayer(
            UUID playerId,
            String playerName,
            double trueSkill,
            PlayerRatingProfile profile
    ) {
        SimulatedPlayer withProfile(PlayerRatingProfile nextProfile) {
            return new SimulatedPlayer(playerId, playerName, trueSkill, nextProfile);
        }
    }
}
```

**Step 2: Run compile/test to verify skeleton**

Run:

```bash
./gradlew test --tests '*EloRatingDistributionSimulationTest' --no-daemon
```

Expected after skeleton if test is empty: pass. If strict TDD is preferred, first add a failing assertion such as `assertEquals(PLAYER_COUNT, 0)` and then replace it in Task 2.

**Step 3: Commit if doing small commits**

```bash
git add src/test/java/kim/biryeong/semiontd/rating/EloRatingDistributionSimulationTest.java
git commit -m "test: add elo distribution simulation skeleton"
```

---

## Task 2: Generate deterministic true-skill pool

**Objective:** Build the 100-player latent skill pool.

**Files:**
- Modify: `src/test/java/kim/biryeong/semiontd/rating/EloRatingDistributionSimulationTest.java`

**Step 1: Add pool helper**

```java
private static List<SimulatedPlayer> createPlayers() {
    Random random = new Random(PLAYER_POOL_SEED);
    List<SimulatedPlayer> players = new ArrayList<>();
    for (int index = 0; index < PLAYER_COUNT; index++) {
        String name = "player-%03d".formatted(index);
        UUID playerId = UUID.nameUUIDFromBytes(name.getBytes());
        double trueSkill = clamp(TRUE_SKILL_MEAN + random.nextGaussian() * TRUE_SKILL_STDDEV, TRUE_SKILL_MIN, TRUE_SKILL_MAX);
        players.add(new SimulatedPlayer(playerId, name, trueSkill, PlayerRatingProfile.initial(playerId, name)));
    }
    return players;
}

private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
}
```

**Step 2: Add assertions**

Inside the test:

```java
List<SimulatedPlayer> players = createPlayers();
assertEquals(PLAYER_COUNT, players.size());
assertTrue(players.stream().map(SimulatedPlayer::playerId).distinct().count() == PLAYER_COUNT);
assertTrue(players.stream().mapToDouble(SimulatedPlayer::trueSkill).min().orElseThrow() >= TRUE_SKILL_MIN);
assertTrue(players.stream().mapToDouble(SimulatedPlayer::trueSkill).max().orElseThrow() <= TRUE_SKILL_MAX);
```

**Step 3: Run focused test**

```bash
./gradlew test --tests '*EloRatingDistributionSimulationTest' --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 3: Simulate 1000 games and apply rating adjustments

**Objective:** Run actual `EloRatingCalculator` over the synthetic pool.

**Files:**
- Modify: `src/test/java/kim/biryeong/semiontd/rating/EloRatingDistributionSimulationTest.java`

**Step 1: Store players in a mutable map**

```java
Map<UUID, SimulatedPlayer> playersById = players.stream()
        .collect(Collectors.toMap(SimulatedPlayer::playerId, Function.identity(), (left, right) -> left, HashMap::new));
```

Add imports:

```java
import java.util.function.Function;
import java.util.stream.Collectors;
```

**Step 2: Add match selection helper**

```java
private static List<SimulatedPlayer> pickPlayers(List<SimulatedPlayer> playerPool, Random random) {
    List<SimulatedPlayer> shuffled = new ArrayList<>(playerPool);
    Collections.shuffle(shuffled, random);
    return new ArrayList<>(shuffled.subList(0, PLAYERS_PER_GAME));
}
```

Add import:

```java
import java.util.Collections;
```

**Step 3: Add performance ranking helper**

Use a local record:

```java
private record MatchPerformance(SimulatedPlayer player, TeamId teamId, double performance, int placement) {
    MatchPerformance withPlacement(int nextPlacement) {
        return new MatchPerformance(player, teamId, performance, nextPlacement);
    }
}
```

Helper:

```java
private static List<MatchPerformance> rankPerformances(List<SimulatedPlayer> selected, Random random) {
    TeamId[] teams = TeamId.values();
    List<MatchPerformance> performances = new ArrayList<>();
    for (int index = 0; index < selected.size(); index++) {
        SimulatedPlayer player = selected.get(index);
        double performance = player.trueSkill() + random.nextGaussian() * PERFORMANCE_NOISE_STDDEV;
        performances.add(new MatchPerformance(player, teams[index], performance, 0));
    }
    performances.sort(Comparator.comparingDouble(MatchPerformance::performance).reversed());

    List<MatchPerformance> ranked = new ArrayList<>();
    for (int index = 0; index < performances.size(); index++) {
        ranked.add(performances.get(index).withPlacement(index + 1));
    }
    return ranked;
}
```

**Step 4: Feed `EloRatingCalculator`**

```java
EloRatingCalculator calculator = new EloRatingCalculator();
Random matchRandom = new Random(MATCHMAKING_SEED);
long negativeSecondPlaceDeltas = 0L;
long secondPlaceFinishes = 0L;

for (int gameIndex = 0; gameIndex < GAME_COUNT; gameIndex++) {
    List<SimulatedPlayer> selected = pickPlayers(new ArrayList<>(playersById.values()), matchRandom);
    List<MatchPerformance> ranked = rankPerformances(selected, matchRandom);
    List<RatingParticipant> participants = ranked.stream()
            .map(performance -> {
                SimulatedPlayer current = playersById.get(performance.player().playerId());
                return new RatingParticipant(
                        current.playerId(),
                        current.playerName(),
                        performance.teamId(),
                        performance.placement() == 1,
                        performance.placement(),
                        1.0,
                        current.profile()
                );
            })
            .toList();

    RatingMatchResult result = calculator.calculate(new RatingMatchInput(
            new MatchId(gameIndex + 1L),
            gameIndex + 1L,
            participants
    ));

    for (RatingAdjustment adjustment : result.adjustments()) {
        SimulatedPlayer current = playersById.get(adjustment.playerId());
        if (participants.stream().anyMatch(participant -> participant.playerId().equals(adjustment.playerId())
                && participant.placement() == 2)) {
            secondPlaceFinishes++;
            if (adjustment.displayEloDelta() < 0) {
                negativeSecondPlaceDeltas++;
            }
        }
        playersById.put(adjustment.playerId(), current.withProfile(adjustment.after()));
    }
}
```

Implementation may simplify the 2nd-place lookup by creating a `Map<UUID, Integer> placementByPlayerId` per match.

**Step 5: Add core accounting assertions**

```java
int totalGamesPlayed = playersById.values().stream()
        .mapToInt(player -> player.profile().gamesPlayed())
        .sum();
assertEquals(GAME_COUNT * PLAYERS_PER_GAME, totalGamesPlayed);
assertTrue(playersById.values().stream().allMatch(player -> player.profile().gamesPlayed() > 0));
assertEquals(GAME_COUNT, secondPlaceFinishes);
```

**Step 6: Run focused test**

```bash
./gradlew test --tests '*EloRatingDistributionSimulationTest' --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 4: Add distribution summary helpers and statistical assertions

**Objective:** Turn the simulation into an actionable regression test and readable report.

**Files:**
- Modify: `src/test/java/kim/biryeong/semiontd/rating/EloRatingDistributionSimulationTest.java`

**Step 1: Add distribution record**

```java
private record DistributionSummary(
        double min,
        double p10,
        double p25,
        double median,
        double p75,
        double p90,
        double max,
        double mean,
        double stddev
) {
}
```

**Step 2: Add percentile/summary helpers**

```java
private static DistributionSummary summarize(List<Double> values) {
    List<Double> sorted = values.stream().sorted().toList();
    double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    double variance = sorted.stream()
            .mapToDouble(value -> Math.pow(value - mean, 2.0))
            .average()
            .orElseThrow();
    return new DistributionSummary(
            sorted.getFirst(),
            percentile(sorted, 0.10),
            percentile(sorted, 0.25),
            percentile(sorted, 0.50),
            percentile(sorted, 0.75),
            percentile(sorted, 0.90),
            sorted.getLast(),
            mean,
            Math.sqrt(variance)
    );
}

private static double percentile(List<Double> sortedValues, double percentile) {
    if (sortedValues.isEmpty()) {
        throw new IllegalArgumentException("values cannot be empty");
    }
    double position = percentile * (sortedValues.size() - 1);
    int lower = (int) Math.floor(position);
    int upper = (int) Math.ceil(position);
    if (lower == upper) {
        return sortedValues.get(lower);
    }
    double weight = position - lower;
    return sortedValues.get(lower) * (1.0 - weight) + sortedValues.get(upper) * weight;
}
```

If the project source compatibility does not support `List.getFirst()` / `getLast()`, use `sorted.get(0)` and `sorted.get(sorted.size() - 1)`.

**Step 3: Add Pearson correlation**

```java
private static double pearsonCorrelation(List<SimulatedPlayer> players) {
    double trueSkillMean = players.stream().mapToDouble(SimulatedPlayer::trueSkill).average().orElseThrow();
    double muMean = players.stream().mapToDouble(player -> player.profile().mu()).average().orElseThrow();
    double numerator = 0.0;
    double trueSkillSquares = 0.0;
    double muSquares = 0.0;
    for (SimulatedPlayer player : players) {
        double trueSkillOffset = player.trueSkill() - trueSkillMean;
        double muOffset = player.profile().mu() - muMean;
        numerator += trueSkillOffset * muOffset;
        trueSkillSquares += trueSkillOffset * trueSkillOffset;
        muSquares += muOffset * muOffset;
    }
    return numerator / Math.sqrt(trueSkillSquares * muSquares);
}
```

**Step 4: Add quartile gap helper**

```java
private static double trueSkillQuartileEloGap(List<SimulatedPlayer> players) {
    List<SimulatedPlayer> sortedBySkill = players.stream()
            .sorted(Comparator.comparingDouble(SimulatedPlayer::trueSkill))
            .toList();
    int quartileSize = sortedBySkill.size() / 4;
    double bottomAverage = sortedBySkill.subList(0, quartileSize).stream()
            .mapToDouble(player -> player.profile().mu())
            .average()
            .orElseThrow();
    double topAverage = sortedBySkill.subList(sortedBySkill.size() - quartileSize, sortedBySkill.size()).stream()
            .mapToDouble(player -> player.profile().mu())
            .average()
            .orElseThrow();
    return topAverage - bottomAverage;
}
```

**Step 5: Add final assertions**

Recommended assertions:

```java
List<SimulatedPlayer> finalPlayers = new ArrayList<>(playersById.values());
DistributionSummary trueSkillSummary = summarize(finalPlayers.stream().map(SimulatedPlayer::trueSkill).toList());
DistributionSummary eloSummary = summarize(finalPlayers.stream().map(player -> player.profile().mu()).toList());
double correlation = pearsonCorrelation(finalPlayers);
double quartileGap = trueSkillQuartileEloGap(finalPlayers);
double secondPlaceNegativeRate = (double) negativeSecondPlaceDeltas / secondPlaceFinishes;

assertTrue(Double.isFinite(correlation));
assertTrue(correlation >= 0.65, "correlation=" + correlation);
assertTrue(quartileGap >= 120.0, "quartileGap=" + quartileGap);
assertTrue(secondPlaceNegativeRate <= 0.05, "secondPlaceNegativeRate=" + secondPlaceNegativeRate);
assertTrue(eloSummary.min() < eloSummary.p10());
assertTrue(eloSummary.p10() < eloSummary.median());
assertTrue(eloSummary.median() < eloSummary.p90());
assertTrue(eloSummary.p90() < eloSummary.max());
assertTrue(eloSummary.stddev() > 0.0);
```

**Step 6: Print summary**

```java
System.out.printf(
        "Simulated ELO distribution after %d players / %d games:%ntrueSkill=%s%nfinalElo=%s%ncorrelation=%.3f%nquartileGap=%.1f%nsecondPlaceNegativeRate=%.3f%n",
        PLAYER_COUNT,
        GAME_COUNT,
        trueSkillSummary,
        eloSummary,
        correlation,
        quartileGap,
        secondPlaceNegativeRate
);
```

**Step 7: Run focused test**

```bash
./gradlew test --tests '*EloRatingDistributionSimulationTest' --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

If thresholds fail on the first deterministic run, inspect the printed summary and adjust the threshold only if the distribution is still product-reasonable. Do not loosen thresholds to hide broken behavior.

---

## Task 5: Decide whether to tag as simulation or keep in normal unit suite

**Objective:** Avoid making the regular test suite noisy or slow.

**Files:**
- Possibly modify: `src/test/java/kim/biryeong/semiontd/rating/EloRatingDistributionSimulationTest.java`

Recommended default: keep it in the normal unit test suite because 1000 games is small.

If runtime turns out unexpectedly high or output is too noisy, add:

```java
import org.junit.jupiter.api.Tag;

@Tag("simulation")
final class EloRatingDistributionSimulationTest {
    ...
}
```

Then decide Gradle tag inclusion/exclusion separately. Do not add Gradle config unless needed.

---

## Task 6: Run validation suite

**Objective:** Verify the simulation test and nearby rating logic.

**Commands:**

Focused simulation test:

```bash
./gradlew test --tests '*EloRatingDistributionSimulationTest' --no-daemon
```

Rating tests:

```bash
./gradlew test --tests '*Rating*' --no-daemon
```

GameTest compile, because rating work in this repo should keep Fabric test sources compiling:

```bash
./gradlew compileGameTestJava --no-daemon
```

Optional full runtime gate if implementation changes anything outside the pure test file:

```bash
./gradlew runGameTest --no-daemon
```

Final hygiene:

```bash
git diff --check
```

Expected final result:

```text
BUILD SUCCESSFUL
```

---

## Risks / Tradeoffs

1. **Statistical brittleness:** Exact final ELO values are sensitive to calculator changes. Prefer broad correlation/quartile/negative-rate assertions over per-player golden values.
2. **Output noise:** `System.out` in tests can clutter logs. Keep output to one compact summary block.
3. **Simulation vs unit test:** This is technically a deterministic simulation/regression test, not a pure unit test. That is acceptable because it directly validates a product policy question.
4. **Skill pool assumptions:** A Gaussian latent skill pool is realistic enough for this test, but not a production matchmaking model.
5. **5-team-only schedule:** This targets the latest placement-band policy. Mixed team-count simulation can be added later if desired.
6. **2nd-place negative-rate threshold:** `<= 5%` is a policy assertion. If the real seeded run shows a much lower rate, tighten it. If it is higher, inspect whether current `0.90` target score is still satisfying the user's fairness requirement.

## Open Questions

None blocking. I will proceed with these assumptions unless told otherwise:

- Use fixed 5-team, one-player-per-team matches.
- Use seeded Gaussian true-skill pool centered at 1500 with stddev 220.
- Use performance noise stddev 170.
- Keep the simulation test in normal JVM unit tests unless runtime/output becomes problematic.
