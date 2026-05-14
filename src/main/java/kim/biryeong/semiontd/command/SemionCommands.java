package kim.biryeong.semiontd.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kim.biryeong.semiontd.SemionTd;
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
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SemionCommands {
    private SemionCommands() {
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
                                .then(argument("id", StringArgumentType.word())
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
                                .executes(context -> sellTower(context.getSource(), gameManager)))
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
                                        )))))
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
                        .executes(context -> summonDialog(context.getSource(), gameManager)))
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
    }

    private static int createGame(CommandSourceStack source, SemionGameManager gameManager) {
        try {
            gameManager.createGame(source.getServer());
            source.sendSuccess(() -> Component.literal("Semion TD 로비와 아레나를 생성했습니다."), false);
            return 1;
        } catch (ArenaLoadException exception) {
            source.sendFailure(Component.literal("Semion TD 아레나 생성 실패: " + exception.getMessage()));
            return 0;
        }
    }

    private static int resetGame(CommandSourceStack source, SemionGameManager gameManager, String actionLabel) {
        try {
            boolean hadActiveGame = gameManager.resetToLobby(source.getServer());
            String suffix = hadActiveGame ? "" : " 진행 중인 게임은 없었습니다.";
            source.sendSuccess(() -> Component.literal("Semion TD 게임을 " + actionLabel + "하고 모두 로비로 이동했습니다." + suffix), false);
            return 1;
        } catch (ArenaLoadException exception) {
            source.sendFailure(Component.literal("Semion TD 로비 이동 실패: " + exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.error("Unexpected Semion TD lobby reset failure.", exception);
            source.sendFailure(Component.literal("Semion TD 로비 이동 중 예기치 못한 오류가 발생했습니다: " + exception.getMessage()));
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
            source.sendFailure(Component.literal("현재 게임 모드로 시작할 준비 완료 인원이 부족합니다."));
            return 0;
        }

        applyPlanToVanillaTeams(source, plan.get());
        assignUnreadyPlayersToSpectatorTeam(source, game, plan.get());
        if (!game.start(source.getServer(), plan.get())) {
            source.sendFailure(Component.literal("참가자 확정 및 게임 시작에 실패했습니다."));
            return 0;
        }

        String lobbyLoaded = gameManager.lobbyWorld().isPresent() ? ", lobbyLoaded=true" : ", lobbyLoaded=false";
        source.sendSuccess(() -> Component.literal("Semion TD 게임을 시작했습니다. 참가자="
                + plan.get().activePlayerCount()
                + ", 팀=" + plan.get().activeTeamCount()
                + ", 구성=" + plan.get().compositionSummary()
                + ", 관전자=" + plan.get().spectatorCount()
                + ", 준비=" + game.readyPlayerCount()
                + ", 모드=" + gameManager.matchMode()
                + lobbyLoaded), false);
        return 1;
    }

    private static int autojoin(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = activeWaitingGame(source, gameManager, "autojoin");
        if (game == null) {
            return 0;
        }

        Optional<ParticipantSelectionPlan> plan = buildSelectionPlan(source, gameManager, game);
        if (plan.isEmpty()) {
            source.sendFailure(Component.literal("현재 게임 모드로 팀을 배정할 준비 완료 인원이 부족합니다."));
            return 0;
        }

        applyPlanToVanillaTeams(source, plan.get());
        String lobbyLoaded = gameManager.lobbyWorld().isPresent() ? ", lobbyLoaded=true" : ", lobbyLoaded=false";
        source.sendSuccess(() -> Component.literal("다음 시작을 위한 팀을 배정했습니다. 참가자="
                + plan.get().activePlayerCount()
                + ", 팀=" + plan.get().activeTeamCount()
                + ", 구성=" + plan.get().compositionSummary()
                + ", 관전자=" + plan.get().spectatorCount()
                + ", 준비=" + game.readyPlayerCount()
                + ", 모드=" + gameManager.matchMode()
                + lobbyLoaded), false);
        return plan.get().activePlayerCount();
    }

    private static int ready(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "ready");
        if (game == null) {
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.markReady(player.getUUID())) {
            source.sendFailure(Component.literal("Semion TD 준비 완료 처리에 실패했습니다."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("준비 완료했습니다. 준비 인원=" + game.readyPlayerCount()), false);
        return 1;
    }

    private static int unready(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "unready");
        if (game == null) {
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.markNotReady(player.getUUID())) {
            source.sendFailure(Component.literal("Semion TD 준비 해제에 실패했습니다."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("준비를 해제했습니다. 준비 인원=" + game.readyPlayerCount()), false);
        return 1;
    }

    private static int spectate(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.rosterLocked() || game.phase() == RoundPhase.ENDED) {
            source.sendFailure(Component.literal("관전할 수 있는 진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.addLateSpectator(source.getServer(), player)) {
            source.sendFailure(Component.literal("현재 상태에서는 관전으로 전환할 수 없습니다."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Semion TD 게임 관전으로 이동했습니다."), false);
        return 1;
    }

    private static int spectate(CommandSourceStack source, SemionGameManager gameManager, String teamName) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.rosterLocked() || game.phase() == RoundPhase.ENDED) {
            source.sendFailure(Component.literal("관전할 수 있는 진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        TeamId targetTeam;
        try {
            targetTeam = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("알 수 없는 팀입니다. red, blue, green, yellow 중 하나를 사용하세요."));
            return 0;
        }

        if (!game.canSpectateTeam(targetTeam)) {
            source.sendFailure(Component.literal("현재 관전할 수 없는 팀입니다: " + targetTeam.name()));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        if (!game.addLateSpectator(source.getServer(), player, targetTeam)) {
            source.sendFailure(Component.literal("현재 상태에서는 관전으로 전환할 수 없습니다."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Semion TD " + targetTeam.name() + " 팀 관전으로 이동했습니다."), false);
        return 1;
    }

    private static int setTestMode(CommandSourceStack source, SemionGameManager gameManager, boolean enabled) {
        SemionGame activeGame = gameManager.activeGame().orElse(null);
        if (activeGame != null && activeGame.rosterLocked()) {
            source.sendFailure(Component.literal("참가자 확정 후에는 테스트 모드를 변경할 수 없습니다."));
            return 0;
        }

        gameManager.setMatchMode(enabled ? MatchMode.TEST : MatchMode.NORMAL);
        source.sendSuccess(() -> Component.literal("Semion TD 테스트 모드=" + enabled), false);
        return 1;
    }

    private static int status(CommandSourceStack source, SemionGameManager gameManager) {
        return sendStatusLines(source, statusLines(gameManager));
    }

    private static int statusTeams(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }
        return sendStatusLines(source, teamStatusLines(game));
    }

    private static int statusPlayers(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }
        return sendStatusLines(source, playerStatusLines(game));
    }

    private static int statusLanes(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }
        return sendStatusLines(source, laneStatusLines(game));
    }

    private static int sendStatusLines(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.isEmpty() ? 0 : 1;
    }

    public static List<String> statusLines(SemionGameManager gameManager) {
        List<String> lines = new ArrayList<>();
        SemionGame game = gameManager.activeGame().orElse(null);
        boolean lobbyLoaded = gameManager.lobbyWorld().isPresent();
        if (game == null) {
            lines.add("Semion TD 상태 activeGame=false, phase=NONE, matchMode="
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

        lines.add("Semion TD 상태 activeGame=true, phase="
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
            source.sendSuccess(() -> Component.literal("Semion TD 상태 창을 열었습니다."), false);
            return 1;
        }

        Optional<MatchResult> lastResult = gameManager.lastMatchResult();
        if (lastResult.isPresent()) {
            gameManager.dialogService().showLastResult(player, lastResult.get());
            source.sendSuccess(() -> Component.literal("Semion TD 최근 결과 창을 열었습니다."), false);
            return 1;
        }

        source.sendFailure(Component.literal("진행 중인 Semion TD 게임이나 최근 결과가 없습니다."));
        return 0;
    }

    private static int economy(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        SemionPlayer player = game.players().get(source.getPlayerOrException().getUUID());
        if (player == null) {
            source.sendFailure(Component.literal("현재 Semion TD 게임 참가자가 아닙니다."));
            return 0;
        }

        PlayerEconomy economy = player.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        source.sendSuccess(() -> Component.literal("경제 다이아=" + economy.diamond()
                + ", 에메랄드=" + economy.emerald()
                + ", 수입=" + economy.income()
                + ", 에메랄드/초=" + economy.emeraldPerSec()
                + ", 생산업글=" + economy.emeraldProductionUpgradeCount()
                + ", 다음업글비용=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대")), false);
        return 1;
    }

    private static int profile(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var profile = gameManager.profile(source.getServer(), player.getUUID(), player.getGameProfile().getName());
        source.sendSuccess(() -> Component.literal("프로필 장식재화=" + profile.cosmeticCurrency()
                + ", 플레이=" + profile.gamesPlayed()
                + ", 승=" + profile.wins()
                + ", 패=" + profile.losses()), false);
        return 1;
    }

    private static int listJobs(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Semion TD 직업 목록:"), false);
        for (SemionJob job : JobRegistry.all()) {
            source.sendSuccess(() -> Component.literal(" - " + job.id()
                    + " => " + job.displayName().getString()), false);
            for (Component line : job.description()) {
                source.sendSuccess(() -> Component.literal("   " + line.getString()), false);
            }
        }
        return JobRegistry.all().size();
    }

    private static int currentJob(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("열린 Semion TD 로비가 없습니다. 관리자에게 /semiontd create 실행을 요청하세요."));
            return 0;
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        SemionJob job = semionPlayer != null
                ? semionPlayer.job().orElse(JobRegistry.defaultJob())
                : game.selectedJobOrDefault(player.getUUID());
        source.sendSuccess(() -> Component.literal("현재 Semion TD 직업: "
                + job.id()
                + " => "
                + job.displayName().getString()), false);
        return 1;
    }

    private static int jobDialog(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("열린 Semion TD 로비가 없습니다. 관리자에게 /semiontd create 실행을 요청하세요."));
            return 0;
        }
        gameManager.dialogService().showJobSelection(source.getPlayerOrException(), game);
        source.sendSuccess(() -> Component.literal("Semion TD 직업 선택 창을 열었습니다."), false);
        return 1;
    }

    private static int selectJob(CommandSourceStack source, SemionGameManager gameManager, String rawJobId)
            throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "직업 선택");
        if (game == null) {
            return 0;
        }
        if (!game.canConfigureRoster()) {
            source.sendFailure(Component.literal("참가자 확정 후에는 직업을 선택할 수 없습니다."));
            return 0;
        }

        ResourceLocation jobId;
        try {
            jobId = parseJobId(rawJobId);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        if (!game.selectJob(source.getPlayerOrException().getUUID(), jobId)) {
            source.sendFailure(Component.literal("알 수 없는 Semion TD 직업입니다: " + jobId + ". /semiontd job list를 확인하세요."));
            return 0;
        }

        SemionJob job = game.selectedJobOrDefault(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.literal("Semion TD 직업을 선택했습니다: "
                + job.id()
                + " => "
                + job.displayName().getString()), false);
        return 1;
    }

    private static int emeraldUp(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        UUID playerId = source.getPlayerOrException().getUUID();
        boolean upgraded = game.upgradeGasProduction(playerId);
        if (!upgraded) {
            source.sendFailure(Component.literal("에메랄드 생산 업그레이드에 실패했습니다."));
            return 0;
        }

        PlayerEconomy economy = game.players().get(playerId).economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        source.sendSuccess(() -> Component.literal("에메랄드 생산을 업그레이드했습니다. 에메랄드/초=" + economy.emeraldPerSec()
                + ", 생산업글=" + economy.emeraldProductionUpgradeCount()
                + ", 다음비용=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대")), false);
        return 1;
    }

    private static int placeTestTower(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerPlacementResult result = TestTowerService.placeTestTower(game, player.getUUID(), player.blockPosition());
        if (result != TowerPlacementResult.SUCCESS) {
            source.sendFailure(Component.literal("테스트 타워 설치 실패: " + placementFailureMessage(result)));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("테스트 타워를 설치했습니다: " + player.blockPosition()), false);
        return 1;
    }

    private static int listProductionTowers(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerService.availableTowers(game, player.getUUID());
        if (entries.isEmpty()) {
            source.sendFailure(Component.literal("현재 직업으로 사용할 수 있는 타워가 없습니다."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("사용 가능한 타워:"), false);
        for (ProductionTowerCatalog.CatalogEntry entry : entries) {
            source.sendSuccess(() -> Component.literal(" - " + entry.type().id()
                    + " => " + entry.type().displayName()
                    + " 다이아비용=" + entry.type().mineralCost()
                    + ", 팩션=" + entry.behavior().faction()
                    + ", 스플래시=" + entry.behavior().splashRadius()
                    + ", 특성=" + entry.behavior().mechanicName()), false);
        }
        return entries.size();
    }

    private static int buildProductionTower(CommandSourceStack source, SemionGameManager gameManager, String towerId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerPlacementResult result = ProductionTowerService.placeTower(game, player.getUUID(), player.blockPosition(), towerId);
        if (result != TowerPlacementResult.SUCCESS) {
            source.sendFailure(Component.literal("타워 설치 실패: " + placementFailureMessage(result)));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("타워를 설치했습니다: " + towerId + ", 위치=" + player.blockPosition()), false);
        return 1;
    }

    private static int towerDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }
        gameManager.dialogService().showTowerControl(source.getPlayerOrException(), game);
        source.sendSuccess(() -> Component.literal("Semion TD 타워 관리 창을 열었습니다."), false);
        return 1;
    }

    private static int sellTower(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ProductionTowerService.SaleResult result = ProductionTowerService.sellTower(game, player.getUUID(), player.blockPosition());
        if (result.result() != TowerSellResult.SUCCESS) {
            source.sendFailure(Component.literal("타워 판매 실패: " + towerSellFailureMessage(result.result())));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("타워를 판매했습니다. 환불 다이아=" + result.refundAmount()), false);
        return 1;
    }


    private static int listTowerUpgrades(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        List<TowerUpgradeOption> upgrades = ProductionTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (upgrades.isEmpty()) {
            source.sendFailure(Component.literal("해당 위치에 사용 가능한 타워 업그레이드가 없습니다: " + player.blockPosition()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("사용 가능한 업그레이드: 위치=" + player.blockPosition()), false);
        for (TowerUpgradeOption option : upgrades) {
            source.sendSuccess(() -> Component.literal(" - " + option.id()
                    + " => " + option.displayName()
                    + " 다이아비용=" + option.mineralCost()), false);
        }
        return upgrades.size();
    }

    private static int upgradeTower(CommandSourceStack source, SemionGameManager gameManager, String upgradeId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerUpgradeResult result = ProductionTowerService.upgradeTower(game, player.getUUID(), player.blockPosition(), upgradeId);
        if (result != TowerUpgradeResult.SUCCESS) {
            source.sendFailure(Component.literal("타워 업그레이드 실패: " + towerUpgradeFailureMessage(result)));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("타워를 업그레이드했습니다: " + player.blockPosition() + ", 업그레이드=" + upgradeId), false);
        return 1;
    }

    private static int summon(CommandSourceStack source, SemionGameManager gameManager, String summonId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        SummonResult result = game.summonMonster(source.getPlayerOrException().getUUID(), summonId);
        if (result.type() != SummonResultType.SUCCESS) {
            source.sendFailure(Component.literal("소환 실패: " + summonFailureMessage(result.type())));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("소환했습니다: " + summonId
                + ", 팀=" + result.targetTeam().orElseThrow()
                + ", 라인=" + result.targetLaneId().orElseThrow()), false);
        return 1;
    }

    private static int summons(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("소환 목록:"), false);
        for (SummonMonsterType type : game.summonShop().all()) {
            source.sendSuccess(() -> Component.literal(" - " + type.id()
                    + " 에메랄드비용=" + type.gasCost()
                    + ", 수입=" + type.incomeGain()
                    + ", 체력=" + Math.round(type.maxHealth())), false);
        }
        return 1;
    }

    private static int summonDialog(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }
        gameManager.dialogService().showSummonShop(source.getPlayerOrException(), game);
        source.sendSuccess(() -> Component.literal("Semion TD 견제 소환 창을 열었습니다."), false);
        return 1;
    }

    private static int killBoss(CommandSourceStack source, SemionGameManager gameManager, String teamName) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("진행 중인 Semion TD 게임이 없습니다."));
            return 0;
        }

        TeamId teamId;
        try {
            teamId = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("알 수 없는 팀입니다: " + teamName + ". RED, BLUE, GREEN, YELLOW 중 하나를 사용하세요."));
            return 0;
        }

        boolean killed = game.killBoss(source.getServer(), teamId);
        if (!killed) {
            source.sendFailure(Component.literal("보스 처치 실패: 팀=" + teamId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("보스를 처치했습니다: 팀=" + teamId), false);
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
            source.sendFailure(Component.literal("참가자 확정 후에는 해당 작업을 할 수 없습니다: " + action));
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
            source.sendFailure(Component.literal("열린 Semion TD 로비가 없습니다. 먼저 /semiontd create를 실행하세요."));
            return null;
        }
        if (!ensureWaitingSetup(source, game, action)) {
            return null;
        }
        return game;
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
        return ParticipantSelectionService.selectReady(candidates, game.readyPlayerIds(), gameManager.matchMode());
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
            case TOWER_NOT_ALLOWED_BY_JOB -> "현재 직업은 해당 타워를 사용할 수 없습니다";
            case OUTSIDE_LANE_AREA -> "lane_path 영역 안에서 실행하세요";
            case OCCUPIED -> "이미 타워가 있는 위치입니다";
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
            case TOWER_NOT_UPGRADABLE -> "사용 가능한 진화 경로가 없습니다";
            case UNKNOWN_UPGRADE -> "해당 타워에 없는 업그레이드 ID입니다";
            case UNKNOWN_TARGET_TYPE -> "타워 진화 대상 타입이 등록되지 않았습니다";
            case TOWER_NOT_ALLOWED_BY_JOB -> "현재 직업은 해당 타워 진화를 사용할 수 없습니다";
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
            case INVALID_PHASE -> "준비/소환 단계에서만 소환할 수 있습니다";
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다";
            case PLAYER_TEAM_ELIMINATED -> "소속 팀이 탈락했습니다";
            case UNKNOWN_SUMMON -> "알 수 없는 소환 ID입니다";
            case SUMMON_NOT_ALLOWED_BY_JOB -> "현재 직업은 해당 소환을 사용할 수 없습니다";
            case NOT_ENOUGH_GAS -> "에메랄드가 부족합니다";
            case NO_TARGET_TEAM -> "공격할 수 있는 상대 팀이 없습니다";
            case NO_TARGET_LANE -> "대상 팀에 활성화된 라인이 없습니다";
            case NO_ACTIVE_GAME -> "진행 중인 Semion TD 게임이 없습니다";
            case SUCCESS -> "성공";
        };
    }
}
