package kim.biryeong.semiontd.tower.pet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class PetCatRangeTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void babyCatAttacksOnlyWithinFiveAndAHalfBlocks(GameTestHelper context) {
        verifyRange(context, PetTowers.CAT_T1, 5.5);
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void companionCatAttacksOnlyWithinFivePointEightBlocks(GameTestHelper context) {
        verifyRange(context, PetTowers.CAT_T2, 5.8);
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void finalCatAttacksOnlyWithinSixPointTwoBlocks(GameTestHelper context) {
        verifyRange(context, PetTowers.CAT_T3, 6.2);
    }

    private static void verifyRange(GameTestHelper context, TowerType type, double expectedRange) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.randomUUID();
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn,
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 13))),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)),
                        context.absolutePos(new BlockPos(10, 5, 14))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(8, 2, 11)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        GridPosition keeperPosition = GridPosition.from(context.absolutePos(new BlockPos(3, 2, 4)));
        GridPosition catPosition = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        PetTower keeper = new PetTower(TowerBalanceRuntime.resolve(PetTowers.BUTLER_T1), owner, TeamId.RED, 1,
                keeperPosition, keeperPosition);
        PetTower cat = new PetTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1,
                catPosition, catPosition);
        SemionMonsterEntity target = null;
        try {
            lane.addTower(keeper);
            lane.addTower(cat);
            SemionTowerEntity source = cat.runtimeEntity(lane).orElseThrow();
            source.setNoAi(true);
            source.setNoGravity(true);
            close(expectedRange, source.attackRange(), "Runtime primary range");
            target = monster(lane, source.position().add(0, 0, expectedRange + 0.01));
            TowerAttackMonsterGoal goal = new TowerAttackMonsterGoal(source);
            goal.tick();
            close(1000, target.runtimeMonster().health(), "No attack outside primary range");
            require(source.currentAttackTarget() == target, "The target must be acquired outside primary range");
            target.setPos(source.position().add(0, 0, expectedRange - 0.01));
            double outgoing = cat.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target));
            goal.tick();
            require(outgoing > 0, "The primary attack must deal damage");
            close(1000 - outgoing, target.runtimeMonster().health(), "Primary attack inside new boundary");
            context.succeed();
        } finally {
            if (target != null) {
                target.discard();
            }
            lane.clearTowers();
        }
    }

    private static SemionMonsterEntity monster(PlayerLane lane, Vec3 position) {
        Monster monster = new Monster("cat_range_" + UUID.randomUUID(), TeamId.RED, 1,
                Optional.empty(), Optional.empty(), 1000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, lane.arenaWorld());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        require(lane.arenaWorld().addFreshEntity(entity), "Range target must spawn");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static void close(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1.0E-6, message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
