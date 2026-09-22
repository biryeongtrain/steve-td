package kim.biryeong.semiontd.tower.undead;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
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
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class UndeadAugmentsGameTest {
    @GameTest
    public void boneChargeHitsOnlyThreeOtherEnemiesForHalfPhysicalDamageWithoutRetriggering(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            EntityBackedTower skeleton = tower(lane, UndeadTowers.T2_MELEE_TOWER, context, 5);
            select(skeleton, UndeadAugments.BONES);
            SemionTowerEntity source = skeleton.runtimeEntity(lane).orElseThrow();
            source.setNoAi(true);
            source.setNoGravity(true);
            Monster death = new Monster("bone_charge", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                    100, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0);
            int cap = TowerBalanceRuntime.abilityInt(skeleton.type().id(), "stackCap");
            for (int i = 0; i < cap + 10; i++) skeleton.onNearbyMonsterDeath(lane, death, source.position());
            SemionMonsterEntity primary = monster(context, lane, source.position().add(2.5, 0, 0), 1);
            List<SemionMonsterEntity> extras = List.of(
                    monster(context, lane, source.position().add(-2, 0, 0), 1),
                    monster(context, lane, source.position().add(0, 0, 2.2), 1),
                    monster(context, lane, source.position().add(0, 0, -2.4), 1));
            SemionMonsterEntity fourth = monster(context, lane, source.position().add(-2, 0, -2.1), 1);
            SemionMonsterEntity outside = monster(context, lane, source.position().add(-3.2, 0, 3.2), 1);
            SemionMonsterEntity otherLane = monster(context, lane, source.position().add(.1, 0, .1), 2);
            double damage = source.attackDamageAmount(primary);
            skeleton.markWaveStarted(5);
            source.recordAttack(primary, damage, 0, 0, false);
            require(extras.stream().allMatch(target -> close(target.runtimeMonster().health(), 10_000)),
                    "A zero-damage attack must not spend a bone charge or hit another enemy.");
            attack(source, primary);
            require(close(primary.runtimeMonster().health(), 10_000 - damage), "The primary is not a bone extra target.");
            require(extras.stream().allMatch(target -> close(target.runtimeMonster().health(), 10_000 - damage * .5)),
                    "Exactly three other enemies each take half of the skeleton's current attack.");
            require(close(skeleton.roundPhysicalDamageDealt(), damage * 2.5) && close(skeleton.roundMagicDamageDealt(), 0),
                    "The primary and all three bone attacks must record physical damage only.");
            AugmentCombat.additionalAttack(source, primary, 1);
            require(extras.stream().allMatch(target -> close(target.runtimeMonster().health(), 10_000 - damage * .5)),
                    "An augment extra attack must not spend the second charge.");
            attack(source, primary);
            require(extras.stream().allMatch(target -> close(target.runtimeMonster().health(), 10_000 - damage)),
                    "The next native attack consumes the one remaining charge without recursive shots.");
            attack(source, primary);
            require(extras.stream().allMatch(target -> close(target.runtimeMonster().health(), 10_000 - damage)),
                    "An attack without a charge must not add bone shots.");
            require(close(fourth.runtimeMonster().health(), 10_000), "A fourth eligible enemy exceeds the three-target cap.");
            require(close(outside.runtimeMonster().health(), 10_000), "Bone shots cannot exceed the skeleton's attack range.");
            require(close(otherLane.runtimeMonster().health(), 10_000), "Bone shots cannot cross lanes.");
            context.succeed();
        } finally {
            for (Monster monster : lane.activeMonsters()) {
                var entity = lane.arenaWorld().getEntity(monster.minecraftEntityId());
                if (entity != null) entity.discard();
            }
            lane.activeMonsters().clear();
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void actualOverflowHealsOnlyLowestNearbyAllyAndExtraAttacksDoNotDonate(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            EntityBackedTower donor = tower(lane, UndeadTowers.T2_ZOMBIE_TOWER, context, 3);
            EntityBackedTower lowest = tower(lane, UndeadTowers.T2_ZOMBIE_TOWER, context, 4);
            EntityBackedTower other = tower(lane, UndeadTowers.T2_ZOMBIE_TOWER, context, 5);
            select(donor, UndeadAugments.DONATION);
            lowest.syncHealth(20);
            lowest.onStateChanged(lane);
            other.syncHealth(40);
            other.onStateChanged(lane);
            var source = donor.runtimeEntity(lane).orElseThrow();
            UndeadAugments.healFromLifeSteal(source, 100);
            require(close(lowest.health(), 30), "Exactly 10% of the 100 overflow must reach the lowest ally.");
            require(close(other.health(), 40), "Only one ally may receive the donation.");
            AugmentCombat.runWithoutTriggers(() -> UndeadAugments.healFromLifeSteal(source, 100));
            require(close(lowest.health(), 30), "Augment extra attacks must retain native healing without another donation.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void kingCopiesCapAtSixOutliveCombatDeathAndDisappearWithOriginalSale(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            EntityBackedTower king = tower(lane, UndeadTowers.T2_RANGED_SKELETON_TOWER, context, 3);
            select(king, UndeadAugments.KING);
            var entity = king.runtimeEntity(lane).orElseThrow();
            Monster enemy = new Monster("king_test", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                    100, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0);
            for (int i = 0; i < 20; i++) {king.onNearbyMonsterDeath(lane, enemy, entity.position());}
            List<Tower> copies = lane.towers().stream().filter(Tower::isTemporaryCopy).toList();
            require(copies.size() == 6, "Four summon events must still create at most six live copies.");
            Tower first = copies.getFirst();
            double fixedDamage = first.modifyAttackDamage(null, null, 0);
            require(close(first.currentMaxHealth(), king.currentMaxHealth() * .35), "Copy maximum health must be 35%.");
            require(copies.stream().allMatch(copy -> copy.slotWeight() == 0 && !copy.canBeSold()),
                    "Copies must be slot-free and unsellable.");
            entity.setHealth(0);
            require(king.isDestroyed(lane), "The original must actually die.");
            king.notifyDeath(lane);
            require(lane.towers().containsAll(copies), "Combat death must not remove copies.");
            require(close(first.modifyAttackDamage(null, null, 0), fixedDamage), "Copy stats must stay fixed.");
            lane.removeTower(king);
            require(lane.towers().stream().noneMatch(Tower::isTemporaryCopy), "Permanent parent removal must remove copies.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void actualDeathRespawnsAtTwentyTicksWithBonusAndResetCancelsTheWait(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            EntityBackedTower tower = tower(lane, UndeadTowers.T1_ZOMBIE_TOWER, context, 3);
            select(tower, UndeadAugments.SECOND_CHANCE);
            GridPosition deathPosition = tower.position();
            tower.runtimeEntity(lane).orElseThrow().setHealth(0);
            require(tower.isDestroyed(lane), "The first life must end before revival.");
            tower.notifyDeath(lane);
            for (int i = 0; i < 19; i++) {UndeadAugments.tick(lane);}
            require(tower.health() == 0, "Revival must not occur before twenty ticks.");
            UndeadAugments.tick(lane);
            require(tower.runtimeEntity(lane).orElseThrow().isAlive(), "Revival must create a live entity.");
            require(tower.position().equals(deathPosition), "Revival must preserve the actual death position.");
            require(close(tower.health(), tower.currentMaxHealth() * .6), "Revival restores 60% health.");
            require(close(UndeadAugments.damageBonus(tower), .6), "Revived attack bonus must be 60%.");
            tower.runtimeEntity(lane).orElseThrow().setHealth(0);
            tower.isDestroyed(lane);
            require(tower.notifyDeath(lane), "A revived tower's second life must notify its actual death.");
            for (int i = 0; i < 20; i++) {UndeadAugments.tick(lane);}
            require(tower.health() == 0, "The second death must not schedule another revival.");
            lane.resetForRound();
            tower.runtimeEntity(lane).orElseThrow().setHealth(0);
            tower.isDestroyed(lane);
            tower.notifyDeath(lane);
            lane.resetForRound();
            for (int i = 0; i < 20; i++) {UndeadAugments.tick(lane);}
            require(close(tower.health(), tower.currentMaxHealth()), "Round end must cancel the pending 60% revival.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    private static PlayerLane lane(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        var layout = new LaneRegionLayout(1, Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 13))),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static void attack(SemionTowerEntity source, SemionMonsterEntity target) {
        double damage = source.attackDamageAmount(target);
        Tower.DamageResult hit = source.runtimeTower().damagePrimaryAttackTargetResult(source, target, damage);
        AugmentCombat.onPrimaryAttackResolved(source, target, hit);
        source.recordAttack(target, damage, hit.outgoingDamage(), hit.dealtDamage(), hit.killed());
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, int laneId) {
        Monster monster = new Monster("bone_target_" + UUID.randomUUID(), TeamId.RED, laneId,
                Optional.empty(), Optional.empty(), 10_000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "Bone target must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static EntityBackedTower tower(PlayerLane lane, TowerType type, GameTestHelper context, int x) {
        var tower = (EntityBackedTower) ProductionTowerCatalog.find(type.id()).orElseThrow().create(lane.ownerPlayer(),
                TeamId.RED, 1, GridPosition.from(context.absolutePos(new BlockPos(x, 2, 4))));
        lane.addTower(tower);
        return tower;
    }

    private static void select(Tower tower, String card) {
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null,
                new AugmentChoice(tower.logicalId(), null, "")))), tower.attachedLane());
    }

    private static boolean close(double a, double b) {return Math.abs(a - b) < .001;}
    private static void require(boolean condition, String message) {if (!condition) {throw new AssertionError(message);}}
}
