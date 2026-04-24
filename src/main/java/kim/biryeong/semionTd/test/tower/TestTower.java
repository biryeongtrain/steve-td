package kim.biryeong.semionTd.test.tower;

import java.util.OptionalInt;
import java.util.UUID;
import kim.biryeong.semionTd.entity.SemionEntityTypes;
import kim.biryeong.semionTd.game.GridPosition;
import kim.biryeong.semionTd.game.PlayerLane;
import kim.biryeong.semionTd.game.TeamId;
import kim.biryeong.semionTd.test.entity.SemionTestTowerEntity;
import kim.biryeong.semionTd.tower.Tower;
import kim.biryeong.semionTd.tower.TowerType;
import net.minecraft.core.BlockPos;

public final class TestTower extends Tower {
    private int entityId = -1;

    public TestTower(UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        this(TestTowerTypes.TEST_DIRECT, ownerPlayer, teamId, laneId, position);
    }

    public TestTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public TestTower(
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
        SemionTestTowerEntity entity = new SemionTestTowerEntity(SemionEntityTypes.TEST_TOWER, lane.arenaWorld());
        entity.configure(this);
        entity.setPos(
                position().x() + 0.5,
                position().y(),
                position().z() + 0.5
        );

        if (lane.arenaWorld().addFreshEntity(entity)) {
            entityId = entity.getId();
        }
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (entity instanceof SemionTestTowerEntity towerEntity) {
                towerEntity.syncTowerState(this);
                towerEntity.setPos(position().x() + 0.5, position().y(), position().z() + 0.5);
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
    public boolean isDestroyed(PlayerLane lane) {
        if (entityId < 0) {
            return false;
        }

        var entity = lane.arenaWorld().getEntity(entityId);
        if (entity instanceof SemionTestTowerEntity towerEntity) {
            syncHealth(towerEntity.getHealth());
            syncPosition(GridPosition.from(BlockPos.containing(towerEntity.position())));
        } else if (entity == null || entity.isRemoved()) {
            syncHealth(0.0);
        }

        return entity == null || entity.isRemoved() || !entity.isAlive();
    }

    @Override
    public void tick(PlayerLane lane) {
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        return false;
    }
}

