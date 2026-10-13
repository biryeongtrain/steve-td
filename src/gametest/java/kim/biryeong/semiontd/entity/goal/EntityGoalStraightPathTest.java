package kim.biryeong.semiontd.entity.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class EntityGoalStraightPathTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void straightPathFollowsOpenGroundAndLeavesObstaclesToTheVanillaPathfinder(GameTestHelper context) {
        BlockPos origin = context.absolutePos(new BlockPos(8, 3, 12));
        Vec3 start = Vec3.atBottomCenterOf(origin);
        var layout = new LaneRegionLayout(1, start, List.of(start.add(14, 0, 0)), start.add(16, 0, 0),
                BlockBounds.of(origin.offset(-6, -1, -8), origin.offset(20, 5, 8)),
                List.of(GridPosition.from(origin.offset(16, 0, 0))));
        var lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        SemionMonsterEntity monster = lane.spawnMonsterAt(new Monster("straight_path", TeamId.RED, 1,
                Optional.empty(), Optional.empty(), 10000, 0, 20, AttackKind.MELEE, "minecraft:zombie", 0L),
                start).orElseThrow();
        context.runAfterDelay(10, () -> {
            try {
                check(context, monster, start);
                context.succeed();
            } finally {
                monster.discard();
            }
        });
    }

    private static void check(GameTestHelper context, SemionMonsterEntity monster, Vec3 spawn) {
        var level = context.getLevel();
        context.assertTrue(monster.onGround(), "The monster must stand on the arena floor");
        monster.setNoAi(true);
        monster.getNavigation().stop();
        Vec3 start = new Vec3(spawn.x, monster.getY(), spawn.z);
        BlockPos origin = BlockPos.containing(start.x, start.y + 0.5, start.z);
        monster.setPos(start);
        monster.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(5.0);

        Path far = EntityGoalStraightPath.straight(monster, origin.offset(12, 1, 0));
        context.assertTrue(far != null && far.getNodeCount() == 5 && !far.canReach(),
                "A distant target yields the same four-block horizon as the vanilla search: " + far);
        for (int index = 0; index < far.getNodeCount(); index++) {
            context.assertTrue(far.getNodePos(index).equals(origin.offset(index, 0, 0)),
                    "Nodes run straight toward the target: " + far.getNodePos(index));
        }
        Path near = EntityGoalStraightPath.straight(monster, origin.offset(3, 1, 0));
        context.assertTrue(near != null && near.getNodeCount() == 3 && near.canReach()
                && near.getEndNode().asBlockPos().equals(origin.offset(2, 0, 0)),
                "A near target stops one block short like the vanilla accuracy rule: " + near);
        Path diagonal = EntityGoalStraightPath.straight(monster, origin.offset(3, 0, 3));
        context.assertTrue(diagonal != null && diagonal.getEndNode().asBlockPos().equals(origin.offset(3, 0, 3)),
                "Diagonal steps are allowed on open ground: " + diagonal);

        BlockPos wall = origin.offset(2, 0, 0);
        BlockState floor = level.getBlockState(wall.below());
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        context.assertTrue(EntityGoalStraightPath.straight(monster, origin.offset(12, 1, 0)) == null,
                "A blocked line is not walked straight");
        var helper = new EntityGoalStraightPath();
        helper.moveTo(monster, start.x + 12, start.y, start.z, 1.0);
        Path around = monster.getNavigation().getPath();
        context.assertTrue(around != null, "The vanilla pathfinder still answers when the line is blocked");
        for (int index = 0; index < around.getNodeCount(); index++) {
            context.assertTrue(!around.getNodePos(index).equals(wall), "The fallback path avoids the wall");
        }
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(wall.below(), Blocks.AIR.defaultBlockState());
        context.assertTrue(EntityGoalStraightPath.straight(monster, origin.offset(12, 1, 0)) == null,
                "A gap in the floor is not walked straight");
        level.setBlockAndUpdate(wall.below(), floor);

        monster.getNavigation().stop();
        helper.moveTo(monster, start.x + 12, start.y, start.z, 1.0);
        Path first = monster.getNavigation().getPath();
        context.assertTrue(first != null && first.getNodeCount() == 5, "Open ground uses the straight path");
        helper.moveTo(monster, start.x + 12, start.y, start.z, 1.0);
        context.assertTrue(monster.getNavigation().getPath() == first, "An unchanged target keeps the current path");
        helper.moveTo(monster, start.x, start.y, start.z + 6, 1.0);
        Path turned = monster.getNavigation().getPath();
        context.assertTrue(turned != first && turned != null
                        && turned.getEndNode().asBlockPos().equals(origin.offset(0, 0, 4)),
                "A new target replaces the path");
        monster.setOnGround(false);
        helper.moveTo(monster, start.x, start.y, start.z + 6, 1.0);
        context.assertTrue(monster.getNavigation().getPath() == null,
                "An airborne monster keeps the vanilla rule and holds no path");
    }
}
