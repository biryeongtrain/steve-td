package kim.biryeong.semionTd.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semionTd.config.WaveMonsterEntry;
import kim.biryeong.semionTd.entity.SemionEntityTypes;
import kim.biryeong.semionTd.entity.defender.DefenderEntity;
import kim.biryeong.semionTd.entity.defender.DefenderEntityState;
import kim.biryeong.semionTd.entity.monster.Monster;
import kim.biryeong.semionTd.entity.monster.MonsterState;
import kim.biryeong.semionTd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semionTd.map.LaneRegionLayout;
import kim.biryeong.semionTd.tower.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class PlayerLane {
    private final TeamId teamId;
    private final int laneId;
    private final UUID ownerPlayer;
    private final ServerLevel arenaWorld;
    private final LaneRegionLayout laneLayout;
    private final List<Monster> activeMonsters = new ArrayList<>();
    private final List<Monster> waveMonsterSpawnQueue = new ArrayList<>();
    private final List<Monster> summonedMonsterSpawnQueue = new ArrayList<>();
    private final List<Tower> towers = new ArrayList<>();
    private final List<DefenderEntity> defenderEntities = new ArrayList<>();
    private boolean clearedThisRound;
    private boolean towersMovedToFinalDefense;

    public PlayerLane(
            TeamId teamId,
            int laneId,
            UUID ownerPlayer,
            ServerLevel arenaWorld,
            LaneRegionLayout laneLayout
    ) {
        this.teamId = teamId;
        this.laneId = laneId;
        this.ownerPlayer = ownerPlayer;
        this.arenaWorld = arenaWorld;
        this.laneLayout = laneLayout;
    }

    public TeamId teamId() {
        return teamId;
    }

    public int laneId() {
        return laneId;
    }

    public UUID ownerPlayer() {
        return ownerPlayer;
    }

    public LaneRegionLayout laneLayout() {
        return laneLayout;
    }

    public ServerLevel arenaWorld() {
        return arenaWorld;
    }

    public List<Monster> activeMonsters() {
        return activeMonsters;
    }

    public List<Tower> towers() {
        return towers;
    }

    public List<DefenderEntity> defenderEntities() {
        return defenderEntities;
    }

    public boolean clearedThisRound() {
        return clearedThisRound;
    }

    public void resetForRound() {
        clearedThisRound = false;
        towersMovedToFinalDefense = false;
        for (Tower tower : towers) {
            tower.resetForRound(this);
        }
    }

    public void enqueueWaveMonster(WaveMonsterEntry entry) {
        for (int i = 0; i < entry.count(); i++) {
            waveMonsterSpawnQueue.add(Monster.fromWaveEntry(entry, teamId, laneId));
        }
    }

    public void enqueueSummonedMonster(Monster monster) {
        summonedMonsterSpawnQueue.add(monster);
    }

    public void addTower(Tower tower) {
        towers.add(tower);
        tower.onPlaced(this);
    }

    public boolean canPlaceTowerAt(BlockPos blockPos) {
        return laneLayout.laneArea().contains(blockPos);
    }

    public boolean hasTowerAt(GridPosition position) {
        return towers.stream().anyMatch(tower -> tower.position().equals(position));
    }

    public Tower towerAt(GridPosition position) {
        return towers.stream()
                .filter(tower -> tower.position().equals(position))
                .findFirst()
                .orElse(null);
    }

    public boolean replaceTower(Tower existing, Tower replacement) {
        int index = towers.indexOf(existing);
        if (index < 0) {
            return false;
        }

        existing.onRemoved(this);
        towers.set(index, replacement);
        replacement.onPlaced(this);
        return true;
    }

    public void addDefenderEntity(DefenderEntity defenderEntity) {
        defenderEntities.add(defenderEntity);
    }

    public List<Monster> tick(MinecraftServer server) {
        return tick(server, null, Map.of());
    }

    public List<Monster> tick(MinecraftServer server, EconomyService economyService, Map<UUID, SemionPlayer> players) {
        spawnQueuedMonster(waveMonsterSpawnQueue);
        spawnQueuedMonster(summonedMonsterSpawnQueue);

        for (Tower tower : towers) {
            tower.tick(this);
        }
        syncTowerStates();
        removeDestroyedTowers();

        List<Monster> reachedBoss = new ArrayList<>();
        Iterator<Monster> iterator = activeMonsters.iterator();
        while (iterator.hasNext()) {
            Monster monster = iterator.next();
            syncMonsterEntityState(monster);
            if (monster.state() == MonsterState.DEAD) {
                if (economyService != null) {
                    economyService.awardMonsterKillReward(monster, players);
                }
                discardMinecraftEntity(monster);
                monster.markRemoved();
                iterator.remove();
                continue;
            }
            if (monster.isRemoved()) {
                discardMinecraftEntity(monster);
                iterator.remove();
            }
        }

        if (!clearedThisRound && activeMonsters.isEmpty()
                && waveMonsterSpawnQueue.isEmpty() && summonedMonsterSpawnQueue.isEmpty()) {
            clearedThisRound = true;
        }

        return reachedBoss;
    }

    public void clearTowers() {
        for (Tower tower : towers) {
            tower.onRemoved(this);
        }
        towers.clear();
    }

    private void removeDestroyedTowers() {
        Iterator<Tower> iterator = towers.iterator();
        while (iterator.hasNext()) {
            Tower tower = iterator.next();
            if (!tower.isDestroyed(this)) {
                continue;
            }

            tower.onRemoved(this);
            iterator.remove();
        }
    }

    public List<DefenderEntity> releaseDefendersToFinalDefense() {
        List<DefenderEntity> released = new ArrayList<>();
        Iterator<DefenderEntity> iterator = defenderEntities.iterator();
        while (iterator.hasNext()) {
            DefenderEntity defenderEntity = iterator.next();
            if (defenderEntity.state() == DefenderEntityState.DEFENDING_LANE) {
                defenderEntity.moveToFinalDefense();
                released.add(defenderEntity);
                iterator.remove();
            }
        }
        return released;
    }

    public void moveTowersToFinalDefense() {
        if (towersMovedToFinalDefense) {
            return;
        }

        List<GridPosition> slots = laneLayout.finalDefenseTowerSlots();
        for (int i = 0; i < towers.size(); i++) {
            Tower tower = towers.get(i);
            GridPosition finalDefensePosition = slots.get(Math.min(i, slots.size() - 1));
            tower.moveToFinalDefense(this, finalDefensePosition);
        }
        towersMovedToFinalDefense = true;
    }

    private void syncTowerStates() {
        for (Tower tower : towers) {
            tower.isDestroyed(this);
        }
    }

    private void spawnQueuedMonster(List<Monster> queue) {
        if (!queue.isEmpty()) {
            Monster monster = queue.removeFirst();
            spawnMinecraftEntity(monster);
            activeMonsters.add(monster);
        }
    }

    private void spawnMinecraftEntity(Monster monster) {
        if (monster.hasMinecraftEntity()) {
            return;
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, arenaWorld);
        entity.configureFrom(monster, laneLayout);

        var spawn = laneLayout.spawn();
        entity.setPos(spawn.x, spawn.y, spawn.z);

        if (arenaWorld.addFreshEntity(entity)) {
            monster.markMinecraftEntitySpawned(entity.getId(), spawn.x, spawn.y, spawn.z);
        }
    }

    private void syncMonsterEntityState(Monster monster) {
        if (!monster.hasMinecraftEntity()) {
            return;
        }

        var entity = arenaWorld.getEntity(monster.minecraftEntityId());
        if (monster.state() == MonsterState.DEAD) {
            if (entity instanceof SemionMonsterEntity monsterEntity && !entity.isRemoved()) {
                monsterEntity.discard();
            }
            return;
        }

        if (!(entity instanceof SemionMonsterEntity monsterEntity) || entity.isRemoved()) {
            if (monster.health() <= 0) {
                monster.syncHealth(0);
            } else {
                monster.markRemoved();
            }
            return;
        }

        monster.syncLaneProgress(laneLayout.progressAt(monsterEntity.position()));
        if (monster.health() < monsterEntity.getHealth()) {
            monsterEntity.setHealth((float) Math.max(0.1, monster.health()));
        } else {
            monster.syncHealth(monsterEntity.getHealth());
        }
    }

    private void discardMinecraftEntity(Monster monster) {
        if (!monster.hasMinecraftEntity()) {
            return;
        }

        var entity = arenaWorld.getEntity(monster.minecraftEntityId());
        if (entity != null) {
            entity.discard();
        }
    }
}