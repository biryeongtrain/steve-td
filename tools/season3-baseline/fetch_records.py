#!/usr/bin/env python3
"""Collect anonymized build histories for the Season 3 local baseline.

Only public GET endpoints are used. Cached raw API responses stay under build/;
records.json contains no player names or UUIDs. Historical catalogs identify the
action source, not a shared balance cohort.
"""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys


REPO = Path(__file__).resolve().parents[2]
FETCH_PATH = REPO / ".agents/skills/semiontd-live-balance-analysis/scripts/fetch_live_metrics.py"
spec = importlib.util.spec_from_file_location("live_metrics_fetch", FETCH_PATH)
live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live)


def canonical_builder(builder_id):
    return "semion-td:end_towers" if builder_id == "semion-td:ender_towers" else builder_id


def player_ref(player_id):
    return hashlib.sha256(f"semiontd-baseline-player-v1:{player_id}".encode()).hexdigest()


def registry_builders(repo=REPO):
    jobs = repo / "src/main/java/kim/biryeong/semiontd/job"
    registry = (jobs / "JobRegistry.java").read_text()
    official_block = registry.split("OFFICIAL_BUILDER_IDS = Set.of(", 1)[1].split(");", 1)[0]
    official = set(re.findall(r"(\w+)\.ID", official_block))
    classes = re.findall(r"registerIfAbsent\(new (\w+)\(\)\)", registry)
    builders = []
    for name in classes:
        source = (jobs / f"{name}.java").read_text()
        match = re.search(r'\bID\s*=\s*ResourceLocation\.fromNamespaceAndPath\(\s*SemionTd\.MOD_ID,\s*"([^"]+)"\s*\)', source)
        if not match:
            raise ValueError(f"Cannot resolve registered job ID: {name}")
        builders.append({"builderId": f"semion-td:{match[1]}",
                         "origin": "OFFICIAL" if name in official else "CREATIVE"})
    if len(builders) != 30 or len(official) != 14 or len({b["builderId"] for b in builders}) != 30:
        raise ValueError("Expected 30 registered builders, including 14 official builders")
    return builders


def normalize_action(action):
    coordinates = [action.get(f"position_{axis}") for axis in "xyz"]
    if any(value is None for value in coordinates) and not all(value is None for value in coordinates):
        raise ValueError("Partially missing action coordinates")
    return {
        "round": int(action["round"]), "type": action["action_type"],
        "subjectId": action["subject_id"],
        "position": None if coordinates[0] is None else dict(zip("xyz", coordinates)),
        "cost": int(action["cost"]), "incomeGain": int(action["income_gain"]),
        "scheduledRound": int(action["scheduled_round"]), "targetTeam": action["target_team"],
        "targetLaneId": int(action["target_lane_id"]), "positionMode": action["position_mode"],
    }


def normalize_record(match, participant):
    actions = sorted(participant.get("build_actions") or [], key=lambda row: row["sequence"])
    if not any(row["action_type"] in ("TOWER_PLACE", "TOWER_UPGRADE") for row in actions):
        return None
    sequences = [int(row["sequence"]) for row in actions]
    if len(sequences) != len(set(sequences)):
        raise ValueError(f"Duplicate action sequence in match {match['match_id']}")
    rounds = participant.get("round_outcomes")
    return {
        "matchId": str(match["match_id"]), "playerRef": player_ref(participant["player_id"]),
        "catalogVersion": match.get("catalog_version"),
        "sourceTraits": participant.get("trait_loadout"), "sourceMap": None,
        "sourceMapReason": "NOT_EXPOSED_BY_PUBLIC_MATCH_API",
        "sourceEnabled": participant.get("builder_enabled"),
        "sourceTeam": participant.get("team_id"), "sourceStartedAt": match["started_at"],
        "sourceEndedAt": match["ended_at"], "sourceFinalRound": int(match["final_round"]),
        "attemptedRounds": [int(row["round_number"]) for row in rounds] if rounds else None,
        "sourceActionSequences": sequences, "actions": [normalize_action(row) for row in actions],
        "exactActionTicksAvailable": False,
    }


def select_records(builders, metrics, get_match):
    """Newest match first; per-builder distinct players; no outcome filtering."""
    rows = {row["builderId"]: {**row, "sourceEnabled": None, "records": [], "status": "NO_SAMPLE"}
            for row in builders}
    seen = {builder_id: set() for builder_id in rows}
    matches = {}
    for metric in metrics:
        if metric.get("match_mode") != "NORMAL":
            continue
        builder_id = canonical_builder(metric.get("job_id"))
        if builder_id in rows:
            matches.setdefault(str(metric["match_id"]), []).append(metric)
    ordered = sorted(matches, key=lambda mid: (matches[mid][0]["ended_at"], int(mid)), reverse=True)
    examined = 0
    for match_id in ordered:
        if not any(len(rows[canonical_builder(metric["job_id"])]["records"]) < 3
                   and player_ref(metric["player_id"]) not in seen[canonical_builder(metric["job_id"])]
                   for metric in matches[match_id]):
            continue
        match = get_match(match_id)
        examined += 1
        if match.get("match_mode") != "NORMAL":
            raise ValueError(f"Match mode changed during collection: {match_id}")
        for participant in match["participants"]:
            builder_id = canonical_builder(participant.get("job_id"))
            if builder_id not in rows or len(rows[builder_id]["records"]) == 3:
                continue
            ref = player_ref(participant["player_id"])
            if ref in seen[builder_id]:
                continue
            record = normalize_record(match, participant)
            if record is not None:
                if not rows[builder_id]["records"]:
                    rows[builder_id]["sourceEnabled"] = record["sourceEnabled"]
                rows[builder_id]["records"].append(record)
                rows[builder_id]["status"] = "RECORDS_AVAILABLE"
                seen[builder_id].add(ref)
    return list(rows.values()), {"normalMatchesConsidered": len(matches), "matchDetailsExamined": examined}


