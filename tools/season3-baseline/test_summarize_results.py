import json
import hashlib
import copy
from pathlib import Path
import tempfile
import unittest

from summarize_results import action_summary, load_wave_hash_evidence, read_reports, summarize


def manifest(*builders):
    return {"builders": [{"builderId": name, "origin": origin,
                          "records": [{"matchId": str(index), "playerRef": player, "catalogVersion": "old-patch"}
                                      for index, player in enumerate(players)]} for name, origin, players in builders]}


def combat(builder, player="p", match="0", repeat=1, damage=100, ticks=100, **changes):
    tower = {"physicalDamageDealt": damage, "magicDamageDealt": 0, "firstCombatTick": 1, "lastCombatTick": ticks,
             "sampleCount": 1, "survivalTicks": ticks}
    return {"builderId": builder, "matchId": match, "playerRef": player, "sourceCatalogVersion": "old-patch",
            "status": "MEASURED", "measured": True, "round": 5, "stage": "COUNT_AND_HEALER_H0", "augmentGroup": "NONE",
            "repetition": repeat, "towerBalanceSha256": "current-config", "waveConfigSha256": "current-wave",
            "lanes": [{"team": "RED", "actions": [], "combat": {"combatMetrics": [tower], "endpointTick": ticks,
                                                                  "laneCleared": True, "leaks": 0}},
                      {"team": "BLUE", "actions": [], "combat": {"combatMetrics": [tower], "endpointTick": ticks,
                                                                   "laneCleared": True, "leaks": 0}}], **changes}


