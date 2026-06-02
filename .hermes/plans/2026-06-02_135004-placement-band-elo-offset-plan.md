# Placement Band ELO Offset Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Change multi-team ELO so placements are split into positive/neutral/negative bands instead of pure all-pairwise rank comparisons: for 4 teams, 1st/2nd gain and 3rd/4th lose; for 5 teams, 1st/2nd gain, 3rd is neutral, 4th/5th lose.

**Architecture:** Keep `RatingParticipant.placement` and current eligibility rules. Replace `EloRatingCalculator.placementBaseDelta(...)` with a placement-band target score model that still uses expected-score logic for rating strength and keeps team-size/contribution multipliers. Update rating policy docs and tests to make the new sign policy explicit.

**Tech Stack:** Java 21, JUnit 5, Fabric GameTest, Gradle.

---

## Confirmed Current Code Facts

- Current branch: `feat/multiteam-placement-elo`.
- Worktree was clean when this plan was written.
- Current calculator file: `src/main/java/kim/biryeong/semiontd/rating/EloRatingCalculator.java`.
- Current calculator uses pairwise placement comparison:
  - better placement vs opponent = actual `1.0`
  - worse placement vs opponent = actual `0.0`
  - same placement = actual `0.5`
  - average over opponents, then `kFactor * averageScoreDelta * teamSizeMultiplier`
- Current behavior already gives 3-team equal-rating deltas of approximately `+16, 0, -16`.
- Current 4-team equal-rating behavior would be `+16, +5, -5, -16`, so it already has the requested signs but should be encoded as policy, not incidental pairwise math.
- Current 5-team equal-rating behavior would be `+16, +8, 0, -8, -16`, matching the requested signs.
- The implementation request is therefore best treated as: make the top-half/bottom-half band policy explicit and stable, then adjust tests/docs so future changes do not accidentally revert it.

## Proposed Rating Policy

### Placement bands

For `teamCount = n` and `placement = p`:

```text
positiveCutoff = floor(n / 2)
neutralPlacement = (n is odd) ? (n + 1) / 2 : none
negativeStart = positiveCutoff + 1 for even n
negativeStart = neutralPlacement + 1 for odd n
```

Expected signs:

```text
2 teams: 1 +, 2 -
3 teams: 1 +, 2 0, 3 -
4 teams: 1 +, 2 +, 3 -, 4 -
5 teams: 1 +, 2 +, 3 0, 4 -, 5 -
```

### Recommended scoring model

Use a deterministic `placementTargetScore(teamCount, placement)` instead of only pairwise actual scores.

For supported `2..5` teams:

```text
2 teams: [1.00, 0.00]
3 teams: [1.00, 0.50, 0.00]
4 teams: [1.00, 0.90, 0.10, 0.00]
5 teams: [1.00, 0.90, 0.50, 0.10, 0.00]
```

The implementation uses buffered top/bottom band scores instead of a purely linear scale:

```java
private static double placementTargetScore(int teamCount, int placement) {
    if (teamCount <= 1) {
        return DRAW_SCORE;
    }
    if (placement <= 1) {
        return 1.0;
    }
    if (placement >= teamCount) {
        return 0.0;
    }
    if (teamCount % 2 == 1 && placement == (teamCount + 1) / 2) {
        return DRAW_SCORE;
    }
    return placement <= teamCount / 2
            ? TOP_HALF_TARGET_SCORE
            : BOTTOM_HALF_TARGET_SCORE;
}
```

Then compare the participant's target score against their expected match score:

```java
baseDelta = kFactor * (targetScore - averageExpectedScore) * teamSizeMultiplier;
```

Where `averageExpectedScore` is the average `expectedScore(ownMu, opponentMu)` over all opponents on other teams.

This keeps normal ELO strength sensitivity:

- High-rated 2nd place in a 4-team match usually still gains; it should only lose when heavily favored against the field.
- Low-rated 2nd place in a 4-team match gains more.
- Equal-rated teams follow the exact sign pattern requested.

### Why this instead of hand-coded offsets?

Avoid hardcoding `+/-` deltas because that would ignore opponent rating strength. The target-score approach encodes the requested placement band while preserving ELO's central property: outperforming expectation gains more, underperforming expectation loses more.

## Non-goals

- Do not change eligibility rules beyond tests needed for this policy.
- Do not change contribution weighting formula except for ensuring neutral base delta remains neutral after multiplier.
- Do not introduce TrueSkill/TrueSkill2 in this PR.
- Do not change persistence schema.
- Do not change `wins/losses` counters unless the user explicitly wants 2nd place in 4/5-team games counted as a non-loss. Current policy remains: `wins = first-place`, `losses = non-first`.

---

## Task 1: Add failing tests for explicit placement-band signs

**Objective:** Lock the requested signs for 2/3/4/5-team equal-rating matches.

**Files:**
- Modify: `src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java`

**Step 1: Add 4-team sign test**

Add a test like:

