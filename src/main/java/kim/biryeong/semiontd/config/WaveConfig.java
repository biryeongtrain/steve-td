package kim.biryeong.semiontd.config;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record WaveConfig(List<RoundWaveConfig> rounds, int infiniteFromRound, RoundWaveConfig infinite) {
    private static final double BASE_WAVE_HEALTH = 10.0;
    private static final double WAVE_HEALTH_PER_ROUND = 1.5;
    private static final int LATE_GAME_HEALTH_SCALING_START_ROUND = 10;
    private static final double LATE_GAME_HEALTH_MULTIPLIER = 1.5;
    private static final double BASE_WAVE_ATTACK_DAMAGE = 1.0;
    private static final int WAVE_ATTACK_DAMAGE_SCALING_ROUND_STEP = 3;
    private static final double WAVE_ATTACK_DAMAGE_PER_STEP = 0.5;
    private static final double RANGED_WAVE_ATTACK_DAMAGE_PENALTY = 1.0;
    private static final double INFINITE_WAVE_HEALTH_PER_ROUND = 0.40;

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
                waveHealth(20),
                8,
                waveAttackDamage(20, AttackKind.MELEE),
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                30,
                30
        );
        return new WaveConfig(
                List.of(
                        round(1, "basic_melee_1", 0, AttackKind.MELEE, "minecraft:zombie", 4, 12),
                        round(2, "basic_melee_2", 0, AttackKind.MELEE, "minecraft:zombie", 5, 14),
                        round(3, "basic_swarm_3", 0, AttackKind.MELEE, "minecraft:zombie", 5, 18),
                        round(4, "armored_melee_4", 2, AttackKind.MELEE, "minecraft:husk", 6, 16),
                        round(5, "ranged_skeleton_5", 0, AttackKind.RANGED, "minecraft:skeleton", 7, 20),
                        round(6, "fast_melee_6", 1, AttackKind.MELEE, "minecraft:zombie", 7, 40),
                        round(7, "armored_swarm_7", 3, AttackKind.MELEE, "minecraft:husk", 8, 30),
                        round(8, "ranged_pack_8", 1, AttackKind.RANGED, "minecraft:skeleton", 8, 30),
                        round(9, "mixed_melee_9", 4, AttackKind.MELEE, "minecraft:husk", 9, 40),
                        round(10, "elite_melee_10", 5, AttackKind.MELEE, "minecraft:zombie", 11, 25),
                        round(11, "ranged_pressure_11", 2, AttackKind.RANGED, "minecraft:skeleton", 11, 30),
                        round(12, "heavy_swarm_12", 6, AttackKind.MELEE, "minecraft:husk", 12, 40),
                        round(13, "fast_pressure_13", 4, AttackKind.MELEE, "minecraft:zombie", 13, 50),
                        round(14, "armored_ranged_14", 5, AttackKind.RANGED, "minecraft:skeleton", 14, 30),
                        round(15, "elite_pack_15", 8, AttackKind.MELEE, "minecraft:husk", 16, 40),
                        round(16, "mixed_horde_16", 7, AttackKind.MELEE, "minecraft:zombie", 16, 50),
                        round(17, "ranged_horde_17", 6, AttackKind.RANGED, "minecraft:skeleton", 17, 60),
                        round(18, "heavy_pressure_18", 10, AttackKind.MELEE, "minecraft:husk", 19, 60),
                        round(19, "pre_infinite_wave_19", 12, AttackKind.MELEE, "minecraft:husk", 22, 60)
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
        double healthMultiplier = 1.0 + Math.max(0, round - infiniteFromRound) * INFINITE_WAVE_HEALTH_PER_ROUND;
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
            double armor,
            AttackKind attackKind,
            String entityType,
            long mineralReward,
            int count
    ) {
        return new RoundWaveConfig(round, Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, List.of(new WaveMonsterEntry(
                id,
                waveHealth(round),
                armor,
                waveAttackDamage(round, attackKind),
                attackKind,
                entityType,
                null,
                mineralReward,
                count
        ))));
    }

    private static double waveHealth(int round) {
        double health = BASE_WAVE_HEALTH + Math.max(0, round - 1) * WAVE_HEALTH_PER_ROUND;
        return round >= LATE_GAME_HEALTH_SCALING_START_ROUND
                ? health * LATE_GAME_HEALTH_MULTIPLIER
                : health;
    }

    private static double waveAttackDamage(int round, AttackKind attackKind) {
        int scalingSteps = Math.max(0, round - 1) / WAVE_ATTACK_DAMAGE_SCALING_ROUND_STEP;
        double damage = BASE_WAVE_ATTACK_DAMAGE + scalingSteps * WAVE_ATTACK_DAMAGE_PER_STEP;
        if (attackKind == AttackKind.RANGED) {
            damage -= RANGED_WAVE_ATTACK_DAMAGE_PENALTY;
        }
        return Math.max(0.0, damage);
    }
}
