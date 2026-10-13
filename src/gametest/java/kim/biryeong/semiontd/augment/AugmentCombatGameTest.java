package kim.biryeong.semiontd.augment;

import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AugmentCombatGameTest extends AugmentCombatFixture {
    @GameTest
    public void beneficialEffectsChangeActualHealthDamageAndAttackIntervalWithoutCompounding(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            for (int tier = 1; tier <= 3; tier++) {
                TowerType type = new TowerType("beneficial_test_" + tier, "beneficial", TowerCategory.DIRECT,
                        10, 100, 8, 100, 100, 0);
                ProductionTowerCatalog.registerStarter(type);
                var tower = new ProductionTower(type, lane.ownerPlayer(), TeamId.RED, 1,
                        GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4))));
                lane.addTower(tower);
                var source = entity(context, tower);
                source.setNoAi(true);
                source.setNoGravity(true);
                var selected = snapshot("beneficial_effect_" + tier, AugmentChoice.none());
                lane.assignAugmentSnapshot(selected);
                lane.assignAugmentSnapshot(selected);
                double bonus = tier / 20.0;
                close(100 * (1 + bonus), source.getMaxHealth(), "The entity must receive health once.");
                close(Math.ceil(100 / (1 + bonus)), source.attackIntervalTicks(), "Attack speed divides the attack interval.");
                var target = monster(context, lane, source.position().add(1, 0, 0), 1000);
                close(100 * (1 + bonus), primary(tower, source, target, 100).dealtDamage(), "Final damage applies once.");
                lane.assignAugmentSnapshot(AugmentSnapshot.none());
                close(100, source.getMaxHealth(), "Clearing the snapshot must restore health.");
                close(100, source.attackIntervalTicks(), "Clearing the snapshot must restore attack speed.");
                close(100, primary(tower, source, target, 100).dealtDamage(), "Clearing the snapshot must restore damage.");
                lane.removeTower(tower);
            }
            context.succeed();
        } catch (Throwable failure) {context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));}
        finally {cleanup(lane);}
    }

    @GameTest(maxTicks = 100)
    public void finishingAndBarrageApplyOnlyToOriginalPrimaryAttack(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "primary", 20);
        lane.assignAugmentSnapshot(snapshot("finishing_fire_2", AugmentChoice.none(), "winning_barrage", AugmentChoice.none()));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity first = monster(context, lane, source.position().add(1, 0, 0), 100);
        first.runtimeMonster().syncHealth(50);
        first.setHealth(50);
        try {
            new TowerAttackMonsterGoal(source).tick();
            close(16, first.runtimeMonster().health(), "The original attack must receive finishing +70%.");
            source.damageTargetResult(first, 10);
            close(6, first.runtimeMonster().health(), "A ten-damage builder secondary must not receive finishing or kill before the next primary.");
            new TowerAttackMonsterGoal(source).tick();
            if (!AugmentCombat.detailLines(tower).contains("칼날비 3/3")) throw new AssertionError("An eligible primary kill must refill three charges.");
            source.setCustomName(net.minecraft.network.chat.Component.literal("빌더 고유 이름"));
            source.refreshAugmentNameplate();
            source.refreshAugmentNameplate();
            String visible = source.getCustomName().getString();
            if (!visible.equals("빌더 고유 이름 · 칼날비 3/3")) {
                throw new AssertionError("Augment counters must preserve builder names without duplicate suffixes: " + visible);
            }
            SemionMonsterEntity next = monster(context, lane, source.position().add(2, 0, 0), 100);
            new TowerAttackMonsterGoal(source).tick();
            close(68, next.runtimeMonster().health(), "The next original primary must receive barrage +60%.");
            if (!AugmentCombat.detailLines(tower).contains("칼날비 2/3")) throw new AssertionError("One actual primary hit consumes one charge.");
            AugmentCombat.captureRoundEnd(lane, 5);
            close(2, combatEnd(lane, tower).finishingHits(), "Only the two original finishing hits are counted.");
            close(1, combatEnd(lane, tower).barrageActivations(), "A reload is not a fired barrage; only the charged hit counts.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void dominoReadsPostShieldAttemptAndDoesNotRepeatAttackerBonus(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "domino", 200);
        lane.assignAugmentSnapshot(snapshot("domino_fire", AugmentChoice.none(), "wartime_economy", AugmentChoice.none()));
        lane.markWaveStarted(15);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity first = monster(context, lane, source.position().add(1, 0, 0), 100);
        SemionMonsterEntity next = monster(context, lane, source.position().add(2, 0, 0), 1000);
        first.runtimeMonster().syncHealth(50);
        first.setHealth(50);
        if (!first.runtimeMonster().grantShield(DamageType.PHYSICAL, 30, 100,
                context.getLevel().getGameTime(), next.runtimeMonster())) {
            throw new AssertionError("Domino fixture must receive a thirty-point physical shield.");
        }
        try {
            Tower.DamageResult result = tower.damagePrimaryAttackTargetResult(source, first, 200);
            close(300, result.healthDamageAttempted(), "The original hit includes wartime once and subtracts the shield before HP capping.");
            AugmentCombat.onPrimaryAttackResolved(source, first, result);
            close(775, next.runtimeMonster().health(), "Domino transfers 225 without multiplying wartime a second time.");
            close(225, tower.roundMetricsTracker().snapshot().augmentSpecialDamageDealt(), "Only actual transferred HP damage is special damage.");
            AugmentCombat.captureRoundEnd(lane, 15);
            close(1, combatEnd(lane, tower).dominoTransfers(), "The resolved transfer must be counted exactly once.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void masteryCountsEnemyHealthLossButNotEnvironmentOrTransferredDamage(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "mastery", 20);
        lane.assignAugmentSnapshot(snapshot("battlefield_mastery", new AugmentChoice(tower.logicalId(), null, "")));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity attacker = monster(context, lane, source.position().add(1, 0, 0), 1000);
        try {
            source.hurt(attacker.damageSources().mobAttack(attacker), 20);
            if (AugmentCombat.detailLines(tower).stream().noneMatch(line -> line.contains("조건 피해 20/20"))) {
                throw new AssertionError("Mastery details must display the configured twenty-percent threshold.");
            }
            AugmentCombat.settleWave(lane, 5);
            close(1, AugmentCombat.masteryStacks(tower), "Twenty percent actual enemy HP damage and survival grants one stack.");
            close(115, tower.currentMaxHealth(), "One mastery stack grants fifteen percent maximum HP.");
            close(92, tower.health(), "Growing maximum HP preserves the eighty-percent HP ratio.");
            lane.markWaveStarted(6);
            source.applyTransferredDamage(42);
            AugmentCombat.settleWave(lane, 6);
            close(1, AugmentCombat.masteryStacks(tower), "Transferred damage must not progress mastery.");
            close(0, tower.roundMetricsTracker().snapshot().enemyHpDamage(), "Transferred damage has no enemy HP credit.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void lowPressureCannotTriggerFinishingOrDominoButConsumesExistingBarrage(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "low_pressure_primary", 20);
        lane.assignAugmentSnapshot(snapshot("finishing_fire_2", AugmentChoice.none(),
                "winning_barrage", AugmentChoice.none(), "domino_fire", AugmentChoice.none()));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        try {
            SemionMonsterEntity ordinary = monster(context, lane, source.position().add(1, 0, 0), 10);
            primary(tower, source, ordinary, 20);
            if (!AugmentCombat.detailLines(tower).contains("칼날비 3/3")) {
                throw new AssertionError("An ordinary kill must prime barrage before the low-pressure hit.");
            }
            SemionMonsterEntity weakened = monster(context, lane, source.position().add(1, 0, 0), 100, true);
            weakened.runtimeMonster().syncHealth(35);
            weakened.setHealth(35);
            SemionMonsterEntity witness = monster(context, lane, source.position().add(2, 0, 0), 1000);
            Tower.DamageResult first = primary(tower, source, weakened, 20);
            close(32, first.outgoingDamage(), "A held barrage charge applies, but low-pressure cannot trigger finishing.");
            close(3, weakened.runtimeMonster().health(), "Charged damage must be applied to the weakened body.");
            if (!AugmentCombat.detailLines(tower).contains("칼날비 2/3")) {
                throw new AssertionError("Hitting a low-pressure body must consume a held charge.");
            }
            primary(tower, source, weakened, 20);
            if (!AugmentCombat.detailLines(tower).contains("칼날비 1/3")) {
                throw new AssertionError("A low-pressure kill must consume, not refill, barrage.");
            }
            close(1000, witness.runtimeMonster().health(), "A low-pressure kill must not start domino.");
            if (AugmentCombat.detailLines(tower).stream().anyMatch(line -> line.startsWith("마무리 사격"))) {
                throw new AssertionError("Low-pressure HP must not add a finishing proc count.");
            }
            AugmentCombat.captureRoundEnd(lane, 5);
            close(2, combatEnd(lane, tower).barrageActivations(), "Both held charges spent on low-pressure targets count as actual uses.");
            close(0, combatEnd(lane, tower).finishingHits(), "Low-pressure targets produce no finishing observation.");
            close(0, combatEnd(lane, tower).dominoTransfers(), "Low-pressure kills produce no transfer observation.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void lowPressureDamageIsMeasuredWithoutGrantingMastery(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "low_pressure_mastery", 20);
        lane.assignAugmentSnapshot(snapshot("battlefield_mastery", new AugmentChoice(tower.logicalId(), null, "")));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        try {
            SemionMonsterEntity weakened = monster(context, lane, source.position().add(1, 0, 0), 100, true);
            source.hurt(weakened.damageSources().mobAttack(weakened), 40);
            close(40, tower.roundMetricsTracker().snapshot().enemyHpDamage(), "Low-pressure damage is still actual enemy HP damage.");
            AugmentCombat.settleWave(lane, 5);
            close(40, combatEnd(lane, tower).enemyHpDamage(), "Telemetry retains all actual enemy HP damage.");
            close(0, combatEnd(lane, tower).masteryEligibleHpDamage(), "Telemetry separates the low-pressure-excluded mastery input.");
            close(0, AugmentCombat.masteryStacks(tower), "Low-pressure damage cannot grant a mastery stack.");

            lane.markWaveStarted(6);
            SemionMonsterEntity ordinary = monster(context, lane, source.position().add(2, 0, 0), 100);
            source.hurt(ordinary.damageSources().mobAttack(ordinary), 40);
            AugmentCombat.settleWave(lane, 6);
            close(1, AugmentCombat.masteryStacks(tower), "Ordinary enemy damage must still grant mastery.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void armorTelemetrySeparatesModifierDeltaFromActualDirectHpDamage(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "armor_telemetry", 20);
        lane.assignAugmentSnapshot(snapshot("biased_armor_physical", AugmentChoice.none()));
        lane.markWaveStarted(15);
        SemionTowerEntity source = entity(context, tower);
        try {
            SemionMonsterEntity physical = monster(context, lane, source.position().add(1, 0, 0), 100);
            SemionMonsterEntity magic = monster(context, lane, source.position().add(2, 0, 0), 100, false, DamageType.MAGIC);
            source.hurt(physical.damageSources().mobAttack(physical), 40);
            close(78, tower.health(), "Physical mode reduces forty to twenty-two before HP loss.");
            source.hurt(magic.damageSources().mobAttack(magic), 100);
            AugmentCombat.captureRoundEnd(lane, 15);
            var sample = combatEnd(lane, tower);
            close(100, sample.startingMaxHealth(), "Opening maximum HP is measured before combat.");
            close(22, sample.physicalDirectHpDamage(), "Physical direct damage is actual HP loss.");
            close(78, sample.magicDirectHpDamage(), "Lethal magic damage is capped by remaining HP.");
            close(18, sample.armorModifierReducedDamage(), "Reduction is the armor-stage difference, not inferred HP saved.");
            close(35, sample.armorModifierIncreasedDamage(), "The armor stage adds thirty-five even though only seventy-eight HP remained.");
            if (!"PHYSICAL".equals(sample.armorMode())) throw new AssertionError("The chosen armor mode must be recorded.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    private static AugmentTelemetrySnapshot.CombatState combatEnd(PlayerLane lane, Tower tower) {
        int reference = lane.augmentTelemetry().towerRef(tower.logicalId());
        return lane.augmentTelemetry().snapshot().combatRounds().stream()
                .filter(sample -> sample.towerRef() == reference && sample.stage().equals("END"))
                .reduce((first, second) -> second).orElseThrow().state();
    }

    private static Tower.DamageResult primary(Tower tower, SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        Tower.DamageResult result = tower.damagePrimaryAttackTargetResult(source, target, damage);
        AugmentCombat.onPrimaryAttackResolved(source, target, result);
        return result;
    }
}
