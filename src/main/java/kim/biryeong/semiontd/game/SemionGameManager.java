package kim.biryeong.semiontd.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.buildguide.BuildGuideService;
import kim.biryeong.semiontd.config.CombatSpeedConfig;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;
import kim.biryeong.semiontd.config.JobAvailabilityConfig;
import kim.biryeong.semiontd.config.LeaderTargetingConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TipConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WebIntegrationConfig;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.AnimalTowerJob;
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
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import kim.biryeong.semiontd.progression.ProgressionService;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingAdjustment;
import kim.biryeong.semiontd.rating.RatingConfig;
import kim.biryeong.semiontd.rating.RatingMatchResult;
import kim.biryeong.semiontd.rating.RatingService;
import kim.biryeong.semiontd.statistics.JobStatisticsService;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsState;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.engineer.EngineerRedstoneBossBarService;
import kim.biryeong.semiontd.tower.hero.HeroCompanionRole;
import kim.biryeong.semiontd.tower.hero.HeroCompanionSkins;
import kim.biryeong.semiontd.tower.hero.HeroPlayerVisuals;
import kim.biryeong.semiontd.tower.illager.IllagerRaidBossBarService;
import kim.biryeong.semiontd.tower.legion.IllusionCloneSpawnQueue;
import kim.biryeong.semiontd.tower.mage.MageManaBossBarService;
import kim.biryeong.semiontd.tower.queen.QueenBossBarService;
import kim.biryeong.semiontd.tower.villager.VillagerAdvReputationBossBarService;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitRegistry;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import kim.biryeong.semiontd.trait.TraitSelectionSession;
import kim.biryeong.semiontd.trait.TraitSelectionSnapshot;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.tutorial.TutorialService;
import kim.biryeong.semiontd.ui.SemionDialogService;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import kim.biryeong.semiontd.ui.SemionRatingTitleService;
import kim.biryeong.semiontd.ui.SemionSidebarHudService;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.util.Scheduler;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;

public final class SemionGameManager {
    private static final int STARTUP_LOBBY_LOAD_DELAY_TICKS = 20;
    public static final int START_COUNTDOWN_TICKS = 15 * 20;
    public static final int MATCH_RESULT_DELAY_TICKS = 5 * 20;
    public static final int MATCH_RESULT_DIALOG_AFTER_LOBBY_DELAY_TICKS = 2 * 20;
    static final int RATING_RETRY_DELAY_TICKS = 20;
    private static final float NORMAL_TICK_RATE = 20.0F;
    static final int COMBAT_SPEED_ENTRY_DELAY_TICKS = 2 * 20;
    private static final double COMBAT_SPEED_ENTRY_LOAD_RATIO = 0.8;
    private static final int COMBAT_SPEED_COOLDOWN_WAVES = 1;
    private static final DateTimeFormatter RATING_BACKUP_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private EconomyConfig economyConfig = EconomyConfig.defaultConfig();
    private WaveConfig waveConfig = WaveConfig.defaultConfig();
    private MapConfig mapConfig = MapConfig.defaultConfig();
    private ProgressionConfig progressionConfig = ProgressionConfig.defaultConfig();
    private RatingConfig ratingConfig = RatingConfig.defaultConfig();
    private SemionPersistenceConfig persistenceConfig = SemionPersistenceConfig.defaultConfig();
    private JobAvailabilityConfig jobAvailabilityConfig = JobAvailabilityConfig.defaultConfig();
    private TowerBalanceConfig towerBalanceConfig = TowerBalanceConfig.defaultConfig();
    private SummonConfig summonConfig = SummonConfig.defaultConfig();
    private LeaderTargetingConfig leaderTargetingConfig = LeaderTargetingConfig.defaultConfig();
    private IncomeLaneRoutingConfig incomeLaneRoutingConfig = IncomeLaneRoutingConfig.defaultConfig();
    private MonsterScalingConfig monsterScalingConfig = MonsterScalingConfig.defaultConfig();
    private TipConfig tipConfig = TipConfig.defaultConfig();
    private TraitSelectionConfig traitSelectionConfig = TraitSelectionConfig.defaultConfig();
    private WebIntegrationConfig webIntegrationConfig = WebIntegrationConfig.defaultConfig();
    private CombatSpeedConfig combatSpeedConfig = CombatSpeedConfig.defaultConfig();
    private Path configDir;
    private Path progressionStorePath;
    private ProgressionService progressionService = new ProgressionService(progressionConfig, null);
    private RatingService ratingService = new RatingService(null);
    private MatchResultRepository matchResultRepository = new FileMatchResultRepository(null);
    private final JobStatisticsService jobStatisticsService = new JobStatisticsService();
    private SemionMusicService musicService = SemionMusicService.disabled();
    private final SemionDialogService dialogService = new SemionDialogService();
    private final SemionSidebarHudService sidebarHudService = new SemionSidebarHudService();
    private final IllagerRaidBossBarService illagerRaidBossBarService = new IllagerRaidBossBarService();
    private final VillagerAdvReputationBossBarService villagerAdvReputationBossBarService = new VillagerAdvReputationBossBarService();
    private final EngineerRedstoneBossBarService engineerRedstoneBossBarService = new EngineerRedstoneBossBarService();
    private final MageManaBossBarService mageManaBossBarService = new MageManaBossBarService();
    private final QueenBossBarService queenBossBarService = new QueenBossBarService();
    private final BuildGuideService buildGuideService = new BuildGuideService(null);
    private final TutorialService tutorialService = new TutorialService();
    private MatchMode matchMode = MatchMode.NORMAL;
    private SemionGame activeGame;
    private LobbyWorld lobbyWorld;
    private MatchResult lastMatchResult;
    private final Set<UUID> nextMatchPriorityPlayerIds = new HashSet<>();
    private final Map<UUID, String> playerLimitBypassNames = new ConcurrentHashMap<>();
    private final Map<UUID, SemionGame> sandboxGames = new ConcurrentHashMap<>();
    private final Map<UUID, SemionGame> tutorialGames = new ConcurrentHashMap<>();
    private final RatingProfileCache ratingProfileCache = new RatingProfileCache();
    private SemionGame pendingFinishedGame;
    private int pendingFinishDelayTicks;
    private MatchResult pendingMatchResultDialog;
    private final Map<MatchId, MatchResult> pendingRatingRetryMatchResults = new LinkedHashMap<>();
    private int pendingRatingRetryDelayTicks;
    private Map<UUID, MatchProgressionReward> pendingMatchResultRewards = Map.of();
    private int pendingMatchResultDialogDelayTicks;
    private ParticipantSelectionPlan pendingStartPlan;
    private TraitSelectionSnapshot pendingStartTraitSnapshot = TraitSelectionSnapshot.empty();
    private TraitSelectionSession pendingTraitSelection;
    private final Map<TeamId, UUID> midLanePreferences = new EnumMap<>(TeamId.class);
    private int startCountdownTicks;
    private int nextStartCountdownAnnouncementSecond;
    private boolean startupLobbyLoadPending;
    private int startupLobbyLoadDelayTicks;
    private RoundPhase previousCombatSpeedPhase = RoundPhase.WAITING;
    private boolean combatSpeedOverloaded;
    private boolean combatSpeedAccelerated;
    private boolean managesTickRate;
    private int combatSpeedEntryDelayTicks;
    private int combatSpeedCooldownWaves;

    public enum StartCountdownResult {
        SCHEDULED,
        NO_ACTIVE_GAME,
        NOT_WAITING,
        ALREADY_PENDING,
        PRELOAD_FAILED
    }

    public enum MidLanePreferenceResult {
        REQUESTED,
        ALREADY_REQUESTED,
        TEAM_ALREADY_REQUESTED,
        NO_PENDING_START,
        NOT_PARTICIPANT,
        NO_MID_LANE
    }

    public enum SandboxStartResult {
        STARTED,
        REPLACED,
        PLAYER_IN_MATCH,
        FAILED
    }

    public enum TutorialStartResult {
        STARTED,
        REPLACED,
        PLAYER_IN_MATCH,
        FAILED
    }

    public enum SandboxSpectateResult {
        SUCCESS,
        TARGET_NOT_IN_SANDBOX,
        CANNOT_SPECTATE_SELF,
        PLAYER_IN_MATCH,
        FAILED
    }

    public enum SandboxCurrency {
        DIAMOND,
        EMERALD,
        INCOME
    }

    public record ReloadConfigResult(boolean reloaded, boolean activeGameUpdated, Path configDir) {
    }

    public record RatingSoftResetResult(Path backupPath) {
    }

    public void configureWebIntegration(WebIntegrationConfig webIntegrationConfig) {
        this.webIntegrationConfig = webIntegrationConfig == null
                ? WebIntegrationConfig.defaultConfig()
                : webIntegrationConfig;
    }

