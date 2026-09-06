package kim.biryeong.semiontd.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;
import kim.biryeong.semiontd.config.LeaderTargetingConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterState;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.entity.monster.WaveHealingState;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.VillagerTowerJob;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.map.TeamArena;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Opt-in checkpoint experiment, not a simulated R1-R16 match or a release balance verdict.
 * Use -Dsemiontd.economyBalance=true and the exact FILTER below, in JAVA_TOOL_OPTIONS.
 * A fixed R15 board/balance and one R5 GOLD selection are fixture inputs. No R1-R14 payout,
 * survival, stacking or earlier cash contracts are invented. R15 and R16 combat are real;
 * only an actual R15 round payout unlocks R16. Preparation ticks are advanced synchronously.
 */
public final class SeasonThreeEconomyBalanceGameTest {
    private static final String FILTER = "semion-td-gametest:season_three_economy_balance_game_test_cash_reinvestment_against_tactical_designation";
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final int ROUND_TICKS = 2_400;
    private static final int FLOOR_MARGIN = 8;
    private static final String SUMMON = "iron_golem";
    private static final int[][] POSITIONS = {{8, 12}, {11, 12}, {6, 15}, {9, 15}, {12, 15}, {9, 18}};
    private static final List<Board> BOARDS = List.of(
            new Board("villager", VillagerTowerJob.ID,
                    List.of("t3_golem_tower", "t3_golem_tower", "villager_splash_t3", "villager_splash_t3", "villager_splash_t2", "t2_allay_tower"), 2,
                    List.of(new Investment(4, "villager_splash_t2", "villager_splash_t3"), new Investment(5, "t2_allay_tower", "t3_armorer_tower"))),
            new Board("animal", AnimalTowerJob.ID,
                    List.of("t3_pig_tower", "t3_pig_tower", "t3_pig_tower", "t3_wolf_dps_tower", "t3_wolf_dps_tower", "t2_wolf_dps_tower"), 3,
                    List.of(new Investment(5, "t2_wolf_dps_tower", "t3_wolf_dps_tower"))));
    private static final List<String> LIMITS = List.of("INTERNAL_CHECKPOINT_NOT_FULL_MATCH", "NO_R1_R14_HISTORY",
            "ONE_R5_GOLD_SELECTION_NO_R15_SELECTION", "FIXED_FACTORY_BOARD_WITHOUT_PRIOR_STACKS",
            "SINGLE_FIXED_REINVESTMENT_POLICY", "ONE_SHARED_BOSS_PER_TEAM", "CROSS_LANE_FINAL_DEFENSE_ATTRIBUTION", "CORRELATED_FOUR_PLAYER_FIXTURE",
            "CUSTOM_SYMMETRIC_ARENA_NOT_PUBLIC_MAP", "UNSEEDED_ENTITY_AI", "NO_CLIENT_OR_GUI_EVIDENCE",
            "NO_SURVIVAL_OR_FUTURE_PAYOUT_ASSUMPTION");

