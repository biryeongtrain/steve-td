package kim.biryeong.semiontd.tower.army;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class ArmyAugmentsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void clear() {
        ArmyStates.clearAll();
    }

    @Test
    void thirdWaveKillPromotesOnlyOnceAndExtraAttacksCannotChargeIt() {
        ArmyTower tower = tower(ArmyTowers.RECRUIT, 0, "job_army_s");
        tower.onWaveStarted(null, 1);
        AugmentCombat.runWithoutTriggers(() -> tower.recordPromotionKill(null));
        tower.recordPromotionKill(null);
        tower.recordPromotionKill(null);
        assertEquals(ArmyRank.PRIVATE, tower.rank());
        tower.recordPromotionKill(null);
        assertEquals(ArmyRank.CORPORAL, tower.rank());
        for (int i = 0; i < 9; i++) tower.recordPromotionKill(null);
        assertEquals(ArmyRank.CORPORAL, tower.rank());
        ArmyTower upgraded = tower(ArmyTowers.SPECIALIST, 0);
        upgraded.copyFrom(tower, 0);
        upgraded.recordPromotionKill(null);
        assertEquals(ArmyRank.CORPORAL, upgraded.rank());
        upgraded.onWaveStarted(null, 2);
        for (int i = 0; i < 3; i++) upgraded.recordPromotionKill(null);
        assertEquals(ArmyRank.SERGEANT, upgraded.rank());
    }

    @Test
    void veteransAggregateOnlyNearbySameTypeLowerRankAttacks() {
        ArmyTower senior = tower(ArmyTowers.RECRUIT, 0, "job_army_g1");
        for (int i = 0; i < 3; i++) senior.promoteOneRank(null);
        ArmyTower first = tower(ArmyTowers.RECRUIT, 1);
        ArmyTower second = tower(ArmyTowers.RECRUIT, 2);
        assertFalse(senior.acceptVeteranAttack(tower(ArmyTowers.SPECIALIST, 1), null));
        assertFalse(senior.acceptVeteranAttack(tower(ArmyTowers.RECRUIT, 100), null));
        assertFalse(senior.acceptVeteranAttack(senior, null));
        assertFalse(senior.acceptVeteranAttack(first, null));
        AugmentCombat.runWithoutTriggers(() -> assertFalse(senior.acceptVeteranAttack(first, null)));
        assertFalse(senior.acceptVeteranAttack(second, null));
        assertTrue(senior.acceptVeteranAttack(first, null));
        assertEquals(ArmyTowers.RECRUIT.damage(), senior.attackDamageBeforeRankPenalty(null, null), 1e-9);
        assertEquals(ArmyTowers.RECRUIT.damage() * ArmyRank.STAFF_SERGEANT.attackMultiplier(),
                senior.modifyAttackDamage(null, null, ArmyTowers.RECRUIT.damage()), 1e-9);
    }

    @Test
    void retirementTicketIsOnePerRoundOneStoredAndCombatStarterOnly() {
        ArmyTower retiree = tower(ArmyTowers.RECRUIT, 0, "job_army_g2");
        ArmyStates.beginRound(OWNER, 4);
        remember(retiree);
        assertTrue(ArmyStates.hasFreeStarter(OWNER, ArmyTowers.RECRUIT));
        assertFalse(ArmyStates.hasFreeStarter(OWNER, ArmyTowers.CLERK));
        assertFalse(ArmyStates.hasFreeStarter(OWNER, ArmyTowers.GUARD));
        assertFalse(ArmyStates.hasFreeStarter(OWNER, ArmyTowers.SPECIALIST));
        remember(retiree);
        assertTrue(ArmyStates.consumeFreeStarter(OWNER, ArmyTowers.RECRUIT));
        remember(retiree);
        assertFalse(ArmyStates.consumeFreeStarter(OWNER, ArmyTowers.RECRUIT));
        ArmyStates.beginRound(OWNER, 5);
        remember(retiree);
        assertTrue(ArmyStates.hasFreeStarter(OWNER, ArmyTowers.RECRUIT));
        ArmyStates.clear(OWNER);
        assertFalse(ArmyStates.hasFreeStarter(OWNER, ArmyTowers.RECRUIT));
        assertTrue(ArmyStates.retirements(OWNER).isEmpty());
    }

    @Test
    void reservesKeepLatestTwoSnapshotsWithoutRankOrDuplicateMedalScaling() {
        ArmyTower first = tower(ArmyTowers.RECRUIT, 0);
        ArmyTower second = tower(ArmyTowers.GUNNER, 1);
        ArmyTower third = tower(ArmyTowers.SPECIALIST, 2);
        remember(first);
        remember(second);
        remember(third);
        ArmyStates.awardMedal(OWNER, 4);
        PlayerLane lane = lane();
        lane.assignAugmentSnapshot(snapshot("job_army_p", "job_army_s", "job_army_g1", "job_army_g2"));
        ArmyStates.spawnReserves(lane, 5);
        ArmyStates.spawnReserves(lane, 5);
        assertEquals(2, lane.towers().size());
        assertEquals(List.of(ArmyTowers.GUNNER.id(), ArmyTowers.SPECIALIST.id()),
                lane.towers().stream().map(tower -> tower.type().id()).toList());
        for (var tower : lane.towers()) {
            ArmyTower reserve = (ArmyTower) tower;
            assertTrue(reserve.isTemporaryCopy());
            assertEquals(0, reserve.slotWeight());
            assertFalse(reserve.canBeSold());
            assertFalse(reserve.ranks());
            assertFalse(reserve.receivesTraitEffects());
            assertFalse(reserve.promoteOneRank(lane));
            reserve.completeServiceWave(lane);
            assertEquals(0, reserve.service());
            assertFalse(reserve.completeDischarge(lane));
            TowerType original = reserve.type().id().equals(ArmyTowers.GUNNER.id())
                    ? ArmyTowers.GUNNER : ArmyTowers.SPECIALIST;
            assertEquals(original.maxHealth(), reserve.currentMaxHealth(), 1e-9);
            assertEquals(original.damage() * 1.5,
                    reserve.modifyAttackDamage(null, null, reserve.type().damage()), 1e-9);
        }
        assertEquals(4, ArmyStates.medalCount(OWNER));
    }

    private static void remember(ArmyTower tower) {
        ArmyStates.recordRetirement(tower, new ArmyStates.Retirement(
                tower.type(), tower.originalPosition(), tower.currentMaxHealth(), tower.type().damage()));
    }

    private static ArmyTower tower(TowerType type, int x, String... cards) {
        ArmyTower tower = new ArmyTower(type, OWNER, TeamId.RED, 1, new GridPosition(x, 0, 0));
        tower.syncAugments(snapshot(cards), null);
        return tower;
    }

    private static AugmentSnapshot snapshot(String... cards) {
        return new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards).map(id ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }

    private static PlayerLane lane() {
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.ZERO, List.of(new Vec3(20, 0, 0)),
                new Vec3(20, 0, 20), BlockBounds.of(new BlockPos(-10, -2, -10), new BlockPos(40, 5, 40)),
                List.of(new GridPosition(30, 0, 30)));
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }
}