    public void configureCombatSpeed(CombatSpeedConfig combatSpeedConfig) {
        this.combatSpeedConfig = combatSpeedConfig == null
                ? CombatSpeedConfig.defaultConfig()
                : combatSpeedConfig;
        resetCombatSpeedState();
    }

    public void configureJobAvailability(JobAvailabilityConfig config) {
        this.jobAvailabilityConfig = config == null ? JobAvailabilityConfig.defaultConfig() : config;
        JobRegistry.configureAvailability(this.jobAvailabilityConfig);
    }

    public boolean setJobEnabled(ResourceLocation jobId, boolean enabled) {
        if (configDir == null
                || jobId == null
                || JobRegistry.find(jobId).isEmpty()
                || (!enabled && JobRegistry.defaultJob().id().equals(jobId))) {
            return false;
        }
        JobAvailabilityConfig updated = jobAvailabilityConfig.withEnabled(jobId, enabled);
        if (updated.equals(jobAvailabilityConfig)) {
            return true;
        }
        if (!SemionConfigLoader.saveJobAvailability(configDir, updated, SemionTd.LOGGER)) {
            return false;
        }
        configureJobAvailability(updated);
        return true;
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
                RatingConfig.defaultConfig(),
                TowerBalanceConfig.defaultConfig(),
                SummonConfig.defaultConfig(),
                SemionPersistenceConfig.defaultConfig(),
                LeaderTargetingConfig.defaultConfig(),
                IncomeLaneRoutingConfig.defaultConfig(),
                MonsterScalingConfig.defaultConfig(),
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
                RatingConfig.defaultConfig(),
                towerBalanceConfig,
                SummonConfig.defaultConfig(),
                SemionPersistenceConfig.defaultConfig(),
                LeaderTargetingConfig.defaultConfig(),
                IncomeLaneRoutingConfig.defaultConfig(),
                MonsterScalingConfig.defaultConfig(),
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
                RatingConfig.defaultConfig(),
                towerBalanceConfig,
                summonConfig,
                SemionPersistenceConfig.defaultConfig(),
                LeaderTargetingConfig.defaultConfig(),
                IncomeLaneRoutingConfig.defaultConfig(),
                MonsterScalingConfig.defaultConfig(),
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
        configure(
                economyConfig,
                waveConfig,
                mapConfig,
                progressionConfig,
                RatingConfig.defaultConfig(),
                towerBalanceConfig,
                summonConfig,
                persistenceConfig,
                LeaderTargetingConfig.defaultConfig(),
                IncomeLaneRoutingConfig.defaultConfig(),
                MonsterScalingConfig.defaultConfig(),
                progressionStorePath
        );
    }

    public void configure(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            MapConfig mapConfig,
            ProgressionConfig progressionConfig,
            RatingConfig ratingConfig,
            TowerBalanceConfig towerBalanceConfig,
            SummonConfig summonConfig,
            SemionPersistenceConfig persistenceConfig,
            Path progressionStorePath
    ) {
        configure(
                economyConfig,
                waveConfig,
                mapConfig,
                progressionConfig,
                ratingConfig,
                towerBalanceConfig,
                summonConfig,
                persistenceConfig,
                LeaderTargetingConfig.defaultConfig(),
                IncomeLaneRoutingConfig.defaultConfig(),
                MonsterScalingConfig.defaultConfig(),
                progressionStorePath
        );
    }

