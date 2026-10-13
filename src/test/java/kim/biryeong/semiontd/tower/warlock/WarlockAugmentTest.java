package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class WarlockAugmentTest {
    @BeforeAll
    static void bootstrap() {SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();}

    @Test
    void explosiveSacrificeRetiresGrowthBonusWithoutChangingExplosionOrRejectingOldConfig() {
        var definition = kim.biryeong.semiontd.augment.AugmentCatalog.find(WarlockAugments.EXPLOSIVE).orElseThrow();
        for (double legacyBonus : new double[]{.25, 9}) {
            var json = com.google.gson.JsonParser.parseString("""
                    {"parameters":{"semiontd:job_warlock_towers_g1":{
                        "growthBonus":%s,"radius":4.5,"maxTargets":7,"healthRatio":0.7}}}
                    """.formatted(legacyBonus)).getAsJsonObject();
            AugmentConfig configured = AugmentConfig.fromJson(json);
            assertFalse(configured.parametersFor(WarlockAugments.EXPLOSIVE).containsKey("growthBonus"));
            assertEquals(4.5, configured.parameter(WarlockAugments.EXPLOSIVE, "radius", -1));
            assertEquals(7, configured.parameter(WarlockAugments.EXPLOSIVE, "maxTargets", -1));
            assertEquals(0.7, configured.parameter(WarlockAugments.EXPLOSIVE, "healthRatio", -1));
            String description = kim.biryeong.semiontd.augment.AugmentDescriptions.describe(definition, configured);
            assertFalse(description.contains("growthBonus"));
            assertFalse(description.contains("영구"));
            assertTrue(description.contains("4.5"));
            assertTrue(description.contains("70%"));
        }
        assertEquals(java.util.Map.of("radius", 3.0, "maxTargets", 12.0, "healthRatio", 1.0),
                AugmentConfig.defaults().parametersFor(WarlockAugments.EXPLOSIVE));
    }

    @Test
    void partnershipSharesGrowthWithoutSacrificeCountersOrAttackSpeed() {
        WarlockState state = new WarlockState();
        state.shareGrowth(new WarlockSacrifice.Gain(100, 20, 40, 8, 3, 10));
        assertEquals(100, state.permanentHealthBonus());
        assertEquals(20, state.permanentDamageBonus());
        assertEquals(40, state.roundHealthBonus());
        assertEquals(8, state.roundDamageBonus());
        assertEquals(0, state.totalSacrificeCount());
        assertEquals(0, state.roundSacrificeCount());
        assertEquals(0, state.roundIntervalReduction());
    }

    @Test
    void partnershipSecondCoreCostsSevenHundredAndDoesNotPreventAwakening() {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(owner);
        lane.assignAugmentSnapshot(snapshot(WarlockAugments.PARTNERSHIP));
        assertEquals(37, WarlockTower.placementCost(lane, WarlockTowers.BASE_WARLOCK_TOWER, 37));
        WarlockTower first = new WarlockTower(WarlockTowers.RANGED_WARLOCK_TOWER, owner, TeamId.RED, 1, new GridPosition(0, 0, 0));
        WarlockTower second = new WarlockTower(WarlockTowers.MELEE_WARLOCK_TOWER, owner, TeamId.RED, 1, new GridPosition(1, 0, 0));
        lane.addTower(first);
        lane.addTower(second);
        assertEquals(700, WarlockTower.placementCost(lane, WarlockTowers.BASE_WARLOCK_TOWER, 0));
        assertEquals(37, WarlockTower.placementCost(lane, WarlockTowers.T1_SLAVE, 37));
        assertTrue(first.isLastSurvivingTower(lane));
        lane.addTower(new WarlockSacrificeTower(WarlockTowers.T1_SLAVE, owner, TeamId.RED, 1, new GridPosition(2, 0, 0)));
        assertFalse(first.isLastSurvivingTower(lane));
    }

    @Test
    void trueAwakeningUsesSixtyPercentThresholdAndImmediateUnlockDetails() {
        WarlockTower tower = new WarlockTower(WarlockTowers.RANGED_WARLOCK_TOWER, UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 0, 0));
        tower.syncAugments(snapshot(WarlockAugments.AWAKENING), null);
        assertEquals(.60, tower.awakeningHealthThreshold());
        assertEquals(0, tower.finalDamageBonus());
        assertFalse(tower.awakenedThisRound());
    }

    private static AugmentSnapshot snapshot(String id) {
        return new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, id, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())));
    }

    private static PlayerLane lane(UUID owner) {
        LaneRegionLayout layout = new LaneRegionLayout(1, new Vec3(.5, 1, .5),
                List.of(new Vec3(.5, 1, 2.5)), new Vec3(.5, 1, 10.5),
                BlockBounds.of(new BlockPos(0, 0, 0), new BlockPos(64, 6, 10)), List.of(new GridPosition(0, 0, 10)));
        return new PlayerLane(TeamId.RED, 1, owner, null, layout);
    }
}
