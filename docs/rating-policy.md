# SemionTD Rating Policy

This document describes the current operational policy for the SemionTD ELO rating subsystem.

## Scope

The rating subsystem currently implements a local-server ELO ladder. It is designed to be deterministic, idempotent per match, and compatible with the existing persistence backend configuration.

## Defaults

- Rating system: `ELO`
- Rating version: `1`
- Initial display ELO: `1500`
- Initial mu: `1500.0`
- Initial sigma: `350.0` (stored for future migration compatibility; this phase does not perform TrueSkill/TrueSkill2 uncertainty updates)
- ELO K-factor: `32.0`
- Leaderboard limit: `10`
- Minimum rating-eligible participants: `2`
- Spectators excluded: `true`

## Eligibility

A match is rating-eligible only when all of the following are true:

1. Rating is enabled.
2. The match has at least two rating-eligible participants after spectator filtering.
3. The match has 2-5 rating-eligible participant teams.
4. The match result contains exactly one winning team.
5. No eligible participant belongs to a `DRAW_OR_UNRATED` team.
6. Every eligible participant team has a `TeamMatchResult`.
7. Eligible team placements are unique and contiguous from `1..teamCount`.
8. The first-place team is the only winning team.
9. Every eligible participant's winner flag matches their team placement (`placement == 1`).
10. At least one eligible participant is a winner.
11. At least one eligible participant is a loser.

Skipped matches are not persisted as rating events and are not marked as applied. This keeps the current data model simple while avoiding false audit records. If skipped-match auditing becomes necessary, add explicit `applied/skippedReason` metadata to `RatingMatchResult` in a follow-up change.

## ELO calculation

For each eligible participant in a 2-5 team placement match:

- Placement maps to a target score, then the target score is compared against the participant's average ELO expected score against other teams.
- First place target score is `1.0`; last place target score is `0.0`.
- In 4-5 team matches, second place uses a high `0.90` target score so top-half finishes normally gain rating and only lose rating when heavily favored.
- In 4-5 team matches, the first bottom-half placement uses a low `0.10` target score so bottom-half finishes normally lose rating.
- Odd team counts have a neutral middle placement with target score `0.50`.
- A team-size multiplier `min(1.0, opposingParticipantCount / ownTeamSize)` keeps uneven team-size matches close to the current aggregate volatility.
- `displayElo` is `round(mu)` after applying the final delta.

For equal 1500-vs-1500 ratings with K=32 and neutral contribution stats, the expected display deltas are:

```text
2 teams: +16, -16
3 teams: +16, 0, -16
4 teams: +16, +13, -13, -16
5 teams: +16, +13, 0, -13, -16
```

This makes the top half gain and the bottom half lose for equal-rating matches, while still preserving ELO expectation: a heavily favored second-place finish can lose rating, but that should be uncommon.

For command/backward-compatibility counters, `wins` means first-place finishes and `losses` means non-first finishes.

## Contribution weighting

The rating system keeps placement outcome as the primary signal and applies player contribution as a bounded multiplier on the base ELO delta. Contribution weighting does not replace placement outcome: positive base deltas stay positive, negative base deltas stay negative, and neutral base deltas stay neutral.

Default contribution config:

- Enabled: `true`
- Multiplier clamp: `0.85..1.15`
- Defense weight: `0.40`
- Pressure weight: `0.25`
- Economy weight: `0.20`
- Assist weight: `0.15`

Current implementation prefers explicit lane/round attribution when available, then falls back to broad counters for historical or partial records.

Tracked attribution fields:

- Lane defense: `ownLaneIncomingThreat`, `incomingIncomeThreat`, `ownLaneLeakedThreat`.
- Income pressure: `sentIncomeThreat`, `incomeAttackSuccessThreat`.
- Economy source: `ownLaneDiamondGain`, `assistClearDiamondGain`, `incomeGenerated`.
- Assist cleanup: `assistClearThreat`.

Collection points:

- A spawned wave/income monster adds incoming threat to the target lane owner.
- A summoned monster adds sent income threat and income generated to the income unit sender.
- A monster reaching boss/final-defense progress adds leaked threat to the target lane owner.
- If that leaked monster was summoned, the income unit sender receives income attack success threat.
- A monster kill records own-lane diamond when the killer owns the target lane; otherwise it records assist-clear diamond/threat.

Scoring:

- Defense compares lane hold rate, with a small income-pressure difficulty bonus.
- Pressure compares sent income threat plus successful leaked threat bonus.
- Economy compares attributed diamond sources and generated income.
- Assist compares cleanup threat cleared outside the player's own lane.

Fallback behavior:

- Defense falls back to `killMinerals` and `monsterKills` relative to the participant's team average.
- Pressure falls back to `summonedMonsters` relative to the match average.
- Economy falls back to `finalIncome` relative to the participant's team average.
- Assist stays neutral when no assist attribution exists.

Positive base delta uses the applied multiplier directly. Negative base delta uses the inverse around `1.0`, so stronger contribution reduces the rating loss while weaker contribution increases it.

```text
positiveFinalDelta = baseDelta * contributionMultiplier
negativeFinalDelta = baseDelta * (2.0 - contributionMultiplier)
neutralFinalDelta = 0
```

The first version intentionally avoids sign reversal. A player with strong stats can reduce a negative placement delta, but does not turn it into a gain. A player with weak stats can reduce a positive placement delta, but does not turn it into a loss.

## Persistence

Rating profiles and rating events follow `SemionPersistenceConfig`:

- `FILE`: `ratings.json`, `rating-events.json`, `rating-applied-matches.json`
- `SQLITE`: `rating_profiles`, `rating_events`, and shared applied-match rows using subsystem `rating`

When SQLite is selected but not required, initialization failure falls back to file storage. When `externalDbRequired=true`, SQLite initialization failure is fatal.

## Commands

- `/semiontd rating`: show the executing player's rating.
- `/semiontd rating top`: show the top 10 rated players.
- Aliases are provided for convenience: `/레이팅`, `/랭크`, `/순위`.

Aliases are intentionally not covered by the full GameTest matrix; canonical `/semiontd rating` commands are the tested contract.

## Non-goals for this phase

The following policies are intentionally deferred:

- Global cross-server ladder.
- Season resets.
- Inactivity decay.
- Provisional placement matches.
- Draw handling.
- TrueSkill2 migration.
- Admin re-rating or event replay commands.
