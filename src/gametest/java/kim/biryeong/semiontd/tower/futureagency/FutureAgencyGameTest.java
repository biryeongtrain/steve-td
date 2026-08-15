package kim.biryeong.semiontd.tower.futureagency;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class FutureAgencyGameTest {
    @GameTest
    public void survivorsStayCapped(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("future-agency-carry".getBytes(StandardCharsets.UTF_8));
        FutureAgencyStates.clear(owner);
        FutureAgencyStates.state(owner).reconstruct();
        PlayerLane lane = testLane(context, owner);
        try {
            GridPosition firstOrigin = floor(context, 3, 2, 3);
            GridPosition secondOrigin = floor(context, 5, 2, 3);
            GridPosition firstCarry = floor(context, 3, 2, 8);
            GridPosition secondCarry = floor(context, 5, 2, 9);
            prepareFloor(context, firstOrigin, secondOrigin, firstCarry, secondCarry);

            FutureAgencyAgentTower first = agent(owner, FutureAgencyRole.COMBAT, firstOrigin);
            FutureAgencyAgentTower second = agent(owner, FutureAgencyRole.COMBAT, secondOrigin);
            lane.addTower(first);
            lane.addTower(second);
            require(first.idleMovementTarget(towerEntity(context, first)).isEmpty(),
                    "Agents must remain still during preparation.");
            lane.markWaveStarted(1);
            require(first.idleMovementTarget(towerEntity(context, first)).isPresent(),
                    "Agents must start advancing when the wave begins.");
            moveAndDamage(first, lane, firstCarry, 31.0);
            moveAndDamage(second, lane, secondCarry, 47.0);

            first.onLaneCleared(lane);
            second.onLaneCleared(lane);
            lane.resetForRound();

            require(first.position().equals(firstOrigin) && close(first.health(), first.currentMaxHealth()),
                    "The installed first agent must return at its origin with full health.");
            require(second.position().equals(secondOrigin) && close(second.health(), second.currentMaxHealth()),
                    "The installed second agent must return at its origin with full health.");
            List<FutureAgencyAgentTower> carried = lane.towers().stream()
                    .filter(FutureAgencyAgentTower.class::isInstance)
                    .map(FutureAgencyAgentTower.class::cast)
                    .filter(FutureAgencyAgentTower::carriedCopy)
                    .toList();
            require(carried.size() == 2, "Each surviving installed agent must add one carried copy.");
            require(carried.stream().anyMatch(agent -> agent.position().equals(firstCarry) && close(agent.health(), 31.0)),
                    "The first survivor copy must preserve its position and health.");
            require(carried.stream().anyMatch(agent -> agent.position().equals(secondCarry) && close(agent.health(), 47.0)),
                    "The second survivor copy must preserve its position and health.");
            require(towerEntity(context, first).isAlive() && towerEntity(context, second).isAlive()
                            && carried.stream().allMatch(agent -> towerEntity(context, agent).isAlive()),
                    "Original and carried agents must appear together during preparation.");
            for (int round = 2; round <= 3; round++) {
                lane.markWaveStarted(round);
                finishWave(lane);
                lane.resetForRound();
                require(carried(lane).size() == 2,
                        "Two installed originals must retain exactly two survivors after wave " + round + ".");
            }
            context.succeed();
        } finally {
            lane.clearTowers();
            FutureAgencyStates.clear(owner);
        }
    }

    @GameTest
    public void finalDefenseCarryIsSafe(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("future-agency-final-defense".getBytes(StandardCharsets.UTF_8));
        FutureAgencyStates.clear(owner);
        FutureAgencyStates.state(owner).reconstruct();
        PlayerLane lane = testLane(context, owner);
        GridPosition firstOrigin = floor(context, 3, 2, 3);
        GridPosition secondOrigin = floor(context, 5, 2, 3);
        prepareFloor(context, firstOrigin, secondOrigin);
        try {
            lane.addTower(agent(owner, FutureAgencyRole.COMBAT, firstOrigin));
            lane.addTower(agent(owner, FutureAgencyRole.SUPPRESSION, secondOrigin));
            lane.moveTowersToFinalDefense();
            require(carried(lane).size() == 2,
                    "Final defense transition must safely establish one survivor per original.");
            context.succeed();
        } finally {
            lane.clearTowers();
            FutureAgencyStates.clear(owner);
        }
    }

    @GameTest
    public void survivorDeathReplacementAndOriginalSaleRespectLink(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("future-agency-death".getBytes(StandardCharsets.UTF_8));
        FutureAgencyStates.clear(owner);
        FutureAgencyStates.state(owner).reconstruct();
        PlayerLane lane = testLane(context, owner);
        GridPosition origin = floor(context, 4, 2, 3);
        GridPosition carry = floor(context, 4, 2, 8);
        prepareFloor(context, origin, carry);
        FutureAgencyAgentTower original = agent(owner, FutureAgencyRole.COMBAT, origin);
        try {
            lane.addTower(original);
            lane.markWaveStarted(1);
            moveAndDamage(original, lane, carry, 40.0);
            finishWave(lane);
            lane.resetForRound();
            require(carried(lane).size() == 1, "The first clear must create one linked survivor.");

            towerEntity(lane, carried(lane).getFirst()).setHealth(0.0f);
            lane.markWaveStarted(2);
            finishWave(lane);
            lane.resetForRound();
            require(carried(lane).size() == 1,
                    "A living original must replace its dead survivor with only one survivor.");

            towerEntity(lane, original).setHealth(0.0f);
            lane.markWaveStarted(3);
            finishWave(lane);
            lane.resetForRound();
            require(carried(lane).size() == 1,
                    "A living survivor must continue when its original dies.");

            towerEntity(lane, original).setHealth(0.0f);
            towerEntity(lane, carried(lane).getFirst()).setHealth(0.0f);
            lane.markWaveStarted(4);
            finishWave(lane);
            lane.resetForRound();
            require(carried(lane).isEmpty(), "No survivor may remain when both linked agents die.");

            lane.markWaveStarted(5);
            finishWave(lane);
            lane.resetForRound();
            require(carried(lane).size() == 1, "The revived original must be able to establish one survivor again.");
            lane.removeTower(original);
            original.onSold(lane);
            require(carried(lane).isEmpty(), "Selling an original must remove its linked survivor immediately.");
            context.succeed();
        } finally {
            lane.clearTowers();
            FutureAgencyStates.clear(owner);
        }
    }

    @GameTest
    public void originalUpgradeSynchronizesSurvivorGradePositionAndHealthRatio(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("future-agency-upgrade".getBytes(StandardCharsets.UTF_8));
        FutureAgencyStates.clear(owner);
        FutureAgencyStates.state(owner).reconstruct();
        PlayerLane lane = testLane(context, owner);
        GridPosition origin = floor(context, 6, 2, 3);
        GridPosition carry = floor(context, 6, 2, 9);
        prepareFloor(context, origin, carry);
        FutureAgencyAgentTower original = agent(owner, FutureAgencyRole.COMBAT, origin);
        try {
            lane.addTower(original);
            lane.markWaveStarted(1);
            moveAndDamage(original, lane, carry, 42.5);
            finishWave(lane);
            lane.resetForRound();
            FutureAgencyAgentTower survivor = carried(lane).getFirst();
            double ratio = survivor.health() / survivor.currentMaxHealth();

            FutureAgencyAgentTower upgraded = new FutureAgencyAgentTower(
                    FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 4), owner, TeamId.RED, 1, origin, origin);
            upgraded.copyFrom(original, 100);
            require(lane.replaceTower(original, upgraded), "The installed original must upgrade.");
            upgraded.onUpgradeCompleted(lane, original, null);

            FutureAgencyAgentTower upgradedSurvivor = carried(lane).getFirst();
            require(upgradedSurvivor.type().id().equals(FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 4).id()),
                    "The linked survivor must share the original grade.");
            require(upgradedSurvivor.position().equals(carry), "The survivor upgrade must preserve its position.");
            require(close(upgradedSurvivor.health() / upgradedSurvivor.currentMaxHealth(), ratio),
                    "The survivor upgrade must preserve its health ratio.");
            require(upgradedSurvivor.slotWeight() == 0 && !upgradedSurvivor.canBeSold(),
                    "The linked survivor must remain slot-free and unsellable.");
            context.succeed();
        } finally {
            lane.clearTowers();
            FutureAgencyStates.clear(owner);
        }
    }

    @GameTest
    public void survivorLinksAreIsolatedByOwnerAndLane(GameTestHelper context) {
        UUID firstOwner = UUID.nameUUIDFromBytes("future-agency-owner-a".getBytes(StandardCharsets.UTF_8));
        UUID secondOwner = UUID.nameUUIDFromBytes("future-agency-owner-b".getBytes(StandardCharsets.UTF_8));
        FutureAgencyStates.state(firstOwner).reconstruct();
        FutureAgencyStates.state(secondOwner).reconstruct();
        PlayerLane firstLane = testLane(context, firstOwner, 1);
        PlayerLane secondLane = testLane(context, firstOwner, 2);
        GridPosition sharedOrigin = floor(context, 3, 2, 3);
        GridPosition otherLaneOrigin = floor(context, 10, 2, 3);
        prepareFloor(context, sharedOrigin, otherLaneOrigin);
        FutureAgencyAgentTower first = agent(firstOwner, FutureAgencyRole.COMBAT, 1, sharedOrigin);
        FutureAgencyAgentTower otherOwner = agent(secondOwner, FutureAgencyRole.COMBAT, 1, sharedOrigin);
        FutureAgencyAgentTower otherLane = agent(firstOwner, FutureAgencyRole.COMBAT, 2, otherLaneOrigin);
        try {
            firstLane.addTower(first);
            firstLane.addTower(otherOwner);
            secondLane.addTower(otherLane);
            first.onLaneCleared(firstLane);
            otherOwner.onLaneCleared(firstLane);
            otherLane.onLaneCleared(secondLane);
            firstLane.resetForRound();
            secondLane.resetForRound();
            require(carried(firstLane).size() == 2 && carried(secondLane).size() == 1,
                    "Each owner and lane must create its own survivor link.");

            firstLane.removeTower(first);
            first.onSold(firstLane);
            require(carried(firstLane).size() == 1
                            && carried(firstLane).getFirst().ownerPlayer().equals(secondOwner),
                    "Selling one owner's original must not remove another owner's survivor.");
            require(carried(secondLane).size() == 1,
                    "Selling in one lane must not remove the same owner's survivor in another lane.");
            context.succeed();
        } finally {
            firstLane.clearTowers();
            secondLane.clearTowers();
            FutureAgencyStates.clear(firstOwner);
            FutureAgencyStates.clear(secondOwner);
        }
    }

    @GameTest
    public void worldSaveStopsNewCarryAndUsesNormalFinalDefenseReset(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("future-agency-world-save".getBytes(StandardCharsets.UTF_8));
        FutureAgencyStates.clear(owner);
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(owner);
        state.reconstruct();
        for (int round = 1; round <= 10; round++) {
            state.openRound(round);
            require(state.choose(state.offers().getFirst()), "A policy must be selectable each preparation.");
            if (round == 5) state.promoteCommander();
        }
        state.saveWorld();
        require(state.worldSaved(), "The tenth policy must allow world salvation.");
        PlayerLane lane = testLane(context, owner);
        GridPosition origin = floor(context, 8, 2, 3);
        prepareFloor(context, origin);
        FutureAgencyAgentTower original = agent(owner, FutureAgencyRole.COMBAT, origin);
        try {
            lane.addTower(original);
            lane.markWaveStarted(11);
            original.onLaneCleared(lane);
            require(carried(lane).isEmpty(), "World salvation must stop creating survivors.");
            require(original.participatesInFinalDefense(), "Saved agents must participate in final defense.");
            lane.moveTowersToFinalDefense();
            require(original.deployedAtFinalDefense(), "Saved agents must move to final defense normally.");
            lane.resetForRound();
            require(original.position().equals(origin), "Saved agents must reset to their installed position.");
            context.succeed();
        } finally {
            lane.clearTowers();
            FutureAgencyStates.clear(owner);
        }
    }

    private static FutureAgencyAgentTower agent(UUID owner, FutureAgencyRole role, GridPosition position) {
        return agent(owner, role, 1, position);
    }

    private static FutureAgencyAgentTower agent(UUID owner, FutureAgencyRole role, int laneId, GridPosition position) {
        return new FutureAgencyAgentTower(
                FutureAgencyTowers.agent(role, 5), owner, TeamId.RED, laneId, position, position);
    }

    private static List<FutureAgencyAgentTower> carried(PlayerLane lane) {
        return lane.towers().stream().filter(FutureAgencyAgentTower.class::isInstance)
                .map(FutureAgencyAgentTower.class::cast)
                .filter(FutureAgencyAgentTower::carriedCopy)
                .toList();
    }

    private static void finishWave(PlayerLane lane) {
        for (var tower : List.copyOf(lane.towers())) {
            tower.onLaneCleared(lane);
        }
    }

    private static void moveAndDamage(FutureAgencyAgentTower agent, PlayerLane lane,
                                      GridPosition position, double health) {
        agent.syncPosition(position);
        agent.syncHealth(health);
        agent.onStateChanged(lane);
        towerEntity(lane, agent).setHealth((float) health);
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, FutureAgencyAgentTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static SemionTowerEntity towerEntity(PlayerLane lane, FutureAgencyAgentTower tower) {
        return (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        return testLane(context, owner, 1);
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner, int laneId) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(
                laneId,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 13))),
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, laneId, owner, context.getLevel(), layout);
    }

    private static GridPosition floor(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void prepareFloor(GameTestHelper context, GridPosition... positions) {
        for (GridPosition position : positions) {
            context.getLevel().setBlock(new BlockPos(position.x(), position.y(), position.z()),
                    Blocks.STONE.defaultBlockState(), 3);
            context.getLevel().setBlock(new BlockPos(position.x(), position.y() + 1, position.z()),
                    Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 0.0001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
