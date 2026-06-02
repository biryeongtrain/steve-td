# Multi-Team Rating / TrueSkill Migration Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Support rating changes for 3, 4, and 5 team SemionTD matches, while leaving a safe path from the current ELO implementation to TrueSkill-style rating.

**Architecture:** Do not replace the current ELO PR in one risky step. First generalize the rating input/model and add deterministic multi-team placement ELO so 3-5 team matches are rated immediately. Then add a shadow TrueSkill-style calculator and simulation suite; switch the visible ladder only after its parameters and migration policy are validated.

**Tech Stack:** Java 21, Fabric Loom GameTest, JUnit 5, current `RatingCalculator` abstraction, SQLite/File rating persistence, CodeGraph.

---

## Confirmed current code facts

- Current branch: `feat/elo-contribution-weighting`, worktree clean when the plan was created.
- Current rating path is `SemionGameManager -> RatingService -> RatingCalculator`.
- `RatingSystemId` already includes `ELO` and `TRUESKILL2`.
- `PlayerRatingProfile` already stores `mu`, `sigma`, `displayElo`, `ratingSystemId`, `ratingVersion`, and `lastUpdatedMatchId`.
- `MatchResult` already includes `teamResults`, and `TeamMatchResult` already includes `placement`, `placementWeight`, `resultGroup`, `eliminatedRound`, `eliminatedTick`, and `bossDamageTaken`.
- `SemionGame.buildMatchResult` already computes placement data from elimination order.
- Current `RatingEligibilityPolicy` rejects anything except exactly two participant teams.
- Current `EloRatingCalculator` is binary: it uses `participant.winner()` and winner/loser average ratings.
- Persistence already stores full `RatingMatchResult` payload, so new per-match adjustment fields are mostly payload-compatible if record schemas remain backward-compatible.

## Recommendation

Implement in three PRs:

1. **PR A: multi-team placement ELO** — required now. Supports 3/4/5 teams using existing ELO data and no external dependency.
2. **PR B: TrueSkill shadow mode** — calculates and persists/exports comparison data but does not affect public ladder.
3. **PR C: TrueSkill visible switch** — only after simulations and migration policy pass.

Reasoning:

- 3-5 team support is required now; a pairwise/placement ELO extension is simpler and testable with existing abstractions.
- TrueSkill/TrueSkill2 can be implemented, but doing it as the production ladder immediately creates parameter, migration, and balance risk.
- Original TrueSkill is easier to implement/validate than TrueSkill2. TrueSkill2 is less commonly specified in a small, copyable formula and would require more careful derivation or dependency vetting.

---

# PR A — Multi-Team Placement ELO

## Policy

For `N` teams where `2 <= N <= 5`:

- Rating-eligible teams are sorted by `TeamMatchResult.placement` ascending.
- Lower placement is better.
- Tied placement or `DRAW_OR_UNRATED` remains unrated for this PR unless an explicit tie policy is added later.
- Each participant receives the sum of pairwise ELO deltas against every participant on other teams.
- A better-placed participant has actual score `1.0` against a worse-placed participant.
- A worse-placed participant has actual score `0.0` against a better-placed participant.
- Per-opponent K is normalized so total volatility stays close to current binary ELO: `pairK = eloKFactor / opposingParticipantCount` or equivalent team-normalized variant.
- Contribution multiplier remains bounded `0.85..1.15`; better contribution increases gains and reduces losses as today.
- `wins/losses` counters need a policy update:
  - Recommended for now: `wins += placement == 1 ? 1 : 0`, `losses += placement > 1 ? 1 : 0` to preserve command compatibility.
  - Add follow-up fields later if we want `podiums`, `averagePlacement`, etc.

## Task A1: Add placement-aware rating participant metadata

**Objective:** Stop relying on boolean winner for all rating math.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/rating/RatingParticipant.java`
- Modify: `src/main/java/kim/biryeong/semiontd/rating/RatingService.java`
- Test: `src/test/java/kim/biryeong/semiontd/rating/RatingServiceTest.java`

**Steps:**
1. Add `int placement` and `double placementWeight` to `RatingParticipant` while keeping `winner()` for existing UI/counter compatibility.
2. In `RatingService.participant`, look up the participant's team in `matchResult.teamResults()` and pass placement/weight.
3. If no team result exists, fall back to `winner ? 1 : 2` to preserve old fixtures.
4. Add a JVM test that a 4-team `MatchResult` produces participants with placements 1..4.
5. Run: `./gradlew test --tests '*RatingServiceTest' --no-daemon`.

## Task A2: Generalize eligibility from binary to placement matches

**Objective:** Accept 3/4/5 team placement matches while still rejecting ambiguous results.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/rating/RatingEligibilityPolicy.java`
- Test: `src/test/java/kim/biryeong/semiontd/rating/RatingEligibilityPolicyTest.java`

