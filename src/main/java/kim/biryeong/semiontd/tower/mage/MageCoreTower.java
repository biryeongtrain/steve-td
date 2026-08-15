package kim.biryeong.semiontd.tower.mage;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;

public final class MageCoreTower extends ProductionTower {
    private boolean resetting;

    public MageCoreTower(TowerType type, UUID owner, TeamId team, int laneId, GridPosition position) {
        super(type, owner, team, laneId, position);
    }

    public MageCoreTower(TowerType type, UUID owner, TeamId team, int laneId, GridPosition original, GridPosition current) {
        super(type, owner, team, laneId, original, current);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        MageStates.state(ownerPlayer()).grantStartingMana();
        super.onPlaced(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        if (!resetting && lane != null && !lane.towers().contains(this)) {
            MageStates.state(ownerPlayer()).clearMana();
            MageTowerRuntime.cancelReservations(lane, ownerPlayer());
        }
    }

    @Override
    public void onDeath(PlayerLane lane) {
        MageStates.state(ownerPlayer()).loseRatio(MageBalance.coreBreakManaLossRatio());
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        resetting = true;
        try {
            super.resetForRound(lane);
        } finally {
            resetting = false;
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        MageStates.PlayerState state = MageStates.state(ownerPlayer());
        return List.of(
                "<aqua>마나</aqua> <white>" + state.mana() + "/" + state.capacity() + "</white>",
                "<green>라운드 생산</green> <white>+" + MageBalance.coreMana() + "</white>",
                "<red>파괴 마나 손실</red> <white>"
                        + Math.round(MageBalance.coreBreakManaLossRatio() * 100.0) + "%</white>"
        );
    }
}
