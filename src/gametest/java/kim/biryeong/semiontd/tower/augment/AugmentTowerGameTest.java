package kim.biryeong.semiontd.tower.augment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AugmentTelemetry;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AugmentTowerGameTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000005309");
    private static final TowerType NORMAL_A = TowerType.builder("augment_test_normal_a", "검사 타워 A")
            .mineralCost(10).maxHealth(200).range(8).damage(20).attackIntervalTicks(20).build();
    private static final TowerType NORMAL_B = TowerType.builder("augment_test_normal_b", "검사 타워 B")
            .mineralCost(10).maxHealth(200).range(8).damage(40).attackIntervalTicks(20).build();

    @GameTest public void emergencyBellUsesThreeDistinctLogicalTargets(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            AugmentTower bell = augment(lane, AugmentTowers.EMERGENCY_BELL, pos(context, 5, 1, 8));
            Tower first = ordinary(lane, NORMAL_A, pos(context, 4, 1, 8));
            Tower second = ordinary(lane, NORMAL_A, pos(context, 6, 1, 8));
            Tower third = ordinary(lane, NORMAL_A, pos(context, 5, 1, 9));
            for (Tower tower : List.of(first, second, third)) health(lane, tower, 50);
            bell.markWaveStarted(5); bell.onWaveStarted(lane, 5);
            for (int i = 0; i < 4; i++) bell.execute(lane);
            for (Tower tower : List.of(first, second, third)) close(100, tower.health(), "Each target receives one 50 HP rescue");
            health(lane, first, 50); bell.execute(lane);
            close(50, first.health(), "A healed logical target cannot be rescued twice");
            require(bell.runtimeDetailLines().contains("구조 3/3"), "Rescue count must be visible");
            require(!bell.canAttackTarget(bell.runtimeEntity(lane).orElseThrow(), null), "Bell cannot attack");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest public void barrierCoreRedirectsOnceAndNeverReturnsLethalOverflow(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            AugmentTower core = augment(lane, AugmentTowers.BARRIER_CORE, pos(context, 5, 1, 8));
            Tower target = ordinary(lane, NORMAL_A, pos(context, 4, 1, 8));
            core.markWaveStarted(5); core.onWaveStarted(lane, 5);
            close(750, AugmentTowerService.redirectDamage(target, 1000), "One quarter is redirected");
            close(450, core.health(), "Core takes unmitigated transferred damage");
            close(7500, AugmentTowerService.redirectDamage(target, 10000), "Lethal overflow is not returned");
            close(0, core.health(), "Core dies on lethal transfer");
            close(700, core.telemetrySample(5, 0, 1, "ROUND_END").barrierAbsorbed(),
                    "Barrier telemetry measures actual absorbed health, not lethal overflow");
            close(100, AugmentTowerService.redirectDamage(target, 100), "Destroyed core has no live links");
            require(!core.canBeSold() && !core.canReceiveAllyHealing() && !core.triggersNearbyDeathEffects(), "Free body restrictions remain active");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest public void relayChargesTheOtherTowerAndDisconnectsOnDeath(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            AugmentTower relay = augment(lane, AugmentTowers.PULSE_RELAY, pos(context, 5, 1, 8));
            Tower first = ordinary(lane, NORMAL_A, pos(context, 4, 1, 8));
            Tower second = ordinary(lane, NORMAL_B, pos(context, 6, 1, 8));
            relay.markWaveStarted(5); relay.onWaveStarted(lane, 5);
            SemionMonsterEntity enemy = monster(context, lane, new Vec3(pos(context, 5, 1, 5).x() + .5,
                    pos(context, 5, 1, 5).y() + 1, pos(context, 5, 1, 5).z() + .5));
            for (int i = 0; i < 5; i++) relay.onLinkedPrimaryAttack(first, enemy, 100, DamageType.PHYSICAL);
            double before = enemy.runtimeMonster().health();
            relay.onLinkedPrimaryAttack(second, enemy, 200, DamageType.PHYSICAL);
            close(before - 100, enemy.runtimeMonster().health(), "Charge adds half of the other tower's resolved basic damage");
            relay.onLinkedPrimaryAttack(second, enemy, 200, DamageType.PHYSICAL);
            close(before - 100, enemy.runtimeMonster().health(), "An uncharged hit cannot repeat the extra damage");
            health(lane, first, 0); relay.onNearbyTowerDeath(lane, first);
            require(relay.runtimeDetailLines().contains("연결 0/2"), "Death disconnects the whole relay for this wave");
            var sample = relay.telemetrySample(5, 0, 1, "ROUND_END");
            require(sample.relayCharges() == 1 && sample.relayTriggers() == 1,
                    "Relay telemetry preserves actual charge creation and secondary attack counts after disconnect");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest public void minesUseFixedPathGroundAndLogicalHitLimit(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            for (int x = 0; x <= 12; x++) for (int z = 0; z <= 14; z++) {
                context.getLevel().setBlockAndUpdate(context.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
            }
            GridPosition position = pos(context, 6, 1, 10);
            require(AmbushMines.canPlace(lane, position), "Three mine positions have legal ground");
            require(!AmbushMines.canPlace(lane, pos(context, 6, 1, 3)), "Out-of-lane mine position rejects placement");
            AugmentTower workshop = augment(lane, AugmentTowers.AMBUSH_WORKSHOP, position);
            workshop.markWaveStarted(5); workshop.onWaveStarted(lane, 5);
            List<Vec3> points = AmbushMines.preview(lane, position);
            require(points.size() == 3, "Preview contains all three points");
            SemionMonsterEntity enemy = monster(context, lane, points.getFirst());
            enemy.setNoGravity(false); enemy.setOnGround(true);
            workshop.execute(lane);
            close(900, enemy.runtimeMonster().health(), "First mine deals one physical hit");
            enemy.setPos(points.get(1)); enemy.setOnGround(true); workshop.execute(lane);
            close(900, enemy.runtimeMonster().health(), "Logical enemy cannot consume a second mine");
            require(workshop.runtimeDetailLines().contains("지뢰 2/3"), "Unused mines remain available");
            require(workshop.telemetrySample(5, 0, 1, "ROUND_END").mineExplosions() == 1,
                    "A used mine records one explosion, independent of target count");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest public void telemetryKeepsLogicalPlacementAndDeduplicatesRoundEnd(GameTestHelper context) {
        PlayerLane lane = lane(context);
        AugmentTelemetry telemetry = new AugmentTelemetry();
        long[] tick = {100};
        int[] round = {5};
        telemetry.bindClock(() -> tick[0], () -> round[0]);
        lane.assignAugmentTelemetry(telemetry);
        try {
            AugmentTower core = augment(lane, AugmentTowers.BARRIER_CORE, pos(context, 5, 1, 8));
            Tower target = ordinary(lane, NORMAL_A, pos(context, 4, 1, 8));
            var placed = telemetry.snapshot().towerSamples().getFirst();
            require(placed.eventType().equals("PLACED") && placed.tick() == 100 && placed.round() == 5,
                    "Placement records the bound match tick and round");
            require(placed.slotWeight() == TowerCapacity.slotCost(core), "Placement records actual occupied slots");
            require(placed.mineExplosions() == null && placed.capacitorChargedShots() == null,
                    "Unrelated tower metrics remain unmeasured");
            core.markWaveStarted(5); core.onWaveStarted(lane, 5);
            AugmentTowerService.redirectDamage(target, 100);
            tick[0] = 200;
            core.recordTelemetry(lane, 5, "ROUND_END");
            core.recordTelemetry(lane, 5, "ROUND_END");
            var samples = telemetry.snapshot().towerSamples();
            require(samples.size() == 2, "Repeated round settlement records one terminal snapshot");
            require(samples.getLast().towerRef() == placed.towerRef(), "One logical tower keeps an anonymous stable reference");
            close(25, samples.getLast().barrierAbsorbed(), "Round-end snapshot preserves the measured transfer");

            AugmentTower copied = new AugmentTower(core.type(), OWNER, TeamId.RED, 1, core.position(), core.position());
            copied.copyFrom(core, 0);
            require(copied.logicalId().equals(core.logicalId()), "Entity replacement preserves logical identity");
            close(25, copied.telemetrySample(5, 200, placed.towerRef(), "ROUND_END").barrierAbsorbed(),
                    "Copied logical state preserves measured counters");
            round[0] = 6;
            health(lane, core, 0);
            core.resetForRound(lane);
            require(telemetry.snapshot().towerSamples().size() == 2,
                    "Round restoration is neither a new placement nor a logical removal");
            close(0, core.telemetrySample(6, 201, placed.towerRef(), "REMOVED").barrierAbsorbed(),
                    "Previous-round counters cannot be relabeled as new-round activity");
            lane.removeTower(core);
            core.onRemoved(lane);
            samples = telemetry.snapshot().towerSamples();
            require(samples.size() == 3 && samples.getLast().eventType().equals("REMOVED"),
                    "Logical removal records exactly one final sample");
            require(samples.getLast().towerRef() == placed.towerRef(), "Removal keeps the placement reference");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest public void lowPressureCannotAdvanceOrTriggerRelay(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            AugmentTower relay = augment(lane, AugmentTowers.PULSE_RELAY, pos(context, 5, 1, 8));
            Tower first = ordinary(lane, NORMAL_A, pos(context, 4, 1, 8));
            Tower second = ordinary(lane, NORMAL_B, pos(context, 6, 1, 8));
            relay.markWaveStarted(15); relay.onWaveStarted(lane, 15);
            Vec3 point = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(5, 2, 5)));
            SemionMonsterEntity weak = monster(context, lane, point);
            OffensiveAugmentTowerGameTest.markLowPressure(weak.runtimeMonster());
            List<String> initial = relay.runtimeDetailLines();
            for (int i = 0; i < 5; i++) relay.onLinkedPrimaryAttack(first, weak, 100, DamageType.PHYSICAL);
            require(initial.equals(relay.runtimeDetailLines()), "Low-pressure hits must not advance relay counters");
            SemionMonsterEntity normal = monster(context, lane, point.add(.5, 0, 0));
            for (int i = 0; i < 5; i++) relay.onLinkedPrimaryAttack(first, normal, 100, DamageType.PHYSICAL);
            List<String> charged = relay.runtimeDetailLines();
            relay.onLinkedPrimaryAttack(second, weak, 200, DamageType.PHYSICAL);
            close(1000, weak.runtimeMonster().health(), "Low-pressure income cannot trigger relay secondary damage");
            require(charged.equals(relay.runtimeDetailLines()), "Blocked relay procs must preserve charge and counters");
            relay.onLinkedPrimaryAttack(second, normal, 200, DamageType.PHYSICAL);
            close(900, normal.runtimeMonster().health(), "A normal hit may use the preserved relay charge");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest public void lowPressureCannotTriggerMinesButTakesCollateralDamage(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            for (int x = 0; x <= 12; x++) for (int z = 0; z <= 14; z++) {
                context.getLevel().setBlockAndUpdate(context.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
            }
            GridPosition position = pos(context, 6, 1, 10);
            AugmentTower workshop = augment(lane, AugmentTowers.AMBUSH_WORKSHOP, position);
            workshop.markWaveStarted(15); workshop.onWaveStarted(lane, 15);
            Vec3 point = AmbushMines.preview(lane, position).getFirst();
            SemionMonsterEntity weak = monster(context, lane, point);
            weak.setNoGravity(false); weak.setOnGround(true);
            OffensiveAugmentTowerGameTest.markLowPressure(weak.runtimeMonster());
            workshop.execute(lane);
            close(1000, weak.runtimeMonster().health(), "A low-pressure enemy alone cannot trigger a mine");
            require(workshop.runtimeDetailLines().contains("지뢰 3/3"), "A weak trigger must not consume any mine");
            SemionMonsterEntity normal = monster(context, lane, point.add(.25, 0, 0));
            normal.setNoGravity(false); normal.setOnGround(true);
            workshop.execute(lane);
            close(900, normal.runtimeMonster().health(), "A normal enemy triggers the mine");
            close(900, weak.runtimeMonster().health(), "Low-pressure income is not immune to collateral mine damage");
            require(workshop.runtimeDetailLines().contains("지뢰 2/3"), "One real trigger consumes only one mine");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Low-pressure mine regression: " + failure));
        } finally { cleanup(lane); }
    }

    @GameTest public void barricadeOnlyOverridesTheExistingChoiceAtEqualDistance(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            TowerType high = TowerType.builder("augment_test_high_aggro", "우선 대상").mineralCost(1)
                    .maxHealth(200).range(5).damage(10).aggroPriority(80).build();
            TowerType highest = TowerType.builder("augment_test_highest_aggro", "최우선 대상").mineralCost(1)
                    .maxHealth(200).range(5).damage(10).aggroPriority(100).build();
            ProductionTowerCatalog.registerStarter(high); ProductionTowerCatalog.registerStarter(highest);
            AugmentTower barricade = augment(lane, AugmentTowers.FOLDING_BARRICADE, pos(context, 3, 1, 5));
            Tower normal = ordinary(lane, high, pos(context, 7, 1, 5));
            Tower farther = ordinary(lane, highest, pos(context, 5, 1, 8));
            SemionMonsterEntity enemy = monster(context, lane,
                    Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(5, 2, 5))));
            AcquireLaneDefenseTargetGoal goal = new AcquireLaneDefenseTargetGoal(enemy);
            goal.start();
            require(enemy.getTarget() == ((EntityBackedTower) farther).runtimeEntity(lane).orElseThrow(),
                    "Different-distance higher-priority target remains the normal choice; selected=" + enemy.getTarget());
            health(lane, farther, 0); enemy.setTarget(null); goal.start();
            require(enemy.getTarget() == barricade.runtimeEntity(lane).orElseThrow(),
                    "At the same squared distance the barricade wins over the baseline target; selected=" + enemy.getTarget());
            ((EntityBackedTower) normal).runtimeEntity(lane).orElseThrow().setPos(enemy.position().add(1, 0, 0));
            enemy.setTarget(null); goal.start();
            require(enemy.getTarget() == ((EntityBackedTower) normal).runtimeEntity(lane).orElseThrow(),
                    "Barricade cannot pull a target from a different distance; selected=" + enemy.getTarget());
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Barricade equal-distance regression: " + failure));
        } finally { cleanup(lane); }
    }

    private static PlayerLane lane(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        ProductionTowerCatalog.registerStarter(NORMAL_A);
        ProductionTowerCatalog.registerStarter(NORMAL_B);
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0)), max = context.absolutePos(new BlockPos(12, 5, 14));
        Vec3 spawn = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(6, 2, 0)));
        Vec3 next = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(6, 2, 7)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn, List.of(next),
                Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(6, 2, 14))), BlockBounds.of(min, max), List.of(pos(context, 6, 1, 12)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, OWNER, context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static AugmentTower augment(PlayerLane lane, TowerType type, GridPosition position) {
        AugmentTower tower = (AugmentTower) ProductionTowerCatalog.entry(type).orElseThrow().create(OWNER, TeamId.RED, 1, position);
        lane.addTower(tower); tower.runtimeEntity(lane).ifPresent(entity -> entity.setNoAi(true)); return tower;
    }
    private static Tower ordinary(PlayerLane lane, TowerType type, GridPosition position) {
        Tower tower = new ProductionTower(type, OWNER, TeamId.RED, 1, position);
        lane.addTower(tower); ((EntityBackedTower) tower).runtimeEntity(lane).ifPresent(entity -> entity.setNoAi(true)); return tower;
    }
    private static void health(PlayerLane lane, Tower tower, double health) {
        tower.syncHealth(health); ((EntityBackedTower) tower).runtimeEntity(lane).orElseThrow().setHealth((float) health);
    }
    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position) {
        Monster monster = new Monster("augment_test_enemy", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                1000, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout()); entity.setNoAi(true); entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "Test enemy spawns");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster); return entity;
    }
    private static GridPosition pos(GameTestHelper context, int x, int y, int z) { return GridPosition.from(context.absolutePos(new BlockPos(x, y, z))); }
    private static void cleanup(PlayerLane lane) {
        lane.disableMonsters();
        lane.clearTowers();
        AreaEffectLaneIndex.unregister(lane);
    }
    private static void close(double expected, double actual, String message) { require(Math.abs(expected - actual) < .01, message + ": " + actual); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
