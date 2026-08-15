package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyLeaderTower;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyPolicy;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.body.PlainMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SemionDialogBodyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dividerMarkersBecomeRenderedComponentsEvenWithoutActionButtons() {
        var bodies = SemionDialogService.actionDialogBodies("\n첫 항목\n<divider>\n둘째 항목");

        assertEquals(1, bodies.size());
        PlainMessage body = assertInstanceOf(PlainMessage.class, bodies.getFirst());
        assertFalse(body.contents().getString().contains("<divider>"));
        assertTrue(body.contents().getSiblings().size() >= 3);
    }

    @Test
    void futureAgencyLeaderUsesTopSaveButtonAndThreePolicyButtonsBelow() {
        List<TowerUpgradeOption> upgrades = List.of(
                option(FutureAgencyLeaderTower.SAVE_WORLD),
                option(FutureAgencyPolicy.AGENCY_TACTICS.upgradeId()),
                option(FutureAgencyPolicy.COMPOSITE_ARMOR.upgradeId()),
                option(FutureAgencyPolicy.REACTION_TRAINING.upgradeId()),
                option(FutureAgencyLeaderTower.PROMOTE_COMMANDER)
        );

        assertEquals(List.of(-1, 0, -1, 1, 2, 3, 4),
                SemionDialogService.futureAgencyUpgradeGrid(upgrades));
    }

    private static TowerUpgradeOption option(String id) {
        return new TowerUpgradeOption(id, id, FutureAgencyTowers.REBUILDER, 0);
    }
}
