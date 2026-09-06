package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AugmentTelemetry;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.frost.FrostSplashTower;
import kim.biryeong.semiontd.tower.frost.FrostTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AugmentCombatGameTest {
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
            close(23, first.runtimeMonster().health(), "The original attack must receive finishing +35%.");
            source.damageTargetResult(first, 20);
            close(3, first.runtimeMonster().health(), "A builder secondary basic must not receive finishing.");
            new TowerAttackMonsterGoal(source).tick();
            if (!AugmentCombat.detailLines(tower).contains("연승 탄환 3/3")) throw new AssertionError("An eligible primary kill must refill three charges.");
            source.setCustomName(net.minecraft.network.chat.Component.literal("빌더 고유 이름"));
            source.refreshAugmentNameplate();
            source.refreshAugmentNameplate();
            String visible = source.getCustomName().getString();
            if (!visible.equals("빌더 고유 이름 · 연승 탄환 3/3")) {
                throw new AssertionError("Augment counters must preserve builder names without duplicate suffixes: " + visible);
            }
            SemionMonsterEntity next = monster(context, lane, source.position().add(2, 0, 0), 100);
            new TowerAttackMonsterGoal(source).tick();
            close(74, next.runtimeMonster().health(), "The next original primary must receive barrage +30%.");
            if (!AugmentCombat.detailLines(tower).contains("연승 탄환 2/3")) throw new AssertionError("One actual primary hit consumes one charge.");
            AugmentCombat.captureRoundEnd(lane, 5);
            close(2, combatEnd(lane, tower).finishingHits(), "Only the two original finishing hits are counted.");
            close(1, combatEnd(lane, tower).barrageActivations(), "A reload is not a fired barrage; only the charged hit counts.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void frostImmediateExtraAttackDoesNotReceivePrimaryFinishingBonus(GameTestHelper context) {
        PlayerLane lane = lane(context);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        FrostSplashTower tower = new FrostSplashTower(FrostTowers.ICE_BREAKER_T3, lane.ownerPlayer(), TeamId.RED, 1, position, position);
        lane.addTower(tower);
        lane.assignAugmentSnapshot(snapshot("finishing_fire_2", AugmentChoice.none()));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity target = monster(context, lane, source.position().add(1, 0, 0), 100_000);
        target.runtimeMonster().syncHealth(50_000);
        target.setHealth(50_000);
        try {
            new TowerAttackMonsterGoal(source).tick();
            close(50_000 - 47, target.runtimeMonster().health(), "Frost must deal primary 27 plus one unaugmented extra 20.");
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
            close(240, result.healthDamageAttempted(), "The original hit includes wartime once and subtracts the shield before HP capping.");
            AugmentCombat.onPrimaryAttackResolved(source, first, result);
            close(886, next.runtimeMonster().health(), "Domino transfers 114 without multiplying wartime a second time.");
            close(114, tower.roundMetricsTracker().snapshot().augmentSpecialDamageDealt(), "Only actual transferred HP damage is special damage.");
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
            source.hurt(attacker.damageSources().mobAttack(attacker), 40);
            AugmentCombat.settleWave(lane, 5);
            close(1, AugmentCombat.masteryStacks(tower), "Forty percent actual enemy HP damage and survival grants one stack.");
            close(104, tower.currentMaxHealth(), "One mastery stack grants four percent maximum HP.");
            close(62.4, tower.health(), "Growing maximum HP preserves the sixty-percent HP ratio.");
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
            if (!AugmentCombat.detailLines(tower).contains("연승 탄환 3/3")) {
                throw new AssertionError("An ordinary kill must prime barrage before the low-pressure hit.");
            }
            SemionMonsterEntity weakened = monster(context, lane, source.position().add(1, 0, 0), 100, true);
            weakened.runtimeMonster().syncHealth(35);
            weakened.setHealth(35);
            SemionMonsterEntity witness = monster(context, lane, source.position().add(2, 0, 0), 1000);
            Tower.DamageResult first = primary(tower, source, weakened, 20);
            close(26, first.outgoingDamage(), "A held barrage charge applies, but low-pressure cannot trigger finishing.");
            close(9, weakened.runtimeMonster().health(), "Charged damage must be applied to the weakened body.");
            if (!AugmentCombat.detailLines(tower).contains("연승 탄환 2/3")) {
                throw new AssertionError("Hitting a low-pressure body must consume a held charge.");
            }
            primary(tower, source, weakened, 20);
            if (!AugmentCombat.detailLines(tower).contains("연승 탄환 1/3")) {
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
            if (AugmentCombat.isMasteryEligible(tower)) throw new AssertionError("It must not unlock mastery selection either.");

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
        lane.assignAugmentSnapshot(snapshot("biased_armor", new AugmentChoice(null, null, "PHYSICAL")));
        lane.markWaveStarted(15);
        SemionTowerEntity source = entity(context, tower);
        try {
            SemionMonsterEntity physical = monster(context, lane, source.position().add(1, 0, 0), 100);
            SemionMonsterEntity magic = monster(context, lane, source.position().add(2, 0, 0), 100, false, DamageType.MAGIC);
            source.hurt(physical.damageSources().mobAttack(physical), 40);
            close(70, tower.health(), "Physical mode reduces forty to thirty before HP loss.");
            source.hurt(magic.damageSources().mobAttack(magic), 100);
            AugmentCombat.captureRoundEnd(lane, 15);
            var sample = combatEnd(lane, tower);
            close(100, sample.startingMaxHealth(), "Opening maximum HP is measured before combat.");
            close(30, sample.physicalDirectHpDamage(), "Physical direct damage is actual HP loss.");
            close(70, sample.magicDirectHpDamage(), "Lethal magic damage is capped by remaining HP.");
            close(10, sample.armorModifierReducedDamage(), "Reduction is the armor-stage difference, not inferred HP saved.");
            close(35, sample.armorModifierIncreasedDamage(), "The armor stage adds thirty-five even though only seventy HP remained.");
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

    private static ProductionTower add(GameTestHelper context, PlayerLane lane, String id, double damage) {
        TowerType type = new TowerType("augment_gametest_" + id, id, TowerCategory.DIRECT, 10, 100, 8, damage, 20, 0);
        ProductionTowerCatalog.registerStarter(type);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        ProductionTower tower = new ProductionTower(type, lane.ownerPlayer(), TeamId.RED, 1, position);
        lane.addTower(tower);
        entity(context, tower).setNoGravity(true);
        return tower;
    }

    private static SemionTowerEntity entity(GameTestHelper context, EntityBackedTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health) {
        return monster(context, lane, position, health, false);
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health, boolean lowPressure) {
        return monster(context, lane, position, health, lowPressure, DamageType.PHYSICAL);
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health,
                                               boolean lowPressure, DamageType damageType) {
        Monster monster = new Monster("augment_target_" + UUID.randomUUID(), TeamId.RED, 1, Optional.empty(), Optional.empty(),
                health, 0, 0, AttackKind.MELEE, "minecraft:zombie", null, damageType, 0,
                SummonTier.T1, List.of(SummonRole.RUSH), 0);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        if (lowPressure) {
            monster.setOrigin(MonsterOrigin.NORMAL_PAID);
            SemionPlayer buyer = new SemionPlayer(UUID.randomUUID(), "low-pressure-fixture", TeamId.BLUE, 1,
                    new PlayerEconomy(EconomyConfig.defaultConfig()));
            AugmentEconomyService.beginPrepare(buyer, 5);
            AugmentEconomyService.onSelected(buyer, "low_pressure_high_yield", 5, Map.of());
            AugmentEconomyService.setContract(buyer, 5, AugmentEconomyService.Contract.LOW_PRESSURE);
            var plan = AugmentEconomyService.quotePurchase(buyer, UUID.randomUUID(), 5,
                    true, true, false, true, true, 10, 5);
            AugmentEconomyService.applyPurchaseBody(monster, plan);
            if (!AugmentEconomyService.commitPurchase(buyer, plan, monster)) throw new AssertionError("Low-pressure fixture contract must commit.");
        }
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static AugmentSnapshot snapshot(Object... values) {
        List<PlayerAugmentState.Selection> selections = new ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            String id = (String) values[index];
            selections.add(new PlayerAugmentState.Selection(5 + index * 5, AugmentCatalog.find(id).orElseThrow().rarity(),
                    id, PlayerAugmentState.Outcome.SELECTED, null, (AugmentChoice) values[index + 1]));
        }
        return new AugmentSnapshot(AugmentConfig.defaults(), selections);
    }

    private static PlayerLane lane(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn, List.of(spawn.add(8, 0, 0)), spawn.add(8, 0, 10),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 10)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        lane.assignAugmentTelemetry(new AugmentTelemetry());
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static void cleanup(PlayerLane lane) {
        for (Monster monster : lane.activeMonsters()) {
            if (monster.hasMinecraftEntity() && lane.arenaWorld().getEntity(monster.minecraftEntityId()) != null) {
                lane.arenaWorld().getEntity(monster.minecraftEntityId()).discard();
            }
        }
        lane.activeMonsters().clear();
        for (Tower tower : List.copyOf(lane.towers())) lane.removeTower(tower);
        AreaEffectLaneIndex.unregister(lane);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    private static void close(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > .01) throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
    }
}
