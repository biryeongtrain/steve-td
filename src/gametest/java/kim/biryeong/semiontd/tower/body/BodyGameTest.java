package kim.biryeong.semiontd.tower.body;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class BodyGameTest {
    @GameTest(maxTicks = 120)
    public void augmentNormalHeartbeatAddsExactlyOneNativeOrganAction(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "body-augment-beats");
        BodyTower heart = tower(BodyTowers.HEART_T1, setup.owner(), context, new BlockPos(3, 2, 3));
        BodyTower skin = tower(BodyTowers.SKIN_T2, setup.owner(), context, new BlockPos(5, 2, 3));
        try {
            setup.lane().addTower(heart);
            setup.lane().addTower(skin);
            setup.lane().assignAugmentSnapshot(augment("job_body_g1"));
            setup.lane().markWaveStarted(1);
            require(heart.execute(setup.lane()), "First normal heartbeat must run.");
            require(heart.execute(setup.lane()), "Second normal heartbeat must run.");
            requireClose(0.18, towerEntity(context, skin).activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Two normal heartbeats must deliver three native skin actions without recursion.");
            context.succeed();
        } finally {cleanup(setup, List.of());}
    }

    @GameTest(maxTicks = 120)
    public void augmentEyeFacesNearestEnemyAndAdrenalineHealsOnce(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "body-augment-eye-heart");
        BodyTower heart = tower(BodyTowers.HEART_T1, setup.owner(), context, new BlockPos(1, 2, 1));
        BodyTower eye = tower(BodyTowers.EYE_T1, setup.owner(), context, new BlockPos(4, 2, 4));
        List<SpawnedTarget> targets = new ArrayList<>();
        try {
            setup.lane().addTower(heart);
            setup.lane().addTower(eye);
            setup.lane().assignAugmentSnapshot(augment("job_body_s", "job_body_g2"));
            setup.lane().markWaveStarted(1);
            SemionTowerEntity eyeEntity = towerEntity(context, eye);
            targets.add(spawnTarget(context, setup.lane(), eyeEntity.position().add(2, 0, 0), "eye-nearest", 1000));
            targets.add(spawnTarget(context, setup.lane(), eyeEntity.position().add(0, 0, 3), "eye-farther", 1000));
            eye.actOnHeartbeat(setup.lane());
            require(targets.getFirst().runtime().health() < 1000, "Eye must hit the closest target to the side.");
            requireClose(1000, targets.getLast().runtime().health(), "Eye must not keep its old facing.");
            SemionTowerEntity heartEntity = towerEntity(context, heart);
            double max = heart.currentMaxHealth();
            heartEntity.hurtIgnoringReductions(heartEntity.damageSources().mobAttack(targets.getFirst().entity()), max * 0.55);
            requireClose(max * 0.75, heart.health(), "Adrenaline restores 30% after crossing 50%.");
            int base = heart.type().attackIntervalTicks();
            for (int index = 0; index < 8; index++) {
                require(heart.cooldownTicksAfterExecute(setup.lane()) == Math.max(1, (int) Math.round(base * 0.5)),
                        "Exactly eight scheduled intervals must be halved.");
            }
            require(heart.cooldownTicksAfterExecute(setup.lane()) == base, "Ninth interval must return to normal.");
            heartEntity.hurtIgnoringReductions(heartEntity.damageSources().mobAttack(targets.getFirst().entity()), max * 0.3);
            requireClose(max * 0.45, heart.health(), "Adrenaline must not heal twice in a wave.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {cleanup(setup, targets);}
    }

    @GameTest(maxTicks = 120)
    public void augmentSkinIgnoresAbsorptionAndSelfDamageAndCapsEachLinkedHeart(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "body-augment-skin");
        BodyTower first = tower(BodyTowers.HEART_T1, setup.owner(), context, new BlockPos(2, 2, 2));
        BodyTower second = tower(BodyTowers.HEART_T1, setup.owner(), context, new BlockPos(3, 2, 2));
        BodyTower skin = tower(BodyTowers.SKIN_T2, setup.owner(), context, new BlockPos(5, 2, 5));
        SpawnedTarget enemy = null;
        try {
            setup.lane().addTower(first);
            setup.lane().addTower(second);
            setup.lane().addTower(skin);
            setup.lane().assignAugmentSnapshot(augment("job_body_p"));
            setup.lane().markWaveStarted(1);
            SemionTowerEntity entity = towerEntity(context, skin);
            enemy = spawnTarget(context, setup.lane(), entity.position().add(0, 0, 1), "skin-damage-source", 10000);
            double threshold = skin.currentMaxHealth() * 0.15;
            entity.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(threshold);
            entity.setAbsorptionAmount((float) threshold);
            requireClose(threshold, entity.getAbsorptionAmount(), "Fixture must provide a real absorption shield.");
            entity.hurtIgnoringReductions(entity.damageSources().mobAttack(enemy.entity()), threshold);
            requireClose(0, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Absorbed damage must not charge skin.");
            entity.hurtIgnoringReductions(entity.damageSources().mobAttack(entity), threshold);
            requireClose(0, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Self damage must not charge skin.");
            entity.hurtIgnoringReductions(entity.damageSources().mobAttack(enemy.entity()), threshold * 2.0);
            requireClose(0.12, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Two linked hearts must each pulse once despite multiple crossed thresholds.");
            entity.hurtIgnoringReductions(entity.damageSources().mobAttack(enemy.entity()), threshold);
            requireClose(0.12, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Further damage inside one second must not bank or replay heartbeats.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {cleanup(setup, enemy == null ? List.of() : List.of(enemy));}
    }

    private static AugmentSnapshot augment(String... ids) {
        return new AugmentSnapshot(AugmentConfig.defaults(), java.util.Arrays.stream(ids).map(id ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }

    @GameTest(maxTicks = 120)
    public void heartPulseMakesSkinActAndGainArmor(GameTestHelper context) {
        TestSetup setup = setup(context, "body-heart-owner");
        BodyTower heart = tower(BodyTowers.HEART_T1, setup.owner(), context, new BlockPos(6, 2, 6));
        BodyTower skin = tower(BodyTowers.SKIN_T2, setup.owner(), context, new BlockPos(8, 2, 6));
        SpawnedTarget target = null;
        try {
            setup.lane().addTower(heart);
            setup.lane().addTower(skin);
            SemionTowerEntity skinEntity = towerEntity(context, skin);
            target = spawnTarget(context, setup.lane(), skinEntity.position().add(0.0, 0.0, 1.0),
                    "body-skin-target", 100.0);

            setup.lane().markWaveStarted(1);
            heart.tick(setup.lane());

            require(target.runtime().health() < 100.0, "A heart pulse must make the skin damage nearby enemies.");
            requireClose(0.06, skinEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "T2 skin must gain one armor stack per action.");
            context.succeed();
        } finally {
            cleanup(setup, target == null ? List.of() : List.of(target));
        }
    }

    @GameTest(maxTicks = 120)
    public void heartDoesNotSignalDuringPreparation(GameTestHelper context) {
        TestSetup setup = setup(context, "body-heart-prepare-owner");
        BodyTower heart = tower(BodyTowers.HEART_T1, setup.owner(), context, new BlockPos(6, 2, 6));
        BodyTower skin = tower(BodyTowers.SKIN_T1, setup.owner(), context, new BlockPos(8, 2, 6));
        SpawnedTarget target = null;
        try {
            setup.lane().addTower(heart);
            setup.lane().addTower(skin);
            SemionTowerEntity skinEntity = towerEntity(context, skin);
            target = spawnTarget(context, setup.lane(), skinEntity.position().add(0.0, 0.0, 1.0),
                    "body-heart-prepare-target", 100.0);

            heart.tick(setup.lane());
            requireClose(100.0, target.runtime().health(),
                    "The heart must not signal before the wave starts.");

            setup.lane().markWaveStarted(1);
            heart.tick(setup.lane());
            double healthAfterWavePulse = target.runtime().health();
            require(healthAfterWavePulse < 100.0,
                    "The heart must signal after the wave starts.");

            heart.resetForRound(setup.lane());
            heart.tick(setup.lane());
            requireClose(healthAfterWavePulse, target.runtime().health(),
                    "The heart must stop signaling when preparation starts again.");
            context.succeed();
        } finally {
            cleanup(setup, target == null ? List.of() : List.of(target));
        }
    }

    @GameTest(maxTicks = 120)
    public void heartGainsOnePermanentStackFromAnOwnedBodyTowerDeath(GameTestHelper context) {
        TestSetup setup = setup(context, "body-heart-death-stack-owner");
        BodyTower heart = tower(BodyTowers.HEART_T2, setup.owner(), context, new BlockPos(6, 2, 6));
        BodyTower skin = tower(BodyTowers.SKIN_T1, setup.owner(), context, new BlockPos(8, 2, 6));
        try {
            setup.lane().addTower(heart);
            setup.lane().addTower(skin);

            require(setup.lane().killTower(skin), "The owned body tower must die through the shared lane flow.");
            require(heart.heartDeathStacks() == 1,
                    "The T2 heart must gain exactly one permanent stack from the body tower death.");
            setup.lane().killTower(skin);
            require(heart.heartDeathStacks() == 1,
                    "The same death notification must not grant another stack.");

            heart.resetForRound(setup.lane());
            require(heart.heartDeathStacks() == 1,
                    "Heart death stacks must survive round preparation resets.");
            context.succeed();
        } finally {
            cleanup(setup, List.of());
        }
    }

    @GameTest(maxTicks = 120)
    public void brainDebuffDoesNotStack(GameTestHelper context) {
        TestSetup setup = setup(context, "body-brain-owner");
        BodyTower brain = tower(BodyTowers.BRAIN_T1, setup.owner(), context, new BlockPos(7, 2, 7));
        SpawnedTarget target = null;
        try {
            setup.lane().addTower(brain);
            target = spawnTarget(context, setup.lane(), towerEntity(context, brain).position().add(0.0, 0.0, 2.0),
                    "body-brain-target", 200.0);

            for (int hit = 0; hit < 4; hit++) {
                brain.actOnHeartbeat(setup.lane());
            }

            requireClose(0.10, target.entity().activeTimedEffectMagnitude(
                    TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                    "Repeated brain hits must keep one damage-taken debuff.");
            requireClose(0.08, target.entity().activeTimedEffectMagnitude(
                    TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "Repeated brain hits must keep one attack-weakening debuff.");
            context.succeed();
        } finally {
            cleanup(setup, target == null ? List.of() : List.of(target));
        }
    }

    @GameTest(maxTicks = 120)
    public void genitalSecondHitDealsMagicDamageAndSlows(GameTestHelper context) {
        TestSetup setup = setup(context, "body-genital-owner");
        BodyTower genital = tower(BodyTowers.GENITAL_T1, setup.owner(), context, new BlockPos(7, 2, 7));
        SpawnedTarget target = null;
        try {
            setup.lane().addTower(genital);
            target = spawnTarget(context, setup.lane(), towerEntity(context, genital).position().add(0.0, 0.0, 2.0),
                    "body-genital-target", 200.0);

            genital.actOnHeartbeat(setup.lane());
            requireClose(0.0, genital.roundMagicDamageDealt(), "The first hit must not trigger magic damage.");
            genital.actOnHeartbeat(setup.lane());

            require(genital.roundMagicDamageDealt() > 0.0, "The second hit must trigger magic damage.");
            require(target.entity().activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION) > 0.0,
                    "The second hit must slow the target.");
            context.succeed();
        } finally {
            cleanup(setup, target == null ? List.of() : List.of(target));
        }
    }

    @GameTest(maxTicks = 120)
    public void genitalExtraAttackContinuesAfterPrimaryDies(GameTestHelper context) {
        TestSetup setup = setup(context, "body-genital-extra-owner");
        BodyTower genital = tower(BodyTowers.GENITAL_T2, setup.owner(), context, new BlockPos(7, 2, 7));
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            setup.lane().addTower(genital);
            SemionTowerEntity source = towerEntity(context, genital);
            targets.add(spawnTarget(context, setup.lane(), source.position().add(0.0, 0.0, 2.0),
                    "body-genital-extra-primary", 1.0, 1.0));
            targets.add(spawnTarget(context, setup.lane(), source.position().add(1.0, 0.0, 2.0),
                    "body-genital-extra-secondary", 100.0, 0.0));

            genital.actOnHeartbeat(setup.lane());

            require(targets.get(0).runtime().health() <= 0.0,
                    "The primary target must die from the first attack.");
            require(targets.get(1).runtime().health() < 100.0,
                    "The T2 extra attack must continue after the primary target dies.");
            context.succeed();
        } finally {
            cleanup(setup, targets);
        }
    }

    @GameTest(maxTicks = 120)
    public void eyeOnlyHitsAgainstLaneTravelDirection(GameTestHelper context) {
        TestSetup setup = setup(context, "body-eye-owner");
        BodyTower eye = tower(BodyTowers.EYE_T1, setup.owner(), context, new BlockPos(10, 2, 4));
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            setup.lane().addTower(eye);
            SemionTowerEntity source = towerEntity(context, eye);
            Vec3 direction = BodyTower.eyeDirection(setup.lane().laneLayout());
            targets.add(spawnTarget(context, setup.lane(), source.position().add(direction.scale(3.0)),
                    "body-eye-front", 100.0));
            targets.add(spawnTarget(context, setup.lane(), source.position().subtract(direction.scale(3.0)),
                    "body-eye-back", 100.0));

            eye.actOnHeartbeat(setup.lane());

            require(targets.get(0).runtime().health() < 100.0,
                    "The eye must hit enemies against the lane travel direction.");
            requireClose(100.0, targets.get(1).runtime().health(),
                    "The eye must not hit enemies along the lane travel direction.");
            context.succeed();
        } finally {
            cleanup(setup, targets);
        }
    }

    @GameTest(maxTicks = 120)
    public void eyeFacesIncomingEnemiesAtFinalDefense(GameTestHelper context) {
        TestSetup setup = setup(context, "body-eye-final-defense-owner");
        BodyTower heart = tower(BodyTowers.HEART_T1, setup.owner(), context, new BlockPos(6, 2, 6));
        BodyTower eye = tower(BodyTowers.EYE_T1, setup.owner(), context, new BlockPos(10, 2, 4));
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            setup.lane().addTower(heart);
            setup.lane().addTower(eye);
            setup.lane().markWaveStarted(1);
            heart.onLaneCleared(setup.lane());
            eye.onLaneCleared(setup.lane());
            setup.lane().moveTowersToFinalDefense();

            SemionTowerEntity source = towerEntity(context, eye);
            Vec3 direction = BodyTower.eyeDirection(setup.lane().laneLayout(), true);
            targets.add(spawnTarget(context, setup.lane(), source.position().add(direction.scale(3.0)),
                    "body-eye-final-front", 100.0));
            targets.add(spawnTarget(context, setup.lane(), source.position().subtract(direction.scale(3.0)),
                    "body-eye-final-back", 100.0));

            heart.tick(setup.lane());

            require(targets.get(0).runtime().health() < 100.0,
                    "The final-defense eye must hit enemies approaching the boss.");
            requireClose(100.0, targets.get(1).runtime().health(),
                    "The final-defense eye must not fire toward the boss.");
            context.succeed();
        } finally {
            cleanup(setup, targets);
        }
    }

    private static TestSetup augmentSetup(GameTestHelper context, String ownerSeed) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        prepareFloor(context, 7);
        UUID owner = stableUuid(ownerSeed);
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 6, 7));
        LaneRegionLayout layout = new LaneRegionLayout(
                1, Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 2)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 6))),
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(5, 2, 6)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        return new TestSetup(owner, lane);
    }

    private static TestSetup setup(GameTestHelper context, String ownerSeed) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        prepareFloor(context);
        UUID owner = stableUuid(ownerSeed);
        PlayerLane lane = testLane(context, owner);
        AreaEffectLaneIndex.register(lane);
        return new TestSetup(owner, lane);
    }

    private static BodyTower tower(TowerType type, UUID owner, GameTestHelper context, BlockPos position) {
        GridPosition grid = GridPosition.from(context.absolutePos(position));
        return new BodyTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, grid, grid);
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            Vec3 position,
            String id,
            double health
    ) {
        return spawnTarget(context, lane, position, id, health, 0.0);
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            Vec3 position,
            String id,
            double health,
            double laneProgress
    ) {
        Monster runtime = new Monster(
                id, TeamId.RED, 1, Optional.empty(), Optional.empty(), health, 0.0, 1.0,
                AttackKind.MELEE, "minecraft:zombie", 0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x, position.y, position.z);
        require(context.getLevel().addFreshEntity(entity), "Target monster must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        runtime.syncLaneProgress(laneProgress);
        lane.activeMonsters().add(runtime);
        return new SpawnedTarget(runtime, entity);
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, BodyTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(10, 2, 2)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(10, 2, 12))),
                BlockBounds.of(min, max),
                List.of(
                        GridPosition.from(context.absolutePos(new BlockPos(8, 2, 10))),
                        GridPosition.from(context.absolutePos(new BlockPos(10, 2, 10)))
                )
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static void prepareFloor(GameTestHelper context) {
        prepareFloor(context, 14);
    }

    private static void prepareFloor(GameTestHelper context, int max) {
        for (int x = 0; x <= max; x++) {
            for (int z = 0; z <= max; z++) {
                BlockPos floor = context.absolutePos(new BlockPos(x, 1, z));
                context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                context.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void cleanup(TestSetup setup, List<SpawnedTarget> targets) {
        targets.forEach(target -> target.entity().discard());
        setup.lane().clearTowers();
        AreaEffectLaneIndex.unregister(setup.lane());
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0E-6) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual);
        }
    }

    private record TestSetup(UUID owner, PlayerLane lane) {
    }

    private record SpawnedTarget(Monster runtime, SemionMonsterEntity entity) {
    }
}
