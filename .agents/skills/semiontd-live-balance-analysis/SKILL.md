---
name: semiontd-live-balance-analysis
description: "Analyze SemionTD live match, builder, tower, economy, and round-combat metrics from semiontd.biryeong.kim for evidence-based builder or tower balance reviews in steve-td. Use when evaluating live balance, comparing builders or patches, investigating round performance, or proposing a balance change from production data."
---

# SemionTD Live Balance Analysis

Use live observations to support the smallest safe balance change. Live data describes what players experienced; active server config defines deployed values; source explains mechanics and defaults.

## Workflow

1. Identify the builder, tower IDs, relevant mechanics, and active balance values in the repository and server config.
2. Read [references/live-api.md](references/live-api.md), then check `/api/v1/stats` and `/api/v1/patches` before interpreting any aggregate.
3. Select one catalog version and `NORMAL` matches. Fetch all required pages with `scripts/fetch_live_metrics.py`; filter by `catalog_version` locally because the API has no catalog-version filter.
4. Compare the target with the same-patch population. Segment by round and tower when samples allow, and report match, participant, round, and tower sample counts.
5. Explain confounders such as player skill, team composition, survival bias, final-round truncation, and low samples. Treat missing telemetry as unavailable, never as zero performance.
6. Recommend one minimal numeric or mechanical change, its expected effect, validation metric, and rollback condition. Do not edit config, deploy, or publish unless explicitly requested.

If implementing a recommendation, also use `$semiontd-builder-tower-dev` and follow its active-config and upgrade-cost rules.

## Non-negotiable calculations

- Aggregate DPS: `sum(total_damage) * 20 / sum(combat_ticks)`. Never average row DPS values.
- Average survival seconds: `sum(survival_ticks) / sum(samples) / 20`.
- Convert tick durations to seconds only after aggregation.
- Parse JSON BIGINT strings as integers; do not pass them through floating-point arithmetic.
- Normalize the historical builder alias `semion-td:ender_towers` to `semion-td:end_towers`.
- Treat `semion-td:demon_lord` as a synthetic combat tower, excluded from builder tower counts.
- Treat final tower composition as surviving towers at match end, not construction frequency.
- Use `catalog.upgrades[].mineralCost` for an upgrade edge. A tower's `mineralCost` is placement/direct-install cost.

## Report

State the catalog version and match window, sample counts, observed difference from the same-patch baseline, likely cause, smallest proposed change, expected impact, and validation/rollback rule. Separate measured facts from inference.
