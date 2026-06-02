package kim.biryeong.semiontd.game;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.buildguide.BuildGuideService;
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
import kim.biryeong.semiontd.persistence.AppliedMatchRepository;
import kim.biryeong.semiontd.persistence.CascadingAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.CascadingMatchResultRepository;
import kim.biryeong.semiontd.persistence.FileAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileMatchResultRepository;
import kim.biryeong.semiontd.persistence.FileRatingEventRepository;
import kim.biryeong.semiontd.persistence.FileRatingRepository;
import kim.biryeong.semiontd.persistence.LoggingAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.LoggingMatchResultRepository;
import kim.biryeong.semiontd.persistence.MatchResultRepository;
import kim.biryeong.semiontd.persistence.PersistenceException;
import kim.biryeong.semiontd.persistence.RatingEventRepository;
import kim.biryeong.semiontd.persistence.RatingRepository;
import kim.biryeong.semiontd.persistence.SemionPersistenceBackendType;
import kim.biryeong.semiontd.persistence.SemionPersistenceConfig;
import kim.biryeong.semiontd.persistence.SQLiteAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.SQLiteMatchResultRepository;
import kim.biryeong.semiontd.persistence.SQLiteRatingEventRepository;
import kim.biryeong.semiontd.persistence.SQLiteRatingRepository;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import kim.biryeong.semiontd.progression.ProgressionService;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingMatchResult;
import kim.biryeong.semiontd.rating.RatingService;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.legion.IllusionCloneSpawnQueue;
import kim.biryeong.semiontd.ui.SemionDialogService;
import kim.biryeong.semiontd.ui.SemionDisplayHudService;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.util.Scheduler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
    public static final int MATCH_RESULT_DIALOG_AFTER_LOBBY_DELAY_TICKS = 2 * 20;
    static final int RATING_RETRY_DELAY_TICKS = 20;

    private EconomyConfig economyConfig = EconomyConfig.defaultConfig();
    private WaveConfig waveConfig = WaveConfig.defaultConfig();
    private MapConfig mapConfig = MapConfig.defaultConfig();
    private ProgressionConfig progressionConfig = ProgressionConfig.defaultConfig();
    private SemionPersistenceConfig persistenceConfig = SemionPersistenceConfig.defaultConfig();
    private TowerBalanceConfig towerBalanceConfig = TowerBalanceConfig.defaultConfig();
    private SummonConfig summonConfig = SummonConfig.defaultConfig();
    private Path configDir;
    private Path progressionStorePath;
    private ProgressionService progressionService = new ProgressionService(progressionConfig, null);
    private RatingService ratingService = new RatingService(null);
    private MatchResultRepository matchResultRepository = new FileMatchResultRepository(null);
    private SemionMusicService musicService = SemionMusicService.disabled();
    private final SemionDialogService dialogService = new SemionDialogService();
    private final SemionDisplayHudService displayHudService = new SemionDisplayHudService();
    private final BuildGuideService buildGuideService = new BuildGuideService(null);
    private MatchMode matchMode = MatchMode.NORMAL;
    private SemionGame activeGame;
    private LobbyWorld lobbyWorld;
    private MatchResult lastMatchResult;
    private final Set<UUID> nextMatchPriorityPlayerIds = new HashSet<>();
    private SemionGame pendingFinishedGame;
    private int pendingFinishDelayTicks;
    private MatchResult pendingMatchResultDialog;
    private final Map<MatchId, MatchResult> pendingRatingRetryMatchResults = new LinkedHashMap<>();
    private int pendingRatingRetryDelayTicks;
    private Map<UUID, MatchProgressionReward> pendingMatchResultRewards = Map.of();
    private int pendingMatchResultDialogDelayTicks;
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
                SemionPersistenceConfig.defaultConfig(),
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
        configure(
                economyConfig,
                waveConfig,
                mapConfig,
                progressionConfig,
                towerBalanceConfig,
                summonConfig,
                SemionPersistenceConfig.defaultConfig(),
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
            SemionPersistenceConfig persistenceConfig,
            Path progressionStorePath
    ) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.mapConfig = mapConfig;
        this.progressionConfig = progressionConfig;
        this.persistenceConfig = persistenceConfig == null ? SemionPersistenceConfig.defaultConfig() : persistenceConfig;
        this.towerBalanceConfig = towerBalanceConfig == null ? TowerBalanceConfig.defaultConfig() : towerBalanceConfig;
        this.summonConfig = summonConfig == null ? SummonConfig.defaultConfig() : summonConfig;
        this.progressionStorePath = progressionStorePath;
        this.configDir = progressionStorePath == null ? null : progressionStorePath.getParent();
        Path matchResultPath = this.configDir == null ? null : this.configDir.resolve("match-results.json");
        Path ratingProfilePath = this.configDir == null ? null : this.configDir.resolve("ratings.json");
        Path ratingEventPath = this.configDir == null ? null : this.configDir.resolve("rating-events.json");
        Path sqlitePath = resolveSqlitePath(this.configDir, this.persistenceConfig);
        Path appliedMatchesPath = progressionStorePath == null
                ? null
                : progressionStorePath.resolveSibling("progression-applied-matches.json");
        Path ratingAppliedMatchesPath = progressionStorePath == null
                ? null
                : progressionStorePath.resolveSibling("rating-applied-matches.json");
        this.progressionService = new ProgressionService(
                progressionConfig,
                progressionStorePath,
                createAppliedMatchRepository(this.persistenceConfig, sqlitePath, appliedMatchesPath, this.configDir)
        );
        this.ratingService = new RatingService(
                createRatingRepository(this.persistenceConfig, sqlitePath, ratingProfilePath),
                createRatingEventRepository(this.persistenceConfig, sqlitePath, ratingEventPath),
                createAppliedMatchRepository(this.persistenceConfig, sqlitePath, ratingAppliedMatchesPath, this.configDir)
        );
        this.matchResultRepository = createMatchResultRepository(this.persistenceConfig, sqlitePath, matchResultPath, this.configDir);
        this.buildGuideService.configure(this.configDir == null ? null : this.configDir.resolve("build_guides.json"));
        ProductionTowerCatalogs.reloadBuiltIns(this.towerBalanceConfig);
        IncomeSummons.reloadBuiltIns(this.summonConfig);
    }

    private static Path resolveSqlitePath(Path configDir, SemionPersistenceConfig persistenceConfig) {
        if (configDir == null || persistenceConfig.backend() != SemionPersistenceBackendType.SQLITE) {
            return null;
        }
        Path configured = Path.of(persistenceConfig.sqlitePath());
        return configured.isAbsolute() ? configured : configDir.resolve(configured).normalize();
    }

    static MatchResultRepository createMatchResultRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath,
            Path configDir
    ) {
        MatchResultRepository file = new FileMatchResultRepository(filePath);
        MatchResultRepository log = new LoggingMatchResultRepository(fallbackLogPath(configDir, "match-results-fallback.log"));
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite match-result repository is required but no SQLite path is available.");
            }
            return new CascadingMatchResultRepository(file, log, log);
        }
        try {
            return new CascadingMatchResultRepository(new SQLiteMatchResultRepository(sqlitePath), file, log);
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite match-result repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite match-result repository initialization failed; using file/log fallback.", exception);
            return new CascadingMatchResultRepository(file, log, log);
        }
    }

    static AppliedMatchRepository createAppliedMatchRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath,
            Path configDir
    ) {
        AppliedMatchRepository file = new FileAppliedMatchRepository(filePath);
        AppliedMatchRepository log = new LoggingAppliedMatchRepository(fallbackLogPath(configDir, "applied-matches-fallback.log"));
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite applied-match repository is required but no SQLite path is available.");
            }
            return new CascadingAppliedMatchRepository(file, log, log);
        }
        try {
            return new CascadingAppliedMatchRepository(new SQLiteAppliedMatchRepository(sqlitePath), file, log);
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite applied-match repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite applied-match repository initialization failed; using file/log fallback.", exception);
            return new CascadingAppliedMatchRepository(file, log, log);
        }
    }

    static RatingRepository createRatingRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath
    ) {
        RatingRepository file = new FileRatingRepository(filePath);
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite rating repository is required but no SQLite path is available.");
            }
            return file;
        }
        try {
            RatingRepository sqlite = new SQLiteRatingRepository(sqlitePath);
            migrateFallbackRatingProfiles(file, sqlite);
            return sqlite;
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite rating repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite rating repository initialization failed; using file fallback.", exception);
            return file;
        }
    }

    static void migrateFallbackRatingProfiles(RatingRepository fallback, RatingRepository primary) {
        int migrated = 0;
        for (PlayerRatingProfile fallbackProfile : fallback.findAllProfiles().values()) {
            Optional<PlayerRatingProfile> existing = primary.findProfile(fallbackProfile.playerId());
            if (existing.isPresent()
                    && existing.get().updatedAtEpochMillis() >= fallbackProfile.updatedAtEpochMillis()) {
                continue;
            }
            primary.saveProfile(fallbackProfile.playerId(), fallbackProfile);
            migrated++;
        }
        if (migrated > 0) {
            SemionTd.LOGGER.info("Migrated {} fallback rating profiles into primary rating repository.", migrated);
        }
    }

    static void migrateFallbackRatingEvents(RatingEventRepository fallback, RatingEventRepository primary) {
        int migrated = 0;
        for (RatingMatchResult fallbackResult : fallback.findAllMatchResults().values()) {
            if (primary.findMatchResult(fallbackResult.matchId()).isPresent()) {
                continue;
            }
            primary.saveMatchResult(fallbackResult);
            migrated++;
        }
        if (migrated > 0) {
            SemionTd.LOGGER.info("Migrated {} fallback rating events into primary rating-event repository.", migrated);
        }
    }

    static RatingEventRepository createRatingEventRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath
    ) {
        RatingEventRepository file = new FileRatingEventRepository(filePath);
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite rating-event repository is required but no SQLite path is available.");
            }
            return file;
        }
        try {
            RatingEventRepository sqlite = new SQLiteRatingEventRepository(sqlitePath);
            migrateFallbackRatingEvents(file, sqlite);
            return sqlite;
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite rating-event repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite rating-event repository initialization failed; using file fallback.", exception);
            return file;
        }
    }

    private static boolean requiresSQLite(SemionPersistenceConfig persistenceConfig) {
        return persistenceConfig.backend() == SemionPersistenceBackendType.SQLITE && persistenceConfig.externalDbRequired();
    }

    private static Path fallbackLogPath(Path configDir, String fileName) {
        return configDir == null ? null : configDir.resolve(fileName);
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
                configs.persistence(),
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

    public Optional<PlayerRatingProfile> ratingProfile(UUID playerId) {
        return ratingService.profile(playerId);
    }

    public List<PlayerRatingProfile> topRatingProfiles(int limit) {
        return ratingService.topProfiles(limit);
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

    public BuildGuideService buildGuideService() {
        return buildGuideService;
    }

    public Optional<BuildGuide> publishLastBuild(ServerPlayer player, String title) {
        if (player == null) {
            return Optional.empty();
        }
        return buildGuideService.publishLastRecording(player.getUUID(), title);
    }

    public void showBuildList(ServerPlayer player) {
        showBuildList(player, 1, 1);
    }

    public void showBuildList(ServerPlayer player, int publicPage, int myPage) {
        showBuildList(player, false, publicPage, myPage);
    }

    public void showDebugBuildList(ServerPlayer player) {
        showBuildList(player, true, 1, 1);
    }

    private void showBuildList(ServerPlayer player, boolean includeDebugGuides, int publicPage, int myPage) {
        SemionPlayerProfile profile = progressionService.profile(
                player.getServer(),
                player.getUUID(),
                player.getGameProfile().getName()
        );
        if (includeDebugGuides) {
            dialogService.showDebugBuildGuides(player, buildGuideService, profile);
        } else {
            dialogService.showBuildGuides(player, buildGuideService, profile, publicPage, myPage);
        }
    }

    public boolean showBuildDetails(ServerPlayer player, String code) {
        Optional<BuildGuide> guide = player == null ? Optional.empty() : buildGuideService.findViewable(code, player.getUUID());
        if (player == null || guide.isEmpty()) {
            return false;
        }
        dialogService.showBuildGuideDetails(player, guide.get());
        return true;
    }

    public Optional<BuildGuide> trackBuild(MinecraftServer server, ServerPlayer player, String code) {
        if (player == null || code == null) {
            return Optional.empty();
        }
        if (!buildGuideService.track(player.getUUID(), code)) {
            return Optional.empty();
        }
        Optional<BuildGuide> guide = buildGuideService.find(code);
        if (guide.isEmpty()) {
            return Optional.empty();
        }
        progressionService.rememberRecentBuildCode(server, player.getUUID(), player.getGameProfile().getName(), guide.get().code());
        return guide;
    }

    public void clearTrackedBuild(ServerPlayer player) {
        if (player != null) {
            buildGuideService.clearTracked(player.getUUID());
        }
    }

    public Optional<BuildGuide> setBuildVisibility(ServerPlayer player, String code, String visibility) {
        if (player == null) {
            return Optional.empty();
        }
        return buildGuideService.setVisibility(player.getUUID(), code, visibility);
    }

    public boolean deleteBuild(ServerPlayer player, String code) {
        return player != null && buildGuideService.delete(player.getUUID(), code);
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
        clearPendingMatchResultDialog();

        GameArena arena = GameArenaLoader.load(server, mapConfig);
        activeGame = new SemionGame(economyConfig, waveConfig, arena, buildGuideService);
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
        clearPendingMatchResultDialog();
        lastMatchResult = null;
        displayHudService.clear(server);
        return hadActiveGame;
    }

    public Optional<SemionGame> activeGame() {
        return Optional.ofNullable(activeGame);
    }

    public boolean canBypassPlayerLimit(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        ParticipantSelectionPlan pendingPlan = pendingStartPlan;
        if (pendingPlan != null && isSelectedForPendingMatch(pendingPlan, playerId)) {
            return true;
        }
        SemionGame game = activeGame;
        if (game == null || game.phase() == RoundPhase.ENDED) {
            return false;
        }
        if (game.rosterLocked()) {
            return game.isActiveParticipant(playerId) || game.isMatchSpectator(playerId);
        }
        return game.isReady(playerId);
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

    private static boolean isSelectedForPendingMatch(ParticipantSelectionPlan plan, UUID playerId) {
        return plan.spectatorIds().contains(playerId)
                || plan.activeParticipants().stream().anyMatch(participant -> participant.uuid().equals(playerId));
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
        IllusionCloneSpawnQueue.tick();
        musicService.tick(server, activeGame);
        tickPendingRatingRetry(server);
        tickPendingMatchResultDialog(server);
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
        IllusionCloneSpawnQueue.clear();
        if (activeGame != null) {
            activeGame.close();
            activeGame = null;
        }
        clearStartCountdown();
        pendingFinishedGame = null;
        pendingFinishDelayTicks = 0;
        clearPendingMatchResultDialog();
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
        IllusionCloneSpawnQueue.clear();
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
            matchResultRepository.saveMatchResult(result.get());
            applyRatingOrQueueRetry(server, result.get());
            buildGuideService.finishMatch(finishedGame, result.get().finalRound());
            nextMatchPriorityPlayerIds.addAll(result.get().spectatorIds());
            Map<UUID, MatchProgressionReward> rewards = progressionService.applyMatchResult(server, result.get());
            queueMatchResultDialog(result.get(), rewards);
        } else {
            lastMatchResult = null;
            clearPendingMatchResultDialog();
        }

        try {
            sendAllPlayersToLobby(server);
        } catch (ArenaLoadException exception) {
            SemionTd.LOGGER.warn("Failed to send players to the Semion TD lobby after match end.", exception);
        }

        closeActiveGameSafely(finishedGame, "finishing match");
    }

    private void applyRatingOrQueueRetry(MinecraftServer server, MatchResult matchResult) {
        if (!pendingRatingRetryMatchResults.isEmpty()
                && !pendingRatingRetryMatchResults.containsKey(matchResult.matchId())) {
            pendingRatingRetryMatchResults.put(matchResult.matchId(), matchResult);
            SemionTd.LOGGER.warn(
                    "Queued Semion TD rating for match {} behind {} pending retry match(es) to preserve chronological profile updates.",
                    matchResult.matchId(),
                    pendingRatingRetryMatchResults.size() - 1
            );
            return;
        }
        try {
            ratingService.applyMatchResult(server, matchResult);
            pendingRatingRetryMatchResults.remove(matchResult.matchId());
            if (pendingRatingRetryMatchResults.isEmpty()) {
                pendingRatingRetryDelayTicks = 0;
            }
        } catch (RuntimeException exception) {
            pendingRatingRetryMatchResults.put(matchResult.matchId(), matchResult);
            pendingRatingRetryDelayTicks = RATING_RETRY_DELAY_TICKS;
            SemionTd.LOGGER.warn(
                    "Failed to apply Semion TD rating for match {}. Queued retry while continuing match finish flow.",
                    matchResult.matchId(),
                    exception
            );
        }
    }

    private void tickPendingRatingRetry(MinecraftServer server) {
        if (pendingRatingRetryMatchResults.isEmpty()) {
            return;
        }
        if (pendingRatingRetryDelayTicks > 0) {
            pendingRatingRetryDelayTicks--;
            return;
        }
        MatchResult matchResult = pendingRatingRetryMatchResults.values().iterator().next();
        try {
            ratingService.applyMatchResult(server, matchResult);
            pendingRatingRetryMatchResults.remove(matchResult.matchId());
            pendingRatingRetryDelayTicks = pendingRatingRetryMatchResults.isEmpty() ? 0 : RATING_RETRY_DELAY_TICKS;
            SemionTd.LOGGER.info("Applied queued Semion TD rating retry for match {}.", matchResult.matchId());
        } catch (RuntimeException exception) {
            pendingRatingRetryDelayTicks = RATING_RETRY_DELAY_TICKS;
            SemionTd.LOGGER.warn("Queued Semion TD rating retry failed for match {}; will retry again.", matchResult.matchId(), exception);
        }
    }

    private void queueMatchResultDialog(MatchResult matchResult, Map<UUID, MatchProgressionReward> rewards) {
        pendingMatchResultDialog = matchResult;
        pendingMatchResultRewards = rewards == null ? Map.of() : Map.copyOf(rewards);
        pendingMatchResultDialogDelayTicks = MATCH_RESULT_DIALOG_AFTER_LOBBY_DELAY_TICKS;
    }

    private void tickPendingMatchResultDialog(MinecraftServer server) {
        if (pendingMatchResultDialog == null) {
            return;
        }
        if (pendingMatchResultDialogDelayTicks > 0) {
            pendingMatchResultDialogDelayTicks--;
            return;
        }
        MatchResult matchResult = pendingMatchResultDialog;
        Map<UUID, MatchProgressionReward> rewards = pendingMatchResultRewards;
        clearPendingMatchResultDialog();
        announceMatchResult(server, matchResult, rewards);
        showMatchResultDialogs(server, matchResult, rewards);
    }

    private void clearPendingMatchResultDialog() {
        pendingMatchResultDialog = null;
        pendingMatchResultRewards = Map.of();
        pendingMatchResultDialogDelayTicks = 0;
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
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) {
                player.sendSystemMessage(buildGuideSavePrompt());
            }

            MatchProgressionReward reward = rewards.get(participant.playerId());
            if (reward == null) {
                continue;
            }
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

    private static Component buildGuideSavePrompt() {
        return SemionText.prefixed(Component.empty()
                .append(Component.literal("이번 게임 빌드를 저장하시겠어요? ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("저장하시려면 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/빌드 기록 <제목>").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" 를 입력해서 저장해주세요!").withStyle(ChatFormatting.GRAY)));
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
