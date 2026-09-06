package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.AugmentTelemetry;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot.CombatRoundSample;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import xyz.nucleoid.map_templates.BlockBounds;

class AugmentCombatTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000313");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreCatalog() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void tacticalTiersChooseDamageOrCoverWithoutCombiningModes() {
        for (int tier = 1; tier <= 3; tier++) {
            PlayerLane lane = lane();
            Tower tower = add(lane, "tactical_" + tier, 0);
            String id = "tactical_designation_" + tier;
            lane.assignAugmentSnapshot(snapshot(id, choice(tower, "ASSAULT")));
            assertEquals(new double[]{.15, .25, .40}[tier - 1], AugmentCombat.damageBonus(tower, null), 1e-9);
            assertEquals(100, AugmentCombat.incomingDamage(tower, null, null, 100, 100), 1e-9);
            lane.assignAugmentSnapshot(snapshot(id, choice(tower, "COVER")));
            assertEquals(0, AugmentCombat.damageBonus(tower, null), 1e-9);
            assertEquals(new double[]{88, 80, 70}[tier - 1], AugmentCombat.incomingDamage(tower, null, null, 100, 100), 1e-9);
        }
    }

    @Test
    void triangleAndTwinsAddAndKeepWaveSnapshotAfterNeighborDeath() {
        PlayerLane lane = lane();
        Tower first = add(lane, "pair", 0);
        Tower second = add(lane, "pair", 2);
        Tower neighbor = add(lane, "neighbor", 4);
        lane.assignAugmentSnapshot(snapshot("triangle_formation", AugmentChoice.none(), "twin_squadron", AugmentChoice.none()));
        AugmentCombat.startWave(lane, 5);
        assertEquals(.18, AugmentCombat.damageBonus(first, null), 1e-9);
        assertEquals(.08, AugmentCombat.damageBonus(neighbor, null), 1e-9);
        second.syncHealth(0);
        lane.removeTower(neighbor);
        assertEquals(.18, AugmentCombat.damageBonus(first, null), 1e-9);
        AugmentCombat.settleWave(lane, 5);
        assertEquals(0, AugmentCombat.damageBonus(first, null), 1e-9);
    }

    @Test
    void independentRadiusIncludesExactlyFourAndSnapshotsPosition() {
        PlayerLane lane = lane();
        Tower first = add(lane, "independent_first", 0);
        Tower neighbor = add(lane, "independent_neighbor", 4);
        lane.assignAugmentSnapshot(snapshot("independent_position", AugmentChoice.none()));
        assertEquals(0, AugmentCombat.independentEligibleCount(lane));
        neighbor.syncPosition(new GridPosition(5, 0, 0));
        assertEquals(2, AugmentCombat.independentEligibleCount(lane));
        AugmentCombat.startWave(lane, 5);
        neighbor.syncPosition(new GridPosition(1, 0, 0));
        assertEquals(.12, AugmentCombat.damageBonus(first, null), 1e-9);
    }

    @Test
    void rolesBreakTogetherAndNeverIncreaseBaselineAggro() {
        PlayerLane lane = lane();
        Tower front = add(lane, "front", 0);
        Tower artillery = add(lane, "artillery", 2);
        lane.assignAugmentSnapshot(snapshot("frontline_specialization", new AugmentChoice(front.logicalId(), artillery.logicalId(), "")));
        AugmentCombat.startWave(lane, 15);
        assertEquals(-.25, AugmentCombat.damageBonus(front, null), 1e-9);
        assertEquals(.30, AugmentCombat.damageBonus(artillery, null), 1e-9);
        assertEquals(front.type().aggroPriority(), front.aggroPriority());
        assertTrue(AugmentCombat.prefersEqualDistance(front));
        assertFalse(AugmentCombat.prefersEqualDistance(artillery));
        front.syncHealth(0);
        front.notifyDeath(lane);
        front.syncHealth(100);
        assertEquals(0, AugmentCombat.damageBonus(artillery, null), 1e-9);
        assertFalse(AugmentCombat.prefersEqualDistance(front));
    }

    @Test
    void overheatSettlesOnlyOnceAndUpgradeKeepsLogicalHeat() {
        PlayerLane lane = lane();
        Tower first = add(lane, "overheat_t1", 0);
        lane.assignAugmentSnapshot(snapshot("overheat_core", choice(first, "")));
        AugmentCombat.startWave(lane, 5);
        assertEquals(.40, AugmentCombat.damageBonus(first, null), 1e-9);
        AugmentCombat.settleWave(lane, 5);
        AugmentCombat.settleWave(lane, 5);
        assertEquals(1, AugmentCombat.heatStacks(first));
        assertEquals(-.06, AugmentCombat.damageBonus(first, null), 1e-9);
        Tower upgraded = create("overheat_t2", 0);
        upgraded.copyFrom(first, 50);
        lane.replaceTower(first, upgraded);
        assertEquals(first.logicalId(), upgraded.logicalId());
        assertEquals(1, AugmentCombat.heatStacks(upgraded));
        lane.assignAugmentSnapshot(snapshot("overheat_core", AugmentChoice.none()));
        AugmentCombat.startWave(lane, 6);
        AugmentCombat.settleWave(lane, 6);
        assertEquals(1, AugmentCombat.heatStacks(upgraded));
    }

    @Test
    void healthBonusesApplyAfterBuilderOnceAndPreserveHealthRatio() {
        PlayerLane lane = lane();
        Tower main = add(lane, "main", 0);
        Tower other = add(lane, "other", 8);
        main.syncHealth(50);
        lane.assignAugmentSnapshot(snapshot("one_man_show", choice(main, ""), "wartime_economy", AugmentChoice.none()));
        assertEquals(150, main.currentMaxHealth(), 1e-9);
        assertEquals(75, main.health(), 1e-9);
        assertEquals(120, other.currentMaxHealth(), 1e-9);
        assertEquals(1.35, AugmentCombat.damageBonus(main, null), 1e-9);
        assertEquals(.15, AugmentCombat.damageBonus(other, null), 1e-9);
        lane.removeTower(main);
        assertEquals(.15, AugmentCombat.damageBonus(other, null), 1e-9);
        Tower later = add(lane, "later", 12);
        assertEquals(.15, AugmentCombat.damageBonus(later, null), 1e-9);
        assertEquals(120, later.health(), 1e-9);
    }

    @Test
    void detachedProductionCopyIsNotAnOrdinaryRegisteredTower() {
        Tower copy = create("not_registered_copy", 0);
        assertTrue(AugmentCombat.isNormalPermanentType(copy));
        assertFalse(AugmentCombat.isNormalPermanent(copy));
    }

    @Test
    void dominoUsesAttemptedHealthDamageAndBiasHonorsGlobalFloor() {
        assertEquals(90, AugmentCombat.dominoDamage(200, 50, .6, .5), 1e-9);
        assertEquals(100, AugmentCombat.dominoDamage(200, 0, .6, .5), 1e-9);
        assertEquals(0, AugmentCombat.dominoDamage(40, 50, .6, .5), 1e-9);
        assertEquals(40, AugmentCombat.biasedDamage(100, 20, true, .75, 1.35), 1e-9);
        assertEquals(135, AugmentCombat.biasedDamage(100, 100, false, .75, 1.35), 1e-9);
    }

    @Test
    void combatTelemetryCapturesStartAndEndOnceWithoutRepeatingSettlement() {
        PlayerLane lane = lane();
        AugmentTelemetry telemetry = new AugmentTelemetry();
        lane.assignAugmentTelemetry(telemetry);
        Tower tower = add(lane, "telemetry_dedupe", 0);
        lane.assignAugmentSnapshot(snapshot("overheat_core", choice(tower, ""),
                "battlefield_mastery", choice(tower, "")));
        startCombat(lane, 5);
        AugmentCombat.captureWaveStartHealth(lane);

        List<CombatRoundSample> samples = telemetry.snapshot().combatRounds();
        assertEquals(1, samples.size());
        assertEquals("START", samples.getFirst().stage());
        assertEquals("PENDING", samples.getFirst().state().masteryResult());
        assertEquals(100.0, samples.getFirst().state().startingMaxHealth());

        AugmentCombat.settleWave(lane, 5);
        AugmentCombat.settleWave(lane, 5);
        AugmentCombat.captureRoundEnd(lane, 5);
        AugmentCombat.captureRoundEnd(lane, 5);
        samples = telemetry.snapshot().combatRounds();
        assertEquals(2, samples.size());
        assertEquals("END", samples.getLast().stage());
        assertEquals(1, samples.getLast().state().heatStacks());
        assertEquals(0, samples.getLast().state().masteryStacks());
        assertEquals("INSUFFICIENT_DAMAGE", samples.getLast().state().masteryResult());
        assertEquals(1, AugmentCombat.heatStacks(tower));
    }

    @Test
    void combatTelemetryKeepsLogicalReferenceAndStacksThroughUpgradeAndRecordsRemovalLoss() {
        PlayerLane lane = lane();
        AugmentTelemetry telemetry = new AugmentTelemetry();
        lane.assignAugmentTelemetry(telemetry);
        Tower first = add(lane, "telemetry_upgrade_t1", 0);
        lane.assignAugmentSnapshot(snapshot("overheat_core", choice(first, ""),
                "battlefield_mastery", choice(first, "")));
        startCombat(lane, 5);
        setRecordedEnemyDamage(first, 40.0);
        AugmentCombat.settleWave(lane, 5);
        CombatRoundSample earned = telemetry.snapshot().combatRounds().getLast();
        assertEquals("STACK_GAINED", earned.state().masteryResult());
        assertEquals(1, earned.state().masteryStacks());
        assertEquals(1, earned.state().heatStacks());

        Tower upgraded = create("telemetry_upgrade_t2", 0);
        upgraded.copyFrom(first, 50);
        assertTrue(lane.replaceTower(first, upgraded));
        assertEquals(2, telemetry.snapshot().combatRounds().size(), "An upgrade is not a removal.");
        assertEquals(first.logicalId(), upgraded.logicalId());
        startCombat(lane, 6);
        CombatRoundSample nextStart = telemetry.snapshot().combatRounds().getLast();
        assertEquals(earned.towerRef(), nextStart.towerRef());
        assertEquals(upgraded.type().id(), nextStart.towerTypeId());
        assertEquals(1, nextStart.state().masteryStacks());
        assertEquals(1, nextStart.state().heatStacks());
        assertEquals(104.0, nextStart.state().startingMaxHealth());

        assertTrue(lane.removeTower(upgraded));
        assertFalse(lane.removeTower(upgraded));
        List<CombatRoundSample> samples = telemetry.snapshot().combatRounds();
        assertEquals(4, samples.size());
        CombatRoundSample removed = samples.getLast();
        assertEquals("REMOVED", removed.stage());
        assertEquals("REMOVED", removed.state().masteryResult());
        assertEquals(earned.towerRef(), removed.towerRef());
        assertEquals(1, removed.state().masteryStacksLost());
    }

    @Test
    void roundEndCaptureReportsQualificationWithoutGrantingStacks() {
        PlayerLane lane = lane();
        AugmentTelemetry telemetry = new AugmentTelemetry();
        lane.assignAugmentTelemetry(telemetry);
        Tower tower = add(lane, "telemetry_unsettled", 0);
        lane.assignAugmentSnapshot(snapshot("battlefield_mastery", choice(tower, "")));
        startCombat(lane, 5);
        setRecordedEnemyDamage(tower, 40.0);

        AugmentCombat.captureRoundEnd(lane, 5);
        AugmentCombat.captureRoundEnd(lane, 5);
        List<CombatRoundSample> samples = telemetry.snapshot().combatRounds();
        assertEquals(2, samples.size());
        assertEquals("END", samples.getLast().stage());
        assertEquals("QUALIFIED_UNSETTLED", samples.getLast().state().masteryResult());
        assertEquals(Boolean.TRUE, samples.getLast().state().masteryQualified());
        assertEquals(0, samples.getLast().state().masteryStacks());
        assertEquals(0, AugmentCombat.masteryStacks(tower));
    }

    @Test
    void twinTelemetryUsesOccupiedSlotWeightsAndKeepsTheOpeningRatioAfterRemoval() {
        PlayerLane lane = lane();
        AugmentTelemetry telemetry = new AugmentTelemetry();
        lane.assignAugmentTelemetry(telemetry);
        Tower heavy = create("telemetry_heavy_pair", 0, 2);
        Tower heavyPartner = create("telemetry_heavy_pair", 2, 2);
        Tower light = create("telemetry_light_pair", 4, 1);
        Tower lightPartner = create("telemetry_light_pair", 6, 1);
        Tower singleton = create("telemetry_singleton", 8, 3);
        for (Tower tower : List.of(heavy, heavyPartner, light, lightPartner, singleton)) lane.addTower(tower);
        lane.assignAugmentSnapshot(snapshot("twin_squadron", AugmentChoice.none()));
        startCombat(lane, 5);

        List<CombatRoundSample> starts = telemetry.snapshot().combatRounds();
        assertEquals(5, starts.size());
        for (CombatRoundSample sample : starts) {
            assertEquals(2, sample.state().twinEligibleGroups());
            assertEquals(2.0 / 3.0, sample.state().twinAffectedSlotRatio(), 1e-9);
            assertEquals(!sample.towerTypeId().equals(singleton.type().id()), sample.state().twinEligible());
        }

        assertTrue(lane.removeTower(heavyPartner));
        AugmentCombat.settleWave(lane, 5);
        List<CombatRoundSample> ends = telemetry.snapshot().combatRounds().stream()
                .filter(sample -> sample.stage().equals("END")).toList();
        assertEquals(4, ends.size());
        for (CombatRoundSample sample : ends) {
            assertEquals(2, sample.state().twinEligibleGroups());
            assertEquals(2.0 / 3.0, sample.state().twinAffectedSlotRatio(), 1e-9);
        }
    }

    private static void startCombat(PlayerLane lane, int round) {
        AugmentCombat.startWave(lane, round);
        lane.towers().forEach(tower -> tower.markWaveStarted(round));
        AugmentCombat.captureWaveStartHealth(lane);
    }

    private static void setRecordedEnemyDamage(Tower tower, double amount) {
        // Actual damage attribution is covered by GameTest; these tests exercise ledger transitions.
        tower.setData(TowerDataKey.of(ResourceLocation.fromNamespaceAndPath("semiontd", "augment_enemy_damage"),
                Double.class), amount);
    }

    private static AugmentChoice choice(Tower tower, String mode) {
        return new AugmentChoice(tower.logicalId(), null, mode);
    }

    static AugmentSnapshot snapshot(Object... values) {
        List<PlayerAugmentState.Selection> selections = new ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            String id = (String) values[index];
            selections.add(new PlayerAugmentState.Selection(5 + index * 5, AugmentCatalog.find(id).orElseThrow().rarity(),
                    id, PlayerAugmentState.Outcome.SELECTED, null, (AugmentChoice) values[index + 1]));
        }
        return new AugmentSnapshot(AugmentConfig.defaults(), selections);
    }

    private static Tower add(PlayerLane lane, String id, int x) {
        Tower tower = create(id, x);
        lane.addTower(tower);
        return tower;
    }

    private static Tower create(String id, int x) {
        return create(id, x, 1);
    }

    private static Tower create(String id, int x, int slotWeight) {
        TowerType type = new TowerType("augment_test_" + id, id, TowerCategory.DIRECT, 10, 100, 5, 20, 20, 7);
        if (ProductionTowerCatalog.entry(type).isEmpty()) ProductionTowerCatalog.register(type);
        return new Tower(type, OWNER, TeamId.RED, 1, new GridPosition(x, 0, 0)) {
            @Override protected boolean execute(PlayerLane lane) {return false;}
            @Override public int slotWeight() {return slotWeight;}
        };
    }

    private static PlayerLane lane() {
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.ZERO, List.of(new Vec3(20, 0, 0)),
                new Vec3(20, 0, 20), BlockBounds.of(new BlockPos(-10, -2, -10), new BlockPos(40, 5, 40)),
                List.of(new GridPosition(30, 0, 30)));
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }
}
