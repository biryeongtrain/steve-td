package kim.biryeong.semiontd.tower.legion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.SummonerTower;
import kim.biryeong.semiontd.tower.Tower;
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

        spawnClones(lane, this, profile, offsets);
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
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        super.moveToFinalDefense(lane, position);
        moveClonesToFinalDefense(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        cleanupClones(lane);
        super.onRemoved(lane);
    }

    protected IllusionProfile illusionProfile(PlayerLane lane) {
        IllusionProfile defaults = defaultIllusionProfile(lane);
        String towerId = type().id();
        return new IllusionProfile(
                TowerBalanceRuntime.abilityInt(towerId, "cloneCount", defaults.cloneCount()),
                TowerBalanceRuntime.abilityTicks(towerId, "cloneDurationTicks", defaults.durationTicks()),
                TowerBalanceRuntime.ability(towerId, "cloneHealthRatio", defaults.healthRatio()),
                TowerBalanceRuntime.ability(towerId, "cloneDamageRatio", defaults.damageRatio()),
                TowerBalanceRuntime.ability(towerId, "cloneRangeRatio", defaults.rangeRatio()),
                TowerBalanceRuntime.ability(towerId, "cloneAttackIntervalMultiplier", defaults.attackIntervalMultiplier()),
                TowerBalanceRuntime.ability(towerId, "cloneSpawnRadius", defaults.spawnRadius()),
                TowerBalanceRuntime.abilityInt(towerId, "cloneAggroPriorityBonus", defaults.aggroPriorityBonus())
        );
    }

    protected IllusionProfile defaultIllusionProfile(PlayerLane lane) {
        return IllusionProfile.defaults();
    }

    protected List<Vec3> spawnOffsets(IllusionProfile profile) {
        return defaultSpawnOffsets(profile);
    }

    protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, Tower cloneTower) {
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        return false;
    }

    protected final void spawnClones(PlayerLane lane, Tower sourceTower, IllusionProfile profile) {
        List<Vec3> offsets = spawnOffsets(profile);
        if (offsets == null || offsets.isEmpty()) {
            offsets = defaultSpawnOffsets(profile);
        }
        spawnClones(lane, sourceTower, profile, offsets);
    }

    private void spawnClones(PlayerLane lane, Tower sourceTower, IllusionProfile profile, List<Vec3> offsets) {
        if (sourceTower == null || profile.cloneCount() <= 0 || sourceTower.health() <= 0.0) {
            return;
        }
        for (int index = 0; index < profile.cloneCount(); index++) {
            Vec3 offset = offsets.get(index % offsets.size());
            spawnClone(lane, sourceTower, profile, offset);
        }
    }

    private void spawnClone(PlayerLane lane, Tower sourceTower, IllusionProfile profile, Vec3 offset) {
        Vec3 spawnPosition = new Vec3(
                sourceTower.position().x() + 0.5 + offset.x,
                sourceTower.position().y() + 1.0 + offset.y,
                sourceTower.position().z() + 0.5 + offset.z
        );
        GridPosition clonePosition = GridPosition.from(BlockPos.containing(spawnPosition.x, spawnPosition.y - 1.0, spawnPosition.z));
        Tower cloneTower = createCloneTower(sourceTower, profile, clonePosition);

        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, lane.arenaWorld());
        entity.configure(cloneTower, lane.laneLayout());
        entity.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);

        if (lane.arenaWorld().addFreshEntity(entity)) {
            clones.add(new CloneInstance(entity.getId(), profile.durationTicks()));
            onCloneSpawned(lane, entity, cloneTower);
        }
    }

    private Tower createCloneTower(Tower sourceTower, IllusionProfile profile, GridPosition clonePosition) {
        TowerType cloneType = cloneType(sourceTower, profile);
        return ProductionTowerCatalog.entry(sourceTower.type())
                .map(entry -> entry.factory().create(
                        cloneType,
                        sourceTower.ownerPlayer(),
                        sourceTower.teamId(),
                        sourceTower.laneId(),
                        clonePosition,
                        clonePosition
                ))
                .orElseGet(() -> new IllusionRuntimeTower(
                        fallbackCloneType(sourceTower, profile),
                        sourceTower.ownerPlayer(),
                        sourceTower.teamId(),
                        sourceTower.laneId(),
                        clonePosition
                ));
    }

    private TowerType cloneType(Tower sourceTower, IllusionProfile profile) {
        TowerType source = sourceTower.type();
        return new TowerType(
                source.id(),
                source.displayName(),
                source.category() == null ? TowerCategory.DIRECT : source.category(),
                0,
                Math.max(0.01, sourceTower.currentMaxHealth() * profile.healthRatio()),
                Math.max(0.0, source.range() * profile.rangeRatio()),
                Math.max(0.0, source.damage() * profile.damageRatio()),
                Math.max(1, (int) Math.ceil(source.attackIntervalTicks() * profile.attackIntervalMultiplier())),
                sourceTower.aggroPriority() + profile.aggroPriorityBonus(),
                source.description(),
                source.visual(),
                List.of()
        );
    }

    private TowerType fallbackCloneType(Tower sourceTower, IllusionProfile profile) {
        TowerType cloneType = cloneType(sourceTower, profile);
        return new TowerType(
                sourceTower.type().id() + "#illusion",
                cloneType.displayName(),
                cloneType.category(),
                cloneType.mineralCost(),
                cloneType.maxHealth(),
                cloneType.range(),
                cloneType.damage(),
                cloneType.attackIntervalTicks(),
                cloneType.aggroPriority(),
                cloneType.description(),
                cloneType.visual(),
                cloneType.upgradeOptions()
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

    protected final void tickClones(PlayerLane lane) {
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
            Tower cloneTower = towerEntity.runtimeTower();
            if (cloneTower == null || cloneTower.health() <= 0.0) {
                towerEntity.discard();
                clones.remove(index);
                continue;
            }
            cloneTower.syncHealth(towerEntity.getHealth());
            cloneTower.syncPosition(GridPosition.from(BlockPos.containing(
                    towerEntity.getX(),
                    towerEntity.getY() - 1.0,
                    towerEntity.getZ()
            )));
            cloneTower.tick(lane);
            towerEntity.syncTowerState(cloneTower);
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

    private void moveClonesToFinalDefense(PlayerLane lane) {
        if (clones.isEmpty()) {
            return;
        }

        List<GridPosition> slots = lane.laneLayout().finalDefenseTowerSlots();
        if (slots.isEmpty()) {
            return;
        }

        int slotIndex = lane.towers().size();
        for (int index = 0; index < clones.size(); index++) {
            CloneInstance clone = clones.get(index);
            var entity = lane.arenaWorld().getEntity(clone.entityId());
            if (!(entity instanceof SemionTowerEntity towerEntity) || towerEntity.isRemoved() || !towerEntity.isAlive()) {
                continue;
            }
            Tower cloneTower = towerEntity.runtimeTower();
            if (cloneTower == null) {
                continue;
            }

            GridPosition finalDefensePosition = finalDefenseTowerPosition(
                    lane,
                    slots.get(Math.min(slotIndex + index, slots.size() - 1))
            );
            cloneTower.moveToFinalDefense(lane, finalDefensePosition);
            towerEntity.syncTowerState(cloneTower);
            towerEntity.setPos(
                    finalDefensePosition.x() + 0.5,
                    finalDefensePosition.y() + 1.0,
                    finalDefensePosition.z() + 0.5
            );
            towerEntity.getNavigation().stop();
        }
    }

    private GridPosition finalDefenseTowerPosition(PlayerLane lane, GridPosition slot) {
        BlockPos slotPos = new BlockPos(slot.x(), slot.y(), slot.z());
        BlockPos below = slotPos.below();
        if (lane.arenaWorld().getBlockState(slotPos).isAir() && !lane.arenaWorld().getBlockState(below).isAir()) {
            return GridPosition.from(below);
        }
        return slot;
    }

    protected final void cleanupClones(PlayerLane lane) {
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
