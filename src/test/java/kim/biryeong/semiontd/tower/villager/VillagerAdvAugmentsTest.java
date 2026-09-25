package kim.biryeong.semiontd.tower.villager;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.*;
import kim.biryeong.semiontd.job.VillagerAdvTowerJob;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class VillagerAdvAugmentsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000402");
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();}
    @BeforeEach void balance() {TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());}

    @Test void earlyGraduationDoublesOnlyTierAwardAndBypassesExperienceAtHigherCost() {
        Tower tower = tower(VillagerTowers.ADV_T1_SPLASH_TOWER, 0);
        var player = new SemionPlayer(OWNER, "adv", TeamId.RED, 1, new PlayerEconomy(EconomyConfig.defaultConfig()));
        player.assignJob(new VillagerAdvTowerJob());
        var option = new TowerUpgradeOption("villager_splash_t2", "upgrade", VillagerTowers.ADV_T2_LIBRARIAN_TOWER, 100);
        assertFalse(VillagerAdvStates.canUpgrade(player, tower, option));
        tower.syncAugments(snapshot(VillagerAdvAugments.EARLY), null);
        assertTrue(VillagerAdvStates.canUpgrade(player, tower, option));
        assertEquals(140, VillagerAdvAugments.upgradeCost(tower, 100));
        var config = TowerBalanceRuntime.villagerAdv();
        assertEquals(config.resolvedExperiencePerTower() + config.resolvedExperiencePerTier() * 2,
                VillagerAdvStates.normalExperienceGain(tower, config), 1e-6);
        tower.syncAugments(AugmentSnapshot.none(), null);
        assertEquals(100, VillagerAdvAugments.upgradeCost(tower, 100));
    }

    @Test void mentorCopiesOnlyNormalAwardToOneLowestExperienceTower() {
        Tower low = tower(VillagerTowers.ADV_T1_SPLASH_TOWER, 0);
        Tower equalLow = tower(VillagerTowers.ADV_T1_CAT_TOWER, 1);
        Tower high = tower(VillagerTowers.ADV_T3_CLERIC_TOWER, 2);
        var snapshots = List.of(
                new VillagerAdvStates.ExperienceGainSnapshot(OWNER, null, low, 1, 0, 4, .5),
                new VillagerAdvStates.ExperienceGainSnapshot(OWNER, null, equalLow, 1, 0, 4, .5),
                new VillagerAdvStates.ExperienceGainSnapshot(OWNER, null, high, 3, 30, 8, .5));
        var results = VillagerAdvStates.calculateExperienceGains(snapshots, TowerBalanceRuntime.villagerAdv());
        assertEquals(8, results.get(0).nextExperience(), 1e-6);
        assertEquals(4, results.get(1).nextExperience(), 1e-6);
        assertEquals(38, results.get(2).nextExperience(), 1e-6);
    }

    @Test void roleContestKeepsAllBranchesInFourStarterFamilies() {
        assertEquals(VillagerAdvAugments.role(tower(VillagerTowers.ADV_T1_CAT_TOWER, 0)),
                VillagerAdvAugments.role(tower(VillagerTowers.ADV_T3_LANE_CLEAR_CAT_TOWER, 1)));
        assertEquals(VillagerAdvAugments.role(tower(VillagerTowers.ADV_T1_ALLAY_TOWER, 0)),
                VillagerAdvAugments.role(tower(VillagerTowers.ADV_T3_WEAPON_SMITH_TOWER, 1)));
        assertEquals(4, List.of(VillagerTowers.ADV_T1_SPLASH_TOWER, VillagerTowers.ADV_T1_GOLEM_TOWER,
                VillagerTowers.ADV_T1_ALLAY_TOWER, VillagerTowers.ADV_T1_CAT_TOWER).stream()
                .map(type -> VillagerAdvAugments.role(tower(type, 0))).distinct().count());
    }

    @Test void graduateChoosesHighestExperienceAttackerAndRangeClearsAfterWave() {
        var layout = new LaneRegionLayout(1, Vec3.ZERO, List.of(new Vec3(40, 0, 0)), new Vec3(40, 0, 40),
                BlockBounds.of(new BlockPos(-10, -2, -10), new BlockPos(40, 5, 40)), List.of(new GridPosition(30, 0, 30)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
        lane.assignAugmentSnapshot(snapshot(VillagerAdvAugments.GRADUATE));
        Tower low = tower(VillagerTowers.ADV_T1_SPLASH_TOWER, 0);
        Tower high = tower(VillagerTowers.ADV_T2_LIBRARIAN_TOWER, 1);
        Tower support = tower(VillagerTowers.ADV_T1_ALLAY_TOWER, 2);
        for (Tower tower : List.of(low, high, support)) lane.addTower(tower);
        high.setData(VillagerAdvStates.EXPERIENCE, 20.0);
        support.setData(VillagerAdvStates.EXPERIENCE, 50.0);
        VillagerAdvAugments.startWave(lane);
        VillagerAdvAugments.captureGraduate(lane);
        assertTrue(VillagerAdvAugments.isGraduate(high));
        assertFalse(VillagerAdvAugments.isGraduate(support));
        assertTrue(VillagerAdvAugments.attackRange(high, 8) > 70);
        assertEquals(8, VillagerAdvAugments.attackRange(low, 8));
        Tower upgraded = tower(VillagerTowers.ADV_T3_CLERIC_TOWER, 1);
        upgraded.copyFrom(high, 200);
        upgraded.syncAugments(snapshot(VillagerAdvAugments.GRADUATE), null);
        assertTrue(VillagerAdvAugments.isGraduate(upgraded));
        VillagerAdvAugments.resetWave(lane);
        assertEquals(8, VillagerAdvAugments.attackRange(high, 8));
    }

    private static Tower tower(TowerType type, int x) {
        return new Tower(type, OWNER, TeamId.RED, 1, new GridPosition(x, 0, 0)) {
            @Override protected boolean execute(PlayerLane lane) {return false;}
        };
    }

    private static AugmentSnapshot snapshot(String card) {
        return new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())));
    }
}
