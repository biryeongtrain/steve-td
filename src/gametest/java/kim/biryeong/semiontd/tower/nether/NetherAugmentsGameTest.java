package kim.biryeong.semiontd.tower.nether;

import java.util.ArrayList;
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
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class NetherAugmentsGameTest {
    @GameTest
    public void bloodlettingChargesFromActualReducedDecayAndHotBloodedUsesSixtyPercent(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ArrayList<SemionMonsterEntity> monsters = new ArrayList<>();
        try {
            NetherTower tower = tower(lane, context);
            select(tower, NetherTower.BLOODLETTING, NetherTower.HOT_BLOODED);
            var source = tower.runtimeEntity(lane).orElseThrow();
            var target = target(context, lane, source.position().add(1, 0, 0));
            monsters.add(target);
            double initialHealth = tower.health();
            tower.tick(lane);
            double expectedLoss = tower.currentMaxHealth()
                    * TowerBalanceRuntime.ability(NetherTower.CONFIG_ID, "netherDecayMaxHealthRatioPerSecond") / 40;
            require(close(initialHealth - tower.health(), expectedLoss), "Natural decay must be exactly halved.");
            tower.recordNaturalHealthLoss(tower.currentMaxHealth() * .4 - expectedLoss);
            double targetBefore = target.runtimeMonster().health();
            tower.onAttackResolved(source, target, 10, 10, 10, false);
            require(close(targetBefore - target.runtimeMonster().health(), tower.currentMaxHealth() * .2),
                    "One basic hit consumes exactly one stored charge as magic damage.");
            require(close(tower.roundMagicDamageDealt(), tower.currentMaxHealth() * .2), "Blood damage must be magic.");
            require(close(tower.consumeBloodCharge(), tower.currentMaxHealth() * .2), "The second charge must remain.");
            tower.syncHealth(tower.currentMaxHealth() * .5);
            source.setHealth((float) tower.health());
            tower.onAttack(source, target, 1, false);
            require(tower.runtimeDetailLines().stream().anyMatch(line -> line.startsWith("체력 감소 완화")),
                    "At 50% health the Strider critical effect must already activate with the 60% condition.");
            context.succeed();
        } finally {
            monsters.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void hotBloodedDoesNotGrantCriticalEffectsAboveSixtyPercentHealth(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ArrayList<SemionMonsterEntity> monsters = new ArrayList<>();
        try {
            NetherTower tower = tower(lane, context);
            select(tower, NetherTower.HOT_BLOODED);
            var source = tower.runtimeEntity(lane).orElseThrow();
            var target = target(context, lane, source.position().add(1, 0, 0));
            monsters.add(target);
            tower.syncHealth(tower.currentMaxHealth() * .601);
            source.setHealth((float) tower.health());
            tower.onAttack(source, target, 0, false);
            require(tower.runtimeDetailLines().stream().noneMatch(line -> line.startsWith("체력 감소 완화")),
                    "Hot Blooded must not activate the Strider critical effect above 60% health.");
            double healthBefore = tower.health();
            double reducedDecay = tower.currentMaxHealth()
                    * TowerBalanceRuntime.ability(NetherTower.CONFIG_ID, "netherDecayMaxHealthRatioPerSecond") / 40;
            tower.tick(lane);
            require(close(healthBefore - tower.health(), reducedDecay),
                    "Above the critical threshold only Hot Blooded's unconditional 50% decay reduction applies.");

            tower.syncHealth(tower.currentMaxHealth() * .6);
            source.setHealth((float) tower.health());
            tower.onAttack(source, target, 0, false);
            require(tower.runtimeDetailLines().stream().anyMatch(line -> line.startsWith("체력 감소 완화")),
                    "The same critical effect must activate at exactly 60% health.");
            healthBefore = tower.health();
            tower.tick(lane);
            require(close(healthBefore - tower.health(), reducedDecay
                            * (1 - TowerBalanceRuntime.ability(tower.type().id(), "decayReductionRatio"))),
                    "At the threshold the actual natural loss must include the Strider critical reduction.");
            context.succeed();
        } finally {
            monsters.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void phaseClampsNaturalLossAndAddsExactPhysicalSplashWithoutRecursiveProc(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ArrayList<SemionMonsterEntity> monsters = new ArrayList<>();
        try {
            NetherTower tower = tower(lane, context);
            select(tower, NetherTower.SECOND_PHASE);
            var source = tower.runtimeEntity(lane).orElseThrow();
            var primary = target(context, lane, source.position().add(1, 0, 0));
            var near = target(context, lane, primary.position().add(2.99, 0, 0));
            var outside = target(context, lane, primary.position().add(3.01, 0, 0));
            monsters.addAll(List.of(primary, near, outside));
            tower.onDamaged(source, null, 1000, 10, 0);
            require(tower.state() == NetherTowerState.ZOMBIE, "First death must transition to zombie.");
            tower.syncHealth(1);
            source.setHealth(1);
            tower.tick(lane);
            require(close(tower.health(), 1), "Second phase must clamp natural decay at one health.");
            double hit = source.attackDamageAmount(primary) * .5;
            tower.onAttackResolved(source, primary, 1, 1, 1, false);
            require(close(10000 - primary.runtimeMonster().health(), hit), "Splash must include the primary target.");
            require(close(10000 - near.runtimeMonster().health(), hit), "Targets inside three blocks take 50% damage.");
            require(close(outside.runtimeMonster().health(), 10000), "Targets outside three blocks must remain untouched.");
            require(close(tower.roundPhysicalDamageDealt(), hit * 2), "Splash must use physical attribution.");
            AugmentCombat.runWithoutTriggers(() -> tower.onAttackResolved(source, primary, 1, 1, 1, false));
            require(close(tower.roundPhysicalDamageDealt(), hit * 2), "An augment attack must not retrigger second phase.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            monsters.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void totemRespawnsActualZombieDeathOnceAtSamePosition(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            NetherTower tower = tower(lane, context);
            select(tower, NetherTower.TOTEM);
            var source = tower.runtimeEntity(lane).orElseThrow();
            tower.onDamaged(source, null, 1000, 10, 0);
            source.setHealth(0);
            require(tower.isDestroyed(lane), "Zombie death must be a real death.");
            GridPosition position = tower.position();
            tower.notifyDeath(lane);
            for (int i = 0; i < 19; i++) {tower.tick(lane);}
            require(tower.health() == 0, "Totem must not revive before twenty ticks.");
            tower.tick(lane);
            require(tower.runtimeEntity(lane).orElseThrow().isAlive(), "Totem must respawn a live entity.");
            require(tower.position().equals(position), "Totem must preserve the death position.");
            require(close(tower.health(), tower.currentMaxHealth() * .5), "Totem restores 50% health.");
            require(close(tower.modifyOutgoingDamage(null, null, 100), 200), "Totem doubles all outgoing damage.");
            tower.runtimeEntity(lane).orElseThrow().setHealth(0);
            tower.isDestroyed(lane);
            tower.notifyDeath(lane);
            for (int i = 0; i < 20; i++) {tower.tick(lane);}
            require(tower.health() == 0, "Totem may only revive once per round.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    private static PlayerLane lane(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        var layout = new LaneRegionLayout(1, Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 13))),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static NetherTower tower(PlayerLane lane, GameTestHelper context) {
        var tower = new NetherTower(NetherTowers.T1_STRIDER, lane.ownerPlayer(), TeamId.RED, 1,
                GridPosition.from(context.absolutePos(new BlockPos(3, 2, 4))));
        lane.addTower(tower);
        return tower;
    }

    private static SemionMonsterEntity target(GameTestHelper context, PlayerLane lane, Vec3 position) {
        Monster runtime = new Monster("nether_augment_target", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
        var target = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        target.configureFrom(runtime, lane.laneLayout());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(position);
        require(context.getLevel().addFreshEntity(target), "Test target must spawn.");
        runtime.markMinecraftEntitySpawned(target.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return target;
    }

    private static void select(NetherTower tower, String... cards) {
        List<PlayerAugmentState.Selection> selections = java.util.Arrays.stream(cards).map(card ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED,
                        null, AugmentChoice.none())).toList();
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), selections), tower.attachedLane());
    }

    private static boolean close(double a, double b) {return Math.abs(a - b) < .01;}
    private static void require(boolean condition, String message) {if (!condition) {throw new AssertionError(message);}}
}
