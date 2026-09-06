#!/usr/bin/env python3
"""Summarize opt-in Season 3 replay logs; historical patches are provenance only."""

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import statistics


CHECKPOINTS = (5, 15, 16, 25)
STAGES = ("BASE", "HEALER_ONLY", "COUNT_AND_HEALER")
EXCLUSIONS = {
    "semion-td:demon_lord_towers": "UNRECORDED_STAT_ALLOCATION_AND_DIRECT_MOVEMENT",
    "semion-td:developer": "UNRECORDED_PATCH_AND_OPTIMIZATION_CHOICES",
    "semion-td:hero_party": "UNRECORDED_WEAPON_AND_ARMOR_CHOICES",
}
MARKERS = {"COMBAT": "SEASON3_RECORDED_COMBAT_REPORT ",
           "OPERATION": "SEASON3_RECORDED_OPERATION_REPORT "}
VALID_OPERATION = {"OBSERVED_MATCH_ENDPOINT", "OBSERVED_HISTORY_ENDPOINT", "CENSORED"}
CONFIG_FIELDS = ("towerBalanceSha256", "waveConfigSha256", "economyConfigSha256",
                 "fixtureMapSha256", "fixtureLayoutSha256", "economySha256", "waveSha256", "augmentSha256",
                 "replayMapTemplateId", "replayMapConfigSha256", "replayMapTemplateSha256",
                 "sourceTemplateId", "fixtureTemplateId", "fixtureTemplateSha256")
CAVEATS = [
    "Historical catalog versions identify action sources, not pooled historical performance.",
    "Configuration hashes, track, checkpoint, phase, stage, and augment group remain separate cohorts.",
    "Repeated trials and mirrored lanes are correlated observations, not independent players.",
    "Official medians give each observed official builder one vote; they are not a universal target.",
    "Partial replay measurements describe the executed board, not faithful source performance.",
    "Missing checkpoints or fields are unavailable, never zero damage or guaranteed survival.",
    "Window clear rates include censored observations as not yet cleared; they are not final defeat rates.",
    "Combat budgets omit previous growth and economy; NONE is not a required-clear difficulty target.",
    "Growth runtime details are retained verbatim; unrelated builder-specific stacks are not added together.",
    "Pre-terminal tower snapshots are labeled separately from current endpoint economy and tower states.",
    "Endpoint defense/support roles can change on normal retreat; zero survivingDefenseTowers is not a death count (including Future Agency).",
    "Arena readiness waits are fixture provenance only and are excluded from all combat-time denominators.",
    "Operation NONE augment configs normalize only with four distinct NONE_CONTROL subjects and explicitly empty selections; active groups retain their hashes.",
    "Local entity observations do not prove live balance, client rendering, or network load.",
]


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def source_key(value):
    return value["builderId"], str(value["matchId"]), value["playerRef"]


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key in wave evidence: {key}")
        result[key] = value
    return result


def load_wave_hash_evidence(path):
    if path is None:
        return {}, None
    path = Path(path).resolve()
    raw_evidence = path.read_bytes()
    evidence = json.loads(raw_evidence, object_pairs_hook=unique_object)
    if evidence.get("schemaVersion") != 1 or not evidence.get("groups"):
        raise ValueError("Expected version 1 wave hash evidence with explicit groups")
    aliases, verified_groups = {}, []
    for group in evidence["groups"]:
        stages, preferred = group["acceptedReportStages"], group["preferredSha256"]
        if not stages or any(not isinstance(stage, str) or not stage for stage in stages):
            raise ValueError("Wave evidence must name its accepted report stages")
        values, hashes = [], []
        for variant in group["variants"]:
            serialized = variant["serializedJson"]
            actual = hashlib.sha256(serialized.encode("utf-8")).hexdigest()
            if actual != variant["sha256"]:
                raise ValueError(f"Wave evidence SHA mismatch: {variant['sha256']}")
            value = json.loads(serialized, object_pairs_hook=unique_object)
            if not isinstance(value, dict):
                raise ValueError("Wave evidence must serialize a JSON object")
            # Compare parsed values only; never pretend Python serialization is Gson's hash input.
            values.append(json.dumps(value, sort_keys=True, allow_nan=False))
            hashes.append(actual)
        if len(set(hashes)) < 2 or preferred not in hashes:
            raise ValueError("Wave evidence needs distinct verified variants and a verified preferred SHA")
        if len(set(values)) != 1:
            raise ValueError(f"Wave evidence values differ within group {group['stage']}")
        for stage in stages:
            for original in hashes:
                key = (stage, original)
                if key in aliases and aliases[key] != preferred:
                    raise ValueError("Conflicting wave evidence groups")
                aliases[key] = preferred
        verified_groups.append({"stage": group["stage"], "acceptedReportStages": stages,
                                "preferredSha256": preferred, "verifiedVariantSha256s": hashes})
    return aliases, {"path": str(path), "fileSha256": hashlib.sha256(raw_evidence).hexdigest(),
                     "verification": "EACH_SERIALIZED_UTF8_SHA_AND_PARSED_OBJECT_EQUALITY",
                     "groups": verified_groups, "sourceInputs": evidence.get("inputs", []),
                     "sourceInputVerification": "PROVENANCE_ONLY_NOT_RECHECKED_BY_SUMMARIZER"}


