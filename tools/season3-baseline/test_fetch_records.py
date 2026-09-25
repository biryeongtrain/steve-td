import unittest

from fetch_records import coordinate_summary, normalize_action, normalize_record, registry_builders, select_records


def action(sequence=0, **changes):
    return {"sequence": sequence, "round": 1, "action_type": "TOWER_PLACE", "subject_id": "t1_pig_tower",
            "position_x": 42, "position_y": -1, "position_z": 2, "position_mode": "lane_relative",
            "cost": "9007199254740993", "income_gain": "0", "scheduled_round": 0,
            "target_team": "", "target_lane_id": 0, **changes}


def match(mid, player, final_round=2, actions=None, mode="NORMAL", job="semion-td:animal_towers"):
    return {"match_id": str(mid), "started_at": "2026-08-30T09:00:00Z", "ended_at": f"2026-08-30T10:{mid:02}:00Z",
            "final_round": final_round, "match_mode": mode, "catalog_version": f"catalog-{mid}",
            "participants": [{"player_id": player, "player_name": "PRIVATE", "job_id": job,
                              "team_id": "RED", "trait_loadout": None,
                              "build_actions": [action()] if actions is None else actions,
                              "round_outcomes": []}]}


class FetchRecordsTest(unittest.TestCase):
    def test_registry_matches_all_30_and_origin(self):
        builders = registry_builders()
        self.assertEqual(30, len(builders))
        self.assertEqual(14, sum(row["origin"] == "OFFICIAL" for row in builders))
        self.assertEqual(16, sum(row["origin"] == "CREATIVE" for row in builders))

    def test_actions_keep_integer_precision_sequence_and_nullable_history(self):
        source = match(1, "secret-player", 40, [action(9), action(2)])
        record = normalize_record(source, source["participants"][0])
        self.assertEqual([2, 9], record["sourceActionSequences"])
        self.assertEqual(9007199254740993, record["actions"][0]["cost"])
        self.assertEqual({"x": 42, "y": -1, "z": 2}, record["actions"][0]["position"])
        self.assertIsNone(record["attemptedRounds"])
        self.assertIsNone(record["sourceMap"])
        self.assertNotIn("secret-player", str(record))
        self.assertNotIn("PRIVATE", str(record))

    def test_latest_distinct_players_not_survival_and_no_final_composition_substitute(self):
        sources = [match(1, "a", 40), match(2, "b", 40), match(3, "c", 40),
                   match(4, "d", 1), match(5, "d", 2), match(6, "e", actions=[]),
                   match(7, "f", mode="TEST")]
        sources[-2]["participants"][0]["final_tower_composition"] = [{"towerTypeId": "t1_pig_tower"}]
        metrics = [{**source, "player_id": source["participants"][0]["player_id"],
                    "job_id": "semion-td:animal_towers"} for source in sources]
        by_id = {source["match_id"]: source for source in sources}
        rows, _ = select_records([{"builderId": "semion-td:animal_towers", "origin": "OFFICIAL"}], metrics, by_id.get)
        self.assertEqual(["5", "3", "2"], [record["matchId"] for record in rows[0]["records"]])
        self.assertEqual(["catalog-5", "catalog-3", "catalog-2"], [record["catalogVersion"] for record in rows[0]["records"]])

    def test_no_sample_alias_and_coordinates(self):
        source = match(1, "a", job="semion-td:ender_towers")
        metrics = [{**source, "player_id": "a", "job_id": "semion-td:ender_towers"}]
        rows, _ = select_records([{"builderId": "semion-td:end_towers", "origin": "OFFICIAL"},
                                  {"builderId": "semion-td:frost", "origin": "CREATIVE"}], metrics, lambda _: source)
        self.assertEqual("RECORDS_AVAILABLE", rows[0]["status"])
        self.assertEqual("NO_SAMPLE", rows[1]["status"])
        summary = coordinate_summary(rows)["lane_relative"]
        self.assertEqual({"x": 42, "y": -1, "z": 2}, summary["min"])
        self.assertEqual(1, summary["positionCount"])

    def test_duplicate_sequence_or_partial_position_rejected(self):
        source = match(1, "a", actions=[action(), action()])
        with self.assertRaises(ValueError):
            normalize_record(source, source["participants"][0])
        with self.assertRaises(ValueError):
            normalize_action(action(position_y=None))


if __name__ == "__main__":
    unittest.main()
