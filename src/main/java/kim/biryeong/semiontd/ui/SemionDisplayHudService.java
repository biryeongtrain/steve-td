package kim.biryeong.semiontd.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.dantashader.displayhud.DisplayHud;
import kim.biryeong.dantashader.displayhud.DisplayHud.HudAlignment;
import kim.biryeong.dantashader.displayhud.DisplayHudManager;
import kim.biryeong.dantashader.displayhud.TextDisplayHud;
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
import net.kyori.adventure.platform.modcommon.impl.NonWrappingComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SemionDisplayHudService {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String HUD_ID = "semion-td:status";
    private static final float HUD_X = 1520.0F;
    private static final float HUD_Y = 190.0F;
    public static final float HUD_SCALE_MULTIPLIER = 2.5F;
    private static final float HUD_SIZE = 34.0F * HUD_SCALE_MULTIPLIER;
    private static final int LINE_WIDTH = Math.round(210 * HUD_SCALE_MULTIPLIER);
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private int updateTicker;

    public void tick(MinecraftServer server, SemionGame game, MatchMode matchMode) {
        updateTicker++;
        if (updateTicker % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (game.canConfigureRoster()) {
                update(player, lobbyTextFor(player, game, matchMode, server));
            } else if (game.isActiveParticipant(player.getUUID()) || game.isMatchSpectator(player.getUUID())) {
                update(player, matchTextFor(player, game, matchMode));
            } else {
                remove(player);
            }
        }
    }

    public void clear(MinecraftServer server) {
        updateTicker = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            remove(player);
        }
    }

    public void remove(ServerPlayer player) {
        DisplayHud.removeHud(player, HUD_ID);
    }

    public static void refreshPlayerHud(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Map<String, DisplayHud> huds = DisplayHud.getHuds(player);
        for (DisplayHud hud : huds.values()) {
            hud.teleport();
            hud.mount();
        }
    }

    private void update(ServerPlayer player, String text) {
        update(player, Component.literal(text));
    }

    private void update(ServerPlayer player, Component text) {
        TextDisplayHud hud = hud(player);
        hud.setText(text);
    }

    private TextDisplayHud hud(ServerPlayer player) {
        DisplayHud existing = DisplayHud.getHud(player, HUD_ID);
        if (existing instanceof TextDisplayHud textHud) {
            return textHud;
        }
        if (existing != null) {
            existing.remove();
        }

        DisplayHudManager.configure(1.0F, 1.0F, 1920.0F, 1080.0F, 10_000);
        TextDisplayHud hud = new TextDisplayHud();
        hud.setLineWidth(LINE_WIDTH);
        hud.setShadowToggle(true);
        hud.setScale(HUD_SIZE, HUD_SIZE, 1.0F);
        hud.setViewRange(1000.0F);
        hud.setAlignment(HudAlignment.UNALIGNED);
        hud.setLocation(HUD_X, HUD_Y, 0.0F);
        hud.spawn(player, HUD_ID);
        return hud;
    }

    private static Component lobbyTextFor(ServerPlayer viewer, SemionGame game, MatchMode matchMode, MinecraftServer server) {
        boolean ready = game.isReady(viewer.getUUID());
        String readyLabel = ready ? "<green><bold>준비 완료</bold></green>" : "<red><bold>미준비</bold></red>";
        int onlinePlayers = server.getPlayerList().getPlayerCount();
        String startableLabel = startableText(server, game, matchMode);
        return miniMessage("""
                <gradient:#67e8f9:#a78bfa><bold>Semion TD</bold></gradient>
                <gray>상태</gray> <yellow>대기 중</yellow>
                <gray>게임 모드</gray> <aqua>%s</aqua>
                <gray>준비 인원</gray> <green>%d</green><dark_gray>/</dark_gray><white>%d</white>
                <gray>준비 상태</gray> %s
                <gray>시작 가능</gray> %s
                """.formatted(matchModeLabel(matchMode), game.readyPlayerCount(), onlinePlayers, readyLabel, startableLabel));
    }

    private static Component miniMessage(String text) {
        return NonWrappingComponentSerializer.INSTANCE.serialize(MINI_MESSAGE.deserialize(text));
    }

    private static Component matchTextFor(ServerPlayer viewer, SemionGame game, MatchMode matchMode) {
        return miniMessage(matchMarkupFor(viewer.getUUID(), viewingTeam(viewer, game), game, matchMode));
    }

    public static String matchMarkupFor(UUID viewerId, Optional<SemionTeam> viewingTeam, SemionGame game, MatchMode matchMode) {
        StringBuilder text = new StringBuilder();
        appendMatchHeader(text, game, matchMode);

        SemionPlayer player = game.players().get(viewerId);
        SemionTeam playerTeam = player == null ? null : game.teams().get(player.teamId());
        if (player != null && playerTeam != null && playerTeam.eliminated()) {
            appendEliminatedPlayerHud(text, player, playerTeam, viewingTeam);
        } else if (player != null) {
            appendActivePlayerHud(text, player, playerTeam);
            appendTeamBossSummary(text, game);
        } else {
            appendSpectatorHud(text, viewingTeam);
        }
        return text.toString();
    }

    private static void appendMatchHeader(StringBuilder text, SemionGame game, MatchMode matchMode) {
        text.append("<gradient:#67e8f9:#a78bfa><bold>Semion TD</bold></gradient>\n");
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

    private static void appendActivePlayerHud(StringBuilder text, SemionPlayer player, SemionTeam team) {
        PlayerEconomy economy = player.economy();
        text.append("<gray>팀/라인</gray> ")
                .append(teamNameText(player.teamId()))
                .append(" <dark_gray>/</dark_gray> <white>")
                .append(player.laneId())
                .append("</white>\n");
        text.append("<gray>다이아</gray> <aqua>")
                .append(economy.diamond())
                .append("</aqua> <gray>에메랄드</gray> <green>")
                .append(economy.emerald())
                .append("</green>\n");
        text.append("<gray>수입</gray> <gold>")
                .append(economy.income())
                .append("</gold> <gray>에메랄드/s</gray> <green>")
                .append(economy.emeraldPerSec())
                .append("</green>\n");
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

    private static java.util.Optional<ParticipantSelectionPlan> readyPlan(
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
        };
    }
}
