package kim.biryeong.semiontd.tower.legion;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

public class LegionGlobalIllusionTower extends IllusionSummonerTower {
    public LegionGlobalIllusionTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public LegionGlobalIllusionTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
    }

    @Override
    public void onDeath(PlayerLane lane) {
        IllusionProfile profile = illusionProfile(lane);
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower == this || !ownerPlayer().equals(tower.ownerPlayer()) || tower.health() <= 0.0) {
                continue;
            }
            spawnClones(lane, tower, profile);
        }
    }

    @Override
    public void tick(PlayerLane lane) {
        if (health() > 0.0) {
            super.tick(lane);
            return;
        }
        tickClones(lane);
    }
}
