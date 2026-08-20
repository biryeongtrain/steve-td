package kim.biryeong.semiontd.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.summon.SummonResult;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tutorial.TutorialService;
import kim.biryeong.semiontd.ui.SemionHudTextService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;

public final class SemionTutorialGameTest {
    @GameTest
    public void koreanTutorialCommandTreeExposesPlayerFlow(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var tutorial = dispatcher.getRoot().getChild("튜토리얼");
        if (!assertTrue(context, tutorial != null && tutorial.getCommand() != null, "Expected bare /튜토리얼 command.")) {
            return;
        }
        for (String child : List.of("상태", "완료", "다시", "종료")) {
            if (!assertTrue(context, tutorial.getChild(child) != null, "Expected /튜토리얼 " + child + " command.")) {
                return;
            }
        }
        var source = context.getLevel().getServer().createCommandSourceStack();
        for (String command : List.of("튜토리얼", "튜토리얼 상태", "튜토리얼 완료", "튜토리얼 다시", "튜토리얼 종료")) {
            var parsed = dispatcher.parse(command, source);
            if (!assertTrue(context, !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "Expected complete parse: /" + command)) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void tutorialUsesIndependentPaidSessionAndRequiresEveryAction(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var player = context.makeMockServerPlayerInLevel();
            var playerId = player.getUUID();
            GameArena arena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));

            if (!assertEquals(
                    context,
                    SemionGameManager.TutorialStartResult.STARTED,
                    manager.startTutorial(server, playerId, player.getGameProfile().getName(), arena),
                    "Tutorial should start."
            )) {
                return;
            }
            SemionGame tutorial = manager.tutorialGame(playerId).orElseThrow();
            if (!assertTrue(context, manager.sandboxGame(playerId).isEmpty(), "Tutorial must not register in sandboxGames.")) {
                return;
            }
            if (!assertTrue(context, tutorial.isTutorialMode() && !tutorial.isSandboxMode(), "Tutorial and sandbox mode flags must stay separate.")) {
                return;
            }
            if (!assertTrue(context, !tutorial.summonsAreFree(), "Tutorial income monsters must use real emerald and income rules.")) {
                return;
            }
            if (!assertEquals(context, AnimalTowerJob.ID, tutorial.players().get(playerId).job().orElseThrow().id(), "Tutorial should use the animal builder.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    EconomyConfig.defaultConfig().startingDiamond() + TutorialService.TRAINING_DIAMONDS,
                    tutorial.players().get(playerId).economy().diamond(),
                    "Tutorial should add only its declared training diamond grant."
            )) {
                return;
            }
            if (!assertEquals(context, "견제 소환", player.getInventory().getItem(1).get(DataComponents.CUSTOM_NAME).getString(), "Tutorial should use the paid match summon tool.")) {
                return;
            }
            if (!assertTrue(context, !player.getInventory().getItem(2).is(Items.CLOCK), "Tutorial must not expose sandbox round controls.")) {
                return;
            }
            if (!assertStage(context, manager, playerId, TutorialService.Stage.INTRO)) {
                return;
            }
            manager.tick(server);
            if (!assertStage(context, manager, playerId, TutorialService.Stage.INTRO)) {
                return;
            }
            if (!tickUntilStage(manager, server, playerId, TutorialService.Stage.PLACE_PIG, 400)) {
                context.fail(Component.literal("Game introduction should finish before the first tower task."));
                return;
            }
            tickMany(manager, server, 51);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.DIAMOND)) {
                return;
            }

            PlayerLane lane = tutorial.playerLane(playerId).orElseThrow();
            BlockPos wrongWolfBlock = BlockPos.containing(lane.laneLayout().positionAt(0.05));
            BlockPos pigBlock = BlockPos.containing(lane.laneLayout().positionAt(0.30));
            BlockPos wolfBlock = BlockPos.containing(lane.laneLayout().positionAt(0.75));
            if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(
                    tutorial, playerId, pigBlock, AnimalTowers.T1_PIG_TOWER.id()
            ), "Player should place the real pig tank.")) {
                return;
            }
            manager.tick(server);
            if (!assertStage(context, manager, playerId, TutorialService.Stage.PLACE_PIG)) {
                return;
            }
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.DIAMOND)) {
                return;
            }
            if (!tickUntilStage(manager, server, playerId, TutorialService.Stage.PLACE_WOLF, 400)) {
                context.fail(Component.literal("Pig placement should advance after its paced narration."));
                return;
            }

            if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(
                    tutorial, playerId, wrongWolfBlock, AnimalTowers.T1_WOLF_TOWER.id()
            ), "Player should be able to make a real placement mistake.")) {
                return;
            }
            tickMany(manager, server, 300);
            if (!assertStage(context, manager, playerId, TutorialService.Stage.PLACE_WOLF)) {
                return;
            }
            GridPosition wrongWolfPosition = lane.towers().stream()
                    .filter(tower -> tower.type().id().equals(AnimalTowers.T1_WOLF_TOWER.id()))
                    .findFirst()
                    .orElseThrow()
                    .managementPosition();
            if (!assertEquals(context, TowerSellResult.SUCCESS, ProductionTowerService.sellTower(
                    tutorial, playerId, wrongWolfPosition
            ).result(), "The misplaced wolf should be removable.")) {
                return;
            }
            if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(
                    tutorial, playerId, wolfBlock, AnimalTowers.T1_WOLF_TOWER.id()
            ), "Player should place the wolf behind the pig.")) {
                return;
            }
            if (!tickUntilStage(manager, server, playerId, TutorialService.Stage.DEFEND_FIRST_WAVE, 20)) {
                context.fail(Component.literal("Pig-front and wolf-back placement should advance the tutorial."));
                return;
            }

            if (!enterWaveAfterTutorialCountdown(context, manager, server, tutorial)) {
                return;
            }
            manager.tick(server);
            Monster escapedMonster = lane.activeMonsters().getFirst();
            escapedMonster.syncLaneProgress(1.0);
            lane.tick(server, null, tutorial.players());
            if (!assertTrue(context, lane.leakedThisRound(), "A monster crossing the lane should count as a defense failure.")) {
                return;
            }
            lane.disableMonsters();
            manager.tick(server);
            manager.tick(server);
            if (!assertEquals(context, 1, tutorial.currentRound(), "Failed first defense should restart round 1.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, tutorial.phase(), "Retry should return to preparation.")) {
                return;
            }
            if (!assertEquals(context, 2, lane.towers().size(), "Retry should keep placed towers so the player can add more.")) {
                return;
            }
            if (!assertStage(context, manager, playerId, TutorialService.Stage.DEFEND_FIRST_WAVE)) {
                return;
            }

            tickMany(manager, server, 260);
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, tutorial.phase(), "Failed defense should keep the normal preparation time.")) {
                return;
            }
            if (!assertTrue(context, tutorial.remainingPrepareSeconds() > 3, "Failed defense should leave time to place more towers.")) {
                return;
            }
            enterWave(manager, server, tutorial);
            lane.disableMonsters();
            manager.tick(server);
            manager.tick(server);
            if (!assertStage(context, manager, playerId, TutorialService.Stage.UPGRADE_TOWER)) {
                return;
            }
            tickMany(manager, server, 51);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.DIAMOND)) {
                return;
            }
            List<SemionTowerEntity> upgradeTargets = towerEntities(lane);
            if (!assertEquals(context, lane.towers().size(), upgradeTargets.size(), "Every tutorial tower should have a visible entity.")) {
                return;
            }
            if (!assertTrue(context, upgradeTargets.stream().allMatch(SemionTowerEntity::isCurrentlyGlowing), "Tower entities should glow during the upgrade explanation.")) {
                return;
            }

            GridPosition towerPosition = lane.towers().stream()
                    .filter(tower -> tower.type().id().equals(AnimalTowers.T1_WOLF_TOWER.id()))
                    .findFirst()
                    .orElseThrow()
                    .managementPosition();
            var upgrade = ProductionTowerService.availableUpgrades(tutorial, playerId, towerPosition).getFirst();
            if (!assertEquals(context, TowerUpgradeResult.SUCCESS, ProductionTowerService.upgradeTower(tutorial, playerId, towerPosition, upgrade.id()), "Player should upgrade the placed tutorial tower.")) {
                return;
            }
            if (!tickUntilStage(manager, server, playerId, TutorialService.Stage.BUY_INCOME_MONSTER, 300)) {
                context.fail(Component.literal("Tower upgrade should advance after its paced narration."));
                return;
            }
            tickMany(manager, server, 51);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.EMERALD)) {
                return;
            }
            if (!assertTrue(context, towerEntities(lane).stream().noneMatch(SemionTowerEntity::isCurrentlyGlowing), "Tower glow should clear after the upgrade task.")) {
                return;
            }
            tickMany(manager, server, 51);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.EMERALD_RATE)) {
                return;
            }

            var economy = tutorial.players().get(playerId).economy();
            var chicken = tutorial.summonShop().find("chicken").orElseThrow();
            long emeraldBefore = economy.emerald();
            long incomeBefore = economy.income();
            SummonResult summon = tutorial.summonMonster(playerId, chicken.id());
            if (!assertEquals(context, SummonResultType.SUCCESS, summon.type(), "Player should buy a tutorial income monster.")) {
                return;
            }
            if (!assertEquals(context, emeraldBefore - chicken.gasCost(), economy.emerald(), "Tutorial income purchase should spend emerald.")) {
                return;
            }
            if (!assertEquals(context, incomeBefore + chicken.incomeGain(), economy.income(), "Tutorial income purchase should raise round income.")) {
                return;
            }
            if (!assertTrue(context, summon.targetTeam().filter(team -> team == tutorial.players().get(playerId).teamId()).isPresent(), "Tutorial income monster should route to the learner's lane.")) {
                return;
            }
            if (!assertEquals(context, 1, lane.queuedSummonCount(), "Tutorial income monster should enter the learner's wave queue.")) {
                return;
            }
            tickMany(manager, server, 102);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.EMERALD)) {
                return;
            }
            if (!tickUntilStage(manager, server, playerId, TutorialService.Stage.LEARN_INCOME, 400)) {
                context.fail(Component.literal("Income purchase should reveal the income explanation."));
                return;
            }
            tickMany(manager, server, 51);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.INCOME)) {
                return;
            }
            if (!tickUntilStage(manager, server, playerId, TutorialService.Stage.UPGRADE_EMERALD_PRODUCTION, 400)) {
                context.fail(Component.literal("Income explanation should finish before the production upgrade task."));
                return;
            }
            tickMany(manager, server, 51);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.EMERALD_RATE)) {
                return;
            }
            tickMany(manager, server, 102);
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.DIAMOND)) {
                return;
            }

            long diamondBeforeUpgrade = economy.diamond();
            long productionBefore = economy.emeraldPerSec();
            long productionCost = tutorial.economyConfig().gasProduction().upgradeCost(economy.emeraldProductionUpgradeCount());
            if (!assertTrue(context, tutorial.upgradeGasProduction(playerId), "Player should buy the diamond-funded income upgrade.")) {
                return;
            }
            if (!assertEquals(context, diamondBeforeUpgrade - productionCost, economy.diamond(), "Income upgrade should spend diamond.")) {
                return;
            }
            if (!assertEquals(context, productionBefore + tutorial.economyConfig().gasProduction().emeraldPerSecIncrease(), economy.emeraldPerSec(), "Income upgrade should raise emerald production.")) {
                return;
            }
            if (!tickUntilStage(manager, server, playerId, TutorialService.Stage.DEFEND_INCOME_MONSTER, 350)) {
                context.fail(Component.literal("Income upgrade should advance after its paced narration."));
                return;
            }

            if (!enterWaveAfterTutorialCountdown(context, manager, server, tutorial)) {
                return;
            }
            int finalDefenseRound = tutorial.currentRound();
            lane.disableMonsters();
            manager.tick(server);
            manager.tick(server);
            if (!assertStage(context, manager, playerId, TutorialService.Stage.FINAL_DEFENSE)) {
                return;
            }
            if (!assertEquals(context, RoundPhase.ROUND_PAYOUT, tutorial.phase(), "Final-defense explanation should hold the completed round before payout.")) {
                return;
            }
            if (!assertEquals(context, finalDefenseRound, tutorial.currentRound(), "Final-defense explanation must not advance the round.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.towers().stream().allMatch(tower -> tower.deployedAtFinalDefense()),
                    "Surviving tutorial towers should move to the final defense line."
            )) {
                return;
            }
            List<SemionTowerEntity> finalDefenseTowers = towerEntities(lane);
            if (!assertEquals(context, lane.towers().size(), finalDefenseTowers.size(), "Every surviving tutorial tower should remain visible at final defense.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    finalDefenseTowers.stream().allMatch(entity -> lane.laneLayout().isInsideFinalDefenseTowerArea(entity.position())),
                    "Tutorial tower entities should stand inside the final defense area."
            )) {
                return;
            }
            if (!assertTrue(context, finalDefenseTowers.stream().allMatch(SemionTowerEntity::isCurrentlyGlowing), "Final-defense tower entities should glow.")) {
                return;
            }
            var team = tutorial.teams().get(tutorial.players().get(playerId).teamId());
            if (!assertTrue(context, team.laneGroup().bossEntity().orElseThrow().isCurrentlyGlowing(), "The team boss should glow during the final-defense explanation.")) {
                return;
            }
            if (!assertTrue(context, !manager.completeTutorial(server, playerId), "Final confirmation should wait until every explanation line is shown.")) {
                return;
            }
            tickMany(manager, server, 153);
            if (!assertEquals(context, finalDefenseRound, tutorial.currentRound(), "The round should remain fixed throughout the final-defense narration.")) {
                return;
            }
            if (!assertHighlight(context, manager, playerId, TutorialService.HighlightTarget.BOSS_HEALTH)) {
                return;
            }
            String bossHealth = Math.round(team.laneGroup().boss().health())
                    + "/"
                    + Math.round(team.laneGroup().boss().maxHealth());
            String highlightedSidebar = SemionHudTextService.matchSidebarMarkupFor(
                    playerId,
                    Optional.of(team),
                    tutorial,
                    MatchMode.TEST,
                    TutorialService.HighlightTarget.BOSS_HEALTH,
                    true
            );
            if (!assertTrue(
                    context,
                    highlightedSidebar.contains("<white><bold>" + bossHealth + "</bold></white>"),
                    "Tutorial sidebar should highlight the team boss health."
            )) {
                return;
            }
            boolean completed = false;
            for (int tick = 0; tick < 500 && !completed; tick++) {
                manager.tick(server);
                completed = manager.completeTutorial(server, playerId);
            }
            if (!assertTrue(context, completed, "Player should confirm the final-defense explanation after the paced narration.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    manager.tutorialGame(playerId).isEmpty()
                            && !manager.isTutorialActive(playerId)
                            && manager.tutorialStage(playerId).isEmpty()
                            && manager.tutorialHighlight(playerId) == TutorialService.HighlightTarget.NONE,
                    "Completion should immediately remove the tutorial session and game."
            )) {
                return;
            }

            if (!assertEquals(
                    context,
                    SemionGameManager.SandboxStartResult.STARTED,
                    manager.startSandbox(
                            server,
                            playerId,
                            player.getGameProfile().getName(),
                            SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(60, 0, 0)))
                    ),
                    "Starting sandbox after completion should create a fresh practice session."
            )) {
                return;
            }
            if (!assertTrue(context, manager.tutorialGame(playerId).isEmpty() && manager.sandboxGame(playerId).isPresent(), "Sandbox should own only sandboxGames after tutorial completion.")) {
                return;
            }
            SemionGame sandbox = manager.sandboxGame(playerId).orElseThrow();
            if (!assertTrue(context, sandbox.isSandboxMode() && !sandbox.isTutorialMode() && sandbox.summonsAreFree(), "Existing sandbox rules must remain intact.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Tutorial flow test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    private static boolean enterWaveAfterTutorialCountdown(
            GameTestHelper context,
            SemionGameManager manager,
            MinecraftServer server,
            SemionGame game
    ) {
        int countdownStart = SemionGame.DEFAULT_PREPARE_TICKS - 3 * 20;
        for (int tick = 0; tick < 400 && game.phaseTicks() != countdownStart; tick++) {
            manager.tick(server);
        }
        if (!assertEquals(context, countdownStart, game.phaseTicks(), "Tutorial narration should start a three-second countdown.")) {
            return false;
        }
        tickMany(manager, server, 59);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Tutorial wave should wait the full three seconds.")) {
            return false;
        }
        manager.tick(server);
        return assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Tutorial wave should start after three seconds.");
    }

    private static void enterWave(SemionGameManager manager, MinecraftServer server, SemionGame game) {
        for (int tick = 0; tick < SemionGame.DEFAULT_PREPARE_TICKS + 500 && game.phase() == RoundPhase.PREPARE_AND_SUMMON; tick++) {
            manager.tick(server);
        }
    }

    private static void tickMany(SemionGameManager manager, MinecraftServer server, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            manager.tick(server);
        }
    }

    private static boolean tickUntilStage(
            SemionGameManager manager,
            MinecraftServer server,
            java.util.UUID playerId,
            TutorialService.Stage expected,
            int maxTicks
    ) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (manager.tutorialStage(playerId).orElse(null) == expected) {
                return true;
            }
            manager.tick(server);
        }
        return manager.tutorialStage(playerId).orElse(null) == expected;
    }

    private static void configureManager(SemionGameManager manager) throws Exception {
        Path tempDir = Files.createTempDirectory("semion-tutorial-gametest");
        manager.configure(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                TowerBalanceConfig.defaultConfig(),
                SummonConfig.defaultConfig(),
                tempDir.resolve("progression.json")
        );
    }

    private static boolean assertStage(
            GameTestHelper context,
            SemionGameManager manager,
            java.util.UUID playerId,
            TutorialService.Stage expected
    ) {
        return assertEquals(context, expected, manager.tutorialStage(playerId).orElse(null), "Unexpected tutorial stage.");
    }

    private static boolean assertHighlight(
            GameTestHelper context,
            SemionGameManager manager,
            java.util.UUID playerId,
            TutorialService.HighlightTarget expected
    ) {
        return assertEquals(context, expected, manager.tutorialHighlight(playerId), "Unexpected tutorial HUD highlight.");
    }

    private static List<SemionTowerEntity> towerEntities(PlayerLane lane) {
        return lane.towers().stream()
                .filter(EntityBackedTower.class::isInstance)
                .map(EntityBackedTower.class::cast)
                .flatMap(tower -> tower.entityId().stream().mapToObj(lane.arenaWorld()::getEntity))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast)
                .toList();
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static <T> boolean assertEquals(GameTestHelper context, T expected, T actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            context.fail(Component.literal(message + " expected=" + expected + " actual=" + actual));
            return false;
        }
        return true;
    }
}
