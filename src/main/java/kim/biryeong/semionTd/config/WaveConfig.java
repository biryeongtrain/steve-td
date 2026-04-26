package kim.biryeong.semionTd.config;

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
        WaveMonsterEntry roundOne = new WaveMonsterEntry(
                "basic_melee_1",
                35,
                0,
                4,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                4,
                12
        );
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
                List.of(new RoundWaveConfig(1, Map.of(RoundWaveConfig.DEFAULT_LANE_KEY, List.of(roundOne)))),
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
}