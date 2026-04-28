package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public class DirectTower extends Tower {
    public DirectTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        Optional<Monster> target = lane.activeMonsters().stream()
                .filter(Monster::isAlive)
                .max(Comparator.comparingDouble(Monster::laneProgress));
        target.ifPresent(monster -> monster.damage(type().damage()));
        return target.isPresent();
    }
}
