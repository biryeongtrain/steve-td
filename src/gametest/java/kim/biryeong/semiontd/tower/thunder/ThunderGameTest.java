package kim.biryeong.semiontd.tower.thunder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.LaneFollowGoal;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class ThunderGameTest {
    @GameTest(maxTicks = 120)
    public void chainUsesResolvedDamageAfterThePrimaryDies(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("thunder-chain-owner");
        PlayerLane lane = testLane(context, owner);
        ThunderTower squirrel = tower(ThunderTowers.SQUIRREL_T3, owner, context, new BlockPos(4, 2, 4));
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        AreaEffectLaneIndex.register(lane);
        prepareFloor(context);
        try {
            lane.addTower(squirrel);
            SemionTowerEntity source = towerEntity(context, squirrel);
            Vec3 center = source.position().add(0.0, 0.0, 2.0);
            SpawnedTarget primary = spawnTarget(context, lane, center, "thunder-primary", 100.0);
            targets.add(primary);
            int chainTargets = ThunderBalance.chainTargets(squirrel.type().id());
            for (int index = 0; index <= chainTargets; index++) {
                targets.add(spawnTarget(context, lane, center.add(0.4 + index * 0.35, 0.0, 0.2),
                        "thunder-chain-" + index, 100.0));
            }

            primary.runtime().syncHealth(0.0);
            primary.entity().discard();
            squirrel.onAttackResolved(source, primary.entity(), 100.0, 100.0, 100.0, true);

            List<SpawnedTarget> hit = targets.subList(1, targets.size()).stream()
                    .filter(target -> target.runtime().health() < 100.0)
                    .toList();
            require(hit.size() == chainTargets, "The chain must respect the configured additional target cap.");
            for (SpawnedTarget target : hit) {
                requireClose(52.0, target.runtime().health(), "Chain damage must be 48% of resolved damage.");
            }
            requireClose(48.0 * chainTargets, squirrel.roundPhysicalDamageDealt(),
                    "Chain damage statistics must record only applied secondary damage.");

            SpawnedTarget lethal = hit.getFirst();
            lethal.runtime().syncHealth(30.0);
            lethal.entity().setHealth(30.0F);
            squirrel.onAttackResolved(source, primary.entity(), 100.0, 100.0, 100.0, true);
            require(!lethal.runtime().isAlive(), "A lethal chain hit must kill through the shared damage path.");
            require(owner.equals(lethal.runtime().lastHitPlayerId().orElse(null))
                            && lethal.runtime().lastHitSourceKind() == KillSourceKind.TOWER,
                    "Chain damage must retain tower owner and kill attribution.");
            context.succeed();
        } catch (AssertionError | RuntimeException failure) {
            context.fail(Component.literal("Thunder chain GameTest failed: " + failure));
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest(maxTicks = 120)
    public void stunImmunityIsSharedPerOwnerButNotAcrossOwners(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("thunder-stun-owner");
        UUID other = stableUuid("thunder-stun-other");
        PlayerLane lane = testLane(context, owner);
        ThunderTower first = tower(ThunderTowers.ARMADILLO_T1, owner, context, new BlockPos(3, 2, 3));
        ThunderTower second = tower(ThunderTowers.ARMADILLO_T1, owner, context, new BlockPos(4, 2, 3));
        ThunderTower otherOwner = tower(ThunderTowers.ARMADILLO_T1, other, context, new BlockPos(5, 2, 3));
        prepareFloor(context);
        SpawnedTarget target = null;
        try {
            lane.addTower(first);
            lane.addTower(second);
            lane.addTower(otherOwner);
            target = spawnTarget(context, lane, towerEntity(context, first).position().add(0.0, 0.0, 2.0),
                    "thunder-stun-target", 500.0);

            first.onAttackResolved(towerEntity(context, first), target.entity(), 1.0, 1.0, 1.0, false);
            ResourceLocation ownerImmunity = immunitySource(owner);
            require(target.entity().hasTimedEffectSource(TimedEffectType.MONSTER_STUN_IMMUNITY, ownerImmunity),
                    "The first stun must add the owner's shared immunity.");
            require(target.entity().isStunned(), "Electric shock must apply the shared stun state.");
            require(target.entity().activeTimedEffectTicks(TimedEffectType.MONSTER_STUN) == 20,
                    "The configured stun must last 20 ticks.");
            requireClose(0.0, target.entity().activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Stun must not masquerade as a slow.");

            second.onAttackResolved(towerEntity(context, second), target.entity(), 1.0, 1.0, 1.0, false);
            require(target.entity().activeTimedEffectTicks(TimedEffectType.MONSTER_STUN_IMMUNITY) == 50,
                    "A second tower from the same owner must not refresh the immunity.");

            otherOwner.onAttackResolved(towerEntity(context, otherOwner), target.entity(), 1.0, 1.0, 1.0, false);
            require(target.entity().hasTimedEffectSource(
                            TimedEffectType.MONSTER_STUN_IMMUNITY, immunitySource(other)),
                    "A different owner must keep an independent stun immunity source.");
            context.succeed();
        } catch (AssertionError | RuntimeException failure) {
            context.fail(Component.literal("Thunder stun immunity GameTest failed: " + failure));
        } finally {
            if (target != null) {
                target.entity().discard();
            }
            lane.clearTowers();
        }
    }

    @GameTest
    public void stunBlocksMovementAndAttacksButSlowDoesNotBlockAttacks(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("shared-stun-defense-owner");
        PlayerLane lane = testLane(context, owner);
        ThunderTower defender = tower(ThunderTowers.ARMADILLO_T1, owner, context, new BlockPos(4, 2, 4));
        prepareFloor(context);
        SpawnedTarget target = null;
        try {
            lane.addTower(defender);
            SemionTowerEntity defense = towerEntity(context, defender);
            target = spawnTarget(context, lane, defense.position().add(0.0, 0.0, 1.0), "shared-stun-attacker", 500.0);
            SemionMonsterEntity monster = target.entity();
            monster.setNoGravity(true);
            monster.setSpeed(1.0F);
            Vec3 start = monster.position();
            monster.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1.0, 2);
            monster.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, 1.0, 30);

            LaneFollowGoal movement = new LaneFollowGoal(monster, 1.0);
            movement.start();
            movement.tick();
            require(monster.getNavigation().isDone(), "Stun must stop lane navigation.");
            monster.setDeltaMovement(Vec3.ZERO);
            monster.travel(new Vec3(1.0, 0.0, 0.0));
            requireClose(0.0, monster.position().distanceToSqr(start), "Stun must suppress movement input.");
            monster.setOnGround(true);
            monster.getJumpControl().jump();
            monster.getJumpControl().tick();
            monster.aiStep();
            // This fixture disables AI, so aiStep does not perform its usual travel step.
            monster.travel(Vec3.ZERO);
            requireClose(start.y, monster.getY(), "Stun must also suppress voluntary jumps.");

            monster.setTarget(defense);
            MonsterAttackTargetGoal attack = new MonsterAttackTargetGoal(monster, 1.0);
            double before = defender.health();
            attack.tick();
            requireClose(before, defender.health(), "A stunned monster must not attack.");
            monster.aiStep();
            require(!monster.isStunned(), "Stun must expire through the entity tick.");
            require(monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION) > 0.0,
                    "The independent slow must remain after stun expires.");
            attack.tick();
            require(defender.health() < before, "A slow alone must not block an in-range attack.");

            monster.setDeltaMovement(Vec3.ZERO);
            monster.setSpeed(1.0F);
            monster.travel(new Vec3(1.0, 0.0, 0.0));
            require(monster.position().distanceToSqr(start) > 0.0, "Movement input must resume after stun expires.");
            monster.setOnGround(true);
            monster.getJumpControl().jump();
            monster.getJumpControl().tick();
            monster.aiStep();
            monster.travel(Vec3.ZERO);
            require(monster.getY() > start.y, "Voluntary jumps must resume after stun expires.");
            context.succeed();
        } catch (AssertionError | RuntimeException failure) {
            context.fail(Component.literal("Shared stun movement GameTest failed: " + failure));
        } finally {
            if (target != null) target.entity().discard();
            lane.clearTowers();
        }
    }

    @GameTest
    public void stunBlocksHealingWhileCooldownRunsWithoutCatchUp(GameTestHelper context) {
        UUID owner = stableUuid("shared-stun-healer-owner");
        PlayerLane lane = testLane(context, owner);
        Vec3 position = Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 4)));
        SpawnedTarget caster = spawnTarget(context, lane, position, "shared-stun-healer", 500.0);
        SpawnedTarget ally = spawnTarget(context, lane, position.add(1.0, 0.0, 0.0), "shared-stun-patient", 500.0);
        try {
            ally.runtime().syncHealth(100.0);
            ally.entity().setHealth(100.0F);
            caster.entity().setNoGravity(true);
            var healing = new AreaAllyHealGoal<>(caster.entity(), SemionMonsterEntity.class, 6.0, 10.0, 1, 4, 1);
            caster.entity().applyTimedEffect(TimedEffectType.MONSTER_STUN, 1.0, 2);
            healing.tick();
            requireClose(100.0, ally.runtime().health(), "Stun must block even the first ready heal.");
            caster.entity().aiStep();
            caster.entity().aiStep();
            healing.tick();
            requireClose(110.0, ally.runtime().health(), "A ready heal must be available after recovery.");

            caster.entity().applyTimedEffect(TimedEffectType.MONSTER_STUN, 1.0, 8);
            for (int tick = 0; tick < 8; tick++) {
                healing.tick();
                caster.entity().aiStep();
            }
            requireClose(110.0, ally.runtime().health(), "No heal may execute during stun.");
            healing.tick();
            requireClose(120.0, ally.runtime().health(), "Cooldown must finish during stun, allowing one heal after recovery.");
            healing.tick();
            requireClose(120.0, ally.runtime().health(), "Missed casts must not accumulate into a catch-up burst.");
            context.succeed();
        } finally {
            caster.entity().discard();
            ally.entity().discard();
        }
    }

    private static ResourceLocation immunitySource(UUID owner) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "thunder_stun/" + owner);
    }

    private static ThunderTower tower(
            kim.biryeong.semiontd.tower.TowerType type,
            UUID owner,
            GameTestHelper context,
            BlockPos position
    ) {
        return new ThunderTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1,
                GridPosition.from(context.absolutePos(position)));
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            Vec3 position,
            String id,
            double health
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
        lane.activeMonsters().add(runtime);
        return new SpawnedTarget(runtime, entity);
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, ThunderTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12))),
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static void prepareFloor(GameTestHelper context) {
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                BlockPos floor = context.absolutePos(new BlockPos(x, 1, z));
                context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                context.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
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

    private record SpawnedTarget(Monster runtime, SemionMonsterEntity entity) {
    }
}
