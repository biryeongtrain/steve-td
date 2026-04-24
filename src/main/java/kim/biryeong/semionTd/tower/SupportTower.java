package kim.biryeong.semionTd.tower;

import kim.biryeong.semionTd.game.GridPosition;
import kim.biryeong.semionTd.game.PlayerLane;
import kim.biryeong.semionTd.game.TeamId;
import java.util.UUID;

public abstract class SupportTower extends Tower {
    protected SupportTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    @Override
    protected abstract boolean execute(PlayerLane lane);
}
