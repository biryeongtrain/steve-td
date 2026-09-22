package kim.biryeong.semiontd.tower.ancientcity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AncientCityAugmentsGameTest {
    @GameTest
    public void detectionSharesWithTwoUnmarkedEnemiesForFullDuration(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(context, owner, "s");
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            AncientCityTower sensor = tower(context, lane, AncientCityTowers.SENSOR_T1, 2, 2);
            targets.add(target(context, lane, 5, 2, 1));
            targets.add(target(context, lane, 5, 3, 1));
            targets.add(target(context, lane, 5, 4, 1));
            targets.add(target(context, lane, 5, 5, 1));
            targets.add(target(context, lane, 5, 2, 2));
            require(sensor.execute(lane), "Sensor must cast.");
            require(targets.stream().filter(target -> AncientCityMarks.damageBonus(target.runtimeMonster(), owner) > 0).count() == 3,
                    "Detection must mark its primary target and two nearest unmarked enemies.");
            require(AncientCityMarks.damageBonus(targets.get(4).runtimeMonster(), owner) == 0,
                    "Detection sharing must not cross lanes.");
            double bonus = TowerBalanceRuntime.ability(sensor.type().id(), "markDamageBonus");
            int duration = TowerBalanceRuntime.abilityTicks(sensor.type().id(), "markDurationTicks");
            Monster copied = targets.get(1).runtimeMonster();
            for (int tick = 0; tick < duration - 1; tick++) copied.tickSurvivalScaling(null, 0);
            require(close(AncientCityMarks.damageBonus(copied, owner), bonus), "Shared mark must keep its full duration.");
            copied.tickSurvivalScaling(null, 0);
            require(AncientCityMarks.damageBonus(copied, owner) == 0, "Shared mark must expire exactly at full duration.");
            context.succeed();
        } finally {
            cleanup(lane, targets);
        }
    }

    @GameTest
    public void domainExpansionExtendsBasicAndSpellRangeOnlyAcrossOwnedSculk(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(context, owner, "g1");
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            TowerType shortRangeSensor = TowerType.builder(AncientCityTowers.SENSOR_T1.id(), "사거리 시험 감지체")
                    .maxHealth(50).range(2).damage(2).visual(AncientCityTowers.SENSOR_T1.visual())
                    .primaryDamageType(DamageType.MAGIC).build();
            AncientCityTower sensor = tower(context, lane, shortRangeSensor, 2, 2);
            SemionTowerEntity source = entity(context, sensor);
            SemionMonsterEntity far = target(context, lane, 6, 6, 1);
            SemionMonsterEntity foreign = target(context, lane, 6, 5, 2);
            targets.add(far);
            targets.add(foreign);
            require(source.distanceToSqr(far) > source.attackRange() * source.attackRange(), "Test enemy must be out of normal range.");
            require(sensor.ignoresAttackRange(source, far), "Both owned sculk positions must unlock range bypass.");
            require(!sensor.ignoresAttackRange(source, foreign), "Range bypass must retain lane ownership.");
            double before = far.runtimeMonster().health();
            new TowerAttackMonsterGoal(source).tick();
            require(far.runtimeMonster().health() < before, "Basic attack must use the shared range exception.");
            before = far.runtimeMonster().health();
            require(sensor.execute(lane) && far.runtimeMonster().health() < before, "Native spell must use the same sculk range exception.");
            far.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(7, 3, 6))));
            require(!sensor.ignoresAttackRange(source, far), "Enemy outside owned sculk must lose bypass.");
            sensor.syncPosition(GridPosition.from(context.absolutePos(new BlockPos(7, 2, 2))));
            far.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(6, 3, 6))));
            require(!sensor.ignoresAttackRange(source, far), "Tower outside owned sculk must lose bypass.");
            context.succeed();
        } finally {
            cleanup(lane, targets);
        }
    }

    @GameTest
    public void chainSonicUsesFullMarkedSecondaryDamageAndAddsThreeTargets(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(context, owner, "g2");
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            AncientCityTower warden = tower(context, lane, AncientCityTowers.WARDEN_T1, 3, 3);
            for (int index = 0; index < 9; index++) {
                SemionMonsterEntity target = target(context, lane, 4 + index % 3, 4 + index / 3, 1);
                AncientCityMarks.apply(target.runtimeMonster(), owner, UUID.randomUUID(), .25, 100);
                targets.add(target);
            }
            int count = warden.sonicTargetCount();
            require(warden.execute(lane), "Warden must cast sonic boom.");
            List<Double> losses = targets.stream().map(target -> 20000 - target.runtimeMonster().health())
                    .filter(loss -> loss > 0).toList();
            require(losses.size() == count, "Sonic boom must include exactly the native count plus three.");
            require(losses.stream().allMatch(loss -> close(loss, losses.getFirst())),
                    "Every marked secondary must receive full primary damage including its mark.");
            context.succeed();
        } finally {
            cleanup(lane, targets);
        }
    }

    @GameTest
    public void awakenedCityUsesStrongestWardenAndSixSitesWithSharedCooldown(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(context, owner, "p");
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            AncientCityTower weak = tower(context, lane, AncientCityTowers.WARDEN_T1, 2, 2);
            AncientCityTower strong = tower(context, lane, AncientCityTowers.WARDEN_T4, 3, 2);
            int[][] cells = {{0,0},{3,0},{6,0},{0,3},{3,3},{6,3},{0,6}};
            for (int[] cell : cells) {
                SemionMonsterEntity target = target(context, lane, cell[0], cell[1], 1);
                AncientCityMarks.apply(target.runtimeMonster(), owner, UUID.randomUUID(), .25, 100);
                targets.add(target);
            }
            weak.onWaveStarted(lane, 1);
            strong.onWaveStarted(lane, 1);
            require(weak.strongestWarden(lane) == strong, "Strongest warden is a damage basis, independent of tower selection.");
            weak.tickAwakenedCity(lane);
            require(targets.stream().allMatch(target -> close(target.runtimeMonster().health(), 20000)),
                    "A weaker warden must not duplicate the owner pulse.");
            strong.tickAwakenedCity(lane);
            double expected = strong.resolveOutgoingDamage(entity(context, strong), targets.getFirst(),
                    strong.magicDamage(targets.getFirst(), TowerBalanceRuntime.ability(strong.type().id(), "magicDamage"), true) * 2);
            List<Double> losses = targets.stream().map(target -> 20000 - target.runtimeMonster().health())
                    .filter(loss -> loss > 0).toList();
            require(losses.size() == 6, "Awakened city must fire from at most six distinct sculk sites.");
            require(losses.stream().allMatch(loss -> close(loss, expected)), "Every pulse must use 200% of strongest sonic primary damage.");
            double damage = strong.roundMagicDamageDealt();
            strong.tickAwakenedCity(lane);
            require(close(strong.roundMagicDamageDealt(), damage), "A repeated tick must respect shared cooldown.");
            long now = context.getLevel().getGameTime();
            require(!AncientCityStates.claimCityPulse(owner, now + 159, 160), "Cooldown must last a full eight seconds.");
            require(AncientCityStates.claimCityPulse(owner, now + 160, 160), "Cooldown must reopen after eight seconds.");
            AncientCityStates.onRoundStarted(owner, 2);
            require(AncientCityStates.cityPulseTicksRemaining(owner, now) == 0, "New round must clear old pulse cooldown.");
            AugmentCombat.runWithoutTriggers(() -> strong.tickAwakenedCity(lane));
            require(close(strong.roundMagicDamageDealt(), damage), "Augment extra attacks must not trigger city pulses.");
            strong.resetForRound(lane);
            strong.tickAwakenedCity(lane);
            require(close(strong.roundMagicDamageDealt(), damage), "Preparation must not emit a city pulse.");
            context.succeed();
        } finally {
            cleanup(lane, targets);
        }
    }

    @GameTest
    public void awakenedCityDeduplicatesCellsAndCapsEachPulseAtEightEnemies(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(context, owner, "p");
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            AncientCityTower warden = tower(context, lane, AncientCityTowers.WARDEN_T1, 2, 2);
            for (int index = 0; index < 10; index++) targets.add(target(context, lane, 5, 5, 1));
            AncientCityMarks.apply(targets.get(0).runtimeMonster(), owner, UUID.randomUUID(), .25, 100);
            AncientCityMarks.apply(targets.get(1).runtimeMonster(), owner, UUID.randomUUID(), .25, 100);
            SemionMonsterEntity outside = target(context, lane, 7, 7, 1);
            SemionMonsterEntity foreign = target(context, lane, 5, 5, 2);
            targets.add(outside);
            targets.add(foreign);
            warden.onWaveStarted(lane, 1);
            warden.tickAwakenedCity(lane);
            require(targets.stream().filter(target -> target.runtimeMonster().health() < 20000).count() == 8,
                    "One sculk cell shared by two marks must emit one pulse against at most eight enemies.");
            require(close(outside.runtimeMonster().health(), 20000), "Pulse radius must remain two blocks.");
            require(close(foreign.runtimeMonster().health(), 20000), "Pulse must preserve lane isolation.");
            context.succeed();
        } finally {
            cleanup(lane, targets);
        }
    }

    private static PlayerLane lane(GameTestHelper context, UUID owner, String... cards) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> global = new LinkedHashMap<>(abilities.get(AncientCityStates.CONFIG_ID));
        global.put("initialSculk", 49.0);
        abilities.put(AncientCityStates.CONFIG_ID, global);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
        AncientCityStates.clear(owner);
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = 2; y <= 6; y++) {
                    context.getLevel().setBlock(context.absolutePos(new BlockPos(x, y, z)),
                            (y == 2 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
                }
            }
        }
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(6, 6, 6));
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.atCenterOf(min), List.of(Vec3.atCenterOf(max)),
                Vec3.atCenterOf(max), BlockBounds.of(min, max), List.of(GridPosition.from(min)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards).map(suffix ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, "semiontd:job_ancient_city_" + suffix,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()));
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static AncientCityTower tower(GameTestHelper context, PlayerLane lane, TowerType type, int x, int z) {
        AncientCityTower tower = new AncientCityTower(type, lane.ownerPlayer(), TeamId.RED, 1,
                GridPosition.from(context.absolutePos(new BlockPos(x, 2, z))));
        lane.addTower(tower);
        entity(context, tower).setNoAi(true);
        return tower;
    }

    private static SemionTowerEntity entity(GameTestHelper context, AncientCityTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static SemionMonsterEntity target(GameTestHelper context, PlayerLane lane, int x, int z, int laneId) {
        Monster runtime = new Monster("ancient-city-augment-target", TeamId.RED, laneId, Optional.empty(), Optional.empty(),
                20000, 1, 0, AttackKind.MELEE, "minecraft:zombie", null, DamageType.PHYSICAL, 0, null, List.of(), 1L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(x, 3, z))));
        require(context.getLevel().addFreshEntity(entity), "Monster must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(runtime);
        return entity;
    }

    private static void cleanup(PlayerLane lane, List<SemionMonsterEntity> targets) {
        targets.forEach(SemionMonsterEntity::discard);
        lane.clearTowers();
        AreaEffectLaneIndex.unregister(lane);
        AncientCityStates.clear(lane.ownerPlayer());
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) < .001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
