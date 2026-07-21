package kim.biryeong.semiontd.config;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public record WaveConfig(
        List<RoundWaveConfig> rounds,
        int infiniteFromRound,
        RoundWaveConfig infinite,
        List<RoundWaveConfig> infiniteTemplates
) {
    public WaveConfig(List<RoundWaveConfig> rounds, int infiniteFromRound, RoundWaveConfig infinite) {
        this(rounds, infiniteFromRound, infinite, List.of());
    }

    public WaveConfig {
        rounds = rounds == null ? List.of() : rounds.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(RoundWaveConfig::round))
                .toList();
        infiniteTemplates = infiniteTemplates == null ? List.of() : infiniteTemplates.stream()
                .filter(Objects::nonNull)
                .toList();
        if (infiniteFromRound < 1) {
            infiniteFromRound = 20;
        }
    }

    public static WaveConfig defaultConfig() {
        List<RoundWaveConfig> rounds = List.of(
                round(1, monster("animal_pig_1", 10.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:pig", 3, 12, 0, 1.0, 2.5, 13)),
                round(2, monster("animal_sheep_2", 11.5, 0.0, 1.0, AttackKind.MELEE, "minecraft:sheep", 3, 14, 0, 1.0, 2.5, 13)),
                round(3, monster("animal_cow_3", 13.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:cow", 3, 18, 0, 1.0, 2.5, 13)),
                round(4, monster("animal_wolf_4", 14.5, 2.0, 1.5, AttackKind.MELEE, "minecraft:wolf", 5, 16, 0, 1.0, 2.5, 13)),
                round(5, monster("animal_llama_5", 16.0, 0.0, 0.5, AttackKind.RANGED, "minecraft:llama", 5, 20, 0, 1.0, 6.0, 13)),
                round(6, monster("zombie_rush_6", 15.0, 1.0, 1.25, AttackKind.MELEE, "minecraft:zombie", 4, 35, 0, 1.2, 2.5, 11)),
                round(7, monster("husk_swarm_7", 19.0, 3.0, 2.0, AttackKind.MELEE, "minecraft:husk", 4, 30, 0, 1.0, 2.5, 13)),
                roundRobin(8,
                        monster("husk_tank_8", 25.0, 4.0, 1.0, AttackKind.MELEE, "minecraft:husk", 5, 20, 45, 0.95, 2.5, 18),
                        monster("skeleton_ranged_8", 12.0, 0.0, 1.5, AttackKind.RANGED, "minecraft:skeleton", 5, 20, 0, 0.8, 7.0, 18)
                ),
                round(9, monster("creeper_melee_9", 22.0, 4.0, 2.0, AttackKind.MELEE, "minecraft:creeper", 5, 40, 0, 1.0, 2.5, 13)),
                round(10, monster("vindicator_elite_10", 35.25, 5.0, 2.5, AttackKind.MELEE, "minecraft:vindicator", 7, 25, 0, 1.0, 2.5, 13)),
                roundRobin(11,
                        monster("zombie_tank_11", 60.0, 6.0, 2.0, AttackKind.MELEE, "minecraft:zombie", 5, 15, 45, 0.95, 2.5, 18),
                        monster("stray_ranged_11", 20.625, 1.0, 2.5, AttackKind.RANGED, "minecraft:stray", 5, 20, 0, 0.8, 8.0, 16)
                ),
                round(12, monster("bogged_swarm_12", 39.75, 6.0, 2.5, AttackKind.MELEE, "minecraft:bogged", 6, 40, 0, 1.0, 2.5, 13)),
                round(13, monster("spider_pressure_13", 42.0, 4.0, 3.0, AttackKind.MELEE, "minecraft:spider", 5, 50, 0, 1.3, 2.5, 10)),
                roundRobin(14,
                        monster("vindicator_tank_14", 75.0, 9.0, 2.5, AttackKind.MELEE, "minecraft:vindicator", 9, 15, 45, 0.95, 2.5, 18),
                        monster("pillager_artillery_14", 21.1875, 2.0, 4.0, AttackKind.RANGED, "minecraft:pillager", 9, 20, 0, 0.7, 9.0, 24)
                ),
                round(15, monster("warden_boss_15", 1100.0, 10.0, 30.0, AttackKind.MELEE, "minecraft:warden", 100, 4, 0, 1.0, 2.5, 13)),
                roundRobin(16,
                        monster("hoglin_tank_16", 120.0, 10.0, 4.0, AttackKind.MELEE, "minecraft:hoglin", 5, 20, 45, 0.95, 2.5, 20),
                        monster("zombified_piglin_rush_16", 60.0, 3.0, 6.0, AttackKind.MELEE, "minecraft:zombified_piglin", 5, 20, 5, 1.3, 2.5, 9),
                        monster("piglin_ranged_16", 45.0, 3.0, 5.0, AttackKind.RANGED, "minecraft:piglin", 5, 20, 0, 0.8, 8.0, 15)
                ),
                roundRobin(17,
                        monster("piglin_brute_tank_17", 80.0, 10.0, 3.0, AttackKind.MELEE, "minecraft:piglin_brute", 4, 50, 45, 0.95, 2.5, 18),
                        monster("blaze_ranged_17", 40.0, 3.0, 5.0, AttackKind.RANGED, "minecraft:blaze", 4, 50, 0, 0.8, 8.0, 14)
                ),
                roundRobin(18,
                        monster("magma_cube_tank_18", 120.0, 14.0, 5.0, AttackKind.MELEE, "minecraft:magma_cube", 4, 20, 45, 0.95, 2.5, 20),
                        monster("wither_skeleton_rush_18", 45.0, 7.0, 7.0, AttackKind.MELEE, "minecraft:wither_skeleton", 4, 40, 5, 1.3, 2.5, 10),
                        monster("piglin_artillery_18", 30.0, 4.0, 9.0, AttackKind.RANGED, "minecraft:piglin", 4, 20, 0, 0.7, 10.0, 24)
                ),
                roundRobin(19,
                        monster("hoglin_tank_19", 160.0, 16.0, 6.0, AttackKind.MELEE, "minecraft:hoglin", 4, 40, 45, 0.95, 2.5, 20),
                        monster("blaze_artillery_19", 80.0, 6.0, 10.0, AttackKind.RANGED, "minecraft:blaze", 4, 40, 0, 0.7, 11.0, 24)
                )
        );

        RoundWaveConfig animalStampede = roundRobin(20,
                monster("infinite_cow_tank", 250.0, 14.0, 10.0, AttackKind.MELEE, "minecraft:cow", 1, 27, 45, 0.95, 2.5, 20),
                monster("infinite_llama_ranged", 132.14, 4.0, 18.0, AttackKind.RANGED, "minecraft:llama", 1, 28, 0, 0.95, 9.0, 16)
        );
        RoundWaveConfig overworldAssault = roundRobin(20,
                monster("infinite_husk_tank", 300.0, 14.0, 10.0, AttackKind.MELEE, "minecraft:husk", 1, 20, 45, 0.95, 2.5, 20),
                monster("infinite_spider_rush", 150.0, 7.0, 10.0, AttackKind.MELEE, "minecraft:spider", 1, 20, 5, 1.3, 2.5, 10),
                monster("infinite_pillager_artillery", 96.67, 4.0, 18.0, AttackKind.RANGED, "minecraft:pillager", 1, 15, 0, 0.95, 11.0, 24)
        );
        RoundWaveConfig zombifiedLegion = roundRobin(20,
                monster("infinite_piglin_brute_tank", 350.0, 14.0, 10.0, AttackKind.MELEE, "minecraft:piglin_brute", 1, 15, 45, 0.95, 2.5, 20),
                monster("infinite_zombified_piglin_rush", 140.0, 7.0, 10.0, AttackKind.MELEE, "minecraft:zombified_piglin", 1, 25, 5, 1.3, 2.5, 10),
                monster("infinite_blaze_ranged", 113.33, 4.0, 18.0, AttackKind.RANGED, "minecraft:blaze", 1, 15, 0, 0.95, 9.0, 16)
        );
        return new WaveConfig(rounds, 20, animalStampede, List.of(animalStampede, overworldAssault, zombifiedLegion));
    }

    public Optional<RoundWaveConfig> configForRound(int round) {
        return candidatesForRound(round).stream().findFirst();
    }

    public Optional<RoundWaveConfig> selectForRound(int round, Random random) {
        List<RoundWaveConfig> candidates = candidatesForRound(round);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }
        Objects.requireNonNull(random, "random");
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    public List<RoundWaveConfig> candidatesForRound(int round) {
        if (round >= infiniteFromRound) {
            List<RoundWaveConfig> templates = infiniteTemplates.isEmpty()
                    ? infinite == null ? List.of() : List.of(infinite)
                    : infiniteTemplates;
            return templates.stream()
                    .map(template -> scaleInfiniteRound(template, round))
                    .toList();
        }
        return rounds.stream()
                .filter(config -> config.round() == round)
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }

    private RoundWaveConfig scaleInfiniteRound(RoundWaveConfig template, int round) {
        int elapsedRounds = Math.max(0, round - infiniteFromRound);
        double healthMultiplier = 1.0 + elapsedRounds * 0.40;
        double attackDamageMultiplier = 1.0 + elapsedRounds * 0.03;
        Map<String, List<WaveMonsterEntry>> scaledLanes = new LinkedHashMap<>();
        for (Map.Entry<String, List<WaveMonsterEntry>> lane : template.lanes().entrySet()) {
            scaledLanes.put(
                    lane.getKey(),
                    lane.getValue().stream()
                            .map(entry -> scaleInfiniteEntry(entry, healthMultiplier, attackDamageMultiplier))
                            .toList()
            );
        }
        return new RoundWaveConfig(round, template.spawnMode(), template.spawnIntervalTicks(), scaledLanes);
    }

    private static WaveMonsterEntry scaleInfiniteEntry(
            WaveMonsterEntry entry,
            double healthMultiplier,
            double attackDamageMultiplier
    ) {
        return new WaveMonsterEntry(
                entry.id(),
                entry.health() * healthMultiplier,
                entry.armor(),
                entry.attackDamage() * attackDamageMultiplier,
                entry.attackKind(),
                entry.entityType(),
                entry.blockbenchModelId(),
                entry.dimensions(),
                entry.mineralReward(),
                entry.count(),
                entry.targetPriority(),
                entry.movementSpeedMultiplier(),
                entry.attackRange(),
                entry.attackIntervalTicks()
        );
    }

    private static RoundWaveConfig round(int round, WaveMonsterEntry... entries) {
        return new RoundWaveConfig(round, Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, List.of(entries)));
    }

    private static RoundWaveConfig roundRobin(int round, WaveMonsterEntry... entries) {
        return new RoundWaveConfig(
                round,
                WaveSpawnMode.ROUND_ROBIN,
                1,
                Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, List.of(entries))
        );
    }

    private static WaveMonsterEntry monster(
            String id,
            double health,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityType,
            long mineralReward,
            int count,
            double targetPriority,
            double movementSpeedMultiplier,
            double attackRange,
            int attackIntervalTicks
    ) {
        return new WaveMonsterEntry(
                id,
                health,
                armor,
                attackDamage,
                attackKind,
                entityType,
                null,
                null,
                mineralReward,
                count,
                targetPriority,
                movementSpeedMultiplier,
                attackRange,
                attackIntervalTicks
        );
    }
}
