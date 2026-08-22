# Live metrics API

Base URL: `https://semiontd.biryeong.kim`

The API is public and cached for about 30 seconds. Use these routes:

| Route | Use |
| --- | --- |
| `/api/v1/stats` | Verify data availability, totals, and latest sync time |
| `/api/v1/patches` | Choose a catalog version and inspect its sample size |
| `/api/v1/catalog?version={sha256}` | Resolve builders, towers, upgrade edges, traits, and costs for that version |
| `/api/v1/builders` | Quick all-history orientation only; it mixes catalog versions |
| `/api/v1/builders/{id}?version={sha256}` | Builder summary for one catalog version |
| `/api/v1/towers/{id}?version={sha256}` | Tower aggregate for one catalog version |
| `/api/v1/participant-metrics` | Raw match-level combat, economy, income, support, and placement rows |
| `/api/v1/round-metrics` | Raw round and tower combat/economy rows |
| `/api/v1/matches/{id}` | Match context and participant round outcomes |

`participant-metrics` supports `matchId`, `playerId`, `builderId`, `fromMatchId`, `toMatchId`, `limit`, and `cursor`. `round-metrics` additionally supports `towerId`, `fromRound`, and `toRound`. The default page is 200 rows and the maximum is 1,000. Paginated responses are `{ "metrics": [...], "next_cursor": "..." }`.

Examples from the skill directory:

```bash
python3 scripts/fetch_live_metrics.py /api/v1/stats
python3 scripts/fetch_live_metrics.py /api/v1/participant-metrics builderId=semion-td:warlock_towers limit=1000 --all-pages > /tmp/warlock-participants.json
python3 scripts/fetch_live_metrics.py /api/v1/round-metrics builderId=semion-td:warlock_towers fromRound=10 limit=1000 --all-pages > /tmp/warlock-rounds.json
```

## Interpretation rules

- Use `/stats` sample counters to decide whether participant-round and tower-round telemetry exists. An empty list means no captured samples, not zero damage, healing, or DPS.
- Do not use `/builders` alone to justify balance changes. Fetch raw rows, keep `match_mode == "NORMAL"`, and compare rows with the same `catalog_version`.
- Patch rows with few or no matches cannot support a strong conclusion. Report the sample rather than inventing a universal minimum threshold.
- Win rate and placement are affected by player skill, team composition, and patch selection. Pair them with economy, round progression, tower composition, and combat measurements.
- Aggregate raw totals before computing rates. Preserve integer precision and round only for presentation.
- A winning final round can end without the next income payment. Do not interpret that as an economy defect.
- Older matches may have participant metrics but no round-combat rows because those snapshots cannot be reconstructed.
- `raw_payload`, rating `mu`/`sigma`, and rating-event source data are intentionally not public.

## Evidence authority

1. Live API: observed production behavior.
2. Active server `config/semion-td`: currently deployed numbers.
3. Repository source/default config: mechanic definitions and future defaults.

When these differ, call out the mismatch instead of combining them.