def disabled_augment_control(report):
    subjects = report.get("subjects")
    return (report.get("group") == "NONE" and report.get("status") in VALID_OPERATION
            and isinstance(report.get("augmentSha256"), str) and bool(report["augmentSha256"])
            and isinstance(subjects, list) and len(subjects) == 4
            and {(subject.get("team"), subject.get("lane")) for subject in subjects}
                == {("RED", 1), ("RED", 2), ("BLUE", 1), ("BLUE", 2)}
            and all(subject.get("augmentStatus") == "NONE_CONTROL" and subject.get("augmentSelections") == []
                    for subject in subjects))


def configuration(report, aliases=None, track=None):
    result = {field: report[field] for field in CONFIG_FIELDS if report.get(field) is not None}
    for field in ("waveConfigSha256", "waveSha256"):
        if field in result and aliases:
            result[field] = aliases.get((report["stage"], result[field]), result[field])
    if track == "OPERATION" and disabled_augment_control(report):
        result.pop("augmentSha256")
        result["augmentState"] = "DISABLED_NO_SELECTIONS"
    return result


def normalization_reasons(original, effective):
    reasons = []
    if any(original.get(field) != effective.get(field) for field in ("waveConfigSha256", "waveSha256")):
        reasons.append("VERIFIED_WAVE_SERIALIZED_SHA_AND_OBJECT_EQUALITY")
    if effective.get("augmentState") == "DISABLED_NO_SELECTIONS":
        reasons.append("OPERATION_NONE_FOUR_DISTINCT_NONE_CONTROL_SUBJECTS_WITH_EMPTY_SELECTIONS")
    return reasons


def read_reports(paths):
    reports, seen = [], {}
    decoder = json.JSONDecoder()
    for path in paths:
        with Path(path).open() as stream:
            for line_number, line in enumerate(stream, 1):
                for track, marker in MARKERS.items():
                    if marker not in line:
                        continue
                    try:
                        report, _ = decoder.raw_decode(line.split(marker, 1)[1].lstrip())
                    except (ValueError, TypeError) as error:
                        raise ValueError(f"Invalid {track} JSON at {path}:{line_number}") from error
                    identity = (track, source_key(report), report.get("round"), report.get("repetition"),
                                report.get("stage"), report.get("augmentGroup", report.get("group")),
                                tuple((key, report.get(key)) for key in CONFIG_FIELDS))
                    fingerprint = digest(report)
                    if identity in seen:
                        if seen[identity] != fingerprint:
                            raise ValueError(f"Conflicting repeated trial at {path}:{line_number}; choose one run's logs")
                        continue
                    seen[identity] = fingerprint
                    reports.append((track, report))
    return reports


def numeric_fields(value, prefix=""):
    result = {}
    for key, item in (value or {}).items():
        if isinstance(item, (int, float)) and not isinstance(item, bool):
            result[prefix + key] = item
    return result


def statistics_for(values):
    return {"observations": len(values), "sum": sum(values), "mean": statistics.mean(values),
            "median": statistics.median(values), "min": min(values), "max": max(values)}


