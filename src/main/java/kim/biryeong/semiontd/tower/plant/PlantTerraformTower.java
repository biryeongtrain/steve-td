package kim.biryeong.semiontd.tower.plant;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * Terrain-only plant tower. It never attacks; placing (or upgrading) it converts lane tiles into its
 * family's {@link PlantSoil}, which is the only place the matching combat towers can be planted.
 */
public class PlantTerraformTower extends ProductionTower {
    public PlantTerraformTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public PlantTerraformTower(
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
    public boolean canChaseTargets() {
        return false;
    }

    /**
     * 지형 전용 설비라 몬스터가 아예 무시하고, 어떤 피해도 받지 않습니다. 공격을 못 하는 타워가
     * 두들겨 맞고 사라지는 그림을 없애고, 지형 확보에 쓴 다이아를 돌려받을 수 없게 만듭니다.
     */
    @Override
    public boolean invulnerable() {
        return true;
    }

    @Override
    public boolean drawsAggro() {
        return false;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        terraform(lane);
    }

    /**
     * 판매하거나 교체될 때 자기가 만든 칸을 원래 블록으로 되돌립니다.
     *
     * <p>지형이 남으면 설치 → 환불 → 한 칸 옆에 재설치를 반복해 라인 전체를 헐값에 덮을 수 있습니다.
     * 업그레이드는 {@code replaceTower} 가 이 메서드 직후 새 타워의 {@code onPlaced} 를 부르므로
     * 넓어진 반경으로 다시 깔립니다.
     */
    @Override
    public void onRemoved(PlayerLane lane) {
        PlantSoilStates.releaseFrom(lane, ownerPlayer(), originalPosition());
        super.onRemoved(lane);
    }

    /**
     * Upgrades run through {@code replaceTower}, which calls {@link #onPlaced}; re-running the
     * conversion there is what widens the radius as the terraformer tiers up.
     */
    private void terraform(PlayerLane lane) {
        PlantSoil soil = PlantTowers.soilOf(type());
        int radius = PlantTowers.terraformRadius(type());
        if (soil == null || radius < 0) {
            return;
        }
        PlantSoilStates.terraform(lane, ownerPlayer(), originalPosition(), radius, soil);
    }

    @Override
    public List<String> runtimeDetailLines() {
        PlantSoil soil = PlantTowers.soilOf(type());
        if (soil == null) {
            return List.of();
        }
        return List.of(
                soil.displayName() + " 지형 " + PlantSoilStates.count(ownerPlayer(), soil) + "칸"
        );
    }
}
