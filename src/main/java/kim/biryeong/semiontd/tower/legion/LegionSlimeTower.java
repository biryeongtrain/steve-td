package kim.biryeong.semiontd.tower.legion;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;

public class LegionSlimeTower extends IllusionSummonerTower {
    private int regenTicks;

    public LegionSlimeTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public LegionSlimeTower(
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
    public void tick(PlayerLane lane) {
        super.tick(lane);
        int interval = TowerBalanceRuntime.abilityTicks(type().id(), "regenIntervalTicks");
        double amount = TowerBalanceRuntime.ability(type().id(), "regenAmount");
        if (health() <= 0.0 || interval <= 0 || amount <= 0.0 || health() >= currentMaxHealth()) {
            return;
        }
        regenTicks++;
        if (regenTicks < interval) {
            return;
        }
        regenTicks = 0;
        syncHealth(health() + amount);
        onStateChanged(lane);
    }
}