def tower_metrics(towers):
    result = {}
    fields = ("physicalDamageDealt", "magicDamageDealt", "damageTaken", "healingDone", "killCount",
              "sampleCount", "deathCount", "startCount", "endAliveCount", "survivalTicks", "enemyHpDamage",
              "augmentSpecialDamageDealt")
    for name in fields:
        values = [tower[name] for tower in towers if tower.get(name) is not None]
        if values:
            result["tower." + name] = sum(values)
    if all(tower.get("firstCombatTick") is not None and tower.get("lastCombatTick") is not None for tower in towers) and towers:
        result["tower.combatTicks"] = sum(max(0, tower["lastCombatTick"] - tower["firstCombatTick"] + 1)
                                         if tower["firstCombatTick"] >= 0 else 0 for tower in towers)
    if "tower.physicalDamageDealt" in result and "tower.magicDamageDealt" in result:
        result["tower.totalDamage"] = result["tower.physicalDamageDealt"] + result["tower.magicDamageDealt"]
    return result


def action_summary(actions, round_limit=None, tick_limit=None, phase=None):
    selected = [action for action in actions if round_limit is None
                or action.get("round", action.get("action", {}).get("round", 0)) <= round_limit]
    if phase == "PREPARE_START" and round_limit is not None:
        selected = [action for action in selected if action["round"] < round_limit]
    if tick_limit is not None:
        selected = [action for action in selected if action.get("lastAttemptTick", -1) <= tick_limit]
    failures = Counter(action["status"] for action in selected
                       if action["status"] not in ("SUCCESS", "ECONOMY_ACTION_EXCLUDED_FROM_COMBAT"))
    differences = Counter(action["divergenceReason"] for action in selected if action.get("divergenceReason"))
    success = [action for action in selected if action["status"] == "SUCCESS"]
    result = {"attempted": len(selected), "successful": len(success), "failures": dict(failures),
            "differences": dict(differences),
            "diamondSpent": sum(max(0, -action.get("diamondsDelta", 0)) for action in success),
            "diamondReceived": sum(max(0, action.get("diamondsDelta", 0)) for action in success),
            "emeraldSpent": sum(max(0, -action.get("emeraldsDelta", 0)) for action in success),
            "incomeDelta": sum(action.get("incomeDelta", 0) for action in success)}
    if success and all(action.get("emeraldProductionDelta") is not None for action in success):
        result["emeraldProductionDelta"] = sum(action["emeraldProductionDelta"] for action in success)
    return result


def first_divergence(subject):
    if subject.get("firstDivergence") is not None:
        return subject["firstDivergence"]
    return next((action for action in subject.get("actions", [])
                 if action.get("divergenceReason") is not None
                 or action["status"] not in ("SUCCESS", "ECONOMY_ACTION_EXCLUDED_FROM_COMBAT")
                 or subject.get("firstDeviationSourceIndex") is not None
                 and action.get("sourceIndex") == subject["firstDeviationSourceIndex"]), None)


