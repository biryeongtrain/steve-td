package kim.biryeong.semiontd.tower.army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ArmyTowerStatsViewTest {
    private static final UUID OWNER = new UUID(0, 8101);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ArmyStates.clear(OWNER);
    }

    @Test
    void recruitShowsRankAndProgressInTheirOriginalOrderWithoutMutatingService() {
        var tower = new ArmyTower(ArmyTowers.RECRUIT, OWNER, TeamId.RED, 1, new GridPosition(0, 0, 0));
        var lines = tower.runtimeDetailLines();
        assertEquals("계급 이등병 (짬 0)", lines.getFirst());
        assertEquals("공격력 100% · 후임 버프 +0%", lines.get(1));
        assertTrue(lines.get(2).startsWith("다음 진급까지 "));
        assertEquals(lines, tower.runtimeDetailLines());
        assertEquals(0, tower.service());
    }

    @Test
    void supportTowerKeepsItsServiceMessageWithoutCombatRankDetails() {
        var tower = new ArmyTower(ArmyTowers.DRILL_SERGEANT, OWNER, TeamId.RED, 1, new GridPosition(0, 0, 0));
        var lines = tower.runtimeDetailLines();
        assertEquals("계급 없음 · 짬의 영향을 받지 않습니다", lines.getFirst());
        assertTrue(lines.get(1).startsWith("주변 아군 짬 +"));
        assertEquals(2, lines.size());
    }
}
