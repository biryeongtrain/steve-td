package kim.biryeong.semiontd.tower.pirate;

import java.util.ArrayList;
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
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class PirateAugmentGameTest {
    @GameTest(maxTicks = 120)
    public void fleetFiresTwoSpacedShellsForOnlyFirstFiveBasics(GameTestHelper context) {
        PlayerLane lane = lane(context, PirateAugments.FLEET);
        try {
            PirateTower tower = tower(context, lane, PirateTowers.ADMIRAL, 3);
            SemionTowerEntity source = tower.runtimeEntity(lane).orElseThrow();
            List<SemionMonsterEntity> targets = new ArrayList<>();
            for (int index = 0; index < 13; index++) targets.add(target(context, lane, source.position().add(2 + index * .02, 0, 0)));
            SemionMonsterEntity primary = targets.getFirst();
            double damage = source.attackDamageAmount(primary) * .8;
            for (int index = 0; index < 6; index++) PirateAugments.onAttack(tower, source, primary, 1);
            AugmentCombat.runWithoutTriggers(() -> PirateAugments.onAttack(tower, source, primary, 1));
            check(PirateAugments.pendingShells(lane.ownerPlayer()) == 10, "Only five basic attacks may schedule two shells each");
            long now = context.getLevel().getGameTime();
            PirateAugments.tick(lane, now + 3);
            close(100_000, primary.runtimeMonster().health(), "Shells wait four ticks");
            PirateAugments.tick(lane, now + 4);
            close(100_000 - 5 * damage, primary.runtimeMonster().health(), "First five shells");
            check(targets.stream().filter(enemy -> enemy.runtimeMonster().health() < 100_000).count() == 12, "Each shell caps its area at twelve enemies");
            check(PirateAugments.pendingShells(lane.ownerPlayer()) == 5, "Shell callbacks cannot schedule more shells");
            PirateAugments.tick(lane, now + 8);
            close(100_000 - 10 * damage, primary.runtimeMonster().health(), "Second shells fire four ticks later");
            check(PirateAugments.pendingShells(lane.ownerPlayer()) == 0, "All ten shells settle");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 120)
    public void fleetRetargetsInsideOriginalRangeAndCancelsMissingTargets(GameTestHelper context) {
        PlayerLane lane = lane(context, PirateAugments.FLEET);
        try {
            PirateTower tower = tower(context, lane, PirateTowers.NAVIGATOR, 3);
            SemionTowerEntity source = tower.runtimeEntity(lane).orElseThrow();
            source.applyTimedEffect(TimedEffectType.TOWER_RANGE_REDUCTION, .8, 120);
            SemionMonsterEntity original = target(context, lane, source.position().add(1, 0, 0));
            SemionMonsterEntity replacement = target(context, lane, source.position().add(-1, 0, 0));
            PirateAugments.onAttack(tower, source, original, 1);
            original.setPos(source.position().add(3, 0, 0));
            check(source.distanceToSqr(original) > source.attackRange() * source.attackRange(), "Original target must leave the current attack range");
            long now = context.getLevel().getGameTime();
            PirateAugments.tick(lane, now + 4);
            close(100_000, original.runtimeMonster().health(), "Out of range target is not hit");
            close(100_000 - source.attackDamageAmount(replacement) * .8, replacement.runtimeMonster().health(), "First shell selects another target in range");
            replacement.discard();
            PirateAugments.tick(lane, now + 8);
            check(PirateAugments.pendingShells(lane.ownerPlayer()) == 0, "Missing-target shell is consumed");
            close(100_000, original.runtimeMonster().health(), "Cancellation cannot reach out of range");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 120)
    public void cannonFixesStrongestAttackAndBombardsAtTwoSecondIntervals(GameTestHelper context) {
        PlayerLane lane = lane(context, PirateAugments.CANNON);
        try {
            tower(context, lane, PirateTowers.ADMIRAL, 1);
            PirateTower strongest = tower(context, lane, PirateTowers.NAVIGATOR, 3);
            SemionTowerEntity source = strongest.runtimeEntity(lane).orElseThrow();
            source.applyTimedEffect(TimedEffectType.TOWER_RANGE_REDUCTION, .9, 240);
            double expected = source.attackDamageAmount(null);
            List<SemionMonsterEntity> targets = new ArrayList<>();
            for (int index = 0; index < 13; index++) targets.add(target(context, lane, source.position().add(2 + index * .02, 0, 0)));
            check(source.distanceToSqr(targets.getFirst()) > source.attackRange() * source.attackRange(), "Artillery target must be beyond the basis tower's attack range");
            targets.getFirst().runtimeMonster().syncLaneProgress(.9);
            SemionMonsterEntity outside = target(context, lane, targets.getFirst().position().add(0, 0, 3.1));
            PirateAugments.onSelected(lane, PirateAugments.CANNON, AugmentConfig.defaults());
            PirateAugments.beginWave(lane);
            close(expected, PirateAugments.cannonDamage(lane.ownerPlayer()), "Highest current attack is fixed at wave start");
            strongest.addPermanentFlatDamageBonus(100, lane);
            long now = context.getLevel().getGameTime();
            PirateAugments.tick(lane, now + 39);
            close(100_000, targets.getFirst().runtimeMonster().health(), "No cannon before two seconds");
            for (int shot = 1; shot <= 5; shot++) {
                PirateAugments.tick(lane, now + shot * 40L);
                close(100_000 - shot * expected * 1.6, targets.getFirst().runtimeMonster().health(), "Cannon preserves initial attack for all five shots");
            }
            check(targets.stream().filter(enemy -> enemy.runtimeMonster().health() < 100_000).count() == 12, "Cannon caps targets at twelve");
            close(100_000, outside.runtimeMonster().health(), "Cannon radius is three blocks");
            check(PirateAugments.pendingCannonShots(lane.ownerPlayer()) == 0, "Exactly five scheduled bombardments");
            check(PirateAugments.cannonStacks(lane.ownerPlayer()) == 0, "One stack consumed for the entire wave");
            context.succeed();
        } finally { cleanup(lane); }
    }

    private static PlayerLane lane(GameTestHelper context, String card) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.randomUUID();
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 6, 7));
        var layout = new LaneRegionLayout(1, Vec3.atCenterOf(min), List.of(Vec3.atCenterOf(min), Vec3.atCenterOf(max)),
                Vec3.atCenterOf(max), BlockBounds.of(min, max), List.of(GridPosition.from(max)));
        var lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static PirateTower tower(GameTestHelper context, PlayerLane lane, TowerType type, int x) {
        var tower = new PirateTower(TowerBalanceRuntime.resolve(type), lane.ownerPlayer(), TeamId.RED, 1,
                GridPosition.from(context.absolutePos(new BlockPos(x, 2, 3))));
        lane.addTower(tower);
        tower.onWaveStarted(lane, 5);
        tower.runtimeEntity(lane).orElseThrow().setNoAi(true);
        return tower;
    }

    private static SemionMonsterEntity target(GameTestHelper context, PlayerLane lane, Vec3 position) {
        Monster monster = new Monster("pirate-augment-" + UUID.randomUUID(), TeamId.RED, 1, Optional.empty(),
                Optional.empty(), 100_000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoGravity(true);
        entity.setNoAi(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static void cleanup(PlayerLane lane) {
        lane.clearTowers();
        PirateAugments.clearPlayer(lane.ownerPlayer());
        AreaEffectLaneIndex.unregister(lane);
        for (Monster monster : lane.activeMonsters()) {
            var entity = lane.arenaWorld().getEntity(monster.minecraftEntityId());
            if (entity != null) entity.discard();
        }
    }

    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static void close(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > .0001) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
