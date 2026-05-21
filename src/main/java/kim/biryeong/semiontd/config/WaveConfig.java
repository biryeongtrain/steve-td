package kim.biryeong.semiontd.config;

import java.util.Comparator;
import java.util.LinkedHashMap;
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
                29.0,
                8,
                1.0,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                30,
                30
        );
        return new WaveConfig(
                List.of(
                        round(1, "basic_melee_1", 10.0, 0, 1.0, AttackKind.MELEE, "minecraft:zombie", 4, 12),
                        round(2, "basic_melee_2", 11.0, 0, 1.0, AttackKind.MELEE, "minecraft:zombie", 5, 14),
                        round(3, "basic_swarm_3", 12.0, 0, 1.0, AttackKind.MELEE, "minecraft:zombie", 5, 18),
                        round(4, "armored_melee_4", 13.0, 2, 1.0, AttackKind.MELEE, "minecraft:husk", 6, 16),
                        round(5, "ranged_skeleton_5", 14.0, 0, 0.0, AttackKind.RANGED, "minecraft:skeleton", 7, 20),
                        round(6, "fast_melee_6", 15.0, 1, 1.0, AttackKind.MELEE, "minecraft:zombie", 7, 40),
                        round(7, "armored_swarm_7", 16.0, 3, 1.0, AttackKind.MELEE, "minecraft:husk", 8, 30),
                        round(8, "ranged_pack_8", 17.0, 1, 0.0, AttackKind.RANGED, "minecraft:skeleton", 8, 30),
                        round(9, "mixed_melee_9", 18.0, 4, 1.0, AttackKind.MELEE, "minecraft:husk", 9, 40),
                        round(10, "elite_melee_10", 19.0, 5, 1.0, AttackKind.MELEE, "minecraft:zombie", 11, 25),
                        round(11, "ranged_pressure_11", 20.0, 2, 0.0, AttackKind.RANGED, "minecraft:skeleton", 11, 30),
                        round(12, "heavy_swarm_12", 21.0, 6, 1.0, AttackKind.MELEE, "minecraft:husk", 12, 40),
                        round(13, "fast_pressure_13", 22.0, 4, 1.0, AttackKind.MELEE, "minecraft:zombie", 13, 50),
                        round(14, "armored_ranged_14", 23.0, 5, 0.0, AttackKind.RANGED, "minecraft:skeleton", 14, 30),
                        round(15, "elite_pack_15", 24.0, 8, 1.0, AttackKind.MELEE, "minecraft:husk", 16, 40),
                        round(16, "mixed_horde_16", 25.0, 7, 1.0, AttackKind.MELEE, "minecraft:zombie", 16, 50),
                        round(17, "ranged_horde_17", 26.0, 6, 0.0, AttackKind.RANGED, "minecraft:skeleton", 17, 60),
                        round(18, "heavy_pressure_18", 27.0, 10, 1.0, AttackKind.MELEE, "minecraft:husk", 19, 60),
                        round(19, "pre_infinite_wave_19", 28.0, 12, 1.0, AttackKind.MELEE, "minecraft:husk", 22, 60)
                ),
                20,
                new RoundWaveConfig(20, Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, List.of(infiniteMonster)))
        );
    }

    public Optional<RoundWaveConfig> configForRound(int round) {
        if (round >= infiniteFromRound && infinite != null) {
            return Optional.of(scaleInfiniteRound(round));
        }
        return rounds.stream()
                .filter(config -> config.round() == round)
                .findFirst();
    }

    private RoundWaveConfig scaleInfiniteRound(int round) {
        double healthMultiplier = 1.0 + Math.max(0, round - infiniteFromRound) * 0.40;
        Map<String, List<WaveMonsterEntry>> scaledLanes = new LinkedHashMap<>();
        for (Map.Entry<String, List<WaveMonsterEntry>> lane : infinite.lanes().entrySet()) {
            scaledLanes.put(
                    lane.getKey(),
                    lane.getValue().stream()
                            .map(entry -> scaleInfiniteEntry(entry, healthMultiplier))
                            .toList()
            );
        }
        return new RoundWaveConfig(round, scaledLanes);
    }

    private static WaveMonsterEntry scaleInfiniteEntry(WaveMonsterEntry entry, double healthMultiplier) {
        return new WaveMonsterEntry(
                entry.id(),
                entry.health() * healthMultiplier,
                entry.armor(),
                entry.attackDamage(),
                entry.attackKind(),
                entry.entityType(),
                entry.blockbenchModelId(),
                entry.dimensions(),
                entry.mineralReward(),
                entry.count()
        );
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
