package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import java.util.UUID;

public abstract class ProducerTower extends Tower {
    protected ProducerTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    @Override
    protected abstract boolean execute(PlayerLane lane);
}
