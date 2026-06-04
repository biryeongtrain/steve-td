package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.defender.DefenderEntity;
import kim.biryeong.semiontd.entity.defender.DefenderEntityState;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterState;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class PlayerLane {
    private static final double FINAL_DEFENSE_MONSTER_PROGRESS = 0.90;

    private final TeamId teamId;
    private final int laneId;
    private final UUID ownerPlayer;
    private final ServerLevel arenaWorld;
    private final LaneRegionLayout laneLayout;
    private final WaveSpawnPositionPolicy waveSpawnPositionPolicy;
    private final List<Monster> activeMonsters = new ArrayList<>();
    private final List<Monster> waveMonsterSpawnQueue = new ArrayList<>();
    private final List<Monster> summonedMonsterSpawnQueue = new ArrayList<>();
    private final List<Monster> nextRoundSummonedMonsterSpawnQueue = new ArrayList<>();
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
        this.waveSpawnPositionPolicy = new WaveSpawnPositionPolicy(laneLayout);
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

    public int queuedSummonCount() {
        return summonedMonsterSpawnQueue.size();
    }

    public double queuedSummonThreat() {
        return summonedMonsterSpawnQueue.stream()
                .mapToDouble(Monster::attributionThreat)
                .sum();
    }

    public int pendingNextRoundSummonCount() {
        return nextRoundSummonedMonsterSpawnQueue.size();
    }

    public double pendingNextRoundSummonThreat() {
        return nextRoundSummonedMonsterSpawnQueue.stream()
                .mapToDouble(Monster::attributionThreat)
                .sum();
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
        for (DefenderEntity defenderEntity : defenderEntities) {
            defenderEntity.remove();
        }
        defenderEntities.clear();
        for (Tower tower : towers) {
            tower.resetForRound(this);
        }
        moveNextRoundSummonsToCurrentRound();
    }

    public void enqueueWaveMonster(WaveMonsterEntry entry) {
        for (int i = 0; i < entry.count(); i++) {
            waveMonsterSpawnQueue.add(Monster.fromWaveEntry(entry, teamId, laneId));
        }
    }

    public void enqueueSummonedMonster(Monster monster) {
        summonedMonsterSpawnQueue.add(monster);
    }

    public void enqueueNextRoundSummonedMonster(Monster monster) {
        nextRoundSummonedMonsterSpawnQueue.add(monster);
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

    public boolean removeTower(Tower tower) {
        if (!towers.remove(tower)) {
            return false;
        }
        tower.onRemoved(this);
        return true;
    }

    public boolean killTower(Tower tower) {
        if (!towers.contains(tower)) {
            return false;
        }
        tower.syncHealth(0.0);
        tower.notifyDeath(this);
        tower.onRemoved(this);
        return true;
    }

    public void markWaveStarted(int currentRound) {
        for (Tower tower : towers) {
            tower.markWaveStarted(currentRound);
            tower.onWaveStarted(this, currentRound);
        }
    }

    public void addDefenderEntity(DefenderEntity defenderEntity) {
        defenderEntities.add(defenderEntity);
    }

    public void tick(MinecraftServer server) {
        tick(server, null, Map.of());
    }

    public void tick(MinecraftServer server, EconomyService economyService, Map<UUID, SemionPlayer> players) {
        spawnQueuedMonster(waveMonsterSpawnQueue, players, true);
        spawnQueuedMonster(summonedMonsterSpawnQueue, players, false);

        for (Tower tower : List.copyOf(towers)) {
            if (towers.contains(tower)) {
                tower.tick(this);
            }
        }
        syncTowerStates();

        Iterator<Monster> iterator = activeMonsters.iterator();
        while (iterator.hasNext()) {
            Monster monster = iterator.next();
            syncMonsterEntityState(monster, players);
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
    }

    public void clearTowers() {
        for (Tower tower : towers) {
            tower.onRemoved(this);
        }
        towers.clear();
    }

    public List<DefenderEntity> forceFinalDefense() {
        spawnAllQueuedMonsters(waveMonsterSpawnQueue);
        spawnAllQueuedMonsters(summonedMonsterSpawnQueue);

        Vec3 finalDefenseMonsterPosition = laneLayout.positionAt(FINAL_DEFENSE_MONSTER_PROGRESS);
        for (Monster monster : activeMonsters) {
            monster.syncLaneProgress(FINAL_DEFENSE_MONSTER_PROGRESS);
            if (monster.hasMinecraftEntity()) {
                var entity = arenaWorld.getEntity(monster.minecraftEntityId());
                if (entity instanceof SemionMonsterEntity monsterEntity && !monsterEntity.isRemoved()) {
                    monsterEntity.teleportTo(finalDefenseMonsterPosition.x, finalDefenseMonsterPosition.y, finalDefenseMonsterPosition.z);
                    monsterEntity.getNavigation().stop();
                }
            }
        }

        moveTowersToFinalDefense();
        List<DefenderEntity> defenders = releaseDefendersToFinalDefense();
        for (DefenderEntity defender : defenders) {
            defender.arriveFinalDefense();
        }
        return defenders;
    }

    public void disableMonsters() {
        for (Monster monster : activeMonsters) {
            discardMinecraftEntity(monster);
            monster.markRemoved();
        }
        activeMonsters.clear();
        waveMonsterSpawnQueue.clear();
        summonedMonsterSpawnQueue.clear();
        nextRoundSummonedMonsterSpawnQueue.clear();
        clearedThisRound = true;
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
            GridPosition finalDefensePosition = finalDefenseTowerPosition(slots.get(Math.min(i, slots.size() - 1)));
            tower.moveToFinalDefense(this, finalDefensePosition);
        }
        towersMovedToFinalDefense = true;
    }

    private GridPosition finalDefenseTowerPosition(GridPosition slot) {
        BlockPos slotPos = new BlockPos(slot.x(), slot.y(), slot.z());
        BlockPos below = slotPos.below();
        if (arenaWorld.getBlockState(slotPos).isAir() && !arenaWorld.getBlockState(below).isAir()) {
            return GridPosition.from(below);
        }
        return slot;
    }

    private void moveNextRoundSummonsToCurrentRound() {
        if (nextRoundSummonedMonsterSpawnQueue.isEmpty()) {
            return;
        }
        summonedMonsterSpawnQueue.addAll(nextRoundSummonedMonsterSpawnQueue);
        nextRoundSummonedMonsterSpawnQueue.clear();
    }

    private void syncTowerStates() {
        for (Tower tower : towers) {
            if (tower.isDestroyed(this)) {
                tower.notifyDeath(this);
            }
        }
    }

    private void spawnQueuedMonster(List<Monster> queue, Map<UUID, SemionPlayer> players, boolean distributeWaveSpawn) {
        if (!queue.isEmpty()) {
            Monster monster = queue.removeFirst();
            spawnMinecraftEntity(monster, distributeWaveSpawn);
            recordIncomingThreat(monster, players);
            activeMonsters.add(monster);
        }
    }

    private void spawnAllQueuedMonsters(List<Monster> queue) {
        while (!queue.isEmpty()) {
            Monster monster = queue.removeFirst();
            spawnMinecraftEntity(monster, false);
            activeMonsters.add(monster);
        }
    }

    private void spawnMinecraftEntity(Monster monster, boolean distributeWaveSpawn) {
        if (monster.hasMinecraftEntity()) {
            return;
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, arenaWorld);
        entity.configureFrom(monster, laneLayout);

        var spawn = distributeWaveSpawn ? waveSpawnPositionPolicy.next() : laneLayout.spawn();
        entity.setPos(spawn.x, spawn.y, spawn.z);

        if (arenaWorld.addFreshEntity(entity)) {
            monster.markMinecraftEntitySpawned(entity.getId(), spawn.x, spawn.y, spawn.z);
        }
    }

    private void syncMonsterEntityState(Monster monster, Map<UUID, SemionPlayer> players) {
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
        if (monster.state() == MonsterState.REACHED_BOSS) {
            recordLaneLeak(monster, players);
        }
    }

    private void recordIncomingThreat(Monster monster, Map<UUID, SemionPlayer> players) {
        if (monster == null || players == null) {
            return;
        }
        SemionPlayer laneOwner = players.get(ownerPlayer);
        if (laneOwner != null) {
            laneOwner.matchStats().recordOwnLaneIncomingThreat(monster.attributionThreat(), monster.ownerPlayer().isPresent());
        }
    }

    private void recordLaneLeak(Monster monster, Map<UUID, SemionPlayer> players) {
        if (monster == null || monster.laneLeakRecorded() || players == null) {
            return;
        }
        double threat = monster.attributionThreat();
        SemionPlayer laneOwner = players.get(ownerPlayer);
        if (laneOwner != null) {
            laneOwner.matchStats().recordOwnLaneLeakedThreat(threat);
        }
        monster.ownerPlayer()
                .map(players::get)
                .ifPresent(owner -> owner.matchStats().recordIncomeAttackSuccessThreat(threat));
        monster.markLaneLeakRecorded();
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
