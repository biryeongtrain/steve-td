package kim.biryeong.semiontd.trait;

import xyz.nucleoid.map_templates.BlockBounds;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TraitBalanceConfig;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraitRoundTowerIndexTest {
    private static final UUID OWNER = UUID.fromString("b3cb88dc-6d7c-4b7a-bdb1-ecbba317031e");
    private static final TraitLoadout LOADOUT = new TraitLoadout(BuiltInTraits.STRENGTH_IN_NUMBERS_ID, BuiltInTraits.DIVERSITY_ID);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreConfig() {
        TraitBalanceRuntime.apply(TraitBalanceConfig.defaultConfig());
    }

    @Test
    void snapshotMatchesOriginalOwnerTypeAndAliveFiltersIncludingNonRecipients() {
        var lane = new PlayerLane(TeamId.RED, 1, OWNER, null, testLayout(1));
        var primary = tower(TestTowerTypes.TEST_DIRECT, OWNER);
        var copy = tower(TestTowerTypes.TEST_DIRECT, OWNER);
        copy.markTemporaryCopy(UUID.randomUUID());
        var otherType = tower(TestTowerTypes.TEST_SNIPER, OWNER);
        var dead = tower(TestTowerTypes.TEST_BASTION, OWNER);
        dead.syncHealth(0);
        var otherOwner = tower(TestTowerTypes.TEST_BASTION, UUID.randomUUID());
        for (Tower tower : List.of(primary, copy, otherType, dead, otherOwner)) {lane.addTower(tower);}
        assertFalse(copy.receivesTraitEffects());
        TraitRoundTowerIndex index = TraitRoundTowerIndex.capture(lane.towers());
        for (Tower tower : lane.towers()) {
            assertEquals(TraitEffects.sameTypeDamageBonus(LOADOUT, lane, tower), index.sameTypeDamageBonus(LOADOUT, tower));
            assertEquals(TraitEffects.diversityDamageBonus(LOADOUT, lane, tower), index.diversityDamageBonus(LOADOUT, tower));
        }
        assertEquals(2 * TraitBalanceRuntime.value(BuiltInTraits.STRENGTH_IN_NUMBERS_ID, "damageBonusPerTower"),
                index.sameTypeDamageBonus(LOADOUT, primary));
        assertEquals(0, index.sameTypeDamageBonus(LOADOUT, null));
        assertEquals(0, index.diversityDamageBonus(LOADOUT, null));
    }

    @Test
    void nextCaptureReflectsHealthAndMembershipChangesWhileExistingSnapshotStaysFixed() {
        var first = tower(TestTowerTypes.TEST_DIRECT, OWNER);
        var second = tower(TestTowerTypes.TEST_DIRECT, OWNER);
        List<Tower> towers = new ArrayList<>(List.of(first, second));
        TraitRoundTowerIndex before = TraitRoundTowerIndex.capture(towers);
        double oldBonus = before.sameTypeDamageBonus(LOADOUT, first);
        second.syncHealth(0);
        towers.remove(first);
        assertEquals(oldBonus, before.sameTypeDamageBonus(LOADOUT, first));
        TraitRoundTowerIndex after = TraitRoundTowerIndex.capture(towers);
        assertEquals(0, after.sameTypeDamageBonus(LOADOUT, first));
        assertEquals(0, after.diversityDamageBonus(LOADOUT, first));
    }

    @Test
    void currentConfigurationAndOriginalMultiplicationOrderRemainAuthoritative() {
        var tower = tower(TestTowerTypes.TEST_DIRECT, OWNER);
        var index = TraitRoundTowerIndex.capture(List.of(tower, tower, tower));
        TraitBalanceRuntime.apply(new TraitBalanceConfig(Map.of(
                "strength_in_numbers", Map.of("damageBonusPerTower", .137),
                "diversity", Map.of("damageBonusPerType", .071))));
        var duplicateSlots = new TraitLoadout(BuiltInTraits.STRENGTH_IN_NUMBERS_ID, BuiltInTraits.STRENGTH_IN_NUMBERS_ID);
        assertEquals(3L * .137 * TraitEffects.effectScale(duplicateSlots, BuiltInTraits.STRENGTH_IN_NUMBERS_ID),
                index.sameTypeDamageBonus(duplicateSlots, tower));
        assertEquals(1L * .071 * TraitEffects.effectScale(LOADOUT, BuiltInTraits.DIVERSITY_ID),
                index.diversityDamageBonus(LOADOUT, tower));
        assertEquals(0, index.sameTypeDamageBonus(TraitLoadout.none(), tower));
    }

    @Test
    void oneCaptureReadsEachHealthOnceAndRepeatedQueriesDoNotRescan() {
        List<CountingTower> towers = new ArrayList<>();
        for (int i = 0; i < 128; i++) {towers.add(new CountingTower(i % 2 == 0 ? TestTowerTypes.TEST_DIRECT : TestTowerTypes.TEST_SNIPER));}
        towers.forEach(tower -> tower.healthReads = 0);
        var index = TraitRoundTowerIndex.capture(towers);
        for (int query = 0; query < 10; query++) {
            for (Tower tower : towers) {
                index.sameTypeDamageBonus(LOADOUT, tower);
                index.diversityDamageBonus(LOADOUT, tower);
            }
        }
        assertEquals(128, towers.stream().mapToInt(tower -> tower.healthReads).sum());
    }

    private static TestTower tower(TowerType type, UUID owner) {
        return new TestTower(type, owner, TeamId.RED, 1, new GridPosition(0, 64, 0));
    }

    private static final class CountingTower extends TestTower {
        private int healthReads;

        private CountingTower(TowerType type) {
            super(type, OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0));
        }

        @Override
        public double health() {
            healthReads++;
            return super.health();
        }
    }

    private static LaneRegionLayout testLayout(int laneId) {
        return new LaneRegionLayout(laneId, new Vec3(0.5, 64, 0.5),
                List.of(new Vec3(0.5, 64, 2.5)), new Vec3(0.5, 64, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(1024, 66, 10)),
                List.of(new GridPosition(0, 63, 10)));
    }
}
