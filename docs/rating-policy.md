# SemionTD Rating Policy

This document describes the current operational policy for the SemionTD ELO rating subsystem.

## Scope

The rating subsystem currently implements a local-server ELO ladder. It is designed to be deterministic, idempotent per match, and compatible with the existing persistence backend configuration.

## Defaults

- Rating system: `ELO`
- Rating version: `1`
- Initial display ELO: `1500`
- Initial mu: `1500.0`
- Initial sigma: `350.0`
- ELO K-factor: `32.0`
- Leaderboard limit: `10`
- Minimum rating-eligible participants: `2`
- Spectators excluded: `true`

## Eligibility

A match is rating-eligible only when all of the following are true:

1. Rating is enabled.
2. The match has at least two rating-eligible participants after spectator filtering.
3. The match result contains exactly one winning team.
4. No eligible participant belongs to a `DRAW_OR_UNRATED` team.
5. Every eligible participant's winner flag matches the match `winningTeams` set.
6. At least one eligible participant is a winner.
7. At least one eligible participant is a loser.

Skipped matches are not persisted as rating events and are not marked as applied. This keeps the current data model simple while avoiding false audit records. If skipped-match auditing becomes necessary, add explicit `applied/skippedReason` metadata to `RatingMatchResult` in a follow-up change.

## ELO calculation

For each eligible participant:

- Winners receive actual score `1.0`.
- Losers receive actual score `0.0`.
- Opponent rating is the average `mu` of the opposite result group.
- Delta is `K * (actual - expected)`.
- `displayElo` is `round(mu)` after applying the delta.

For equal 1500-vs-1500 ratings with K=32, this yields `+16` for winners and `-16` for losers.

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
