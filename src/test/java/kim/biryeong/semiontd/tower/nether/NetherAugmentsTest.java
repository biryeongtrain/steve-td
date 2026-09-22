package kim.biryeong.semiontd.tower.nether;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NetherAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void bloodChargesPreserveOverflowUseOnePerBasicAttackAndReset() {
        NetherTower tower = tower(NetherTowers.T1_STRIDER);
        select(tower, NetherTower.BLOODLETTING);
        double threshold = tower.currentMaxHealth() * .2;
        tower.recordNaturalHealthLoss(threshold * 2.5);
        assertEquals(threshold, tower.consumeBloodCharge(), .001);
        AugmentCombat.runWithoutTriggers(() -> {
            assertEquals(0, tower.consumeBloodCharge());
            tower.recordNaturalHealthLoss(threshold * 5);
        });
        assertEquals(threshold, tower.consumeBloodCharge(), .001);
        assertEquals(0, tower.consumeBloodCharge());
        tower.recordNaturalHealthLoss(threshold * .5);
        assertEquals(threshold, tower.consumeBloodCharge(), .001);
        tower.recordNaturalHealthLoss(threshold * 3);
        tower.resetForRound(null);
        assertEquals(0, tower.consumeBloodCharge());
    }

    @Test
    void upgradeCopiesChargesIndependentlyAndNonNaturalDamageDoesNotCharge() {
        NetherTower original = tower(NetherTowers.T1_STRIDER);
        select(original, NetherTower.BLOODLETTING);
        original.onDamaged(null, null, 10, original.health(), original.health() - 10);
        assertEquals(0, original.consumeBloodCharge());
        double threshold = original.currentMaxHealth() * .2;
        original.recordNaturalHealthLoss(threshold);
        NetherTower upgraded = tower(NetherTowers.T2_PIGLIN);
        upgraded.copyFrom(original, 100);
        assertEquals(threshold, upgraded.consumeBloodCharge(), .001);
        assertEquals(threshold, original.consumeBloodCharge(), .001);
    }

    @Test
    void totemRevivesAfterDeathAtTwentyTicksOnceAndOnlyAsZombie() {
        NetherTower tower = tower(NetherTowers.T1_STRIDER);
        select(tower, NetherTower.TOTEM);
        tower.onDeath(null);
        for (int i = 0; i < 20; i++) {tower.tick(null);}
        assertEquals(100, tower.modifyOutgoingDamage(null, null, 100));
        tower.onDamaged(null, null, 1000, 10, 0);
        assertEquals(NetherTowerState.ZOMBIE, tower.state());
        tower.syncHealth(0);
        tower.onDeath(null);
        for (int i = 0; i < 19; i++) {tower.tick(null);}
        assertEquals(0, tower.health());
        tower.tick(null);
        assertEquals(tower.currentMaxHealth() * .5, tower.health(), .001);
        assertEquals(200, tower.modifyOutgoingDamage(null, null, 100));
        tower.syncHealth(0);
        tower.onDeath(null);
        for (int i = 0; i < 20; i++) {tower.tick(null);}
        assertEquals(0, tower.health());
        tower.resetForRound(null);
        assertEquals(100, tower.modifyOutgoingDamage(null, null, 100));
        assertEquals(NetherTowerState.NETHER, tower.state());
    }

    @Test
    void secondPhasePreventsEveryDamagePathFromDroppingBelowOneUntilSixtyTicks() {
        NetherTower tower = tower(NetherTowers.T1_STRIDER);
        select(tower, NetherTower.SECOND_PHASE);
        tower.onDamaged(null, null, 1000, 10, 0);
        tower.syncHealth(10);
        assertEquals(9, tower.modifyIncomingDamage(null, null, 100));
        assertEquals(9, tower.modifyIncomingDamageIgnoringReductions(null, null, 100));
        for (int i = 0; i < 60; i++) {tower.tick(null);}
        assertEquals(100, tower.modifyIncomingDamage(null, null, 100));
    }

    private static NetherTower tower(TowerType type) {
        return new NetherTower(type, UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 64, 0));
    }

    private static void select(NetherTower tower, String card) {
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))), null);
    }
}
