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
3. The match has exactly two rating-eligible participant teams.
4. The match result contains exactly one winning team.
5. No eligible participant belongs to a `DRAW_OR_UNRATED` team.
6. Every eligible participant's winner flag matches the match `winningTeams` set.
7. At least one eligible participant is a winner.
8. At least one eligible participant is a loser.

Skipped matches are not persisted as rating events and are not marked as applied. This keeps the current data model simple while avoiding false audit records. If skipped-match auditing becomes necessary, add explicit `applied/skippedReason` metadata to `RatingMatchResult` in a follow-up change.

## ELO calculation

For each eligible participant:

- Winners receive actual score `1.0`.
- Losers receive actual score `0.0`.
- Opponent rating is the average `mu` of the opposite result group.
- Base delta is `K * (actual - expected) * min(1.0, opposingTeamSize / ownTeamSize)`, so larger teams do not multiply aggregate ladder inflation by participant count.
- `displayElo` is `round(mu)` after applying the final delta.

For equal 1500-vs-1500 ratings with K=32 and neutral contribution stats, this yields `+16` for winners and `-16` for losers.

## Contribution weighting

The rating system keeps win/loss as the primary signal and applies player contribution as a bounded multiplier on the base ELO delta. Contribution weighting does not replace match outcome: winners still gain rating and losers still lose rating in this phase.

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

Winner delta uses the applied multiplier directly. Loser delta uses the inverse around `1.0`, so stronger contribution reduces the rating loss while weaker contribution increases it.

```text
winnerFinalDelta = baseDelta * contributionMultiplier
loserFinalDelta = baseDelta * (2.0 - contributionMultiplier)
```

The first version intentionally avoids sign reversal. A losing player with strong stats loses less rating, but does not gain rating from a lost match. A winning player with weak stats gains less rating, but does not lose rating from a won match.

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
- Partial placement scoring for three or more teams.
- TrueSkill2 migration.
- Admin re-rating or event replay commands.
