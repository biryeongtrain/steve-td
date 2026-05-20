package kim.biryeong.semiontd.game;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.map.ArenaLoadException;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.GameArenaLoader;
import kim.biryeong.semiontd.map.LobbyWorld;
import kim.biryeong.semiontd.map.LobbyWorldLoader;
import kim.biryeong.semiontd.music.SemionMusicService;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import kim.biryeong.semiontd.progression.ProgressionService;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.ui.SemionDialogService;
import kim.biryeong.semiontd.ui.SemionDisplayHudService;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.util.Scheduler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;

public final class SemionGameManager {
    private static final int STARTUP_LOBBY_LOAD_DELAY_TICKS = 20;
    public static final int START_COUNTDOWN_TICKS = 5 * 20;
    public static final int MATCH_RESULT_DELAY_TICKS = 5 * 20;

    private EconomyConfig economyConfig = EconomyConfig.defaultConfig();
    private WaveConfig waveConfig = WaveConfig.defaultConfig();
    private MapConfig mapConfig = MapConfig.defaultConfig();
    private ProgressionConfig progressionConfig = ProgressionConfig.defaultConfig();
    private TowerBalanceConfig towerBalanceConfig = TowerBalanceConfig.defaultConfig();
    private SummonConfig summonConfig = SummonConfig.defaultConfig();
    private Path configDir;
    private Path progressionStorePath;
    private ProgressionService progressionService = new ProgressionService(progressionConfig, null);
    private SemionMusicService musicService = SemionMusicService.disabled();
    private final SemionDialogService dialogService = new SemionDialogService();
    private final SemionDisplayHudService displayHudService = new SemionDisplayHudService();
    private MatchMode matchMode = MatchMode.NORMAL;
    private SemionGame activeGame;
    private LobbyWorld lobbyWorld;
    private MatchResult lastMatchResult;
    private final Set<UUID> nextMatchPriorityPlayerIds = new HashSet<>();
    private SemionGame pendingFinishedGame;
    private int pendingFinishDelayTicks;
    private ParticipantSelectionPlan pendingStartPlan;
    private int startCountdownTicks;
    private int nextStartCountdownAnnouncementSecond;
    private boolean startupLobbyLoadPending;
    private int startupLobbyLoadDelayTicks;

    public enum StartCountdownResult {
        SCHEDULED,
        NO_ACTIVE_GAME,
        NOT_WAITING,
        ALREADY_PENDING,
        PRELOAD_FAILED
    }

    public record ReloadConfigResult(boolean reloaded, boolean activeGameUpdated, Path configDir) {
    }

    public void configure(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            MapConfig mapConfig,
            ProgressionConfig progressionConfig,
            Path progressionStorePath
    ) {
        configure(
                economyConfig,
                waveConfig,
                mapConfig,
                progressionConfig,
                TowerBalanceConfig.defaultConfig(),
                SummonConfig.defaultConfig(),
                progressionStorePath
        );
    }

    public void configure(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            MapConfig mapConfig,
            ProgressionConfig progressionConfig,
            TowerBalanceConfig towerBalanceConfig,
            Path progressionStorePath
    ) {
        configure(
                economyConfig,
                waveConfig,
                mapConfig,
                progressionConfig,
                towerBalanceConfig,
                SummonConfig.defaultConfig(),
                progressionStorePath
        );
    }

