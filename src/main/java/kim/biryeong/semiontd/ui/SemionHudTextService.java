package kim.biryeong.semiontd.ui;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.ParticipantSelectionService;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.StartCandidate;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.placeholder.SemionPlaceholders;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SemionHudTextService {
    public static final int MAX_SIDEBAR_LINES = 14;

    private SemionHudTextService() {
    }

    public static Component title() {
        return miniMessage(SemionText.BRAND_MARKUP);
    }

    public static List<Component> sidebarLinesFor(ServerPlayer viewer, SemionGame game, MatchMode matchMode, MinecraftServer server) {
        if (game.canConfigureRoster()) {
            return components(lobbyMarkupFor(viewer, game, matchMode, server));
        }
        if (game.isActiveParticipant(viewer.getUUID()) || game.isMatchSpectator(viewer.getUUID())) {
            return components(matchSidebarMarkupFor(viewer, game, matchMode));
        }
        return List.of();
    }

    public static String matchSidebarMarkupFor(ServerPlayer viewer, SemionGame game, MatchMode matchMode) {
        return matchSidebarMarkupFor(viewer.getUUID(), viewer, viewingTeam(viewer, game), game, matchMode);
    }

    public static String matchSidebarMarkupFor(UUID viewerId, Optional<SemionTeam> viewingTeam, SemionGame game, MatchMode matchMode) {
        return matchSidebarMarkupFor(viewerId, null, viewingTeam, game, matchMode);
    }

    public static Optional<Component> actionbarTextFor(UUID viewerId, SemionGame game) {
        SemionPlayer player = game.players().get(viewerId);
        if (player == null) {
            return Optional.empty();
        }
        SemionTeam team = game.teams().get(player.teamId());
        if (team == null || team.eliminated()) {
            return Optional.empty();
        }
        return Optional.of(miniMessage(actionbarMarkupFor(player, game)));
    }

    public static String actionbarMarkupFor(SemionPlayer player, SemionGame game) {
        PlayerEconomy economy = player.economy();
        int currentTowers = game.towerCount(player.uuid());
        int maxTowers = game.towerLimitForPlayer(player.uuid());
        return "<aqua>◆ 다이아 " + economy.diamond() + "</aqua>"
                + " <dark_gray>|</dark_gray> <green>⬢ 에메랄드 " + economy.emerald() + "</green>"
                + " <dark_gray>|</dark_gray> <dark_green>↗</dark_green> <green>에메랄드/s " + economy.emeraldPerSec() + "</green>"
                + " <dark_gray>|</dark_gray> <gold>+ 수입 " + economy.income() + "</gold>"
                + " <dark_gray>|</dark_gray> <gray>▣ 타워</gray> " + towerLimitText(currentTowers, maxTowers);
    }

    private static String lobbyMarkupFor(ServerPlayer viewer, SemionGame game, MatchMode matchMode, MinecraftServer server) {
        boolean ready = game.isReady(viewer.getUUID());
        String readyLabel = ready ? "<green><bold>준비 완료</bold></green>" : "<red><bold>미준비</bold></red>";
        int onlinePlayers = server.getPlayerList().getPlayerCount();
        String startableLabel = startableText(server, game, matchMode);
        String selectedJob = selectedJobText(viewer, null);
        return "<gray>상태</gray> <yellow>대기 중</yellow>\n"
                + "<gray>게임 모드</gray> <aqua>" + matchModeLabel(matchMode) + "</aqua>\n"
                + "<gray>선택 직업</gray> <yellow>" + selectedJob + "</yellow>\n"
                + "<gray>준비 인원</gray> <green>" + game.readyPlayerCount() + "</green><dark_gray>/</dark_gray><white>" + onlinePlayers + "</white>\n"
                + "<gray>준비 상태</gray> " + readyLabel + "\n"
                + "<gray>시작 가능</gray> " + startableLabel;
    }

    private static String matchSidebarMarkupFor(UUID viewerId, ServerPlayer viewer, Optional<SemionTeam> viewingTeam, SemionGame game, MatchMode matchMode) {
        StringBuilder text = new StringBuilder();
        appendMatchHeader(text, game, matchMode);

        SemionPlayer player = game.players().get(viewerId);
        SemionTeam playerTeam = player == null ? null : game.teams().get(player.teamId());
        if (player != null && playerTeam != null && playerTeam.eliminated()) {
            appendEliminatedPlayerHud(text, viewer, player, playerTeam, viewingTeam);
        } else if (player != null) {
            appendActivePlayerHud(text, viewer, player, playerTeam);
            appendTeamBossSummary(text, game);
        } else {
            appendSpectatorHud(text, viewingTeam);
        }
        return text.toString();
    }

    private static List<Component> components(String markup) {
        String[] lines = markup.split("\\R");
        List<Component> components = new ArrayList<>(Math.min(MAX_SIDEBAR_LINES, lines.length));
        for (String line : lines) {
            if (components.size() >= MAX_SIDEBAR_LINES) {
                break;
            }
            if (!line.isBlank()) {
                components.add(miniMessage(line));
            }
        }
        return components;
    }

    private static Component miniMessage(String text) {
        return SemionText.mini(text);
    }

    private static void appendMatchHeader(StringBuilder text, SemionGame game, MatchMode matchMode) {
        text.append("<gray>상태</gray> <yellow>").append(phaseLabel(game.phase())).append("</yellow>\n");
        text.append("<gray>게임 모드</gray> <aqua>").append(matchModeLabel(matchMode)).append("</aqua>\n");
        text.append("<gray>라운드</gray> <gold>").append(game.currentRound()).append("</gold>");
        int remainingPrepareSeconds = game.remainingPrepareSeconds();
        if (remainingPrepareSeconds >= 0) {
            text.append(" <dark_gray>|</dark_gray> <gray>남은 준비</gray> <green>")
                    .append(remainingPrepareSeconds)
                    .append("초</green>");
        }
        text.append('\n');
    }

    private static void appendActivePlayerHud(StringBuilder text, ServerPlayer viewer, SemionPlayer player, SemionTeam team) {
        text.append("<gray>팀/라인</gray> ")
                .append(teamNameText(player.teamId()))
                .append(" <dark_gray>/</dark_gray> <white>")
                .append(player.laneId())
                .append("</white>\n");
        text.append("<gray>직업</gray> <yellow>")
                .append(selectedJobText(viewer, player))
                .append("</yellow>\n");
        if (team != null) {
            text.append("<gray>내 팀 보스</gray> ")
                    .append(bossHealthText(team))
                    .append('\n');
        }
    }


    private static void appendSpectatorHud(StringBuilder text, Optional<SemionTeam> viewingTeam) {
        text.append("<gray>준비 상태</gray> <yellow>관전 중</yellow>\n");
        viewingTeam.ifPresent(team -> {
            text.append("<gray>관전 팀</gray> ")
                    .append(teamNameText(team.id()))
                    .append('\n');
            text.append("<gray>관전 팀 보스</gray> ")
                    .append(bossHealthText(team))
                    .append('\n');
        });
    }

    private static void appendEliminatedPlayerHud(
            StringBuilder text,
            ServerPlayer viewer,
            SemionPlayer player,
            SemionTeam playerTeam,
            Optional<SemionTeam> viewingTeam
    ) {
        text.append("<gray>준비 상태</gray> <red>탈락 후 관전 중</red>\n");
        text.append("<gray>소속 팀</gray> ")
                .append(teamNameText(player.teamId()))
                .append(" <dark_gray>/</dark_gray> <white>")
                .append(player.laneId())
                .append("</white>\n");
        text.append("<gray>직업</gray> <yellow>")
                .append(selectedJobText(viewer, player))
                .append("</yellow>\n");
        text.append("<gray>소속 팀 보스</gray> ")
                .append(bossHealthText(playerTeam))
                .append('\n');
        viewingTeam
                .filter(team -> team.id() != player.teamId())
                .ifPresent(team -> {
                    text.append("<gray>관전 팀</gray> ")
                            .append(teamNameText(team.id()))
                            .append('\n');
                    text.append("<gray>관전 팀 보스</gray> ")
                            .append(bossHealthText(team))
                            .append('\n');
                });
    }

    private static String selectedJobText(ServerPlayer viewer, SemionPlayer player) {
        if (viewer != null) {
            PlaceholderResult result = Placeholders.parsePlaceholder(
                    SemionPlaceholders.SELECTED_JOB,
                    null,
                    PlaceholderContext.of(viewer)
            );
            if (result.isValid()) {
                return result.text().getString();
            }
        }
        if (player != null) {
            return player.job().orElse(JobRegistry.defaultJob()).displayName().getString();
        }
        return JobRegistry.defaultJob().displayName().getString();
    }

    private static void appendTeamBossSummary(StringBuilder text, SemionGame game) {
        List<SemionTeam> activeTeams = game.teams().values().stream()
                .filter(SemionTeam::active)
                .sorted(Comparator.comparing(SemionTeam::id))
                .toList();
        text.append("<dark_gray>────</dark_gray>\n");
        text.append("<gray>전체 팀 보스</gray>\n");
        for (SemionTeam team : activeTeams) {
            text.append(teamNameText(team.id()))
                    .append(" ")
                    .append(bossHealthText(team))
                    .append('\n');
        }
    }

    private static Optional<SemionTeam> viewingTeam(ServerPlayer viewer, SemionGame game) {
        if (!(viewer.level() instanceof ServerLevel world)) {
            return Optional.empty();
        }
        return game.teamForWorld(world);
    }

    private static String startableText(MinecraftServer server, SemionGame game, MatchMode matchMode) {
        return readyPlan(server, game, matchMode)
                .map(plan -> "<green><bold>가능</bold></green> <dark_gray>(</dark_gray><white>"
                        + plan.activePlayerCount()
                        + "명</white><dark_gray>)</dark_gray>")
                .orElse("<red><bold>대기</bold></red>");
    }

    private static Optional<ParticipantSelectionPlan> readyPlan(
            MinecraftServer server,
            SemionGame game,
            MatchMode matchMode
    ) {
        List<StartCandidate> candidates = server.getPlayerList().getPlayers().stream()
                .map(player -> new StartCandidate(player.getUUID(), player.getGameProfile().getName()))
                .toList();
        return ParticipantSelectionService.selectReady(candidates, game.readyPlayerIds(), matchMode);
    }

    private static String phaseLabel(RoundPhase phase) {
        return switch (phase) {
            case WAITING -> "대기";
            case PREPARE_AND_SUMMON -> "준비/소환";
            case LANE_WAVE -> "웨이브";
            case ROUND_PAYOUT -> "정산";
            case ENDED -> "종료";
        };
    }

    private static String matchModeLabel(MatchMode matchMode) {
        return switch (matchMode) {
            case NORMAL -> "일반";
            case TEST -> "테스트";
        };
    }

    private static String bossHealthText(SemionTeam team) {
        if (team.eliminated()) {
            return "<red><bold>탈락</bold></red>";
        }
        long health = Math.round(team.laneGroup().boss().health());
        long maxHealth = Math.round(team.laneGroup().boss().maxHealth());
        return "<red>" + health + "</red><dark_gray>/</dark_gray><white>" + maxHealth + "</white>";
    }

    private static String towerLimitText(int current, int max) {
        String color = towerLimitColor(current, max);
        return "<" + color + ">" + current + "/" + max + "</" + color + ">";
    }

    private static String towerLimitColor(int current, int max) {
        if (max <= 0) {
            return "red";
        }
        double ratio = (double) current / max;
        if (ratio <= 0.50) {
            return "green";
        }
        if (ratio <= 0.75) {
            return "yellow";
        }
        return "red";
    }

    private static String teamColor(TeamId teamId) {
        return "<" + teamColorName(teamId) + ">";
    }

    private static String teamNameText(TeamId teamId) {
        return teamColor(teamId)
                + teamId.name()
                + "</"
                + teamColorName(teamId)
                + ">";
    }

    private static String teamColorName(TeamId teamId) {
        return switch (teamId) {
            case RED -> "red";
            case BLUE -> "blue";
            case GREEN -> "green";
            case YELLOW -> "yellow";
            case PURPLE -> "light_purple";
        };
    }
}
