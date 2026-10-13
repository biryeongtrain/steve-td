package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarlockStatsTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetState() {
        WarlockAwakeningProgress.clearAllForTesting();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void runtimeStatsShowAbsorptionAndAccumulatedCombatValues() {
        WarlockTower tower = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        String details = String.join("\n", tower.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertEquals(7.0, tower.adjustAttackRange(7.0), 0.0001);
        assertEquals(5, tower.minimumAttackIntervalTicks());
        assertEquals(0.30, tower.incomeDebuffResistance(), 0.0001);
        assertTrue(details.contains("영구 흡수: 0기"));
        assertTrue(details.contains("라운드 흡수: 0기"));
        assertTrue(details.contains("각성 해금: 0/1400킬"));
        assertTrue(details.contains("영구 체력: +0"));
        assertFalse(details.contains("재생:"));
        assertTrue(details.contains("생명력 흡수: 0% (10)"));
        assertTrue(details.contains("피해 감소: +0% (4)"));
        assertTrue(details.contains("영구 피해: +0"));
        assertTrue(details.contains("공격 속도: -0틱"));
        assertTrue(details.contains("공격 범위: +0 블록 (2)"));
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+30%</#53DFFF>", tower.runtimeDetailLines().getLast());

        UUID baseOwner = UUID.randomUUID();
        WarlockTower base = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.BASE_WARLOCK_TOWER),
                baseOwner,
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        String baseLockedDetails = String.join("\n", base.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertEquals(0.0, base.incomeDebuffResistance(), 0.0001);
        assertFalse(baseLockedDetails.contains("디버프 저항:"));
        assertTrue(baseLockedDetails.contains("각성 해금: 0/1400킬"));
        for (int kill = 0; kill < 1400; kill++) {
            WarlockAwakeningProgress.recordKill(baseOwner);
        }
        String baseUnlockedDetails = String.join("\n", base.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertTrue(baseUnlockedDetails.contains("각성 해금: 완료 · 분기 선택 필요"));

        WarlockTower melee = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        String meleeDetails = String.join("\n", melee.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertEquals(5, melee.minimumAttackIntervalTicks());
        assertEquals(0.40, melee.incomeDebuffResistance(), 0.0001);
        assertTrue(meleeDetails.contains("영구 흡수: 0기"));
        assertTrue(meleeDetails.contains("라운드 흡수: 0기"));
        assertTrue(meleeDetails.contains("각성 해금: 0/1400킬"));
        assertTrue(meleeDetails.contains("영구 체력: +0"));
        assertFalse(meleeDetails.contains("재생:"));
        assertTrue(meleeDetails.contains("생명력 흡수: 0% (1)"));
        assertTrue(meleeDetails.contains("피해 감소: +0% (10)"));
        assertTrue(meleeDetails.contains("영구 피해: +0"));
        assertTrue(meleeDetails.contains("공격 속도: -0틱 (1)"));
        assertTrue(meleeDetails.contains("공격 범위: +0 블록 (1)"));
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+40%</#53DFFF>", melee.runtimeDetailLines().getLast());
    }

    @Test
    void viewIncludesRequestedWarlockStats() {
        List<String> lines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        12,
                        7,
                        true,
                        true,
                        new WarlockStatsView.AwakeningStats(1400, 1400, true, true, 0.35, 0.40, true, 40.0, 0.0, 0.0),
                        new WarlockStatsView.CombatStats(42.5, 4, 15, 1.5, 8.0, true),
                        new WarlockStatsView.DefenseStats(75.0, 0.08, 0.08, 400, 0.10, 0.10, 0.05),
                        new WarlockStatsView.ProgressionStats(
                                true, 12, 10, 7, 4,
                                true, true, 7, 1,
                                12, 2, true
                        )
                )
        );
        assertEquals("<white>영구 흡수: <dark_purple>12기</dark_purple></white>", lines.get(0));
        assertEquals("<white>라운드 흡수: <dark_purple>7기</dark_purple></white>", lines.get(1));
        assertEquals("<white>각성 상태: <dark_purple>각성 완료</dark_purple></white>", lines.get(2));
        String details = String.join("\n", lines).replaceAll("<[^>]+>", "");
        assertTrue(details.contains("영구 흡수: 12기"));
        assertTrue(details.contains("라운드 흡수: 7기"));
        assertTrue(details.contains("각성 상태: 각성 완료"));
        assertTrue(details.contains("영구 체력: +75"));
        assertTrue(details.contains("재생: +40 HP/s"));
        assertTrue(details.contains("생명력 흡수: 8% (MAX)"));
        assertTrue(details.contains("피해 감소: +10% (MAX)"));
        assertTrue(details.contains("영구 피해: +42.5"));
        assertTrue(details.contains("공격 속도: -4틱"));
        assertTrue(details.contains("공격 범위: +1.5 블록 (14)"));
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+5%</#53DFFF>", lines.get(lines.size() - 2));
        assertEquals("<#20985d>➕ 재생</#20985d><white>: </white><#20985d>+40 HP/s</#20985d>", lines.getLast());

        List<String> awakenedMeleeLines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        12,
                        7,
                        true,
                        true,
                        new WarlockStatsView.AwakeningStats(1400, 1400, true, true, 0.35, 0.40, true, 0.0, 75.0, 0.30),
                        new WarlockStatsView.CombatStats(42.5, 4, 15, 1.5, 8.0, true),
                        new WarlockStatsView.DefenseStats(75.0, 0.08, 0.08, 400, 0.10, 0.10, 0.05),
                        new WarlockStatsView.ProgressionStats(
                                true, 7, 1, 12, 10,
                                true, false, 7, 1,
                                7, 1, true
                        )
                )
        );
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+5%</#53DFFF>", awakenedMeleeLines.get(awakenedMeleeLines.size() - 3));
        assertEquals("<#ec8d34>🪓 추가 피해</#ec8d34><white>: </white><#ec8d34>75</#ec8d34>", awakenedMeleeLines.get(awakenedMeleeLines.size() - 2));
        assertEquals("<#F1E7D4>👟 이동 속도</#F1E7D4><white>: </white><#F1E7D4>+30%</#F1E7D4>", awakenedMeleeLines.getLast());

        List<String> compressedLines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        100,
                        20,
                        false,
                        false,
                        new WarlockStatsView.AwakeningStats(0, 1400, false, true, 0.35, 0.40, true, 0.0, 0.0, 0.0),
                        new WarlockStatsView.CombatStats(247.2593, 15, 15, 8.0, 8.0, true),
                        new WarlockStatsView.DefenseStats(4395.8797, 0.0, 0.08, 400, 0.0, 0.10, 0.05),
                        new WarlockStatsView.ProgressionStats(
                                true, 100, 10, 20, 4,
                                true, true, 20, 1,
                                100, 2, true
                        )
                )
        );
        String compressedDetails = String.join("\n", compressedLines).replaceAll("<[^>]+>", "");
        assertTrue(compressedDetails.contains("영구 체력: +4395.88"));
        assertTrue(compressedDetails.contains("영구 피해: +247.26"));
    }
}