```java
@Test
void fourTeamPlacementEloRewardsTopHalfAndPenalizesBottomHalf() {
    RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
            new MatchId(8L),
            1000L,
            List.of(
                    participant("first-4", TeamId.RED, true, 1),
                    participant("second-4", TeamId.BLUE, false, 2),
                    participant("third-4", TeamId.GREEN, false, 3),
                    participant("fourth-4", TeamId.YELLOW, false, 4)
            )
    ));

    List<Integer> deltas = result.adjustments().stream()
            .map(RatingAdjustment::displayEloDelta)
            .toList();
    assertTrue(deltas.get(0) > 0);
    assertTrue(deltas.get(1) > 0);
    assertTrue(deltas.get(2) < 0);
    assertTrue(deltas.get(3) < deltas.get(2));
}
```

**Step 2: Strengthen 5-team test**

Extend the existing `fiveTeamPlacementEloDeltasAreMonotonicByPlacement` test to assert:

```java
assertTrue(deltas.get(0) > 0);
assertTrue(deltas.get(1) > 0);
assertEquals(0, deltas.get(2));
assertTrue(deltas.get(3) < 0);
assertTrue(deltas.get(4) < deltas.get(3));
```

**Step 3: Run tests and verify current/future behavior**

Run:

```bash
./gradlew test --tests '*EloRatingCalculatorTest' --no-daemon
```

Expected before implementation:

- The tests may already pass under current pairwise math for equal ratings.
- If they pass, keep them as regression tests and proceed to make the policy explicit in code.

**Step 4: Commit tests only if using strict TDD commits**

```bash
git add src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java
git commit -m "test: lock placement band elo signs"
```

---

## Task 2: Replace implicit pairwise actual-score math with explicit target-score math

**Objective:** Make the 1/2 split policy explicit in `EloRatingCalculator`.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/rating/EloRatingCalculator.java`
- Test: `src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java`

**Step 1: Modify `placementBaseDelta(...)`**

Replace the pairwise `actualScore - expectedScore` accumulation with:

```java
private double placementBaseDelta(List<RatingParticipant> participants, RatingParticipant participant) {
    List<RatingParticipant> opponents = participants.stream()
            .filter(opponent -> !opponent.teamId().equals(participant.teamId()))
            .toList();
    if (opponents.isEmpty()) {
        return 0.0;
    }

    int teamCount = distinctTeamCount(participants);
    double targetScore = placementTargetScore(teamCount, participant.placement());
    double averageExpectedScore = opponents.stream()
            .mapToDouble(opponent -> expectedScore(participant.currentProfile().mu(), opponent.currentProfile().mu()))
            .average()
            .orElse(0.5);
    double teamSizeMultiplier = teamSizeMultiplier(participants, participant, opponents.size());
    return kFactor * (targetScore - averageExpectedScore) * teamSizeMultiplier;
}
```

**Step 2: Add helper methods**

```java
private static int distinctTeamCount(List<RatingParticipant> participants) {
    return (int) participants.stream()
            .map(RatingParticipant::teamId)
            .distinct()
            .count();
}

private static double placementTargetScore(int teamCount, int placement) {
    if (teamCount <= 1) {
        return DRAW_SCORE;
    }
    return 1.0 - ((double) placement - 1.0) / ((double) teamCount - 1.0);
}
```

**Step 3: Remove no-longer-needed `actualScore(...)` if unused**

If `actualScore(...)`, `WIN_SCORE`, and `LOSS_SCORE` become unused, remove them. Keep `DRAW_SCORE` for neutral target fallback.

**Step 4: Run focused calculator tests**

Run:

```bash
./gradlew test --tests '*EloRatingCalculatorTest' --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/rating/EloRatingCalculator.java src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java
git commit -m "feat: make placement band elo offsets explicit"
```

---

## Task 3: Add favorite/underdog tests for 2nd place behavior

**Objective:** Prove that 2nd place gains in equal-rating 4/5-team games, but ELO expectation still matters.

**Files:**
- Modify: `src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java`

**Step 1: Add equal-rating 4-team exact-ish expectation test**

For equal ratings and K=32, expected display deltas should be:

```text
4 teams: +16, +13, -13, -16
5 teams: +16, +13, 0, -13, -16
```

Add assertions if rounding is stable:

```java
assertEquals(List.of(16, 13, -13, -16), deltas);
```

and for 5-team:

```java
assertEquals(List.of(16, 13, 0, -13, -16), deltas);
```

If contribution multipliers or future config make exact display values brittle, assert signs plus monotonic ordering instead.

**Step 2: Add high-rated 2nd place expectation test**

Create a 4-team match where the 2nd-place player is much stronger than all others. Assert that the delta is smaller than the equal-rating 2nd-place delta, and decide policy with the user if it goes negative.

Recommended assertion:

```java
assertTrue(highRatedSecondDelta < equalRatedSecondDelta);
```

Do not force it positive unless product policy says placement band should override rating expectation absolutely.

**Step 3: Run focused tests**

```bash
./gradlew test --tests '*EloRatingCalculatorTest' --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 4: Commit**

