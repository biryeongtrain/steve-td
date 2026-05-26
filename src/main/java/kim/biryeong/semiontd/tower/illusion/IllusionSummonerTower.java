package kim.biryeong.semiontd.tower.illusion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.SummonerTower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public abstract class IllusionSummonerTower extends SummonerTower {
    private final List<CloneInstance> clones = new ArrayList<>();

    protected IllusionSummonerTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    protected IllusionSummonerTower(
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
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        cleanupClones(lane);
        IllusionProfile profile = illusionProfile(lane);
        if (profile.cloneCount() <= 0 || health() <= 0.0) {
            return;
        }

        List<Vec3> offsets = spawnOffsets(profile);
        if (offsets == null || offsets.isEmpty()) {
            offsets = defaultSpawnOffsets(profile);
        }

        for (int index = 0; index < profile.cloneCount(); index++) {
            Vec3 offset = offsets.get(index % offsets.size());
            spawnClone(lane, profile, offset);
        }
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        tickClones(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        cleanupClones(lane);
        super.resetForRound(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        cleanupClones(lane);
        super.onRemoved(lane);
    }

    protected IllusionProfile illusionProfile(PlayerLane lane) {
        String towerId = type().id();
        return new IllusionProfile(
                TowerBalanceRuntime.abilityInt(towerId, "cloneCount", IllusionProfile.DEFAULT_CLONE_COUNT),
                TowerBalanceRuntime.abilityTicks(towerId, "cloneDurationTicks", IllusionProfile.DEFAULT_DURATION_TICKS),
                TowerBalanceRuntime.ability(towerId, "cloneHealthRatio", IllusionProfile.DEFAULT_HEALTH_RATIO),
                TowerBalanceRuntime.ability(towerId, "cloneDamageRatio", IllusionProfile.DEFAULT_DAMAGE_RATIO),
                TowerBalanceRuntime.ability(towerId, "cloneRangeRatio", IllusionProfile.DEFAULT_RANGE_RATIO),
                TowerBalanceRuntime.ability(towerId, "cloneAttackIntervalMultiplier", IllusionProfile.DEFAULT_ATTACK_INTERVAL_MULTIPLIER),
                TowerBalanceRuntime.ability(towerId, "cloneSpawnRadius", IllusionProfile.DEFAULT_SPAWN_RADIUS),
                TowerBalanceRuntime.abilityInt(towerId, "cloneAggroPriorityBonus", IllusionProfile.DEFAULT_AGGRO_PRIORITY_BONUS)
        );
    }

    protected List<Vec3> spawnOffsets(IllusionProfile profile) {
        return defaultSpawnOffsets(profile);
    }

    protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, IllusionRuntimeTower cloneTower) {
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        return false;
    }

    private void spawnClone(PlayerLane lane, IllusionProfile profile, Vec3 offset) {
        Vec3 spawnPosition = new Vec3(
                position().x() + 0.5 + offset.x,
                position().y() + 1.0 + offset.y,
                position().z() + 0.5 + offset.z
        );
        GridPosition clonePosition = GridPosition.from(BlockPos.containing(spawnPosition.x, spawnPosition.y - 1.0, spawnPosition.z));
        IllusionRuntimeTower cloneTower = new IllusionRuntimeTower(
                cloneType(profile),
                ownerPlayer(),
                teamId(),
                laneId(),
                clonePosition
        );

        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, lane.arenaWorld());
        entity.configure(cloneTower, lane.laneLayout());
        entity.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);

        if (lane.arenaWorld().addFreshEntity(entity)) {
            clones.add(new CloneInstance(entity.getId(), profile.durationTicks()));
            onCloneSpawned(lane, entity, cloneTower);
        }
    }

    private TowerType cloneType(IllusionProfile profile) {
        TowerType source = type();
        return new TowerType(
                source.id() + "#illusion",
                source.displayName(),
                source.category() == null ? TowerCategory.DIRECT : source.category(),
                0,
                Math.max(0.01, currentMaxHealth() * profile.healthRatio()),
                Math.max(0.0, source.range() * profile.rangeRatio()),
                Math.max(0.0, source.damage() * profile.damageRatio()),
                Math.max(1, (int) Math.ceil(source.attackIntervalTicks() * profile.attackIntervalMultiplier())),
                aggroPriority() + profile.aggroPriorityBonus(),
                source.description(),
                source.visual(),
                List.of()
        );
    }

    private List<Vec3> defaultSpawnOffsets(IllusionProfile profile) {
        if (profile.cloneCount() <= 0) {
            return List.of();
        }
        List<Vec3> offsets = new ArrayList<>(profile.cloneCount());
        for (int index = 0; index < profile.cloneCount(); index++) {
            double angle = (Math.PI * 2.0 * index) / profile.cloneCount();
            offsets.add(new Vec3(Math.cos(angle) * profile.spawnRadius(), 0.0, Math.sin(angle) * profile.spawnRadius()));
        }
        return offsets;
    }

    private void tickClones(PlayerLane lane) {
        for (int index = clones.size() - 1; index >= 0; index--) {
            CloneInstance clone = clones.get(index);
            var entity = lane.arenaWorld().getEntity(clone.entityId());
            if (!(entity instanceof SemionTowerEntity towerEntity) || towerEntity.isRemoved()) {
                clones.remove(index);
                continue;
            }
            if (!towerEntity.isAlive()) {
                towerEntity.discard();
                clones.remove(index);
                continue;
            }
            if (clone.durationTicks() <= 0) {
                continue;
            }
            clone.incrementAge();
            if (clone.ageTicks() >= clone.durationTicks()) {
                towerEntity.discard();
                clones.remove(index);
            }
        }
    }

    private void cleanupClones(PlayerLane lane) {
        for (CloneInstance clone : clones) {
            var entity = lane.arenaWorld().getEntity(clone.entityId());
            if (entity != null) {
                entity.discard();
            }
        }
        clones.clear();
    }

    private static final class CloneInstance {
        private final int entityId;
        private final int durationTicks;
        private int ageTicks;

        private CloneInstance(int entityId, int durationTicks) {
            this.entityId = entityId;
            this.durationTicks = durationTicks;
        }

        private int entityId() {
            return entityId;
        }

        private int durationTicks() {
            return durationTicks;
        }

        private int ageTicks() {
            return ageTicks;
        }

        private void incrementAge() {
            ageTicks++;
        }
    }
}
