package kim.biryeong.semiontd.ui;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.attackDamageText;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.attackRangeText;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.attackSpeedText;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.damageReductionText;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.magicDamageText;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
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
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        return sidebarLinesFor(viewer, game, matchMode, server, false);
    }

    public static List<Component> sidebarLinesFor(
            ServerPlayer viewer,
            SemionGame game,
            MatchMode matchMode,
            MinecraftServer server,
            boolean damageView
    ) {
        if (game.canConfigureRoster()) {
            return components(lobbyMarkupFor(viewer, game, matchMode, server));
        }
        if (damageView && game.playerLane(viewer.getUUID()).isPresent()) {
            return components(damageSidebarMarkupFor(viewer.getUUID(), game));
        }
        if (game.isActiveParticipant(viewer.getUUID()) || game.isMatchSpectator(viewer.getUUID())) {
            return components(matchSidebarMarkupFor(viewer, game, matchMode));
        }
        return List.of();
    }

    public static String damageSidebarMarkupFor(UUID viewerId, SemionGame game) {
        var lane = game.playerLane(viewerId).orElse(null);
        if (lane == null) {
            return "";
        }

        Map<String, TowerDamageSummary> byType = new HashMap<>();
        for (Tower tower : lane.towers()) {
            TowerType type = tower.roundCombatType();
            if (type == null) {
                continue;
            }
            byType.merge(
                    type.id(),
                    new TowerDamageSummary(
                            type.id(),
                            type.displayName(),
                            tower.roundPhysicalDamageDealt(),
                            tower.roundMagicDamageDealt(),
                            tower.roundDamageTaken()
                    ),
                    TowerDamageSummary::merge
            );
        }
        DemonLordState demonLord = DemonLordStates.get(viewerId);
        if (demonLord != null && (demonLord.roundPhysicalDamageDealt() > 0.0
                || demonLord.roundMagicDamageDealt() > 0.0)) {
            byType.put("semion-td:demon_lord", new TowerDamageSummary(
                    "semion-td:demon_lord",
                    "마왕",
                    demonLord.roundPhysicalDamageDealt(),
                    demonLord.roundMagicDamageDealt(),
                    0.0
            ));
        }

        List<TowerDamageSummary> summaries = List.copyOf(byType.values());
        double totalPhysical = summaries.stream().mapToDouble(TowerDamageSummary::physical).sum();
        double totalMagic = summaries.stream().mapToDouble(TowerDamageSummary::magic).sum();
        double totalTaken = summaries.stream().mapToDouble(TowerDamageSummary::taken).sum();
        StringBuilder text = new StringBuilder();
        text.append("<gold>").append(damageRoundLabel(game)).append("</gold>");
        int remainingPrepareSeconds = game.remainingPrepareSeconds();
        if (remainingPrepareSeconds >= 0) {
            text.append(" <dark_gray>|</dark_gray> <gray>준비</gray> <green>")
                    .append(remainingPrepareSeconds)
                    .append("초</green>");
        }
        text.append(" <dark_gray>|</dark_gray> ").append(attackDamageText("🪓 " + formatDamage(totalPhysical)))
                .append(" <dark_gray>|</dark_gray> ").append(magicDamageText("🔥 " + formatDamage(totalMagic)))
                .append(" <dark_gray>|</dark_gray> <aqua>🛡 ").append(formatDamage(totalTaken)).append("</aqua>\n");
        appendCompactNextWavePreview(text, viewerId, game);
        appendDamageTop(text, summaries, true);
        appendDamageTop(text, summaries, false);
        return text.toString();
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
        int currentTowers = game.towerCapacityUsed(player.uuid());
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
            if (game.phase() == RoundPhase.PREPARE_AND_SUMMON) {
                appendNextWavePreview(text, viewerId, game);
            } else {
                appendTeamBossSummary(text, game);
            }
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

    static String formatDamage(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return "0";
        }
        if (value < 1_000.0) {
            return Long.toString(Math.round(value));
        }
        if (value < 1_000_000.0) {
            return compactDamage(value / 1_000.0, "K");
        }
        if (value < 1_000_000_000.0) {
            return compactDamage(value / 1_000_000.0, "M");
        }
        return compactDamage(value / 1_000_000_000.0, "B");
    }

    private static String compactDamage(double value, String suffix) {
        return String.format(Locale.ROOT, "%.1f%s", value, suffix);
    }

    private static void appendDamageTop(StringBuilder text, List<TowerDamageSummary> summaries, boolean dealt) {
        text.append(dealt ? "<red><bold>⚔ 가한 피해 TOP 5</bold></red>\n"
                : "<aqua><bold>🛡 받은 피해 TOP 5</bold></aqua>\n");
        Comparator<TowerDamageSummary> comparator = Comparator
                .comparingDouble((TowerDamageSummary summary) -> dealt ? summary.dealt() : summary.taken())
                .reversed()
                .thenComparing(TowerDamageSummary::displayName)
                .thenComparing(TowerDamageSummary::id);
        List<TowerDamageSummary> top = summaries.stream()
                .filter(summary -> (dealt ? summary.dealt() : summary.taken()) > 0.0)
                .sorted(comparator)
                .limit(5)
                .toList();
        if (top.isEmpty()) {
            text.append("<gray>기록 없음</gray>\n");
            return;
        }
        for (int index = 0; index < top.size(); index++) {
            TowerDamageSummary summary = top.get(index);
            text.append("<gray>").append(index + 1).append(".</gray> <white>")
                    .append(summary.displayName()).append("</white> ");
            if (dealt) {
                text.append(attackDamageText("🪓 " + formatDamage(summary.physical()))).append(' ')
                        .append(magicDamageText("🔥 " + formatDamage(summary.magic()))).append('\n');
            } else {
                text.append("<aqua>🛡 ").append(formatDamage(summary.taken())).append("</aqua>\n");
            }
        }
    }

    private static String damageRoundLabel(SemionGame game) {
        return switch (game.phase()) {
            case LANE_WAVE -> "R" + game.currentRound() + " 실시간";
            case ROUND_PAYOUT, ENDED -> "R" + game.currentRound() + " 최종";
            case PREPARE_AND_SUMMON -> game.currentRound() <= 1
                    ? "R1 시작 전"
                    : "R" + (game.currentRound() - 1) + " 최종";
            case WAITING -> "R1 시작 전";
        };
    }

    private record TowerDamageSummary(String id, String displayName, double physical, double magic, double taken) {
        private double dealt() {
            return physical + magic;
        }

        private TowerDamageSummary merge(TowerDamageSummary other) {
            return new TowerDamageSummary(
                    id,
                    displayName,
                    physical + other.physical,
                    magic + other.magic,
                    taken + other.taken
            );
        }
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

    private static void appendNextWavePreview(StringBuilder text, UUID viewerId, SemionGame game) {
        List<WaveMonsterEntry> entries = game.upcomingWaveEntries(viewerId);
        text.append("<dark_gray>────</dark_gray>\n");
        text.append("<aqua><bold>다음 웨이브</bold></aqua>\n");
        if (entries.isEmpty()) {
            text.append("<gray>정보 없음</gray>\n");
        } else {
            int shown = Math.min(3, entries.size());
            for (int index = 0; index < shown; index++) {
                WaveMonsterEntry entry = entries.get(index);
                text.append("<white>").append(monsterNameMarkup(entry)).append("</white>")
                        .append(" <gray>×").append(entry.count()).append("</gray>")
                        .append(" <red>♥").append(formatDamage(entry.health())).append("</red> ")
                        .append(definingStatMarkup(entry))
                        .append('\n');
            }
            if (entries.size() > shown) {
                text.append("<gray>외 ").append(entries.size() - shown).append("종</gray>\n");
            }
        }
        game.playerLane(viewerId).ifPresent(lane -> {
            if (lane.queuedSummonCount() > 0) {
                text.append("<yellow>추가 소환 ").append(lane.queuedSummonCount()).append("기</yellow>")
                        .append(" <dark_gray>·</dark_gray> <gray>위협 ")
                        .append(formatDamage(lane.queuedSummonThreat())).append("</gray>\n");
            }
        });
    }

    private static void appendCompactNextWavePreview(StringBuilder text, UUID viewerId, SemionGame game) {
        if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return;
        }
        List<WaveMonsterEntry> entries = game.upcomingWaveEntries(viewerId);
        text.append("<aqua>다음</aqua> ");
        if (entries.isEmpty()) {
            text.append("<gray>정보 없음</gray>\n");
            return;
        }
        int totalCount = entries.stream().mapToInt(WaveMonsterEntry::count).sum();
        text.append("<white>").append(monsterNameMarkup(entries.getFirst())).append("</white>");
        if (entries.size() > 1) {
            text.append(" <gray>외 ").append(entries.size() - 1).append("종</gray>");
        }
        text.append(" <dark_gray>·</dark_gray> <gray>").append(totalCount).append("기</gray>");
        game.playerLane(viewerId).ifPresent(lane -> {
            if (lane.queuedSummonCount() > 0) {
                text.append(" <yellow>+소환 ").append(lane.queuedSummonCount()).append("</yellow>");
            }
        });
        text.append('\n');
    }

    private static String monsterNameMarkup(WaveMonsterEntry entry) {
        if (entry.entityType() == null || entry.entityType().isBlank()) {
            return "특수 적";
        }
        ResourceLocation entityType = ResourceLocation.tryParse(entry.entityType());
        if (entityType == null) {
            return "특수 적";
        }
        return "<lang:entity."
                + entityType.getNamespace()
                + "."
                + entityType.getPath().replace('/', '.')
                + ">";
    }

    private static String definingStatMarkup(WaveMonsterEntry entry) {
        if (entry.attackKind() == AttackKind.RANGED) {
            return attackRangeText("사거리 " + formatStat(entry.attackRange()));
        }
        if (entry.movementSpeedMultiplier() >= 1.15) {
            return attackSpeedText("속도 ×" + formatStat(entry.movementSpeedMultiplier()));
        }
        if (entry.armor() > 0.0) {
            return damageReductionText("🛡" + formatStat(entry.armor()));
        }
        return attackDamageText("⚔" + formatStat(entry.attackDamage()));
    }

    private static String formatStat(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
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
