package kim.biryeong.semiontd.tower.resonance;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ResonanceTowerLinkControllerTest {
    private static final UUID OWNER = new UUID(0, 1);

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
    void friendSelectionPrioritizesLinksBeforeDistanceAndThenLogicalId() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CRYSTAL, OWNER, TeamId.RED, 1, 0);
        ResonanceTower near = tower(ResonanceTowers.WAVE_CRYSTAL, OWNER, TeamId.RED, 1, 4);
        ResonanceTower far = tower(ResonanceTowers.WAVE_CRYSTAL, OWNER, TeamId.RED, 1, 10);
        far.updateResonanceState(2, 2);
        assertSame(far, ResonanceTowerLinkController.selectFriend(focus, List.of(near, far), List.of()));

        near.updateResonanceState(2, 2);
        assertSame(near, ResonanceTowerLinkController.selectFriend(focus, List.of(far, near), List.of()));

        ResonanceTower tied = tower(ResonanceTowers.FROST_CRYSTAL, OWNER, TeamId.RED, 1, -4);
        tied.updateResonanceState(2, 2);
        ResonanceTower expected = Comparator.comparing(Tower::logicalId).compare(near, tied) <= 0 ? near : tied;
        assertSame(expected, ResonanceTowerLinkController.selectFriend(focus, List.of(tied, near), List.of()));
        assertSame(expected, ResonanceTowerLinkController.selectFriend(focus, List.of(near, tied), List.of()));
    }

    @Test
    void friendSelectionRejectsExistingLinksAndDifferentOwnerTeamLaneOrSameAspect() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CRYSTAL, OWNER, TeamId.RED, 1, 0);
        ResonanceTower linked = tower(ResonanceTowers.WAVE_CRYSTAL, OWNER, TeamId.RED, 1, 1);
        List<ResonanceTower> excluded = List.of(
                focus, linked,
                tower(ResonanceTowers.FOCUS_PRISM, OWNER, TeamId.RED, 1, 5),
                tower(ResonanceTowers.WAVE_CRYSTAL, new UUID(0, 2), TeamId.RED, 1, 5),
                tower(ResonanceTowers.WAVE_CRYSTAL, OWNER, TeamId.BLUE, 1, 5),
                tower(ResonanceTowers.WAVE_CRYSTAL, OWNER, TeamId.RED, 2, 5)
        );

        assertNull(ResonanceTowerLinkController.selectFriend(focus, excluded, List.of(linked)));
        assertNull(ResonanceTowerLinkController.selectFriend(focus, List.of(), List.of()));
    }

    private static ResonanceTower tower(TowerType type, UUID owner, TeamId team, int lane, int x) {
        GridPosition position = new GridPosition(x, 0, 0);
        return new ResonanceTower(type, owner, team, lane, position, position);
    }
}
