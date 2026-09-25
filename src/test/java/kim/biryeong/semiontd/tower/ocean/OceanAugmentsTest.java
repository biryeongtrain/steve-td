package kim.biryeong.semiontd.tower.ocean;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OceanAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void balance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void highTideScalesSupplyAndSoftCapAndFloorsOnlyWaterUsers() {
        OceanTower fish = fish("g1");
        fish.onWaveStarted(null, 1);
        assertEquals(150, fish.water());
        assertEquals(1500, fish.waterSoftCap());
        fish.addWater(2000);
        fish.onWaveStarted(null, 2);
        assertEquals(2150, fish.water());
        OceanWaterTower source = new OceanWaterTower(OceanTowers.T1_WATER, UUID.randomUUID(), TeamId.RED, 1,
                new GridPosition(0, 64, 0));
        source.syncAugments(snapshot("g1"), null);
        assertEquals(1.5, source.supplyMultiplier());
        assertEquals(2500, TowerBalanceRuntime.ability(OceanTower.CONFIG_ID, "waterSupplyStopThreshold"));
    }

    @Test
    void recyclePreservesPreAttenuationAllocationAndNeverSuppliesCappedRecipients() {
        OceanTower full = fish("g1");
        OceanTower low = fish("g1");
        OceanTower medium = fish("g1");
        full.addWater(1900);
        medium.addWater(400);
        Map<OceanTower, Double> allocations = OceanWaterTower.supplyAllocations(List.of(full, low, medium), 15, true);
        assertFalse(allocations.containsKey(full));
        assertEquals(30, allocations.get(low), .0001);
        assertEquals(15, allocations.get(medium), .0001);
        assertEquals(45, allocations.values().stream().mapToDouble(Double::doubleValue).sum());
        assertEquals(3, OceanWaterTower.supplyAllocations(List.of(full, low, medium), 15, false).size());
        assertTrue(OceanWaterTower.supplyAllocations(List.of(full), 15, true).isEmpty());
    }

    @Test
    void currentCountsActualSpendAccumulatesAndConsumesOnePerBasicAttack() {
        OceanTower fish = fish("g2");
        fish.onWaveStarted(null, 1);
        assertFalse(fish.spendWater(101));
        assertEquals(0, fish.currentCharges());
        assertTrue(fish.spendWater(95));
        assertEquals(3, fish.currentCharges());
        assertTrue(fish.consumeCurrentCharge());
        assertEquals(2, fish.currentCharges());
        assertTrue(fish.spendWater(5));
        fish.addWater(100);
        AugmentCombat.runWithoutTriggers(() -> {
            assertFalse(fish.consumeCurrentCharge());
            fish.spendWater(60);
        });
        assertEquals(2, fish.currentCharges());
        OceanTower upgrade = fish("g2");
        upgrade.copyFrom(fish, 10);
        assertEquals(2, upgrade.currentCharges());
        fish.resetForRound(null);
        assertEquals(0, fish.currentCharges());
        fish.onWaveStarted(null, 2);
        fish.spendWater(20);
        assertEquals(0, fish.currentCharges());
    }

    @Test
    void tideStartsAtTwelveSecondsMaintainsFourSecondsAndPreservesWaterAboveCap() {
        OceanTower fish = fish("g1", "p", "g2");
        fish.onWaveStarted(null, 1);
        for (int tick = 0; tick < 239; tick++) fish.tickTide();
        assertFalse(fish.tideActive());
        assertEquals(150, fish.water());
        fish.tickTide();
        assertTrue(fish.tideActive());
        assertEquals(1500, fish.water());
        assertTrue(fish.spendWater(120));
        assertEquals(1500, fish.water());
        assertEquals(4, fish.currentCharges());
        fish.addWater(500);
        fish.tickTide();
        assertEquals(2000, fish.water());
        for (int tick = 241; tick < 320; tick++) fish.tickTide();
        assertFalse(fish.tideActive());
        fish.spendWater(1500);
        assertEquals(500, fish.water());
        for (int tick = 320; tick < 480; tick++) fish.tickTide();
        assertEquals(1500, fish.water());
        fish.resetForRound(null);
        assertFalse(fish.tideActive());
    }

    private static OceanTower fish(String... cards) {
        OceanTower tower = new OceanTower(OceanTowers.T1_COD, UUID.randomUUID(), TeamId.RED, 1,
                new GridPosition(0, 64, 0));
        tower.syncAugments(snapshot(cards), null);
        return tower;
    }

    private static AugmentSnapshot snapshot(String... cards) {
        return new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards).map(suffix ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, "semiontd:job_ocean_" + suffix,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }
}
