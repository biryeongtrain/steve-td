package kim.biryeong.semiontd.tower.adversary;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AdversaryRivalGameTest {
    @GameTest
    public void rivalConvertsAtItsSlotAndReturnsWithoutFakeTowerDeath(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes(
                "adversary-rival-lifecycle".getBytes(StandardCharsets.UTF_8)
        );
        PlayerLane lane = testLane(context, owner);
        GridPosition rivalPosition = GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3)));
        DeathObserverTower fox = new DeathObserverTower(
                AdversaryTowers.FOX,
                owner,
                new GridPosition(rivalPosition.x() - 1, rivalPosition.y(), rivalPosition.z())
        );
        AdversaryRivalTower rival = new AdversaryRivalTower(
                AdversaryTowers.BREEZE_RIVAL,
                owner,
                TeamId.RED,
                1,
                rivalPosition
        );

        try {
            lane.addTower(fox);
            lane.addTower(rival);
            int preparationEntityId = rival.entityId().orElseThrow();

            lane.markWaveStarted(1);

            require(rival.convertedForWave(), "Rival should convert when the wave starts.");
            require(rival.entityId().isEmpty(), "Converted rival should hide its tower entity.");
            require(lane.towers().contains(rival), "Converted rival must keep occupying its logical tower slot.");
            require(lane.activeMonsters().size() == 1, "One rival proxy should be active.");
            require(context.getLevel().getEntity(preparationEntityId) == null
                            || context.getLevel().getEntity(preparationEntityId).isRemoved(),
                    "Preparation tower entity should be discarded during conversion.");

            Monster proxy = lane.activeMonsters().getFirst();
            require(proxy.mineralReward() == 0L, "Rival proxy must never award minerals.");
            require(proxy.hasMinecraftEntity(), "Rival proxy should have a live Minecraft entity.");
            require(context.getLevel().getEntity(proxy.minecraftEntityId()) instanceof SemionMonsterEntity,
                    "Rival proxy entity should use the shared Semion monster runtime.");
            Vec3 expected = new Vec3(
                    rivalPosition.x() + 0.5,
                    rivalPosition.y() + 1.0,
                    rivalPosition.z() + 0.5
            );
            require(context.getLevel().getEntity(proxy.minecraftEntityId()).position().distanceToSqr(expected) < 0.01,
                    "Rival proxy should spawn at its installed slot.");

            lane.tick(context.getLevel().getServer());
            require(fox.nearbyTowerDeaths == 0,
                    "Wave conversion must not fan out a fake nearby tower-death event.");
            require(!lane.laneDefenseBroken(), "A living fox should keep the lane defense active.");

            fox.syncHealth(0.0);
            lane.tick(context.getLevel().getServer());
            require(lane.laneDefenseBroken(),
                    "Hidden rivals must not prevent defense collapse after the fox dies.");

            lane.resetForRound();
            require(!rival.convertedForWave(), "Rival should return for the next preparation.");
            require(rival.entityId().isPresent(), "Returned rival should respawn its tower entity.");
            require(lane.activeMonsters().stream()
                            .noneMatch(monster -> AdversaryRivalTower.logicalRivalIdOf(monster)
                                    .filter(rival.rivalId()::equals)
                                    .isPresent()),
                    "No rival proxy may survive the preparation reset.");
            require(!lane.laneDefenseBroken(), "Round reset should restore lane defense state.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AdversaryProgressStates.clear(owner);
        }
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(10, 5, 14));
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        Vec3 waypoint = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 8)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 13)));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                List.of(waypoint),
                boss,
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(7, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class DeathObserverTower extends Tower {
        private int nearbyTowerDeaths;

        private DeathObserverTower(TowerType type, UUID owner, GridPosition position) {
            super(type, owner, TeamId.RED, 1, position);
        }

        @Override
        public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
            nearbyTowerDeaths++;
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
