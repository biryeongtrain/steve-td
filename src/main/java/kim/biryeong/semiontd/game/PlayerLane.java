package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.config.WaveSpawnMode;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.defender.DefenderEntity;
import kim.biryeong.semiontd.entity.defender.DefenderEntityState;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterState;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class PlayerLane {
    private static final double FINAL_DEFENSE_MONSTER_PROGRESS = Monster.FINAL_DEFENSE_PROGRESS;
    private static final int FORCED_FINAL_DEFENSE_COLUMNS = 5;
    private static final int FORCED_FINAL_DEFENSE_ROWS = 20;
    private static final double FORCED_FINAL_DEFENSE_COLUMN_SPACING = 0.9;
    private static final double FORCED_FINAL_DEFENSE_ROW_SPACING = 0.9;

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
    private boolean leakedThisRound;
    private boolean towersMovedToFinalDefense;
    private boolean laneDefenseBroken;
    private int waveMonsterSpawnIntervalTicks = 1;
    private int waveMonsterSpawnCooldownTicks;
    private FinalDefenseSlotAllocator finalDefenseSlotAllocator;

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
        this.finalDefenseSlotAllocator = FinalDefenseSlotAllocator.fromLayouts(List.of(laneLayout));
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

    public boolean leakedThisRound() {
        return leakedThisRound;
    }

    public boolean laneDefenseBroken() {
        return laneDefenseBroken;
    }

    public void resetForRound() {
        clearedThisRound = false;
        leakedThisRound = false;
        towersMovedToFinalDefense = false;
        laneDefenseBroken = false;
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

    public void enqueueWave(List<WaveMonsterEntry> entries, WaveSpawnMode spawnMode, int spawnIntervalTicks) {
        waveMonsterSpawnIntervalTicks = Math.max(1, spawnIntervalTicks);
        waveMonsterSpawnCooldownTicks = 0;
        for (WaveMonsterEntry entry : expandWaveEntries(entries, spawnMode)) {
            waveMonsterSpawnQueue.add(Monster.fromWaveEntry(entry, teamId, laneId));
        }
    }

    static List<WaveMonsterEntry> expandWaveEntries(List<WaveMonsterEntry> entries, WaveSpawnMode spawnMode) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<WaveMonsterEntry> expanded = new ArrayList<>();
        if (spawnMode != WaveSpawnMode.ROUND_ROBIN) {
            for (WaveMonsterEntry entry : entries) {
                for (int i = 0; i < entry.count(); i++) {
                    expanded.add(entry);
                }
            }
            return expanded;
        }

        int maxCount = entries.stream().mapToInt(WaveMonsterEntry::count).max().orElse(0);
        for (int index = 0; index < maxCount; index++) {
            for (WaveMonsterEntry entry : entries) {
                if (index < entry.count()) {
                    expanded.add(entry);
                }
            }
        }
        return expanded;
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
        if (tower.notifyDeath(this)) {
            notifyNearbyTowerDeath(tower);
        }
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
        tick(server, economyService, players, MonsterScalingConfig.defaultConfig(), 0);
    }

    public void tick(
            MinecraftServer server,
            EconomyService economyService,
            Map<UUID, SemionPlayer> players,
            MonsterScalingConfig monsterScalingConfig,
            int roundElapsedTicks
    ) {
        spawnQueuedWaveMonster(players);
        spawnQueuedMonster(summonedMonsterSpawnQueue, players, false);

        tickTowers();

        Iterator<Monster> iterator = activeMonsters.iterator();
        while (iterator.hasNext()) {
            Monster monster = iterator.next();
            syncMonsterEntityState(monster, players, monsterScalingConfig, roundElapsedTicks);
            if (monster.state() == MonsterState.DEAD) {
                if (economyService != null) {
                    economyService.awardMonsterKillReward(monster, players);
                }
                IllagerRaidStates.onMonsterKilled(players, monster);
                notifyNearbyMonsterDeath(monster, monsterDeathPosition(monster));
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
        IllagerRaidStates.playPendingActivationEffects(server, this);
    }

    void tickTowers() {
        for (Tower tower : List.copyOf(towers)) {
            if (towers.contains(tower)) {
                tower.tick(this);
            }
        }
        syncTowerStates();
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

        for (int index = 0; index < activeMonsters.size(); index++) {
            Monster monster = activeMonsters.get(index);
            Vec3 finalDefenseMonsterPosition = forcedFinalDefensePosition(index);
            monster.enterFinalDefenseCombat();
            monster.syncLaneProgress(laneLayout.progressAt(finalDefenseMonsterPosition));
            if (monster.hasMinecraftEntity()) {
                var entity = arenaWorld.getEntity(monster.minecraftEntityId());
                if (entity instanceof SemionMonsterEntity monsterEntity && !monsterEntity.isRemoved()) {
                    Vec3 movement = finalDefenseMonsterPosition.subtract(monsterEntity.position());
                    if (!arenaWorld.noCollision(monsterEntity, monsterEntity.getBoundingBox().move(movement))) {
                        finalDefenseMonsterPosition = laneLayout.positionAt(monster.laneProgress());
                    }
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

    Vec3 forcedFinalDefensePosition(int index) {
        int positionIndex = Math.floorMod(index, FORCED_FINAL_DEFENSE_COLUMNS * FORCED_FINAL_DEFENSE_ROWS);
        int row = positionIndex / FORCED_FINAL_DEFENSE_COLUMNS;
        int column = positionIndex % FORCED_FINAL_DEFENSE_COLUMNS;
        Vec3 center = laneLayout.positionAt(FINAL_DEFENSE_MONSTER_PROGRESS);
        Vec3 previous = laneLayout.positionAt(FINAL_DEFENSE_MONSTER_PROGRESS - 0.10);
        Vec3 direction = center.subtract(previous);
        Vec3 forward = direction.horizontalDistanceSqr() <= 1.0E-6
                ? new Vec3(0.0, 0.0, 1.0)
                : new Vec3(direction.x, 0.0, direction.z).normalize();
        center = center.subtract(forward.scale(row * FORCED_FINAL_DEFENSE_ROW_SPACING));
        Vec3 lateral = direction.horizontalDistanceSqr() <= 1.0E-6
                ? new Vec3(1.0, 0.0, 0.0)
                : new Vec3(-forward.z, 0.0, forward.x);
        double lateralOffset = (column - (FORCED_FINAL_DEFENSE_COLUMNS - 1) / 2.0)
                * FORCED_FINAL_DEFENSE_COLUMN_SPACING;
        Vec3 candidate = center.add(lateral.scale(lateralOffset));
        var bounds = laneLayout.laneArea();
        return new Vec3(
                Math.max(bounds.min().getX() + 0.5, Math.min(bounds.max().getX() + 0.5, candidate.x)),
                candidate.y,
                Math.max(bounds.min().getZ() + 0.5, Math.min(bounds.max().getZ() + 0.5, candidate.z))
        );
    }

    public void disableMonsters() {
        for (Monster monster : activeMonsters) {
            discardMinecraftEntity(monster);
            monster.markRemoved();
        }
        activeMonsters.clear();
        waveMonsterSpawnQueue.clear();
        waveMonsterSpawnCooldownTicks = 0;
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

        for (Tower tower : towers) {
            tower.moveToFinalDefense(this, nextFinalDefenseTowerPosition(tower));
        }
        towersMovedToFinalDefense = true;
    }

    public GridPosition nextFinalDefenseTowerPosition(Tower tower) {
        GridPosition slot = finalDefenseSlotAllocator.allocate(tower)
                .orElseGet(() -> GridPosition.from(BlockPos.containing(laneLayout.bossPosition())));
        return finalDefenseTowerPosition(slot);
    }

    void setFinalDefenseSlotAllocator(FinalDefenseSlotAllocator finalDefenseSlotAllocator) {
        this.finalDefenseSlotAllocator = finalDefenseSlotAllocator == null
                ? FinalDefenseSlotAllocator.fromLayouts(List.of(laneLayout))
                : finalDefenseSlotAllocator;
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
        boolean allTowersDestroyed = !towers.isEmpty();
        for (Tower tower : towers) {
            boolean destroyed = tower.isDestroyed(this);
            if (destroyed) {
                if (tower.notifyDeath(this)) {
                    notifyNearbyTowerDeath(tower);
                }
            } else {
                allTowersDestroyed = false;
            }
        }
        if (!laneDefenseBroken && allTowersDestroyed) {
            laneDefenseBroken = true;
        }
        if (laneDefenseBroken) {
            activeMonsters.forEach(Monster::enterFinalDefenseCombat);
        }
    }

    private void notifyNearbyMonsterDeath(Monster monster, Vec3 deathPosition) {
        for (Tower tower : List.copyOf(towers)) {
            if (towers.contains(tower)) {
                tower.onNearbyMonsterDeath(this, monster, deathPosition);
            }
        }
    }

    private void notifyNearbyTowerDeath(Tower destroyedTower) {
        IllagerRaidStates.onTowerDeath(this, destroyedTower);
        for (Tower tower : List.copyOf(towers)) {
            if (tower != destroyedTower && towers.contains(tower)) {
                tower.onNearbyTowerDeath(this, destroyedTower);
            }
        }
    }

    private Vec3 monsterDeathPosition(Monster monster) {
        if (monster == null) {
            return null;
        }
        if (monster.hasMinecraftEntity()) {
            var entity = arenaWorld.getEntity(monster.minecraftEntityId());
            if (entity != null) {
                return entity.position();
            }
        }
        return laneLayout.positionAt(monster.laneProgress());
    }

    private void spawnQueuedMonster(List<Monster> queue, Map<UUID, SemionPlayer> players, boolean distributeWaveSpawn) {
        if (!queue.isEmpty()) {
            Monster monster = queue.removeFirst();
            spawnMinecraftEntity(monster, distributeWaveSpawn);
            recordIncomingThreat(monster, players);
            activeMonsters.add(monster);
        }
    }

    private void spawnQueuedWaveMonster(Map<UUID, SemionPlayer> players) {
        if (waveMonsterSpawnQueue.isEmpty()) {
            waveMonsterSpawnCooldownTicks = 0;
            return;
        }
        if (waveMonsterSpawnCooldownTicks > 0) {
            waveMonsterSpawnCooldownTicks--;
            return;
        }
        spawnQueuedMonster(waveMonsterSpawnQueue, players, true);
        waveMonsterSpawnCooldownTicks = waveMonsterSpawnIntervalTicks - 1;
    }

    private void spawnAllQueuedMonsters(List<Monster> queue) {
        while (!queue.isEmpty()) {
            Monster monster = queue.removeFirst();
            spawnMinecraftEntity(monster, false);
            activeMonsters.add(monster);
        }
    }

    private void spawnMinecraftEntity(Monster monster, boolean distributeWaveSpawn) {
        Vec3 spawn = distributeWaveSpawn ? waveSpawnPositionPolicy.next() : laneLayout.spawn();
        spawnMinecraftEntity(monster, spawn);
    }

    private void spawnMinecraftEntity(Monster monster, Vec3 spawn) {
        if (monster.hasMinecraftEntity()) {
            return;
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, arenaWorld);
        entity.configureFrom(monster, laneLayout);

        entity.setPos(spawn.x, spawn.y, spawn.z);

        if (arenaWorld.addFreshEntity(entity)) {
            monster.markMinecraftEntitySpawned(entity.getId(), spawn.x, spawn.y, spawn.z);
        }
    }

    private void syncMonsterEntityState(
            Monster monster,
            Map<UUID, SemionPlayer> players,
            MonsterScalingConfig monsterScalingConfig,
            int roundElapsedTicks
    ) {
        if (!monster.hasMinecraftEntity()) {
            restoreMinecraftEntity(monster);
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
                monster.clearMinecraftEntityReference();
                restoreMinecraftEntity(monster);
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
        if (monster.tickSurvivalScaling(monsterScalingConfig, roundElapsedTicks)) {
            monsterEntity.syncAttributesFromRuntimeMonster();
            monsterEntity.setHealth((float) Math.max(0.1, monster.health()));
        }
    }

    private void restoreMinecraftEntity(Monster monster) {
        if (monster == null || monster.isRemoved() || monster.health() <= 0.0) {
            return;
        }
        Vec3 position = laneLayout.positionAt(monster.laneProgress());
        BlockPos blockPos = BlockPos.containing(position);
        arenaWorld.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        spawnMinecraftEntity(monster, position);
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
            leakedThisRound = true;
            laneOwner.matchStats().recordOwnLaneLeakedThreat(threat);
            VillagerAdvStates.onLaneLeak(laneOwner, this);
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
