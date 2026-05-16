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
                300,
                8,
                11,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                30,
                30
        );
        return new WaveConfig(
                List.of(
                        round(1, "basic_melee_1", 18, 0, 1, AttackKind.MELEE, "minecraft:zombie", 4, 12),
                        round(2, "basic_melee_2", 24, 0, 2, AttackKind.MELEE, "minecraft:zombie", 5, 14),
                        round(3, "basic_swarm_3", 22, 0, 2, AttackKind.MELEE, "minecraft:zombie", 5, 18),
                        round(4, "armored_melee_4", 36, 2, 2, AttackKind.MELEE, "minecraft:husk", 6, 16),
                        round(5, "ranged_skeleton_5", 30, 0, 2, AttackKind.RANGED, "minecraft:skeleton", 7, 14),
                        round(6, "fast_melee_6", 38, 1, 2, AttackKind.MELEE, "minecraft:zombie", 7, 20),
                        round(7, "armored_swarm_7", 55, 3, 3, AttackKind.MELEE, "minecraft:husk", 8, 18),
                        round(8, "ranged_pack_8", 48, 1, 2, AttackKind.RANGED, "minecraft:skeleton", 8, 18),
                        round(9, "mixed_melee_9", 70, 4, 4, AttackKind.MELEE, "minecraft:husk", 9, 20),
                        round(10, "elite_melee_10", 90, 5, 4, AttackKind.MELEE, "minecraft:zombie", 11, 16),
                        round(11, "ranged_pressure_11", 74, 2, 4, AttackKind.RANGED, "minecraft:skeleton", 11, 20),
                        round(12, "heavy_swarm_12", 100, 6, 5, AttackKind.MELEE, "minecraft:husk", 12, 22),
                        round(13, "fast_pressure_13", 94, 4, 6, AttackKind.MELEE, "minecraft:zombie", 13, 26),
                        round(14, "armored_ranged_14", 112, 5, 5, AttackKind.RANGED, "minecraft:skeleton", 14, 20),
                        round(15, "elite_pack_15", 145, 8, 7, AttackKind.MELEE, "minecraft:husk", 16, 20),
                        round(16, "mixed_horde_16", 140, 7, 7, AttackKind.MELEE, "minecraft:zombie", 16, 28),
                        round(17, "ranged_horde_17", 150, 6, 7, AttackKind.RANGED, "minecraft:skeleton", 17, 24),
                        round(18, "heavy_pressure_18", 190, 10, 8, AttackKind.MELEE, "minecraft:husk", 19, 24),
                        round(19, "pre_infinite_wave_19", 240, 12, 10, AttackKind.MELEE, "minecraft:husk", 22, 28)
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
