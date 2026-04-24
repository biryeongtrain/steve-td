package kim.biryeong.semionTd.game;

import kim.biryeong.semionTd.config.EconomyConfig;
import kim.biryeong.semionTd.config.RoundWaveConfig;
import kim.biryeong.semionTd.config.WaveConfig;
import kim.biryeong.semionTd.entity.monster.Monster;
import kim.biryeong.semionTd.map.GameArena;
import kim.biryeong.semionTd.map.LaneRegionLayout;
import kim.biryeong.semionTd.map.TeamArena;
import kim.biryeong.semionTd.summon.SummonMonsterType;
import kim.biryeong.semionTd.summon.SummonResult;
import kim.biryeong.semionTd.summon.SummonResultType;
import kim.biryeong.semionTd.summon.SummonShop;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
    private final SummonShop summonShop = new SummonShop();
    private final Random random = new Random();
    private final Map<TeamId, SemionTeam> teams = new EnumMap<>(TeamId.class);
    private final Map<UUID, SemionPlayer> players = new java.util.HashMap<>();
    private final Set<UUID> spectatorIds = new HashSet<>();
    private RoundPhase phase = RoundPhase.WAITING;
    private boolean rosterLocked;
    private int currentRound = 1;
    private long tickCounter;
    private int phaseTicks;

    public SemionGame(EconomyConfig economyConfig, WaveConfig waveConfig, GameArena arena) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.arena = arena;
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

    public boolean rosterLocked() {
        return rosterLocked;
    }

    public int spectatorCount() {
        return spectatorIds.size();
    }

    public boolean canConfigureRoster() {
        return phase == RoundPhase.WAITING && !rosterLocked;
    }

    public boolean start(MinecraftServer server, ParticipantSelectionPlan plan) {
        if (!canConfigureRoster() || plan.activeParticipants().isEmpty()) {
            return false;
        }

        spectatorIds.clear();
        spectatorIds.addAll(plan.spectatorIds());

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
        placeSpectators(server, plan);
        rosterLocked = true;
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

        if (!player.economy().spendGas(type.get().gasCost())) {
            return SummonResult.failure(SummonResultType.NOT_ENOUGH_GAS, summonId);
        }

        Optional<SemionTeam> targetTeam = randomTargetTeam(player.teamId());
        if (targetTeam.isEmpty()) {
            player.economy().addGas(type.get().gasCost(), economyConfig.gasCapForRound(currentRound));
            return SummonResult.failure(SummonResultType.NO_TARGET_TEAM, summonId);
        }

        Optional<PlayerLane> targetLane = randomTargetLane(targetTeam.get());
        if (targetLane.isEmpty()) {
            player.economy().addGas(type.get().gasCost(), economyConfig.gasCapForRound(currentRound));
            return SummonResult.failure(SummonResultType.NO_TARGET_LANE, summonId);
        }

        player.economy().addIncome(type.get().incomeGain());
        Monster monster = Monster.fromSummonType(
                type.get(),
                targetTeam.get().id(),
                targetLane.get().laneId(),
                player.uuid(),
                player.teamId()
        );
        targetLane.get().enqueueSummonedMonster(monster);
        return SummonResult.success(summonId, targetTeam.get().id(), targetLane.get().laneId());
    }

    public boolean upgradeGasProduction(UUID playerId) {
        SemionPlayer player = players.get(playerId);
        if (player == null) {
            return false;
        }
        SemionTeam team = teams.get(player.teamId());
        if (team == null || team.eliminated()) {
            return false;
        }
        return player.economy().upgradeGasProduction(economyConfig.gasProduction());
    }


    public boolean killBoss(TeamId teamId) {
        SemionTeam team = teams.get(teamId);
        if (team == null || !team.active() || team.eliminated()) {
            return false;
        }
        team.laneGroup().boss().damage(Double.MAX_VALUE);
        team.eliminate();
        checkVictory();
        return true;
    }

    public void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 20 == 0) {
            tickGas();
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
            team.tick(server);
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
        for (SemionPlayer player : players.values()) {
            SemionTeam team = teams.get(player.teamId());
            if (team != null && !team.eliminated()) {
                player.economy().payIncome();
            }
        }
        currentRound++;
        startPreparePhase();
    }

    private void startPreparePhase() {
        phase = RoundPhase.PREPARE_AND_SUMMON;
        phaseTicks = 0;
        for (SemionTeam team : livingTeams()) {
            team.resetForRound();
        }
    }

    private void tickGas() {
        long gasCap = economyConfig.gasCapForRound(currentRound);
        for (SemionPlayer player : players.values()) {
            SemionTeam team = teams.get(player.teamId());
            if (team != null && !team.eliminated()) {
                player.economy().addGas(player.economy().gasPerSec(), gasCap);
            }
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

            arena.teamArena(participant.teamId()).ifPresent(teamArena -> {
                Vec3 position = StartPlacement.activePlayerSpawn(teamArena.layout(), participant.laneId());
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
    }

    private void placeSpectators(MinecraftServer server, ParticipantSelectionPlan plan) {
        TeamId spectatorTeam = plan.activeParticipants().stream()
                .map(AssignedParticipant::teamId)
                .findFirst()
                .orElse(null);
        if (spectatorTeam == null) {
            return;
        }

        Optional<TeamArena> spectatorArena = arena.teamArena(spectatorTeam);
        if (spectatorArena.isEmpty()) {
            return;
        }

        int spectatorIndex = 0;
        for (UUID spectatorId : plan.spectatorIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(spectatorId);
            if (player == null) {
                continue;
            }

            Vec3 position = StartPlacement.spectatorSpawn(spectatorArena.get().layout(), spectatorIndex++);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(
                    spectatorArena.get().world(),
                    position.x,
                    position.y,
                    position.z,
                    Set.<Relative>of(),
                    player.getYRot(),
                    player.getXRot(),
                    false
            );
        }
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
}