```bash
git add src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java
git commit -m "test: cover placement band expectation sensitivity"
```

---

## Task 4: Update rating policy documentation

**Objective:** Document the top-half/bottom-half policy so server operators understand the behavior.

**Files:**
- Modify: `docs/rating-policy.md`

**Step 1: Update ELO calculation section**

Replace the pairwise actual-score bullets with target-score bullets:

```markdown
For each eligible participant in a 2-5 team placement match:

- Placement maps to a target score from `1.0` for first place to `0.0` for last place.
- Equal-rating examples with K=32:
  - 2 teams: `+16, -16`
  - 3 teams: `+16, 0, -16`
  - 4 teams: `+16, +8, -8, -16`
  - 5 teams: `+16, +8, 0, -8, -16`
- This means the top half gains and bottom half loses; odd team counts have a neutral middle placement.
- The target score is compared to average ELO expected score against other teams, so stronger players still need stronger finishes to gain the same amount.
- A team-size multiplier `min(1.0, opposingParticipantCount / ownTeamSize)` keeps uneven team-size matches close to the current aggregate volatility.
```

**Step 2: Keep contribution weighting section sign wording accurate**

Current docs say winners gain and losers lose. With the new policy, 2nd place in 4/5-team games can gain despite `winner == false`. Update the wording:

```markdown
Contribution weighting does not replace placement outcome: positive base deltas stay positive, negative base deltas stay negative, and neutral base deltas stay neutral.
```

Update formula labels from `winnerFinalDelta`/`loserFinalDelta` to:

```text
positiveFinalDelta = baseDelta * contributionMultiplier
negativeFinalDelta = baseDelta * (2.0 - contributionMultiplier)
neutralFinalDelta = 0
```

**Step 3: Run markdown-free validation via tests**

No markdown linter is configured. Run calculator tests as proxy:

```bash
./gradlew test --tests '*EloRatingCalculatorTest' --no-daemon
```

**Step 4: Commit**

```bash
git add docs/rating-policy.md
git commit -m "docs: describe placement band elo policy"
```

---

## Task 5: Run integration tests and GameTest compile/runtime

**Objective:** Ensure the policy change does not break rating service or GameTest integration.

**Files:**
- No direct edits expected unless tests reveal failures.

**Step 1: Run rating test suite**

```bash
./gradlew test --tests '*Rating*' compileGameTestJava --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 2: Run GameTest runtime**

```bash
./gradlew runGameTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 3: Run final full relevant validation**

```bash
./gradlew test compileGameTestJava runGameTest --no-daemon --rerun-tasks
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 4: Check diff hygiene**

```bash
git diff --check
```

Expected: no output.

---

## Task 6: Review, push, and update PR

**Objective:** Ship the change cleanly on the existing PR A branch.

**Files:**
- No direct edits expected unless review finds issues.

**Step 1: Inspect final diff**

```bash
git status --short --branch
git log --oneline --decorate -5
git diff origin/feat/multiteam-placement-elo..HEAD --stat
```

**Step 2: Optional self-review/security scan**

Check added lines for accidental risky patterns:

```bash
git diff origin/feat/multiteam-placement-elo..HEAD -- src docs | grep -E "password|secret|token|Runtime\.getRuntime|ProcessBuilder|exec\(|format\(|prepareStatement" || true
```

Expected: no suspicious additions relevant to this change.

**Step 3: Push**

```bash
git push origin feat/multiteam-placement-elo
```

**Step 4: Confirm PR state**

```bash
gh pr view 7 --json url,state,baseRefName,headRefName,mergeStateStatus,statusCheckRollup
```

Expected:

```text
state: OPEN
mergeStateStatus: CLEAN
```

---

## Risks / Tradeoffs

1. **Product interpretation:** There are two possible meanings of “2nd place should increase.”
   - Recommended: equal-rating 2nd place increases, but high-rated 2nd place can still lose if they underperform expectation.
   - Alternative: 2nd place always increases regardless of rating expectation. This is less ELO-like and can inflate rating.
2. **Contribution weighting wording:** Current code applies contribution multiplier based on `baseDelta >= 0`. That already supports 2nd-place positive deltas, but docs/tests should avoid “winner/loser” wording for sign behavior.
3. **Exact display deltas:** Exact values like `+13` depend on equal ratings, K=32, one participant per team, and neutral contribution. Tests should be explicit about those conditions.
4. **Wins/losses counters:** Keeping `wins = first place`, `losses = non-first` means 2nd place can gain ELO while still incrementing `losses`. This is consistent with prior docs but may look odd in UI.

## Open Question

Should 2nd place in a 4/5-team match **always** gain ELO, even when the player/team is heavily favored and therefore underperformed expectation?

Recommendation: **No.** Use the target-score ELO model so equal-rating 2nd place gains, but ELO expectation still controls upsets/favorites. This avoids ladder inflation and keeps the rating meaningful.
