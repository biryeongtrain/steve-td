package kim.biryeong.semiontd.tower.insect;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.world.phys.Vec3;

public final class InsectSpawnerTower extends ProductionTower {
    private int radiusPulseTicks;
    private boolean waveActive;

    public InsectSpawnerTower(
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
        showReviveRadius(lane);
        radiusPulseTicks = InsectBalance.radiusVfxIntervalTicks();
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
        showReviveRadius(lane);
        radiusPulseTicks = InsectBalance.radiusVfxIntervalTicks();
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        radiusPulseTicks = InsectBalance.radiusVfxIntervalTicks();
        super.resetForRound(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (health() <= 0.0 || !waveActive) {
            return;
        }
        if (--radiusPulseTicks <= 0) {
            showReviveRadius(lane);
            radiusPulseTicks = InsectBalance.radiusVfxIntervalTicks();
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        return List.of("<light_purple>부활 반경</light_purple> <white>" + oneDecimal(InsectBalance.spawnerRadius()) + "블록</white>");
    }

    public boolean showDebugRadiusVfx(PlayerLane lane) {
        return showReviveRadius(lane);
    }

    private boolean showReviveRadius(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return false;
        }
        return entityId().stream()
                .mapToObj(lane.arenaWorld()::getEntity)
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast)
                .filter(SemionTowerEntity::isAlive)
                .findFirst()
                .map(entity -> {
                    TowerVfxService.showAreaEffect(
                            entity,
                            AreaEffectIds.tower(this, "revive_radius"),
                            AreaVfxStyles.BUFF,
                            new Vec3(position().x() + 0.5, position().y() + 1.08, position().z() + 0.5),
                            InsectBalance.spawnerRadius(),
                            List.of(),
                            0,
                            0,
                            0
                    );
                    return true;
                })
                .orElse(false);
    }
}
