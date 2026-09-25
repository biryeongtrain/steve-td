package kim.biryeong.semiontd.tower.insect;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class InsectGameTest {
    @GameTest
    public void augmentHatchingKeepsLifePenaltiesAndBlocksOnlyOneRevivedHit(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = testLane(context, owner);
        GridPosition position = floor(context, 3, 2, 3);
        prepareFloor(context, position, floor(context, 2, 2, 3));
        lane.assignAugmentSnapshot(augmentSnapshot(InsectAugments.SHELL, InsectAugments.HATCH));
        InsectUnitTower bee = new InsectUnitTower(InsectTowers.BEE, owner, TeamId.RED, 1, position, position);
        try {
            lane.addTower(new InsectSpawnerTower(InsectTowers.SPAWNER, owner, TeamId.RED, 1,
                    floor(context, 2, 2, 3), floor(context, 2, 2, 3)));
            lane.addTower(bee);
            ArrayList<InsectUnitTower> neighbors = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                GridPosition at = floor(context, 3 + i, 2, 4);
                prepareFloor(context, at);
                InsectUnitTower unit = new InsectUnitTower(InsectTowers.SILVERFISH, owner, TeamId.RED, 1, at, at);
                neighbors.add(unit);
                lane.addTower(unit);
            }
            lane.markWaveStarted(1);
            double initialHealth = bee.currentMaxHealth();
            for (InsectUnitTower unit : neighbors) {
                towerEntity(context, unit).setHealth(0);
                require(!unit.isDestroyed(lane) && unit.reviveTicksRemaining() == 60, "Silverfish first wait must halve to sixty ticks.");
            }
            int[] expectedWaits = {20, 35, 50, 130};
            for (int life = 0; life < expectedWaits.length; life++) {
                towerEntity(context, bee).setHealth(0);
                require(!bee.isDestroyed(lane), "A linked bee must wait for revival.");
                require(bee.reviveTicksRemaining() == expectedWaits[life], "Only the first three delays must halve.");
                for (int tick = 0; tick < expectedWaits[life]; tick++) bee.tick(lane);
                require(close(bee.currentMaxHealth(), initialHealth * Math.pow(.95, life + 1)), "Health loss must remain cumulative.");
                require(close(bee.modifyIncomingDamage(null, null, 10), 0), "First revived hit must be blocked.");
                require(close(bee.modifyIncomingDamage(null, null, 10), 10 * (1 + .2 * (life + 1))),
                        "Second hit must retain existing revival damage penalty.");
                if (life == 0) {
                    require(neighbors.stream().filter(unit -> unit.reviveTicksRemaining() == 20).count() == 2,
                            "Exactly two nearby waiting insects must lose forty ticks.");
                    require(neighbors.stream().filter(unit -> unit.reviveTicksRemaining() == 60).count() == 1,
                            "The third nearby waiter must remain unchanged.");
                }
            }
            context.succeed();
        } finally {lane.clearTowers(); InsectAugments.clear(owner);}
    }

    @GameTest
    public void colonyUsesLivingOriginalAnchorsAndCancelsWhenAllDisappear(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = testLane(context, owner);
        GridPosition at = floor(context, 5, 2, 5), otherAt = floor(context, 6, 2, 5), spawnAt = floor(context, 4, 2, 5);
        prepareFloor(context, at, otherAt, spawnAt);
        lane.assignAugmentSnapshot(augmentSnapshot(InsectAugments.COLONY));
        InsectUnitTower unit = new InsectUnitTower(InsectTowers.SILVERFISH, owner, TeamId.RED, 1, at, at);
        InsectUnitTower anchor = new InsectUnitTower(InsectTowers.SILVERFISH, owner, TeamId.RED, 1, otherAt, otherAt);
        InsectSpawnerTower spawner = new InsectSpawnerTower(InsectTowers.SPAWNER, owner, TeamId.RED, 1, spawnAt, spawnAt);
        try {
            lane.addTower(spawner); lane.addTower(unit); lane.addTower(anchor);
            lane.markWaveStarted(1);
            towerEntity(context, unit).setHealth(0);
            require(!unit.isDestroyed(lane), "Unit must reserve its first revival.");
            lane.removeTower(spawner);
            require(!unit.isDestroyed(lane), "A living original must preserve revival after the spawner disappears.");
            towerEntity(context, anchor).setHealth(0);
            require(unit.isDestroyed(lane), "All dead anchors must cancel the pending revival without mutual recursion.");
            context.succeed();
        } finally {lane.clearTowers(); InsectAugments.clear(owner);}
    }

    @GameTest
    public void larvaeReserveSixPerPlayerAndKeepOnlyNativeExplosions(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        PlayerLane lane = testLane(context, owner);
        lane.assignAugmentSnapshot(augmentSnapshot(InsectAugments.MARCH, InsectAugments.COLONY));
        ArrayList<InsectUnitTower> originals = new ArrayList<>();
        AreaEffectLaneIndex.register(lane);
        SemionMonsterEntity target = null;
        try {
            for (int i = 0; i < 4; i++) {
                GridPosition at = floor(context, 3 + i, 2, 5);
                prepareFloor(context, at);
                InsectUnitTower unit = new InsectUnitTower(InsectTowers.BEE, owner, TeamId.RED, 1, at, at);
                lane.addTower(unit); originals.add(unit);
            }
            lane.markWaveStarted(1);
            double maxHealth = originals.getFirst().currentMaxHealth();
            for (InsectUnitTower unit : originals) {
                towerEntity(context, unit).setHealth(0);
                unit.isDestroyed(lane);
            }
            require(InsectAugments.reserved(owner) == 6, "Unflushed reservations must already consume the player round cap.");
            InsectAugments.flush(lane);
            List<InsectUnitTower> larvae = lane.towers().stream().filter(InsectUnitTower.class::isInstance)
                    .map(InsectUnitTower.class::cast).filter(InsectUnitTower::isLarva).toList();
            require(larvae.size() == 6, "Four original deaths may create only six larvae total.");
            InsectUnitTower larva = larvae.getFirst();
            require(close(larva.currentMaxHealth(), maxHealth * .4) && larva.slotWeight() == 0 && !larva.canBeSold(),
                    "Larvae must snapshot forty-percent health and remain temporary slot-free units.");
            SemionTowerEntity entity = towerEntity(context, larva);
            target = spawnTarget(context, lane, entity.position().add(1, 0, 0), 1000, 0, 1);
            larva.detonateOnContact(entity, target);
            require(!entity.isAlive() && close(target.runtimeMonster().health(), 1000 - maxHealth * .4 * .25),
                    "Bee larvae must retain contact-triggered native death explosion at their own health scale.");
            require(larva.reviveTicksRemaining() == -1, "Larvae must never reserve revival.");
            InsectAugments.flush(lane);
            require(InsectAugments.reserved(owner) == 6 && lane.towers().size() == 10,
                    "Larval death must neither reproduce nor refund its reservation.");
            lane.resetForRound();
            require(lane.towers().stream().noneMatch(tower -> tower instanceof InsectUnitTower unit && unit.isLarva()),
                    "Round cleanup must remove every larva.");
            context.succeed();
        } finally {if (target != null) target.discard(); lane.clearTowers(); InsectAugments.clear(owner); AreaEffectLaneIndex.unregister(lane);}
    }

    private static kim.biryeong.semiontd.augment.AugmentSnapshot augmentSnapshot(String... cards) {
        return new kim.biryeong.semiontd.augment.AugmentSnapshot(kim.biryeong.semiontd.augment.AugmentConfig.defaults(),
                java.util.Arrays.stream(cards).map(card -> new kim.biryeong.semiontd.augment.PlayerAugmentState.Selection(
                        5, kim.biryeong.semiontd.augment.AugmentRarity.GOLD, "semiontd:" + card,
                        kim.biryeong.semiontd.augment.PlayerAugmentState.Outcome.SELECTED, null,
                        kim.biryeong.semiontd.augment.AugmentChoice.none())).toList());
    }

    @GameTest
    public void freshUnitRevivesAtDeathPositionAndSpawnerLossCancelsNextRevival(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("insect-revival".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        GridPosition spawnerPosition = floor(context, 3, 2, 4);
        GridPosition unitPosition = floor(context, 5, 2, 4);
        prepareFloor(context, spawnerPosition, unitPosition);

        ProductionTower spawner = new ProductionTower(
                InsectTowers.SPAWNER, owner, TeamId.RED, 1, spawnerPosition, spawnerPosition);
        InsectUnitTower unit = new InsectUnitTower(
                InsectTowers.SILVERFISH, owner, TeamId.RED, 1, unitPosition, unitPosition);
        try {
            unit.recordPlacementEconomy(30, 1);
            lane.addTower(spawner);
            lane.addTower(unit);
            lane.markWaveStarted(1);
            require(unit.freshPowerActive(), "A newly placed T1 unit must receive first-wave power.");
            require(close(unit.currentMaxHealth(), 180.0), "Fresh Silverfish must have 200% max health.");

            towerEntity(context, unit).setHealth(0.0f);
            require(!unit.isDestroyed(lane), "A valid nearby spawner must suppress permanent death.");
            require(unit.reviveTicksRemaining() == 120, "First revival must take exactly six seconds.");
            for (int tick = 0; tick < 120; tick++) {
                unit.tick(lane);
            }
            require(unit.health() == unit.currentMaxHealth(), "Revival must restore full current-tier health.");
            require(unit.position().equals(unitPosition), "Revival must use the death position.");
            require(towerEntity(context, unit).isAlive(), "Revival must respawn the tower entity.");

            towerEntity(context, unit).setHealth(0.0f);
            require(!unit.isDestroyed(lane), "Second death must initially schedule another revival.");
            towerEntity(context, spawner).setHealth(0.0f);
            require(unit.isDestroyed(lane), "Destroying every linked spawner must cancel pending revival.");
            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void linkedSpawnersSurviveFinalDefenseWithoutSpeedingRevival(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("insect-final-defense".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        GridPosition firstSpawnerPosition = floor(context, 3, 2, 4);
        GridPosition secondSpawnerPosition = floor(context, 4, 2, 4);
        GridPosition unitPosition = floor(context, 5, 2, 4);
        prepareFloor(context, firstSpawnerPosition, secondSpawnerPosition, unitPosition);

        ProductionTower firstSpawner = new InsectSpawnerTower(
                InsectTowers.SPAWNER, owner, TeamId.RED, 1, firstSpawnerPosition, firstSpawnerPosition);
        ProductionTower secondSpawner = new InsectSpawnerTower(
                InsectTowers.SPAWNER, owner, TeamId.RED, 1, secondSpawnerPosition, secondSpawnerPosition);
        InsectUnitTower unit = new InsectUnitTower(
                InsectTowers.SILVERFISH, owner, TeamId.RED, 1, unitPosition, unitPosition);
        try {
            lane.addTower(firstSpawner);
            lane.addTower(secondSpawner);
            unit.recordPlacementEconomy(30, 1);
            lane.addTower(unit);
            lane.markWaveStarted(1);
            towerEntity(context, unit).setHealth(0.0f);
            require(!unit.isDestroyed(lane), "Either linked spawner must allow revival.");
            require(unit.reviveTicksRemaining() == 120, "Overlapping spawners must not shorten revival.");

            lane.moveTowersToFinalDefense();
            GridPosition finalPosition = unit.position();
            require(!finalPosition.equals(unitPosition), "Final defense must assign a new revival position.");
            require(!unit.isDestroyed(lane), "Original spawner links must survive final-defense movement.");
            for (int tick = 0; tick < 120; tick++) {
                unit.tick(lane);
            }
            require(unit.position().equals(finalPosition), "Revival must use the assigned final-defense slot.");
            require(towerEntity(context, unit).isAlive(), "Final-defense revival must respawn the unit.");

            towerEntity(context, unit).setHealth(0.0f);
            require(!unit.isDestroyed(lane), "The revived unit must retain unlimited revival identity.");
            unit.resetForRound(lane);
            require(unit.deathsThisRound() == 0 && unit.reviveTicksRemaining() == -1,
                    "Round reset must clear revival wait and death vulnerability.");
            require(close(unit.modifyIncomingDamage(null, null, 100.0), 100.0),
                    "Round reset must clear accumulated incoming-damage vulnerability: "
                            + unit.modifyIncomingDamage(null, null, 100.0));
            context.succeed();
        } catch (RuntimeException | AssertionError failure) {
            context.fail(Component.literal("Insect final-defense revival failed: " + failure));
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void spawnersAreIsolatedByOwner(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("insect-owner-a".getBytes(StandardCharsets.UTF_8));
        UUID otherOwner = UUID.nameUUIDFromBytes("insect-owner-b".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        GridPosition spawnerPosition = floor(context, 3, 2, 4);
        GridPosition unitPosition = floor(context, 5, 2, 4);
        prepareFloor(context, spawnerPosition, unitPosition);

        InsectSpawnerTower foreignSpawner = new InsectSpawnerTower(
                InsectTowers.SPAWNER, otherOwner, TeamId.RED, 1, spawnerPosition, spawnerPosition);
        InsectUnitTower unit = new InsectUnitTower(
                InsectTowers.SILVERFISH, owner, TeamId.RED, 1, unitPosition, unitPosition);
        try {
            lane.addTower(foreignSpawner);
            lane.addTower(unit);
            lane.markWaveStarted(1);
            towerEntity(context, unit).setHealth(0.0f);
            require(unit.isDestroyed(lane), "Another owner's spawner must not grant revival.");
            require(!unit.showDebugRevivalVfx(lane), "Cancelled or unlinked revival must not emit success VFX.");
            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void repeatedExplosionsUseDeathHealthMagicResistanceAndExactRadius(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("insect-explosion".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        GridPosition position = floor(context, 5, 2, 5);
        InsectUnitTower unit = new InsectUnitTower(InsectTowers.SILVERFISH, owner, TeamId.RED, 1, position, position);
        GridPosition spawnerPosition = floor(context, 3, 2, 5);
        ProductionTower spawner = new InsectSpawnerTower(InsectTowers.SPAWNER, owner, TeamId.RED, 1,
                spawnerPosition, spawnerPosition);
        ArrayList<SemionMonsterEntity> targets = new ArrayList<>();
        AreaEffectLaneIndex.register(lane);
        prepareFloor(context, position, spawnerPosition);
        try {
            unit.recordPlacementEconomy(30, 1);
            lane.addTower(spawner);
            lane.addTower(unit);
            lane.markWaveStarted(1);
            SemionTowerEntity source = towerEntity(context, unit);
            Vec3 center = source.position();
            SemionMonsterEntity near = spawnTarget(context, lane, center.add(1, 0, 0), 1000, 0, 1);
            SemionMonsterEntity boundary = spawnTarget(context, lane, center.add(2, 0, 0), 1000, 0, 1);
            SemionMonsterEntity outside = spawnTarget(context, lane, center.add(2.01, 0, 0), 1000, 0, 1);
            SemionMonsterEntity resistant = spawnTarget(context, lane, center.add(0, 0, 1), 1000, 100, 1);
            SemionMonsterEntity otherLane = spawnTarget(context, lane, center.add(0, 0, 2), 1000, 0, 2);
            SemionMonsterEntity lethal = spawnTarget(context, lane, center.add(-1, 0, 0), 40, 0, 1);
            targets.addAll(List.of(near, boundary, outside, resistant, otherLane, lethal));
            source.setHealth(0.0F);
            require(!unit.isDestroyed(lane), "A spawner must keep the dead unit registered for revival.");
            require(close(near.runtimeMonster().health(), 955), "Fresh T1 death must deal 45 magic damage.");
            require(close(boundary.runtimeMonster().health(), 955), "The radius boundary must take full damage.");
            require(close(outside.runtimeMonster().health(), 1000), "Beyond the radius must remain untouched.");
            require(close(resistant.runtimeMonster().health(), 977.5), "100 resistance must halve magic damage.");
            require(close(otherLane.runtimeMonster().health(), 1000), "An unrelated lane must remain untouched.");
            require(!lethal.runtimeMonster().isAlive(), "Explosion must kill through the normal damage pipeline.");
            require(owner.equals(lethal.runtimeMonster().lastHitPlayerId().orElse(null))
                            && lethal.runtimeMonster().lastHitSourceKind() == KillSourceKind.TOWER,
                    "Explosion kills must retain the tower owner for rewards.");
            require(close(unit.roundMagicDamageDealt(), 152.5), "Statistics must count applied magic damage, including capped lethal damage.");
            require(close(unit.roundPhysicalDamageDealt(), 0), "Explosion must not record physical damage.");
            for (int query = 0; query < 5; query++) unit.isDestroyed(lane);
            require(close(near.runtimeMonster().health(), 955), "Repeated death queries must not explode again.");
            for (int tick = 0; tick < 120; tick++) unit.tick(lane);
            source = towerEntity(context, unit);
            require(close(unit.currentMaxHealth(), 171), "First revival must retain 95% of fresh health.");
            source.applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, 0.5, 200);
            source.refreshMaxHealthEffects(false);
            source.refreshMaxHealthEffects(false);
            require(close(unit.currentMaxHealth(), 256.5), "Health refresh must apply first-wave and decay factors exactly once.");
            require(close(source.getMaxHealth(), 256.5), "Entity health must match the logical tower.");
            source.setHealth(0.0F);
            require(!unit.isDestroyed(lane), "Second death must schedule revival.");
            require(close(near.runtimeMonster().health(), 890.875), "Second explosion must use buffed health before the next decay.");
            require(unit.reviveTicksRemaining() == 180, "Second melee revival must take nine seconds.");
            for (int tick = 0; tick < 180; tick++) unit.tick(lane);
            require(close(unit.currentMaxHealth(), 162.45), "A removed corpse's temporary buff must not leak into the next entity.");
            unit.resetForRound(lane);
            require(close(unit.currentMaxHealth(), 90), "Round reset must clear fresh power and decay.");
            require(close(near.runtimeMonster().health(), 890.875), "Round reset must not cause an explosion.");
            context.succeed();
        } finally {
            targets.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void removalAndSpawnerDeathDoNotExplodeButUnlinkedUnitDeathDoes(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("insect-removal".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        GridPosition position = floor(context, 5, 2, 5);
        InsectUnitTower unit = new InsectUnitTower(InsectTowers.BEE, owner, TeamId.RED, 1, position, position);
        SemionMonsterEntity target = null;
        AreaEffectLaneIndex.register(lane);
        prepareFloor(context, position);
        try {
            lane.addTower(unit);
            lane.markWaveStarted(1);
            target = spawnTarget(context, lane, towerEntity(context, unit).position().add(1, 0, 0), 1000, 0, 1);
            unit.onRemoved(lane);
            require(close(target.runtimeMonster().health(), 1000), "Removal used by sales/upgrades must not explode.");
            unit.onPlaced(lane);
            towerEntity(context, unit).setHealth(0.0F);
            require(unit.isDestroyed(lane), "Without a spawner, death must be permanent.");
            require(close(target.runtimeMonster().health(), 987.5), "An unlinked bee must still explode when killed on approach.");
            unit.notifyDeath(lane);
            unit.isDestroyed(lane);
            require(close(target.runtimeMonster().health(), 987.5), "Final death notification must not repeat the explosion.");
            GridPosition spawnerPosition = floor(context, 4, 2, 5);
            InsectSpawnerTower spawner = new InsectSpawnerTower(InsectTowers.SPAWNER, owner, TeamId.RED, 1,
                    spawnerPosition, spawnerPosition);
            lane.addTower(spawner);
            towerEntity(context, spawner).setHealth(0.0F);
            require(spawner.isDestroyed(lane), "Spawner must die normally.");
            spawner.notifyDeath(lane);
            require(close(target.runtimeMonster().health(), 987.5), "Spawner death must not explode.");
            context.succeed();
        } finally {
            if (target != null) target.discard();
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest(maxTicks = 140)
    public void beeActuallyApproachesAndDetonatesWithoutRangedAttacks(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("insect-contact".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        GridPosition position = floor(context, 4, 1, 4);
        for (int x = 1; x <= 12; x++) {
            for (int z = 1; z <= 12; z++) prepareFloor(context, floor(context, x, 1, z));
        }
        InsectUnitTower unit = new InsectUnitTower(InsectTowers.BEE, owner, TeamId.RED, 1, position, position);
        AreaEffectLaneIndex.register(lane);
        unit.recordPlacementEconomy(40, 1);
        lane.addTower(unit);
        lane.markWaveStarted(1);
        SemionTowerEntity source = towerEntity(context, unit);
        Vec3 start = source.position();
        SemionMonsterEntity target = spawnTarget(context, lane, start.add(4, 0, 0), 1000, 0, 1);
        try {
            source.applyTimedEffect(TimedEffectType.TOWER_FLAT_RANGE_BONUS, 10.0, 200);
            source.applyTimedEffect(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS, 100.0, 200);
            new TowerAttackMonsterGoal(source).tick();
            require(source.isAlive() && close(target.runtimeMonster().health(), 1000),
                    "Even range and damage buffs must not produce a ranged attack or distant detonation.");
        } catch (RuntimeException | AssertionError failure) {
            target.discard();
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            throw failure;
        }
        context.runAtTickTime(100, () -> {
            try {
                require(!source.isAlive(), "The real movement goal must reach the target and detonate.");
                require(source.position().distanceToSqr(start) > 1, "Bee must actually approach before detonating.");
                require(source.distanceToSqr(target) <= 1.5 * 1.5, "Contact range must stay 1.5 despite range buffs.");
                require(close(target.runtimeMonster().health(), 975), "Contact must deal only the 25-point death explosion; health="
                        + target.runtimeMonster().health() + ", maxHealth=" + unit.currentMaxHealth()
                        + ", magic=" + unit.roundMagicDamageDealt());
                require(close(unit.roundPhysicalDamageDealt(), 0), "A buffed bee must never deal primary attack damage.");
                require(close(unit.roundMagicDamageDealt(), 25), "Contact must use the shared death explosion once.");
                context.succeed();
            } catch (RuntimeException | AssertionError failure) {
                context.fail(Component.literal("Bee contact movement failed: " + failure));
            } finally {
                target.discard();
                lane.clearTowers();
                AreaEffectLaneIndex.unregister(lane);
            }
        });
    }

    @GameTest
    public void finalDefenseBeeWaitsForContactAndRevivesAtItsAssignedSlot(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("insect-contact-final".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        GridPosition position = floor(context, 5, 2, 5);
        GridPosition spawnerPosition = floor(context, 3, 2, 5);
        prepareFloor(context, position, spawnerPosition);
        InsectUnitTower bee = new InsectUnitTower(InsectTowers.BEE, owner, TeamId.RED, 1, position, position);
        lane.addTower(new InsectSpawnerTower(InsectTowers.SPAWNER, owner, TeamId.RED, 1, spawnerPosition, spawnerPosition));
        lane.addTower(bee);
        lane.markWaveStarted(1);
        AreaEffectLaneIndex.register(lane);
        SemionMonsterEntity target = null;
        try {
            lane.moveTowersToFinalDefense();
            GridPosition assigned = bee.position();
            SemionTowerEntity source = towerEntity(context, bee);
            Vec3 center = source.position();
            target = spawnTarget(context, lane, center.add(4, 0, 0), 1000, 0, 1);
            TowerAttackMonsterGoal goal = new TowerAttackMonsterGoal(source);
            goal.tick();
            require(source.isAlive() && source.getNavigation().isDone(), "Final-defense bee must not chase a distant target.");
            require(close(target.runtimeMonster().health(), 1000), "Final-defense bee must not fire at range.");
            target.setPos(center.add(1.51, 0, 0));
            goal.tick();
            require(source.isAlive(), "Outside the contact boundary must not detonate.");
            target.setPos(center.add(1.5, 0, 0));
            goal.tick();
            require(!source.isAlive(), "Contact at the boundary must detonate.");
            require(bee.reviveTicksRemaining() == 40, "First bee revival must take two seconds.");
            for (int tick = 0; tick < 40; tick++) bee.tick(lane);
            require(bee.position().equals(assigned), "Revived bee must retain its final-defense slot.");
            require(close(bee.currentMaxHealth(), 47.5), "Bee revival must apply five-percent health decay.");
            context.succeed();
        } finally {
            if (target != null) target.discard();
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void allNineUnitsUseTieredExplosionDamageAndRadius(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        for (TowerType type : InsectTowers.all().stream().filter(InsectTowers::isCombatUnit).toList()) {
            UUID owner = UUID.nameUUIDFromBytes(type.id().getBytes(StandardCharsets.UTF_8));
            PlayerLane lane = testLane(context, owner);
            GridPosition position = floor(context, 5, 2, 5);
            prepareFloor(context, position);
            InsectUnitTower unit = new InsectUnitTower(type, owner, TeamId.RED, 1, position, position);
            ArrayList<SemionMonsterEntity> targets = new ArrayList<>();
            AreaEffectLaneIndex.register(lane);
            try {
                unit.recordPlacementEconomy(type.mineralCost(), 1);
                lane.addTower(unit);
                lane.markWaveStarted(1);
                SemionTowerEntity source = towerEntity(context, unit);
                int tier = InsectTowers.tier(type);
                double ratio = switch (tier) {
                    case 1 -> 0.25;
                    case 2 -> 0.4;
                    default -> 0.5;
                };
                double radius = tier == 1 ? 2 : 3;
                double expected = type.maxHealth() * (tier == 1 ? 2 : 1) * ratio;
                SemionMonsterEntity near = spawnTarget(context, lane, source.position().add(1, 0, 0), 1000, 0, 1);
                SemionMonsterEntity edge = spawnTarget(context, lane, source.position().add(radius, 0, 0), 1000, 0, 1);
                SemionMonsterEntity outside = spawnTarget(context, lane, source.position().add(radius + 0.01, 0, 0), 1000, 0, 1);
                targets.addAll(List.of(near, edge, outside));
                source.setHealth(0.0F);
                require(unit.isDestroyed(lane), "An unlinked unit must die permanently: " + type.id());
                require(close(near.runtimeMonster().health(), 1000 - expected), "Wrong tier damage: " + type.id());
                require(close(edge.runtimeMonster().health(), 1000 - expected), "Boundary must take full tier damage: " + type.id());
                require(close(outside.runtimeMonster().health(), 1000), "Outside tier radius must remain untouched: " + type.id());
                require(close(unit.roundMagicDamageDealt(), expected * 2), "Tier damage must reach statistics: " + type.id());
            } finally {
                targets.forEach(SemionMonsterEntity::discard);
                lane.clearTowers();
                AreaEffectLaneIndex.unregister(lane);
            }
        }
        context.succeed();
    }

    private static SemionMonsterEntity spawnTarget(
            GameTestHelper context, PlayerLane lane, Vec3 position, double health, double resistance, int laneId
    ) {
        Monster runtime = new Monster("insect-target", TeamId.RED, laneId, Optional.empty(), Optional.empty(),
                health, 100.0, 0.0, AttackKind.MELEE, "minecraft:zombie", null, DamageType.PHYSICAL,
                resistance, null, List.of(), 1L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "Test monster must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return entity;
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, kim.biryeong.semiontd.tower.EntityBackedTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 13))),
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static GridPosition floor(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void prepareFloor(GameTestHelper context, GridPosition... positions) {
        for (GridPosition position : positions) {
            context.getLevel().setBlock(
                    new BlockPos(position.x(), position.y(), position.z()),
                    Blocks.STONE.defaultBlockState(), 3);
            context.getLevel().setBlock(
                    new BlockPos(position.x(), position.y() + 1, position.z()),
                    Blocks.AIR.defaultBlockState(), 3);
            context.getLevel().setBlock(
                    new BlockPos(position.x(), position.y() + 2, position.z()),
                    Blocks.AIR.defaultBlockState(), 3);
            context.getLevel().setBlock(
                    new BlockPos(position.x(), position.y() + 3, position.z()),
                    Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 0.0001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
