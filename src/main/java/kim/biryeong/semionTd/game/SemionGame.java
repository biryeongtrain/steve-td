package kim.biryeong.semionTd.game;

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
import kim.biryeong.semionTd.config.EconomyConfig;
import kim.biryeong.semionTd.config.RoundWaveConfig;
import kim.biryeong.semionTd.config.SummonConfig;
import kim.biryeong.semionTd.config.WaveConfig;
import kim.biryeong.semionTd.entity.monster.Monster;
import kim.biryeong.semionTd.job.JobContext;
import kim.biryeong.semionTd.job.JobRegistry;
import kim.biryeong.semionTd.job.SemionJob;
import kim.biryeong.semionTd.map.GameArena;
import kim.biryeong.semionTd.map.LaneRegionLayout;
import kim.biryeong.semionTd.map.TeamArena;
import kim.biryeong.semionTd.summon.SummonMonsterType;
import kim.biryeong.semionTd.summon.SummonResult;
import kim.biryeong.semionTd.summon.SummonResultType;
import kim.biryeong.semionTd.summon.SummonShop;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class SemionGame {
    public static final int DEFAULT_PREPARE_TICKS = 25 * 20;

    private final EconomyConfig economyConfig;
    private final WaveConfig waveConfig;
    private final GameArena arena;
    private final EconomyService economyService;
    private final SummonShop summonShop;
    private final Random random = new Random();
    private final Map<TeamId, SemionTeam> teams = new EnumMap<>(TeamId.class);
    private final Map<UUID, SemionPlayer> players = new java.util.HashMap<>();
    private final Map<UUID, SemionJob> selectedJobs = new java.util.HashMap<>();
    private final Set<UUID> initialSpectatorIds = new HashSet<>();
    private final Set<UUID> matchSpectatorIds = new HashSet<>();
    private final Set<TeamId> announcedEliminations = new HashSet<>();
    private RoundPhase phase = RoundPhase.WAITING;
    private boolean rosterLocked;
    private int currentRound = 1;
    private long tickCounter;
    private int phaseTicks;

    public SemionGame(EconomyConfig economyConfig, WaveConfig waveConfig, GameArena arena) {
        this(economyConfig, waveConfig, SummonConfig.defaultConfig(), arena);
    }

    public SemionGame(EconomyConfig economyConfig, WaveConfig waveConfig, SummonConfig summonConfig, GameArena arena) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.arena = arena;
        this.economyService = new EconomyService(economyConfig, this);
        this.summonShop = new SummonShop(summonConfig);
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

    public GameArena arena() {
        return arena;
    }

    public EconomyConfig economyConfig() {
        return economyConfig;
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

    public boolean isActiveParticipant(UUID playerId) {
        return players.containsKey(playerId);
    }

    public boolean isMatchSpectator(UUID playerId) {
        return matchSpectatorIds.contains(playerId);
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
        return Optional.of(new MatchResult(participants, initialSpectatorIds, winningTeams, currentRound));
    }

    public boolean start(MinecraftServer server, ParticipantSelectionPlan plan) {
        if (!canConfigureRoster() || plan.activeParticipants().isEmpty()) {
            return false;
        }

        initialSpectatorIds.clear();
        matchSpectatorIds.clear();
        announcedEliminations.clear();
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

        spawnBossesForActiveTeams(activeTeams);
        placeActivePlayers(server, plan.activeParticipants());
        placeSpectators(server, plan.spectatorIds());
        rosterLocked = true;
        notifyMatchStarted();
        startPreparePhase();
        return true;
    }

    public void close() {
        arena.unload();
    }

    public SummonResult summonMonster(UUID playerId, String summonId) {
        if (phase != RoundPhase.PREPARE_AND_SUMMON) {
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
        SemionJob job = player.job().orElse(JobRegistry.defaultJob());
        if (!job.canUseSummon(jobContext, type.get())) {
            return SummonResult.failure(SummonResultType.SUMMON_NOT_ALLOWED_BY_JOB, summonId);
        }
        long gasCost = Math.max(0, job.modifySummonGasCost(jobContext, type.get(), type.get().gasCost()));
        long incomeGain = Math.max(0, job.modifySummonIncomeGain(jobContext, type.get(), type.get().incomeGain()));

        if (!economyService.spendForSummon(player, gasCost)) {
            return SummonResult.failure(SummonResultType.NOT_ENOUGH_GAS, summonId);
        }

        Optional<SemionTeam> targetTeam = randomTargetTeam(player.teamId());
        if (targetTeam.isEmpty()) {
            economyService.refundSummon(player, gasCost, currentRound);
            return SummonResult.failure(SummonResultType.NO_TARGET_TEAM, summonId);
        }

        Optional<PlayerLane> targetLane = randomTargetLane(targetTeam.get());
        if (targetLane.isEmpty()) {
            economyService.refundSummon(player, gasCost, currentRound);
            return SummonResult.failure(SummonResultType.NO_TARGET_LANE, summonId);
        }

        economyService.applySummonIncome(player, incomeGain);
        player.matchStats().recordSummonedMonster();
        Monster monster = Monster.fromSummonType(
                type.get(),
                targetTeam.get().id(),
                targetLane.get().laneId(),
                player.uuid(),
                player.teamId()
        );
        job.onSummonedMonster(jobContext, type.get(), monster);
        targetLane.get().enqueueSummonedMonster(monster);
        return SummonResult.success(summonId, targetTeam.get().id(), targetLane.get().laneId());
    }

    public boolean upgradeGasProduction(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return false;
        }
        SemionTeam team = teams.get(player.teamId());
        return economyService.upgradeGasProduction(player, team);
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
        announcedEliminations.add(teamId);
        handleTeamEliminated(server, team);
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

    public void tick(MinecraftServer server) {
        tickCounter++;
        if (phase != RoundPhase.WAITING && phase != RoundPhase.ENDED && tickCounter % 20 == 0) {
            economyService.tickGas(players.values(), teams, currentRound);
        }

        switch (phase) {
            case WAITING, ENDED -> {
            }
            case PREPARE_AND_SUMMON -> tickPrepare();
            case LANE_WAVE -> tickWave(server);
            case ROUND_PAYOUT -> tickPayout();
        }
    }

    private void tickPrepare() {
        phaseTicks++;
        if (phaseTicks >= DEFAULT_PREPARE_TICKS) {
            startWavePhase();
        }
    }

    private void startWavePhase() {
        phase = RoundPhase.LANE_WAVE;
        phaseTicks = 0;
        for (SemionTeam team : livingTeams()) {
            enqueueWave(team);
        }
    }

    private void tickWave(MinecraftServer server) {
        for (SemionTeam team : teams.values()) {
            team.tick(server, economyService, players);
            if (team.active() && team.eliminated() && announcedEliminations.add(team.id())) {
                handleTeamEliminated(server, team);
            }
        }
        if (checkVictory()) {
            return;
        }
        if (livingTeams().stream().allMatch(SemionTeam::isRoundResolved)) {
            phase = RoundPhase.ROUND_PAYOUT;
            phaseTicks = 0;
        }
    }

    private void tickPayout() {
        notifyRoundEnded(currentRound);
        economyService.payRoundIncome(players.values(), teams);
        currentRound++;
        startPreparePhase();
    }

    private void startPreparePhase() {
        phase = RoundPhase.PREPARE_AND_SUMMON;
        phaseTicks = 0;
        for (SemionTeam team : livingTeams()) {
            team.resetForRound();
        }
        notifyRoundStarted(currentRound);
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

    private Optional<SemionTeam> randomTargetTeam(TeamId senderTeam) {
        List<SemionTeam> candidates = livingTeams().stream()
                .filter(team -> team.id() != senderTeam)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    private Optional<PlayerLane> randomTargetLane(SemionTeam team) {
        List<PlayerLane> lanes = team.laneGroup().lanes();
        if (lanes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(lanes.get(random.nextInt(lanes.size())));
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
            player.teleportTo(
                    teamArena.world(),
                    position.x,
                    position.y,
                    position.z,
                    Set.<Relative>of(),
                    player.getYRot(),
                    player.getXRot(),
                    false
            );
        });
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
            player.teleportTo(
                    teamArena.world(),
                    position.x,
                    position.y,
                    position.z,
                    Set.<Relative>of(),
                    player.getYRot(),
                    player.getXRot(),
                    false
            );
        });
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
            player.sendSystemMessage(Component.literal("Your team has been eliminated. You are now spectating."));
        }
        for (UUID memberId : team.memberIds()) {
            SemionPlayer semionPlayer = players.get(memberId);
            if (semionPlayer != null) {
                semionPlayer.job().ifPresent(job -> job.onEliminated(new JobContext(this, semionPlayer)));
            }
        }

        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("Semion TD: " + team.id().name() + " team has been eliminated."),
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
}