**Steps:**
1. Replace `ratedTeamCount == 2` rule with `2 <= ratedTeamCount <= 5`.
2. Require every rated team to have exactly one `TeamMatchResult`.
3. Require placements to be unique and contiguous `1..teamCount`.
4. Reject `DRAW_OR_UNRATED` teams for now.
5. For two-team compatibility, keep winner flag validation against `winningTeams`.
6. For 3-5 teams, validate `winner == (team placement == 1)` so first place remains the only winner for existing counters.
7. Add tests:
   - 3-team placement is eligible.
   - 5-team placement is eligible.
   - 6-team placement is rejected.
   - duplicate placement is rejected.
   - missing team result is rejected.
   - draw/unrated placement remains rejected.
8. Run: `./gradlew test --tests '*RatingEligibilityPolicyTest' --no-daemon`.

## Task A3: Replace binary ELO calculator with placement ELO behavior

**Objective:** Calculate rating deltas for 2-5 team placement matches.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/rating/EloRatingCalculator.java`
- Test: `src/test/java/kim/biryeong/semiontd/rating/EloRatingCalculatorTest.java`

**Steps:**
1. Keep class name for minimal diff, but change internals from winner/loser average to pairwise placement comparisons.
2. Group participants by team.
3. For each participant, compare against all participants on all other teams.
4. For each opponent:
   - `actual = 1.0` if own placement < opponent placement, else `0.0`.
   - `expected = current ELO expectedScore(ownMu, opponentMu)`.
   - normalize K by total number of opponents or by opposing team count so equal 1v1 still yields `+16/-16`.
5. Sum pairwise deltas to `baseDelta`.
6. Apply contribution multiplier:
   - if `baseDelta >= 0`, multiply by `contribution.appliedMultiplier()`.
   - if `baseDelta < 0`, multiply by `2.0 - contribution.appliedMultiplier()`.
7. Update profile with `RatingSystemId.ELO`, `ratingVersion + 1`, `gamesPlayed + 1`, `wins/losses` based on first place.
8. Add tests:
   - 2-team equal match still yields `+16/-16`.
   - 3-team equal match yields first positive, middle near zero, last negative.
   - 5-team equal match is monotonic by placement.
   - underdog first place beats favorites and receives larger gain.
   - contribution still reduces loss for strong losing participant.
9. Run: `./gradlew test --tests '*EloRatingCalculatorTest' --no-daemon`.

## Task A4: Update docs and command wording

**Objective:** Make policy visible and avoid claiming binary-only ELO.

**Files:**
- Modify: `docs/rating-policy.md`
- Maybe modify: `src/main/java/kim/biryeong/semiontd/command/SemionCommands.java`

**Steps:**
1. Change docs from `exactly two teams` to `2-5 teams with unique placements`.
2. Document that `wins` means first-place finishes and `losses` means non-first finishes in multi-team matches.
3. Consider command wording from `W/L` to Korean text that does not imply pure 1v1 if necessary.
4. Run: `./gradlew test --tests '*SemionPlaceholders*' --tests '*Rating*' --no-daemon` if matching tests exist; otherwise run full `test`.

## Task A5: Add GameTest coverage for multi-team result rating path

**Objective:** Prove Fabric/runtime path can persist rating for a multi-team match result.

**Files:**
- Modify: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionRatingGameTest.java`

**Steps:**
1. Add focused GameTest that builds or finalizes a 3-team/4-team `MatchResult` with placements.
2. Route it through `SemionGameManager`/`RatingService` as close to actual runtime as existing rating GameTests allow.
3. Assert first-place profile increases, last-place profile decreases, and middle placement profile is between them.
4. Assert rating event and applied marker exist if accessible in the current test seam.
5. Run: `./gradlew compileGameTestJava gametest --no-daemon`.

## Task A6: Final validation and PR

**Steps:**
1. Run: `./gradlew test compileGameTestJava gametest --no-daemon --rerun-tasks`.
2. Run CodeGraph sync: `/Users/qf/.local/bin/codegraph sync /Users/qf/IdeaProjects/steve-td`.
3. Run: `git diff --check`.
4. Run static scans for secrets, exec/reflection, SQL string concat.
5. Commit: `feat: support multi-team placement elo`.
6. Open PR against the current target branch.

---

# PR B — TrueSkill Shadow Mode

## Policy

