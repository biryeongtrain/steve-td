package kim.biryeong.semiontd.tower.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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

final class PlantTowerStatsViewTest {
    private static final UUID OWNER = new UUID(0, 8103);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlantSoilStates.clear(OWNER);
    }

    @Test
    void soillessPlantKeepsTheSingleNoEffectMessage() {
        var tower = new PlantCombatTower(PlantTowers.T1_MEADOW_TOWER, OWNER, TeamId.RED, 1, new GridPosition(0, 0, 0));
        assertEquals(List.of("맨땅 위라 지형 효과가 없습니다."), tower.runtimeDetailLines());
    }

    @Test
    void meadowViewReflectsGrowthWithoutAdvancingIt() {
        var tower = new PlantCombatTower(PlantTowers.T1_MEADOW_TOWER, OWNER, TeamId.RED, 1, new GridPosition(0, 0, 0)) {
            @Override
            PlantSoil standingSoil() {
                return PlantSoil.MEADOW;
            }
        };
        tower.addGrowthRounds(2, null);
        var lines = tower.runtimeDetailLines();
        assertEquals("잔디 위 · 지형 0칸", lines.getFirst());
        assertEquals("개화 피해 +0%", lines.get(1));
        assertTrue(lines.get(2).startsWith("성장 최대 체력 +"));
        assertTrue(lines.get(2).endsWith(" · 2라운드째"));
        assertEquals(lines, tower.runtimeDetailLines());
        assertEquals(2, tower.growthRounds());
    }
}
