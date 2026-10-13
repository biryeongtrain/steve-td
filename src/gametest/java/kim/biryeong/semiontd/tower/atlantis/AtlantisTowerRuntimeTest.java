package kim.biryeong.semiontd.tower.atlantis;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.AtlantisTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.tower.atlantis.AtlantisPressure;
import kim.biryeong.semiontd.tower.atlantis.AtlantisStates;
import kim.biryeong.semiontd.tower.atlantis.AtlantisTower;
import kim.biryeong.semiontd.tower.atlantis.AtlantisTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class AtlantisTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void atlantisStateClearsOnMatchStartEliminationAndClose(GameTestHelper context) {
        UUID playerId = stableUuid("atlantis-state-lifecycle-owner");
        UUID monsterId = stableUuid("atlantis-state-lifecycle-monster");
        GridPosition staleSource = new GridPosition(0, 64, 0);
        AtlantisPressure.addStacks(monsterId, playerId, staleSource, 3, 40.0, 10, 100);

        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AtlantisTowerJob.ID);
        boolean closed = false;
        try {
            if (!assertEquals(context, 0, AtlantisPressure.stacks(playerId, monsterId),
                    "Match start should clear pressure from a previous game.")) {
                return;
            }

            PlayerLane lane = redLane(game, 1);
            GridPosition turtlePosition = GridPosition.from(towerPlacementPos(lane));
            AtlantisTower turtle = new AtlantisTower(
                    TowerBalanceRuntime.resolve(AtlantisTowers.TURTLE_T1),
                    playerId,
                    TeamId.RED,
                    1,
                    turtlePosition
            );
            lane.addTower(turtle);
            AtlantisPressure.addStacks(monsterId, playerId, turtlePosition, 3, 40.0, 10, 100);
            new AtlantisTowerJob().onEliminated(new JobContext(game, game.players().get(playerId)));
            if (!assertEquals(context, 0, AtlantisStates.zoneCount(playerId),
                    "Elimination should clear pressure zones.")) {
                return;
            }
            if (!assertEquals(context, 0, AtlantisPressure.stacks(playerId, monsterId),
                    "Elimination should clear carried pressure.")) {
                return;
            }

            AtlantisStates.rebuild(playerId, lane);
            AtlantisPressure.addStacks(monsterId, playerId, turtlePosition, 3, 40.0, 10, 100);
            game.close();
            closed = true;
            if (!assertEquals(context, 0, AtlantisStates.zoneCount(playerId),
                    "Game close should clear pressure zones.")) {
                return;
            }
            if (!assertEquals(context, 0, AtlantisPressure.stacks(playerId, monsterId),
                    "Game close should clear carried pressure for player reuse.")) {
                return;
            }
            context.succeed();
        } finally {
            if (!closed) {
                game.close();
            }
            AtlantisStates.clear(playerId);
            AtlantisPressure.clearPlayer(playerId);
        }
    }
}