def observations(track, report):
    if track == "COMBAT":
        if not report.get("measured") or report.get("status") not in ("MEASURED", "PARTIAL_REPLAY"):
            return
        for lane in report.get("lanes", []):
            combat = lane.get("combat")
            if combat is None:
                continue
            metrics = numeric_fields(combat)
            if lane.get("automaticActivations") is not None:
                metrics["control.automaticActivations"] = lane["automaticActivations"]
            metrics.update(tower_metrics(combat.get("combatMetrics", [])))
            metrics.update(numeric_fields(combat.get("healing"), "waveHealing."))
            metrics.update(numeric_fields(combat.get("naturalSupport"), "naturalSupport."))
            metrics["lane.waveTicks"] = combat.get("endpointTick")
            for key in ("laneCleared", "perfectClear", "censored"):
                if key in combat:
                    metrics[key] = int(combat[key])
            yield report["round"], "COMBAT_ENDPOINT", lane, metrics, []
        return
    if report.get("status") not in VALID_OPERATION:
        return
    for subject in report.get("subjects", []):
        battles = {battle["round"]: battle for battle in subject.get("battles", [])}
        for checkpoint in subject.get("checkpoints", []):
            if checkpoint["round"] not in CHECKPOINTS:
                continue
            metrics = numeric_fields(checkpoint.get("balance"), "economy.")
            towers = checkpoint.get("towers", [])
            board_prefix = "boardPreTerminal." if checkpoint.get("towerStateTiming") == "PRE_TERMINAL_TICK" else "board."
            if "towers" in checkpoint:
                metrics[board_prefix + "towerCount"] = len(towers)
                for name, field in (("paidDiamonds", "paidDiamonds"), ("health", "health")):
                    if all(tower.get(field) is not None for tower in towers):
                        metrics[board_prefix + name] = sum(tower[field] for tower in towers)
            if "payouts" in subject:
                payouts = [payout for payout in subject["payouts"] if payout["round"] < checkpoint["round"]
                           or payout["round"] == checkpoint["round"] and checkpoint["phase"] == "AFTER_ACTUAL_PAYOUT"]
                metrics["payout.count"] = len(payouts)
                metrics["payout.actualDiamondDelta"] = sum(payout["actualDiamondDelta"] for payout in payouts)
            growth = {"checkpointTick": checkpoint.get("tick"), "towerStateTiming": checkpoint.get("towerStateTiming"),
                      "towerStateTick": checkpoint.get("towerStateTick"), "towers": [
                          {"towerRef": tower.get("towerRef"), "typeId": tower["typeId"], "runtimeDetails": tower["runtimeDetails"]}
                          for tower in towers if tower.get("runtimeDetails")]}
            if checkpoint["phase"] == "COMBAT_ENDPOINT" and checkpoint["round"] in battles:
                battle = battles[checkpoint["round"]]
                accounting = battle.get("accounting") or {}
                if any(accounting.get(key, 0) for key in ("unexplained", "belowFloor", "outsideMap", "unforcedChunk")):
                    continue
                metrics.update(numeric_fields(battle))
                metrics.update(tower_metrics(battle.get("towers", [])))
                for key, prefix in (("healing", "waveHealing."), ("naturalSupport", "naturalSupport."),
                                    ("utilitySupport", "utilitySupport."), ("accounting", "accounting.")):
                    metrics.update(numeric_fields(battle.get(key), prefix))
                metrics["lane.waveTicks"] = battle.get("observedWaveTicks")
                for key in ("laneCleared", "defenseBroken"):
                    if key in battle:
                        metrics[key] = int(battle[key])
                metrics["censored"] = int(battle["status"] == "CENSORED")
            yield checkpoint["round"], checkpoint["phase"], subject, metrics, growth


def aggregate(rows):
    fields = defaultdict(list)
    failures, differences, statuses, growth = Counter(), Counter(), Counter(), []
    for report, subject, metrics, tower_growth in rows:
        for name, value in metrics.items():
            if value is not None:
                fields[name].append(value)
        statuses["PARTIAL_REPLAY" if first_divergence(subject) else subject.get("status", report["status"])] += 1
        action = action_summary(subject.get("actions", []), report.get("summaryRound"),
                                report.get("summaryTick"), report.get("summaryPhase"))
        failures.update(action.pop("failures"))
        differences.update(action.pop("differences"))
        for name, value in action.items():
            fields["actions." + name].append(value)
        if tower_growth:
            growth.append({"matchId": report["matchId"], "playerRef": report["playerRef"],
                           "team": subject.get("team"), "lane": subject.get("lane"), **tower_growth})
    stats = {name: statistics_for(values) for name, values in sorted(fields.items())}
    rates = {}
    for name, numerator, denominator, factor in (
            ("pooledTowerDps", "tower.totalDamage", "tower.combatTicks", 20),
            ("boardDps", "tower.totalDamage", "lane.waveTicks", 20),
            ("averageTowerSurvivalSeconds", "tower.survivalTicks", "tower.sampleCount", 0.05),
            ("observedWindowClearRate", "laneCleared", None, 1),
            ("observedWindowPerfectClearRate", "perfectClear", None, 1),
            ("censoredObservationRate", "censored", None, 1)):
        paired = [(metrics[numerator], metrics.get(denominator, 1)) for _, _, metrics, _ in rows
                  if metrics.get(numerator) is not None and (denominator is None or metrics.get(denominator) is not None)]
        divisor = sum(pair[1] for pair in paired)
        rates[name] = sum(pair[0] for pair in paired) * factor / divisor if divisor else None
    return {"observations": len(rows), "distinctPlayers": len({report["playerRef"] for report, *_ in rows}),
            "sourceRecords": len({source_key(report) for report, *_ in rows}),
            "sourceCatalogVersions": sorted({report["sourceCatalogVersion"] for report, *_ in rows}),
            "replayStatuses": dict(statuses), "actionFailures": dict(failures), "actionDifferences": dict(differences),
            "metrics": stats, "rates": rates, "growthSnapshots": growth}


