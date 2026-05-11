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
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
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
                        .executes(context -> spectate(context.getSource(), gameManager)))
                .then(literal("economy")
                        .executes(context -> economy(context.getSource(), gameManager)))
                .then(literal("profile")
                        .executes(context -> profile(context.getSource(), gameManager)))
                .then(literal("job")
                        .then(literal("list")
                                .executes(context -> listJobs(context.getSource())))
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
                .then(literal("killboss")
                        .requires(source -> source.hasPermission(2))
                        .then(argument("team", StringArgumentType.word())
                                .executes(context -> killBoss(
                                        context.getSource(),
                                        gameManager,
                                        StringArgumentType.getString(context, "team")
                                ))))
                .then(literal("status")
                        .executes(context -> status(context.getSource(), gameManager)))
                .then(literal("ui")
                        .executes(context -> statusDialog(context.getSource(), gameManager))));
    }

    private static int createGame(CommandSourceStack source, SemionGameManager gameManager) {
        try {
            gameManager.createGame(source.getServer());
            gameManager.sendAllPlayersToLobby(source.getServer());
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
            source.sendSuccess(() -> Component.literal("Semion TD 게임을 " + actionLabel + "하고 모두 로비로 이동했습니다." + suffix), true);
            return 1;
        } catch (ArenaLoadException exception) {
            source.sendFailure(Component.literal("Semion TD 로비 이동 실패: " + exception.getMessage()));
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
        SemionGame game = gameManager.activeGame().orElse(null);
        boolean lobbyLoaded = gameManager.lobbyWorld().isPresent();
        if (game == null) {
            var lastResult = gameManager.lastMatchResult();
            if (lastResult.isPresent()) {
                String winners = lastResult.get().winningTeams().isEmpty()
                        ? "none"
                        : lastResult.get().winningTeams().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(", "));
                source.sendSuccess(() -> Component.literal("진행 중인 Semion TD 게임이 없습니다. 로비로드=" + lobbyLoaded
                        + ", 최근승자=" + winners
                        + ", 최근라운드=" + lastResult.get().finalRound()), false);
                return 1;
            }
            source.sendSuccess(() -> Component.literal("진행 중인 Semion TD 게임이 없습니다. 로비로드=" + lobbyLoaded), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Semion TD 라운드=" + game.currentRound()
                + ", 상태=" + game.phase()
                + ", 참가자=" + game.players().size()
                + ", 관전자=" + game.spectatorCount()
                + ", 준비=" + game.readyPlayerCount()
                + ", rosterLocked=" + game.rosterLocked()
                + ", 모드=" + gameManager.matchMode()
                + ", 로비로드=" + lobbyLoaded), false);
        for (SemionTeam team : game.teams().values()) {
            source.sendSuccess(() -> Component.literal(" - " + team.id()
                    + " 활성=" + team.active()
                    + " 탈락=" + team.eliminated()
                    + ", 인원=" + team.memberIds().size()
                    + ", 보스HP=" + Math.round(team.laneGroup().boss().health())), false);
        }
        return 1;
    }

    private static int statusDialog(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game != null) {
            gameManager.dialogService().showGameStatus(player, game);
            source.sendSuccess(() -> Component.literal("Opened Semion TD status dialog."), false);
            return 1;
        }

        Optional<MatchResult> lastResult = gameManager.lastMatchResult();
        if (lastResult.isPresent()) {
            gameManager.dialogService().showLastResult(player, lastResult.get());
            source.sendSuccess(() -> Component.literal("Opened Semion TD last result dialog."), false);
            return 1;
        }

        source.sendFailure(Component.literal("No active Semion TD game or previous result."));
        return 0;
    }

    private static int economy(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        SemionPlayer player = game.players().get(source.getPlayerOrException().getUUID());
        if (player == null) {
            source.sendFailure(Component.literal("You are not in the active Semion TD game."));
            return 0;
        }

        PlayerEconomy economy = player.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        source.sendSuccess(() -> Component.literal("Economy diamond=" + economy.diamond()
                + ", emerald=" + economy.emerald()
                + ", income=" + economy.income()
                + ", emeraldPerSec=" + economy.emeraldPerSec()
                + ", emeraldUpgrades=" + economy.emeraldProductionUpgradeCount()
                + ", nextEmeraldUpCost=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "capped")), false);
        return 1;
    }

    private static int profile(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var profile = gameManager.profile(source.getServer(), player.getUUID(), player.getGameProfile().getName());
        source.sendSuccess(() -> Component.literal("Profile cosmetics=" + profile.cosmeticCurrency()
                + ", games=" + profile.gamesPlayed()
                + ", wins=" + profile.wins()
                + ", losses=" + profile.losses()), false);
        return 1;
    }

    private static int listJobs(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Available Semion TD jobs:"), false);
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
            source.sendFailure(Component.literal("No Semion TD lobby is open. Ask an admin to run /semiontd create first."));
            return 0;
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        SemionJob job = semionPlayer != null
                ? semionPlayer.job().orElse(JobRegistry.defaultJob())
                : game.selectedJobOrDefault(player.getUUID());
        source.sendSuccess(() -> Component.literal("Current Semion TD job: "
                + job.id()
                + " => "
                + job.displayName().getString()), false);
        return 1;
    }

    private static int selectJob(CommandSourceStack source, SemionGameManager gameManager, String rawJobId)
            throws CommandSyntaxException {
        SemionGame game = activeWaitingGame(source, gameManager, "select a job");
        if (game == null) {
            return 0;
        }
        if (!game.canConfigureRoster()) {
            source.sendFailure(Component.literal("Cannot select a job after the match roster has been locked."));
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
            source.sendFailure(Component.literal("Unknown Semion TD job: " + jobId + ". Use /semiontd job list."));
            return 0;
        }

        SemionJob job = game.selectedJobOrDefault(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.literal("Selected Semion TD job "
                + job.id()
                + " => "
                + job.displayName().getString()
                + "."), false);
        return 1;
    }

    private static int emeraldUp(CommandSourceStack source, SemionGameManager gameManager) throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        UUID playerId = source.getPlayerOrException().getUUID();
        boolean upgraded = game.upgradeGasProduction(playerId);
        if (!upgraded) {
            source.sendFailure(Component.literal("Failed to upgrade emerald production."));
            return 0;
        }

        PlayerEconomy economy = game.players().get(playerId).economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        source.sendSuccess(() -> Component.literal("Emerald production upgraded. emeraldPerSec=" + economy.emeraldPerSec()
                + ", emeraldUpgrades=" + economy.emeraldProductionUpgradeCount()
                + ", nextCost=" + (nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "capped")), false);
        return 1;
    }

    private static int placeTestTower(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerPlacementResult result = TestTowerService.placeTestTower(game, player.getUUID(), player.blockPosition());
        if (result != TowerPlacementResult.SUCCESS) {
            source.sendFailure(Component.literal("Failed to place test tower: " + placementFailureMessage(result)));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Placed test tower at " + player.blockPosition() + "."), false);
        return 1;
    }

    private static int listTowerUpgrades(CommandSourceStack source, SemionGameManager gameManager)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        List<TowerUpgradeOption> upgrades = TestTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (upgrades.isEmpty()) {
            source.sendFailure(Component.literal("No test tower upgrades available at " + player.blockPosition() + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Available upgrades at " + player.blockPosition() + ":"), false);
        for (TowerUpgradeOption option : upgrades) {
            source.sendSuccess(() -> Component.literal(" - " + option.id()
                    + " => " + option.displayName()
                    + " diamondCost=" + option.mineralCost()), false);
        }
        return upgrades.size();
    }

    private static int upgradeTower(CommandSourceStack source, SemionGameManager gameManager, String upgradeId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TowerUpgradeResult result = TestTowerService.upgradeTestTower(game, player.getUUID(), player.blockPosition(), upgradeId);
        if (result != TowerUpgradeResult.SUCCESS) {
            source.sendFailure(Component.literal("Failed to upgrade tower: " + towerUpgradeFailureMessage(result)));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Upgraded tower at " + player.blockPosition() + " via " + upgradeId + "."), false);
        return 1;
    }

    private static int summon(CommandSourceStack source, SemionGameManager gameManager, String summonId)
            throws CommandSyntaxException {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        SummonResult result = game.summonMonster(source.getPlayerOrException().getUUID(), summonId);
        if (result.type() != SummonResultType.SUCCESS) {
            source.sendFailure(Component.literal("Summon failed: " + summonFailureMessage(result.type())));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Summoned " + summonId
                + " to " + result.targetTeam().orElseThrow()
                + " lane " + result.targetLaneId().orElseThrow()), false);
        return 1;
    }

    private static int summons(CommandSourceStack source, SemionGameManager gameManager) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Available summons:"), false);
        for (SummonMonsterType type : game.summonShop().all()) {
            source.sendSuccess(() -> Component.literal(" - " + type.id()
                    + " emeraldCost=" + type.gasCost()
                    + ", income=" + type.incomeGain()
                    + ", hp=" + Math.round(type.maxHealth())), false);
        }
        return 1;
    }

    private static int killBoss(CommandSourceStack source, SemionGameManager gameManager, String teamName) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            source.sendFailure(Component.literal("No active Semion TD game."));
            return 0;
        }

        TeamId teamId;
        try {
            teamId = parseTeam(teamName);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Unknown team: " + teamName + ". Use RED, BLUE, GREEN, or YELLOW."));
            return 0;
        }

        boolean killed = game.killBoss(source.getServer(), teamId);
        if (!killed) {
            source.sendFailure(Component.literal("Failed to kill boss for team " + teamId + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Killed boss for team " + teamId + "."), false);
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
            throw new IllegalArgumentException("Invalid job id: " + rawJobId);
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
            case INVALID_PHASE -> "tower placement is only allowed during prepare phase";
            case PLAYER_NOT_IN_GAME -> "you are not an active player in this match";
            case PLAYER_TEAM_ELIMINATED -> "your team is eliminated";
            case UNKNOWN_LANE -> "your lane is not active";
            case TOWER_NOT_ALLOWED_BY_JOB -> "your job cannot use that tower";
            case OUTSIDE_LANE_AREA -> "stand inside your lane_path region";
            case OCCUPIED -> "that block already has a tower";
            case NOT_ENOUGH_MINERAL -> "not enough diamond";
            case SUCCESS -> "success";
        };
    }

    private static String towerUpgradeFailureMessage(TowerUpgradeResult result) {
        return switch (result) {
            case INVALID_PHASE -> "tower upgrade is only allowed during prepare phase";
            case PLAYER_NOT_IN_GAME -> "you are not an active player in this match";
            case PLAYER_TEAM_ELIMINATED -> "your team is eliminated";
            case UNKNOWN_LANE -> "your lane is not active";
            case NO_TOWER_AT_POSITION -> "there is no tower at that block";
            case TOWER_NOT_UPGRADABLE -> "that tower has no available evolution path";
            case UNKNOWN_UPGRADE -> "unknown upgrade id for that tower";
            case UNKNOWN_TARGET_TYPE -> "tower evolution target type is not registered";
            case TOWER_NOT_ALLOWED_BY_JOB -> "your job cannot use that tower evolution";
            case NOT_ENOUGH_MINERAL -> "not enough diamond";
            case SUCCESS -> "success";
        };
    }

    private static String summonFailureMessage(SummonResultType result) {
        return switch (result) {
            case NOT_ENOUGH_GAS -> "not enough emerald";
            default -> result.name();
        };
    }
}