    public void configure(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            MapConfig mapConfig,
            ProgressionConfig progressionConfig,
            RatingConfig ratingConfig,
            TowerBalanceConfig towerBalanceConfig,
            SummonConfig summonConfig,
            SemionPersistenceConfig persistenceConfig,
            LeaderTargetingConfig leaderTargetingConfig,
            IncomeLaneRoutingConfig incomeLaneRoutingConfig,
            MonsterScalingConfig monsterScalingConfig,
            Path progressionStorePath
    ) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.mapConfig = mapConfig;
        this.progressionConfig = progressionConfig;
        this.ratingConfig = ratingConfig == null ? RatingConfig.defaultConfig() : ratingConfig;
        this.persistenceConfig = persistenceConfig == null ? SemionPersistenceConfig.defaultConfig() : persistenceConfig;
        this.towerBalanceConfig = towerBalanceConfig == null ? TowerBalanceConfig.defaultConfig() : towerBalanceConfig;
        this.summonConfig = summonConfig == null ? SummonConfig.defaultConfig() : summonConfig;
        this.leaderTargetingConfig = leaderTargetingConfig == null ? LeaderTargetingConfig.defaultConfig() : leaderTargetingConfig;
        this.incomeLaneRoutingConfig = incomeLaneRoutingConfig == null ? IncomeLaneRoutingConfig.defaultConfig() : incomeLaneRoutingConfig;
        this.monsterScalingConfig = monsterScalingConfig == null ? MonsterScalingConfig.defaultConfig() : monsterScalingConfig;
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
                createAppliedMatchRepository(this.persistenceConfig, sqlitePath, ratingAppliedMatchesPath, this.configDir),
                this.ratingConfig
        );
        clearRatingProfileCache();
        this.matchResultRepository = createMatchResultRepository(this.persistenceConfig, sqlitePath, matchResultPath, this.configDir);
        this.jobStatisticsService.configure(
                sqlitePath,
                matchResultPath,
                this.configDir == null ? null : this.configDir.resolve("job-statistics.db")
        );
        this.buildGuideService.configure(this.configDir == null ? null : this.configDir.resolve("build_guides.json"));
        ProductionTowerCatalogs.reloadBuiltIns(this.towerBalanceConfig);
        IncomeSummons.reloadBuiltIns(this.summonConfig);
        if (webIntegrationConfig.enabled()) {
            try {
                WebCatalogExporter.export(this.configDir);
            } catch (IOException | RuntimeException exception) {
                WebCatalogExporter.clearCurrentVersion();
                SemionTd.LOGGER.warn("Failed to export the Semion TD web catalog.", exception);
            }
        } else {
            WebCatalogExporter.clearCurrentVersion();
        }
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

        LoadedConfigs configs = SemionConfigLoader.load(
                configDir,
                SemionTd.LOGGER,
                TowerBalanceRuntime.current(),
                jobAvailabilityConfig
        );
        configureJobAvailability(configs.jobAvailability());
        TraitBalanceRuntime.apply(configs.traitBalance());
        TowerVfxService.configure(configs.vfx());
        configureTips(configs.tips());
        configureTraits(configs.traits());
        configureWebIntegration(configs.webIntegration());
        configureCombatSpeed(configs.combatSpeed());
        configure(
                configs.economy(),
                configs.waves(),
                configs.map(),
                configs.progression(),
                configs.rating(),
                configs.towerBalance(),
                configs.summons(),
                configs.persistence(),
                configs.leaderTargeting(),
                configs.incomeLaneRouting(),
                configs.monsterScaling(),
                configDir.resolve("profiles.json")
        );
        boolean activeGameUpdated = activeGame != null && activeGame.phase() != RoundPhase.ENDED;
        if (activeGameUpdated) {
            activeGame.applyConfigs(configs.economy(), configs.waves(), configs.leaderTargeting(), configs.incomeLaneRouting(), configs.monsterScaling());
            activeGame.refreshProductionTowerTypes();
            activeGame.refreshSummonShop();
            if (activeGame.rosterLocked()) {
                cacheRatingProfilesForGame(activeGame);
            }
            sidebarHudService.refreshNow(server, activeGame, matchMode);
        }
        return new ReloadConfigResult(true, activeGameUpdated, configDir);
    }

    public RatingSoftResetResult softResetRatingsWithBackup() {
        if (configDir == null) {
            throw new PersistenceException("Semion TD config directory is not configured.");
        }

        RatingSoftResetResult result = softResetRatingStore(configDir, persistenceConfig);
        Path ratingProfilePath = configDir.resolve("ratings.json");
        Path ratingEventPath = configDir.resolve("rating-events.json");
        Path sqlitePath = resolveSqlitePath(configDir, persistenceConfig);

        Path ratingAppliedMatchesPath = progressionStorePath == null
                ? null
                : progressionStorePath.resolveSibling("rating-applied-matches.json");
        this.ratingService = new RatingService(
                createRatingRepository(this.persistenceConfig, sqlitePath, ratingProfilePath),
                createRatingEventRepository(this.persistenceConfig, sqlitePath, ratingEventPath),
                createAppliedMatchRepository(this.persistenceConfig, sqlitePath, ratingAppliedMatchesPath, this.configDir),
                this.ratingConfig
        );
        clearRatingProfileCache();
        pendingRatingRetryMatchResults.clear();
        pendingRatingRetryDelayTicks = 0;
        return result;
    }

    static RatingSoftResetResult softResetRatingStore(Path configDir, SemionPersistenceConfig persistenceConfig) {
        if (configDir == null) {
            throw new PersistenceException("Semion TD config directory is not configured.");
        }

        Path ratingProfilePath = configDir.resolve("ratings.json");
        Path ratingEventPath = configDir.resolve("rating-events.json");
        SemionPersistenceConfig safePersistenceConfig = persistenceConfig == null
                ? SemionPersistenceConfig.defaultConfig()
                : persistenceConfig;
        Path sqlitePath = resolveSqlitePath(configDir, safePersistenceConfig);
        Path backupPath = configDir.resolve("rating-backups")
                .resolve("elo-softreset-" + RATING_BACKUP_TIMESTAMP_FORMATTER.format(Instant.now()));

        try {
            Files.createDirectories(backupPath);
            backupFileIfExists(ratingProfilePath, backupPath.resolve("ratings.json"));
            backupFileIfExists(ratingEventPath, backupPath.resolve("rating-events.json"));
            if (sqlitePath != null) {
                backupSqliteRatings(sqlitePath, backupPath.resolve(sqlitePath.getFileName()));
            }
            Files.deleteIfExists(ratingProfilePath);
            Files.deleteIfExists(ratingEventPath);
            if (sqlitePath != null) {
                clearSqliteRatings(sqlitePath);
            }
        } catch (IOException | SQLException exception) {
            throw new PersistenceException("Failed to soft reset ratings with backup " + backupPath, exception);
        }
        return new RatingSoftResetResult(backupPath);
    }

    private static void backupFileIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.copy(source, target);
        }
    }

    private static void backupSqliteRatings(Path sqlitePath, Path backupPath) throws IOException, SQLException {
        checkpointSqlite(sqlitePath);
        Files.copy(sqlitePath, backupPath);
    }

    private static void checkpointSqlite(Path sqlitePath) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
    }

    private static void clearSqliteRatings(Path sqlitePath) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            connection.setAutoCommit(false);
            try {
                statement.executeUpdate("DELETE FROM rating_profiles");
                statement.executeUpdate("DELETE FROM rating_events");
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void configureMusic(SemionMusicService musicService) {
        this.musicService = musicService == null ? SemionMusicService.disabled() : musicService;
    }

    public void configureTips(TipConfig tipConfig) {
        this.tipConfig = tipConfig == null ? TipConfig.defaultConfig() : tipConfig;
    }

    public void configureTraits(TraitSelectionConfig traitSelectionConfig) {
        this.traitSelectionConfig = traitSelectionConfig == null
                ? TraitSelectionConfig.defaultConfig()
                : traitSelectionConfig;
    }

    public TipConfig tipConfig() {
        return tipConfig;
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
        SemionPlayerProfile profile = progressionService.profile(server, playerId, playerName);
        HeroCompanionSkins.load(playerId, profile.heroCompanionSkins());
        return profile;
    }

    public Optional<SemionPlayerProfile> grantCosmeticCurrency(UUID playerId, String playerName, long amount) {
        return progressionService.grantCosmeticCurrency(playerId, playerName, amount);
    }

    public Optional<PlayerRatingProfile> ratingProfile(UUID playerId) {
        return ratingProfileCache.profile(playerId, ratingService::profile);
    }

    void cacheRatingProfilesForMatch(ParticipantSelectionPlan plan) {
        ratingProfileCache.capture(plan, ratingService::profile);
    }

    void cacheRatingProfilesForGame(SemionGame game) {
        if (game == null) {
            ratingProfileCache.clear();
            return;
        }
        Set<UUID> playerIds = new HashSet<>(game.players().keySet());
        playerIds.addAll(game.matchSpectatorIds());
        ratingProfileCache.captureIds(playerIds, ratingService::profile);
    }

    void clearRatingProfileCache() {
        ratingProfileCache.clear();
    }

    public List<PlayerRatingProfile> topRatingProfiles(int limit) {
        return ratingService.topProfiles(limit);
    }

    public List<PlayerRatingProfile> topRatingProfiles() {
        return ratingService.topProfiles();
    }

    public SemionPlayerProfile saveSelectedJob(MinecraftServer server, UUID playerId, String playerName, ResourceLocation jobId) {
        return progressionService.saveSelectedJob(server, playerId, playerName, jobId);
    }

    public SemionPlayerProfile saveSelectedSkybox(MinecraftServer server, UUID playerId, String playerName, String skyboxId) {
        return progressionService.saveSelectedSkybox(server, playerId, playerName, skyboxId);
    }

    public SemionPlayerProfile saveTipsEnabled(MinecraftServer server, UUID playerId, String playerName, boolean enabled) {
        return progressionService.saveTipsEnabled(server, playerId, playerName, enabled);
    }

    public ProgressionService.CosmeticUpdateResult purchaseCosmetic(UUID playerId, String playerName, String cosmeticId, long price) {
        return progressionService.purchaseCosmetic(playerId, playerName, cosmeticId, price);
    }

    public ProgressionService.CosmeticUpdateResult selectCosmetics(UUID playerId, String playerName, List<String> cosmeticIds) {
        return progressionService.selectCosmetics(playerId, playerName, cosmeticIds);
    }

    public boolean clearSelectedCosmetic(String cosmeticId) {
        return progressionService.clearSelectedCosmetic(cosmeticId);
    }

    public boolean saveHeroCompanionSkin(
            MinecraftServer server,
            UUID playerId,
            String playerName,
            HeroCompanionRole role,
            HeroCompanionSkinPreference skin
    ) {
        if (role == null || !progressionService.saveHeroCompanionSkin(playerId, playerName, role.id(), skin)) {
            return false;
        }
        HeroCompanionSkins.set(playerId, role, skin);
        HeroPlayerVisuals.refreshSkin(playerId, role);
        return true;
    }

    public Optional<MatchResult> lastMatchResult() {
        return Optional.ofNullable(lastMatchResult);
    }

    public Set<UUID> nextMatchPriorityPlayerIds() {
        return Set.copyOf(nextMatchPriorityPlayerIds);
    }

    public boolean addPlayerLimitBypass(UUID playerId, String playerName) {
        if (playerId == null) {
            return false;
        }
        String displayName = playerName == null || playerName.isBlank() ? playerId.toString() : playerName;
        return playerLimitBypassNames.put(playerId, displayName) == null;
    }

    public boolean removePlayerLimitBypass(UUID playerId) {
        return playerId != null && playerLimitBypassNames.remove(playerId) != null;
    }

    public Map<UUID, String> playerLimitBypasses() {
        return Map.copyOf(playerLimitBypassNames);
    }

    public SemionDialogService dialogService() {
        return dialogService;
    }

    public JobStatisticsSnapshot jobStatisticsSnapshot() {
        return jobStatisticsService.snapshot();
    }

    public JobStatisticsState jobStatisticsState() {
        return jobStatisticsService.state();
    }

    public SemionSidebarHudService sidebarHudService() {
        return sidebarHudService;
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
            finalizeBuildGuideRecording(activeGame, activeGame.matchResult());
            sidebarHudService.clear(server);
            illagerRaidBossBarService.clear(server);
            villagerAdvReputationBossBarService.clear(server);
            engineerRedstoneBossBarService.clear(server);
            mageManaBossBarService.clear(server);
            queenBossBarService.clear(server);
            sendAllPlayersToLobby(server);
            closeActiveGameSafely(activeGame, "replacing active game during create");
        }
        clearRatingProfileCache();
        pendingFinishedGame = null;
        pendingFinishDelayTicks = 0;
        clearPendingMatchResultDialog();

        GameArena arena = GameArenaLoader.load(server, mapConfig);
        resetCombatSpeedState();
        activeGame = new SemionGame(economyConfig, waveConfig, leaderTargetingConfig, incomeLaneRoutingConfig, monsterScalingConfig, arena, buildGuideService);
        applyPersistedJobSelections(server, activeGame);
        lastMatchResult = null;
        VanillaTeamBridge.ensureTeams(server);
        sendAllPlayersToLobby(server);
        sidebarHudService.refreshNow(server, activeGame, matchMode);
        return activeGame;
    }

    public boolean resetToLobby(MinecraftServer server) throws ArenaLoadException {
        ensureLobby(server);
        boolean hadActiveGame = activeGame != null;
        sidebarHudService.clear(server);
        illagerRaidBossBarService.clear(server);
        villagerAdvReputationBossBarService.clear(server);
        engineerRedstoneBossBarService.clear(server);
        mageManaBossBarService.clear(server);
        queenBossBarService.clear(server);
        sendAllPlayersToLobby(server);
        if (activeGame != null) {
            finalizeBuildGuideRecording(activeGame, activeGame.matchResult());
            closeActiveGameSafely(activeGame, "resetting match to lobby");
        }
        clearRatingProfileCache();
        pendingFinishedGame = null;
        pendingFinishDelayTicks = 0;
        clearPendingMatchResultDialog();
        lastMatchResult = null;
        sidebarHudService.clear(server);
        illagerRaidBossBarService.clear(server);
        villagerAdvReputationBossBarService.clear(server);
        engineerRedstoneBossBarService.clear(server);
        mageManaBossBarService.clear(server);
        queenBossBarService.clear(server);
        return hadActiveGame;
    }

    public Optional<SemionGame> activeGame() {
        return Optional.ofNullable(activeGame);
    }

    public Optional<SemionGame> sandboxGame(UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : sandboxGames.get(playerId));
    }

    public Optional<SemionGame> tutorialGame(UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : tutorialGames.get(playerId));
    }

    public Optional<SemionGame> sandboxSpectatorGame(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return sandboxGames.values().stream()
                .filter(game -> game.isMatchSpectator(playerId))
                .findFirst();
    }

    public boolean hasIllagerRaidBossBar(UUID playerId) {
        return illagerRaidBossBarService.hasPlayer(playerId);
    }

    public Optional<SemionGame> playableGame(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        if (activeGame != null && activeGame.isActiveParticipant(playerId)) {
            return Optional.of(activeGame);
        }
        SemionGame tutorial = tutorialGames.get(playerId);
        if (tutorial != null) {
            return Optional.of(tutorial);
        }
        return sandboxGame(playerId);
    }

    public SemionGame protectionGame(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        if (activeGame != null && (activeGame.isActiveParticipant(playerId) || activeGame.isMatchSpectator(playerId))) {
            return activeGame;
        }
        SemionGame tutorial = tutorialGames.get(playerId);
        if (tutorial != null) {
            return tutorial;
        }
        SemionGame ownedSandbox = sandboxGames.get(playerId);
        return ownedSandbox != null ? ownedSandbox : sandboxSpectatorGame(playerId).orElse(null);
    }

    public SandboxStartResult startSandbox(MinecraftServer server, ServerPlayer player) {
        if (player == null) {
            return SandboxStartResult.FAILED;
        }
        if (isBlockedFromPractice(player.getUUID())) {
            return SandboxStartResult.PLAYER_IN_MATCH;
        }
        try {
            return startSandbox(server, player.getUUID(), player.getGameProfile().getName(), GameArenaLoader.load(server, mapConfig));
        } catch (ArenaLoadException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to start Semion TD sandbox for {}.", player.getGameProfile().getName(), exception);
            return SandboxStartResult.FAILED;
        }
    }

    public SandboxStartResult startSandbox(MinecraftServer server, UUID playerId, String playerName, GameArena arena) {
        return startPracticeGame(server, playerId, playerName, arena, false);
    }

    public TutorialStartResult startTutorial(MinecraftServer server, ServerPlayer player) {
        if (player == null) {
            return TutorialStartResult.FAILED;
        }
        if (isBlockedFromPractice(player.getUUID())) {
            return TutorialStartResult.PLAYER_IN_MATCH;
        }
        try {
            return startTutorial(server, player.getUUID(), player.getGameProfile().getName(), GameArenaLoader.load(server, mapConfig));
        } catch (ArenaLoadException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to start Semion TD tutorial for {}.", player.getGameProfile().getName(), exception);
            return TutorialStartResult.FAILED;
        }
    }

    public TutorialStartResult startTutorial(MinecraftServer server, UUID playerId, String playerName, GameArena arena) {
        return tutorialResult(startPracticeGame(server, playerId, playerName, arena, true));
    }

    private SandboxStartResult startPracticeGame(
            MinecraftServer server,
            UUID playerId,
            String playerName,
            GameArena arena,
            boolean tutorial
    ) {
        if (server == null || playerId == null || arena == null) {
            return SandboxStartResult.FAILED;
        }
        if (isBlockedFromPractice(playerId)) {
            arena.unload();
            return SandboxStartResult.PLAYER_IN_MATCH;
        }

        SemionGame previousSandbox = sandboxGames.get(playerId);
        TraitLoadout previousTraitLoadout = TraitLoadout.none();
        if (!tutorial && traitsEnabled()) {
            previousTraitLoadout = previousSandbox != null
                    ? previousSandbox.selectedTraitLoadoutOrDefault(playerId)
                    : activeGame != null && activeGame.canConfigureRoster()
                            ? activeGame.selectedTraitLoadoutOrDefault(playerId)
                            : TraitLoadout.none();
        }
        releaseActiveMatchSpectator(playerId);
        releaseSandboxSpectator(playerId);
        boolean replacing = stopSandbox(server, playerId) | stopTutorial(server, playerId);
        SemionGame sandbox = new SemionGame(
                economyConfig,
                waveConfig,
                leaderTargetingConfig,
                incomeLaneRoutingConfig,
                monsterScalingConfig,
                arena,
                tutorial ? new BuildGuideService(null) : null
        );
        if (tutorial) {
            sandbox.enableTutorialMode();
        } else {
            sandbox.enableSandboxMode();
        }
        sandbox.disableWaveSpawnsForTeam(TeamId.BLUE);
        sandbox.selectTrait(playerId, TraitSlot.PRIMARY, previousTraitLoadout.primaryTraitId());
        sandbox.selectTrait(playerId, TraitSlot.SECONDARY, previousTraitLoadout.secondaryTraitId());
        if (tutorial) {
            if (!sandbox.selectJob(playerId, AnimalTowerJob.ID)) {
                applyFallbackSandboxJob(sandbox, playerId);
            }
        } else if (!applyPersistedJobSelection(server, sandbox, playerId, playerName)) {
            applyFallbackSandboxJob(sandbox, playerId);
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.TEST,
                List.of(
                        new AssignedParticipant(
                                playerId,
                                playerName == null || playerName.isBlank() ? tutorial ? "tutorial" : "sandbox" : playerName,
                                TeamId.RED,
                                1
                        ),
                        new AssignedParticipant(
                                practiceDummyPlayerId(playerId, tutorial),
                                tutorial ? "Tutorial Dummy" : "Sandbox Dummy",
                                TeamId.BLUE,
                                1
                        )
                ),
                Set.of(),
                2
        );
        TraitSelectionSnapshot traitSnapshot = new TraitSelectionSnapshot(Map.of(
                playerId,
                sandbox.selectedTraitLoadoutOrDefault(playerId)
        ));
        if (!sandbox.start(server, plan, traitSnapshot)) {
            sandbox.close();
            return SandboxStartResult.FAILED;
        }
        if (tutorial) {
            tutorialGames.put(playerId, sandbox);
            tutorialService.start(server, playerId, sandbox);
        } else {
            sandboxGames.put(playerId, sandbox);
        }
        return replacing ? SandboxStartResult.REPLACED : SandboxStartResult.STARTED;
    }

    private static TutorialStartResult tutorialResult(SandboxStartResult result) {
        return TutorialStartResult.valueOf(result.name());
    }

    public boolean isTutorialActive(UUID playerId) {
        return tutorialService.isActive(playerId);
    }

    public Optional<TutorialService.Stage> tutorialStage(UUID playerId) {
        return tutorialService.stage(playerId);
    }

    public boolean showTutorial(MinecraftServer server, UUID playerId) {
        SemionGame game = tutorialGames.get(playerId);
        return game != null && tutorialService.showCurrent(server, playerId, game);
    }

    public boolean completeTutorial(MinecraftServer server, UUID playerId) {
        SemionGame game = tutorialGames.get(playerId);
        return game != null && tutorialService.complete(server, playerId, game);
    }

    public boolean leaveTutorial(MinecraftServer server, ServerPlayer player) {
        if (player == null || !tutorialService.isActive(player.getUUID())) {
            return false;
        }
        returnPlayerToLobby(server, player);
        return stopTutorial(server, player.getUUID());
    }

    public boolean grantSandboxCurrency(UUID playerId, SandboxCurrency currency, long amount) {
        if (playerId == null || currency == null || amount <= 0) {
            return false;
        }
        SemionGame sandbox = sandboxGames.get(playerId);
        if (sandbox == null) {
            return false;
        }
        SemionPlayer player = sandbox.players().get(playerId);
        if (player == null) {
            return false;
        }
        PlayerEconomy economy = player.economy();
        switch (currency) {
            case DIAMOND -> economy.addDiamond(amount);
            case EMERALD -> economy.addEmerald(amount);
            case INCOME -> economy.addIncome(amount);
        }
        return true;
    }

    public boolean resetSandbox(MinecraftServer server, ServerPlayer player) {
        return startSandbox(server, player) != SandboxStartResult.FAILED;
    }

    public boolean moveSandboxToRound(MinecraftServer server, UUID playerId, int round) {
        SemionGame sandbox = playerId == null ? null : sandboxGames.get(playerId);
        return sandbox != null && sandbox.moveSandboxToRound(server, round);
    }

    public SandboxSpectateResult spectateSandbox(
            MinecraftServer server,
            ServerPlayer spectator,
            UUID ownerId
    ) {
        if (server == null || spectator == null || ownerId == null) {
            return SandboxSpectateResult.FAILED;
        }
        UUID spectatorId = spectator.getUUID();
        if (spectatorId.equals(ownerId)) {
            return SandboxSpectateResult.CANNOT_SPECTATE_SELF;
        }
        SemionGame targetSandbox = sandboxGames.get(ownerId);
        if (targetSandbox == null || !targetSandbox.canSpectateTeam(TeamId.RED)) {
            return SandboxSpectateResult.TARGET_NOT_IN_SANDBOX;
        }
        if (isBlockedFromPractice(spectatorId)) {
            return SandboxSpectateResult.PLAYER_IN_MATCH;
        }

        releaseActiveMatchSpectator(spectatorId);
        releaseSandboxSpectator(spectatorId);
        stopSandbox(server, spectatorId);
        if (!targetSandbox.addLateSpectator(server, spectator, TeamId.RED)) {
            returnPlayerToLobby(server, spectator);
            return SandboxSpectateResult.FAILED;
        }
        return SandboxSpectateResult.SUCCESS;
    }

    public boolean leaveSandbox(MinecraftServer server, ServerPlayer player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        boolean ownedSandbox = sandboxGames.containsKey(playerId);
        boolean spectatingSandbox = releaseSandboxSpectator(playerId);
        if (!ownedSandbox && !spectatingSandbox) {
            return false;
        }
        returnPlayerToLobby(server, player);
        if (ownedSandbox) {
            stopSandbox(server, playerId);
        }
        return true;
    }

    public boolean stopSandbox(UUID playerId) {
        return stopSandbox(null, playerId);
    }

    public boolean stopSandbox(MinecraftServer server, UUID playerId) {
        SemionGame existing = playerId == null ? null : sandboxGames.remove(playerId);
        if (existing == null) {
            return false;
        }
        for (UUID spectatorId : Set.copyOf(existing.matchSpectatorIds())) {
            existing.removeMatchSpectator(spectatorId);
            illagerRaidBossBarService.removePlayer(spectatorId);
            villagerAdvReputationBossBarService.removePlayer(spectatorId);
            if (server != null) {
                ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorId);
                if (spectator != null) {
                    returnPlayerToLobby(server, spectator);
                    spectator.sendSystemMessage(SemionText.prefixedPlain("관전하던 샌드박스가 종료되어 로비로 이동했습니다."));
                }
            }
        }
        illagerRaidBossBarService.removePlayer(playerId);
        villagerAdvReputationBossBarService.removePlayer(playerId);
        engineerRedstoneBossBarService.removePlayer(playerId);
        mageManaBossBarService.removePlayer(playerId);
        queenBossBarService.removePlayer(playerId);
        try {
            existing.close();
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to close Semion TD sandbox for player {}.", playerId, exception);
        }
        return true;
    }

    public boolean stopTutorial(MinecraftServer server, UUID playerId) {
        SemionGame existing = playerId == null ? null : tutorialGames.remove(playerId);
        if (existing == null) {
            return false;
        }
        tutorialService.stop(playerId);
        illagerRaidBossBarService.removePlayer(playerId);
        villagerAdvReputationBossBarService.removePlayer(playerId);
        engineerRedstoneBossBarService.removePlayer(playerId);
        mageManaBossBarService.removePlayer(playerId);
        queenBossBarService.removePlayer(playerId);
        try {
            existing.close();
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to close Semion TD tutorial for player {}.", playerId, exception);
        }
        return true;
    }

    private void returnPlayerToLobby(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        sidebarHudService.remove(player);
        try {
            sendPlayerToLobby(server, player);
        } catch (ArenaLoadException exception) {
            SemionTd.LOGGER.warn("Failed to return practice player {} to lobby.", player.getGameProfile().getName(), exception);
        }
    }

    private boolean isBlockedFromPractice(UUID playerId) {
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
            return game.isActiveParticipant(playerId);
        }
        return game.isReady(playerId);
    }

    private void releaseActiveMatchSpectator(UUID playerId) {
        SemionGame game = activeGame;
        if (game != null) {
            game.removeMatchSpectator(playerId);
        }
    }

    private boolean releaseSandboxSpectator(UUID playerId) {
        SemionGame sandbox = sandboxSpectatorGame(playerId).orElse(null);
        return sandbox != null && sandbox.removeMatchSpectator(playerId);
    }

    private static UUID practiceDummyPlayerId(UUID ownerId, boolean tutorial) {
        String kind = tutorial ? "tutorial" : "sandbox";
        return UUID.nameUUIDFromBytes(("semion-td-" + kind + "-dummy:" + ownerId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void closePracticeGamesFor(MinecraftServer server, ParticipantSelectionPlan plan) {
        if (plan == null) {
            return;
        }
        plan.spectatorIds().forEach(playerId -> closeSandboxForPlayer(server, playerId));
        plan.activeParticipants().stream()
                .map(AssignedParticipant::uuid)
                .forEach(playerId -> closeSandboxForPlayer(server, playerId));
    }

    private void closeSandboxForPlayer(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null && leaveSandbox(server, player)) {
            stopTutorial(server, playerId);
            return;
        }
        releaseSandboxSpectator(playerId);
        stopSandbox(server, playerId);
        stopTutorial(server, playerId);
    }

    private void tickSandboxGames(MinecraftServer server) {
        for (Map.Entry<UUID, SemionGame> entry : sandboxGames.entrySet()) {
            SemionGame sandbox = entry.getValue();
            sandbox.tick(server);
            Set<UUID> viewers = new HashSet<>(sandbox.matchSpectatorIds());
            viewers.add(entry.getKey());
            sidebarHudService.refreshPlayersNow(server, sandbox, MatchMode.TEST, viewers);
            illagerRaidBossBarService.refreshPlayersNow(server, sandbox, Set.of(entry.getKey()));
            villagerAdvReputationBossBarService.refreshPlayersNow(server, sandbox, Set.of(entry.getKey()));
            engineerRedstoneBossBarService.refreshPlayersNow(server, sandbox, Set.of(entry.getKey()));
            mageManaBossBarService.refreshPlayersNow(server, sandbox, Set.of(entry.getKey()));
            queenBossBarService.refreshPlayersNow(server, sandbox, Set.of(entry.getKey()));
        }
    }

    private void tickTutorialGames(MinecraftServer server) {
        for (Map.Entry<UUID, SemionGame> entry : tutorialGames.entrySet()) {
            SemionGame tutorial = entry.getValue();
            tutorial.tick(server);
            tutorialService.tick(server, entry.getKey(), tutorial);
            sidebarHudService.refreshPlayersNow(server, tutorial, MatchMode.TEST, Set.of(entry.getKey()));
            illagerRaidBossBarService.refreshPlayersNow(server, tutorial, Set.of(entry.getKey()));
            villagerAdvReputationBossBarService.refreshPlayersNow(server, tutorial, Set.of(entry.getKey()));
            engineerRedstoneBossBarService.refreshPlayersNow(server, tutorial, Set.of(entry.getKey()));
            mageManaBossBarService.refreshPlayersNow(server, tutorial, Set.of(entry.getKey()));
            queenBossBarService.refreshPlayersNow(server, tutorial, Set.of(entry.getKey()));
        }
    }

    private Set<UUID> practiceViewerIds() {
        Set<UUID> viewerIds = new HashSet<>(sandboxGames.keySet());
        viewerIds.addAll(tutorialGames.keySet());
        for (SemionGame sandbox : sandboxGames.values()) {
            viewerIds.addAll(sandbox.matchSpectatorIds());
        }
        return viewerIds;
    }

    private void closeAllPracticeGames() {
        for (UUID playerId : List.copyOf(sandboxGames.keySet())) {
            stopSandbox(playerId);
        }
        for (UUID playerId : List.copyOf(tutorialGames.keySet())) {
            stopTutorial(null, playerId);
        }
    }

    public boolean canBypassPlayerLimit(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (playerLimitBypassNames.containsKey(playerId)) {
            return true;
        }
        ParticipantSelectionPlan pendingPlan = pendingStartPlan;
        if (pendingPlan != null && isSelectedForPendingMatch(pendingPlan, playerId)) {
            return true;
        }
        TraitSelectionSession pendingSelection = pendingTraitSelection;
        if (pendingSelection != null && isSelectedForPendingMatch(pendingSelection.plan(), playerId)) {
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
        return pendingStartPlan != null || pendingTraitSelection != null;
    }

    public boolean traitSelectionActive() {
        return pendingTraitSelection != null;
    }

    public boolean traitsEnabled() {
        return traitSelectionConfig.enabled();
    }

    public int traitSelectionSecondsRemaining() {
        return pendingTraitSelection == null ? 0 : pendingTraitSelection.remainingSeconds();
    }

    public int startCountdownSecondsRemaining() {
        if (pendingTraitSelection != null) {
            return pendingTraitSelection.remainingSeconds();
        }
        if (pendingStartPlan == null) {
            return 0;
        }
        return Math.max(1, (startCountdownTicks + 19) / 20);
    }

    public MidLanePreferenceResult requestMidLane(UUID playerId) {
        ParticipantSelectionPlan plan = pendingStartPlan != null
                ? pendingStartPlan
                : pendingTraitSelection == null ? null : pendingTraitSelection.plan();
        if (plan == null) {
            return MidLanePreferenceResult.NO_PENDING_START;
        }

        AssignedParticipant participant = plan.activeParticipants().stream()
                .filter(candidate -> candidate.uuid().equals(playerId))
                .findFirst()
                .orElse(null);
        if (participant == null) {
            return MidLanePreferenceResult.NOT_PARTICIPANT;
        }
        if (plan.teamSizes().getOrDefault(participant.teamId(), 0) != 5) {
            return MidLanePreferenceResult.NO_MID_LANE;
        }

        UUID current = midLanePreferences.putIfAbsent(participant.teamId(), playerId);
        if (current == null) {
            return MidLanePreferenceResult.REQUESTED;
        }
        return current.equals(playerId)
                ? MidLanePreferenceResult.ALREADY_REQUESTED
                : MidLanePreferenceResult.TEAM_ALREADY_REQUESTED;
    }

    private static boolean isSelectedForPendingMatch(ParticipantSelectionPlan plan, UUID playerId) {
        return plan.spectatorIds().contains(playerId)
                || plan.activeParticipants().stream().anyMatch(participant -> participant.uuid().equals(playerId));
    }

    public StartCountdownResult scheduleStart(MinecraftServer server, ParticipantSelectionPlan plan) {
        if (activeGame == null) {
            return StartCountdownResult.NO_ACTIVE_GAME;
        }
        if (pendingStartPlan != null || pendingTraitSelection != null) {
            return StartCountdownResult.ALREADY_PENDING;
        }
        if (!activeGame.canConfigureRoster()) {
            return StartCountdownResult.NOT_WAITING;
        }
        if (!activeGame.preloadWorldsForStart(plan)) {
            return StartCountdownResult.PRELOAD_FAILED;
        }

        midLanePreferences.clear();
        closePracticeGamesFor(server, plan);
        if (!traitsEnabled() || !hasSelectableTraits()) {
            pendingStartPlan = plan;
            pendingStartTraitSnapshot = TraitSelectionSnapshot.empty();
            startCountdownTicks = START_COUNTDOWN_TICKS;
            nextStartCountdownAnnouncementSecond = startCountdownSecondsRemaining();
            announceStartCountdown(server, nextStartCountdownAnnouncementSecond);
            return StartCountdownResult.SCHEDULED;
        }

        pendingTraitSelection = new TraitSelectionSession(
                plan,
                activeGame.selectedTraitLoadouts(),
                traitSelectionConfig.selectionDurationTicks()
        );
        announceTraitSelectionStarted(server, pendingTraitSelection);
        showTraitSelectionToActiveParticipants(server, pendingTraitSelection);
        if (pendingTraitSelection.complete()) {
            finishTraitSelectionAndScheduleCountdown(server, false);
        }
        return StartCountdownResult.SCHEDULED;
    }

    public TraitSelectionSession.SelectionResult selectTrait(
            MinecraftServer server,
            UUID playerId,
            TraitSlot slot,
            ResourceLocation traitId
    ) {
        if (!traitsEnabled()) {
            return TraitSelectionSession.SelectionResult.DISABLED;
        }
        SemionGame sandbox = sandboxGames.get(playerId);
        if (sandbox != null) {
            return sandbox.selectTrait(playerId, slot, traitId);
        }
        if (activeGame == null) {
            return TraitSelectionSession.SelectionResult.NOT_PARTICIPANT;
        }
        if (pendingTraitSelection != null) {
            TraitSelectionSession.SelectionResult result = pendingTraitSelection.select(playerId, slot, traitId);
            if (result == TraitSelectionSession.SelectionResult.SELECTED && pendingTraitSelection.complete()) {
                finishTraitSelectionAndScheduleCountdown(server, false);
            }
            return result;
        }
        if (!activeGame.canConfigureRoster()) {
            return TraitSelectionSession.SelectionResult.STARTED;
        }
        return activeGame.selectTrait(playerId, slot, traitId);
    }

    public TraitLoadout traitLoadoutOrDefault(UUID playerId) {
        SemionGame sandbox = sandboxGames.get(playerId);
        if (sandbox != null) {
            return sandbox.selectedTraitLoadoutOrDefault(playerId);
        }
        if (pendingTraitSelection != null) {
            return pendingTraitSelection.loadoutOrDefault(playerId);
        }
        if (pendingStartPlan != null) {
            return pendingStartTraitSnapshot.loadoutOrDefault(playerId);
        }
        return activeGame == null ? TraitLoadout.none() : activeGame.selectedTraitLoadoutOrDefault(playerId);
    }

    public MatchMode matchMode() {
        return matchMode;
    }

    public boolean teamEloMatchmakingEnabled() {
        return ratingConfig.teamEloMatchmakingEnabled();
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode;
    }

    public void tick(MinecraftServer server) {
        tickCombatSpeed(server);
        IllusionCloneSpawnQueue.tick();
        musicService.tick(server, activeGame, java.util.stream.Stream
                .concat(sandboxGames.values().stream(), tutorialGames.values().stream())
                .toList());
        tickPendingRatingRetry(server);
        tickPendingMatchResultDialog(server);
        tickSandboxGames(server);
        tickTutorialGames(server);
        if (activeGame == null) {
            clearStartCountdown();
            clearTraitSelection();
            Set<UUID> practiceViewerIds = practiceViewerIds();
            illagerRaidBossBarService.clearExcept(practiceViewerIds);
            villagerAdvReputationBossBarService.clearExcept(practiceViewerIds);
            engineerRedstoneBossBarService.clearExcept(practiceViewerIds);
            mageManaBossBarService.clearExcept(practiceViewerIds);
            queenBossBarService.clearExcept(practiceViewerIds);
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
            sidebarHudService.tick(server, activeGame, matchMode, practiceViewerIds());
            return;
        }

        if (pendingTraitSelection != null) {
            tickTraitSelection(server);
            sidebarHudService.tick(server, activeGame, matchMode, practiceViewerIds());
            return;
        }

        activeGame.tick(server);
        if (activeGame != null && activeGame.phase() == RoundPhase.ENDED) {
            beginDelayedMatchResult(server, activeGame);
            return;
        }
        if (activeGame != null) {
            Set<UUID> practiceViewerIds = practiceViewerIds();
            illagerRaidBossBarService.tick(server, activeGame, practiceViewerIds);
            villagerAdvReputationBossBarService.tick(server, activeGame, practiceViewerIds);
            engineerRedstoneBossBarService.tick(server, activeGame, practiceViewerIds);
            mageManaBossBarService.tick(server, activeGame, practiceViewerIds);
            queenBossBarService.tick(server, activeGame, practiceViewerIds);
            sidebarHudService.tick(server, activeGame, matchMode, practiceViewerIds);
        }
    }

    public void handlePlayerJoin(ServerPlayer player) {
        musicService.handlePlayerJoin(player);
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        profile(server, player.getUUID(), player.getGameProfile().getName());
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
                player.sendSystemMessage(SemionText.prefixedPlain("이미 게임이 진행중입니다! /semiontd spectate 를 이용해 관전하거나 /샌드박스 start 로 연습모드를 플레이해보세요!"));
                return;
            }

            if (activeGame != null && activeGame.canConfigureRoster()) {
                applyPersistedJobSelection(server, activeGame, player);
                TraitSelectionSession session = pendingTraitSelection;
                if (session != null && session.plan().activeParticipants().stream()
                        .anyMatch(participant -> participant.uuid().equals(player.getUUID()))) {
                    dialogService.showTraitSelection(
                            player,
                            session.loadoutOrDefault(player.getUUID()),
                            session.remainingSeconds()
                    );
                }
            }
            try {
                sendPlayerToLobby(server, player);
                if (activeGame != null && activeGame.canConfigureRoster()) {
                    sidebarHudService.refreshNow(server, activeGame, matchMode, practiceViewerIds());
                }
            } catch (ArenaLoadException exception) {
                SemionTd.LOGGER.warn("Failed to send player {} to lobby.", player.getGameProfile().getName(), exception);
            }
        },1);
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        sidebarHudService.remove(player);
        illagerRaidBossBarService.removePlayer(player.getUUID());
        villagerAdvReputationBossBarService.removePlayer(player.getUUID());
        engineerRedstoneBossBarService.removePlayer(player.getUUID());
        mageManaBossBarService.removePlayer(player.getUUID());
        queenBossBarService.removePlayer(player.getUUID());
        releaseSandboxSpectator(player.getUUID());
        stopSandbox(player.getServer(), player.getUUID());
        stopTutorial(player.getServer(), player.getUUID());
    }

    public void handlePlayerWorldChanged(ServerPlayer player) {
        musicService.handlePlayerWorldChanged(player);
    }

    public void shutdown() {
        IllusionCloneSpawnQueue.clear();
        jobStatisticsService.shutdown();
        closeAllPracticeGames();
        if (activeGame != null) {
            activeGame.close();
            activeGame = null;
        }
        HeroCompanionSkins.clearAll();
        clearRatingProfileCache();
        clearStartCountdown();
        clearTraitSelection();
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

    public void restoreCombatTickRate(MinecraftServer server) {
        if (managesTickRate && server != null) {
            server.tickRateManager().setTickRate(NORMAL_TICK_RATE);
        }
        managesTickRate = false;
    }

    private void tickCombatSpeed(MinecraftServer server) {
        RoundPhase phase = activeGame == null ? RoundPhase.WAITING : activeGame.phase();
        if (phase != previousCombatSpeedPhase) {
            if (phase == RoundPhase.LANE_WAVE) {
                beginCombatSpeedWave();
            }
            previousCombatSpeedPhase = phase;
        }

        if (!combatSpeedConfig.enabled()) {
            restoreCombatTickRate(server);
            return;
        }

        managesTickRate = true;
        float targetTickRate = combatTickRateTarget(
                combatSpeedConfig,
                phase,
                !sandboxGames.isEmpty() || !tutorialGames.isEmpty(),
                combatSpeedOverloaded
        );
        if (targetTickRate <= NORMAL_TICK_RATE) {
            setCombatTickRate(server, NORMAL_TICK_RATE);
            combatSpeedAccelerated = false;
            combatSpeedEntryDelayTicks = 0;
            return;
        }

        double averageTickTimeMillis = server.getAverageTickTimeNanos() / 1_000_000.0;
        if (!combatSpeedAccelerated) {
            setCombatTickRate(server, NORMAL_TICK_RATE);
            if (combatSpeedEntryDelayTicks++ < COMBAT_SPEED_ENTRY_DELAY_TICKS) {
                return;
            }
            if (!isCombatSpeedEntrySafe(combatSpeedConfig, averageTickTimeMillis)) {
                blockCombatSpeedForWave();
                SemionTd.LOGGER.warn(
                        "Combat tick rate remained at 20 TPS: average tick time {} ms exceeded the {} ms entry threshold.",
                        String.format("%.2f", averageTickTimeMillis),
                        String.format("%.2f", combatSpeedEntryThresholdMillis(combatSpeedConfig))
                );
                return;
            }
            combatSpeedAccelerated = true;
            setCombatTickRate(server, targetTickRate);
            return;
        }

        setCombatTickRate(server, targetTickRate);
        if (averageTickTimeMillis <= combatSpeedConfig.maxAverageTickTimeMillis()) {
            return;
        }

        blockCombatSpeedForWave();
        setCombatTickRate(server, NORMAL_TICK_RATE);
        SemionTd.LOGGER.warn(
                "Combat tick rate restored to 20 TPS: average tick time {} ms exceeded {} ms.",
                String.format("%.2f", averageTickTimeMillis),
                combatSpeedConfig.maxAverageTickTimeMillis()
        );
        server.getPlayerList().broadcastSystemMessage(
                SemionText.prefixedPlain("서버 성능 보호를 위해 이번 웨이브의 전투 배속을 해제했습니다."),
                false
        );
    }

    private void setCombatTickRate(MinecraftServer server, float tickRate) {
        if (Math.abs(server.tickRateManager().tickrate() - tickRate) > 0.01F) {
            server.tickRateManager().setTickRate(tickRate);
        }
    }

    void blockCombatSpeedForWave() {
        combatSpeedOverloaded = true;
        combatSpeedAccelerated = false;
        combatSpeedEntryDelayTicks = 0;
        combatSpeedCooldownWaves = COMBAT_SPEED_COOLDOWN_WAVES;
    }

    boolean beginCombatSpeedWave() {
        combatSpeedOverloaded = combatSpeedCooldownWaves > 0;
        combatSpeedCooldownWaves = Math.max(0, combatSpeedCooldownWaves - 1);
        combatSpeedAccelerated = false;
        combatSpeedEntryDelayTicks = 0;
        return combatSpeedOverloaded;
    }

    private void resetCombatSpeedState() {
        previousCombatSpeedPhase = RoundPhase.WAITING;
        combatSpeedOverloaded = false;
        combatSpeedAccelerated = false;
        combatSpeedEntryDelayTicks = 0;
        combatSpeedCooldownWaves = 0;
    }

    static double combatSpeedEntryThresholdMillis(CombatSpeedConfig config) {
        return config.maxAverageTickTimeMillis() * COMBAT_SPEED_ENTRY_LOAD_RATIO;
    }

    static boolean isCombatSpeedEntrySafe(CombatSpeedConfig config, double averageTickTimeMillis) {
        return averageTickTimeMillis <= combatSpeedEntryThresholdMillis(config);
    }

    static float combatTickRateTarget(
            CombatSpeedConfig config,
            RoundPhase phase,
            boolean sandboxActive,
            boolean overloaded
    ) {
        return config.enabled() && phase == RoundPhase.LANE_WAVE && !sandboxActive && !overloaded
                ? config.combatTickRate()
                : NORMAL_TICK_RATE;
    }

    private void sendPlayerToLobby(MinecraftServer server, ServerPlayer player) throws ArenaLoadException {
        LobbyWorld lobby = ensureLobby(server);
        VanillaTeamBridge.assignSpectator(server, player);
        player.setGameMode(GameType.ADVENTURE);
        player.teleport(PlayerTeleportTransitions.preservingFacing(
                lobby.world(),
                lobby.spawn(),
                player.getDeltaMovement(),
                player
        ));
        SemionSidebarHudService.refreshPlayerHud(player);
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

    private boolean applyPersistedJobSelection(MinecraftServer server, SemionGame game, ServerPlayer player) {
        return applyPersistedJobSelection(
                server,
                game,
                player.getUUID(),
                player.getGameProfile().getName()
        );
    }

    private boolean applyPersistedJobSelection(MinecraftServer server, SemionGame game, UUID playerId, String playerName) {
        SemionPlayerProfile profile = profile(
                server,
                playerId,
                playerName
        );
        Optional<ResourceLocation> selectedJobId = profile.selectedJobResource();
        if (selectedJobId.isEmpty()) {
            return false;
        }
        ResourceLocation jobId = selectedJobId.get();
        if (!game.selectJob(playerId, jobId)) {
            SemionTd.LOGGER.warn(
                    "Ignoring persisted Semion TD job {} for player {}; the job is not available.",
                    jobId,
                    playerName
            );
            return false;
        }
        return true;
    }

    private void applyFallbackSandboxJob(SemionGame sandbox, UUID playerId) {
        kim.biryeong.semiontd.job.JobRegistry.all().stream()
                .filter(job -> !job.id().equals(kim.biryeong.semiontd.job.JobRegistry.defaultJob().id()))
                .filter(kim.biryeong.semiontd.job.JobRegistry::isEnabled)
                .findFirst()
                .ifPresent(job -> sandbox.selectJob(playerId, job.id()));
    }

    private void tickTraitSelection(MinecraftServer server) {
        if (pendingTraitSelection == null) {
            return;
        }
        if (activeGame == null || !activeGame.canConfigureRoster()) {
            clearTraitSelection();
            return;
        }
        if (pendingTraitSelection.complete()) {
            finishTraitSelectionAndScheduleCountdown(server, false);
            return;
        }
        int previousSeconds = pendingTraitSelection.remainingSeconds();
        boolean timedOut = pendingTraitSelection.tick();
        int secondsRemaining = pendingTraitSelection.remainingSeconds();
        if (!timedOut && secondsRemaining < previousSeconds) {
            announceTraitSelectionCountdown(server, secondsRemaining);
        }
        if (timedOut) {
            finishTraitSelectionAndScheduleCountdown(server, true);
        }
    }

    private static boolean hasSelectableTraits() {
        return TraitRegistry.all().stream().anyMatch(trait -> !TraitLoadout.isNone(trait.id()));
    }

    private void finishTraitSelectionAndScheduleCountdown(MinecraftServer server, boolean timeout) {
        TraitSelectionSession session = pendingTraitSelection;
        if (session == null) {
            return;
        }
        TraitSelectionSnapshot snapshot = session.snapshot();
        ParticipantSelectionPlan plan = session.plan();
        clearTraitSelection();
        pendingStartPlan = plan;
        pendingStartTraitSnapshot = snapshot;
        startCountdownTicks = START_COUNTDOWN_TICKS;
        nextStartCountdownAnnouncementSecond = startCountdownSecondsRemaining();
        if (timeout) {
            server.getPlayerList().broadcastSystemMessage(
                    SemionText.prefixedPlain("특성 선택 시간이 종료되어 미선택 슬롯은 '선택 안 함'으로 적용됩니다."),
                    false
            );
        } else {
            server.getPlayerList().broadcastSystemMessage(
                    SemionText.prefixedPlain("모든 참가자가 특성 선택을 완료했습니다."),
                    false
            );
        }
        announceStartCountdown(server, nextStartCountdownAnnouncementSecond);
    }

    private void announceTraitSelectionStarted(MinecraftServer server, TraitSelectionSession session) {
        server.getPlayerList().broadcastSystemMessage(
                SemionText.prefixedMini("<aqua><bold>특성 선택</bold></aqua> 단계가 시작되었습니다. <yellow>"
                        + session.remainingSeconds()
                        + "초</yellow> 안에 /특성 으로 주특성/부특성을 선택하세요."),
                false
        );
        if (shouldPlayTraitSelectionChime(session.remainingSeconds())) {
            playTraitSelectionChime(server);
        }
    }

    private void announceTraitSelectionCountdown(MinecraftServer server, int secondsRemaining) {
        if (shouldAnnounceTraitSelectionCountdown(secondsRemaining)) {
            server.getPlayerList().broadcastSystemMessage(
                    SemionText.prefixedMini("<aqua><bold>특성 선택</bold></aqua> 종료까지 <yellow>"
                            + secondsRemaining + "초</yellow> 남았습니다."),
                    false
            );
        }
        if (shouldPlayTraitSelectionChime(secondsRemaining)) {
            playTraitSelectionChime(server);
        }
    }

    static boolean shouldAnnounceTraitSelectionCountdown(int secondsRemaining) {
        return secondsRemaining == 30
                || secondsRemaining == 15
                || secondsRemaining >= 1 && secondsRemaining <= 10;
    }

    static boolean shouldPlayTraitSelectionChime(int secondsRemaining) {
        return secondsRemaining > 0 && secondsRemaining % 5 == 0;
    }

    private static void playTraitSelectionChime(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(player ->
                player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0F, 1.0F)
        );
    }

    private void showTraitSelectionToActiveParticipants(MinecraftServer server, TraitSelectionSession session) {
        for (AssignedParticipant participant : session.plan().activeParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.uuid());
            if (player != null) {
                dialogService.showTraitSelection(player, session.loadoutOrDefault(participant.uuid()), session.remainingSeconds());
            }
        }
    }

    private void clearTraitSelection() {
        pendingTraitSelection = null;
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
                if (secondsRemaining == 10 || secondsRemaining <= 5) {
                    announceStartCountdown(server, secondsRemaining);
                }
            }
            if (startCountdownTicks > 0) {
                return;
            }
        }

        ParticipantSelectionPlan plan = pendingStartPlan;
        for (UUID playerId : midLanePreferences.values()) {
            plan = plan.withFifthLanePreference(playerId);
        }
        TraitSelectionSnapshot traitSnapshot = pendingStartTraitSnapshot;
        clearStartCountdown();
        closePracticeGamesFor(server, plan);
        if (!activeGame.start(server, plan, traitSnapshot)) {
            server.getPlayerList().broadcastSystemMessage(
                    SemionText.prefixedPlain("시작 카운트다운이 취소되었습니다. 참가자 확정에 실패했습니다."),
                    false
            );
            return;
        }
        cacheRatingProfilesForMatch(plan);
        clearPriorityForActiveParticipants(plan);
        server.getPlayerList().broadcastSystemMessage(SemionText.prefixedMini("<green><bold>게임을 시작합니다.</bold></green>"), false);
        activeGame.announceTeamLeaders(server);
        sidebarHudService.refreshNow(server, activeGame, matchMode, practiceViewerIds());
    }

    private void announceStartCountdown(MinecraftServer server, int secondsRemaining) {
        server.getPlayerList().broadcastSystemMessage(
                SemionText.prefixedMini("<yellow>" + secondsRemaining + "초</yellow> 후 게임을 시작합니다."),
                false
        );
        if (secondsRemaining == START_COUNTDOWN_TICKS / 20) {
            server.getPlayerList().broadcastSystemMessage(
                    SemionText.prefixedMini("5인 팀에서 <aqua>5번 라인(미드)</aqua>을 원하면 지금 <yellow>/미드희망</yellow>을 입력하세요."),
                    false
            );
        }
        server.getPlayerList().getPlayers().forEach(player -> {
            player.playNotifySound(SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.MUSIC, 1557f, 1f);
        });
    }

    private void clearStartCountdown() {
        pendingStartPlan = null;
        pendingStartTraitSnapshot = TraitSelectionSnapshot.empty();
        midLanePreferences.clear();
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
            clearTraitSelection();
            clearRatingProfileCache();
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
        Optional<MatchResult> result = finishedGame.matchResult();
        finalizeBuildGuideRecording(finishedGame, result);
        sidebarHudService.clear(server);
        illagerRaidBossBarService.clear(server);
        villagerAdvReputationBossBarService.clear(server);
        engineerRedstoneBossBarService.clear(server);
        mageManaBossBarService.clear(server);
        queenBossBarService.clear(server);
        server.getPlayerList().broadcastSystemMessage(
                SemionText.prefixedMini("<gold>경기 종료.</gold> 결과를 집계하는 중입니다..."),
                false
        );
    }

    void finalizeBuildGuideRecording(SemionGame finishedGame, Optional<MatchResult> result) {
        if (finishedGame == null || !finishedGame.rosterLocked()) {
            return;
        }
        int finalRound = result
                .map(MatchResult::finalRound)
                .orElseGet(() -> Math.max(1, finishedGame.currentRound()));
        buildGuideService.finishMatch(finishedGame, finalRound);
    }

    private void finishActiveGame(MinecraftServer server, SemionGame finishedGame) {
        sidebarHudService.clear(server);
        illagerRaidBossBarService.clear(server);
        villagerAdvReputationBossBarService.clear(server);
        engineerRedstoneBossBarService.clear(server);
        mageManaBossBarService.clear(server);
        queenBossBarService.clear(server);
        Optional<MatchResult> result = finishedGame.matchResult();
        Optional<RatingMatchResult> ratingResult = Optional.empty();
        if (result.isPresent()) {
            lastMatchResult = result.get();
            matchResultRepository.saveMatchResult(result.get());
            jobStatisticsService.record(result.get());
            ratingResult = applyRatingOrQueueRetry(server, result.get());
            finalizeBuildGuideRecording(finishedGame, result);
            nextMatchPriorityPlayerIds.addAll(result.get().spectatorIds());
            Map<UUID, MatchProgressionReward> rewards = progressionService.applyMatchResult(server, result.get());
            queueMatchResultDialog(result.get(), rewards);
        } else {
            lastMatchResult = null;
            clearPendingMatchResultDialog();
        }

        try {
            sendAllPlayersToLobby(server);
            ratingResult.ifPresent(appliedRating -> showRatingTitle(server, appliedRating));
        } catch (ArenaLoadException exception) {
            SemionTd.LOGGER.warn("Failed to send players to the Semion TD lobby after match end.", exception);
        }

        closeActiveGameSafely(finishedGame, "finishing match");
    }

    private Optional<RatingMatchResult> applyRatingOrQueueRetry(MinecraftServer server, MatchResult matchResult) {
        if (!pendingRatingRetryMatchResults.isEmpty()
                && !pendingRatingRetryMatchResults.containsKey(matchResult.matchId())) {
            pendingRatingRetryMatchResults.put(matchResult.matchId(), matchResult);
            SemionTd.LOGGER.warn(
                    "Queued Semion TD rating for match {} behind {} pending retry match(es) to preserve chronological profile updates.",
                    matchResult.matchId(),
                    pendingRatingRetryMatchResults.size() - 1
            );
            return Optional.empty();
        }
        try {
            RatingMatchResult ratingResult = ratingService.applyMatchResult(server, matchResult);
            pendingRatingRetryMatchResults.remove(matchResult.matchId());
            if (pendingRatingRetryMatchResults.isEmpty()) {
                pendingRatingRetryDelayTicks = 0;
            }
            return Optional.of(ratingResult);
        } catch (RuntimeException exception) {
            pendingRatingRetryMatchResults.put(matchResult.matchId(), matchResult);
            pendingRatingRetryDelayTicks = RATING_RETRY_DELAY_TICKS;
            SemionTd.LOGGER.warn(
                    "Failed to apply Semion TD rating for match {}. Queued retry while continuing match finish flow.",
                    matchResult.matchId(),
                    exception
            );
            return Optional.empty();
        }
    }

    private void showRatingTitle(MinecraftServer server, RatingMatchResult ratingResult) {
        for (RatingAdjustment adjustment : ratingResult.adjustments()) {
            ServerPlayer player = server.getPlayerList().getPlayer(adjustment.playerId());
            if (player != null) {
                SemionRatingTitleService.showRatingChange(player, adjustment);
            }
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