- Keep visible/public ladder on placement ELO.
- Add a TrueSkill-style calculator that produces `RatingMatchResult` with `RatingSystemId.TRUESKILL2` or `TRUESKILL_SHADOW` if a new enum value is preferred.
- Do not change commands/placeholders to use TrueSkill yet.
- Run simulations and store/report comparison deltas.

## Key design choice

Prefer implementing **original TrueSkill** first unless we find a vetted Java 21-compatible dependency for TrueSkill2. The name can stay `TRUESKILL2` only if the implemented formula is actually TrueSkill2. If not, add `TRUESKILL` or `TRUESKILL_SHADOW` to avoid misleading data.

## Task B1: Add rating system selection config

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/rating/RatingConfig.java`
- Modify config loader files under `src/main/java/kim/biryeong/semiontd/config/`
- Test: config loader tests if present

**Policy:**

```text
visibleSystem = ELO
shadowSystems = [TRUESKILL]
```

Do not switch visible default.

## Task B2: Add TrueSkill parameter config

**Parameters:**

```text
initialMu = 25.0 or mapped from 1500 scale
initialSigma = initialMu / 3
beta = initialSigma / 2
tau = initialSigma / 100
drawProbability = 0.0 initially
conservativeDisplay = mu - 3*sigma
```

If we keep 1500 display scale, define a reversible mapping from `(mu, sigma)` to display ELO.

## Task B3: Implement calculator behind tests

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/rating/TrueSkillRatingCalculator.java`
- Test: `src/test/java/kim/biryeong/semiontd/rating/TrueSkillRatingCalculatorTest.java`

**Tests:**
- Equal 1v1: winner mu up, sigma down; loser mu down, sigma down.
- Underdog upset: underdog gains more than favorite would.
- 3-team placement: first up, middle small/near neutral, last down.
- 5-team placement: monotonic by placement.
- New high-sigma player moves more than veteran low-sigma player.

## Task B4: Add simulation test matrix

**Files:**
- Create: `src/test/java/kim/biryeong/semiontd/rating/RatingSimulationTest.java`

**Scenarios:**
- Equal teams repeated 100 matches.
- Favorite wins repeatedly.
- Underdog upset.
- 2v2, 3v3, 4v4, 5-team FFA-like placements.
- Contribution extremes.
- New player vs veteran.

**Acceptance:**
- No runaway inflation/deflation beyond expected bounds.
- Monotonic placement results.
- New player volatility bounded but higher than veteran.

## Task B5: Shadow integration

**Files:**
- Modify: `RatingService` or create `CompositeRatingService`.
- Persistence: decide whether shadow profiles share `rating_profiles` table. Current table is keyed by player only, so true multi-system profiles likely need schema support.

**Important blocker:** Current `rating_profiles` appears player-keyed, not `(rating_system_id, player_id)` keyed. Before storing shadow TrueSkill profiles, change repository schema/model to support per-system profiles or store shadow events only. Do not overwrite ELO profiles.

---

# PR C — Visible TrueSkill switch

Only start this after PR B simulation output is acceptable.

## Required decisions

- Is the visible ladder display `mu`, `mu - 3*sigma`, or scaled ELO-like score?
- Do existing ELO users start with reset sigma or migrated sigma?
- Does historical ELO event replay migrate, or do we start TrueSkill from a cutover date?
- How are inactive players handled? Increase sigma over time or not?
- Are contribution stats integrated into TrueSkill itself, or only as a bounded post-update adjustment?

## Recommended migration

- Keep ELO profiles unchanged.
- Start TrueSkill profile from current ELO mapped to initial TrueSkill mu, with conservative sigma reset.
- Set `ratingVersion = 2` and `ratingSystemId = TRUESKILL`/`TRUESKILL2`.
- Keep a rollback config switch to ELO for at least one release.

---

# Acceptance criteria for 3-5 team launch

- 2-team behavior remains backward-compatible within existing tests.
- 3/4/5-team matches produce rating events and profile updates.
- 6+ team or tied/draw/unrated placements remain explicitly skipped with clear log reason.
- SQLite and file persistence tests pass.
- Runtime GameTest covers at least one 3-team placement result.
- Docs explain that current published system is placement ELO, not TrueSkill.
- TrueSkill work is tracked separately unless shadow mode is included in the same PR by explicit decision.

# Verification commands

```bash
./gradlew test --tests '*Rating*' --no-daemon
./gradlew test compileGameTestJava gametest --no-daemon --rerun-tasks
/Users/qf/.local/bin/codegraph sync /Users/qf/IdeaProjects/steve-td
git diff --check
```
