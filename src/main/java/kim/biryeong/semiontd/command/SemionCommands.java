package kim.biryeong.semiontd.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.game.*;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.map.ArenaLoadException;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonResult;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.test.TestTowerService;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.ui.SemionText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SemionCommands {
    private SemionCommands() {
    }

    private enum DebugBuildGuideView {
        LIST,
        DETAIL,
        TOWER_UI
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> SemionText.prefixedPlain(message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(SemionText.prefixedError(message));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, SemionGameManager gameManager) {
        dispatcher.register(literal("semiontd")
                .then(literal("create")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> createGame(context.getSource(), gameManager)))
                .then(literal("start")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> startGame(context.getSource(), gameManager)))
                .then(literal("end")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetGame(context.getSource(), gameManager, "강제 종료")))
                .then(literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetGame(context.getSource(), gameManager, "리셋")))
                .then(literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reloadConfigs(context.getSource(), gameManager)))
                .then(literal("testmode")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setTestMode(
                                        context.getSource(),
                                        gameManager,
                                        BoolArgumentType.getBool(context, "enabled")
                                ))))
                .then(literal("autojoin")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> autojoin(context.getSource(), gameManager)))
                .then(literal("ready")
                        .executes(context -> ready(context.getSource(), gameManager)))
                .then(literal("unready")
                        .executes(context -> unready(context.getSource(), gameManager)))
                .then(literal("spectate")
                        .executes(context -> spectate(context.getSource(), gameManager))
                        .then(argument("team", StringArgumentType.word())
                                .executes(context -> spectate(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "team")
                                ))))
                .then(literal("economy")
                        .executes(context -> economy(context.getSource(), gameManager)))
                .then(literal("profile")
                        .executes(context -> profile(context.getSource(), gameManager)))
                .then(literal("job")
                        .then(literal("list")
                                .executes(context -> listJobs(context.getSource())))
                        .then(literal("ui")
                                .executes(context -> jobDialog(context.getSource(), gameManager)))
                        .then(literal("current")
                                .executes(context -> currentJob(context.getSource(), gameManager)))
                        .then(literal("select")
                                .then(argument("id", StringArgumentType.string())
                                        .executes(context -> selectJob(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "id")
                                        )))))
                .then(literal("tower")
                        .then(literal("list")
                                .executes(context -> listProductionTowers(context.getSource(), gameManager)))
                        .then(literal("ui")
                                .executes(context -> towerDialog(context.getSource(), gameManager)))
                        .then(literal("build")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(context -> buildProductionTower(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "id")
                                        ))))
                        .then(literal("sell")
                                .executes(context -> sellTower(context.getSource(), gameManager))
                                .then(argument("x", IntegerArgumentType.integer())
                                        .then(argument("y", IntegerArgumentType.integer())
                                                .then(argument("z", IntegerArgumentType.integer())
                                                        .executes(context -> sellTower(
                                                                context.getSource(),
                                                                gameManager,
                                                                new GridPosition(
                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                        IntegerArgumentType.getInteger(context, "z")
                                                                )
                                                        ))))))
                        .then(literal("test")
                                .executes(context -> placeTestTower(context.getSource(), gameManager)))
                        .then(literal("upgrades")
                                .executes(context -> listTowerUpgrades(context.getSource(), gameManager)))
                        .then(literal("upgrade")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(context -> upgradeTower(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "id")
                                        ))
                                        .then(argument("x", IntegerArgumentType.integer())
                                                .then(argument("y", IntegerArgumentType.integer())
                                                        .then(argument("z", IntegerArgumentType.integer())
                                                                .executes(context -> upgradeTower(
                                                                        context.getSource(),
                                                                        gameManager,
                                                                        StringArgumentType.getString(context, "id"),
                                                                        new GridPosition(
                                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                                IntegerArgumentType.getInteger(context, "z")
                                                                        )
                                                                ))))))))
                .then(literal("gasup")
                        .executes(context -> emeraldUp(context.getSource(), gameManager)))
                .then(literal("emeraldup")
                        .executes(context -> emeraldUp(context.getSource(), gameManager)))
                .then(literal("summon")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> summon(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "id")
                                ))))
                .then(literal("summons")
                        .executes(context -> summons(context.getSource(), gameManager)))
                .then(literal("summonui")
                        .executes(context -> summonDialog(context.getSource(), gameManager, 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> summonDialog(
                                        context.getSource(),
                                        gameManager,
                                        IntegerArgumentType.getInteger(context, "page")
                                ))))
                .then(literal("killboss")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("team", StringArgumentType.word())
                                .executes(context -> killBoss(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "team")
                                ))))
                .then(literal("status")
                        .executes(context -> status(context.getSource(), gameManager))
                        .then(literal("teams")
                                .executes(context -> statusTeams(context.getSource(), gameManager)))
                        .then(literal("lanes")
                                .executes(context -> statusLanes(context.getSource(), gameManager)))
                        .then(literal("players")
                                .executes(context -> statusPlayers(context.getSource(), gameManager))))
                .then(literal("ui")
                        .executes(context -> statusDialog(context.getSource(), gameManager))));

        dispatcher.register(literal("직업")
                .executes(context -> jobDialog(context.getSource(), gameManager)));
        dispatcher.register(literal("준비")
                .executes(context -> ready(context.getSource(), gameManager)));
        dispatcher.register(literal("빌드")
                .then(literal("기록")
                        .then(argument("title", StringArgumentType.greedyString())
                                .executes(context -> publishBuild(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "title")
                                ))))
                .then(literal("목록")
                        .executes(context -> buildListDialog(context.getSource(), gameManager))));

        dispatcher.register(literal("semiontd-internal")
                .then(literal("build")
                        .then(literal("list")
                                .then(argument("publicPage", IntegerArgumentType.integer(1))
                                        .then(argument("myPage", IntegerArgumentType.integer(1))
                                                .executes(context -> buildListDialog(
                                                        context.getSource(),
                                                        gameManager,
                                                        IntegerArgumentType.getInteger(context, "publicPage"),
                                                        IntegerArgumentType.getInteger(context, "myPage")
                                                )))))
                        .then(literal("track")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> trackBuild(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code")
                                        ))))
                        .then(literal("detail")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> showBuildDetails(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code")
                                        ))))
                        .then(literal("public")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> setBuildVisibility(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code"),
                                                BuildGuide.VISIBILITY_PUBLIC
                                        ))))
                        .then(literal("private")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> setBuildVisibility(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code"),
                                                BuildGuide.VISIBILITY_PRIVATE
                                        ))))
                        .then(literal("delete")
                                .then(argument("code", StringArgumentType.word())
                                        .executes(context -> deleteBuild(
                                                context.getSource(),
                                                gameManager,
                                                StringArgumentType.getString(context, "code")
                                        ))))
                        .then(literal("clear")
                                .executes(context -> clearTrackedBuild(context.getSource(), gameManager)))));

        dispatcher.register(literal("semiontd-debug")
                .requires(source -> source.hasPermission(2))
                .then(literal("towerui")
                        .executes(context -> debugTowerDialog(context.getSource(), gameManager)))
                .then(literal("tower")
                        .then(literal("ui")
                                .executes(context -> debugTowerDialog(context.getSource(), gameManager))))
                .then(literal("summonui")
                        .executes(context -> debugSummonDialog(context.getSource(), gameManager, 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> debugSummonDialog(
                                        context.getSource(),
                                        gameManager,
                                        IntegerArgumentType.getInteger(context, "page")
                                ))))
                .then(literal("summon")
                        .then(literal("ui")
                                .executes(context -> debugSummonDialog(context.getSource(), gameManager, 1))
                                .then(argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> debugSummonDialog(
                                                context.getSource(),
                                                gameManager,
                                                IntegerArgumentType.getInteger(context, "page")
                                        )))))
                .then(literal("give")
                        .then(literal("diamond")
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> debugGiveCurrency(
                                                context.getSource(),
                                                gameManager,
                                                CurrencyDebugType.DIAMOND,
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                null
                                        ))
                                        .then(argument("player", EntityArgument.player())
                                                .executes(context -> debugGiveCurrency(
                                                        context.getSource(),
                                                        gameManager,
                                                        CurrencyDebugType.DIAMOND,
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        EntityArgument.getPlayer(context, "player")
                                                )))))
                        .then(literal("emerald")
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> debugGiveCurrency(
                                                context.getSource(),
                                                gameManager,
                                                CurrencyDebugType.EMERALD,
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                null
                                        ))
                                        .then(argument("player", EntityArgument.player())
                                                .executes(context -> debugGiveCurrency(
                                                        context.getSource(),
                                                        gameManager,
                                                        CurrencyDebugType.EMERALD,
                                                        IntegerArgumentType.getInteger(context, "amount"),
                                                        EntityArgument.getPlayer(context, "player")
                                                ))))))
                .then(literal("buildguide")
                        .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.LIST))
                        .then(literal("list")
                                .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.LIST)))
                        .then(literal("detail")
                                .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.DETAIL)))
                        .then(literal("towerui")
                                .executes(context -> debugBuildGuideVisual(context.getSource(), gameManager, DebugBuildGuideView.TOWER_UI)))));
    }

    private static int createGame(CommandSourceStack source, SemionGameManager gameManager) {
        try {
            gameManager.createGame(source.getServer());
            success(source, "로비와 아레나를 생성했습니다.");
            return 1;
        } catch (ArenaLoadException exception) {
            failure(source, "아레나 생성 실패: " + exception.getMessage());
            return 0;
        }
    }

    private static int resetGame(CommandSourceStack source, SemionGameManager gameManager, String actionLabel) {
        try {
            boolean hadActiveGame = gameManager.resetToLobby(source.getServer());
            String suffix = hadActiveGame ? "" : " 진행 중인 게임은 없었습니다.";
            success(source, "게임을 " + actionLabel + "하고 모두 로비로 이동했습니다." + suffix);
            return 1;
        } catch (ArenaLoadException exception) {
            failure(source, "로비 이동 실패: " + exception.getMessage());
            return 0;
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.error("Unexpected Semion TD lobby reset failure.", exception);
            failure(source, "로비 이동 중 예기치 못한 오류가 발생했습니다: " + exception.getMessage());
            return 0;
        }
    }

    private static int startGame(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = activeWaitingGame(source, gameManager, "start");
        if (game == null) {
            return 0;
        }

        Optional<ParticipantSelectionPlan> plan = buildSelectionPlan(source, gameManager, game);
        if (plan.isEmpty()) {
            failure(source, "현재 게임 모드로 시작할 준비 완료 인원이 부족합니다.");
            return 0;
        }

        SemionGameManager.StartCountdownResult countdownResult = gameManager.scheduleStart(source.getServer(), plan.get());
        if (countdownResult != SemionGameManager.StartCountdownResult.SCHEDULED) {
            failure(source, startCountdownFailureMessage(countdownResult));
            return 0;
        }

        applyPlanToVanillaTeams(source, plan.get());
        assignUnreadyPlayersToSpectatorTeam(source, game, plan.get());
        String lobbyLoaded = gameManager.lobbyWorld().isPresent() ? ", lobbyLoaded=true" : ", lobbyLoaded=false";
        success(source, "시작 카운트다운을 시작했습니다. 참가자="
                + plan.get().activePlayerCount()
                + ", 팀=" + plan.get().activeTeamCount()
                + ", 구성=" + plan.get().compositionSummary()
                + ", 관전자=" + plan.get().spectatorCount()
                + ", 준비=" + game.readyPlayerCount()
                + ", 모드=" + gameManager.matchMode()
                + ", 카운트다운=" + gameManager.startCountdownSecondsRemaining() + "초"
                + lobbyLoaded);
        return 1;
    }

    private static int reloadConfigs(CommandSourceStack source, SemionGameManager gameManager) {
        try {
            SemionGameManager.ReloadConfigResult result = gameManager.reloadConfigs(source.getServer());
            if (!result.reloaded()) {
                failure(source, "설정 경로를 찾지 못해 리로드하지 못했습니다.");
                return 0;
            }
            String activeGameText = result.activeGameUpdated()
                    ? "진행 중인 게임의 경제/웨이브 설정도 즉시 반영했습니다."
                    : "진행 중인 게임은 없습니다.";
            success(source, "컨픽을 다시 불러왔습니다. " + activeGameText + " 타워 카탈로그는 즉시 갱신되며, 맵 설정은 다음 게임 생성부터 적용됩니다.");
            return 1;
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.error("Unexpected Semion TD config reload failure.", exception);
            failure(source, "컨픽 리로드 중 예기치 못한 오류가 발생했습니다: " + exception.getMessage());
            return 0;
        }
    }

    private static int autojoin(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = activeWaitingGame(source, gameManager, "autojoin");
        if (game == null) {
            return 0;
        }

        Optional<ParticipantSelectionPlan> plan = buildSelectionPlan(source, gameManager, game);
        if (plan.isEmpty()) {
            failure(source, "현재 게임 모드로 팀을 배정할 준비 완료 인원이 부족합니다.");
            return 0;
        }

        applyPlanToVanillaTeams(source, plan.get());
        String lobbyLoaded = gameManager.lobbyWorld().isPresent() ? ", lobbyLoaded=true" : ", lobbyLoaded=false";
        success(source, "다음 시작을 위한 팀을 배정했습니다. 참가자="
                + plan.get().activePlayerCount()
                + ", 팀=" + plan.get().activeTeamCount()
                + ", 구성=" + plan.get().compositionSummary()
                + ", 관전자=" + plan.get().spectatorCount()
                + ", 준비=" + game.readyPlayerCount()
                + ", 모드=" + gameManager.matchMode()
                + lobbyLoaded);
        return plan.get().activePlayerCount();
    }

    private static int ready(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "ready");
        if (game == null) {
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.markReady(player.getUUID())) {
            failure(source, "준비 완료 처리에 실패했습니다.");
            return 0;
        }

        success(source, "준비 완료했습니다. 준비 인원=" + game.readyPlayerCount());
        return 1;
    }

    private static int unready(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "unready");
        if (game == null) {
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.markNotReady(player.getUUID())) {
            failure(source, "준비 해제에 실패했습니다.");
            return 0;
        }

        success(source, "준비를 해제했습니다. 준비 인원=" + game.readyPlayerCount());
        return 1;
    }

    private static int spectate(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.rosterLocked() || game.phase() == RoundPhase.ENDED) {
            failure(source, "관전할 수 있는 진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.addLateSpectator(source.getServer(), player)) {
            failure(source, "현재 상태에서는 관전으로 전환할 수 없습니다.");
            return 0;
        }

        success(source, "게임 관전으로 이동했습니다.");
        return 1;
    }

    private static int spectate(CommandSourceStack source, SemionGameManager gameManager, String teamName) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.rosterLocked() || game.phase() == RoundPhase.ENDED) {
            failure(source, "관전할 수 있는 진행 중인 게임이 없습니다.");
            return 0;
        }

        TeamId targetTeam;
        try {
            targetTeam = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            failure(source, "알 수 없는 팀입니다. red, blue, green, yellow 중 하나를 사용하세요.");
            return 0;
        }

        if (!game.canSpectateTeam(targetTeam)) {
            failure(source, "현재 관전할 수 없는 팀입니다: " + targetTeam.name());
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.addLateSpectator(source.getServer(), player, targetTeam)) {
            failure(source, "현재 상태에서는 관전으로 전환할 수 없습니다.");
            return 0;
        }

        success(source, targetTeam.name() + " 팀 관전으로 이동했습니다.");
        return 1;
    }

    private static int setTestMode(CommandSourceStack source, SemionGameManager gameManager, boolean enabled) {
        SemionGame activeGame = gameManager.activeGame().orElse(null);
        if (activeGame != null && activeGame.rosterLocked()) {
            failure(source, "참가자 확정 후에는 테스트 모드를 변경할 수 없습니다.");
            return 0;
        }

        gameManager.setMatchMode(enabled ? MatchMode.TEST : MatchMode.NORMAL);
        success(source, "테스트 모드=" + enabled);
        return 1;
    }

    private static int status(CommandSourceStack source, SemionGameManager gameManager) {
        return sendStatusLines(source, statusLines(gameManager));
    }

    private static int statusTeams(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        return sendStatusLines(source, teamStatusLines(game));
    }

    private static int statusPlayers(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        return sendStatusLines(source, playerStatusLines(game));
    }

    private static int statusLanes(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        return sendStatusLines(source, laneStatusLines(game));
    }

    private static int sendStatusLines(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            success(source, line);
        }
        return lines.isEmpty() ? 0 : 1;
    }

    public static List<String> statusLines(SemionGameManager gameManager) {
        List<String> lines = new ArrayList<>();
        SemionGame game = gameManager.activeGame().orElse(null);
        boolean lobbyLoaded = gameManager.lobbyWorld().isPresent();
        if (game == null) {
            lines.add("상태 activeGame=false, phase=NONE, matchMode="
                    + gameManager.matchMode()
                    + ", lobbyLoaded=" + lobbyLoaded
                    + ", arenaLoaded=false");
            gameManager.lastMatchResult().ifPresent(result -> lines.add("최근 결과 winners="
                    + winnersText(result)
                    + ", finalRound=" + result.finalRound()
                    + ", participants=" + result.participantCount()
                    + ", spectators=" + result.spectatorIds().size()));
            return lines;
        }

        lines.add("상태 activeGame=true, phase="
                + game.phase()
                + ", round=" + game.currentRound()
                + ", matchMode=" + gameManager.matchMode()
                + ", rosterLocked=" + game.rosterLocked());
        lines.add("운영 상태 ready="
                + game.readyPlayerCount()
                + ", activeParticipants=" + game.players().size()
                + ", spectators=" + game.spectatorCount()
                + ", lobbyLoaded=" + lobbyLoaded
                + ", arenaLoaded=" + loadedArenaCount(game) + "/" + TeamId.values().length);
        lines.addAll(teamStatusLines(game));
        return lines;
    }

    public static List<String> teamStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        for (SemionTeam team : game.teams().values()) {
            lines.add("팀 " + team.id()
                    + " active=" + team.active()
                    + ", eliminated=" + team.eliminated()
                    + ", arenaLoaded=" + game.arena().teamArena(team.id()).isPresent()
                    + ", players=" + team.memberIds().size()
                    + ", lanes=" + team.laneGroup().lanes().size()
                    + ", boss=" + bossHealthStatus(team));
        }
        return lines;
    }

    public static List<String> laneStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        for (SemionTeam team : game.teams().values()) {
            if (!team.active()) {
                continue;
            }
            for (PlayerLane lane : team.laneGroup().lanes()) {
                SemionPlayer owner = game.players().get(lane.ownerPlayer());
                BlockPos areaMin = lane.laneLayout().laneArea().min();
                BlockPos areaMax = lane.laneLayout().laneArea().max();
                BlockPos towerSample = centerBlockPos(areaMin, areaMax);
                lines.add("라인 " + team.id()
                        + "#" + lane.laneId()
                        + " player=" + (owner == null ? lane.ownerPlayer() : owner.name())
                        + ", towerSample=" + blockPosText(towerSample)
                        + ", laneArea=" + blockPosText(areaMin) + ".." + blockPosText(areaMax)
                        + ", monsters=" + lane.activeMonsters().size()
                        + ", towers=" + lane.towers().size());
            }
        }
        if (lines.isEmpty()) {
            lines.add("활성 라인 없음");
        }
        return lines;
    }

    public static List<String> playerStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        if (game.players().isEmpty()) {
            lines.add("참가자 없음");
        } else {
            game.players().values().stream()
                    .sorted(java.util.Comparator.comparing(SemionPlayer::name))
                    .forEach(player -> lines.add("참가자 "
                            + player.name()
                            + " uuid=" + player.uuid()
                            + ", team=" + player.teamId()
                            + ", lane=" + player.laneId()
                            + ", eliminated=" + game.teams().get(player.teamId()).eliminated()));
        }

        List<UUID> spectators = game.matchSpectatorIds().stream()
                .filter(spectatorId -> !game.players().containsKey(spectatorId))
                .sorted()
                .toList();
        if (spectators.isEmpty()) {
            lines.add("관전자 없음");
        } else {
            spectators.forEach(spectatorId -> lines.add("관전자 uuid=" + spectatorId));
        }
        return lines;
    }

    private static int loadedArenaCount(SemionGame game) {
        int loaded = 0;
        for (TeamId teamId : TeamId.values()) {
            if (game.arena().teamArena(teamId).isPresent()) {
                loaded++;
            }
        }
        return loaded;
    }

    private static String bossHealthStatus(SemionTeam team) {
        if (team.eliminated()) {
            return "ELIMINATED";
        }
        return Math.round(team.laneGroup().boss().health())
                + "/"
                + Math.round(team.laneGroup().boss().maxHealth());
    }

    private static String blockPosText(BlockPos blockPos) {
        return blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
    }

    private static BlockPos centerBlockPos(BlockPos min, BlockPos max) {
        return new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2)
        );
    }

    private static String winnersText(MatchResult result) {
        if (result.winningTeams().isEmpty()) {
            return "none";
        }
        return result.winningTeams().stream()
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static int statusDialog(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game != null) {
            gameManager.dialogService().showGameStatus(player, game);
            success(source, "상태 창을 열었습니다.");
            return 1;
        }

        Optional<MatchResult> lastResult = gameManager.lastMatchResult();
        if (lastResult.isPresent()) {
            gameManager.dialogService().showLastResult(player, lastResult.get());
            success(source, "최근 결과 창을 열었습니다.");
            return 1;
        }

        failure(source, "진행 중인 게임이나 최근 결과가 없습니다.");
        return 0;
    }

    private static int economy(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        SemionPlayer player = game.players().get(source.getPlayerOrException().getUUID());
        if (player == null) {
            failure(source, "현재 게임 참가자가 아닙니다.");
            return 0;
        }

        PlayerEconomy economy = player.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        success(source, "경제 다이아=" + economy.diamond()
                + ", 에메랄드=" + economy.emerald()
                + ", 수입=" + economy.income()
                + ", 에메랄드/초=" + economy.emeraldPerSec()
                + ", 생산업글=" + economy.emeraldProductionUpgradeCount()
                + ", 다음업글비용=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대"));
        return 1;
    }

    private enum CurrencyDebugType {
        DIAMOND,
        EMERALD
    }

    private static int debugGiveCurrency(
            CommandSourceStack source,
            SemionGameManager gameManager,
            CurrencyDebugType currency,
            int amount,
            ServerPlayer targetPlayer
    ) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer target = targetPlayer == null ? source.getPlayerOrException() : targetPlayer;
        SemionPlayer semionPlayer = game.players().get(target.getUUID());
        if (semionPlayer == null) {
            failure(source, "현재 게임 참가자가 아닙니다: " + target.getGameProfile().getName());
            return 0;
        }

        PlayerEconomy economy = semionPlayer.economy();
        if (currency == CurrencyDebugType.DIAMOND) {
            economy.addDiamond(amount);
        } else {
            economy.addEmerald(amount);
        }

        success(source, target.getGameProfile().getName()
                + "에게 "
                + (currency == CurrencyDebugType.DIAMOND ? "다이아" : "에메랄드")
                + " "
                + amount
                + "개를 지급했습니다. 현재 다이아="
                + economy.diamond()
                + ", 에메랄드="
                + economy.emerald());
        return 1;
    }

    private static int profile(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var profile = gameManager.profile(source.getServer(), player.getUUID(), player.getGameProfile().getName());
        success(source, "프로필 장식재화=" + profile.cosmeticCurrency()
                + ", 플레이=" + profile.gamesPlayed()
                + ", 승=" + profile.wins()
                + ", 패=" + profile.losses()
                + ", 저장 직업=" + profile.selectedJobResource()
                        .flatMap(JobRegistry::find)
                        .map(job -> job.displayName().getString())
                        .orElse(JobRegistry.defaultJob().displayName().getString()));
        return 1;
    }

    private static int listJobs(CommandSourceStack source) {
        success(source, "직업 목록:");
        for (SemionJob job : JobRegistry.all()) {
            success(source, " - " + job.id() + " => " + job.displayName().getString());
            for (Component line : job.description()) {
                success(source, "   " + line.getString());
            }
        }
        return JobRegistry.all().size();
    }

    private static int currentJob(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "열린 로비가 없습니다. 관리자에게 /semiontd create 실행을 요청하세요.");
            return 0;
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        SemionJob job = semionPlayer != null
                ? semionPlayer.job().orElse(JobRegistry.defaultJob())
                : game.selectedJobOrDefault(player.getUUID());
        success(source, "현재 직업: "
                + job.id()
                + " => "
                + job.displayName().getString());
        return 1;
    }

    private static int jobDialog(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "열린 로비가 없습니다. 관리자에게 /semiontd create 실행을 요청하세요.");
            return 0;
        }
        gameManager.dialogService().showJobSelection(source.getPlayerOrException(), game);
        success(source, "직업 선택 창을 열었습니다.");
        return 1;
    }

    private static int selectJob(CommandSourceStack source, SemionGameManager gameManager, String rawJobId)
            throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "직업 선택");
        if (game == null) {
            return 0;
        }
        if (!game.canConfigureRoster()) {
            failure(source, "참가자 확정 후에는 직업을 선택할 수 없습니다.");
            return 0;
        }

        ResourceLocation jobId;
        try {
            jobId = parseJobId(rawJobId);
        } catch (IllegalArgumentException exception) {
            failure(source, exception.getMessage());
            return 0;
        }
        if (!game.selectJob(source.getPlayerOrException().getUUID(), jobId)) {
            failure(source, "알 수 없는 직업입니다: " + jobId + ". /semiontd job list를 확인하세요.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        SemionJob job = game.selectedJobOrDefault(player.getUUID());
        gameManager.saveSelectedJob(source.getServer(), player.getUUID(), player.getGameProfile().getName(), job.id());
        success(source, "직업을 선택했습니다: "
                + job.id()
                + " => "
                + job.displayName().getString()
                + " (다음 로비부터 자동 적용)");
        return 1;
    }

    private static int emeraldUp(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        UUID playerId = source.getPlayerOrException().getUUID();
        boolean upgraded = game.upgradeGasProduction(playerId);
        if (!upgraded) {
            failure(source, "에메랄드 생산 업그레이드에 실패했습니다.");
            return 0;
        }

        PlayerEconomy economy = game.players().get(playerId).economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        success(source, "에메랄드 생산을 업그레이드했습니다. 에메랄드/초=" + economy.emeraldPerSec()
                + ", 생산업글=" + economy.emeraldProductionUpgradeCount()
                + ", 다음비용=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대"));
        return 1;
    }

    private static int placeTestTower(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerPlacementResult result = TestTowerService.placeTestTower(game, player.getUUID(), player.blockPosition());
        if (result != TowerPlacementResult.SUCCESS) {
            failure(source, "테스트 타워 설치 실패: " + placementFailureMessage(result));
            return 0;
        }

        success(source, "테스트 타워를 설치했습니다: " + player.blockPosition());
        return 1;
    }

    private static int listProductionTowers(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerService.availableTowers(game, player.getUUID());
        if (entries.isEmpty()) {
            failure(source, "사용할 수 있는 타워가 없습니다.");
            return 0;
        }

        success(source, "사용 가능한 타워:");
        for (ProductionTowerCatalog.CatalogEntry entry : entries) {
            success(source, " - " + entry.type().id()
                    + " => " + entry.type().displayName()
                    + " 다이아비용=" + entry.type().mineralCost());
        }
        return entries.size();
    }

    private static int buildProductionTower(CommandSourceStack source, SemionGameManager gameManager, String towerId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerPlacementResult result = ProductionTowerService.placeTower(game, player.getUUID(), player.blockPosition(), towerId);
        if (result != TowerPlacementResult.SUCCESS) {
            failure(source, "타워 설치 실패: " + placementFailureMessage(result));
            return 0;
        }

        success(source, "타워를 설치했습니다: " + towerId + ", 위치=" + player.blockPosition());
        return 1;
    }

    private static int towerDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        gameManager.dialogService().showTowerControl(source.getPlayerOrException(), game, gameManager.buildGuideService());
        success(source, "타워 관리 창을 열었습니다.");
        return 1;
    }

    private static int debugTowerDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        gameManager.dialogService().showDebugTowerControl(source.getPlayerOrException());
        success(source, "디버그 타워 설치 창을 열었습니다.");
        return 1;
    }

    private static int sellTower(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        return sellTower(source, gameManager, null);
    }

    private static int sellTower(CommandSourceStack source, SemionGameManager gameManager, GridPosition position)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ProductionTowerService.SaleResult result = position == null
                ? ProductionTowerService.sellTower(game, player.getUUID(), player.blockPosition())
                : ProductionTowerService.sellTower(game, player.getUUID(), position);
        if (result.result() != TowerSellResult.SUCCESS) {
            failure(source, "타워 판매 실패: " + towerSellFailureMessage(result.result()));
            return 0;
        }

        success(source, "타워를 판매했습니다. 환불 다이아=" + result.refundAmount());
        return 1;
    }


    private static int listTowerUpgrades(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        List<TowerUpgradeOption> upgrades = ProductionTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (upgrades.isEmpty()) {
            failure(source, "해당 위치에 사용 가능한 타워 업그레이드가 없습니다: " + player.blockPosition());
            return 0;
        }

        success(source, "사용 가능한 업그레이드: 위치=" + player.blockPosition());
        for (TowerUpgradeOption option : upgrades) {
            success(source, " - " + option.id()
                    + " => " + option.displayName()
                    + " 다이아비용=" + option.mineralCost());
        }
        return upgrades.size();
    }

    private static int upgradeTower(CommandSourceStack source, SemionGameManager gameManager, String upgradeId)
            throws CommandSyntaxException {
        return upgradeTower(source, gameManager, upgradeId, null);
    }

    private static int upgradeTower(
            CommandSourceStack source,
            SemionGameManager gameManager,
            String upgradeId,
            GridPosition position
    ) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerUpgradeResult result = position == null
                ? ProductionTowerService.upgradeTower(game, player.getUUID(), player.blockPosition(), upgradeId)
                : ProductionTowerService.upgradeTower(game, player.getUUID(), position, upgradeId);
        if (result != TowerUpgradeResult.SUCCESS) {
            failure(source, "타워 업그레이드 실패: " + towerUpgradeFailureMessage(result));
            return 0;
        }

        Object upgradedPosition = position == null ? player.blockPosition() : position;
        success(source, "타워를 업그레이드했습니다: " + upgradedPosition + ", 업그레이드=" + upgradeId);
        return 1;
    }

    private static int summon(CommandSourceStack source, SemionGameManager gameManager, String summonId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        SummonResult result = game.summonMonster(player.getUUID(), summonId);
        if (result.type() != SummonResultType.SUCCESS) {
            failure(source, "소환 실패: " + summonFailureMessage(result.type()));
            return 0;
        }

        int scheduledRound = result.scheduledRound().orElse(game.currentRound());
        if (scheduledRound > game.currentRound()) {
            success(source, "다음 라운드에 소환 예약했습니다: " + summonId
                    + ", 팀=" + result.targetTeam().orElseThrow()
                    + ", 라인=" + result.targetLaneId().orElseThrow()
                    + ", 라운드=" + scheduledRound);
        } else {
            success(source, "소환했습니다: " + summonId
                    + ", 팀=" + result.targetTeam().orElseThrow()
                    + ", 라인=" + result.targetLaneId().orElseThrow());
        }
        gameManager.dialogService().showSummonShop(player, game);
        return 1;
    }

    private static int summons(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        success(source, "소환 목록:");
        for (SummonMonsterType type : game.summonShop().all()) {
            success(source, " - " + type.id()
                    + " 에메랄드비용=" + type.gasCost()
                    + ", 수입=" + type.incomeGain()
                    + ", 체력=" + Math.round(type.maxHealth()));
        }
        return 1;
    }

    private static int summonDialog(CommandSourceStack source, SemionGameManager gameManager, int page)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }
        gameManager.dialogService().showSummonShop(source.getPlayerOrException(), game, page);
        success(source, "견제 소환 창을 열었습니다.");
        return 1;
    }

    private static int debugSummonDialog(CommandSourceStack source, SemionGameManager gameManager, int page)
            throws CommandSyntaxException {
        gameManager.dialogService().showDebugSummonShop(source.getPlayerOrException(), page);
        success(source, "디버그 견제 소환 창을 열었습니다.");
        return 1;
    }

    private static int debugBuildGuideVisual(
            CommandSourceStack source,
            SemionGameManager gameManager,
            DebugBuildGuideView view
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        SemionPlayer semionPlayer = game == null ? null : game.players().get(player.getUUID());
        PlayerLane lane = game == null ? null : game.playerLane(player.getUUID()).orElse(null);
        boolean liveParticipant = game != null && semionPlayer != null && lane != null;

        int currentRound = liveParticipant ? game.currentRound() : 1;
        String jobId = liveParticipant
                ? semionPlayer.job().map(job -> job.id().toString()).orElse("")
                : "semion-td:debug";
        List<BuildAction> actions = liveParticipant
                ? debugBuildGuideActions(game, player.getUUID(), lane, debugTargetPosition(lane, player.blockPosition()))
                : debugBuildGuideActions(currentRound, GridPosition.from(player.blockPosition()));
        BuildGuide guide = saveDebugBuildGuideSamples(gameManager, player, jobId, currentRound, actions);
        gameManager.trackBuild(source.getServer(), player, guide.code());
        if (liveParticipant) {
            gameManager.buildGuideService().onPreparePhaseStarted(source.getServer(), game, game.currentRound());
        }

        if (view == DebugBuildGuideView.DETAIL) {
            gameManager.dialogService().showBuildGuideDetails(player, guide);
            success(source, "임시 빌드 상세 UI를 열었습니다. 코드=" + guide.code());
        } else if (view == DebugBuildGuideView.TOWER_UI) {
            if (liveParticipant) {
                gameManager.dialogService().showTowerControl(player, game, gameManager.buildGuideService());
                success(source, "디버그 빌드 가이드를 추적하고 타워 UI를 열었습니다. 추천 버튼은 파란색으로 표시됩니다. 코드=" + guide.code());
            } else {
                gameManager.dialogService().showDebugTowerControl(player);
                success(source, "경기 밖이라 디버그 타워 UI를 열었습니다. 빌드 목록/상세 임시 데이터도 준비했습니다. 코드=" + guide.code());
            }
        } else {
            gameManager.showDebugBuildList(player);
            String suffix = liveParticipant ? " 파티클/라운드 안내도 표시했습니다." : "";
            success(source, "임시 데이터가 들어간 빌드 목록 UI를 열었습니다." + suffix + " 코드=" + guide.code());
        }
        return 1;
    }

    private static BuildGuide saveDebugBuildGuideSamples(
            SemionGameManager gameManager,
            ServerPlayer player,
            String jobId,
            int currentRound,
            List<BuildAction> primaryActions
    ) {
        BuildGuide primary = gameManager.buildGuideService().saveDebugGuide(
                "DEBUG1",
                "디버그 추천 빌드",
                player.getUUID(),
                player.getGameProfile().getName(),
                jobId,
                currentRound + 3,
                primaryActions
        );
        gameManager.buildGuideService().saveDebugGuide(
                "DEBUG2",
                "초반 인컴 압박 빌드",
                player.getUUID(),
                player.getGameProfile().getName(),
                jobId,
                currentRound + 5,
                List.of(
                        BuildAction.towerPlace(currentRound, "sample_income_tower", new GridPosition(2, 64, 2), 75),
                        BuildAction.summon(currentRound, "chicken", 20, 1, currentRound, "BLUE", 1),
                        BuildAction.emeraldProductionUpgrade(currentRound + 1, 2, 50, 1),
                        BuildAction.towerUpgrade(currentRound + 2, "sample_upgrade", new GridPosition(2, 64, 2), 120)
                )
        );
        gameManager.buildGuideService().saveDebugGuide(
                "DEBUG3",
                "방어 안정화 빌드",
                player.getUUID(),
                player.getGameProfile().getName(),
                jobId,
                currentRound + 7,
                List.of(
                        BuildAction.towerPlace(currentRound, "sample_guard_tower", new GridPosition(-2, 64, -2), 100),
                        BuildAction.towerPlace(currentRound + 1, "sample_support_tower", new GridPosition(-1, 64, -2), 90),
                        BuildAction.towerUpgrade(currentRound + 2, "sample_guard_upgrade", new GridPosition(-2, 64, -2), 160),
                        BuildAction.summon(currentRound + 3, "piglin", 80, 5, currentRound + 3, "GREEN", 1)
                )
        );
        return primary;
    }

    private static GridPosition debugTargetPosition(PlayerLane lane, BlockPos playerPos) {
        return TowerPlacementPositions.resolveGrid(lane, playerPos)
                .or(() -> TowerPlacementPositions.resolveGrid(lane, BlockPos.containing(lane.laneLayout().positionAt(0.35))))
                .orElseGet(() -> GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(0.35))));
    }

    private static List<BuildAction> debugBuildGuideActions(
            SemionGame game,
            UUID playerId,
            PlayerLane lane,
            GridPosition targetPosition
    ) {
        ArrayList<BuildAction> actions = new ArrayList<>();
        Tower tower = lane.towerAt(targetPosition);
        if (tower == null) {
            ProductionTowerService.availableTowers(game, playerId).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            entry -> actions.add(BuildAction.towerPlace(
                                    game.currentRound(),
                                    entry.type().id(),
                                    targetPosition,
                                    entry.type().mineralCost()
                            )),
                            () -> actions.add(BuildAction.towerPlace(game.currentRound(), "debug_tower", targetPosition, 0))
                    );
        } else {
            ProductionTowerService.availableUpgrades(game, playerId, tower.position()).stream()
                    .findFirst()
                    .ifPresent(option -> actions.add(BuildAction.towerUpgrade(
                            game.currentRound(),
                            option.id(),
                            tower.position(),
                            option.mineralCost()
                    )));
        }
        game.summonShop().all().stream()
                .findFirst()
                .ifPresent(type -> actions.add(BuildAction.summon(
                        game.currentRound(),
                        type.id(),
                        type.gasCost(),
                        type.incomeGain(),
                        game.currentRound(),
                        "",
                        0
                )));
        actions.add(BuildAction.emeraldProductionUpgrade(game.currentRound(), 1, 0, 1));
        return actions;
    }

    private static List<BuildAction> debugBuildGuideActions(int currentRound, GridPosition targetPosition) {
        return List.of(
                BuildAction.towerPlace(currentRound, "debug_tower", targetPosition, 100),
                BuildAction.summon(currentRound, "chicken", 20, 1, currentRound, "BLUE", 1),
                BuildAction.emeraldProductionUpgrade(currentRound, 1, 50, 1),
                BuildAction.towerUpgrade(currentRound + 1, "debug_upgrade", targetPosition, 125)
        );
    }

    private static int publishBuild(CommandSourceStack source, SemionGameManager gameManager, String title)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BuildGuide> guide = gameManager.publishLastBuild(player, title);
        if (guide.isEmpty()) {
            failure(source, "저장할 수 있는 마지막 경기 빌드 기록이 없습니다. 경기 종료 후 실제 행동 기록이 있어야 합니다.");
            return 0;
        }
        success(source, "빌드를 비공개로 저장했습니다: " + guide.get().title() + " 코드=" + guide.get().code() + " /빌드 목록에서 공개로 전환할 수 있습니다.");
        return 1;
    }

    private static int buildListDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        gameManager.showBuildList(source.getPlayerOrException());
        success(source, "빌드 목록 창을 열었습니다.");
        return 1;
    }

    private static int buildListDialog(CommandSourceStack source, SemionGameManager gameManager, int publicPage, int myPage)
            throws CommandSyntaxException {
        gameManager.showBuildList(source.getPlayerOrException(), publicPage, myPage);
        success(source, "빌드 목록 창을 열었습니다.");
        return 1;
    }

    private static int trackBuild(CommandSourceStack source, SemionGameManager gameManager, String code)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BuildGuide> guide = gameManager.trackBuild(source.getServer(), player, code);
        if (guide.isEmpty()) {
            failure(source, "빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, "빌드 추적을 시작했습니다: " + guide.get().title() + " 코드=" + guide.get().code());
        gameManager.showBuildList(player);
        return 1;
    }

    private static int clearTrackedBuild(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        gameManager.clearTrackedBuild(player);
        success(source, "현재 추적 중인 빌드를 해제했습니다.");
        gameManager.showBuildList(player);
        return 1;
    }

    private static int showBuildDetails(CommandSourceStack source, SemionGameManager gameManager, String code)
            throws CommandSyntaxException {
        if (!gameManager.showBuildDetails(source.getPlayerOrException(), code)) {
            failure(source, "빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, "빌드 상세 창을 열었습니다: " + code);
        return 1;
    }

    private static int setBuildVisibility(CommandSourceStack source, SemionGameManager gameManager, String code, String visibility)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<BuildGuide> guide = gameManager.setBuildVisibility(player, code, visibility);
        if (guide.isEmpty()) {
            failure(source, "내 빌드가 아니거나 빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, guide.get().isPublic() ? "빌드를 공개했습니다: " + guide.get().title() : "빌드를 비공개로 전환했습니다: " + guide.get().title());
        gameManager.dialogService().showBuildGuideDetails(player, guide.get());
        return 1;
    }

    private static int deleteBuild(CommandSourceStack source, SemionGameManager gameManager, String code)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!gameManager.deleteBuild(player, code)) {
            failure(source, "내 빌드가 아니거나 빌드 코드를 찾을 수 없습니다: " + code);
            return 0;
        }
        success(source, "빌드를 삭제했습니다: " + code);
        gameManager.showBuildList(player);
        return 1;
    }

    private static int killBoss(CommandSourceStack source, SemionGameManager gameManager, String teamName) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "진행 중인 게임이 없습니다.");
            return 0;
        }

        TeamId teamId;
        try {
            teamId = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            failure(source, "알 수 없는 팀입니다: " + teamName + ". RED, BLUE, GREEN, YELLOW, PURPLE 중 하나를 사용하세요.");
            return 0;
        }

        boolean killed = game.killBoss(source.getServer(), teamId);
        if (!killed) {
            failure(source, "보스 처치 실패: 팀=" + teamId);
            return 0;
        }

        success(source, "보스를 처치했습니다: 팀=" + teamId);
        return 1;
    }

    private static TeamId parseTeam(String teamName) {
        return TeamId.valueOf(teamName.toUpperCase());
    }

    private static ResourceLocation parseJobId(String rawJobId) {
        ResourceLocation parsed = ResourceLocation.tryParse(rawJobId);
        if (parsed != null && rawJobId.contains(":")) {
            return parsed;
        }
        ResourceLocation defaulted = ResourceLocation.tryBuild(SemionTd.MOD_ID, rawJobId);
        if (defaulted == null) {
            throw new IllegalArgumentException("잘못된 직업 ID입니다: " + rawJobId);
        }
        return defaulted;
    }

    private static boolean ensureWaitingSetup(CommandSourceStack source, SemionGame game, String action) {
        if (!game.canConfigureRoster()) {
            failure(source, "참가자 확정 후에는 해당 작업을 할 수 없습니다: " + action);
            return false;
        }
        return true;
    }

    private static SemionGame activeWaitingGame(
            CommandSourceStack source,
            SemionGameManager gameManager,
            String action
    ) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            failure(source, "열린 로비가 없습니다. 먼저 /semiontd create를 실행하세요.");
            return null;
        }
        if (gameManager.startCountdownActive()) {
            failure(source, "시작 카운트다운이 이미 진행 중입니다.");
            return null;
        }
        if (!ensureWaitingSetup(source, game, action)) {
            return null;
        }
        return game;
    }

    private static String startCountdownFailureMessage(SemionGameManager.StartCountdownResult result) {
        return switch (result) {
            case NO_ACTIVE_GAME -> "열린 로비가 없습니다. 먼저 /semiontd create를 실행하세요.";
            case NOT_WAITING -> "참가자 확정 후에는 게임 시작 카운트다운을 시작할 수 없습니다.";
            case ALREADY_PENDING -> "시작 카운트다운이 이미 진행 중입니다.";
            case PRELOAD_FAILED -> "게임 시작 전 맵 프리로드에 실패했습니다.";
            case SCHEDULED -> "시작 카운트다운을 시작했습니다.";
        };
    }

    private static Optional<ParticipantSelectionPlan> buildSelectionPlan(
            CommandSourceStack source,
            SemionGameManager gameManager,
            SemionGame game
    ) {
        VanillaTeamBridge.ensureTeams(source.getServer());
        List<StartCandidate> candidates = new ArrayList<>();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            candidates.add(new StartCandidate(
                    player.getUUID(),
                    player.getGameProfile().getName()
            ));
        }
        return ParticipantSelectionService.selectReady(
                candidates,
                game.readyPlayerIds(),
                gameManager.matchMode(),
                gameManager.nextMatchPriorityPlayerIds()
        );
    }

    private static void applyPlanToVanillaTeams(CommandSourceStack source, ParticipantSelectionPlan plan) {
        VanillaTeamBridge.ensureTeams(source.getServer());
        for (AssignedParticipant participant : plan.activeParticipants()) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayer(participant.uuid());
            if (player != null) {
                VanillaTeamBridge.assignPlayer(source.getServer(), player, participant.teamId());
            }
        }
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (plan.spectatorIds().contains(player.getUUID())) {
                VanillaTeamBridge.assignSpectator(source.getServer(), player);
            }
        }
    }

    private static void assignUnreadyPlayersToSpectatorTeam(
            CommandSourceStack source,
            SemionGame game,
            ParticipantSelectionPlan plan
    ) {
        java.util.Set<UUID> selectedIds = new java.util.HashSet<>();
        for (AssignedParticipant participant : plan.activeParticipants()) {
            selectedIds.add(participant.uuid());
        }
        selectedIds.addAll(plan.spectatorIds());

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (!game.readyPlayerIds().contains(playerId) && !selectedIds.contains(playerId)) {
                VanillaTeamBridge.assignSpectator(source.getServer(), player);
            }
        }
    }

    private static long nextGasUpgradeCost(SemionGame game, PlayerEconomy economy) {
        var gasProduction = game.economyConfig().gasProduction();
        if (economy.gasProductionUpgradeCount() >= gasProduction.maxUpgradeCount()) {
            return -1;
        }
        return gasProduction.upgradeCost(economy.gasProductionUpgradeCount());
    }

    private static String placementFailureMessage(TowerPlacementResult result) {
        return switch (result) {
            case INVALID_PHASE -> "준비 단계에서만 설치할 수 있습니다";
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다";
            case PLAYER_TEAM_ELIMINATED -> "소속 팀이 탈락했습니다";
            case UNKNOWN_LANE -> "활성화된 라인이 아닙니다";
            case UNKNOWN_TOWER -> "알 수 없는 타워입니다";
            case TOWER_NOT_ALLOWED -> "현재 직업으로 사용할 수 없는 타워입니다";
            case OUTSIDE_LANE_AREA -> "lane_path 영역 안에서 실행하세요";
            case OCCUPIED -> "이미 타워가 있는 위치입니다";
            case TOWER_LIMIT_REACHED -> "타워 설치 제한에 도달했습니다";
            case NOT_ENOUGH_MINERAL -> "다이아가 부족합니다";
            case SUCCESS -> "성공";
        };
    }

    private static String towerUpgradeFailureMessage(TowerUpgradeResult result) {
        return switch (result) {
            case INVALID_PHASE -> "준비 단계에서만 업그레이드할 수 있습니다";
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다";
            case PLAYER_TEAM_ELIMINATED -> "소속 팀이 탈락했습니다";
            case UNKNOWN_LANE -> "활성화된 라인이 아닙니다";
            case NO_TOWER_AT_POSITION -> "해당 위치에 타워가 없습니다";
            case TOWER_NOT_OWNED -> "자신이 설치한 타워만 업그레이드할 수 있습니다";
            case TOWER_NOT_UPGRADABLE -> "사용 가능한 진화 경로가 없습니다";
            case UNKNOWN_UPGRADE -> "해당 타워에 없는 업그레이드 ID입니다";
            case UNKNOWN_TARGET_TYPE -> "타워 진화 대상 타입이 등록되지 않았습니다";
            case TOWER_NOT_ALLOWED -> "현재 직업으로 사용할 수 없는 타워입니다";
            case NOT_ENOUGH_MINERAL -> "다이아가 부족합니다";
            case SUCCESS -> "성공";
        };
    }

    private static String towerSellFailureMessage(TowerSellResult result) {
        return switch (result) {
            case SUCCESS -> "성공";
            case INVALID_PHASE -> "현재 단계에서는 판매할 수 없습니다.";
            case PLAYER_NOT_IN_GAME -> "현재 게임 참가자가 아닙니다.";
            case PLAYER_TEAM_ELIMINATED -> "탈락한 팀은 판매할 수 없습니다.";
            case UNKNOWN_LANE -> "담당 라인을 찾을 수 없습니다.";
            case NO_TOWER_AT_POSITION -> "현재 위치에 판매할 타워가 없습니다.";
            case TOWER_NOT_OWNED -> "자신이 설치한 타워만 판매할 수 있습니다.";
        };
    }

    private static String summonFailureMessage(SummonResultType result) {
        return switch (result) {
            case INVALID_PHASE -> "준비 단계 또는 웨이브 중에만 구매할 수 있습니다";
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다";
            case PLAYER_TEAM_ELIMINATED -> "소속 팀이 탈락했습니다";
            case UNKNOWN_SUMMON -> "알 수 없는 소환 ID입니다";
            case SUMMON_NOT_ALLOWED_BY_JOB -> "현재 직업은 해당 소환을 사용할 수 없습니다";
            case NOT_ENOUGH_GAS -> "에메랄드가 부족합니다";
            case NO_TARGET_TEAM -> "공격할 수 있는 상대 팀이 없습니다";
            case NO_TARGET_LANE -> "대상 팀에 활성화된 라인이 없습니다";
            case NO_ACTIVE_GAME -> "진행 중인 게임이 없습니다";
            case SUCCESS -> "성공";
        };
    }
}