def summarize(manifest, reports, wave_hash_evidence=None):
    aliases, evidence_provenance = load_wave_hash_evidence(wave_hash_evidence)
    builders = {builder["builderId"]: builder for builder in manifest["builders"]}
    sources = {source_key({**record, "builderId": builder_id}): record
               for builder_id, builder in builders.items() for record in builder["records"]}
    by_builder, buckets, equivalent_trials = defaultdict(list), defaultdict(list), {}
    for track, report in reports:
        key = source_key(report)
        if key not in sources:
            raise ValueError(f"Report source is absent from manifest: {key}")
        if report.get("sourceCatalogVersion") != sources[key]["catalogVersion"]:
            raise ValueError(f"Source catalog changed: {key}")
        by_builder[key[0]].append((track, report))
        if key[0] in EXCLUSIONS:
            continue
        config = configuration(report, aliases, track)
        config_hash = digest(config)
        identity = (track, key, report.get("round"), report.get("repetition"), report["stage"],
                    report.get("augmentGroup", report.get("group")), config_hash)
        original = configuration(report)
        if identity in equivalent_trials and equivalent_trials[identity] != original:
            raise ValueError("Equivalent configurations expose overlapping trial runs; choose one run per source/checkpoint/repetition")
        equivalent_trials[identity] = original
        for round_number, phase, subject, metrics, growth in observations(track, report):
            cohort = (key[0], track, round_number, phase, report["stage"],
                      report.get("augmentGroup", report.get("group")), config_hash)
            buckets[cohort].append(({**report, "summaryRound": round_number, "summaryPhase": phase,
                                    "summaryTick": growth.get("checkpointTick") if growth else None,
                                    "configuration": config}, subject, metrics, growth))
    aggregates = []
    for cohort, rows in sorted(buckets.items()):
        builder_id, track, round_number, phase, stage, group, config_hash = cohort
        aggregates.append({"builderId": builder_id, "origin": builders[builder_id]["origin"], "track": track,
                           "round": round_number, "phase": phase, "stage": stage, "group": group,
                           "configurationSha256": config_hash, "configuration": rows[0][0]["configuration"],
                           "originalConfigurations": list({digest(configuration(row[0])): configuration(row[0])
                                                           for row in rows}.values()),
                           "configurationNormalizationReasons": list({digest(configuration(row[0])): {
                               "originalConfigurationSha256": digest(configuration(row[0])),
                               "reasons": normalization_reasons(configuration(row[0]), row[0]["configuration"])}
                               for row in rows}.values()), **aggregate(rows)})
    coverage = []
    for builder_id, builder in builders.items():
        records = builder["records"]
        own = by_builder[builder_id]
        measured = [row for row in aggregates if row["builderId"] == builder_id]
        expected = {"COMBAT": len(records) * len(CHECKPOINTS) * 3, "OPERATION": len(records) * len(STAGES)}
        complete_keys = {"COMBAT": set(), "OPERATION": set()}
        scenario_configs = defaultdict(set)
        for track, report in own:
            scenario = (track, source_key(report), report.get("round"), report.get("repetition"),
                        report.get("stage"), report.get("augmentGroup", report.get("group")))
            scenario_configs[scenario].add(tuple(sorted(configuration(report, aliases, track).items())))
            if (track == "COMBAT" and report.get("measured") and report.get("status") == "MEASURED"
                    and report.get("augmentGroup") == "NONE" and report.get("round") in CHECKPOINTS
                    and report.get("repetition") in (1, 2, 3) and len(report.get("lanes", [])) == 2
                    and all(lane.get("combat") is not None and not lane["combat"].get("censored")
                            and first_divergence(lane) is None
                            for lane in report["lanes"]) and not report.get("reason")):
                complete_keys[track].add((source_key(report), report["round"], report["repetition"]))
            if (track == "OPERATION" and report.get("group") == "NONE"
                    and report.get("stage") in STAGES
                    and report.get("status") in VALID_OPERATION - {"CENSORED"}
                    and len(report.get("subjects", [])) == 4
                    and all(subject.get("status") != "PARTIAL_REPLAY" and first_divergence(subject) is None and subject.get("battles")
                            for subject in report["subjects"])):
                complete_keys[track].add((source_key(report), report["stage"]))
        completed = {track: len(keys) for track, keys in complete_keys.items()}
        mixed_runs = any(len(configs) > 1 for configs in scenario_configs.values())
        status = "EXCLUDED" if builder_id in EXCLUSIONS else "NO_SAMPLE" if not records else (
            "MEASURED" if not mixed_runs and all(completed[track] == expected[track] for track in expected) else
            "PARTIAL_REPLAY" if measured else "UNREPRODUCIBLE" if own else "NOT_RUN")
        reasons = Counter(report.get("reason") for _, report in own if report.get("reason"))
        if mixed_runs:
            reasons["MULTIPLE_CONFIGURATIONS_FOR_SAME_SCENARIO"] += 1
        divergences = []
        automation = Counter()
        automation_evidence = []
        for track, report in own:
            subjects = report.get("lanes", []) if track == "COMBAT" else report.get("subjects", [])
            controls = [{"team": subject.get("team"), "lane": subject.get("lane"),
                         **{field: subject[field] for field in ("automaticActivations", "automaticActions", "automation", "controlActions")
                            if subject.get(field) is not None}} for subject in subjects]
            if report.get("automaticActions") is not None or any(len(control) > 2 for control in controls):
                automation_evidence.append({"track": track, "matchId": report["matchId"], "playerRef": report["playerRef"],
                                            "round": report.get("round"), "repetition": report.get("repetition"),
                                            "stage": report["stage"], "group": report.get("augmentGroup", report.get("group")),
                                            "rootEvents": report.get("automaticActions"), "subjects": controls})
            for subject in subjects:
                failure = first_divergence(subject)
                if failure is not None:
                    divergences.append({"track": track, "matchId": report["matchId"], "playerRef": report["playerRef"],
                                        "round": report.get("round"), "stage": report["stage"],
                                        "group": report.get("augmentGroup", report.get("group")), "action": failure})
                automation.update(str(item) if isinstance(item, str) else item.get("policy", item.get("action", "UNKNOWN"))
                                  for field in ("automaticActions", "automation", "controlActions")
                                  for item in subject.get(field, []))
        coverage.append({"builderId": builder_id, "origin": builder["origin"], "status": status,
                         "exclusion": EXCLUSIONS.get(builder_id), "sourceRecords": len(records),
                         "distinctPlayers": len({record["playerRef"] for record in records}),
                         "expectedNoneReports": expected, "fullyReplayedNoneReports": completed,
                         "reportStatuses": dict(Counter(track + ":" + report["status"] for track, report in own)),
                         "measuredCheckpoints": {track: sorted({row["round"] for row in measured if row["track"] == track})
                                                 for track in expected}, "reasons": dict(reasons),
                         "firstDivergences": list({digest(value): value for value in divergences}.values()),
                         "disabledAugmentEvidence": [{"matchId": report["matchId"], "playerRef": report["playerRef"],
                                                       "stage": report["stage"], "group": report["group"],
                                                       "originalAugmentSha256": report["augmentSha256"],
                                                       "subjects": [{field: subject[field] for field in
                                                                     ("team", "lane", "augmentStatus", "augmentSelections")}
                                                                    for subject in report["subjects"]]}
                                                      for track, report in own if track == "OPERATION"
                                                      and disabled_augment_control(report)],
                         "automaticActionEvidenceAcrossMirrorsAndRepeats": automation_evidence,
                         "arenaReadinessWaits": [{"matchId": report["matchId"], "playerRef": report["playerRef"],
                                                 "round": report.get("round"), "repetition": report.get("repetition"),
                                                 "ticks": report["arenaReadyWaitTicks"]}
                                                for track, report in own if track == "COMBAT"
                                                and report.get("arenaReadyWaitTicks") is not None],
                         "automaticActionCountsAcrossMirrorsAndRepeats": dict(automation)})
    medians = official_medians(aggregates)
    return {"schemaVersion": 1, "manifestCanonicalSha256": digest(manifest), "reportCount": len(reports),
            "waveHashEvidence": evidence_provenance,
            "statusCounts": dict(Counter(row["status"] for row in coverage)), "coverage": coverage,
            "aggregates": aggregates, "officialMedians": medians, "reviewShortlist": review_shortlist(coverage, aggregates, medians),
            "caveats": CAVEATS + sorted({limit for _, report in reports for limit in report.get("limits", [])})}