    @GameTest(maxTicks = 10_000)
    public void cashReinvestmentAgainstTacticalDesignation(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.economyBalance")) {
            SemionTd.LOGGER.info("SEASON3_ECONOMY_BALANCE_NOT_RUN: opt-in property absent; no economy balance evidence collected.");
            context.succeed();
            return;
        }
        if (!FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof GameTestServer)) {
            context.fail(Component.literal("Economy fixture requires its exact isolated filter and headless GameTestServer."));
            return;
        }
        new Run(context, 0).start();
    }

    private enum Group { CASH, ATTACK }
    private record Investment(int slot, String from, String upgradeId) {}
    private record Board(String id, ResourceLocation job, List<String> towers, int assaultSlot, List<Investment> priority) {}
    private record Balance(long diamonds, long emeralds, long income, long emeraldPerSecond) {}
    private record Purchase(String summonId, int round, long baseEmeraldCost, long actualEmeraldPaid,
            long normalIncomeGain, long actualIncomeGain, long instantDiamonds, long incomeForgone,
            TeamId targetTeam, int targetLane) {}
    private record Upgrade(int round, int slot, String from, String upgradeId, long quotedCost,
            TowerUpgradeResult result, long actualDiamondPaid) {}
    private record Payment(int round, long incomeBeforePayout, long actualDiamondGranted) {}
    private record MissingMonster(int logicalRef, String typeId, MonsterOrigin origin, MonsterState state,
            double health, KillSourceKind lastHitSource, boolean leaked, Double x, Double y, Double z, String removalReason) {}
    /** Terminal categories are disjoint. Leaked deaths/rewards remain in their kill category, not counted twice. */
    private record MonsterAccounting(int observedLogical, int entityConfirmed, int playerKilled, int bossKilled,
            int leakedNotKilled, int activeUnleaked, int endpointCleanup, int unaccounted,
            int uncreditedDeaths, int belowPlatform, int outsidePlatform, List<MissingMonster> missing) {
        boolean valid() { return entityConfirmed == observedLogical && unaccounted == 0 && uncreditedDeaths == 0 && belowPlatform == 0 && outsidePlatform == 0; }
    }
    private record Battle(int round, String status, int observedTicks, int expectedNaturalSpawns, int observedNaturalSpawns,
            double initialNaturalHealth, int observedPaidSpawns, long observedHealers, int leaks, double leakedThreat, boolean laneCleared,
            boolean defenseBroken, boolean teamSurvived, double teamBossHealth, long aliveMonsters,
            Integer firstClearTick, long rewardedKills,
            List<TowerRoundMetricsSnapshot> towers, WaveHealingState.Snapshot healing,
            MonsterSupportMetrics.Snapshot naturalSupport, boolean rawLaneCleared, MonsterAccounting accounting) {}
    private record SubjectReport(Group group, TeamId team, int lane, List<PlayerAugmentState.Selection> selections,
            Balance startingBalance, Purchase purchase, List<Upgrade> upgrades, List<Payment> payouts,
            Balance endingBalance, List<Battle> battles, String round16Status,
            List<AugmentTelemetrySnapshot.EconomyEvent> economyEvents) {}
    private record Report(String status, String board, String matchMode, int logicalParticipants, int initialTowers,
            String towerBalanceSha256, String augmentConfigSha256, String waveConfigSha256,
            List<String> initialBoard, List<Investment> fixedInvestmentPriority, List<String> limits,
            int endpointRound, int observedCombatTicks, double wallSeconds, List<SubjectReport> subjects) {}

    private static final class Subject {
        final Group group;
        final SemionPlayer player;
        final PlayerLane lane;
        final List<GridPosition> slots = new ArrayList<>();
        final List<Upgrade> upgrades = new ArrayList<>();
        final List<Payment> payouts = new ArrayList<>();
        final List<Battle> battles = new ArrayList<>();
        final Map<UUID, Monster> spawned = new LinkedHashMap<>();
        final Map<UUID, Entity> entities = new LinkedHashMap<>();
        final Set<UUID> belowPlatform = new HashSet<>();
        final Set<UUID> outsidePlatform = new HashSet<>();
        Set<UUID> activeBeforeTick = Set.of();
        Balance startingBalance;
        Purchase purchase;
        int expectedNaturalSpawns;
        double initialNaturalHealth;
        long killsAtWaveStart;
        Integer firstClearTick;

        Subject(Group group, SemionPlayer player, PlayerLane lane) {
            this.group = group;
            this.player = player;
            this.lane = lane;
        }

    }

    private static final class Run {
        final GameTestHelper context;
        final ServerLevel level;
        final BlockPos origin;
        final List<BlockPos> floor = new ArrayList<>();
        final Set<Long> forcedChunks = new HashSet<>();
        final List<Subject> subjects = new ArrayList<>();
        final int boardIndex;
        final Board board;
        final WaveConfig waves = WaveConfig.defaultConfig().withSeason3Stages(false, true, Set.of());
        final AugmentConfig augments;
        final String towerHash = hash(TowerBalanceRuntime.current());
        SemionGame game;
        int round = 15;
        int roundTicks;
        int totalCombatTicks;
        long started;

        Run(GameTestHelper context, int boardIndex) {
            this.context = context;
            this.boardIndex = boardIndex;
            board = BOARDS.get(boardIndex);
            level = context.getLevel();
            origin = context.absolutePos(BlockPos.ZERO).offset(2048, 0, 1024);
            Map<String, Integer> weights = new LinkedHashMap<>();
            AugmentConfig.defaults().rarityWeights().keySet().forEach(key -> weights.put(key, key.equals("GGG") ? 100 : 0));
            augments = new AugmentConfig(true, false, weights, AugmentConfig.defaults().parameters(), Set.of());
        }

        void start() {
            safely(() -> {
                GameArena arena = buildArena();
                game = new SemionGame(EconomyConfig.defaultConfig(), waves, LeaderTargetingConfig.defaultConfig(),
                        new IncomeLaneRoutingConfig(true, IncomeLaneRoutingConfig.Mode.LEAST_THREAT_PRESSURE,
                                1, .75, IncomeLaneRoutingConfig.TieBreakMode.ROUND_ROBIN), arena);
                game.configureAugments(augments);
                context.runAfterDelay(40, () -> safely(this::prepareCheckpoint));
            });
        }

        private void prepareCheckpoint() {
            List<AssignedParticipant> participants = new ArrayList<>();
            for (TeamId team : List.of(TeamId.RED, TeamId.BLUE)) {
                for (int lane = 1; lane <= 2; lane++) {
                    UUID id = UUID.nameUUIDFromBytes(("s3-economy-" + team + "-" + lane).getBytes(StandardCharsets.UTF_8));
                    participants.add(new AssignedParticipant(id, "economy-" + team + "-" + lane, team, lane));
                    require(game.selectJob(id, board.job()), "The fixture must select the designated ordinary official builder.");
                }
            }
            require(game.start(level.getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, participants, Set.of(), 2)), "NORMAL fixture must start.");
            require(game.augmentRarities().equals(List.of(AugmentRarity.GOLD, AugmentRarity.GOLD, AugmentRarity.GOLD)), "Both choices require the same GOLD schedule.");
            startAtCheckpointRound(5);
            for (AssignedParticipant participant : participants) {
                SemionPlayer player = game.players().get(participant.uuid());
                Group group = (participant.teamId() == TeamId.RED) == (participant.laneId() == 1) ? Group.CASH : Group.ATTACK;
                Subject subject = new Subject(group, player, game.playerLane(player.uuid()).orElseThrow());
                subjects.add(subject);
                BlockPos base = laneBase(player.teamId(), player.laneId());
                for (int slot = 0; slot < board.towers().size(); slot++) {
                    GridPosition position = new GridPosition(base.getX() + POSITIONS[slot][0], base.getY(), base.getZ() + POSITIONS[slot][1]);
                    Tower tower = ProductionTowerCatalog.find(board.towers().get(slot)).orElseThrow().create(player.uuid(), player.teamId(), player.laneId(), position);
                    subject.lane.addTower(tower);
                    subject.slots.add(position);
                }
                require(subject.lane.towers().stream().allMatch(tower -> player.job().orElseThrow().includesTowerInCatalog(tower.type())), "Every baseline tower must belong to the selected builder.");
                require(game.towerCapacityUsed(player.uuid()) <= game.towerLimitForPlayer(player.uuid()), "The fixed board must respect the real tower capacity.");
                var offer = player.augments().forceOffer(5, 5, game.currentTick() + kim.biryeong.semiontd.augment.AugmentService.PREPARE_TICKS,
                        List.of("semiontd:cash_settlement", "semiontd:tactical_designation_2", "semiontd:reserve_income_gold"));
                int selectedSlot = group == Group.CASH ? 0 : 1;
                AugmentChoice choice = group == Group.CASH ? AugmentChoice.none()
                        : new AugmentChoice(subject.lane.towerAt(subject.slots.get(board.assaultSlot())).logicalId(), null, "ASSAULT");
                for (int tick = 0; tick < 20; tick++) { game.tick(level.getServer()); }
                long inputTick = game.currentTick();
                require(player.augments().draft(5, selectedSlot, offer.revision(), 0, choice, UUID.randomUUID(), inputTick, ignored -> true).successful(), "The fixed R5 draft must be valid.");
                var draft = player.augments().currentOffer().orElseThrow();
                require(player.augments().confirm(5, draft.revision(), draft.draftRevision(), UUID.randomUUID(), inputTick, ignored -> true,
                        (card, checkedChoice) -> {
                            AugmentEconomyService.onSelected(player, card.id(), 5, augments.parameters().getOrDefault(card.id(), Map.of()));
                            return true;
                        }).successful(), "The fixed R5 choice must commit exactly once.");
            }
            // These are explicitly supplied checkpoint inputs, not earnings or survival from omitted rounds.
            startAtCheckpointRound(15);
            for (Subject subject : subjects) {
                subject.player.economy().overrideStartingValues(140, 460, 80, EconomyConfig.defaultConfig().gasProduction().initialEmeraldPerSec());
                subject.startingBalance = balance(subject.player);
            }
            require(subjects.stream().allMatch(subject -> game.upcomingWaveEntries(subject.player.uuid()).equals(game.upcomingWaveEntries(subjects.getFirst().player.uuid()))), "All subjects must receive the same natural R15 wave definition.");
            for (Subject subject : subjects) { purchase(subject); reinvest(subject); }
            for (Subject subject : subjects) {
                require(subject.lane.queuedSummonCount() == 1, "Symmetric purchases must queue one identical enemy summon in each lane.");
            }
            started = System.nanoTime();
            advancePreparation();
            context.runAfterDelay(1, () -> safely(this::tickBattle));
        }

        private void purchase(Subject subject) {
            SemionPlayer player = subject.player;
            if (subject.group == Group.CASH) {
                require(AugmentEconomyService.setContract(player, 15, AugmentEconomyService.Contract.CASH), "CASH must arm only in its real preparation.");
            }
            var type = game.summonShop().find(SUMMON).orElseThrow();
            var quote = AugmentEconomyService.previewPurchase(game, player, type).orElseThrow();
            Balance before = balance(player);
            var result = game.summonMonster(player.uuid(), SUMMON);
            require(result.type() == SummonResultType.SUCCESS, "The fixed legal R15 purchase must succeed with actual funds.");
            subject.purchase = new Purchase(SUMMON, 15, quote.normalEmeraldCost(), before.emeralds() - player.economy().emerald(),
                    quote.normalIncomeGain(), player.economy().income() - before.income(), player.economy().diamond() - before.diamonds(),
                    player.economyAugments().incomeForgone(), result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        }

        private void reinvest(Subject subject) {
            for (Investment investment : board.priority()) {
                Tower tower = subject.lane.towerAt(subject.slots.get(investment.slot()));
                if (tower == null || !tower.type().id().equals(investment.from())) { continue; }
                var option = ProductionTowerCatalog.upgrade(tower.type(), investment.upgradeId()).orElseThrow();
                long before = subject.player.economy().diamond();
                TowerUpgradeResult result = ProductionTowerService.upgradeTower(game, subject.player.uuid(), tower.position(), option.id());
                long paid = before - subject.player.economy().diamond();
                subject.upgrades.add(new Upgrade(game.currentRound(), investment.slot(), investment.from(), option.id(), option.mineralCost(), result, paid));
                require(result == TowerUpgradeResult.SUCCESS || result == TowerUpgradeResult.NOT_ENOUGH_MINERAL, "The predeclared upgrade must fail only for insufficient actual funds: " + result);
                require(paid == (result == TowerUpgradeResult.SUCCESS ? option.mineralCost() : 0), "Only a successful legal upgrade may consume its quoted diamond cost.");
            }
        }

        private void advancePreparation() {
            require(towerHash.equals(hash(TowerBalanceRuntime.current())), "Tower configuration changed between comparison rounds.");
            for (int tick = 0; game.phase() == RoundPhase.PREPARE_AND_SUMMON && tick < 1_000; tick++) { game.tick(level.getServer()); }
            require(game.phase() == RoundPhase.LANE_WAVE, "Real preparation must transition to its actual wave.");
            roundTicks = 0;
            subjects.forEach(subject -> {
                subject.spawned.clear();
                subject.entities.clear();
                subject.belowPlatform.clear();
                subject.outsidePlatform.clear();
                subject.firstClearTick = null;
                subject.expectedNaturalSpawns = subject.lane.naturalWaveCount();
                subject.initialNaturalHealth = subject.lane.naturalWaveStartingHealth();
                subject.killsAtWaveStart = subject.player.matchStats().monsterKills();
                capture(subject);
            });
        }

        private void capture(Subject subject) {
            for (Monster monster : subject.lane.activeMonsters()) {
                subject.spawned.putIfAbsent(monster.logicalId(), monster);
                Entity entity = level.getEntity(monster.minecraftEntityId());
                if (entity != null) { subject.entities.put(monster.logicalId(), entity); }
            }
            BlockPos base = teamBase(subject.player.teamId());
            subject.entities.forEach((id, entity) -> {
                if (entity.isRemoved()) { return; }
                if (entity.getY() < base.getY()) { subject.belowPlatform.add(id); }
                if (entity.getX() < base.getX() - FLOOR_MARGIN || entity.getX() >= base.getX() + 47 + FLOOR_MARGIN
                        || entity.getZ() < base.getZ() - FLOOR_MARGIN || entity.getZ() >= base.getZ() + 47 + FLOOR_MARGIN) {
                    subject.outsidePlatform.add(id);
                }
            });
            if (subject.firstClearTick == null && subject.lane.clearedThisRound()
                    && game.phase() != RoundPhase.ENDED && !game.teams().get(subject.player.teamId()).eliminated()) {
                subject.firstClearTick = roundTicks;
            }
        }

        private MonsterAccounting accounting(Subject subject) {
            int killed = 0, bossKilled = 0, leaked = 0, active = 0, cleanup = 0, missing = 0, uncredited = 0;
            List<MissingMonster> diagnostics = new ArrayList<>();
            int ref = 0;
            for (Monster monster : subject.spawned.values()) {
                ref++;
                UUID id = monster.logicalId();
                Entity entity = subject.entities.get(id);
                boolean unknown = false;
                if (monster.rewardGranted()) { killed++; }
                else if (monster.health() <= 0 && monster.lastHitSourceKind() == KillSourceKind.BOSS) { bossKilled++; }
                else if (monster.isRemoved() && subject.activeBeforeTick.contains(id)
                        && (game.phase() == RoundPhase.ENDED || game.teams().get(subject.player.teamId()).eliminated())) { cleanup++; }
                else if (monster.health() <= 0) { uncredited++; unknown = true; }
                else if (monster.laneLeakRecorded()) { leaked++; }
                else if (!monster.isRemoved() && entity != null && !entity.isRemoved()) { active++; }
                else { missing++; unknown = true; }
                if (unknown || entity == null || subject.belowPlatform.contains(id) || subject.outsidePlatform.contains(id)) {
                    diagnostics.add(new MissingMonster(ref, monster.id(), monster.origin(),
                            monster.state(), monster.health(), monster.lastHitSourceKind(), monster.laneLeakRecorded(),
                            entity == null ? null : entity.getX(), entity == null ? null : entity.getY(), entity == null ? null : entity.getZ(),
                            entity == null || entity.getRemovalReason() == null ? null : entity.getRemovalReason().name()));
                }
            }
            return new MonsterAccounting(subject.spawned.size(), subject.entities.size(), killed, bossKilled, leaked, active, cleanup,
                    missing, uncredited, subject.belowPlatform.size(), subject.outsidePlatform.size(), List.copyOf(diagnostics));
        }

        private void tickBattle() {
            subjects.forEach(subject -> {
                capture(subject);
                subject.activeBeforeTick = subject.lane.activeMonsters().stream().map(Monster::logicalId).collect(java.util.stream.Collectors.toUnmodifiableSet());
            });
            game.tick(level.getServer());
            roundTicks++;
            totalCombatTicks++;
            subjects.forEach(this::capture);
            if (game.phase() == RoundPhase.LANE_WAVE && roundTicks < ROUND_TICKS) {
                context.runAfterDelay(1, () -> safely(this::tickBattle));
                return;
            }
            require(towerHash.equals(hash(TowerBalanceRuntime.current())), "Tower configuration changed during the comparison.");
            for (Subject subject : subjects) {
                var team = game.teams().get(subject.player.teamId());
                String status = game.phase() == RoundPhase.LANE_WAVE ? "CENSORED_TIME_LIMIT"
                        : team.eliminated() ? "TEAM_ELIMINATED" : "OBSERVED_ROUND_ENDPOINT";
                // Victory serializes and clears the lane's monster ledger during this tick.
                var saved = game.phase() == RoundPhase.ENDED ? game.matchResult().orElseThrow().participants().stream()
                        .filter(participant -> participant.playerId().equals(subject.player.uuid())).findFirst().orElseThrow()
                        .roundMetrics().stream().filter(metrics -> metrics.round() == round).findFirst().orElseThrow() : null;
                subject.battles.add(new Battle(round, status, roundTicks, subject.expectedNaturalSpawns,
                        (int) subject.spawned.values().stream().filter(monster -> monster.origin() == MonsterOrigin.NATURAL_WAVE).count(),
                        subject.initialNaturalHealth,
                        (int) subject.spawned.values().stream().filter(monster -> monster.origin() == MonsterOrigin.NORMAL_PAID).count(),
                        subject.spawned.values().stream().filter(monster -> monster.waveHealing() != null).count(),
                        subject.lane.leakedCountThisRound(), subject.lane.leakedThreatThisRound(), subject.lane.clearedThisRound() && !team.eliminated(),
                        subject.lane.laneDefenseBroken(), !team.eliminated(), team.laneGroup().boss().health(),
                        subject.lane.activeMonsters().stream().filter(monster -> !monster.isRemoved() && monster.health() > 0).count(), subject.firstClearTick,
                        subject.player.matchStats().monsterKills() - subject.killsAtWaveStart, subject.lane.roundTowerMetrics(),
                        saved == null ? subject.lane.waveSupportMetrics() : saved.naturalWaveMetrics(),
                        saved == null ? subject.lane.naturalWaveSupportMetrics() : saved.waveSupportMetrics(),
                        subject.lane.clearedThisRound(), accounting(subject)));
            }
            if (subjects.stream().anyMatch(subject -> !subject.battles.getLast().accounting().valid())) {
                report("FIXTURE_ACCOUNTING_INVALID");
                throw new AssertionError("Unaccounted monster removal or off-platform movement invalidates this balance observation.");
            }
            if (round == 15 && game.phase() == RoundPhase.ROUND_PAYOUT) {
                List<Balance> before = subjects.stream().map(subject -> balance(subject.player)).toList();
                game.tick(level.getServer());
                require(game.currentRound() == 16 && game.phase() == RoundPhase.PREPARE_AND_SUMMON, "Only the real R15 payout may advance to R16.");
                for (int index = 0; index < subjects.size(); index++) {
                    Subject subject = subjects.get(index);
                    subject.payouts.add(new Payment(15, before.get(index).income(), subject.player.economy().diamond() - before.get(index).diamonds()));
                    reinvest(subject);
                }
                round = 16;
                advancePreparation();
                context.runAfterDelay(1, () -> safely(this::tickBattle));
                return;
            }
            report("INTERNAL_OBSERVATION_ONLY");
            require(subjects.stream().flatMap(subject -> subject.battles.stream()).flatMap(battle -> battle.towers().stream())
                    .mapToDouble(tower -> tower.physicalDamageDealt() + tower.magicDamageDealt()).sum() > 0,
                    "No real tower damage was observed; this cannot count as combat evidence.");
            cleanup();
            if (boardIndex + 1 < BOARDS.size()) { context.runAfterDelay(1, () -> new Run(context, boardIndex + 1).start()); }
            else { context.succeed(); }
        }

        private void report(String status) {
            List<SubjectReport> result = subjects.stream().map(subject -> new SubjectReport(subject.group,
                    subject.player.teamId(), subject.player.laneId(), subject.player.augments().selections(), subject.startingBalance,
                    subject.purchase, List.copyOf(subject.upgrades), List.copyOf(subject.payouts), balance(subject.player),
                    List.copyOf(subject.battles), subject.battles.stream().anyMatch(battle -> battle.round() == 16) ? "OBSERVED" : "NOT_REACHED",
                    subject.player.augmentTelemetry().snapshot().economyEvents())).toList();
            SemionTd.LOGGER.info("SEASON3_ECONOMY_BALANCE_REPORT {}", JSON.toJson(new Report(status, board.id(), "NORMAL", subjects.size(), subjects.stream().mapToInt(subject -> subject.slots.size()).sum(),
                    towerHash, hash(augments), hash(waves), board.towers(), board.priority(), LIMITS, round, totalCombatTicks,
                    started == 0 ? 0 : (System.nanoTime() - started) / 1_000_000_000.0, result)));
        }

        private BlockPos teamBase(TeamId team) { return origin.offset(team == TeamId.RED ? 0 : 96, 1, 0); }
        private BlockPos laneBase(TeamId team, int lane) { return teamBase(team).offset((lane - 1) * 28, 0, 0); }

        private GameArena buildArena() {
            Map<TeamId, TeamArena> arenas = new EnumMap<>(TeamId.class);
            for (TeamId team : List.of(TeamId.RED, TeamId.BLUE)) {
                BlockPos base = teamBase(team);
                for (int x = (base.getX() - FLOOR_MARGIN) >> 4; x <= (base.getX() + 46 + FLOOR_MARGIN) >> 4; x++) {
                    for (int z = (base.getZ() - FLOOR_MARGIN) >> 4; z <= (base.getZ() + 46 + FLOOR_MARGIN) >> 4; z++) {
                        long key = ChunkPos.asLong(x, z);
                        if (!level.getForceLoadedChunks().contains(key)) { level.setChunkForced(x, z, true); forcedChunks.add(key); }
                        level.getChunk(x, z);
                    }
                }
                for (int x = -FLOOR_MARGIN; x <= 46 + FLOOR_MARGIN; x++) {
                    for (int z = -FLOOR_MARGIN; z <= 46 + FLOOR_MARGIN; z++) {
                        BlockPos position = base.offset(x, 0, z);
                        require(level.getBlockState(position).isAir(), "Economy arena must not overwrite existing blocks.");
                        floor.add(position);
                        level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                    }
                }
                Vec3 boss = Vec3.atBottomCenterOf(base.offset(23, 1, 44));
                Map<Integer, LaneRegionLayout> lanes = new LinkedHashMap<>();
                for (int lane = 1; lane <= 2; lane++) {
                    BlockPos laneBase = laneBase(team, lane);
                    List<GridPosition> finalSlots = new ArrayList<>();
                    for (int slot = 0; slot < board.towers().size(); slot++) {
                        finalSlots.add(GridPosition.from(base.offset(17 + (lane - 1) * 7 + slot % 3 * 2, 0, 36 + slot / 3 * 2)));
                    }
                    lanes.put(lane, new LaneRegionLayout(lane, Vec3.atBottomCenterOf(laneBase.offset(9, 1, 2)),
                            BlockBounds.of(laneBase.offset(7, 1, 1), laneBase.offset(11, 1, 2)),
                            List.of(Vec3.atBottomCenterOf(laneBase.offset(9, 1, 14)), Vec3.atBottomCenterOf(laneBase.offset(9, 1, 28)),
                                    Vec3.atBottomCenterOf(base.offset(23, 1, 39))), boss,
                            BlockBounds.of(laneBase.offset(0, 1, 0), laneBase.offset(17, 6, 31)), finalSlots, 2));
                }
                arenas.put(team, new TeamArena(team, () -> {}, level, new ArenaLayout(boss, boss, lanes)));
            }
            return new GameArena(arenas);
        }

        private void startAtCheckpointRound(int checkpoint) {
            try {
                var current = SemionGame.class.getDeclaredField("currentRound");
                current.setAccessible(true);
                current.setInt(game, checkpoint);
                var start = SemionGame.class.getDeclaredMethod("startPreparePhase", MinecraftServer.class);
                start.setAccessible(true);
                start.invoke(game, level.getServer());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot initialize a checkpoint without fabricating a payout.", failure);
            }
        }

        private void safely(Runnable action) {
            try { action.run(); }
            catch (RuntimeException | AssertionError failure) {
                report("FIXTURE_FAILED_PARTIAL");
                cleanup();
                context.fail(Component.literal("Season 3 economy comparison failed: " + failure));
            }
        }

        private void cleanup() {
            if (game != null) { game.close(); }
            for (BlockPos position : floor) { level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()); }
            floor.clear();
            for (long key : forcedChunks) { level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false); }
            forcedChunks.clear();
        }
    }

    private static Balance balance(SemionPlayer player) {
        return new Balance(player.economy().diamond(), player.economy().emerald(), player.economy().income(), player.economy().emeraldPerSec());
    }

    private static String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJson(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
