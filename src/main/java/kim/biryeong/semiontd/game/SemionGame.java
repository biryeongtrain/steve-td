package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.buildguide.BuildGuideService;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;
import kim.biryeong.semiontd.config.LeaderTargetingConfig;
import kim.biryeong.semiontd.config.RoundWaveConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.map.TeamArena;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonResult;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.summon.SummonShop;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import kim.biryeong.semiontd.ui.SemionLaneIndicatorService;
import kim.biryeong.semiontd.ui.SemionSidebarHudService;
import kim.biryeong.semiontd.ui.SemionText;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class SemionGame {
    public static final int DEFAULT_PREPARE_TICKS = 25 * 20;
    public static final int DEFAULT_WAVE_FINAL_DEFENSE_TICKS = 90 * 20;
    static final int LEADER_TARGET_COOLDOWN_ROUNDS = 3;

    private EconomyConfig economyConfig;
    private WaveConfig waveConfig;
    private LeaderTargetingConfig leaderTargetingConfig;
    private IncomeLaneRoutingConfig incomeLaneRoutingConfig;
    private final GameArena arena;
    private final EconomyService economyService;
    private IncomeLaneRoutingPolicy incomeLaneRoutingPolicy;
    private final SummonShop summonShop;
    private final BuildGuideService buildGuideService;
    private final Random random = new Random();
    private final Map<TeamId, SemionTeam> teams = new EnumMap<>(TeamId.class);
    private final Map<UUID, SemionPlayer> players = new java.util.HashMap<>();
    private final Map<UUID, SemionJob> selectedJobs = new java.util.HashMap<>();
    private final Set<UUID> readyPlayerIds = new HashSet<>();
    private final Set<UUID> initialSpectatorIds = new HashSet<>();
    private final Set<UUID> matchSpectatorIds = new HashSet<>();
    private final Set<TeamId> announcedEliminations = new HashSet<>();
    private final Set<TeamId> currentWaveTeamIds = new HashSet<>();
    private final List<TeamEliminationRecord> eliminationOrder = new ArrayList<>();
    private MatchId matchId = MatchId.newId();
    private long startedAtEpochMillis;
    private long endedAtEpochMillis;
    private RoundPhase phase = RoundPhase.WAITING;
    private boolean rosterLocked;
    private int currentRound = 1;
    private long tickCounter;
    private int phaseTicks;
    private boolean finalDefenseForcedThisRound;

    public SemionGame(EconomyConfig economyConfig, WaveConfig waveConfig, GameArena arena) {
        this(economyConfig, waveConfig, arena, null);
    }

    public SemionGame(EconomyConfig economyConfig, WaveConfig waveConfig, GameArena arena, BuildGuideService buildGuideService) {
        this(economyConfig, waveConfig, LeaderTargetingConfig.defaultConfig(), IncomeLaneRoutingConfig.defaultConfig(), arena, buildGuideService);
    }

    public SemionGame(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            LeaderTargetingConfig leaderTargetingConfig,
            GameArena arena
    ) {
        this(economyConfig, waveConfig, leaderTargetingConfig, IncomeLaneRoutingConfig.defaultConfig(), arena, null);
    }

    public SemionGame(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            LeaderTargetingConfig leaderTargetingConfig,
            IncomeLaneRoutingConfig incomeLaneRoutingConfig,
            GameArena arena
    ) {
        this(economyConfig, waveConfig, leaderTargetingConfig, incomeLaneRoutingConfig, arena, null);
    }

    public SemionGame(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            LeaderTargetingConfig leaderTargetingConfig,
            GameArena arena,
            BuildGuideService buildGuideService
    ) {
        this(economyConfig, waveConfig, leaderTargetingConfig, IncomeLaneRoutingConfig.defaultConfig(), arena, buildGuideService);
    }

    public SemionGame(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            LeaderTargetingConfig leaderTargetingConfig,
            IncomeLaneRoutingConfig incomeLaneRoutingConfig,
            GameArena arena,
            BuildGuideService buildGuideService
    ) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.leaderTargetingConfig = leaderTargetingConfig == null ? LeaderTargetingConfig.defaultConfig() : leaderTargetingConfig;
        this.incomeLaneRoutingConfig = incomeLaneRoutingConfig == null ? IncomeLaneRoutingConfig.defaultConfig() : incomeLaneRoutingConfig;
        this.arena = arena;
        this.economyService = new EconomyService(economyConfig, this);
        this.incomeLaneRoutingPolicy = new IncomeLaneRoutingPolicy(this.incomeLaneRoutingConfig, random);
        this.summonShop = new SummonShop();
        this.buildGuideService = buildGuideService;
        for (TeamId teamId : TeamId.values()) {
            teams.put(teamId, new SemionTeam(teamId));
        }
    }

    public RoundPhase phase() {
        return phase;
    }

    public int currentRound() {
        return currentRound;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public int remainingPrepareSeconds() {
        if (phase != RoundPhase.PREPARE_AND_SUMMON) {
            return -1;
        }
        int remainingTicks = Math.max(0, DEFAULT_PREPARE_TICKS - phaseTicks);
        return (remainingTicks + 19) / 20;
    }

    public Map<TeamId, SemionTeam> teams() {
        return teams;
    }

    public Map<UUID, SemionPlayer> players() {
        return players;
    }

    public SummonShop summonShop() {
        return summonShop;
    }

    public Optional<BuildGuideService> buildGuideService() {
        return Optional.ofNullable(buildGuideService);
    }

    public GameArena arena() {
        return arena;
    }

    public EconomyConfig economyConfig() {
        return economyConfig;
    }

    public void applyConfigs(EconomyConfig economyConfig, WaveConfig waveConfig) {
        applyConfigs(economyConfig, waveConfig, leaderTargetingConfig, incomeLaneRoutingConfig);
    }

    public void applyConfigs(EconomyConfig economyConfig, WaveConfig waveConfig, LeaderTargetingConfig leaderTargetingConfig) {
        applyConfigs(economyConfig, waveConfig, leaderTargetingConfig, incomeLaneRoutingConfig);
    }

    public void applyConfigs(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            LeaderTargetingConfig leaderTargetingConfig,
            IncomeLaneRoutingConfig incomeLaneRoutingConfig
    ) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.leaderTargetingConfig = leaderTargetingConfig == null ? LeaderTargetingConfig.defaultConfig() : leaderTargetingConfig;
        this.incomeLaneRoutingConfig = incomeLaneRoutingConfig == null ? IncomeLaneRoutingConfig.defaultConfig() : incomeLaneRoutingConfig;
        this.incomeLaneRoutingPolicy = new IncomeLaneRoutingPolicy(this.incomeLaneRoutingConfig, random);
        this.economyService.configure(economyConfig);
    }

    public void refreshProductionTowerTypes() {
        for (SemionTeam team : teams.values()) {
            for (PlayerLane lane : team.laneGroup().lanes()) {
                for (Tower tower : lane.towers()) {
                    ProductionTowerCatalog.find(tower.type().id())
                            .ifPresent(entry -> tower.refreshType(entry.type(), lane));
                }
            }
        }
    }

    public void refreshSummonShop() {
        summonShop.reloadFromRegistry();
    }

    public int towerLimitForCurrentRound() {
        return economyConfig.towerLimitForRound(currentRound);
    }

    public int towerLimitForPlayer(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        int purchasedBonus = player == null
                ? 0
                : economyConfig.towerLimit().purchasedBonus(player.economy().towerLimitPurchaseCount());
        return towerLimitForCurrentRound() + purchasedBonus;
    }

    public long nextTowerLimitPurchaseDiamondCost(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return -1;
        }
        return economyConfig.towerLimit().purchaseDiamondCost(player.economy().towerLimitPurchaseCount());
    }

    public long nextTowerLimitPurchaseEmeraldCost(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return -1;
        }
        return economyConfig.towerLimit().purchaseEmeraldCost(player.economy().towerLimitPurchaseCount());
    }

    public boolean purchaseTowerLimit(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        return player != null && player.economy().purchaseTowerLimit(economyConfig.towerLimit());
    }

    public int towerCount(UUID playerId) {
        return playerLane(playerId)
                .map(lane -> (int) lane.towers().stream()
                        .filter(tower -> tower.ownerPlayer().equals(playerId))
                        .count())
                .orElse(0);
    }

    public boolean canPlaceMoreTowers(UUID playerId) {
        return towerCount(playerId) < towerLimitForPlayer(playerId);
    }

    public boolean rosterLocked() {
        return rosterLocked;
    }

    public int spectatorCount() {
        return matchSpectatorIds.size();
    }

    public boolean canConfigureRoster() {
        return phase == RoundPhase.WAITING && !rosterLocked;
    }

    public Set<UUID> readyPlayerIds() {
        return java.util.Collections.unmodifiableSet(readyPlayerIds);
    }

    public int readyPlayerCount() {
        return readyPlayerIds.size();
    }

    public boolean isReady(UUID playerId) {
        return readyPlayerIds.contains(playerId);
    }

    public boolean markReady(UUID playerId) {
        if (!canConfigureRoster() || playerId == null) {
            return false;
        }
        readyPlayerIds.add(playerId);
        return true;
    }

    public boolean markNotReady(UUID playerId) {
        if (!canConfigureRoster() || playerId == null) {
            return false;
        }
        readyPlayerIds.remove(playerId);
        return true;
    }

    public boolean isActiveParticipant(UUID playerId) {
        return players.containsKey(playerId);
    }

    public boolean isMatchSpectator(UUID playerId) {
        return matchSpectatorIds.contains(playerId);
    }

    public Set<UUID> matchSpectatorIds() {
        return java.util.Collections.unmodifiableSet(matchSpectatorIds);
    }

    public Optional<SemionTeam> teamForWorld(ServerLevel world) {
        if (world == null) {
            return Optional.empty();
        }
        return teams.values().stream()
                .filter(SemionTeam::active)
                .filter(team -> arena.teamArena(team.id())
                        .map(teamArena -> teamArena.world() == world)
                        .orElse(false))
                .findFirst();
    }

    public Map<UUID, SemionJob> selectedJobs() {
        return java.util.Collections.unmodifiableMap(selectedJobs);
    }

    public boolean selectJob(UUID playerId, ResourceLocation jobId) {
        if (!canConfigureRoster()) {
            return false;
        }
        Optional<SemionJob> job = JobRegistry.find(jobId);
        if (job.isEmpty()) {
            return false;
        }
        selectedJobs.put(playerId, job.get());
        return true;
    }

    public SemionJob selectedJobOrDefault(UUID playerId) {
        return selectedJobs.getOrDefault(playerId, JobRegistry.defaultJob());
    }

    public Optional<MatchResult> matchResult() {
        if (phase != RoundPhase.ENDED || !rosterLocked) {
            return Optional.empty();
        }

        Set<TeamId> winningTeams = livingTeams().stream()
                .map(SemionTeam::id)
                .collect(Collectors.toUnmodifiableSet());
        List<MatchParticipantResult> participants = players.values().stream()
                .sorted(Comparator.comparing(SemionPlayer::name))
                .map(player -> new MatchParticipantResult(
                        player.uuid(),
                        player.name(),
                        player.teamId(),
                        winningTeams.contains(player.teamId()),
                        player.matchStats().snapshot(player.economy().income())
                ))
                .toList();
        return Optional.of(new MatchResult(
                matchId,
                startedAtEpochMillis,
                endedAtEpochMillis,
                participants,
                initialSpectatorIds,
                winningTeams,
                teamResults(winningTeams),
                currentRound
        ));
    }

    public boolean start(MinecraftServer server, ParticipantSelectionPlan plan) {
        if (!canConfigureRoster() || plan.activeParticipants().isEmpty()) {
            return false;
        }

        initialSpectatorIds.clear();
        matchSpectatorIds.clear();
        announcedEliminations.clear();
        eliminationOrder.clear();
        initialSpectatorIds.addAll(plan.spectatorIds());
        matchSpectatorIds.addAll(plan.spectatorIds());

        Set<TeamId> activeTeams = new HashSet<>();
        for (AssignedParticipant participant : plan.activeParticipants()) {
            activeTeams.add(participant.teamId());
        }

        for (SemionTeam team : teams.values()) {
            if (activeTeams.contains(team.id())) {
                team.activate();
            } else {
                team.deactivate();
            }
        }

        for (AssignedParticipant participant : plan.activeParticipants()) {
            if (!activateParticipant(participant)) {
                return false;
            }
        }
        assignTeamLeadersFromParticipants(plan.activeParticipants());

        spawnBossesForActiveTeams(activeTeams);
        placeActivePlayers(server, plan.activeParticipants());
        sendTowerControlHint(server);
        placeSpectators(server, plan.spectatorIds());
        matchId = MatchId.newId();
        startedAtEpochMillis = System.currentTimeMillis();
        endedAtEpochMillis = 0L;
        rosterLocked = true;
        notifyMatchStarted();
        if (buildGuideService != null) {
            buildGuideService.startMatch(this);
        }
        startPreparePhase(server);
        return true;
    }

    public boolean preloadWorldsForStart(ParticipantSelectionPlan plan) {
        if (!canConfigureRoster() || plan.activeParticipants().isEmpty()) {
            return false;
        }

        Set<TeamId> activeTeams = new HashSet<>();
        for (AssignedParticipant participant : plan.activeParticipants()) {
            activeTeams.add(participant.teamId());
        }

        for (TeamId teamId : activeTeams) {
            Optional<TeamArena> teamArena = arena.teamArena(teamId);
            if (teamArena.isEmpty()) {
                return false;
            }
            teamArena.get().preloadForTeleport();
        }
        return true;
    }

    public void close() {
        arena.unload();
    }

    public SummonResult summonMonster(UUID playerId, String summonId) {
        if (phase != RoundPhase.PREPARE_AND_SUMMON && phase != RoundPhase.LANE_WAVE) {
            return SummonResult.failure(SummonResultType.INVALID_PHASE, summonId);
        }

        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return SummonResult.failure(SummonResultType.PLAYER_NOT_IN_GAME, summonId);
        }

        SemionTeam senderTeam = teams.get(player.teamId());
        if (senderTeam == null || senderTeam.eliminated()) {
            return SummonResult.failure(SummonResultType.PLAYER_TEAM_ELIMINATED, summonId);
        }

        Optional<SummonMonsterType> type = summonShop.find(summonId);
        if (type.isEmpty()) {
            return SummonResult.failure(SummonResultType.UNKNOWN_SUMMON, summonId);
        }

        JobContext jobContext = new JobContext(this, player);
        SummonContext summonContext = new SummonContext(this, player);
        SemionJob job = player.job().orElse(JobRegistry.defaultJob());
        if (!job.canUseSummon(jobContext, type.get())) {
            return SummonResult.failure(SummonResultType.SUMMON_NOT_ALLOWED_BY_JOB, summonId);
        }
        long gasCost = Math.max(0, job.modifySummonGasCost(jobContext, type.get(), type.get().gasCost()));
        long incomeGain = Math.max(0, job.modifySummonIncomeGain(jobContext, type.get(), type.get().incomeGain()));

        if (!economyService.spendForSummon(player, gasCost)) {
            return SummonResult.failure(SummonResultType.NOT_ENOUGH_GAS, summonId);
        }

        Optional<SemionTeam> targetTeam = targetTeamForSummon(player.teamId());
        if (targetTeam.isEmpty()) {
            economyService.refundSummon(player, gasCost, currentRound);
            return SummonResult.failure(SummonResultType.NO_TARGET_TEAM, summonId);
        }

        Optional<PlayerLane> targetLane = targetLaneForSummon(targetTeam.get());
        if (targetLane.isEmpty()) {
            economyService.refundSummon(player, gasCost, currentRound);
            return SummonResult.failure(SummonResultType.NO_TARGET_LANE, summonId);
        }

        economyService.applySummonIncome(player, incomeGain);
        player.matchStats().recordSummonedMonster();
        player.matchStats().recordIncomeGenerated(incomeGain);
        int scheduledRound = phase == RoundPhase.LANE_WAVE ? currentRound + 1 : currentRound;
        Monster monster = type.get().createMonster(summonContext, targetTeam.get().id(), targetLane.get().laneId(), scheduledRound);
        player.matchStats().recordSentIncomeThreat(monster.attributionThreat());
        job.onSummonedMonster(jobContext, type.get(), monster);
        type.get().onSummoned(summonContext, monster);
        if (phase == RoundPhase.LANE_WAVE) {
            targetLane.get().enqueueNextRoundSummonedMonster(monster);
        } else {
            targetLane.get().enqueueSummonedMonster(monster);
        }
        if (buildGuideService != null) {
            buildGuideService.recordSummon(
                    this,
                    playerId,
                    summonId,
                    gasCost,
                    incomeGain,
                    targetTeam.get().id(),
                    targetLane.get().laneId(),
                    scheduledRound
            );
        }
        return SummonResult.success(summonId, targetTeam.get().id(), targetLane.get().laneId(), scheduledRound);
    }

    public boolean upgradeGasProduction(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return false;
        }
        SemionTeam team = teams.get(player.teamId());
        PlayerEconomy economy = player.economy();
        long emeraldPerSecBefore = economy.emeraldPerSec();
        int upgradeCountBefore = economy.emeraldProductionUpgradeCount();
        long cost = economyConfig.gasProduction().upgradeCost(upgradeCountBefore);
        boolean upgraded = economyService.upgradeGasProduction(player, team);
        if (upgraded && buildGuideService != null) {
            buildGuideService.recordEmeraldProductionUpgrade(
                    this,
                    playerId,
                    economy.emeraldProductionUpgradeCount(),
                    cost,
                    Math.max(0L, economy.emeraldPerSec() - emeraldPerSecBefore)
            );
        }
        return upgraded;
    }

    public void recordTowerPlacement(UUID playerId, String towerId, GridPosition position, long cost) {
        if (buildGuideService != null) {
            buildGuideService.recordTowerPlacement(this, playerId, towerId, position, cost);
        }
    }

    public void recordTowerUpgrade(UUID playerId, String upgradeId, GridPosition position, long cost) {
        if (buildGuideService != null) {
            buildGuideService.recordTowerUpgrade(this, playerId, upgradeId, position, cost);
        }
    }

    public boolean killBoss(TeamId teamId) {
        return killBoss(null, teamId);
    }

    public boolean killBoss(MinecraftServer server, TeamId teamId) {
        SemionTeam team = teams.get(teamId);
        if (team == null || !team.active() || team.eliminated()) {
            return false;
        }
        team.laneGroup().boss().damage(Double.MAX_VALUE);
        if (!team.eliminate()) {
            return false;
        }
        if (announcedEliminations.add(teamId)) {
            recordTeamEliminated(team);
            handleTeamEliminated(server, team);
        }
        checkVictory();
        return true;
    }

    public boolean restorePlayerPlacement(MinecraftServer server, ServerPlayer player) {
        SemionPlayer activePlayer = players.get(player.getUUID());
        if (activePlayer != null) {
            SemionTeam team = teams.get(activePlayer.teamId());
            if (team != null && team.active() && !team.eliminated()) {
                placeActivePlayer(player, activePlayer);
            } else {
                placeSpectatorPlayer(player, spectatorIndex(player.getUUID()), activePlayer.teamId());
            }
            return true;
        }

        if (matchSpectatorIds.contains(player.getUUID())) {
            placeSpectatorPlayer(player, spectatorIndex(player.getUUID()), null);
            return true;
        }

        return false;
    }

    public boolean addLateSpectator(UUID spectatorId) {
        if (!rosterLocked || phase == RoundPhase.WAITING || phase == RoundPhase.ENDED || spectatorId == null) {
            return false;
        }
        if (players.containsKey(spectatorId) && !matchSpectatorIds.contains(spectatorId)) {
            return false;
        }
        matchSpectatorIds.add(spectatorId);
        return true;
    }

    public boolean addLateSpectator(UUID spectatorId, TeamId targetTeam) {
        if (!canSpectateTeam(targetTeam)) {
            return false;
        }
        return addLateSpectator(spectatorId);
    }

    public boolean addLateSpectator(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null || !addLateSpectator(player.getUUID())) {
            return false;
        }
        VanillaTeamBridge.assignSpectator(server, player);
        placeSpectatorPlayer(player, spectatorIndex(player.getUUID()), null);
        return true;
    }

    public boolean addLateSpectator(MinecraftServer server, ServerPlayer player, TeamId targetTeam) {
        if (server == null || player == null || !addLateSpectator(player.getUUID(), targetTeam)) {
            return false;
        }
        VanillaTeamBridge.assignSpectator(server, player);
        placeSpectatorPlayerAtTeam(player, spectatorIndex(player.getUUID()), targetTeam);
        return true;
    }

    public boolean canSpectateTeam(TeamId targetTeam) {
        return spectatorArenaForActiveTeam(targetTeam).isPresent();
    }

    public Optional<PlayerLane> playerLane(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return Optional.empty();
        }
        SemionTeam team = teams.get(player.teamId());
        if (team == null || !team.active() || team.eliminated()) {
            return Optional.empty();
        }
        return team.laneGroup().lane(player.laneId());
    }

    public void tick(MinecraftServer server) {
        tickCounter++;
        if (phase != RoundPhase.WAITING && phase != RoundPhase.ENDED && tickCounter % 20 == 0) {
            economyService.tickGas(players.values(), teams, currentRound);
        }

        switch (phase) {
            case WAITING, ENDED -> {
            }
            case PREPARE_AND_SUMMON -> tickPrepare(server);
            case LANE_WAVE -> tickWave(server);
            case ROUND_PAYOUT -> tickPayout(server);
        }
    }

    private void tickPrepare(MinecraftServer server) {
        phaseTicks++;
        if (phaseTicks % 80 == 0) {
            showLaneIndicators(server);
        }
        if (phaseTicks >= DEFAULT_PREPARE_TICKS) {
            startWavePhase();
        }
    }

    private void startWavePhase() {
        phase = RoundPhase.LANE_WAVE;
        phaseTicks = 0;
        finalDefenseForcedThisRound = false;
        currentWaveTeamIds.clear();
        for (SemionTeam team : livingTeams()) {
            currentWaveTeamIds.add(team.id());
            team.laneGroup().setCurrentRound(currentRound);
            for (PlayerLane lane : team.laneGroup().lanes()) {
                lane.markWaveStarted(currentRound);
            }
            enqueueWave(team);
        }
    }

    private void tickWave(MinecraftServer server) {
        phaseTicks++;
        if (!finalDefenseForcedThisRound && phaseTicks >= DEFAULT_WAVE_FINAL_DEFENSE_TICKS) {
            forceFinalDefenseForLivingTeams();
            finalDefenseForcedThisRound = true;
        }
        for (SemionTeam team : teams.values()) {
            team.tick(server, economyService, players);
            if (team.active() && team.eliminated() && announcedEliminations.add(team.id())) {
                recordTeamEliminated(team);
                handleTeamEliminated(server, team);
            }
        }
        if (checkVictory()) {
            return;
        }
        if (currentWaveTeamIds.stream().allMatch(this::isCurrentWaveTeamResolved)) {
            phase = RoundPhase.ROUND_PAYOUT;
            phaseTicks = 0;
        }
    }

    private boolean isCurrentWaveTeamResolved(TeamId teamId) {
        SemionTeam team = teams.get(teamId);
        return team == null || team.isRoundResolved();
    }

    private void forceFinalDefenseForLivingTeams() {
        for (SemionTeam team : livingTeams()) {
            team.laneGroup().forceFinalDefense();
        }
    }

    private void tickPayout(MinecraftServer server) {
        notifyRoundEnded(currentRound);
        economyService.payRoundIncome(players.values(), teams);
        currentWaveTeamIds.clear();
        currentRound++;
        tickLeaderCooldowns();
        startPreparePhase(server);
    }

    private void startPreparePhase(MinecraftServer server) {
        phase = RoundPhase.PREPARE_AND_SUMMON;
        phaseTicks = 0;
        for (SemionTeam team : livingTeams()) {
            team.resetForRound();
        }
        prepareActivePlayers(server);
        notifyRoundStarted(currentRound);
        if (buildGuideService != null) {
            buildGuideService.onPreparePhaseStarted(server, this, currentRound);
        }
    }

    private void enqueueWave(SemionTeam team) {
        Optional<RoundWaveConfig> config = waveConfig.configForRound(currentRound);
        if (config.isEmpty()) {
            return;
        }

        for (PlayerLane lane : team.laneGroup().lanes()) {
            String laneKey = "lane_" + lane.laneId();
            for (var entry : config.get().entriesForLane(laneKey)) {
                lane.enqueueWaveMonster(entry);
            }
        }
    }

    private boolean checkVictory() {
        List<SemionTeam> living = livingTeams();
        if (living.size() <= 1 && phase != RoundPhase.WAITING) {
            if (endedAtEpochMillis == 0L) {
                endedAtEpochMillis = System.currentTimeMillis();
            }
            phase = RoundPhase.ENDED;
            return true;
        }
        return false;
    }

    private List<SemionTeam> livingTeams() {
        return teams.values().stream()
                .filter(SemionTeam::active)
                .filter(team -> !team.eliminated())
                .toList();
    }

    private List<TeamMatchResult> teamResults(Set<TeamId> winningTeams) {
        List<TeamMatchResult> results = new ArrayList<>();
        List<SemionTeam> living = livingTeams().stream()
                .sorted(Comparator.comparing(SemionTeam::id))
                .toList();
        for (SemionTeam team : living) {
            results.add(new TeamMatchResult(
                    team.id(),
                    1,
                    winningTeams.contains(team.id()) ? MatchResultGroup.WIN_GROUP : MatchResultGroup.DRAW_OR_UNRATED,
                    1.0,
                    -1,
                    -1,
                    bossDamageTaken(team)
            ));
        }

        int placement = living.isEmpty() ? 1 : living.size() + 1;
        MatchResultGroup eliminatedResultGroup = winningTeams.isEmpty()
                ? MatchResultGroup.DRAW_OR_UNRATED
                : MatchResultGroup.LOSS_GROUP;
        for (int index = eliminationOrder.size() - 1; index >= 0; index--) {
            TeamEliminationRecord record = eliminationOrder.get(index);
            int resultPlacement = winningTeams.isEmpty() ? 1 : placement++;
            results.add(new TeamMatchResult(
                    record.teamId(),
                    resultPlacement,
                    eliminatedResultGroup,
                    winningTeams.isEmpty() ? 0.0 : placementWeight(resultPlacement),
                    record.round(),
                    record.tick(),
                    record.bossDamageTaken()
            ));
        }
        return List.copyOf(results);
    }

    private static double placementWeight(int placement) {
        return placement <= 1 ? 1.0 : 1.0 / placement;
    }

    private void recordTeamEliminated(SemionTeam team) {
        eliminationOrder.add(new TeamEliminationRecord(
                team.id(),
                currentRound,
                tickCounter,
                bossDamageTaken(team)
        ));
    }

    private static double bossDamageTaken(SemionTeam team) {
        return Math.max(0.0, team.laneGroup().boss().maxHealth() - team.laneGroup().boss().health());
    }

    void assignTeamLeadersFromParticipants(List<AssignedParticipant> activeParticipants) {
        for (SemionTeam team : teams.values()) {
            if (!team.active() || team.eliminated()) {
                continue;
            }
            activeParticipants.stream()
                    .filter(participant -> participant.teamId() == team.id())
                    .filter(participant -> players.containsKey(participant.uuid()))
                    .min(Comparator.comparingInt(AssignedParticipant::laneId))
                    .ifPresent(participant -> team.setLeader(participant.uuid()));
        }
    }

    public void announceTeamLeaders(MinecraftServer server) {
        for (SemionTeam team : teams.values()) {
            team.leaderPlayerId()
                    .map(players::get)
                    .map(player -> teamLeaderAnnouncementMarkup(team.id(), player.name()))
                    .map(SemionText::prefixedMini)
                    .ifPresent(message -> server.getPlayerList().broadcastSystemMessage(message, false));
        }
    }

    static String teamLeaderAnnouncementMarkup(TeamId teamId, String playerName) {
        return "<white>" + teamId.name() + " 팀장: </white>" + teamColoredNameMarkup(teamId, playerName);
    }

    private static String teamColoredNameMarkup(TeamId teamId, String playerName) {
        String color = switch (teamId) {
            case RED -> "red";
            case BLUE -> "blue";
            case GREEN -> "green";
            case YELLOW -> "yellow";
            case PURPLE -> "light_purple";
        };
        return "<" + color + ">" + MiniMessage.miniMessage().escapeTags(playerName) + "</" + color + ">";
    }

    public LeaderTargetResult setLeaderTarget(UUID playerId, TeamId targetTeamId) {
        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return LeaderTargetResult.PLAYER_NOT_IN_GAME;
        }
        SemionTeam senderTeam = teams.get(player.teamId());
        if (senderTeam == null || senderTeam.leaderTargeting().isEmpty() || !senderTeam.hasLeader(playerId)) {
            return LeaderTargetResult.NOT_TEAM_LEADER;
        }
        if (targetTeamId == null) {
            return LeaderTargetResult.INVALID_TARGET_TEAM;
        }
        if (targetTeamId == player.teamId()) {
            return LeaderTargetResult.TARGET_SELF_TEAM;
        }
        SemionTeam targetTeam = teams.get(targetTeamId);
        if (targetTeam == null || !targetTeam.active() || targetTeam.eliminated()) {
            return LeaderTargetResult.TARGET_TEAM_NOT_ALIVE;
        }
        LeaderTargetingState leaderTargeting = senderTeam.leaderTargeting().orElseThrow();
        if (!leaderTargeting.canUse()) {
            return LeaderTargetResult.COOLDOWN_ACTIVE;
        }
        if (activeLeaderTargetCount(targetTeamId, senderTeam.id()) >= leaderTargetingConfig.maxTargetingTeamsPerTarget()) {
            return LeaderTargetResult.TARGET_TEAM_ALREADY_DESIGNATED;
        }
        leaderTargeting.use(
                targetTeamId,
                currentRound,
                LEADER_TARGET_COOLDOWN_ROUNDS,
                leaderTargetingConfig.activeTargetRounds()
        );
        return LeaderTargetResult.SUCCESS;
    }

    private long activeLeaderTargetCount(TeamId targetTeamId, TeamId excludingSenderTeamId) {
        return livingTeams().stream()
                .filter(team -> team.id() != excludingSenderTeamId)
                .map(SemionTeam::leaderTargeting)
                .flatMap(Optional::stream)
                .flatMap(state -> state.targetTeamId().stream())
                .filter(targetTeamId::equals)
                .count();
    }

    void tickLeaderCooldowns() {
        for (SemionTeam team : livingTeams()) {
            team.leaderTargeting().ifPresent(LeaderTargetingState::tickRoundCooldown);
        }
    }

    Optional<SemionTeam> targetTeamForSummon(TeamId senderTeam) {
        SemionTeam team = teams.get(senderTeam);
        if (team != null) {
            Optional<LeaderTargetingState> leaderTargeting = team.leaderTargeting();
            if (leaderTargeting.isPresent() && leaderTargeting.get().targetTeamId().isPresent()) {
                TeamId targetTeamId = leaderTargeting.get().targetTeamId().orElseThrow();
                SemionTeam targetTeam = teams.get(targetTeamId);
                if (targetTeam != null && targetTeam.active() && !targetTeam.eliminated() && targetTeam.id() != senderTeam) {
                    return Optional.of(targetTeam);
                }
                leaderTargeting.get().clearTarget();
            }
        }
        return randomTargetTeam(senderTeam);
    }

    private Optional<SemionTeam> randomTargetTeam(TeamId senderTeam) {
        List<SemionTeam> candidates = livingTeams().stream()
                .filter(team -> team.id() != senderTeam)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    Optional<PlayerLane> targetLaneForSummon(SemionTeam team) {
        List<PlayerLane> lanes = team.laneGroup().lanes();
        if (lanes.isEmpty()) {
            return Optional.empty();
        }
        return incomeLaneRoutingPolicy.select(lanes);
    }

    private boolean activateParticipant(AssignedParticipant participant) {
        if (players.containsKey(participant.uuid())) {
            return false;
        }

        SemionTeam team = teams.get(participant.teamId());
        if (team == null || !team.active() || team.eliminated() || team.memberIds().size() >= SemionTeam.MAX_PLAYERS) {
            return false;
        }

        Optional<TeamArena> teamArena = arena.teamArena(participant.teamId());
        if (teamArena.isEmpty()) {
            return false;
        }

        Optional<LaneRegionLayout> laneLayout = teamArena.get().layout().lane(participant.laneId());
        if (laneLayout.isEmpty()) {
            return false;
        }

        SemionPlayer player = new SemionPlayer(
                participant.uuid(),
                participant.name(),
                participant.teamId(),
                participant.laneId(),
                new PlayerEconomy(economyConfig)
        );
        SemionJob job = selectedJobOrDefault(participant.uuid());
        player.assignJob(job);
        applyJobStartingEconomy(player, job);
        job.onSelected(new JobContext(this, player));
        if (!team.addPlayer(player, teamArena.get().world(), laneLayout.get())) {
            return false;
        }
        players.put(participant.uuid(), player);
        return true;
    }

    private void placeActivePlayers(MinecraftServer server, List<AssignedParticipant> activeParticipants) {
        for (AssignedParticipant participant : activeParticipants) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.uuid());
            if (player == null) {
                continue;
            }
            SemionPlayer activePlayer = players.get(participant.uuid());
            if (activePlayer != null) {
                placeActivePlayer(player, activePlayer);
            }
        }
    }

    private void placeActivePlayer(ServerPlayer player, SemionPlayer activePlayer) {
        arena.teamArena(activePlayer.teamId()).ifPresent(teamArena -> {
            Vec3 position = StartPlacement.activePlayerSpawn(teamArena.layout(), activePlayer.laneId());
            player.setGameMode(GameType.ADVENTURE);
            player.teleport(PlayerTeleportTransitions.preservingFacing(teamArena.world(), position, Vec3.ZERO, player));
            SemionSidebarHudService.refreshPlayerHud(player);
            SemionHotbarService.grantMatchTools(player);
            if (teams.get(activePlayer.teamId()).hasLeader(activePlayer.uuid())) {
                SemionHotbarService.grantLeaderTool(player);
            }
            setFlight(player, true);
            playerLane(activePlayer.uuid()).ifPresent(lane -> SemionLaneIndicatorService.showLane(player, lane));
        });
    }

    private static void setFlight(ServerPlayer player, boolean enabled) {
        player.getAbilities().mayfly = enabled;
        player.getAbilities().flying = enabled;
        player.onUpdateAbilities();
    }

    private void sendTowerControlHint(MinecraftServer server) {
        for (SemionPlayer activePlayer : players.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(activePlayer.uuid());
            if (player != null) {
                player.sendSystemMessage(SemionText.prefixedMini(
                        "<yellow>나침반</yellow>을 우클릭해서 <aqua>타워 설치</aqua> 창을 여세요."
                ));
            }
        }
    }

    private void placeSpectators(MinecraftServer server, Set<UUID> spectatorIdSet) {
        int spectatorIndex = 0;
        for (UUID spectatorId : spectatorIdSet.stream().sorted().toList()) {
            ServerPlayer player = server.getPlayerList().getPlayer(spectatorId);
            if (player == null) {
                continue;
            }
            placeSpectatorPlayer(player, spectatorIndex++, null);
        }
    }

    private void placeSpectatorPlayer(ServerPlayer player, int spectatorIndex, TeamId fallbackTeam) {
        spectatorArena(fallbackTeam).ifPresent(teamArena -> {
            Vec3 position = StartPlacement.spectatorSpawn(teamArena.layout(), spectatorIndex);
            player.setGameMode(GameType.SPECTATOR);
            player.teleport(PlayerTeleportTransitions.preservingFacing(teamArena.world(), position, Vec3.ZERO, player));
            SemionSidebarHudService.refreshPlayerHud(player);
            SemionHotbarService.clearMatchTools(player);
        });
    }

    private void placeSpectatorPlayerAtTeam(ServerPlayer player, int spectatorIndex, TeamId targetTeam) {
        spectatorArenaForActiveTeam(targetTeam).ifPresent(teamArena -> {
            Vec3 position = StartPlacement.spectatorSpawn(teamArena.layout(), spectatorIndex);
            player.setGameMode(GameType.SPECTATOR);
            player.teleport(PlayerTeleportTransitions.preservingFacing(teamArena.world(), position, Vec3.ZERO, player));
            SemionSidebarHudService.refreshPlayerHud(player);
            SemionHotbarService.clearMatchTools(player);
        });
    }

    private Optional<TeamArena> spectatorArenaForActiveTeam(TeamId targetTeam) {
        if (!rosterLocked || phase == RoundPhase.WAITING || phase == RoundPhase.ENDED || targetTeam == null) {
            return Optional.empty();
        }
        SemionTeam team = teams.get(targetTeam);
        if (team == null || !team.active() || team.eliminated()) {
            return Optional.empty();
        }
        return arena.teamArena(targetTeam);
    }

    private Optional<TeamArena> spectatorArena(TeamId fallbackTeam) {
        Optional<TeamArena> livingArena = teams.values().stream()
                .filter(SemionTeam::active)
                .filter(team -> !team.eliminated())
                .map(SemionTeam::id)
                .map(arena::teamArena)
                .flatMap(Optional::stream)
                .findFirst();
        if (livingArena.isPresent()) {
            return livingArena;
        }
        if (fallbackTeam != null) {
            Optional<TeamArena> fallbackArena = arena.teamArena(fallbackTeam);
            if (fallbackArena.isPresent()) {
                return fallbackArena;
            }
        }
        return teams.values().stream()
                .filter(SemionTeam::active)
                .map(SemionTeam::id)
                .map(arena::teamArena)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private int spectatorIndex(UUID spectatorId) {
        List<UUID> ordered = matchSpectatorIds.stream().sorted().toList();
        int index = ordered.indexOf(spectatorId);
        return Math.max(0, index);
    }

    private void spawnBossesForActiveTeams(Set<TeamId> activeTeams) {
        for (TeamId teamId : activeTeams) {
            Optional<TeamArena> teamArena = arena.teamArena(teamId);
            Optional<SemionTeam> team = Optional.ofNullable(teams.get(teamId));
            if (teamArena.isEmpty() || team.isEmpty()) {
                continue;
            }
            team.get().laneGroup().spawnBossEntity(teamArena.get().world(), teamArena.get().layout().bossSpawn());
        }
    }

    private void handleTeamEliminated(MinecraftServer server, SemionTeam team) {
        for (UUID memberId : team.memberIds()) {
            matchSpectatorIds.add(memberId);
            if (server == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player == null) {
                continue;
            }
            VanillaTeamBridge.assignSpectator(server, player);
            placeSpectatorPlayer(player, spectatorIndex(memberId), team.id());
            player.sendSystemMessage(SemionText.prefixedPlain("소속 팀이 탈락했습니다. 관전 모드로 전환됩니다."));
        }
        for (UUID memberId : team.memberIds()) {
            SemionPlayer semionPlayer = players.get(memberId);
            if (semionPlayer != null) {
                semionPlayer.job().ifPresent(job -> job.onEliminated(new JobContext(this, semionPlayer)));
            }
        }

        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    SemionText.prefixedMini("<red>" + team.id().name() + "</red> 팀이 탈락했습니다."),
                    false
            );
        }
    }

    private void applyJobStartingEconomy(SemionPlayer player, SemionJob job) {
        JobContext context = new JobContext(this, player);
        PlayerEconomy economy = player.economy();
        economy.overrideStartingValues(
                job.modifyStartingMineral(context, economy.mineral()),
                job.modifyStartingGas(context, economy.gas()),
                job.modifyStartingIncome(context, economy.income()),
                job.modifyStartingGasPerSec(context, economy.gasPerSec())
        );
    }

    private void prepareActivePlayers(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (SemionPlayer activePlayer : players.values()) {
            SemionTeam team = teams.get(activePlayer.teamId());
            if (team == null || !team.active() || team.eliminated()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(activePlayer.uuid());
            if (player != null) {
                placeActivePlayer(player, activePlayer);
            }
        }
    }

    private void showLaneIndicators(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (SemionPlayer activePlayer : players.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(activePlayer.uuid());
            if (player != null) {
                playerLane(activePlayer.uuid()).ifPresent(lane -> SemionLaneIndicatorService.showLane(player, lane));
            }
        }
    }

    private void notifyMatchStarted() {
        for (SemionPlayer player : players.values()) {
            player.job().ifPresent(job -> job.onMatchStarted(new JobContext(this, player)));
        }
    }

    private void notifyRoundStarted(int round) {
        for (SemionPlayer player : players.values()) {
            SemionTeam team = teams.get(player.teamId());
            if (team != null && team.active() && !team.eliminated()) {
                player.job().ifPresent(job -> job.onRoundStarted(new JobContext(this, player), round));
            }
        }
    }

    private void notifyRoundEnded(int round) {
        for (SemionPlayer player : players.values()) {
            SemionTeam team = teams.get(player.teamId());
            if (team != null && team.active() && !team.eliminated()) {
                player.job().ifPresent(job -> job.onRoundEnded(new JobContext(this, player), round));
            }
        }
    }

    private record TeamEliminationRecord(
            TeamId teamId,
            int round,
            long tick,
            double bossDamageTaken
    ) {
    }
}