def cohort_key(row):
    return tuple(row[key] for key in ("track", "round", "phase", "stage", "group", "configurationSha256"))


def official_medians(aggregates):
    cohorts = defaultdict(list)
    for row in aggregates:
        if row["origin"] == "OFFICIAL":
            cohorts[cohort_key(row)].append(row)
    result = []
    for _, rows in sorted(cohorts.items()):
        values = defaultdict(list)
        for row in rows:
            for name, value in row["rates"].items():
                if value is not None:
                    values[name].append(value)
            for name, value in row["metrics"].items():
                values["mean." + name].append(value["mean"])
        result.append({**{key: rows[0][key] for key in ("track", "round", "phase", "stage", "group", "configurationSha256")},
                       "builderIds": [row["builderId"] for row in rows], "builderCount": len(rows),
                       "values": {name: {"median": statistics.median(value), "builders": len(value)}
                                  for name, value in sorted(values.items())}})
    return result


def review_shortlist(coverage, aggregates, medians):
    result = [{"builderId": row["builderId"], "kind": "REPLAY_OR_COVERAGE_REVIEW", "status": row["status"],
               "reasons": row["reasons"], "measuredCheckpoints": row["measuredCheckpoints"],
               "firstDivergence": next(iter(row["firstDivergences"]), None)}
              for row in coverage if row["status"] in ("PARTIAL_REPLAY", "UNREPRODUCIBLE")]
    result.sort(key=lambda row: (row["status"] != "UNREPRODUCIBLE", row["firstDivergence"] is None, row["builderId"]))
    result = result[:8]
    differences = []
    baselines = {cohort_key(row): row for row in medians}
    for row in aggregates:
        baseline = baselines.get(cohort_key(row))
        if row["origin"] != "CREATIVE" or row["phase"] != "COMBAT_ENDPOINT" or row["group"] != "NONE" or not baseline:
            continue
        if row["rates"].get("censoredObservationRate") != 0:
            continue
        reference = baseline["values"].get("observedWindowClearRate")
        rate = row["rates"].get("observedWindowClearRate")
        if reference is not None and rate is not None and rate != reference["median"]:
            differences.append({"builderId": row["builderId"], "kind": "OBSERVED_CLEAR_DIFFERENCE_NOT_BALANCE_VERDICT",
                           **{key: row[key] for key in ("track", "round", "stage", "configurationSha256", "distinctPlayers")},
                           "observedWindowClearRate": rate, "officialBuilderMedian": reference["median"],
                           "officialBuilders": reference["builders"], "actionFailures": row["actionFailures"],
                           "nextCheck": "Check replay failures, actual investment, support and growth before any numeric change."})
    differences.sort(key=lambda row: -abs(row["observedWindowClearRate"] - row["officialBuilderMedian"]))
    return result + differences[:8]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("records", type=Path)
    parser.add_argument("logs", nargs="*", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--wave-hash-evidence", type=Path,
                        help="Verify explicit equivalent serialized wave configs before normalizing their hashes")
    args = parser.parse_args()
    result = summarize(json.loads(args.records.read_text()), read_reports(args.logs), args.wave_hash_evidence)
    result["manifestFileSha256"] = hashlib.sha256(args.records.read_bytes()).hexdigest()
    rendered = json.dumps(result, indent=2, ensure_ascii=False, allow_nan=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered)
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
