package kim.biryeong.semiontd.tower;

import java.util.OptionalInt;
import java.util.UUID;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.core.BlockPos;

/**
 * Tower runtime backed by a {@link SemionTowerEntity}; this does not imply that the tower attacks.
 */
public abstract class EntityBackedTower extends Tower {
    private int entityId = -1;

    protected EntityBackedTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    protected EntityBackedTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public OptionalInt entityId() {
        return entityId >= 0 ? OptionalInt.of(entityId) : OptionalInt.empty();
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, lane.arenaWorld());
        entity.configure(this, lane.laneLayout());
        entity.setPos(anchorX(), anchorY(), anchorZ());

        if (lane.arenaWorld().addFreshEntity(entity)) {
            entityId = entity.getId();
            configureEntityAfterSpawn(entity, lane);
        }
    }

    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (entity instanceof SemionTowerEntity towerEntity) {
                towerEntity.syncTowerState(this);
                towerEntity.setPos(anchorX(), anchorY(), anchorZ());
            }
        });
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        });
        entityId = -1;
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        boolean shouldRespawn = shouldRespawnEntity(lane);
        if (shouldRespawn) {
            onRemoved(lane);
        }
        super.resetForRound(lane);
        if (shouldRespawn) {
            onPlaced(lane);
        }
    }

    @Override
    public boolean isDestroyed(PlayerLane lane) {
        if (entityId < 0) {
            return super.isDestroyed(lane);
        }

        var entity = lane.arenaWorld().getEntity(entityId);
        if (entity instanceof SemionTowerEntity towerEntity) {
            syncHealth(towerEntity.getHealth());
            syncPosition(GridPosition.from(BlockPos.containing(
                    towerEntity.getX(),
                    towerEntity.getY() - entityAnchorYOffset(),
                    towerEntity.getZ()
            )));
        } else if (entity == null || entity.isRemoved()) {
            syncHealth(0.0);
        }

        return entity == null || entity.isRemoved() || !entity.isAlive();
    }

    private boolean shouldRespawnEntity(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return false;
        }
        if (entityId < 0) {
            return true;
        }

        var entity = lane.arenaWorld().getEntity(entityId);
        return !(entity instanceof SemionTowerEntity towerEntity) || towerEntity.isRemoved() || !towerEntity.isAlive();
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        return false;
    }

    private double anchorX() {
        return position().x() + 0.5;
    }

    protected double anchorY() {
        return position().y() + entityAnchorYOffset();
    }

    protected double entityAnchorYOffset() {
        return 1.0;
    }

    private double anchorZ() {
        return position().z() + 0.5;
    }
}
