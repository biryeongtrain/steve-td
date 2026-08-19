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
public final class MonsterVoidFallGameTest {
    @GameTest
    public void monsterKnockedIntoTheVoidDiesInsteadOfHangingTheWave(GameTestHelper context) {
        UUID owner = stableUuid("void-rescue-owner");
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, owner);
        group.addLane(lane);
        try {
            Monster monster = spawnMonster(context, lane, "void-fall-target", position(context, 3, 1, 3));
            SemionMonsterEntity entity =
                    (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
            require(entity != null, "The test monster must have an entity.");
            require(lane.activeMonsters().contains(monster), "The monster must start in the lane.");

            // 공허로 밀려납니다. 넉백은 속도만 주므로 결과는 좌표가 아래로 빠지는 것입니다.
            double floor = lane.laneLayout().laneArea().min().getY();
            entity.teleportTo(entity.getX(), floor - 40.0, entity.getZ());
            entity.setDeltaMovement(new Vec3(0.0, -3.0, 0.0));

            lane.tick(context.getLevel().getServer());

            require(!monster.isAlive(), "떨어진 몹은 죽어야 합니다. 안 죽으면 웨이브가 끝나지 않습니다.");
            require(!lane.activeMonsters().contains(monster),
                    "죽은 몹은 레인 목록에서 빠져야 웨이브가 정리됩니다.");
            require(context.getLevel().getEntity(monster.minecraftEntityId()) == null,
                    "엔티티도 같이 정리돼야 합니다. 남으면 다시 소환되는 경로를 탑니다.");
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
}
