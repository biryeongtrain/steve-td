package kim.biryeong.semiontd.tower.animal;

import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

abstract class AnimalStackTower extends EntityBackedTower {
    private int currentStacks;

    protected AnimalStackTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    protected AnimalStackTower(
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
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        refreshAnimalStacks(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        refreshAnimalStacks(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        refreshAnimalStacks(lane);
        super.tick(lane);
    }

    protected final int currentStacks() {
        return currentStacks;
    }

    protected final boolean atMaxStacks() {
        return currentStacks >= maxStacks();
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        return java.util.List.of("무리 스택 " + currentStacks + "/" + maxStacks());
    }

    protected final int refreshStacks(PlayerLane lane) {
        int previousStacks = currentStacks;
        currentStacks = countMatchingTowers(lane);
        onStacksChanged(lane, previousStacks, currentStacks);
        return currentStacks;
    }

    protected void onStacksChanged(PlayerLane lane, int previousStacks, int currentStacks) {
    }

    protected abstract boolean isStackFamily(Tower tower);

    protected abstract int maxStacks();

    private int countMatchingTowers(PlayerLane lane) {
        if (lane == null) {
            return 0;
        }
        long count = lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(this::isStackFamily)
                .count();
        return Math.min(maxStacks(), (int) count);
    }

    static void refreshAnimalStacks(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof AnimalStackTower animalTower) {
                animalTower.refreshStacks(lane);
            }
        }
    }
}
