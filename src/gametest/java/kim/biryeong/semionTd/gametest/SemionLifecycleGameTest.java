package kim.biryeong.semionTd.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semionTd.config.EconomyConfig;
import kim.biryeong.semionTd.config.MapConfig;
import kim.biryeong.semionTd.config.ProgressionConfig;
import kim.biryeong.semionTd.config.SummonConfig;
import kim.biryeong.semionTd.config.WaveConfig;
import kim.biryeong.semionTd.game.AssignedParticipant;
import kim.biryeong.semionTd.game.MatchMode;
import kim.biryeong.semionTd.game.MatchParticipantResult;
import kim.biryeong.semionTd.game.MatchResult;
import kim.biryeong.semionTd.game.ParticipantSelectionPlan;
import kim.biryeong.semionTd.game.RoundPhase;
import kim.biryeong.semionTd.game.SemionGame;
import kim.biryeong.semionTd.game.SemionGameManager;
import kim.biryeong.semionTd.game.TeamId;
import kim.biryeong.semionTd.job.JobContext;
import kim.biryeong.semionTd.job.JobRegistry;
import kim.biryeong.semionTd.job.SemionJob;
import kim.biryeong.semionTd.map.LobbyWorld;
import kim.biryeong.semionTd.progression.MatchProgressionReward;
import kim.biryeong.semionTd.progression.ProgressionService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class SemionLifecycleGameTest implements CustomTestMethodInvoker {
    private static final SemionJob TEST_RICH_JOB = JobRegistry.register(new SemionJob(
            ResourceLocation.fromNamespaceAndPath("semion-td-test", "rich"),
            Component.literal("Rich Test Job"),
            List.of(Component.literal("Adds starting resources for tests."))
    ) {
        @Override
        public long modifyStartingMineral(JobContext context, long baseMineral) {
            return baseMineral + 77;
        }

        @Override
        public long modifyStartingGas(JobContext context, long baseGas) {
            return baseGas + 3;
        }

        @Override
        public long modifyStartingIncome(JobContext context, long baseIncome) {
            return baseIncome + 4;
        }

        @Override
        public long modifyStartingGasPerSec(JobContext context, long baseGasPerSec) {
            return baseGasPerSec + 2;
        }
    });

    @GameTest
    public void matchResultRecordsWinnerAndLoser(GameTestHelper context) {
        SemionGame game = startedTwoPlayerGame(context, playerId("life-red"), "life-red", playerId("life-blue"), "life-blue");
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the match.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.ENDED, game.phase(), "Game should enter ENDED phase after the last team survives.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertTrue(context, result.isPresent(), "Ended game should expose a match result.")) {
            return;
        }
        if (!assertEquals(context, Set.of(TeamId.RED), result.get().winningTeams(), "RED should be the only winning team.")) {
            return;
        }

        Map<String, Boolean> winnersByName = result.get().participants().stream()
                .collect(java.util.stream.Collectors.toMap(MatchParticipantResult::playerName, MatchParticipantResult::winner));
        if (!assertEquals(context, Boolean.TRUE, winnersByName.get("life-red"), "RED participant should be marked as winner.")) {
            return;
        }
        if (!assertEquals(context, Boolean.FALSE, winnersByName.get("life-blue"), "BLUE participant should be marked as loser.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void eliminatedParticipantsBecomeMatchSpectatorsWithoutPollutingResultSpectators(GameTestHelper context) {
        UUID redId = playerId("spectator-red");
        UUID blueId = playerId("spectator-blue");
        UUID spectatorId = playerId("spectator-waiting");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "spectator-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "spectator-blue", TeamId.BLUE, 1)
                ),
                Set.of(spectatorId),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for spectator lifecycle test.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the spectator lifecycle test.")) {
            return;
        }
        if (!assertEquals(context, 2, game.spectatorCount(), "Initial spectator plus eliminated BLUE player should be match spectators.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertTrue(context, result.isPresent(), "Ended game should expose a match result.")) {
            return;
        }
        if (!assertEquals(context, Set.of(spectatorId), result.get().spectatorIds(), "Only original spectators should be stored as match spectators.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultCarriesPlayerStatsSnapshot(GameTestHelper context) {
        UUID redId = playerId("stats-red");
        UUID blueId = playerId("stats-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, "stats-red", blueId, "stats-blue");

        game.players().get(redId).matchStats().recordMonsterKill(12);
        game.players().get(redId).matchStats().recordSummonedMonster();
        game.players().get(redId).economy().addIncome(5);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the stat snapshot test.")) {
            return;
        }

        MatchParticipantResult redResult = game.matchResult().orElseThrow().participants().stream()
                .filter(participant -> participant.playerId().equals(redId))
                .findFirst()
                .orElseThrow();
        if (!assertEquals(context, 1L, redResult.stats().monsterKills(), "Match result should preserve player kill count.")) {
            return;
        }
        if (!assertEquals(context, 12L, redResult.stats().killMinerals(), "Match result should preserve kill mineral total.")) {
            return;
        }
        if (!assertEquals(context, 1L, redResult.stats().summonedMonsters(), "Match result should preserve summoned monster count.")) {
            return;
        }
        if (!assertEquals(context, 5L, redResult.stats().finalIncome(), "Match result should preserve final income.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void selectedJobAppliesStartingEconomyModifiers(GameTestHelper context) {
        UUID redId = playerId("job-red");
        UUID blueId = playerId("job-blue");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        if (!assertTrue(context, game.selectJob(redId, TEST_RICH_JOB.id()), "Job selection should succeed before roster lock.")) {
            return;
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "job-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "job-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with selected job.")) {
            return;
        }

        var red = game.players().get(redId);
        var blue = game.players().get(blueId);
        if (!assertEquals(context, TEST_RICH_JOB.id(), red.job().orElseThrow().id(), "Selected player should keep the selected job.")) {
            return;
        }
        if (!assertEquals(context, JobRegistry.defaultJob().id(), blue.job().orElseThrow().id(), "Unselected player should receive the default job.")) {
            return;
        }
        if (!assertEquals(context, 277L, red.economy().mineral(), "Job should modify starting mineral.")) {
            return;
        }
        if (!assertEquals(context, 53L, red.economy().gas(), "Job should modify starting gas.")) {
            return;
        }
        if (!assertEquals(context, 4L, red.economy().income(), "Job should modify starting income.")) {
            return;
        }
        if (!assertEquals(context, 3L, red.economy().gasPerSec(), "Job should modify starting gas per second.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void progressionServiceWritesProfilesToDedicatedFile(GameTestHelper context) {
        UUID winnerId = playerId("profile-winner");
        UUID loserId = playerId("profile-loser");
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-progression-test").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }
        ProgressionService progressionService = new ProgressionService(new ProgressionConfig(10, 15, 5), storePath);

        MatchResult result = new MatchResult(
                List.of(
                        new MatchParticipantResult(winnerId, "profile-winner", TeamId.RED, true),
                        new MatchParticipantResult(loserId, "profile-loser", TeamId.BLUE, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                4
        );
        Map<UUID, MatchProgressionReward> rewards = progressionService.applyMatchResult(context.getLevel().getServer(), result);

        if (!assertEquals(context, 25L, rewards.get(winnerId).currencyAwarded(), "Winner should receive play reward plus win bonus.")) {
            return;
        }
        if (!assertEquals(context, 15L, rewards.get(loserId).currencyAwarded(), "Loser should receive play reward plus loss reward.")) {
            return;
        }
        if (!assertTrue(context, Files.exists(storePath), "Progression store should be written to a dedicated file.")) {
            return;
        }

        ProgressionService reloaded = new ProgressionService(new ProgressionConfig(10, 15, 5), storePath);
        if (!assertEquals(context, 25L, reloaded.profile(context.getLevel().getServer(), winnerId, "profile-winner").cosmeticCurrency(), "Winner cosmetic currency should survive service reload.")) {
            return;
        }
        if (!assertEquals(context, 15L, reloaded.profile(context.getLevel().getServer(), loserId, "profile-loser").cosmeticCurrency(), "Loser cosmetic currency should survive service reload.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gameManagerFinalizesEndedMatchAndClearsActiveGame(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("manager-red");
        UUID blueId = playerId("manager-blue");
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-progression-test").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary manager progression store path."));
            return;
        }
        manager.configure(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                MapConfig.defaultConfig(),
                SummonConfig.defaultConfig(),
                new ProgressionConfig(10, 15, 5),
                storePath
        );

        SemionGame game = startedTwoPlayerGame(context, redId, "manager-red", blueId, "manager-blue");
        setField(manager, "activeGame", game);
        setField(manager, "lobbyWorld", new LobbyWorld(() -> {
        }, context.getLevel(), new Vec3(0.5, 2.0, 0.5)));

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to end the manager test match.")) {
            return;
        }

        manager.tick(server);

        if (!assertTrue(context, manager.activeGame().isEmpty(), "Game manager should clear the active game after finalization.")) {
            return;
        }
        if (!assertTrue(context, manager.lastMatchResult().isPresent(), "Game manager should retain the last match result after finalization.")) {
            return;
        }
        if (!assertEquals(context, Set.of(TeamId.RED), manager.lastMatchResult().orElseThrow().winningTeams(), "Last match result should preserve the winning team.")) {
            return;
        }
        if (!assertEquals(context, 25L, manager.profile(server, redId, "manager-red").cosmeticCurrency(), "Winner reward should be written during finalization.")) {
            return;
        }
        if (!assertEquals(context, 15L, manager.profile(server, blueId, "manager-blue").cosmeticCurrency(), "Loser reward should be written during finalization.")) {
            return;
        }
        if (!assertTrue(context, Files.exists(storePath), "Manager finalization should persist progression in the dedicated file.")) {
            return;
        }
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        context.setBlock(0, 0, 0, Blocks.AIR);
        method.invoke(this, context);
    }

    private static SemionGame startedTwoPlayerGame(
            GameTestHelper context,
            UUID redId,
            String redName,
            UUID blueId,
            String blueName
    ) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, redName, TeamId.RED, 1),
                        new AssignedParticipant(blueId, blueName, TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start synthetic two-player game for lifecycle test.");
        }
        return game;
    }

    private static UUID playerId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to set field " + fieldName + ".", exception);
        }
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            context.fail(Component.literal(message + " expected=" + expected + " actual=" + actual));
            return false;
        }
        return true;
    }
}
