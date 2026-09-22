package kim.biryeong.semiontd.tower.body;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BodyAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        kim.biryeong.semiontd.config.TowerBalanceRuntime.apply(kim.biryeong.semiontd.config.TowerBalanceConfig.defaultConfig());
    }

    @Test
    void adrenalineHealthAppliesOnlyToHeartsAndPreservesHealthRatio() {
        BodyTower heart = tower(BodyTowers.HEART_T1);
        BodyTower skin = tower(BodyTowers.SKIN_T1);
        heart.syncHealth(heart.currentMaxHealth() / 2.0);
        double base = heart.currentMaxHealth();
        heart.syncAugments(snapshot("job_body_g2"), null);
        skin.syncAugments(snapshot("job_body_g2"), null);
        assertEquals(base * 1.8, heart.currentMaxHealth(), 1.0e-6);
        assertEquals(heart.currentMaxHealth() / 2.0, heart.health(), 1.0e-6);
        assertEquals(skin.type().maxHealth(), skin.currentMaxHealth(), 1.0e-6);
    }

    @Test
    void skinAccumulatesLossWithoutBankingWholeThresholdsAndResetsAtWaveStart() {
        BodyTower skin = tower(BodyTowers.SKIN_T1);
        skin.syncAugments(snapshot("job_body_p"), null);
        double threshold = skin.currentMaxHealth() * 0.15;
        assertFalse(skin.accumulateSkinLoss(threshold * 0.5));
        assertTrue(skin.accumulateSkinLoss(threshold * 0.5));
        assertTrue(skin.accumulateSkinLoss(threshold * 3.25));
        assertFalse(skin.accumulateSkinLoss(threshold * 0.25));
        skin.onWaveStarted(null, 2);
        assertFalse(skin.accumulateSkinLoss(threshold * 0.5));
    }

    @Test
    void skinCooldownIsPerHeartAndDoesNotRunBetweenWaves() {
        BodyTower first = tower(BodyTowers.HEART_T1);
        BodyTower second = tower(BodyTowers.HEART_T1);
        assertFalse(first.acceptSkinHeartbeat(0));
        first.onWaveStarted(null, 1);
        second.onWaveStarted(null, 1);
        assertTrue(first.acceptSkinHeartbeat(0));
        assertFalse(first.acceptSkinHeartbeat(19));
        assertTrue(second.acceptSkinHeartbeat(19));
        assertTrue(first.acceptSkinHeartbeat(20));
        first.onWaveStarted(null, 2);
        assertTrue(first.acceptSkinHeartbeat(21));
    }

    private static BodyTower tower(TowerType type) {
        GridPosition position = new GridPosition(0, 64, 0);
        return new BodyTower(type, UUID.randomUUID(), TeamId.RED, 1, position, position);
    }

    private static AugmentSnapshot snapshot(String id) {
        return new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, id, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())));
    }
}
