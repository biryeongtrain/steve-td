package kim.biryeong.semiontd.summon.invasion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class InvasionSiegeTargetTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void golemUsesAggroAndReacquiresAfterMovementDeathAndBlockedPath(GameTestHelper context) {
        BlockPos origin = context.absolutePos(new BlockPos(12, 3, 12));
        Vec3 start = Vec3.atCenterOf(origin);
        var layout = new LaneRegionLayout(1, start, List.of(start.add(0, 0, 12)), start.add(0, 0, 18),
                BlockBounds.of(origin.offset(-8, -1, -8), origin.offset(18, 5, 18)),
                List.of(GridPosition.from(origin.offset(10, 0, 0)), GridPosition.from(origin.offset(11, 0, 0))));
        UUID owner = UUID.randomUUID();
        var lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        var low = tower(lane, origin.east(), 10);
        var high = tower(lane, origin.east(5), 100);
        lane.addTower(low);
        lane.addTower(high);
        var definition = SummonConfig.defaultConfig().summons().get("siege_golem");
        var runtime = new Monster("siege_golem", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                10000, 0, 20, kim.biryeong.semiontd.config.AttackKind.MELEE, "minecraft:iron_golem", 0L);
        var monster = lane.spawnMonsterAt(runtime, start).orElseThrow();
        monster.setNoAi(true);
        monster.setNoGravity(true);
        new InvasionSummon(definition).createAbilityGoals(monster);
        var lowEntity = (SemionTowerEntity) context.getLevel().getEntity(low.entityId().orElseThrow());
        var highEntity = (SemionTowerEntity) context.getLevel().getEntity(high.entityId().orElseThrow());
        var acquire = new AcquireLaneDefenseTargetGoal(monster);
        var attack = new MonsterAttackTargetGoal(monster, 1.0);
        try {
            context.assertTrue(!monster.ignoresDefenses(), "Siege golem participates in common defense targeting");
            acquire.start();
            context.assertTrue(monster.getTarget() == highEntity, "Higher aggro wins over nearer lower-aggro tower");
            context.setBlock(15, 3, 12, Blocks.STONE);
            attack.tick();
            context.assertTrue(monster.getTarget() == highEntity, "An obstructed path must not bypass the defender for the boss");
            highEntity.setPos(start.add(200, 0, 0));
            attack.tick();
            context.assertTrue(monster.getTarget() == null, "Moving out of leash clears the old target");
            acquire.start();
            context.assertTrue(monster.getTarget() == lowEntity, "Remaining reachable defender is reacquired");
            double health = low.health();
            monster.attackStyle().hit(monster, lowEntity);
            context.assertTrue(low.health() < health, "Golem attack damages the selected tower");
            lane.killTower(low);
            monster.aiStep();
            context.assertTrue(monster.getTarget() == null, "Destroyed tower is dropped by common monster lifecycle");
            context.assertTrue(!acquire.canUse(), "Without nearby towers, no stale defender is acquired");
            highEntity.setPos(start.add(4, 0, 0));
            acquire.start();
            context.assertTrue(monster.getTarget() == highEntity, "Returning defender is acquired again");
            lane.moveTowersToFinalDefense();
            monster.setPos(highEntity.position().add(0, 0, 2));
            monster.setTarget(null);
            acquire.start();
            context.assertTrue(monster.getTarget() == highEntity && highEntity.deployedAtFinalDefense(), "Central defense tower keeps aggro");
            context.assertTrue(runtime.inFinalDefenseCombat(), "Acquiring a central tower marks final defense combat");
            context.succeed();
        } finally {
            monster.discard();
            lane.clearTowers();
        }
    }

    private static ProductionTower tower(PlayerLane lane, BlockPos position, int priority) {
        return new ProductionTower(TowerType.builder("siege_target_" + priority, "Siege target")
                .maxHealth(1000).damage(0).range(0).aggroPriority(priority).build(), lane.ownerPlayer(),
                lane.teamId(), lane.laneId(), GridPosition.from(position));
    }
}