class SummarizeResultsTest(unittest.TestCase):
    def test_pooled_dps_repeats_and_official_builder_median(self):
        source = manifest(("a", "OFFICIAL", ["p", "q"]), ("b", "OFFICIAL", ["r"]))
        reports = [("COMBAT", combat("a", damage=100, ticks=100)),
                   ("COMBAT", combat("a", "q", "1", damage=100, ticks=300)),
                   ("COMBAT", combat("b", "r", damage=100, ticks=100))]
        reports[0][1]["arenaReadyWaitTicks"] = 9000
        result = summarize(source, reports)
        a = result["aggregates"][0]
        self.assertEqual(4, a["observations"])
        self.assertEqual(2, a["distinctPlayers"])
        self.assertEqual(10, a["rates"]["pooledTowerDps"])
        self.assertEqual(10, a["rates"]["boardDps"])
        self.assertEqual(9000, result["coverage"][0]["arenaReadinessWaits"][0]["ticks"])
        self.assertEqual(15, result["officialMedians"][0]["values"]["pooledTowerDps"]["median"])
        self.assertEqual("PARTIAL_REPLAY", result["coverage"][0]["status"])

    def test_missing_failed_excluded_and_configuration_separation(self):
        source = manifest(("a", "OFFICIAL", ["p"]), ("empty", "CREATIVE", []),
                          ("unrun", "CREATIVE", ["q"]), ("semion-td:developer", "CREATIVE", ["r"]))
        reports = [("COMBAT", combat("a")), ("COMBAT", combat("a", towerBalanceSha256="other-config")),
                   ("COMBAT", combat("a", status="FIXTURE_FAILED_PARTIAL", measured=False, round=25))]
        result = summarize(source, reports)
        self.assertEqual(2, len(result["aggregates"]))
        self.assertEqual(2, len(result["officialMedians"]))
        status = {row["builderId"]: row["status"] for row in result["coverage"]}
        self.assertEqual("NO_SAMPLE", status["empty"])
        self.assertEqual("NOT_RUN", status["unrun"])
        self.assertEqual("EXCLUDED", status["semion-td:developer"])
        self.assertEqual({"COMBAT": [5], "OPERATION": []}, result["coverage"][0]["measuredCheckpoints"])

    def test_operation_missing_fields_do_not_become_zero_and_growth_retained(self):
        source = manifest(("a", "OFFICIAL", ["p"]))
        subject = {"team": "RED", "lane": 1, "status": "PARTIAL_REPLAY", "actions": [
            {"round": 5, "status": "INSUFFICIENT_DIAMONDS", "diamondsDelta": 0}],
            "checkpoints": [{"round": 5, "phase": "COMBAT_ENDPOINT", "balance": {"diamonds": 9007199254740993},
                             "towers": [{"typeId": "growth", "runtimeDetails": ["stack 3"]}]}],
            "battles": [{"round": 5, "status": "CENSORED", "observedWaveTicks": 100, "laneCleared": False,
                         "accounting": {"unexplained": 0, "belowFloor": 0, "playerKilled": 2,
                                        "bossKilled": 1, "environmentalDeaths": 3,
                                        "diagnostics": [{"vanillaDeath": {"damageId": "cramming"}}]}}]}
        report = combat("a", status="CENSORED", stage="BASE", group="NONE", subjects=[subject])
        report.pop("augmentGroup")
        result = summarize(source, [("OPERATION", report)])
        row = result["aggregates"][0]
        self.assertIsNone(row["rates"]["pooledTowerDps"])
        self.assertEqual(9007199254740993, row["metrics"]["economy.diamonds"]["sum"])
        self.assertEqual(["stack 3"], row["growthSnapshots"][0]["towers"][0]["runtimeDetails"])
        self.assertEqual({"INSUFFICIENT_DIAMONDS": 1}, row["actionFailures"])
        self.assertNotIn("board.paidDiamonds", row["metrics"])
        self.assertEqual(3, row["metrics"]["accounting.environmentalDeaths"]["sum"])
        self.assertEqual(2, row["metrics"]["accounting.playerKilled"]["sum"])
        self.assertEqual(1, row["metrics"]["accounting.bossKilled"]["sum"])
        subject["battles"][0]["accounting"]["unexplained"] = 1
        self.assertEqual([], summarize(source, [("OPERATION", report)])["aggregates"])

    def test_completion_requires_both_tracks_and_uncensored_observations(self):
        source = manifest(("a", "OFFICIAL", ["p"]))
        reports = [("COMBAT", combat("a", repeat=repeat, round=round_number))
                   for round_number in (5, 15, 16, 25) for repeat in (1, 2, 3)]
        for stage in ("BASE", "HEALER_ONLY", "COUNT_AND_HEALER"):
            report = combat("a", status="OBSERVED_HISTORY_ENDPOINT", stage=stage, group="NONE",
                            subjects=[{"status": "OBSERVED_HISTORY_ENDPOINT", "battles": [{"round": 5}]}
                                      for _ in range(4)])
            report.pop("augmentGroup")
            reports.append(("OPERATION", report))
        self.assertEqual("MEASURED", summarize(source, reports)["coverage"][0]["status"])
        reports[0][1]["lanes"][0]["combat"]["censored"] = True
        self.assertEqual("PARTIAL_REPLAY", summarize(source, reports)["coverage"][0]["status"])

    def test_log_duplicates_conflicts_and_unknown_sources_are_rejected(self):
        report = combat("a")
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "test.log"
            line = "[server] SEASON3_RECORDED_COMBAT_REPORT " + json.dumps(report) + "\n"
            log.write_text(line * 2)
            self.assertEqual(1, len(read_reports([log])))
            log.write_text(line + "SEASON3_RECORDED_COMBAT_REPORT " + json.dumps({**report, "reason": "different"}))
            with self.assertRaisesRegex(ValueError, "Conflicting"):
                read_reports([log])
        with self.assertRaisesRegex(ValueError, "absent"):
            summarize(manifest(("b", "OFFICIAL", ["p"])), [("COMBAT", report)])

    def test_successful_economy_difference_and_pre_terminal_growth_timing(self):
        source = manifest(("a", "OFFICIAL", ["p"]))
        changed = {"round": 5, "sourceIndex": 0, "status": "SUCCESS", "divergenceReason": "SOURCE_DIAMOND_COST_CHANGED",
                   "diamondsDelta": -50, "emeraldsDelta": 0, "incomeDelta": 0, "emeraldProductionDelta": 0}
        subject = {"team": "RED", "lane": 1, "status": "OBSERVED_HISTORY_ENDPOINT", "actions": [changed],
                   "checkpoints": [{"round": 5, "phase": "COMBAT_ENDPOINT", "tick": 400, "towerStateTick": 399,
                                    "towerStateTiming": "PRE_TERMINAL_TICK", "balance": {"diamonds": 30},
                                    "towers": [{"typeId": "growth", "health": 20, "paidDiamonds": 50,
                                                "runtimeDetails": ["stack 3"]}]}]}
        report = combat("a", status="OBSERVED_HISTORY_ENDPOINT", stage="BASE", group="NONE", subjects=[subject],
                        replayMapTemplateSha256="map-1")
        report.pop("augmentGroup")
        second = {**report, "replayMapTemplateSha256": "map-2"}
        result = summarize(source, [("OPERATION", report), ("OPERATION", second)])
        self.assertEqual(2, len(result["aggregates"]))
        row = result["aggregates"][0]
        self.assertEqual({}, row["actionFailures"])
        self.assertEqual({"SOURCE_DIAMOND_COST_CHANGED": 1}, row["actionDifferences"])
        self.assertEqual(1, row["metrics"]["actions.successful"]["sum"])
        self.assertEqual({"PARTIAL_REPLAY": 1}, row["replayStatuses"])
        self.assertEqual(changed, result["coverage"][0]["firstDivergences"][0]["action"])
        self.assertEqual(20, row["metrics"]["boardPreTerminal.health"]["sum"])
        self.assertNotIn("board.health", row["metrics"])
        self.assertEqual(400, row["growthSnapshots"][0]["checkpointTick"])
        self.assertEqual(399, row["growthSnapshots"][0]["towerStateTick"])
        self.assertEqual("PRE_TERMINAL_TICK", row["growthSnapshots"][0]["towerStateTiming"])
        changed["lastAttemptTick"] = 401
        self.assertEqual(0, action_summary([changed], 5, 400, "COMBAT_ENDPOINT")["successful"])
        self.assertEqual(0, action_summary([changed], 5, 402, "PREPARE_START")["successful"])

    def test_normal_retreat_does_not_infer_deaths_or_add_review_flags(self):
        source = manifest(("semion-td:future_agency_towers", "CREATIVE", ["p"]))
        report = combat("semion-td:future_agency_towers")
        before = summarize(source, [("COMBAT", report)])
        for lane in report["lanes"]:
            lane["combat"].update(survivingDefenseTowers=0, nonCombatSupportCount=4)
            lane["combat"]["combatMetrics"][0]["deathCount"] = 0
        after = summarize(source, [("COMBAT", report)])
        self.assertEqual(before["reviewShortlist"], after["reviewShortlist"])
        self.assertEqual(before["coverage"][0]["status"], after["coverage"][0]["status"])
        self.assertEqual(1, after["aggregates"][0]["rates"]["observedWindowClearRate"])
        self.assertEqual(0, after["aggregates"][0]["metrics"]["tower.deathCount"]["sum"])

    def test_root_control_events_and_lane_activations_survive_summary(self):
        source = manifest(("semion-td:frost", "CREATIVE", ["p"]))
        event = {"playerId": "fixture-player", "round": 5, "worldTick": 123, "action": "FROST_FULL_OPERATION"}
        report = combat("semion-td:frost", automaticActions=[event])
        report["lanes"][0]["automaticActivations"] = 1
        report["lanes"][1]["automaticActivations"] = 2
        result = summarize(source, [("COMBAT", report)])
        evidence = result["coverage"][0]["automaticActionEvidenceAcrossMirrorsAndRepeats"][0]
        self.assertEqual([event], evidence["rootEvents"])
        self.assertEqual([1, 2], [lane["automaticActivations"] for lane in evidence["subjects"]])
        self.assertEqual(3, result["aggregates"][0]["metrics"]["control.automaticActivations"]["sum"])
        self.assertEqual(1, result["aggregates"][0]["distinctPlayers"])

    def test_wave_aliases_require_raw_sha_and_equal_values_preserving_provenance(self):
        serialized = ['{"counts":{"20":1.2,"25":1.3},"stages":[1,2]}',
                      '{"stages":[1,2],"counts":{"25":1.3,"20":1.2}}']
        variants = [{"serializedJson": raw, "sha256": hashlib.sha256(raw.encode()).hexdigest()} for raw in serialized]
        group = {"stage": "COUNT_AND_HEALER_H0", "acceptedReportStages": ["COUNT_AND_HEALER_H0"],
                 "preferredSha256": variants[0]["sha256"], "variants": variants}
        evidence = {"schemaVersion": 1, "groups": [group]}
        source = manifest(("a", "OFFICIAL", ["p", "q"]))
        reports = [("COMBAT", combat("a", waveConfigSha256=variants[0]["sha256"])),
                   ("COMBAT", combat("a", "q", "1", waveConfigSha256=variants[1]["sha256"]))]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "evidence.json"
            path.write_text(json.dumps(evidence))
            self.assertEqual(2, len(summarize(source, reports)["aggregates"]))
            result = summarize(source, reports, path)
            self.assertEqual(1, len(result["aggregates"]))
            self.assertEqual(2, len(result["aggregates"][0]["originalConfigurations"]))
            self.assertEqual(str(path.resolve()), result["waveHashEvidence"]["path"])
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), result["waveHashEvidence"]["fileSha256"])
            for _, report in reports:
                report["stage"] = "BASE"
            self.assertEqual(2, len(summarize(source, reports, path)["aggregates"]))
            for _, report in reports:
                report["stage"] = "COUNT_AND_HEALER_H0"
            reports[1][1]["matchId"], reports[1][1]["playerRef"] = "0", "p"
            with self.assertRaisesRegex(ValueError, "overlapping"):
                summarize(source, reports, path)
            variants[1]["sha256"] = "0" * 64
            path.write_text(json.dumps(evidence))
            with self.assertRaisesRegex(ValueError, "SHA mismatch"):
                load_wave_hash_evidence(path)
            variants[1]["serializedJson"] = '{"counts":{"20":1.2,"25":1.4},"stages":[1,2]}'
            variants[1]["sha256"] = hashlib.sha256(variants[1]["serializedJson"].encode()).hexdigest()
            path.write_text(json.dumps(evidence))
            with self.assertRaisesRegex(ValueError, "values differ"):
                load_wave_hash_evidence(path)
            variants[1]["serializedJson"] = '{"counts":{"20":1.2,"25":1.3},"stages":[2,1]}'
            variants[1]["sha256"] = hashlib.sha256(variants[1]["serializedJson"].encode()).hexdigest()
            path.write_text(json.dumps(evidence))
            with self.assertRaisesRegex(ValueError, "values differ"):
                load_wave_hash_evidence(path)

    def test_disabled_augment_cohort_requires_explicit_four_subject_proof(self):
        source = manifest(("a", "OFFICIAL", ["p", "q"]))
        subjects = [{"team": team, "lane": lane, "augmentStatus": "NONE_CONTROL", "augmentSelections": [],
                     "checkpoints": [{"round": 5, "phase": "PREPARE_START", "balance": {"diamonds": 100}}]}
                    for team in ("RED", "BLUE") for lane in (1, 2)]
        first = combat("a", status="OBSERVED_HISTORY_ENDPOINT", stage="BASE", group="NONE",
                       subjects=subjects, augmentSha256="raw-augment-a")
        first.pop("augmentGroup")
        second = {**copy.deepcopy(first), "matchId": "1", "playerRef": "q", "augmentSha256": "raw-augment-b"}
        reports = [("OPERATION", first), ("OPERATION", second)]
        result = summarize(source, reports)
        self.assertEqual(1, len(result["aggregates"]))
        cohort = result["aggregates"][0]
        self.assertEqual("DISABLED_NO_SELECTIONS", cohort["configuration"]["augmentState"])
        self.assertNotIn("augmentSha256", cohort["configuration"])
        self.assertEqual({"raw-augment-a", "raw-augment-b"},
                         {value["augmentSha256"] for value in cohort["originalConfigurations"]})
        self.assertTrue(all(row["reasons"] for row in cohort["configurationNormalizationReasons"]))
        self.assertEqual(2, len(result["coverage"][0]["disabledAugmentEvidence"]))
        for mutation in (lambda report: report.update(group="ATTACK"),
                         lambda report: report["subjects"][0].update(augmentSelections=[{"cardId": "card"}]),
                         lambda report: report["subjects"][0].update(augmentSelections=None),
                         lambda report: report["subjects"][0].pop("augmentSelections"),
                         lambda report: report["subjects"][0].update(augmentStatus="NOT_REACHED"),
                         lambda report: report["subjects"].pop(),
                         lambda report: report["subjects"][0].update(team="BLUE")):
            changed = copy.deepcopy(second)
            mutation(changed)
            checked = summarize(source, [("OPERATION", first), ("OPERATION", changed)])
            raw = next(row for row in checked["aggregates"] if "augmentSha256" in row["configuration"])
            self.assertEqual("raw-augment-b", raw["configuration"]["augmentSha256"])


if __name__ == "__main__":
    unittest.main()
