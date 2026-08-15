package kim.biryeong.semiontd.tower;

import java.util.OptionalInt;
import java.util.UUID;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.phys.Vec3;

/**
 * Tower runtime backed by a {@link SemionTowerEntity}; this does not imply that the tower attacks.
 */
public abstract class EntityBackedTower extends Tower {
    private int entityId = -1;
    private SemionTowerEntity entity;

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
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        spawnEntity(lane);
    }

    private void spawnEntity(PlayerLane lane) {
        SemionTowerEntity spawnedEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, lane.arenaWorld());
        spawnedEntity.configure(this, lane.laneLayout());
        spawnedEntity.setPos(anchorX(), anchorY(), anchorZ());

        if (lane.arenaWorld().addFreshEntity(spawnedEntity)) {
            entity = spawnedEntity;
            entityId = spawnedEntity.getId();
            configureEntityAfterSpawn(spawnedEntity, lane);
        }
    }

    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        entityId().ifPresent(id -> {
            var currentEntity = lane.arenaWorld().getEntity(id);
            if (currentEntity instanceof SemionTowerEntity towerEntity) {
                towerEntity.syncTowerState(this);
                towerEntity.setPos(anchorX(), anchorY(), anchorZ());
            }
        });
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        if (lane != null && lane.arenaWorld() != null) {
            entityId().ifPresent(id -> {
                var currentEntity = lane.arenaWorld().getEntity(id);
                if (currentEntity != null) {
                    currentEntity.discard();
                }
            });
        }
        entity = null;
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
        } else if (lane != null && lane.arenaWorld() != null) {
            entityId().ifPresent(id -> {
                if (lane.arenaWorld().getEntity(id) instanceof SemionTowerEntity towerEntity) {
                    towerEntity.getNavigation().stop();
                    towerEntity.getMoveControl().setWantedPosition(anchorX(), anchorY(), anchorZ(), 0.0);
                    towerEntity.setDeltaMovement(Vec3.ZERO);
                    towerEntity.recordCurrentAttackTarget(null);
                    towerEntity.teleportTo(anchorX(), anchorY(), anchorZ());
                    // A direct MoveControl request can survive Navigation#stop. Consume the
                    // zero-distance request now so it cannot move the tower on the next tick.
                    towerEntity.getMoveControl().tick();
                }
            });
        }
    }

    @Override
    public boolean isDestroyed(PlayerLane lane) {
        if (entityId < 0) {
            return super.isDestroyed(lane);
        }
        if (lane == null || lane.arenaWorld() == null) {
            return super.isDestroyed(lane);
        }

        var currentEntity = lane.arenaWorld().getEntity(entityId);
        if (!(currentEntity instanceof SemionTowerEntity)
                && entity != null
                && entity.getId() == entityId
                && entity.level() == lane.arenaWorld()
                && !entity.isRemoved()) {
            currentEntity = entity;
        }
        if (currentEntity instanceof SemionTowerEntity towerEntity) {
            syncHealth(towerEntity.getHealth());
            syncPosition(GridPosition.from(BlockPos.containing(
                    towerEntity.getX(),
                    towerEntity.getY() - entityAnchorYOffset(),
                    towerEntity.getZ()
            )));
            return !towerEntity.isAlive();
        }
        if (entityWasUnloaded()) {
            return super.isDestroyed(lane);
        }
        syncHealth(0.0);
        return true;
    }

    private boolean shouldRespawnEntity(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return false;
        }
        if (entityId < 0) {
            return true;
        }

        var currentEntity = lane.arenaWorld().getEntity(entityId);
        return !(currentEntity instanceof SemionTowerEntity towerEntity)
                || towerEntity.isRemoved()
                || !towerEntity.isAlive();
    }

    @Override
    public void tick(PlayerLane lane) {
        if (entityWasUnloaded() && isAnchorChunkLoaded(lane)) {
            spawnEntity(lane);
        }
        super.tick(lane);
    }

    protected final boolean entityWasUnloaded() {
        if (entity == null || !entity.isRemoved()) {
            return false;
        }
        RemovalReason reason = entity.getRemovalReason();
        return reason == RemovalReason.UNLOADED_TO_CHUNK || reason == RemovalReason.UNLOADED_WITH_PLAYER;
    }

    private boolean isAnchorChunkLoaded(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return false;
        }
        BlockPos anchor = BlockPos.containing(anchorX(), anchorY(), anchorZ());
        return lane.arenaWorld().getChunkSource().hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
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