def coordinate_summary(builders):
    modes = Counter()
    positions = {}
    for builder in builders:
        for record in builder["records"]:
            for action in record["actions"]:
                mode = action["positionMode"]
                modes[mode] += 1
                if action["position"] is not None:
                    positions.setdefault(mode, []).append(action["position"])
    return {mode: {"actionCount": count, "positionCount": len(positions.get(mode, [])),
                   "min": {axis: min(p[axis] for p in positions[mode]) for axis in "xyz"} if mode in positions else None,
                   "max": {axis: max(p[axis] for p in positions[mode]) for axis in "xyz"} if mode in positions else None}
            for mode, count in sorted(modes.items())}


def write_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def collect(output_dir, refresh=False, offline=False):
    request_manifest = []

    def fetch(path, params=None, fresh=False):
        params = params or {}
        cache_key = hashlib.sha256(json.dumps([path, params], sort_keys=True).encode()).hexdigest()
        cache_path = output_dir / "api-cache" / f"{cache_key}.json"
        if cache_path.exists() and (offline or not (refresh or fresh)):
            entry = json.loads(cache_path.read_text())
        else:
            if offline:
                raise ValueError(f"Missing offline cache: {path} {params}")
            entry = {"fetchedAt": datetime.now(timezone.utc).isoformat(), "payload": live.fetch(path, params)}
            write_json(cache_path, entry)
        manifest_params = {key: f"sha256:{hashlib.sha256(value.encode()).hexdigest()}" if key == "cursor" else value
                           for key, value in params.items()}
        request_manifest.append({"path": path, "params": manifest_params, "fetchedAt": entry["fetchedAt"]})
        return entry["payload"]

    # Inspect availability and patch boundaries before reading any participants.
    stats = fetch("/api/v1/stats", fresh=True)
    patches = fetch("/api/v1/patches", fresh=True)
    metrics = []
    cursor = None
    seen_cursors = set()
    while True:
        params = {"limit": "1000", **({"cursor": cursor} if cursor else {})}
        page = fetch("/api/v1/participant-metrics", params)
        metrics.extend(page["metrics"])
        cursor = page.get("next_cursor")
        if not cursor:
            break
        if cursor in seen_cursors or len(seen_cursors) >= 99:
            raise ValueError("Participant pagination repeated or exceeded 100 pages")
        seen_cursors.add(cursor)
    builders, coverage = select_records(registry_builders(), metrics,
                                        lambda mid: fetch(f"/api/v1/matches/{mid}"))
    catalogs = []
    versions = sorted({record["catalogVersion"] for builder in builders for record in builder["records"]
                       if record["catalogVersion"]})
    for version in versions:
        catalog = fetch("/api/v1/catalog", {"version": version})
        if catalog.get("versionHash") != version:
            raise ValueError(f"Catalog version mismatch: {version}")
        relative_path = f"catalogs/{version}.json"
        write_json(output_dir / relative_path, catalog)
        catalogs.append({"version": version, "path": relative_path})
    payload = {
        "schemaVersion": 1,
        "source": {"fetchedAt": max(request["fetchedAt"] for request in request_manifest), "baseUrl": live.BASE_URL,
                   "stats": stats, "patches": patches["patches"], "requests": request_manifest,
                   "catalogs": catalogs, "participantRowsScanned": len(metrics), **coverage,
                   "selectionPolicy": "LATEST_NORMAL_WITH_TOWER_PURCHASES_MAX_3_DISTINCT_PLAYERS_PER_BUILDER",
                   "coordinateSummary": coordinate_summary(builders),
                   "notes": ["Historical catalogs are action provenance, not merged balance cohorts.",
                             "Missing round outcomes are null; final round is not participant survival evidence.",
                             "Map and exact action ticks are not exposed by the public API.",
                             "Raw API caches are local build artifacts and must not be committed."]},
        "builders": builders,
    }
    write_json(output_dir / "records.json", payload)
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=REPO / "build/season3-baseline")
    parser.add_argument("--refresh", action="store_true", help="Re-fetch all cached public responses")
    parser.add_argument("--offline", action="store_true", help="Rebuild from the existing cache without requests")
    args = parser.parse_args()
    if args.refresh and args.offline:
        parser.error("--refresh and --offline are mutually exclusive")
    payload = collect(args.output_dir, args.refresh, args.offline)
    print(json.dumps({"output": str(args.output_dir / "records.json"),
                      "builders": len(payload["builders"]),
                      "records": sum(len(row["records"]) for row in payload["builders"]),
                      "coverage": {row["builderId"]: len(row["records"]) for row in payload["builders"]},
                      "coordinates": payload["source"]["coordinateSummary"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
