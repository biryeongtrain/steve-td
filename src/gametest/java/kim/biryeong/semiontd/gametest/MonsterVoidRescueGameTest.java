package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * 넉백에 밀려 아레나 밖으로 떨어진 몹이 웨이브를 멈춰 세우지 않는지 확인합니다.
 *
 * <p>넉백 계열 스킬을 이어 쓰면 몹이 공허로 떨어집니다. 그러면 죽지도 사라지지도 않은 채 남아
 * 웨이브가 영영 끝나지 않습니다.
 */
public final class MonsterVoidRescueGameTest {
    @GameTest
    public void monsterKnockedIntoTheVoidComesBackToItsPath(GameTestHelper context) {
        UUID owner = stableUuid("void-rescue-owner");
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, owner);
        group.addLane(lane);
        try {
            Monster monster = spawnMonster(context, lane, "void-rescue-target", position(context, 3, 1, 3));
            SemionMonsterEntity entity =
                    (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
            require(entity != null, "The test monster must have an entity.");
            double progressBeforeFall = monster.laneProgress();

            // 공허로 밀려납니다. 넉백은 속도만 주므로 결과는 좌표가 아래로 빠지는 것입니다.
            double floor = lane.laneLayout().laneArea().min().getY();
            entity.teleportTo(entity.getX(), floor - 40.0, entity.getZ());
            entity.setDeltaMovement(new Vec3(0.0, -3.0, 0.0));

            lane.tick(context.getLevel().getServer());

            require(monster.isAlive(), "떨어졌다고 몹이 사라지면 넉백이 가장 싼 처형 수단이 됩니다.");
            require(entity.getY() >= floor - 4.0,
                    "떨어진 몹은 경로 위로 돌아와야 합니다. y=" + entity.getY() + " (바닥 " + floor + ")");
            require(entity.getDeltaMovement().lengthSqr() < 1.0E-6,
                    "되돌린 뒤에도 낙하 속도가 남아 있으면 그대로 다시 떨어집니다.");
            requireClose(progressBeforeFall, monster.laneProgress(),
                    "되돌리기는 진행도를 건드리지 않아야 합니다.");

            // 되돌아온 뒤에는 평소대로 진행도가 갱신됩니다.
            lane.tick(context.getLevel().getServer());
            require(monster.isAlive(), "되돌아온 몹은 계속 살아 있어야 합니다.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Monster void rescue failed: " + failure.getMessage()));
        } finally {
            group.closeRuntime();
        }
    }

    private static Monster spawnMonster(GameTestHelper context, PlayerLane lane, String id, GridPosition position) {
        Monster monster = new Monster(
                id,
                lane.teamId(),
                lane.laneId(),
                Optional.empty(),
                Optional.empty(),
                1_000.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);
        require(context.getLevel().addFreshEntity(entity), "The test monster must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return monster;
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 4, 7));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7))),
                BlockBounds.of(min, max),
                List.of(position(context, 6, 1, 6))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
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
        if (Math.abs(expected - actual) > 0.001) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
