package kim.biryeong.semiontd.config;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record WaveConfig(List<RoundWaveConfig> rounds, int infiniteFromRound, RoundWaveConfig infinite) {
    public WaveConfig {
        rounds = rounds == null ? List.of() : rounds.stream()
                .sorted(Comparator.comparingInt(RoundWaveConfig::round))
                .toList();
        if (infiniteFromRound < 1) {
            infiniteFromRound = 20;
        }
    }

    public static WaveConfig defaultConfig() {
        WaveMonsterEntry infiniteMonster = new WaveMonsterEntry(
                "infinite_melee",
                400,
                8,
                25,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                30,
                30
        );
        return new WaveConfig(
                List.of(
                        round(1, "basic_melee_1", 35, 0, 4, AttackKind.MELEE, "minecraft:zombie", 4, 12),
                        round(2, "basic_melee_2", 45, 0, 5, AttackKind.MELEE, "minecraft:zombie", 5, 14),
                        round(3, "basic_swarm_3", 40, 0, 5, AttackKind.MELEE, "minecraft:zombie", 5, 18),
                        round(4, "armored_melee_4", 65, 2, 6, AttackKind.MELEE, "minecraft:husk", 6, 16),
                        round(5, "ranged_skeleton_5", 55, 0, 5, AttackKind.RANGED, "minecraft:skeleton", 7, 14),
                        round(6, "fast_melee_6", 60, 1, 7, AttackKind.MELEE, "minecraft:zombie", 7, 20),
                        round(7, "armored_swarm_7", 85, 3, 8, AttackKind.MELEE, "minecraft:husk", 8, 18),
                        round(8, "ranged_pack_8", 75, 1, 7, AttackKind.RANGED, "minecraft:skeleton", 8, 18),
                        round(9, "mixed_melee_9", 105, 4, 9, AttackKind.MELEE, "minecraft:husk", 9, 20),
                        round(10, "elite_melee_10", 140, 5, 11, AttackKind.MELEE, "minecraft:zombie", 11, 16),
                        round(11, "ranged_pressure_11", 115, 2, 10, AttackKind.RANGED, "minecraft:skeleton", 11, 20),
                        round(12, "heavy_swarm_12", 150, 6, 12, AttackKind.MELEE, "minecraft:husk", 12, 22),
                        round(13, "fast_pressure_13", 135, 4, 13, AttackKind.MELEE, "minecraft:zombie", 13, 26),
                        round(14, "armored_ranged_14", 160, 5, 12, AttackKind.RANGED, "minecraft:skeleton", 14, 20),
                        round(15, "elite_pack_15", 210, 8, 16, AttackKind.MELEE, "minecraft:husk", 16, 20),
                        round(16, "mixed_horde_16", 200, 7, 15, AttackKind.MELEE, "minecraft:zombie", 16, 28),
                        round(17, "ranged_horde_17", 210, 6, 16, AttackKind.RANGED, "minecraft:skeleton", 17, 24),
                        round(18, "heavy_pressure_18", 270, 10, 19, AttackKind.MELEE, "minecraft:husk", 19, 24),
                        round(19, "pre_infinite_wave_19", 330, 12, 22, AttackKind.MELEE, "minecraft:husk", 22, 28)
                ),
                20,
                new RoundWaveConfig(20, Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, List.of(infiniteMonster)))
        );
    }

    public Optional<RoundWaveConfig> configForRound(int round) {
        if (round >= infiniteFromRound && infinite != null) {
            return Optional.of(infinite);
        }
        return rounds.stream()
                .filter(config -> config.round() == round)
                .findFirst();
    }

    private static RoundWaveConfig round(
            int round,
            String id,
            double health,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityType,
            long mineralReward,
            int count
    ) {
        return new RoundWaveConfig(round, Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, List.of(new WaveMonsterEntry(
                id,
                health,
                armor,
                attackDamage,
                attackKind,
                entityType,
                null,
                mineralReward,
                count
        ))));
    }
}
