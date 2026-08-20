package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.RoundWaveConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import org.junit.jupiter.api.Test;

final class SemionGameLateJoinTest {
    @Test
    void catchUpDiamondUsesLaneRewardsThroughRequestedRoundAndFiveIncomePayments() {
        WaveConfig waves = new WaveConfig(
                List.of(
                        round(1, Map.of("default", List.of(monster("r1", 3, 4)))),
                        round(2, Map.of(
                                "default", List.of(monster("r2-default", 5, 2)),
                                "lane_2", List.of(monster("r2-lane-2", 7, 3))
                        )),
                        round(3, Map.of("default", List.of(monster("r3", 100, 100))))
                ),
                20,
                null
        );

        assertEquals(83, SemionGame.lateJoinCatchUpDiamond(waves, 2, 2, 10));
    }

    private static RoundWaveConfig round(int round, Map<String, List<WaveMonsterEntry>> lanes) {
        return new RoundWaveConfig(round, lanes);
    }

    private static WaveMonsterEntry monster(String id, long reward, int count) {
        return new WaveMonsterEntry(
                id,
                10.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                reward,
                count
        );
    }
}
