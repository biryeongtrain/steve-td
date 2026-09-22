package kim.biryeong.semiontd.tower.animal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class AnimalAugmentsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000404");

    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();}
    @BeforeEach void balance() {TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());}

    @Test void friendshipAddsOnlyOneVirtualAnimalAndDoesNotUnlockLeaderUpgrade() {
        PlayerLane lane = lane(AnimalStackTower.FRIENDSHIP);
        PigTower pig = new PigTower(AnimalTowers.T3_PIG_TOWER, OWNER, TeamId.RED, 1, new GridPosition(0, 0, 0));
        lane.addTower(pig);
        int max = pig.maxStacks();
        for (int i = 1; i < max; i++) lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER,
                OWNER, TeamId.RED, 1, new GridPosition(i, 0, 0)));
        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, OWNER, TeamId.RED, 1, new GridPosition(20, 0, 0)));
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, OWNER, TeamId.RED, 1, new GridPosition(21, 0, 0)));
        assertEquals(max, pig.currentStacks());
        TowerUpgradeOption upgrade = new TowerUpgradeOption("test_leader", "leader", AnimalTowers.T4_PIG_LEADER_TOWER, 1);
        assertFalse(pig.meetsUpgradeRequirements(lane, upgrade));
        lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, OWNER, TeamId.RED, 1, new GridPosition(22, 0, 0)));
        assertTrue(pig.meetsUpgradeRequirements(lane, upgrade));
    }

    @Test void leaderStatsApplyOnlyToActualLeader() {
        PlayerLane lane = lane(AnimalStackTower.LEADER);
        RabbitTower leader = new RabbitTower(AnimalTowers.T4_RABBIT_LEADER_TOWER, OWNER, TeamId.RED, 1, new GridPosition(0, 0, 0));
        lane.addTower(leader);
        assertEquals(leader.maxHealth() * 2, leader.currentMaxHealth(), 1e-6);
        assertEquals(160, leader.modifyAttackDamage(null, null, 100), 1e-6);
        WolfTower normal = new WolfTower(AnimalTowers.T1_WOLF_TOWER, OWNER, TeamId.RED, 1, new GridPosition(30, 0, 0));
        lane.addTower(normal);
        assertEquals(normal.maxHealth(), normal.currentMaxHealth(), 1e-6);
        assertEquals(100, normal.modifyAttackDamage(null, null, 100), 1e-6);
    }

    @Test void threeLeaderKindsShareDoubleAurasAcrossSpeciesAndDistanceUntilOneDies() {
        PlayerLane lane = lane(AnimalStackTower.UNION);
        PigTower pig = new PigTower(AnimalTowers.T4_PIG_LEADER_TOWER, OWNER, TeamId.RED, 1, new GridPosition(0, 0, 0));
        WolfTower wolf = new WolfTower(AnimalTowers.T4_WOLF_LEADER_TOWER, OWNER, TeamId.RED, 1, new GridPosition(100, 0, 0));
        RabbitTower rabbit = new RabbitTower(AnimalTowers.T4_RABBIT_LEADER_TOWER, OWNER, TeamId.RED, 1, new GridPosition(200, 0, 0));
        FoxTower fox = new FoxTower(AnimalTowers.T1_FOX_TOWER, OWNER, TeamId.RED, 1, new GridPosition(300, 0, 0));
        for (AnimalStackTower tower : List.of(pig, wolf, rabbit, fox)) lane.addTower(tower);
        assertEquals(fox.maxHealth() * 1.3, fox.currentMaxHealth(), 1e-6);
        assertEquals(116, fox.modifyAttackDamage(null, null, 100), 1e-6);
        assertEquals(18, fox.adjustAttackInterval(20));
        assertEquals(7, fox.adjustAttackRange(5), 1e-6);
        assertEquals(90, fox.modifyIncomingDamage(null, null, 100), 1e-6);
        assertEquals(pig.maxHealth() * 1.3, pig.currentMaxHealth(), 1e-6);
        rabbit.syncHealth(0);
        AnimalStackTower.refreshAnimalStacks(lane);
        assertEquals(fox.maxHealth(), fox.currentMaxHealth(), 1e-6);
        assertEquals(20, fox.adjustAttackInterval(20));
        assertEquals(5, fox.adjustAttackRange(5), 1e-6);
    }

    private static PlayerLane lane(String card) {
        var layout = new LaneRegionLayout(1, Vec3.ZERO, List.of(new Vec3(20, 0, 0)), new Vec3(20, 0, 20),
                BlockBounds.of(new BlockPos(-10, -2, -10), new BlockPos(400, 5, 40)), List.of(new GridPosition(30, 0, 30)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
        return lane;
    }
}
