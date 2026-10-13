package kim.biryeong.semiontd.tower.gamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GambleTowerStatsViewTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void signedStatsKeepTheSameDecimalSeparatorAcrossLocales() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("+0.0", GambleTowerStatsView.signed(0));
            assertEquals("+12.5", GambleTowerStatsView.signed(12.5));
            assertEquals("-12.5", GambleTowerStatsView.signed(-12.5));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void runtimeDetailsUseCurrentStateAndPreserveLineOrderWithoutMutatingIt() {
        GamblerTower tower = tower();
        assertEquals("도박 횟수: 0", tower.runtimeDetailLines().getFirst());
        assertEquals("최근 결과: 도박 전", tower.runtimeDetailLines().getLast());
        GambleState state = new GambleState(10, -2, 3, .25, 0, 17, Set.of(), 2, "변경 결과");
        tower.setData(GamblerTower.STATE, state);

        List<String> lines = tower.runtimeDetailLines();

        assertEquals("도박 횟수: 2", lines.get(0));
        assertEquals("최대 체력 변화: +10.0", lines.get(2));
        assertEquals("공격력 변화: -2.0", lines.get(3));
        assertEquals("마법 공격력 변화: +3.0", lines.get(4));
        assertTrue(lines.contains("보유 능력: 없음"));
        assertEquals("최근 결과: 변경 결과", lines.getLast());
        assertEquals(state, tower.state());
        assertThrows(UnsupportedOperationException.class, () -> lines.add("changed"));
    }

    @Test
    void unknownUpgradeKeepsAnEmptyTooltipAndDoesNotPlaceABet() {
        GamblerTower tower = tower();
        TowerUpgradeOption option = new TowerUpgradeOption("unrelated", "unrelated", tower.type(), 0);

        assertEquals(List.of(), tower.upgradeTooltipLines(option));
        assertEquals(GambleState.EMPTY, tower.state());
    }

    private static GamblerTower tower() {
        GridPosition position = new GridPosition(0, 80, 0);
        return new GamblerTower(GambleTowers.GAMBLER, new UUID(0, 1), TeamId.RED, 1, position, position);
    }
}
