package kim.biryeong.semiontd.tower.legion;

import java.util.Arrays;
import java.util.List;
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
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class LegionAugmentGameTest {
    @GameTest
    public void factoryFourInitialClonesMakeEightChildrenAndStopAtThirdGeneration(GameTestHelper context) {
        factoryBudget(context, 4, 8);
    }

    @GameTest
    public void factoryEightInitialClonesShareTwelveReservationsEvenAfterDeath(GameTestHelper context) {
        factoryBudget(context, 8, 12);
    }

    private static void factoryBudget(GameTestHelper context, int initial, int expected) {
        PlayerLane lane = lane(context, LegionAugments.FACTORY);
        Fixture source = new Fixture(lane, position(context, 3, 2, 4), initial);
        lane.addTower(source);
        lane.markWaveStarted(5);
        try {
            drainQueue();
            require(LegionAugments.clones(lane).size() == initial, "Initial clones remain outside the additional budget");
            SemionMonsterEntity target = monster(context, lane, 5, 4);
            for (SemionTowerEntity clone : LegionAugments.clones(lane)) attack(clone, target);
            require(LegionAugments.additionalSpawns(lane) == initial, "Reservations consume budget before spawn");
            require(LegionAugments.clones(lane).size() == initial, "An attack queues only one generation");
            drainQueue();
            require(LegionAugments.clones(lane).stream().filter(clone -> Math.abs(clone.runtimeTower().type().damage() - 80) < .001).count() == initial,
                    "Second generation snapshots eighty percent of each parent");
            for (SemionTowerEntity clone : LegionAugments.clones(lane)) attack(clone, target);
            require(LegionAugments.additionalSpawns(lane) == expected, "Third generation shares the per-player twelve budget");
            drainQueue();
            int total = LegionAugments.clones(lane).size();
            require(total == initial + expected, "Budget counts additional clones rather than total clones");
            for (SemionTowerEntity clone : LegionAugments.clones(lane)) attack(clone, target);
            drainQueue();
            require(LegionAugments.clones(lane).size() == total, "Third generation never reproduces");
            LegionAugments.clones(lane).getLast().discard();
            LegionAugments.tick(lane);
            for (SemionTowerEntity clone : LegionAugments.clones(lane)) attack(clone, target);
            drainQueue();
            require(LegionAugments.additionalSpawns(lane) == expected, "Death never refunds spawn budget");
            source.syncHealth(0);
            LegionAugments.tick(lane);
            require(!LegionAugments.clones(lane).isEmpty(), "Combat death preserves children until round end");
            source.moveToFinalDefense(lane, position(context, 6, 2, 6));
            require(LegionAugments.clones(lane).stream().allMatch(clone -> clone.runtimeTower().deployedAtFinalDefense()),
                    "Children follow final defense after the body dies");
            LegionAugments.clear(lane);
            require(LegionAugments.clones(lane).isEmpty(), "Round cleanup removes every child");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            LegionAugments.clear(lane);
            IllusionCloneSpawnQueue.clear();
        }
    }

    @GameTest
    public void mergedFactoryPreservesEveryMaterialGenerationAndAddsSplash(GameTestHelper context) {
        PlayerLane lane = lane(context, LegionAugments.MERGE, LegionAugments.FACTORY);
        AreaEffectLaneIndex.register(lane);
        Fixture source = new Fixture(lane, position(context, 3, 2, 4), 2);
        lane.addTower(source);
        lane.markWaveStarted(5);
        try {
            drainQueue();
            require(LegionAugments.clones(lane).size() == 1, "Same-type clones merge immediately");
            SemionTowerEntity merged = LegionAugments.clones(lane).getFirst();
            requireClose(200, merged.runtimeTower().currentMaxHealth(), "Merged maximum health");
            requireClose(200, merged.getHealth(), "Merged current health");
            requireClose(200, merged.attackDamageAmount(null), "Merged attack");
            requireClose(1.10, merged.runtimeTower().visual().scale(), "Two materials increase scale ten percent");
            require(!merged.usesSharedAttackTarget(), "Merged clone targets independently");
            SemionMonsterEntity target = monster(context, lane, 4, 4);
            SemionMonsterEntity nearby = monster(context, lane, 5, 4);
            SemionMonsterEntity far = monster(context, lane, 7, 4);
            attack(merged, target);
            requireClose(4_950, nearby.getHealth(), "Merged attack adds twenty-five percent splash within two blocks");
            requireClose(5_000, far.getHealth(), "Splash preserves its two-block radius");
            require(LegionAugments.additionalSpawns(lane) == 2, "Two merged materials reserve two children");
            drainQueue();
            require(LegionAugments.clones(lane).size() == 1, "Children merge into their existing type immediately");
            requireClose(360, merged.attackDamageAmount(null), "Two hundred plus two eighty-damage children");
            attack(merged, target);
            drainQueue();
            require(LegionAugments.additionalSpawns(lane) == 4, "The next attack reproduces only the two new second-generation materials");
            requireClose(488, merged.attackDamageAmount(null), "Two sixty-four-damage grandchildren");
            attack(merged, target);
            drainQueue();
            require(LegionAugments.additionalSpawns(lane) == 4, "Merged third-generation materials have no rights left");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            AreaEffectLaneIndex.unregister(lane);
            LegionAugments.clear(lane);
            IllusionCloneSpawnQueue.clear();
        }
    }

    @GameTest
    public void trickShowPreventsLethalOnceAndCharismaDoesNotChargeFromExtraAttacks(GameTestHelper context) {
        PlayerLane lane = lane(context, LegionAugments.TRICK, LegionAugments.CHARISMA);
        Fixture source = new Fixture(lane, position(context, 3, 2, 4), 2);
        lane.addTower(source);
        lane.markWaveStarted(5);
        try {
            drainQueue();
            SemionTowerEntity body = source.runtimeEntity(lane).orElseThrow();
            SemionTowerEntity clone = LegionAugments.clones(lane).getFirst();
            SemionMonsterEntity target = monster(context, lane, 5, 4);
            for (int attack = 0; attack < 4; attack++) attack(body, target);
            AugmentCombat.runWithoutTriggers(() -> attack(body, target));
            requireClose(100, clone.attackDamageAmount(target), "An extra attack must not charge charisma");
            attack(body, target);
            requireClose(300, clone.attackDamageAmount(target), "The fifth native attack empowers each clone to three hundred percent");
            attack(clone, target);
            requireClose(100, clone.attackDamageAmount(target), "Empowerment lasts one successful attack");
            source.syncHealth(60);
            body.setHealth(60);
            body.hurtIgnoringReductions(body.damageSources().generic(), 500);
            requireClose(120, body.getHealth(), "Lethal damage is cancelled and thirty percent maximum health is added to pre-hit health");
            require(LegionAugments.clones(lane).size() == 1, "Trick show sacrifices one own clone");
            body.hurtIgnoringReductions(body.damageSources().generic(), 500);
            require(!body.isAlive(), "Each original can save itself only once per round");
            require(LegionAugments.clones(lane).size() == 1, "Combat death preserves remaining clones");
            lane.removeTower(source);
            require(LegionAugments.clones(lane).isEmpty(), "Permanent removal removes linked clones");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            LegionAugments.clear(lane);
            IllusionCloneSpawnQueue.clear();
        }
    }

    @GameTest
    public void mergePreservesOtherBodiesWhenOneSourceIsRemovedAndFactoryChoosesInvestment(GameTestHelper context) {
        PlayerLane lane = lane(context, LegionAugments.MERGE, LegionAugments.FACTORY);
        Fixture cheap = new Fixture(lane, position(context, 3, 2, 4), 1);
        Fixture expensive = new Fixture(lane, position(context, 4, 2, 4), 1);
        cheap.recordPlacementEconomy(100, 1);
        expensive.recordPlacementEconomy(400, 1);
        lane.addTower(cheap);
        lane.addTower(expensive);
        lane.markWaveStarted(5);
        try {
            drainQueue();
            SemionTowerEntity merged = LegionAugments.clones(lane).getFirst();
            requireClose(200, merged.attackDamageAmount(null), "Different bodies of the same type merge");
            attack(merged, monster(context, lane, 5, 4));
            require(LegionAugments.additionalSpawns(lane) == 1, "Only the highest-investment body's material reproduces");
            drainQueue();
            lane.removeTower(cheap);
            require(LegionAugments.clones(lane).size() == 1, "Removing one original retains other originals' merged material");
            requireClose(180, merged.attackDamageAmount(null), "Only the removed body's one hundred attack is subtracted");
            lane.removeTower(expensive);
            require(LegionAugments.clones(lane).isEmpty(), "Removing the last linked original clears the merged clone");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            LegionAugments.clear(lane);
            IllusionCloneSpawnQueue.clear();
        }
    }

    private static void attack(SemionTowerEntity entity, SemionMonsterEntity target) {
        double damage = entity.attackDamageAmount(target);
        entity.recordAttack(target, damage, damage, 1, false);
    }

    private static void drainQueue() {
        for (int tick = 0; tick <= TowerBalanceRuntime.illusionCloneSpawnSpreadTicks(); tick++) {
            IllusionCloneSpawnQueue.tick();
        }
    }

    private static PlayerLane lane(GameTestHelper context, String... cards) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        IllusionCloneSpawnQueue.clear();
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 5, 7));
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.atCenterOf(min),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 4)))), Vec3.atCenterOf(max),
                BlockBounds.of(min, max), List.of(position(context, 6, 2, 6)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards)
                .map(card -> new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, card,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()));
        return lane;
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, int x, int z) {
        Monster monster = new Monster("legion-augment-" + x + "-" + z, TeamId.RED, 1, Optional.empty(), Optional.empty(),
                5_000, 0, 10, AttackKind.MELEE, "minecraft:zombie", 0);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        GridPosition position = position(context, x, 2, z);
        entity.setPos(position.x() + .5, position.y() + 1, position.z() + .5);
        require(context.getLevel().addFreshEntity(entity), "Fixture monster must spawn");
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < .001, message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture extends IllusionSummonerTower {
        private final int count;

        Fixture(PlayerLane lane, GridPosition position, int count) {
            super(new TowerType("legion_augment_fixture", "Legion fixture", TowerCategory.DIRECT, 100,
                    200, 20, 200, 20, 10), lane.ownerPlayer(), TeamId.RED, 1, position);
            this.count = count;
        }

        @Override
        protected IllusionProfile illusionProfile(PlayerLane lane) {
            return new IllusionProfile(count, 0, .5, .5, 1, 1, 1, 1);
        }
    }
}