    public void configure(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            MapConfig mapConfig,
            ProgressionConfig progressionConfig,
            TowerBalanceConfig towerBalanceConfig,
            SummonConfig summonConfig,
            Path progressionStorePath
    ) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.mapConfig = mapConfig;
        this.progressionConfig = progressionConfig;
        this.towerBalanceConfig = towerBalanceConfig == null ? TowerBalanceConfig.defaultConfig() : towerBalanceConfig;
        this.summonConfig = summonConfig == null ? SummonConfig.defaultConfig() : summonConfig;
        this.progressionStorePath = progressionStorePath;
        this.configDir = progressionStorePath == null ? null : progressionStorePath.getParent();
        this.progressionService = new ProgressionService(progressionConfig, progressionStorePath);
        ProductionTowerCatalogs.reloadBuiltIns(this.towerBalanceConfig);
        IncomeSummons.reloadBuiltIns(this.summonConfig);
    }

    public ReloadConfigResult reloadConfigs(MinecraftServer server) {
        if (configDir == null) {
            return new ReloadConfigResult(false, false, null);
        }

        LoadedConfigs configs = SemionConfigLoader.load(configDir, SemionTd.LOGGER);
        configure(
                configs.economy(),
                configs.waves(),
                configs.map(),
                configs.progression(),
                configs.towerBalance(),
                configs.summons(),
                configDir.resolve("profiles.json")
        );
        boolean activeGameUpdated = activeGame != null && activeGame.phase() != RoundPhase.ENDED;
        if (activeGameUpdated) {
            activeGame.applyConfigs(configs.economy(), configs.waves());
            activeGame.refreshProductionTowerTypes();
            activeGame.refreshSummonShop();
            displayHudService.refreshNow(server, activeGame, matchMode);
        }
        return new ReloadConfigResult(true, activeGameUpdated, configDir);
    }

    public void configureMusic(SemionMusicService musicService) {
        this.musicService = musicService == null ? SemionMusicService.disabled() : musicService;
    }

    public LobbyWorld ensureLobby(MinecraftServer server) throws ArenaLoadException {
        if (lobbyWorld != null) {
            return lobbyWorld;
        }
        lobbyWorld = LobbyWorldLoader.load(server);
        return lobbyWorld;
    }

    public Optional<LobbyWorld> lobbyWorld() {
        return Optional.ofNullable(lobbyWorld);
    }

    public ProgressionConfig progressionConfig() {
        return progressionConfig;
    }

    public SemionPlayerProfile profile(MinecraftServer server, UUID playerId, String playerName) {
        return progressionService.profile(server, playerId, playerName);
    }

    public SemionPlayerProfile saveSelectedJob(MinecraftServer server, UUID playerId, String playerName, ResourceLocation jobId) {
        return progressionService.saveSelectedJob(server, playerId, playerName, jobId);
    }

    public Optional<MatchResult> lastMatchResult() {
        return Optional.ofNullable(lastMatchResult);
    }

    public Set<UUID> nextMatchPriorityPlayerIds() {
        return Set.copyOf(nextMatchPriorityPlayerIds);
    }

    public SemionDialogService dialogService() {
        return dialogService;
    }

    public SemionDisplayHudService displayHudService() {
        return displayHudService;
    }

    public void sendAllPlayersToLobby(MinecraftServer server) throws ArenaLoadException {
        ensureLobby(server);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            try {
                sendPlayerToLobby(server, player);
            } catch (RuntimeException exception) {
                SemionTd.LOGGER.warn(
                        "Failed to send player {} to the Semion TD lobby; disconnecting for a clean reconnect.",
                        player.getGameProfile().getName(),
                        exception
                );
                disconnectForLobbyReset(player);
            }
        }
    }

    public void scheduleStartupLobbyLoad(MinecraftServer server) {
        if (lobbyWorld != null) {
            return;
        }
        startupLobbyLoadPending = true;
        startupLobbyLoadDelayTicks = STARTUP_LOBBY_LOAD_DELAY_TICKS;
    }

    public void tickStartupLobbyLoad(MinecraftServer server) {
        if (!startupLobbyLoadPending || lobbyWorld != null) {
            return;
        }
        if (startupLobbyLoadDelayTicks > 0) {
            startupLobbyLoadDelayTicks--;
            return;
        }
        startupLobbyLoadPending = false;
        try {
            ensureLobby(server);
        } catch (ArenaLoadException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to load the Semion TD lobby on server start.", exception);
        }
    }

    public SemionGame createGame(MinecraftServer server) throws ArenaLoadException {
        ensureLobby(server);

        if (activeGame != null) {
            displayHudService.clear(server);
            sendAllPlayersToLobby(server);
            closeActiveGameSafely(activeGame, "replacing active game during create");
        }
        pendingFinishedGame = null;
        pendingFinishDelayTicks = 0;

        GameArena arena = GameArenaLoader.load(server, mapConfig);
        activeGame = new SemionGame(economyConfig, waveConfig, arena);
        applyPersistedJobSelections(server, activeGame);
        lastMatchResult = null;
        VanillaTeamBridge.ensureTeams(server);
        sendAllPlayersToLobby(server);
        displayHudService.refreshNow(server, activeGame, matchMode);
        return activeGame;
    }

    public boolean resetToLobby(MinecraftServer server) throws ArenaLoadException {
        ensureLobby(server);
        boolean hadActiveGame = activeGame != null;
        displayHudService.clear(server);
        sendAllPlayersToLobby(server);
        if (activeGame != null) {
            closeActiveGameSafely(activeGame, "resetting match to lobby");
        }
        pendingFinishedGame = null;
        pendingFinishDelayTicks = 0;
        lastMatchResult = null;
        displayHudService.clear(server);
        return hadActiveGame;
    }

    public Optional<SemionGame> activeGame() {
        return Optional.ofNullable(activeGame);
    }

    public boolean startCountdownActive() {
        return pendingStartPlan != null;
    }

    public int startCountdownSecondsRemaining() {
        if (pendingStartPlan == null) {
            return 0;
        }
        return Math.max(1, (startCountdownTicks + 19) / 20);
    }

    public StartCountdownResult scheduleStart(MinecraftServer server, ParticipantSelectionPlan plan) {
        if (activeGame == null) {
            return StartCountdownResult.NO_ACTIVE_GAME;
        }
        if (pendingStartPlan != null) {
            return StartCountdownResult.ALREADY_PENDING;
        }
        if (!activeGame.canConfigureRoster()) {
            return StartCountdownResult.NOT_WAITING;
        }
        if (!activeGame.preloadWorldsForStart(plan)) {
            return StartCountdownResult.PRELOAD_FAILED;
        }

        pendingStartPlan = plan;
        startCountdownTicks = START_COUNTDOWN_TICKS;
        nextStartCountdownAnnouncementSecond = startCountdownSecondsRemaining();
        announceStartCountdown(server, nextStartCountdownAnnouncementSecond);
        return StartCountdownResult.SCHEDULED;
    }

    public MatchMode matchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode;
    }

    public void tick(MinecraftServer server) {
        musicService.tick(server, activeGame);
        if (activeGame == null) {
            clearStartCountdown();
            return;
        }

        if (pendingFinishedGame != null) {
            if (pendingFinishDelayTicks > 0) {
                pendingFinishDelayTicks--;
                return;
            }
            finishActiveGame(server, pendingFinishedGame);
            return;
        }

        if (pendingStartPlan != null) {
            tickStartCountdown(server);
            displayHudService.tick(server, activeGame, matchMode);
            return;
        }

        activeGame.tick(server);
        if (activeGame != null && activeGame.phase() == RoundPhase.ENDED) {
            beginDelayedMatchResult(server, activeGame);
            return;
        }
        if (activeGame != null) {
            displayHudService.tick(server, activeGame, matchMode);
        }
    }

    public void handlePlayerJoin(ServerPlayer player) {
        musicService.handlePlayerJoin(player);
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        VanillaTeamBridge.ensureTeams(server);

        Scheduler.INSTANCE.submit((s) -> {
            if (activeGame != null && activeGame.rosterLocked()) {
                if (activeGame.restorePlayerPlacement(s, player)) {
                    return;
                }

                VanillaTeamBridge.assignSpectator(s, player);
                try {
                    sendPlayerToLobby(s, player);
                } catch (ArenaLoadException exception) {
                    SemionTd.LOGGER.warn("Failed to send late-joining player {} to lobby.", player.getGameProfile().getName(), exception);
                }
                player.sendSystemMessage(SemionText.prefixedPlain("게임이 진행 중입니다. 관전하려면 /semiontd spectate를 사용하세요."));
                return;
            }

            if (activeGame != null && activeGame.canConfigureRoster()) {
                applyPersistedJobSelection(server, activeGame, player);
            }
            try {
                sendPlayerToLobby(server, player);
                if (activeGame != null && activeGame.canConfigureRoster()) {
                    displayHudService.refreshNow(server, activeGame, matchMode);
                }
            } catch (ArenaLoadException exception) {
                SemionTd.LOGGER.warn("Failed to send player {} to lobby.", player.getGameProfile().getName(), exception);
            }
        },1);
    }

    public void handlePlayerWorldChanged(ServerPlayer player) {
        musicService.handlePlayerWorldChanged(player);
    }

    public void shutdown() {
        if (activeGame != null) {
            activeGame.close();
            activeGame = null;
        }
        clearStartCountdown();
        pendingFinishedGame = null;
        pendingFinishDelayTicks = 0;
        if (lobbyWorld != null) {
            lobbyWorld.unload();
            lobbyWorld = null;
        }
        startupLobbyLoadPending = false;
        startupLobbyLoadDelayTicks = 0;
    }

    private void sendPlayerToLobby(MinecraftServer server, ServerPlayer player) throws ArenaLoadException {
        LobbyWorld lobby = ensureLobby(server);
        VanillaTeamBridge.assignSpectator(server, player);
        player.setGameMode(GameType.ADVENTURE);
        player.teleport(
                new TeleportTransition(
                        lobby.world(),
                        lobby.spawn(),
                        player.getDeltaMovement(),
                        player.getXRot(),
                        player.getYRot(),
                        TeleportTransition.DO_NOTHING
                )
        );
        SemionDisplayHudService.refreshPlayerHud(player);
        SemionHotbarService.clearMatchTools(player);
        setFlight(player, false);
    }

    private static void setFlight(ServerPlayer player, boolean enabled) {
        player.getAbilities().mayfly = enabled;
        player.getAbilities().flying = enabled;
        player.onUpdateAbilities();
    }

    private void applyPersistedJobSelections(MinecraftServer server, SemionGame game) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyPersistedJobSelection(server, game, player);
        }
    }

    private void applyPersistedJobSelection(MinecraftServer server, SemionGame game, ServerPlayer player) {
        SemionPlayerProfile profile = progressionService.profile(
                server,
                player.getUUID(),
                player.getGameProfile().getName()
        );
        profile.selectedJobResource().ifPresent(jobId -> {
            if (!game.selectJob(player.getUUID(), jobId)) {
                SemionTd.LOGGER.warn(
                        "Ignoring persisted Semion TD job {} for player {}; the job is not available.",
                        jobId,
                        player.getGameProfile().getName()
                );
            }
        });
    }

    private void tickStartCountdown(MinecraftServer server) {
        if (pendingStartPlan == null) {
            return;
        }
        if (activeGame == null || !activeGame.canConfigureRoster()) {
            clearStartCountdown();
            return;
        }

        if (startCountdownTicks > 0) {
            startCountdownTicks--;
            int secondsRemaining = (startCountdownTicks + 19) / 20;
            if (secondsRemaining > 0 && secondsRemaining < nextStartCountdownAnnouncementSecond) {
                nextStartCountdownAnnouncementSecond = secondsRemaining;
                announceStartCountdown(server, secondsRemaining);
            }
            if (startCountdownTicks > 0) {
                return;
            }
        }

        ParticipantSelectionPlan plan = pendingStartPlan;
        clearStartCountdown();
        if (!activeGame.start(server, plan)) {
            server.getPlayerList().broadcastSystemMessage(
                    SemionText.prefixedPlain("시작 카운트다운이 취소되었습니다. 참가자 확정에 실패했습니다."),
                    false
            );
            return;
        }
        clearPriorityForActiveParticipants(plan);
        server.getPlayerList().broadcastSystemMessage(SemionText.prefixedMini("<green><bold>게임을 시작합니다.</bold></green>"), false);
        displayHudService.refreshNow(server, activeGame, matchMode);
    }

    private void announceStartCountdown(MinecraftServer server, int secondsRemaining) {
        server.getPlayerList().broadcastSystemMessage(
                SemionText.prefixedMini("<yellow>" + secondsRemaining + "초</yellow> 후 게임을 시작합니다."),
                false
        );
        server.getPlayerList().getPlayers().forEach(player -> {
            player.playNotifySound(SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.MUSIC, 1557f, 1f);
        });
    }

    private void clearStartCountdown() {
        pendingStartPlan = null;
        startCountdownTicks = 0;
        nextStartCountdownAnnouncementSecond = 0;
    }

    private void closeActiveGameSafely(SemionGame game, String reason) {
        if (game == null) {
            return;
        }
        if (activeGame == game) {
            activeGame = null;
            clearStartCountdown();
        }
        if (pendingFinishedGame == game) {
            pendingFinishedGame = null;
            pendingFinishDelayTicks = 0;
        }
        try {
            game.close();
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to close Semion TD game while {}. Continuing with manager state cleared.", reason, exception);
        }
    }

    private void disconnectForLobbyReset(ServerPlayer player) {
        try {
            player.connection.disconnect(SemionText.prefixedPlain("로비 이동에 실패했습니다. 다시 접속해 주세요."));
        } catch (RuntimeException disconnectException) {
            SemionTd.LOGGER.warn(
                    "Failed to disconnect player {} after lobby reset failure.",
                    player.getGameProfile().getName(),
                    disconnectException
            );
        }
    }

    private void beginDelayedMatchResult(MinecraftServer server, SemionGame finishedGame) {
        pendingFinishedGame = finishedGame;
        pendingFinishDelayTicks = MATCH_RESULT_DELAY_TICKS;
        displayHudService.clear(server);
        server.getPlayerList().broadcastSystemMessage(
                SemionText.prefixedMini("<gold>경기 종료.</gold> 결과를 집계하는 중입니다..."),
                false
        );
    }

    private void finishActiveGame(MinecraftServer server, SemionGame finishedGame) {
        displayHudService.clear(server);
        Optional<MatchResult> result = finishedGame.matchResult();
        if (result.isPresent()) {
            lastMatchResult = result.get();
            nextMatchPriorityPlayerIds.addAll(result.get().spectatorIds());
            Map<UUID, MatchProgressionReward> rewards = progressionService.applyMatchResult(server, result.get());
            announceMatchResult(server, result.get(), rewards);
            showMatchResultDialogs(server, result.get(), rewards);
        } else {
            lastMatchResult = null;
        }

        try {
            sendAllPlayersToLobby(server);
        } catch (ArenaLoadException exception) {
            SemionTd.LOGGER.warn("Failed to send players to the Semion TD lobby after match end.", exception);
        }

        closeActiveGameSafely(finishedGame, "finishing match");
    }

    private void clearPriorityForActiveParticipants(ParticipantSelectionPlan plan) {
        for (AssignedParticipant participant : plan.activeParticipants()) {
            nextMatchPriorityPlayerIds.remove(participant.uuid());
        }
    }

    private void announceMatchResult(
            MinecraftServer server,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        String winners = matchResult.winningTeams().isEmpty()
                ? "없음"
                : matchResult.winningTeams().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        server.getPlayerList().broadcastSystemMessage(
                SemionText.prefixedMini("<gold>경기 종료.</gold> <gray>승리팀</gray> <aqua>" + winners + "</aqua><dark_gray>,</dark_gray> <gray>라운드</gray> <yellow>" + matchResult.finalRound() + "</yellow>"),
                false
        );

        for (MatchParticipantResult participant : matchResult.participants()) {
            MatchProgressionReward reward = rewards.get(participant.playerId());
            if (reward == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player == null) {
                continue;
            }
            player.sendSystemMessage(SemionText.prefixedMini(
                    "<gray>보상</gray> <gold>+" + reward.currencyAwarded() + "</gold> "
                            + "<dark_gray>|</dark_gray> <gray>경기</gray> <white>" + reward.profile().gamesPlayed() + "</white>"
                            + " <gray>승</gray> <green>" + reward.profile().wins() + "</green>"
                            + " <gray>패</gray> <red>" + reward.profile().losses() + "</red>"
                            + " <gray>보유</gray> <aqua>" + reward.profile().cosmeticCurrency() + "</aqua>"
            ));
        }
    }

    private void showMatchResultDialogs(
            MinecraftServer server,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            dialogService.showMatchResult(player, matchResult, rewards);
        }
    }
}
