package kim.biryeong.semiontd.ui;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import de.tomalbrc.avatarrenderer.impl.AvatarRenderer;
import de.tomalbrc.avatarrenderer.impl.SkinLoader;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.buildguide.BuildGuideService;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonShop;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowerState;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.trait.SemionTrait;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitRegistry;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsState;
import kim.biryeong.semiontd.statistics.JobStatisticsTotals;
import kim.biryeong.semiontd.statistics.TraitCombinationStatisticsEntry;
import kim.biryeong.semiontd.statistics.TraitTowerStatisticsEntry;
import kim.biryeong.semiontd.ui.dialog.body.AlignedMessage;
import kim.biryeong.semiontd.ui.dialog.body.SplitAlignedMessage;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

public final class SemionDialogService {
    private static final int BODY_WIDTH = 256;
    private static final int TITLE_HEADER_WIDTH = 200;
    private static final int PLAYER_STATUS_WIDTH = 480;
    private static final int JOB_STATISTICS_WIDTH = 460;
    private static final int PLAYER_STATUS_AVATAR_WIDTH = 16;
    private static final int PLAYER_STATUS_NAME_WIDTH = 112;
    private static final int PLAYER_STATUS_NUMBER_WIDTH = 56;
    private static final int PLAYER_STATUS_TOWER_WIDTH = 48;
    private static final int PLAYER_STATUS_TOWER_JOB_GAP = 12;
    private static final int PLAYER_STATUS_JOB_WIDTH = 108;
    private static final int BUTTON_WIDTH = 180;
    private static final int COMPACT_BUTTON_WIDTH = 118;
    private static final int SUMMON_BUTTON_WIDTH = 82;
    private static final int TRAIT_BUTTON_WIDTH = 82;
    private static final int TEAM_TARGET_BUTTON_WIDTH = 82;
    private static final int TEAM_TARGET_COLUMNS = 4;
    private static final int SUMMON_COLUMNS = 5;
    private static final int TRAIT_COLUMNS = 5;
    private static final int SUMMON_PAGE_SIZE = 25;
    private static final int BUILD_GUIDE_PAGE_SIZE = 4;
    private static final DateTimeFormatter STATISTICS_TIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final ConcurrentMap<SmallAvatarKey, Component> SMALL_AVATAR_CACHE = new ConcurrentHashMap<>();
    public void showGameStatus(ServerPlayer player, SemionGame game) {
        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(new HeaderMessage(miniMessage("<gradient:#facc15:#22d3ee><bold>플레이어 현황</bold></gradient>"), PLAYER_STATUS_WIDTH));
        bodies.add(new PlainMessage(playerStatusHeader(), PLAYER_STATUS_WIDTH));
        bodies.add(new PlainMessage(
                miniMessage("<dark_gray>──────────────────────────────────────────────────</dark_gray>"),
                PLAYER_STATUS_WIDTH
        ));
        TeamId currentTeam = null;
        MutableComponent teamBody = null;
        for (PlayerStatusRow row : playerStatusRows(game)) {
            if (row.teamId() != currentTeam) {
                if (teamBody != null) {
                    bodies.add(new PlainMessage(teamBody, PLAYER_STATUS_WIDTH));
                }
                currentTeam = row.teamId();
                teamBody = Component.empty().append(playerStatusTeamHeader(currentTeam));
            }
            teamBody.append("\n").append(playerStatusBody(row));
        }
        if (teamBody != null) {
            bodies.add(new PlainMessage(teamBody, PLAYER_STATUS_WIDTH));
        }
        showActions(player, "세미온 TD 플레이어 현황", bodies, List.of(), 1);
    }

    public static List<PlayerStatusRow> playerStatusRows(SemionGame game) {
        return game.players().values().stream()
                .sorted(Comparator.comparing(SemionPlayer::teamId)
                        .thenComparingInt(SemionPlayer::laneId)
                        .thenComparing(SemionPlayer::name))
                .map(semionPlayer -> {
                    PlayerEconomy economy = semionPlayer.economy();
                    SemionJob job = semionPlayer.job().orElse(JobRegistry.defaultJob());
                    return new PlayerStatusRow(
                            semionPlayer.uuid(),
                            semionPlayer.name(),
                            semionPlayer.teamId(),
                            economy.diamond(),
                            economy.emerald(),
                            economy.income(),
                            game.towerCount(semionPlayer.uuid()),
                            job.displayName().getString()
                    );
                })
                .toList();
    }

    public void showMatchResult(
            ServerPlayer player,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        List<MatchParticipantResult> orderedParticipants = matchResult.participants().stream()
                .sorted(participantComparator())
                .toList();

        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(decoratedHeader(
                miniMessage("<gradient:#facc15:#22d3ee><bold>경기 결과</bold></gradient>"),
                TITLE_HEADER_WIDTH
        ));
        bodies.add(new PlainMessage(
                miniMessage("<gray>최종 라운드:</gray> <white>" + matchResult.finalRound() + "</white>"),
                BODY_WIDTH
        ));
        bodies.add(new PlainMessage(
                miniMessage("<gray>승리 팀:</gray> " + teamListMarkup(matchResult.winningTeams())),
                BODY_WIDTH
        ));

        bodies.add(decoratedHeader(miniMessage("<yellow><bold>참가자 기록</bold></yellow>"), BODY_WIDTH));
        for (MatchParticipantResult participant : orderedParticipants) {
            bodies.add(new PlainMessage(
                    participantResultBody(participant, rewards.get(participant.playerId())),
                    BODY_WIDTH
            ));
            bodies.add(new PlainMessage(Component.literal(" "), BODY_WIDTH));
        }

        List<MatchParticipantResult> losers = matchResult.participants().stream()
                .filter(participant -> !participant.winner())
                .sorted(participantComparator())
                .toList();
        bodies.add(new HeaderMessage(miniMessage("<red><bold>탈락 플레이어</bold></red>"), BODY_WIDTH));
        if (losers.isEmpty()) {
            bodies.add(new AlignedMessage(miniMessage("<gray>- 없음</gray>"), BODY_WIDTH, AlignedMessage.Align.LEFT));
        } else {
            for (MatchParticipantResult participant : losers) {
                bodies.add(new AlignedMessage(miniMessage("<gray>- </gray><white>" + participant.playerName() + "</white>"
                        + " <dark_gray>[</dark_gray>" + teamMarkup(participant.teamId()) + "<dark_gray>]</dark_gray>"),
                        BODY_WIDTH,
                        AlignedMessage.Align.LEFT
                ));
            }
        }

        showActions(player, "세미온 TD 결과", bodies, List.of(), 1);
    }

    public void showLastResult(ServerPlayer player, MatchResult matchResult) {
        showMatchResult(player, matchResult, Map.of());
    }

    public void showJobSelection(ServerPlayer player, SemionGame game) {
        SemionJob currentJob = game.selectedJobOrDefault(player.getUUID());
        String body = "<gradient:#67e8f9:#a78bfa><bold>직업 선택</bold></gradient>\n"
                + "<gray>현재 선택</gray> <yellow>" + currentJob.displayName().getString() + "</yellow>\n"
                + "<gray>버튼에 마우스를 올려 직업 특성을 확인하세요.</gray>";
        ArrayList<ActionButton> actions = new ArrayList<>();
        for (SemionJob job : JobRegistry.all()) {
            actions.add(jobButton(job, currentJob.id().equals(job.id())));
        }

        showActions(player, "세미온 TD 직업", body, actions, 2);
    }

    public void showJobStatistics(
            ServerPlayer player,
            JobStatisticsSnapshot snapshot,
            JobStatisticsState state
    ) {
        if (snapshot.participantAppearances() == 0L && state == JobStatisticsState.LOADING) {
            show(player, "세미온 TD 직업 통계", "<yellow>직업 통계를 집계하고 있습니다.</yellow>");
            return;
        }
        if (snapshot.participantAppearances() == 0L && state == JobStatisticsState.FAILED) {
            show(player, "세미온 TD 직업 통계", "<red>직업 통계를 불러오지 못했습니다.</red>");
            return;
        }

        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(decoratedHeader(
                miniMessage("<gradient:#67e8f9:#facc15><bold>직업 통계</bold></gradient>"),
                JOB_STATISTICS_WIDTH
        ));
        bodies.add(new PlainMessage(statisticsOverview(snapshot), JOB_STATISTICS_WIDTH));
        if (state == JobStatisticsState.LOADING) {
            bodies.add(new PlainMessage(
                    Component.literal("재집계 중입니다. 마지막 정상 통계를 표시합니다.").withStyle(ChatFormatting.YELLOW),
                    JOB_STATISTICS_WIDTH
            ));
        } else if (state == JobStatisticsState.FAILED) {
            bodies.add(new PlainMessage(
                    Component.literal("최근 갱신에 실패했습니다. 마지막 정상 통계를 표시합니다.").withStyle(ChatFormatting.RED),
                    JOB_STATISTICS_WIDTH
            ));
        }
        bodies.add(new HeaderMessage(Component.literal("직업별 요약").withStyle(ChatFormatting.YELLOW), JOB_STATISTICS_WIDTH));

        ArrayList<ActionButton> actions = new ArrayList<>();
        for (JobStatisticsRow row : jobStatisticsRows(snapshot)) {
            bodies.add(new PlainMessage(jobStatisticsSummaryLine(snapshot, row), JOB_STATISTICS_WIDTH));
            bodies.add(new PlainMessage(jobStatisticsRoundMilestonesLine(row), JOB_STATISTICS_WIDTH));
            actions.add(jobStatisticsButton(row));
        }
        showActions(player, "세미온 TD 직업 통계", bodies, actions, 3);
    }

    public void showJobStatisticsDetail(
            ServerPlayer player,
            JobStatisticsSnapshot snapshot,
            JobStatisticsState state,
            String jobId
    ) {
        if (snapshot.participantAppearances() == 0L && state == JobStatisticsState.LOADING) {
            show(player, "세미온 TD 직업 통계", "<yellow>직업 통계를 집계하고 있습니다.</yellow>");
            return;
        }
        if (snapshot.participantAppearances() == 0L && state == JobStatisticsState.FAILED) {
            show(player, "세미온 TD 직업 통계", "<red>직업 통계를 불러오지 못했습니다.</red>");
            return;
        }
        JobStatisticsRow row = jobStatisticsRows(snapshot).stream()
                .filter(candidate -> candidate.jobId().equals(jobId))
                .findFirst()
                .orElse(null);
        if (row == null) {
            show(player, "세미온 TD 직업 통계", "<red>해당 직업 통계를 찾을 수 없습니다.</red>");
            return;
        }

        JobStatisticsEntry entry = row.entry();
        JobStatisticsTotals totals = entry.totals();
        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(decoratedHeader(
                Component.literal(row.displayName() + " 통계").withStyle(ChatFormatting.AQUA),
                JOB_STATISTICS_WIDTH
        ));
        if (state != JobStatisticsState.READY && snapshot.participantAppearances() > 0L) {
            bodies.add(new PlainMessage(
                    Component.literal("마지막 정상 통계를 표시합니다.").withStyle(ChatFormatting.YELLOW),
                    JOB_STATISTICS_WIDTH
            ));
        }

        addStatisticsSection(bodies, "표본");
        addStatisticsLine(bodies, "선택", formatCount(entry.appearances()) + "회");
        addStatisticsLine(bodies, "선택률", formatPercent(snapshot.selectionRate(entry)));
        addStatisticsLine(bodies, "승리", formatCount(entry.wins()) + "승 "
                + formatCount(entry.appearances() - entry.wins()) + "패");
        addStatisticsLine(bodies, "승률", formatPercent(entry.winRate()));
        addStatisticsLine(bodies, "평균 순위", formatAverage(entry.averagePlacement(), "위"));
        addStatisticsLine(bodies, "평균 최종 라운드", formatRound(entry.averageFinalRound()));
        if (entry.appearances() > 0L) {
            addStatisticsLine(bodies, "기록 기간", formatTime(entry.firstMatchAtEpochMillis())
                    + " ~ " + formatTime(entry.lastMatchAtEpochMillis()));
        }

        addStatisticsSection(bodies, "라운드 통과율");
        if (entry.appearances() == 0L) {
            addStatisticsLine(bodies, "R1~R40", "-");
        } else {
            bodies.add(new PlainMessage(jobStatisticsRoundTable(entry), JOB_STATISTICS_WIDTH));
        }

        addTraitCombinationStatistics(bodies, snapshot.traitCombinationsForJob(jobId), entry.appearances());

        addStatisticsSection(bodies, "전투");
        addStatisticsLine(bodies, "평균 처치", formatAverage(entry.averageValue(totals.monsterKills()), ""));
        addStatisticsLine(bodies, "평균 처치 미네랄", formatAverage(entry.averageValue(totals.killMinerals()), ""));

        addStatisticsSection(bodies, "인컴·소환");
        addStatisticsLine(bodies, "평균 소환", formatAverage(entry.averageValue(totals.summonedMonsters()), ""));
        addStatisticsLine(bodies, "평균 최종 인컴", formatAverage(entry.averageValue(totals.finalIncome()), ""));
        addStatisticsLine(bodies, "평균 생성 인컴", formatAverage(entry.averageValue(totals.incomeGenerated()), ""));
        addStatisticsLine(bodies, "평균 보낸 위협", formatAverage(entry.averageValue(totals.sentIncomeThreat()), ""));
        addStatisticsLine(bodies, "평균 공격 성공 위협", formatAverage(
                entry.averageValue(totals.incomeAttackSuccessThreat()), ""));
        addStatisticsLine(bodies, "인컴 공격 성공률", formatPercent(entry.incomeAttackSuccessRate()));
        addStatisticsLine(bodies, "평균 받은 인컴 위협", formatAverage(
                entry.averageValue(totals.incomingIncomeThreat()), ""));

        addStatisticsSection(bodies, "라인 방어·지원");
        addStatisticsLine(bodies, "평균 라인 유입 위협", formatAverage(
                entry.averageValue(totals.ownLaneIncomingThreat()), ""));
        addStatisticsLine(bodies, "평균 누수 위협", formatAverage(
                entry.averageValue(totals.ownLaneLeakedThreat()), ""));
        addStatisticsLine(bodies, "방어율", formatPercent(entry.defenseSuccessRate()));
        addStatisticsLine(bodies, "평균 본인 라인 다이아", formatAverage(
                entry.averageValue(totals.ownLaneDiamondGain()), ""));
        addStatisticsLine(bodies, "평균 지원 다이아", formatAverage(
                entry.averageValue(totals.assistClearDiamondGain()), ""));
        addStatisticsLine(bodies, "평균 지원 정리 위협", formatAverage(
                entry.averageValue(totals.assistClearThreat()), ""));

        showActions(
                player,
                "세미온 TD " + row.displayName() + " 통계",
                bodies,
                List.of(actionButton("전체 통계", "/semiontd job stats", "직업 통계 목록으로 돌아갑니다.")),
                1
        );
    }

    public static List<JobStatisticsRow> jobStatisticsRows(JobStatisticsSnapshot snapshot) {
        LinkedHashMap<String, JobStatisticsEntry> remaining = new LinkedHashMap<>();
        for (JobStatisticsEntry entry : snapshot.jobs()) {
            remaining.put(entry.jobId(), entry);
        }

        ArrayList<JobStatisticsRow> rows = new ArrayList<>();
        for (SemionJob job : JobRegistry.all()) {
            String jobId = job.id().toString();
            JobStatisticsEntry entry = remaining.remove(jobId);
            rows.add(new JobStatisticsRow(
                    jobId,
                    job.displayName().getString(),
                    entry == null ? emptyJobStatisticsEntry(jobId) : entry,
                    true
            ));
        }
        remaining.values().stream()
                .sorted(Comparator.comparing(JobStatisticsEntry::jobId))
                .map(entry -> new JobStatisticsRow(entry.jobId(), entry.jobId(), entry, false))
                .forEach(rows::add);
        return List.copyOf(rows);
    }

    public void showTraitSelection(ServerPlayer player, TraitLoadout loadout, int secondsRemaining) {
        String selectionTime = secondsRemaining < 0 ? "제한 없음" : secondsRemaining + "초";
        String reopenHint = secondsRemaining < 0
                ? "<gray>샌드박스에서는 /특성으로 언제든 다시 열 수 있습니다.</gray>"
                : "<gray>창을 닫아도 제한 시간 안에는 /특성으로 다시 열 수 있습니다.</gray>";
        String body = "<gradient:#67e8f9:#a78bfa><bold>특성 선택</bold></gradient>\n"
                + "<gray>주특성 100%</gray> <yellow>" + traitName(loadout.primaryTraitId()) + "</yellow>"
                + " <dark_gray>·</dark_gray> <aqua>" + traitEffectSummary(loadout.primaryTraitId(), TraitSlot.PRIMARY) + "</aqua>\n"
                + "<gray>부특성 50%</gray> <yellow>" + traitName(loadout.secondaryTraitId()) + "</yellow>"
                + " <dark_gray>·</dark_gray> <aqua>" + traitEffectSummary(loadout.secondaryTraitId(), TraitSlot.SECONDARY) + "</aqua>\n"
                + "<gray>남은 시간</gray> <aqua>" + selectionTime + "</aqua>\n"
                + "<gray>아래 버튼으로 주특성/부특성을 각각 선택하세요.</gray>\n"
                + reopenHint;
        ArrayList<ActionButton> actions = new ArrayList<>();
        actions.add(actionButton(
                "주특성 선택/변경",
                "/semiontd trait ui primary",
                "100% 효과로 적용할 주특성을 선택합니다."
        ));
        actions.add(actionButton(
                "부특성 선택/변경",
                "/semiontd trait ui secondary",
                "50% 효과로 적용할 부특성을 선택합니다."
        ));

        showActions(player, "세미온 TD 특성", body, actions, 2);
    }

    public void showAppliedTraits(ServerPlayer player, TraitLoadout loadout) {
        String body = "<gradient:#67e8f9:#a78bfa><bold>현재 적용 중인 특성</bold></gradient>\n"
                + "<gray>주특성 100%</gray> <yellow>" + traitName(loadout.primaryTraitId()) + "</yellow>"
                + " <dark_gray>·</dark_gray> <aqua>" + traitEffectSummary(loadout.primaryTraitId(), TraitSlot.PRIMARY) + "</aqua>\n"
                + "<gray>부특성 50%</gray> <yellow>" + traitName(loadout.secondaryTraitId()) + "</yellow>"
                + " <dark_gray>·</dark_gray> <aqua>" + traitEffectSummary(loadout.secondaryTraitId(), TraitSlot.SECONDARY) + "</aqua>\n\n"
                + "<gray>이번 게임에 실제 적용된 특성입니다.</gray>";
        show(player, "세미온 TD 현재 특성", body);
    }

    public void showTraitSelection(ServerPlayer player, TraitLoadout loadout, int secondsRemaining, TraitSlot slot) {
        String selectionTime = secondsRemaining < 0 ? "제한 없음" : secondsRemaining + "초";
        String reopenHint = secondsRemaining < 0
                ? "<gray>샌드박스에서는 /특성으로 언제든 다시 열 수 있습니다.</gray>"
                : "<gray>창을 닫아도 제한 시간 안에는 /특성으로 다시 열 수 있습니다.</gray>";
        String body = "<gradient:#67e8f9:#a78bfa><bold>" + slot.displayName() + " 선택</bold></gradient>\n"
                + "<gray>현재 " + slot.displayName() + "</gray> <yellow>" + traitName(loadout.traitId(slot)) + "</yellow> "
                + "<gray>(" + Math.round(slot.effectScale() * 100.0D) + "%)</gray>"
                + " <dark_gray>·</dark_gray> <aqua>" + traitEffectSummary(loadout.traitId(slot), slot) + "</aqua>\n"
                + "<gray>주특성</gray> <yellow>" + traitName(loadout.primaryTraitId()) + "</yellow> <gray>(100%)</gray>\n"
                + "<gray>부특성</gray> <yellow>" + traitName(loadout.secondaryTraitId()) + "</yellow> <gray>(50%)</gray>\n"
                + "<gray>남은 시간</gray> <aqua>" + selectionTime + "</aqua>\n"
                + "<gray>같은 non-none 특성은 주/부특성에 동시에 선택할 수 없습니다.</gray>\n"
                + "<gray>버튼에 마우스를 올리면 효과와 설명이 표시됩니다.</gray>\n"
                + reopenHint;
        ArrayList<ActionButton> actions = new ArrayList<>();
        for (SemionTrait trait : TraitRegistry.all()) {
            actions.add(traitButton(trait, slot, loadout.traitId(slot).equals(trait.id())));
        }

        showActions(
                player,
                "세미온 TD " + slot.displayName(),
                body,
                actions,
                actionButton("뒤로", "/semiontd trait ui", "특성 선택 요약으로 돌아갑니다."),
                TRAIT_COLUMNS
        );
    }

    public void showTowerControl(ServerPlayer player, SemionGame game) {
        showTowerControl(player, game, null);
    }

    public void showTowerControl(ServerPlayer player, SemionGame game, BuildGuideService buildGuideService) {
        var semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer == null) {
            show(player, "세미온 TD 타워", "<red>현재 게임 참가자가 아닙니다.</red>");
            return;
        }

        PlayerEconomy economy = semionPlayer.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        long nextTowerLimitDiamondCost = game.nextTowerLimitPurchaseDiamondCost(player.getUUID());
        long nextTowerLimitEmeraldCost = game.nextTowerLimitPurchaseEmeraldCost(player.getUUID());
        int towerCount = game.towerCount(player.getUUID());
        int towerLimit = game.towerLimitForPlayer(player.getUUID());
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 관리</bold></gradient>\n");
        body.append("<gray>라인</gray> <white>").append(semionPlayer.teamId()).append(" #").append(semionPlayer.laneId()).append("</white>\n");
        body.append("<gray>다이아</gray> <aqua>").append(economy.diamond()).append("</aqua> ");
        body.append("<gray>에메랄드</gray> <green>").append(economy.emerald()).append("</green>\n");
        body.append("<gray>에메랄드/초</gray> <green>").append(economy.emeraldPerSec()).append("</green>");
        body.append(" <dark_gray>|</dark_gray> <gray>다음 인컴 업글</gray> <gold>")
                .append(nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대")
                .append("</gold>\n");
        body.append("<gray>타워 수</gray> <yellow>").append(towerCount).append("/").append(towerLimit).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>다음 타워 수 구매</gray> <gold>")
                .append(formatTowerLimitPurchaseCost(nextTowerLimitDiamondCost, nextTowerLimitEmeraldCost))
                .append("</gold>\n\n");
        body.append("<gray>보조 기능</gray>\n");
        body.append(commandLink("인컴 업그레이드", "/semiontd emeraldup", "green"));
        body.append("  ");
        body.append(commandLink("타워 수 +" + game.economyConfig().towerLimit().purchaseIncreaseAmount(), "/semiontd tower limitup", "yellow"));
        body.append("  ");
        body.append(commandLink("상태 보기", "/semiontd ui", "aqua"));
        body.append("\n\n<dark_gray>────────────────────────</dark_gray>\n\n");

        Tower selectedTower = game.playerLane(player.getUUID())
                .flatMap(lane -> TowerPlacementPositions.resolveGrid(lane, player.blockPosition())
                        .map(lane::towerAt))
                .orElse(null);
        if (selectedTower != null) {
            body.append("<yellow>현재 위치 타워</yellow> <white>").append(selectedTower.type().displayName()).append("</white>\n");
            body.append("<gray>레벨</gray> <white>").append(selectedTower.level()).append("</white>");
            body.append(" <gray>체력</gray> <red>").append(Math.round(selectedTower.health())).append("</red>");
            body.append("<dark_gray>/</dark_gray><red>").append(Math.round(selectedTower.currentMaxHealth())).append("</red>");
            body.append(" <gray>판매 환불</gray> <gold>").append(selectedTower.sellRefundAmount()).append("</gold>\n\n");
        }

        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerService.availableTowers(game, player.getUUID());
        List<TowerUpgradeOption> upgrades = selectedTower == null
                ? List.of()
                : ProductionTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (!upgrades.isEmpty()) {
            body.append("<gray>아래 버튼에서 업그레이드를 선택하세요.</gray>\n");
        } else if (selectedTower != null) {
            body.append("<gray>현재 위치 타워는 더 이상 업그레이드할 수 없습니다.</gray>\n");
        } else if (entries.isEmpty()) {
            body.append("<red>사용할 수 있는 타워가 없습니다.</red>\n");
        } else {
            body.append("<gray>건설 후보</gray> <yellow>").append(entries.size()).append("</yellow>");
            body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (selectedTower == null) {
            for (ProductionTowerCatalog.CatalogEntry entry : entries) {
                long mineralCost = Math.max(0, entry.type().mineralCost());
                boolean recommended = buildGuideService != null && TowerPlacementPositions.resolveGrid(game.playerLane(player.getUUID()).orElse(null), player.blockPosition())
                        .map(position -> buildGuideService.isRecommendedTower(game, player.getUUID(), game.currentRound(), position, entry.type().id()))
                        .orElse(false);
                actions.add(towerButton(entry, mineralCost, economy.diamond() >= mineralCost, recommended));
            }
        } else {
            for (TowerUpgradeOption option : upgrades) {
                boolean mineralAffordable = economy.diamond() >= option.mineralCost();
                boolean recommended = buildGuideService != null
                        && buildGuideService.isRecommendedUpgrade(game, player.getUUID(), game.currentRound(), selectedTower.position(), option.id());
                actions.add(actionButton(
                        upgradeButtonLabel(option, mineralAffordable && advExperienceAffordable(selectedTower, option), recommended),
                        "/semiontd tower upgrade " + option.id(),
                        upgradeTooltip(option, mineralAffordable, recommended, selectedTower),
                        COMPACT_BUTTON_WIDTH
                ));
            }
        }
        showActions(player, "세미온 TD 타워", body.toString(), actions, 3);
    }

    public void showTowerDetails(ServerPlayer player, SemionGame game, Tower tower) {
        showTowerDetails(player, game, tower, null);
    }

    public void showTowerDetails(ServerPlayer player, SemionGame game, Tower tower, BuildGuideService buildGuideService) {
        showTowerDetails(player, game, tower, buildGuideService, null);
    }

    public void showTowerDetails(
            ServerPlayer player,
            SemionGame game,
            Tower tower,
            BuildGuideService buildGuideService,
            SemionTowerEntity knownTowerEntity
    ) {
        if (tower == null) {
            show(player, "세미온 TD 타워", "<red>타워 정보를 찾을 수 없습니다.</red>");
            return;
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        boolean ownedByPlayer = tower.ownerPlayer().equals(player.getUUID());
        boolean sameLane = semionPlayer != null
                && semionPlayer.teamId() == tower.teamId()
                && semionPlayer.laneId() == tower.laneId();
        Optional<SemionTowerEntity> towerEntity = knownTowerEntity == null
                ? towerEntity(game, tower)
                : Optional.of(knownTowerEntity);
        EndTower previewEndTower = tower instanceof EndTower endTower
                && endTower.state() == EndTowerState.EGG
                ? endTower
                : null;
        Optional<SemionTowerEntity> combatStatsEntity = previewEndTower != null
                ? Optional.empty()
                : towerEntity;
        double currentDamage = previewEndTower != null
                ? previewEndTower.previewHatchedAttackDamage()
                : combatStatsEntity
                        .map(entity -> entity.applyTraitOutgoingDamage(null, entity.attackDamageAmount(null)))
                        .orElseGet(() -> tower.modifyAttackDamage(null, null, tower.type().damage()));
        double currentRange = previewEndTower != null
                ? previewEndTower.previewHatchedAttackRange()
                : combatStatsEntity
                        .map(SemionTowerEntity::attackRange)
                        .orElse(tower.type().range());
        int currentAttackIntervalTicks = previewEndTower != null
                ? previewEndTower.previewHatchedAttackIntervalTicks()
                : combatStatsEntity
                        .map(SemionTowerEntity::attackIntervalTicks)
                        .orElseGet(() -> tower.adjustAttackInterval(tower.type().attackIntervalTicks()));
        double currentMaxHealth = previewEndTower != null
                ? previewEndTower.previewHatchedMaxHealth()
                : tower.currentMaxHealth();

        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 상세 정보</bold></gradient>\n");
        body.append("<white><bold>").append(tower.type().displayName()).append("</bold></white>\n");
        body.append("<gray>🏷 팀</gray> <white>").append(tower.teamId()).append("</white>");
        body.append(" <dark_gray>|</dark_gray> <gray>라인</gray> <yellow>#").append(tower.laneId()).append("</yellow>\n");
        body.append("<gray>📍 위치</gray> <white>")
                .append(tower.position().x()).append(", ")
                .append(tower.position().y()).append(", ")
                .append(tower.position().z()).append("</white>");
        body.append(" <dark_gray>|</dark_gray> <gray>⭐ 레벨</gray> <gold>").append(tower.level()).append("</gold>\n");
        body.append("<red>❤ 체력 ").append(Math.round(tower.health())).append("</red>");
        body.append("<dark_gray>/</dark_gray><red>").append(Math.round(currentMaxHealth)).append("</red>")
                .append(statDeltaSuffix(tower.type().maxHealth(), currentMaxHealth, true));
        body.append(" <yellow>🧲 어그로 ").append(tower.type().aggroPriority()).append("</yellow>\n");
        body.append("<dark_red>⚔ 피해 ").append(oneDecimal(currentDamage)).append("</dark_red>")
                .append(statDeltaSuffix(tower.type().damage(), currentDamage, true));
        body.append(" <light_purple>🎯 사거리 ").append(oneDecimal(currentRange)).append("</light_purple>")
                .append(statDeltaSuffix(tower.type().range(), currentRange, true))
                .append('\n');
        double baseAttacksPerSecond = 20.0 / Math.max(1, tower.type().attackIntervalTicks());
        double currentAttacksPerSecond = 20.0 / Math.max(1, currentAttackIntervalTicks);
        body.append("<green>⚡ 공속 ").append(oneDecimal(currentAttacksPerSecond)).append("/초</green>")
                .append(statDeltaSuffix(baseAttacksPerSecond, currentAttacksPerSecond, true));
        body.append(" <dark_gray>(</dark_gray><gray>").append(currentAttackIntervalTicks).append("틱</gray><dark_gray>)</dark_gray>\n");
        towerEntity.ifPresent(entity -> appendTowerTimedEffects(body, entity));
        appendTowerRuntimeDetails(body, tower);
        body.append("<aqua>💎 판매 환불 ").append(tower.sellRefundAmount()).append(" 다이아</aqua>\n");
        appendTowerDescription(body, tower.type().description());
        if (!ownedByPlayer) {
            body.append("\n<red>자신이 설치한 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        } else if (!sameLane) {
            body.append("\n<red>현재 담당 라인의 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (ownedByPlayer && sameLane) {
            List<TowerUpgradeOption> upgrades = ProductionTowerService.availableUpgrades(game, player.getUUID(), tower.position());
            for (TowerUpgradeOption option : upgrades) {
                boolean mineralAffordable = semionPlayer.economy().diamond() >= option.mineralCost();
                boolean recommended = buildGuideService != null
                        && buildGuideService.isRecommendedUpgrade(game, player.getUUID(), game.currentRound(), tower.position(), option.id());
                actions.add(actionButton(
                        upgradeButtonLabel(option, mineralAffordable && advExperienceAffordable(tower, option), recommended),
                        "/semiontd tower upgrade "
                                + option.id() + " "
                                + tower.position().x() + " "
                                + tower.position().y() + " "
                                + tower.position().z(),
                        upgradeTooltip(option, mineralAffordable, recommended, tower),
                        COMPACT_BUTTON_WIDTH
                ));
            }
            actions.add(actionButton(
                    "판매",
                    "/semiontd tower sell "
                            + tower.position().x() + " "
                            + tower.position().y() + " "
                            + tower.position().z(),
                    Component.literal("이 타워를 판매하고 환불을 받습니다."),
                    BUTTON_WIDTH
            ));
        }
        showActions(player, "세미온 TD 타워 상세", body.toString(), actions, 2);
    }

    public void showDebugTowerControl(ServerPlayer player) {
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 관리</bold></gradient>\n");
        body.append("<gray>건설 후보</gray> <yellow>")
                .append(ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count())
                .append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n");

        List<ActionButton> actions = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(entry -> towerButton(entry, entry.type().mineralCost(), true, false))
                .toList();
        showActions(player, "세미온 TD 타워", body.toString(), actions, 3);
    }

    public void showBuildGuides(ServerPlayer player, BuildGuideService buildGuideService, SemionPlayerProfile profile) {
        showBuildGuides(player, buildGuideService, profile, 1, 1, false);
    }

    public void showBuildGuides(ServerPlayer player, BuildGuideService buildGuideService, SemionPlayerProfile profile, int publicPage, int myPage) {
        showBuildGuides(player, buildGuideService, profile, publicPage, myPage, false);
    }

    public void showDebugBuildGuides(ServerPlayer player, BuildGuideService buildGuideService, SemionPlayerProfile profile) {
        showBuildGuides(player, buildGuideService, profile, 1, 1, true);
    }

    private void showBuildGuides(ServerPlayer player, BuildGuideService buildGuideService, SemionPlayerProfile profile, int publicPage, int myPage, boolean includeDebugGuides) {
        Optional<BuildGuide> tracked = buildGuideService.trackedGuide(player.getUUID())
                .filter(guide -> includeDebugGuides || !BuildGuideService.isDebugGuide(guide));
        List<BuildGuide> allPublicGuides = includeDebugGuides ? buildGuideService.debugPublicGuides() : buildGuideService.publicGuides();
        List<BuildGuide> allMyGuides = includeDebugGuides
                ? allPublicGuides.stream().filter(guide -> guide.ownedBy(player.getUUID())).toList()
                : buildGuideService.myGuides(player.getUUID());
        int publicPageCount = buildGuidePageCount(allPublicGuides.size());
        int myPageCount = buildGuidePageCount(allMyGuides.size());
        int safePublicPage = clampPage(publicPage, publicPageCount);
        int safeMyPage = clampPage(myPage, myPageCount);
        List<BuildGuide> publicGuides = buildGuidePage(allPublicGuides, safePublicPage);
        List<BuildGuide> myGuides = buildGuidePage(allMyGuides, safeMyPage);
        List<BuildGuide> recentGuides = (includeDebugGuides ? buildGuideService.debugRecentGuides(profile.recentBuildCodes()) : buildGuideService.recentGuides(player.getUUID(), profile.recentBuildCodes()))
                .stream()
                .limit(5)
                .toList();

        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(new HeaderMessage(miniMessage("<gradient:#60a5fa:#22c55e><bold>빌드 공유</bold></gradient>"), TITLE_HEADER_WIDTH));
        bodies.add(new AlignedMessage(
                miniMessage("<gray>공개 빌드, 최근 본 빌드, 현재 추적 빌드를 선택합니다.</gray>"),
                BODY_WIDTH,
                AlignedMessage.Align.LEFT
        ));
        bodies.add(new HeaderMessage(miniMessage("<aqua>현재 추적</aqua>"), BODY_WIDTH));
        appendBuildGuideBody(bodies, tracked.orElse(null), "없음", true);
        bodies.add(new HeaderMessage(miniMessage("<light_purple>내 빌드 " + safeMyPage + "/" + myPageCount + "</light_purple>"), BODY_WIDTH));
        appendBuildGuideBodies(bodies, myGuides, "없음");
        bodies.add(new HeaderMessage(miniMessage("<yellow>공개 빌드 " + safePublicPage + "/" + publicPageCount + "</yellow>"), BODY_WIDTH));
        appendBuildGuideBodies(bodies, publicGuides, "없음");
        bodies.add(new HeaderMessage(miniMessage("<green>최근 본 빌드</green>"), BODY_WIDTH));
        appendBuildGuideBodies(bodies, recentGuides, "없음");

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (!includeDebugGuides) {
            if (safeMyPage > 1) {
                actions.add(actionButton("내 이전", buildListCommand(safePublicPage, safeMyPage - 1), Component.literal("내 빌드 이전 페이지"), COMPACT_BUTTON_WIDTH));
            }
            if (safeMyPage < myPageCount) {
                actions.add(actionButton("내 다음", buildListCommand(safePublicPage, safeMyPage + 1), Component.literal("내 빌드 다음 페이지"), COMPACT_BUTTON_WIDTH));
            }
            if (safePublicPage > 1) {
                actions.add(actionButton("공개 이전", buildListCommand(safePublicPage - 1, safeMyPage), Component.literal("공개 빌드 이전 페이지"), COMPACT_BUTTON_WIDTH));
            }
            if (safePublicPage < publicPageCount) {
                actions.add(actionButton("공개 다음", buildListCommand(safePublicPage + 1, safeMyPage), Component.literal("공개 빌드 다음 페이지"), COMPACT_BUTTON_WIDTH));
            }
        }
        showActions(player, "세미온 TD 빌드", bodies, actions, 2);
    }

    public void showBuildGuideDetails(ServerPlayer player, BuildGuide guide) {
        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(new HeaderMessage(miniMessage("<gradient:#60a5fa:#22c55e><bold>" + guide.title() + "</bold></gradient>"), BODY_WIDTH));
        bodies.add(new SplitAlignedMessage(
                miniMessage("<blue><bold>" + guide.code() + "</bold></blue>"
                        + " <dark_gray>|</dark_gray> <gray>작성자</gray> <yellow>" + guide.authorName() + "</yellow>\n"
                        + "<gray>직업</gray> <white>" + guide.jobId() + "</white>"
                        + " <dark_gray>|</dark_gray> <gray>특성</gray> <light_purple>"
                        + traitName(guide.traitLoadout().primaryTraitId()) + " / "
                        + traitName(guide.traitLoadout().secondaryTraitId()) + "</light_purple>"
                        + " <dark_gray>|</dark_gray> <gray>최종 라운드</gray> <aqua>" + guide.finalRound() + "</aqua>"
                        + " <dark_gray>|</dark_gray> <gray>행동</gray> <green>" + guide.actions().size() + "</green>"
                        + " <dark_gray>|</dark_gray> <gray>상태</gray> " + visibilityMarkup(guide)),
                miniMessage(commandLink("추적", "/semiontd-internal build track " + guide.code(), "blue")),
                BODY_WIDTH
        ));

        Map<Integer, List<kim.biryeong.semiontd.buildguide.BuildAction>> byRound = guide.actions().stream()
                .collect(Collectors.groupingBy(
                        kim.biryeong.semiontd.buildguide.BuildAction::round,
                        java.util.TreeMap::new,
                        Collectors.toList()
                ));
        if (byRound.isEmpty()) {
            bodies.add(new HeaderMessage(miniMessage("<gray>행동</gray>"), BODY_WIDTH));
            bodies.add(new AlignedMessage(miniMessage("<gray>기록된 행동이 없습니다.</gray>"), BODY_WIDTH, AlignedMessage.Align.LEFT));
        } else {
            for (Map.Entry<Integer, List<kim.biryeong.semiontd.buildguide.BuildAction>> entry : byRound.entrySet()) {
                bodies.add(new HeaderMessage(miniMessage("<yellow>라운드 " + entry.getKey() + "</yellow>"), BODY_WIDTH));
                for (kim.biryeong.semiontd.buildguide.BuildAction action : entry.getValue()) {
                    bodies.add(buildActionBody(action));
                }
            }
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (guide.ownedBy(player.getUUID())) {
            boolean visible = guide.isPublic();
            actions.add(actionButton(
                    Component.literal(visible ? "비공개" : "공개").withStyle(visible ? ChatFormatting.GRAY : ChatFormatting.GREEN),
                    "/semiontd-internal build " + (visible ? "private " : "public ") + guide.code(),
                    Component.literal(visible ? "내 빌드를 비공개로 전환합니다." : "내 빌드를 공개 목록에 올립니다."),
                    COMPACT_BUTTON_WIDTH
            ));
            actions.add(actionButton(
                    Component.literal("삭제").withStyle(ChatFormatting.RED),
                    "/semiontd-internal build delete " + guide.code(),
                    Component.literal("내 빌드를 삭제합니다."),
                    COMPACT_BUTTON_WIDTH
            ));
        }
        actions.add(actionButton(
                Component.literal("목록").withStyle(ChatFormatting.AQUA),
                "/빌드 목록",
                Component.literal("빌드 목록으로 돌아갑니다."),
                BUTTON_WIDTH
        ));
        showActions(player, "세미온 TD 빌드 상세", bodies, actions, 2);
    }

    public void showSummonShop(ServerPlayer player, SemionGame game) {
        showSummonShop(player, game, 1);
    }

    public void showLeaderTargetControl(ServerPlayer player, SemionGame game) {
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer == null) {
            show(player, "세미온 TD 팀장", "<red>현재 게임 참가자가 아닙니다.</red>");
            return;
        }
        SemionTeam team = game.teams().get(semionPlayer.teamId());
        if (team == null || !team.hasLeader(player.getUUID()) || team.leaderTargeting().isEmpty()) {
            show(player, "세미온 TD 팀장", "<red>팀장만 타깃을 지정할 수 있습니다.</red>");
            return;
        }

        var leaderTargeting = team.leaderTargeting().orElseThrow();
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#fb923c><bold>팀장 타깃 지정</bold></gradient>\n");
        body.append("<gray>내 팀</gray> ").append(teamMarkup(semionPlayer.teamId())).append("\n");
        body.append("<gray>현재 타깃</gray> ")
                .append(leaderTargeting.targetTeamId().map(SemionDialogService::teamMarkup).orElse("<dark_gray>없음</dark_gray>"))
                .append("\n");
        if (!leaderTargeting.canUse()) {
            body.append("\n<red>쿨타임: </red><yellow>")
                    .append(leaderTargeting.cooldownRemainingRounds())
                    .append("라운드</yellow>");
            showActions(player, "세미온 TD 팀장", body.toString(), List.of(), TEAM_TARGET_COLUMNS);
            return;
        }
        body.append("\n<gray>견제 유닛을 보낼 팀을 선택하세요.</gray>");

        ArrayList<ActionButton> actions = game.teams().values().stream()
                .filter(candidate -> candidate.active() && !candidate.eliminated())
                .filter(candidate -> candidate.id() != semionPlayer.teamId())
                .sorted(Comparator.comparing(SemionTeam::id))
                .limit(TEAM_TARGET_COLUMNS)
                .map(candidate -> actionButton(
                        teamButtonLabel(candidate.id()),
                        "/semiontd leader target " + candidate.id().name().toLowerCase(java.util.Locale.ROOT),
                        Component.literal(candidate.id().name() + " 팀으로 이후 견제 유닛을 보냅니다."),
                        TEAM_TARGET_BUTTON_WIDTH
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        showActions(player, "세미온 TD 팀장", body.toString(), actions, TEAM_TARGET_COLUMNS);
    }

    public void showSummonShop(ServerPlayer player, SemionGame game, int page) {
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        long emerald = semionPlayer == null ? 0 : semionPlayer.economy().emerald();
        List<SummonMonsterType> summons = sortedSummons(game.summonShop().all());
        int pageCount = pageCount(summons.size());
        int safePage = clampPage(page, pageCount);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append("<gray>페이지</gray> <yellow>").append(safePage).append("</yellow><gray>/</gray><yellow>").append(pageCount).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>소환 후보</gray> <yellow>").append(summons.size()).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>");
        appendSummonNavigation(body, "/semiontd summonui ", safePage, pageCount);

        ArrayList<ActionButton> actions = summons.stream()
                .skip((long) (safePage - 1) * SUMMON_PAGE_SIZE)
                .limit(SUMMON_PAGE_SIZE)
                .map(type -> actionButton(
                        summonButtonLabel(type, emerald >= type.gasCost()),
                        "/semiontd summon " + type.id(),
                        summonTooltip(type, emerald >= type.gasCost()),
                        SUMMON_BUTTON_WIDTH
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        showActions(player, "세미온 TD 소환", body.toString(), actions, SUMMON_COLUMNS);
    }

    public void showDebugSummonShop(ServerPlayer player) {
        showDebugSummonShop(player, 1);
    }

    public void showDebugSummonShop(ServerPlayer player, int page) {
        SummonShop summonShop = new SummonShop();
        List<SummonMonsterType> summons = sortedSummons(summonShop.all());
        int pageCount = pageCount(summons.size());
        int safePage = clampPage(page, pageCount);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append("<gray>페이지</gray> <yellow>").append(safePage).append("</yellow><gray>/</gray><yellow>").append(pageCount).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>소환 후보</gray> <yellow>").append(summons.size()).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>");
        appendSummonNavigation(body, "/semiontd-debug summonui ", safePage, pageCount);

        ArrayList<ActionButton> actions = summons.stream()
                .skip((long) (safePage - 1) * SUMMON_PAGE_SIZE)
                .limit(SUMMON_PAGE_SIZE)
                .map(type -> actionButton(
                        summonButtonLabel(type, true),
                        "/semiontd summon " + type.id(),
                        summonTooltip(type, true),
                        SUMMON_BUTTON_WIDTH
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        showActions(player, "세미온 TD 소환", body.toString(), actions, SUMMON_COLUMNS);
    }

    private static List<SummonMonsterType> sortedSummons(java.util.Collection<SummonMonsterType> summons) {
        return summons.stream()
                .sorted(Comparator.comparingLong(SummonMonsterType::gasCost)
                        .thenComparing(type -> primaryRole(type).ordinal())
                        .thenComparing(SummonMonsterType::displayName))
                .toList();
    }

    private static int pageCount(int size) {
        return Math.max(1, (int) Math.ceil((double) Math.max(0, size) / SUMMON_PAGE_SIZE));
    }

    private static int buildGuidePageCount(int size) {
        return Math.max(1, (int) Math.ceil((double) Math.max(0, size) / BUILD_GUIDE_PAGE_SIZE));
    }

    private static List<BuildGuide> buildGuidePage(List<BuildGuide> guides, int page) {
        if (guides == null || guides.isEmpty()) {
            return List.of();
        }
        int from = Math.min(guides.size(), Math.max(0, page - 1) * BUILD_GUIDE_PAGE_SIZE);
        int to = Math.min(guides.size(), from + BUILD_GUIDE_PAGE_SIZE);
        return guides.subList(from, to);
    }

    private static int clampPage(int page, int pageCount) {
        return Math.max(1, Math.min(Math.max(1, pageCount), page));
    }

    private static String buildListCommand(int publicPage, int myPage) {
        return "/semiontd-internal build list " + publicPage + " " + myPage;
    }

    private static void appendSummonNavigation(StringBuilder body, String commandPrefix, int page, int pageCount) {
        if (pageCount <= 1) {
            return;
        }
        body.append("\n\n<dark_gray>────────────────────────</dark_gray>\n");
        body.append("<gray>페이지 이동</gray> ");
        if (page > 1) {
            body.append(commandLink("이전", commandPrefix + (page - 1), "aqua"));
        } else {
            body.append("<dark_gray>이전</dark_gray>");
        }
        body.append(" <dark_gray>|</dark_gray> ");
        if (page < pageCount) {
            body.append(commandLink("다음", commandPrefix + (page + 1), "aqua"));
        } else {
            body.append("<dark_gray>다음</dark_gray>");
        }
        body.append("\n<dark_gray>────────────────────────</dark_gray>");
    }

    private static Optional<SemionTowerEntity> towerEntity(SemionGame game, Tower tower) {
        if (game == null || !(tower instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
            return Optional.empty();
        }
        SemionTeam team = game.teams().get(tower.teamId());
        if (team == null) {
            return Optional.empty();
        }
        return team.laneGroup()
                .lane(tower.laneId())
                .map(lane -> lane.arenaWorld().getEntity(entityBackedTower.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private static void appendTowerTimedEffects(StringBuilder body, SemionTowerEntity entity) {
        double incomingDamageReduction = entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION);
        if (incomingDamageReduction > 0.0) {
            body.append("<blue>🛡 받는 피해 ")
                    .append(oneDecimal((1.0 - incomingDamageReduction) * 100.0))
                    .append("%</blue>")
                    .append(" <dark_gray>(</dark_gray><green>-")
                    .append(percent(incomingDamageReduction))
                    .append("</green><dark_gray>)</dark_gray>\n");
        }

        StringBuilder effects = new StringBuilder();
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_DAMAGE_BONUS, "<green>⚔ 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_ATTACK_SPEED_BONUS, "<green>⚡ 공속 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RANGE_BONUS, "<green>🎯 사거리 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_DAMAGE_REDUCTION, "<blue>🛡 받피 감소 +", "</blue>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_MAX_HEALTH_BONUS, "<green>❤ 최대체력 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_INCOME_DAMAGE_BONUS, "<green>⚔ 인컴 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_WAVE_DAMAGE_BONUS, "<green>⚔ 웨이브 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, "<green>⚔ 특성 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_TRAIT_INCOME_DAMAGE_BONUS, "<green>⚔ 특성 인컴 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_TRAIT_WAVE_DAMAGE_BONUS, "<green>⚔ 특성 웨이브 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_FINAL_DAMAGE_BONUS, "<green>⚔ 최종 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_DAMAGE_TAKEN_BONUS, "<red>🛡 받는 피해 증가 +", "</red>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_TRAIT_MAX_HEALTH_BONUS, "<green>❤ 특성 최대체력 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_HEAL_AMOUNT_BONUS, "<green>❤ 회복량 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_ABILITY_INTERVAL_REDUCTION, "<green>⏱ 주기 감소 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, "<red>⚡ 공속 감소 -", "</red>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RANGE_REDUCTION, "<red>🎯 사거리 감소 -", "</red>");
        if (effects.length() > 0) {
            body.append("<yellow>✨ 활성 효과</yellow>\n").append(effects);
        }
    }

    public static List<String> towerRuntimeDetailLines(Tower tower) {
        if (tower == null) {
            return List.of();
        }
        ArrayList<String> lines = new ArrayList<>();
        if (VillagerAdvStates.isAdvTower(tower)) {
            lines.add("경험치 " + oneDecimal(VillagerAdvStates.experience(tower))
                    + "/" + oneDecimal(TowerBalanceRuntime.villagerAdv().resolvedExperienceMax()));
        }
        lines.addAll(tower.runtimeDetailLines());
        return lines;
    }

    private static void appendTowerRuntimeDetails(StringBuilder body, Tower tower) {
        List<String> lines = towerRuntimeDetailLines(tower);
        if (lines.isEmpty()) {
            return;
        }
        body.append("<yellow>✨ 성장/시너지 상태</yellow>\n");
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                body.append("<dark_gray>-</dark_gray> <green>").append(line).append("</green>\n");
            }
        }
    }

    private static void appendTimedEffect(
            StringBuilder body,
            SemionTowerEntity entity,
            TimedEffectType type,
            String prefix,
            String suffix
    ) {
        double magnitude = entity.activeEffectMagnitude(type);
        int ticks = entity.activeTimedEffectTicks(type);
        boolean persistent = entity.hasPersistentEffect(type);
        if (magnitude <= 0.0 || (!persistent && ticks <= 0)) {
            return;
        }
        body.append("<dark_gray>-</dark_gray> ")
                .append(prefix)
                .append(percent(magnitude))
                .append(suffix);
        if (persistent) {
            body.append(" <gray>지속</gray>\n");
        } else {
            body.append(" <gray>")
                    .append(oneDecimal(ticks / 20.0))
                    .append("초</gray>\n");
        }
    }

    private void show(ServerPlayer player, String title, String body) {
        show(player, title, miniMessage(body));
    }

    private void show(ServerPlayer player, String title, Component body) {
        Dialog dialog = new NoticeDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        List.<DialogBody>of(new PlainMessage(body, BODY_WIDTH)),
                        List.of()
                ),
                NoticeDialog.DEFAULT_ACTION
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private void showActions(ServerPlayer player, String title, String body, List<ActionButton> actions) {
        showActions(player, title, body, actions, 2);
    }

    private void showActions(ServerPlayer player, String title, String body, List<ActionButton> actions, int columns) {
        showActions(player, title, body, actions, actionButton("닫기", "", "창을 닫습니다."), columns);
    }

    private void showActions(
            ServerPlayer player,
            String title,
            String body,
            List<ActionButton> actions,
            ActionButton exitAction,
            int columns
    ) {
        if (actions.isEmpty()) {
            show(player, title, body);
            return;
        }
        Dialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        actionDialogBodies(body),
                        List.of()
                ),
                actions,
                Optional.of(exitAction),
                columns
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private void showActions(ServerPlayer player, String title, List<DialogBody> bodies, List<ActionButton> actions, int columns) {
        if (actions.isEmpty()) {
            Dialog dialog = new NoticeDialog(
                    new CommonDialogData(
                            Component.literal(title),
                            Optional.empty(),
                            true,
                            false,
                            DialogAction.CLOSE,
                            bodies,
                            List.of()
                    ),
                    NoticeDialog.DEFAULT_ACTION
            );
            player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
            return;
        }
        Dialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        bodies,
                        List.of()
                ),
                actions,
                Optional.of(actionButton("닫기", "", "창을 닫습니다.")),
                columns
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private static List<DialogBody> actionDialogBodies(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        int split = body.indexOf('\n');
        if (split < 0) {
            return List.of(new HeaderMessage(miniMessage(body), BODY_WIDTH));
        }

        ArrayList<DialogBody> bodies = new ArrayList<>();
        String header = body.substring(0, split);
        String content = body.substring(split + 1);
        if (!header.isBlank()) {
            bodies.add(new HeaderMessage(miniMessage(header), BODY_WIDTH));
        }
        if (!content.isBlank()) {
            bodies.add(new PlainMessage(miniMessage(content), BODY_WIDTH));
        }
        return bodies;
    }

    private static ActionButton actionButton(String label, String command, String tooltip) {
        return actionButton(label, command, Component.literal(tooltip), BUTTON_WIDTH);
    }

    private static ActionButton actionButton(String label, String command, Component tooltip, int width) {
        return actionButton(Component.literal(label), command, tooltip, width);
    }

    private static ActionButton actionButton(Component label, String command, Component tooltip, int width) {
        Optional<net.minecraft.server.dialog.action.Action> action = command == null || command.isBlank()
                ? Optional.empty()
                : Optional.of(new StaticAction(new ClickEvent.RunCommand(command)));
        return new ActionButton(
                new CommonButtonData(label, Optional.of(tooltip), width),
                action
        );
    }

    private static String commandLink(String label, String command, String color) {
        return "<click:run_command:'" + command + "'><hover:show_text:'" + label + "'><" + color + ">[" + label + "]</" + color + "></hover></click>";
    }

    private static ActionButton towerButton(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable, boolean recommended) {
        return actionButton(
                towerButtonLabel(entry, affordable, recommended),
                "/semiontd tower build " + entry.type().id(),
                towerTooltip(entry, mineralCost, affordable, recommended),
                COMPACT_BUTTON_WIDTH
        );
    }

    private static ActionButton jobButton(SemionJob job, boolean selected) {
        return actionButton(
                jobButtonLabel(job, selected),
                jobSelectionCommand(job),
                jobTooltip(job, selected),
                BUTTON_WIDTH
        );
    }

    private static ActionButton traitButton(SemionTrait trait, TraitSlot slot, boolean selected) {
        return actionButton(
                traitButtonLabel(trait, selected),
                traitSelectionCommand(trait, slot),
                traitTooltip(trait, slot, selected),
                TRAIT_BUTTON_WIDTH
        );
    }

    public static String jobSelectionCommand(SemionJob job) {
        return "/semiontd job select " + job.id().getPath();
    }

    public static String traitSelectionCommand(SemionTrait trait, TraitSlot slot) {
        String slotName = slot == TraitSlot.PRIMARY ? "primary" : "secondary";
        return "/semiontd trait select " + slotName + " " + trait.id().getPath();
    }

    private static String traitName(net.minecraft.resources.ResourceLocation traitId) {
        return TraitRegistry.find(traitId)
                .map(trait -> trait.displayName().getString())
                .orElse(traitId.toString());
    }

    private static String traitEffectSummary(net.minecraft.resources.ResourceLocation traitId, TraitSlot slot) {
        return TraitRegistry.find(traitId)
                .map(trait -> trait.effectSummary(slot).getString())
                .orElse("효과 정보 없음");
    }

    private static String traitName(String traitId) {
        net.minecraft.resources.ResourceLocation parsed =
                traitId == null ? null : net.minecraft.resources.ResourceLocation.tryParse(traitId);
        return parsed == null ? String.valueOf(traitId) : traitName(parsed);
    }

    public static Component jobButtonLabel(SemionJob job, boolean selected) {
        String prefix = selected ? "✓ " : "";
        return Component.literal(prefix + job.displayName().getString())
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    private static Component jobTooltip(SemionJob job, boolean selected) {
        MutableComponent tooltip = job.displayName().copy()
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA);
        if (selected) {
            tooltip.append(Component.literal("\n현재 선택된 직업입니다.").withStyle(ChatFormatting.GREEN));
        }
        for (Component line : job.description()) {
            tooltip.append(Component.literal("\n").append(line.copy().withStyle(ChatFormatting.GRAY)));
        }
        return tooltip;
    }

    private static Component traitButtonLabel(SemionTrait trait, boolean selected) {
        return trait.displayName().copy()
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    private static Component traitTooltip(SemionTrait trait, TraitSlot slot, boolean selected) {
        MutableComponent tooltip = Component.literal(slot.displayName() + " " + Math.round(slot.effectScale() * 100.0D) + "%")
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                .append(Component.literal("\n").append(trait.displayName().copy().withStyle(ChatFormatting.YELLOW)))
                .append(Component.literal("\n").append(trait.effectSummary(slot).copy().withStyle(ChatFormatting.GOLD)));
        if (selected) {
            tooltip.append(Component.literal("\n현재 선택된 특성입니다.").withStyle(ChatFormatting.GREEN));
        }
        for (Component line : trait.description()) {
            tooltip.append(Component.literal("\n").append(line.copy().withStyle(ChatFormatting.GRAY)));
        }
        return tooltip;
    }

    public static Component towerButtonLabel(ProductionTowerCatalog.CatalogEntry entry, boolean affordable) {
        return towerButtonLabel(entry, affordable, false);
    }

    public static Component towerButtonLabel(ProductionTowerCatalog.CatalogEntry entry, boolean affordable, boolean recommended) {
        return Component.literal(entry.type().displayName())
                .withStyle(style -> style
                        .withColor(recommended ? ChatFormatting.BLUE : (affordable ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .withBold(true));
    }

    private static Component towerTooltip(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable) {
        return towerTooltip(entry, mineralCost, affordable, false);
    }

    private static Component towerTooltip(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable, boolean recommended) {
        var type = entry.type();
        MutableComponent tooltip = mutableMiniMessage(
                "<white><bold>" + type.displayName() + "</bold></white>\n"
                        + (recommended ? "<blue>빌드 추천</blue>\n" : "")
                        + "<aqua>💎 " + mineralCost + " 다이아</aqua>" + (affordable ? " <green>(구매 가능)</green>" : " <red>(부족)</red>") + "\n"
                        + "<red>❤ 체력 " + Math.round(type.maxHealth()) + "</red> "
                        + "<yellow>🧲 어그로 " + type.aggroPriority() + "</yellow>\n"
                        + "<dark_red>⚔ 피해 " + oneDecimal(type.damage()) + "</dark_red> "
                        + "<light_purple>🎯 사거리 " + oneDecimal(type.range()) + "</light_purple>\n"
                        + "<green>⚡ 공속 " + attacksPerSecond(type.attackIntervalTicks()) + "/초</green>"
        );
        appendTowerDescription(tooltip, type.description());
        return tooltip;
    }

    private static Component upgradeTooltip(TowerUpgradeOption option) {
        return upgradeTooltip(option, true, false, null);
    }

    private static Component upgradeTooltip(TowerUpgradeOption option, boolean affordable, boolean recommended) {
        return upgradeTooltip(option, affordable, recommended, null);
    }

    private static Component upgradeTooltip(TowerUpgradeOption option, boolean affordable, boolean recommended, Tower currentTower) {
        Optional<ProductionTowerCatalog.CatalogEntry> target = ProductionTowerCatalog.entry(option.targetType());
        if (target.isEmpty()) {
            return Component.literal("대상 타워를 찾을 수 없습니다.\n비용 " + option.mineralCost() + " 다이아");
        }
        var entry = target.get();
        var type = entry.type();
        MutableComponent tooltip = mutableMiniMessage(
                "<yellow><bold>" + option.displayName() + "</bold></yellow>\n"
                        + (recommended ? "<blue>빌드 추천</blue>\n" : "")
                        + "<gray>대상</gray> <white>" + type.displayName() + "</white>\n"
                        + "<aqua>💎 " + option.mineralCost() + " 다이아</aqua>"
                        + (affordable ? " <green>(구매 가능)</green>" : " <red>(부족)</red>") + "\n"
                        + advExperienceRequirementLine(currentTower, option)
                        + "<red>❤ 체력 " + Math.round(type.maxHealth()) + "</red> "
                        + "<yellow>🧲 어그로 " + type.aggroPriority() + "</yellow>\n"
                        + "<dark_red>⚔ 피해 " + oneDecimal(type.damage()) + "</dark_red> "
                        + "<light_purple>🎯 사거리 " + oneDecimal(type.range()) + "</light_purple>\n"
                        + "<green>⚡ 공속 " + attacksPerSecond(type.attackIntervalTicks()) + "/초</green>"
        );
        appendTowerDescription(tooltip, type.description());
        return tooltip;
    }

    private static String advExperienceRequirementLine(Tower tower, TowerUpgradeOption option) {
        double requirement = advExperienceRequirement(tower, option);
        if (requirement <= 0.0) {
            return "";
        }
        double experience = VillagerAdvStates.experience(tower);
        String color = advExperienceAffordable(tower, option) ? "green" : "red";
        return "<" + color + ">경험치 " + oneDecimal(experience) + "/" + oneDecimal(requirement) + "</" + color + ">\n";
    }

    private static boolean advExperienceAffordable(Tower tower, TowerUpgradeOption option) {
        double requirement = advExperienceRequirement(tower, option);
        return requirement <= 0.0 || VillagerAdvStates.experience(tower) + 1.0E-6 >= requirement;
    }

    private static double advExperienceRequirement(Tower tower, TowerUpgradeOption option) {
        if (!VillagerAdvStates.isAdvTower(tower) || option == null) {
            return 0.0;
        }
        return TowerBalanceRuntime.villagerAdvUpgradeRequirement(tower.type(), option.id());
    }

    public static Component upgradeButtonLabel(TowerUpgradeOption option, boolean affordable, boolean recommended) {
        return Component.literal(option.displayName())
                .withStyle(style -> style
                        .withColor(recommended ? ChatFormatting.BLUE : (affordable ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .withBold(true));
    }

    private static void appendBuildGuideBodies(List<DialogBody> bodies, List<BuildGuide> guides, String emptyText) {
        if (guides.isEmpty()) {
            bodies.add(new AlignedMessage(miniMessage("<gray>" + emptyText + "</gray>"), BODY_WIDTH, AlignedMessage.Align.LEFT));
            return;
        }
        for (BuildGuide guide : guides) {
            appendBuildGuideBody(bodies, guide, emptyText, false);
        }
    }

    private static void appendBuildGuideBody(List<DialogBody> bodies, BuildGuide guide, String emptyText, boolean trackedRow) {
        if (guide == null) {
            bodies.add(new AlignedMessage(miniMessage("<gray>" + emptyText + "</gray>"), BODY_WIDTH, AlignedMessage.Align.LEFT));
            return;
        }
        Component description = miniMessage(
                "<blue><bold>" + guide.code() + "</bold></blue> <white>" + guide.title() + "</white>\n"
                        + "<gray>작성자</gray> <yellow>" + guide.authorName() + "</yellow>"
                        + " <dark_gray>|</dark_gray> <gray>라운드</gray> <aqua>" + guide.finalRound() + "</aqua>"
                        + " <dark_gray>|</dark_gray> <gray>행동</gray> <green>" + guide.actions().size() + "</green>"
                        + " <dark_gray>|</dark_gray> <gray>상태</gray> " + visibilityMarkup(guide)
        );
        bodies.add(new SplitAlignedMessage(description, miniMessage(buildGuideLinks(guide, trackedRow)), BODY_WIDTH));
    }

    private static String visibilityMarkup(BuildGuide guide) {
        return guide != null && guide.isPublic() ? "<green>공개</green>" : "<gray>비공개</gray>";
    }

    private static String buildGuideLinks(BuildGuide guide, boolean trackedRow) {
        String detail = commandLink("상세보기", "/semiontd-internal build detail " + guide.code(), "aqua");
        if (trackedRow) {
            return detail + "<dark_gray>|</dark_gray>" + commandLink("추적해제", "/semiontd-internal build clear", "red");
        }
        return detail + "<dark_gray>|</dark_gray>" + commandLink("추적", "/semiontd-internal build track " + guide.code(), "blue");
    }

    private static DialogBody buildActionBody(kim.biryeong.semiontd.buildguide.BuildAction action) {
        return new AlignedMessage(buildActionDescription(action), BODY_WIDTH, AlignedMessage.Align.LEFT);
    }

    private static Component buildActionDescription(kim.biryeong.semiontd.buildguide.BuildAction action) {
        String line = switch (action.type()) {
            case TOWER_PLACE -> "<blue>타워 설치</blue> <white>" + BuildGuideService.subjectDisplayName(action) + "</white>"
                    + " <gray>" + buildPositionLabel(action) + "</gray>"
                    + " <dark_gray>|</dark_gray> <aqua>💎 " + action.cost() + "</aqua>";
            case TOWER_UPGRADE -> "<blue>타워 업그레이드</blue> <white>" + BuildGuideService.subjectDisplayName(action) + "</white>"
                    + " <gray>" + buildPositionLabel(action) + "</gray>"
                    + " <dark_gray>|</dark_gray> <aqua>💎 " + action.cost() + "</aqua>";
            case SUMMON -> "<light_purple>견제 소환</light_purple> <white>" + BuildGuideService.subjectDisplayName(action) + "</white>"
                    + " <dark_gray>|</dark_gray> <green>◆ " + action.cost() + "</green>"
                    + " <dark_gray>|</dark_gray> <yellow>인컴 +" + action.incomeGain() + "</yellow>"
                    + " <dark_gray>|</dark_gray> <gray>예약 " + action.scheduledRound() + "R</gray>";
            case EMERALD_PRODUCTION_UPGRADE -> "<green>에메랄드 생산 업그레이드</green>"
                    + " <dark_gray>|</dark_gray> <gray>" + BuildGuideService.subjectDisplayName(action) + "</gray>"
                    + " <dark_gray>|</dark_gray> <yellow>+" + action.incomeGain() + "/초</yellow>";
        };
        return miniMessage(line);
    }

    private static String buildPositionLabel(kim.biryeong.semiontd.buildguide.BuildAction action) {
        if (action == null) {
            return "";
        }
        String label = buildPositionLabel(action.position());
        return action.hasLaneRelativePosition() && !label.isEmpty() ? "라인 상대 " + label : label;
    }

    private static String buildPositionLabel(kim.biryeong.semiontd.game.GridPosition position) {
        if (position == null) {
            return "";
        }
        return "(" + position.x() + ", " + position.y() + ", " + position.z() + ")";
    }

    private static Component summonButtonLabel(SummonMonsterType type, boolean affordable) {
        return Component.literal(type.displayName().split("\\s+", 2)[0])
                .withStyle(style -> style
                        .withColor(affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .withBold(true));
    }

    private static Component summonTooltip(SummonMonsterType type, boolean affordable) {
        MutableComponent tooltip = mutableMiniMessage(
                "<white><bold>" + type.displayName() + "</bold></white>\n"
                        + "<gray>🏷 역할</gray> <yellow>" + roleList(type) + "</yellow>\n"
                        + "<green>◆ 비용 " + type.gasCost() + " 에메랄드</green>"
                        + (affordable ? "" : " <red>(부족)</red>")
                        + " <dark_gray>|</dark_gray> <yellow>📈 인컴 +" + type.incomeGain() + "</yellow>\n"
                        + "<aqua>💎 처치 " + type.mineralReward() + " 다이아</aqua> "
                        + "<light_purple>⚖ 효율 " + oneDecimal(type.incomeRatio() * 100.0) + "%</light_purple>\n"
                        + "<red>❤ 체력 " + Math.round(type.maxHealth()) + "</red> "
                        + "<blue>🛡 방어 " + oneDecimal(type.armor()) + "</blue> "
                        + "<dark_purple>✦ 저항 " + oneDecimal(type.resistance()) + "</dark_purple>\n"
                        + "<dark_red>⚔ 공격 " + oneDecimal(type.attackDamage()) + "</dark_red> "
                        + "<gray>" + attackKindIcon(type.attackKind()) + " 방식 " + attackKindLabel(type.attackKind()) + "</gray> "
                        + "<gold>🔥 피해 " + damageTypeLabel(type.damageType()) + "</gold>\n"
                        + "<green>⚡ 공속 " + attacksPerSecond(13) + "/초</green> "
                        + "<white>📏 크기 " + oneDecimal(type.dimensions().width()) + "x" + oneDecimal(type.dimensions().height()) + "</white>\n"
                        + "<light_purple>🎯 타겟 " + oneDecimal(type.targetRolePriority()) + "</light_purple> "
                        + "<yellow>✨ 능력 " + abilityActivationList(type) + "</yellow>"
        );
        appendSummonDescription(tooltip, type.description());
        return tooltip;
    }

    public static Component teamButtonLabel(TeamId teamId) {
        return miniMessage(teamMarkup(teamId));
    }

    private static void appendTowerDescription(StringBuilder body, List<String> description) {
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                body.append("<dark_gray>-</dark_gray> <gray>").append(line).append("</gray>\n");
            }
        }
    }

    private static void appendTowerDescription(MutableComponent tooltip, List<String> description) {
        if (description.isEmpty()) {
            return;
        }
        tooltip.append(Component.literal("\n\n설명"));
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal("\n- ").withStyle(ChatFormatting.DARK_GRAY));
                tooltip.append(miniMessage("<gray>" + line + "</gray>"));
            }
        }
    }

    private static void appendDescription(MutableComponent tooltip, List<String> description) {
        if (description.isEmpty()) {
            return;
        }
        tooltip.append(Component.literal("\n\n설명"));
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal("\n- " + line));
            }
        }
    }

    private static void appendSummonDescription(MutableComponent tooltip, List<String> description) {
        if (description.isEmpty()) {
            return;
        }
        tooltip.append(Component.literal("\n\n"));
        tooltip.append(miniMessage("<gray>설명</gray>"));
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal("\n- ").withStyle(ChatFormatting.DARK_GRAY));
                tooltip.append(miniMessage("<gray>" + line + "</gray>"));
            }
        }
    }

    private static SummonRole primaryRole(SummonMonsterType type) {
        return type.roles().stream()
                .max(Comparator.comparingInt(SummonRole::targetPriority))
                .orElse(SummonRole.RUSH);
    }

    private static String roleList(SummonMonsterType type) {
        return type.roles().stream().map(SemionDialogService::roleLabel).collect(Collectors.joining(", "));
    }

    private static String abilityActivationList(SummonMonsterType type) {
        return type.abilityActivations().stream()
                .map(SemionDialogService::abilityActivationLabel)
                .collect(Collectors.joining(", "));
    }

    private static String abilityActivationLabel(SummonAbilityActivation activation) {
        return switch (activation) {
            case PASSIVE -> "지속";
            case CONDITIONAL -> "조건부";
            case COOLDOWN -> "쿨다운";
        };
    }

    private static String roleLabel(SummonRole role) {
        return switch (role) {
            case SWARM -> "물량";
            case RUSH -> "러시";
            case SIEGE -> "공성";
            case SUPPORT -> "지원";
            case TANK -> "탱커";
            case DISRUPTOR -> "교란";
        };
    }

    private static String attackKindLabel(AttackKind attackKind) {
        return switch (attackKind) {
            case MELEE -> "근접";
            case RANGED -> "원거리";
        };
    }

    private static String attackKindIcon(AttackKind attackKind) {
        return switch (attackKind) {
            case MELEE -> "🗡";
            case RANGED -> "🏹";
        };
    }

    private static String damageTypeLabel(DamageType damageType) {
        return switch (damageType) {
            case PHYSICAL -> "물리";
            case MAGIC -> "마법";
            case TRUE -> "고정";
        };
    }

    private static String attacksPerSecond(int attackIntervalTicks) {
        return oneDecimal(20.0 / Math.max(1, attackIntervalTicks));
    }

    private static String statDeltaSuffix(double baseValue, double currentValue, boolean higherBetter) {
        if (Math.abs(baseValue - currentValue) < 0.005) {
            return "";
        }
        String color = (currentValue > baseValue) == higherBetter ? "green" : "red";
        String sign = currentValue > baseValue ? "+" : "";
        String delta = Math.abs(baseValue) < 0.0001
                ? sign + oneDecimal(currentValue - baseValue)
                : sign + percent((currentValue - baseValue) / baseValue);
        return " <dark_gray>(기본 " + oneDecimal(baseValue)
                + ", </dark_gray><" + color + ">" + delta + "</" + color + "><dark_gray>)</dark_gray>";
    }

    private static String percent(double value) {
        return oneDecimal(value * 100.0) + "%";
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static Component miniMessage(String text) {
        try {
            return SemionText.mini(text);
        } catch (RuntimeException exception) {
            return Component.literal(text);
        }
    }

    private static MutableComponent mutableMiniMessage(String text) {
        try {
            return SemionText.mutableMini(text);
        } catch (RuntimeException exception) {
            return Component.empty().append(Component.literal(text));
        }
    }

    private static PlainMessage decoratedHeader(Component title, int width) {
        Component side = Component.literal("──────").withStyle(ChatFormatting.DARK_GRAY);
        return new PlainMessage(
                Component.empty().append(side).append(" ").append(title).append(" ").append(side),
                width
        );
    }

    private static Component participantResultBody(
            MatchParticipantResult participant,
            MatchProgressionReward reward
    ) {
        var stats = participant.stats();
        MutableComponent body = Component.empty()
                .append(TextUncenterer.filler(8))
                .append(avatarComponent(participant.playerName(), AvatarVariant.RESULT))
                .append(miniMessage(" <white>" + participant.playerName() + "</white>"
                        + " <dark_gray>[</dark_gray>"
                        + (participant.winner() ? "<gold>승리</gold>" : "<gray>패배</gray>")
                        + " " + teamMarkup(participant.teamId())
                        + "<dark_gray>]</dark_gray>\n"
                        + "  <gray>처치</gray> <red>" + stats.monsterKills() + "</red>"
                        + " <dark_gray>|</dark_gray> <gray>수입</gray> <green>" + stats.finalIncome() + "</green>"
                        + " <dark_gray>|</dark_gray> <gray>소환</gray> <light_purple>" + stats.summonedMonsters() + "</light_purple>\n"
                        + "  <gray>처치다이아</gray> <aqua>+" + stats.killMinerals() + "</aqua>"));
        if (reward != null) {
            body.append(miniMessage(" <dark_gray>|</dark_gray> <gray>꾸미기</gray> <gold>+" + reward.currencyAwarded() + "</gold>"));
        }
        return body;
    }

    private static Component playerStatusHeader() {
        return Component.empty()
                .append(TextUncenterer.filler(PLAYER_STATUS_AVATAR_WIDTH))
                .append(tableCell(Component.literal("플레이어").withStyle(ChatFormatting.GRAY), PLAYER_STATUS_NAME_WIDTH, false))
                .append(tableCell(Component.literal("다이아").withStyle(ChatFormatting.GRAY), PLAYER_STATUS_NUMBER_WIDTH, true))
                .append(tableCell(Component.literal("에메랄드").withStyle(ChatFormatting.GRAY), PLAYER_STATUS_NUMBER_WIDTH, true))
                .append(tableCell(Component.literal("수입").withStyle(ChatFormatting.GRAY), PLAYER_STATUS_NUMBER_WIDTH, true))
                .append(tableCell(Component.literal("타워").withStyle(ChatFormatting.GRAY), PLAYER_STATUS_TOWER_WIDTH, true))
                .append(TextUncenterer.filler(PLAYER_STATUS_TOWER_JOB_GAP))
                .append(tableCell(Component.literal("직업").withStyle(ChatFormatting.GRAY), PLAYER_STATUS_JOB_WIDTH, false));
    }

    private static Component playerStatusTeamHeader(TeamId teamId) {
        return Component.literal(teamId.name() + " 팀").withStyle(teamColor(teamId), ChatFormatting.BOLD);
    }

    private static Component playerStatusBody(PlayerStatusRow row) {
        return Component.empty()
                .append(avatarComponent(row.playerName(), AvatarVariant.COMPACT))
                .append(TextUncenterer.filler(6))
                .append(tableCell(Component.literal(row.playerName()).withStyle(teamColor(row.teamId())), PLAYER_STATUS_NAME_WIDTH, false))
                .append(tableCell(Component.literal(Long.toString(row.diamond())).withStyle(ChatFormatting.AQUA), PLAYER_STATUS_NUMBER_WIDTH, true))
                .append(tableCell(Component.literal(Long.toString(row.emerald())).withStyle(ChatFormatting.GREEN), PLAYER_STATUS_NUMBER_WIDTH, true))
                .append(tableCell(Component.literal(Long.toString(row.income())).withStyle(ChatFormatting.YELLOW), PLAYER_STATUS_NUMBER_WIDTH, true))
                .append(tableCell(Component.literal(Integer.toString(row.towerCount())).withStyle(ChatFormatting.GOLD), PLAYER_STATUS_TOWER_WIDTH, true))
                .append(TextUncenterer.filler(PLAYER_STATUS_TOWER_JOB_GAP))
                .append(tableCell(Component.literal(row.jobName()).withStyle(ChatFormatting.LIGHT_PURPLE), PLAYER_STATUS_JOB_WIDTH, false));
    }

    private static Component tableCell(Component value, int width, boolean rightAligned) {
        Component padding = TextUncenterer.filler(Math.max(0, width - TextUncenterer.width(value)));
        return rightAligned
                ? Component.empty().append(padding).append(value)
                : Component.empty().append(value).append(padding);
    }

    private static Component avatarComponent(String playerName, AvatarVariant variant) {
        SmallAvatarKey key = new SmallAvatarKey(playerName, variant);
        Component cached = SMALL_AVATAR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            Optional<BufferedImage> skin = SkinLoader.load(playerName);
            if (skin.isPresent()) {
                Component avatar = AvatarRenderer.asTextComponent(avatarImage(skin.get(), variant), variant.yOffset());
                SMALL_AVATAR_CACHE.putIfAbsent(key, avatar);
                return avatar;
            }
        } catch (RuntimeException exception) {
            // Fall back to the bundled Steve skin below.
        }

        SmallAvatarKey defaultKey = new SmallAvatarKey("Steve", variant);
        return SMALL_AVATAR_CACHE.computeIfAbsent(defaultKey, SemionDialogService::defaultSmallAvatar);
    }

    private static Component defaultSmallAvatar(SmallAvatarKey key) {
        try (var stream = AvatarRendererMod.class.getResourceAsStream("/steve.png")) {
            if (stream == null) {
                return Component.empty();
            }
            BufferedImage skin = javax.imageio.ImageIO.read(stream);
            if (skin == null) {
                return Component.empty();
            }
            return AvatarRenderer.asTextComponent(avatarImage(skin, key.variant()), key.variant().yOffset());
        } catch (java.io.IOException exception) {
            return Component.empty();
        }
    }

    private static BufferedImage avatarImage(BufferedImage skin, AvatarVariant variant) {
        BufferedImage face = new BufferedImage(variant.imageSize(), variant.imageSize(), BufferedImage.TYPE_INT_ARGB);
        boolean hasFaceOverlay = skin.getHeight() >= 64;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int color = skinPixel(skin, 8 + x, 8 + y);
                if (hasFaceOverlay) {
                    int overlay = skinPixel(skin, 40 + x, 8 + y);
                    if ((overlay >>> 24) > 16) {
                        color = overlay;
                    }
                }
                if ((color >>> 24) != 0) {
                    int targetX = 1 + x * variant.pixelScale();
                    int targetY = 1 + y * variant.pixelScale();
                    for (int dy = 0; dy < variant.pixelScale(); dy++) {
                        for (int dx = 0; dx < variant.pixelScale(); dx++) {
                            face.setRGB(targetX + dx, targetY + dy, color);
                        }
                    }
                }
            }
        }

        BufferedImage outlined = new BufferedImage(variant.imageSize(), variant.imageSize(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < face.getHeight(); y++) {
            for (int x = 0; x < face.getWidth(); x++) {
                if ((face.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ox = x + dx;
                        int oy = y + dy;
                        if (ox >= 0 && ox < outlined.getWidth() && oy >= 0 && oy < outlined.getHeight()
                                && (outlined.getRGB(ox, oy) >>> 24) == 0) {
                            outlined.setRGB(ox, oy, 0xFF000000);
                        }
                    }
                }
            }
        }
        for (int y = 0; y < face.getHeight(); y++) {
            for (int x = 0; x < face.getWidth(); x++) {
                int color = face.getRGB(x, y);
                if ((color >>> 24) != 0) {
                    outlined.setRGB(x, y, color);
                }
            }
        }
        return outlined;
    }

    private static Component statisticsOverview(JobStatisticsSnapshot snapshot) {
        return Component.literal("일반 경기 ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatCount(snapshot.eligibleMatchCount()) + "회").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  참가 표본 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatCount(snapshot.participantAppearances()) + "건").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  최근 갱신 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatTime(snapshot.generatedAtEpochMillis())).withStyle(ChatFormatting.WHITE));
    }

    private static Component jobStatisticsSummaryLine(JobStatisticsSnapshot snapshot, JobStatisticsRow row) {
        JobStatisticsEntry entry = row.entry();
        MutableComponent line = Component.literal(row.displayName()).withStyle(ChatFormatting.AQUA);
        if (!row.registered()) {
            line.append(Component.literal(" [미등록]").withStyle(ChatFormatting.DARK_GRAY));
        }
        return line
                .append(Component.literal("  선택 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatCount(entry.appearances()) + "회").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + formatPercent(snapshot.selectionRate(entry)) + ")").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("  승률 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatPercent(entry.winRate())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  평균 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatAverage(entry.averagePlacement(), "위")).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(formatRound(entry.averageFinalRound())).withStyle(ChatFormatting.WHITE));
    }

    private static Component jobStatisticsRoundMilestonesLine(JobStatisticsRow row) {
        MutableComponent line = Component.literal("  통과 ").withStyle(ChatFormatting.DARK_GRAY);
        int[] rounds = {10, 20, 30, 40};
        for (int index = 0; index < rounds.length; index++) {
            if (index > 0) {
                line.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY));
            }
            int round = rounds[index];
            line.append(Component.literal("R" + round + " ").withStyle(ChatFormatting.GRAY));
            line.append(Component.literal(formatPercent(row.entry().roundPassRate(round)))
                    .withStyle(ChatFormatting.AQUA));
        }
        return line;
    }

    private static Component jobStatisticsRoundRangeLine(
            JobStatisticsEntry entry,
            int firstRound,
            int lastRound
    ) {
        MutableComponent line = Component.empty();
        for (int round = firstRound; round <= lastRound; round++) {
            if (round > firstRound) {
                line.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY));
            }
            line.append(Component.literal(String.format(Locale.ROOT, "R%02d ", round))
                    .withStyle(ChatFormatting.GRAY));
            line.append(Component.literal(formatPercent(entry.roundPassRate(round)))
                    .withStyle(ChatFormatting.AQUA));
        }
        return line;
    }

    private static Component jobStatisticsRoundTable(JobStatisticsEntry entry) {
        MutableComponent table = Component.empty();
        for (int firstRound = 1; firstRound <= JobStatisticsEntry.MAX_TRACKED_ROUND; firstRound += 5) {
            if (firstRound > 1) {
                table.append(Component.literal("\n"));
            }
            table.append(jobStatisticsRoundRangeLine(entry, firstRound, firstRound + 4));
        }
        return table;
    }

    private static ActionButton jobStatisticsButton(JobStatisticsRow row) {
        String tooltip = "선택 " + formatCount(row.entry().appearances()) + "회 · 승률 "
                + formatPercent(row.entry().winRate());
        return actionButton(
                Component.literal(row.displayName()),
                "/semiontd job stats " + row.jobId(),
                Component.literal(tooltip),
                COMPACT_BUTTON_WIDTH
        );
    }

    private static JobStatisticsEntry emptyJobStatisticsEntry(String jobId) {
        return new JobStatisticsEntry(
                jobId,
                0L,
                0L,
                0L,
                0L,
                0L,
                JobStatisticsTotals.empty(),
                0L,
                0L,
                0L
        );
    }

    private static void addStatisticsSection(List<DialogBody> bodies, String title) {
        bodies.add(new HeaderMessage(Component.literal(title).withStyle(ChatFormatting.YELLOW), JOB_STATISTICS_WIDTH));
    }

    private static void addStatisticsLine(List<DialogBody> bodies, String label, String value) {
        bodies.add(new PlainMessage(
                Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(value).withStyle(ChatFormatting.WHITE)),
                JOB_STATISTICS_WIDTH
        ));
    }

    private static void addTraitCombinationStatistics(
            List<DialogBody> bodies,
            List<TraitCombinationStatisticsEntry> combinations,
            long jobAppearances
    ) {
        addStatisticsSection(bodies, "특성 조합");
        if (combinations.isEmpty()) {
            addStatisticsLine(bodies, "조합", "-");
            return;
        }
        int visibleCount = Math.min(8, combinations.size());
        for (int index = 0; index < visibleCount; index++) {
            TraitCombinationStatisticsEntry combination = combinations.get(index);
            String label = traitName(combination.primaryTraitId())
                    + traitVersionSuffix(combination.primaryTraitVersion())
                    + " / "
                    + traitName(combination.secondaryTraitId())
                    + traitVersionSuffix(combination.secondaryTraitVersion());
            addStatisticsLine(
                    bodies,
                    label,
                    formatCount(combination.appearances()) + "회 · 선택률 "
                            + formatPercent(combination.selectionRate(jobAppearances))
            );
            addStatisticsLine(
                    bodies,
                    "  성과",
                    "승률 " + formatPercent(combination.winRate())
                            + " · 평균 순위 " + formatAverage(combination.averagePlacement(), "위")
                            + " · " + formatRound(combination.averageFinalRound())
            );
            addStatisticsLine(
                    bodies,
                    "  통과",
                    "R10 " + formatPercent(combination.roundPassRate(10))
                            + " · R20 " + formatPercent(combination.roundPassRate(20))
                            + " · R30 " + formatPercent(combination.roundPassRate(30))
                            + " · R40 " + formatPercent(combination.roundPassRate(40))
            );
            if (!combination.finalTowers().isEmpty()) {
                addStatisticsLine(bodies, "  최종 타워", combination.finalTowers().stream()
                        .limit(3)
                        .map(tower -> traitTowerSummary(tower, combination.appearances()))
                        .collect(Collectors.joining(", ")));
            }
        }
        if (visibleCount < combinations.size()) {
            addStatisticsLine(bodies, "나머지", formatCount(combinations.size() - visibleCount) + "개 조합");
        }
    }

    private static String traitVersionSuffix(int version) {
        return version <= 0 ? "" : " v" + version;
    }

    private static String traitTowerSummary(TraitTowerStatisticsEntry tower, long combinationAppearances) {
        String displayName = ProductionTowerCatalog.find(tower.towerTypeId())
                .map(entry -> entry.type().displayName())
                .orElse(tower.towerTypeId());
        return displayName + " T" + tower.tier() + " "
                + formatAverage(tower.averageCount(combinationAppearances), "개");
    }

    private static String formatPercent(OptionalDouble value) {
        if (value.isEmpty() || !Double.isFinite(value.getAsDouble())) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%%", value.getAsDouble() * 100.0);
    }

    private static String formatAverage(OptionalDouble value, String suffix) {
        if (value.isEmpty() || !Double.isFinite(value.getAsDouble())) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%s", value.getAsDouble(), suffix);
    }

    private static String formatRound(OptionalDouble value) {
        if (value.isEmpty() || !Double.isFinite(value.getAsDouble())) {
            return "-";
        }
        return String.format(Locale.ROOT, "R%.1f", value.getAsDouble());
    }

    private static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatTime(long epochMillis) {
        return epochMillis <= 0L ? "-" : STATISTICS_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static int skinPixel(BufferedImage skin, int x, int y) {
        if (x < 0 || y < 0 || x >= skin.getWidth() || y >= skin.getHeight()) {
            return 0;
        }
        return skin.getRGB(x, y);
    }

    private static String teamListMarkup(java.util.Set<TeamId> teams) {
        if (teams.isEmpty()) {
            return "<gray>없음</gray>";
        }
        return teams.stream()
                .sorted()
                .map(SemionDialogService::teamMarkup)
                .collect(Collectors.joining("<dark_gray>, </dark_gray>"));
    }

    private static String teamMarkup(TeamId teamId) {
        String color = switch (teamId) {
            case RED -> "red";
            case BLUE -> "blue";
            case GREEN -> "green";
            case YELLOW -> "yellow";
            case PURPLE -> "light_purple";
        };
        return "<" + color + ">" + teamId.name() + "</" + color + ">";
    }

    private static ChatFormatting teamColor(TeamId teamId) {
        return switch (teamId) {
            case RED -> ChatFormatting.RED;
            case BLUE -> ChatFormatting.BLUE;
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.YELLOW;
            case PURPLE -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static long nextGasUpgradeCost(SemionGame game, PlayerEconomy economy) {
        var config = game.economyConfig().gasProduction();
        if (economy.emeraldProductionUpgradeCount() >= config.maxUpgradeCount()) {
            return -1;
        }
        return config.upgradeCost(economy.emeraldProductionUpgradeCount());
    }

    private static String formatTowerLimitPurchaseCost(long diamondCost, long emeraldCost) {
        if (diamondCost < 0 || emeraldCost < 0) {
            return "최대";
        }
        return diamondCost + " 다이아 + " + emeraldCost + " 에메랄드";
    }

    private static String teamList(java.util.Set<TeamId> teams) {
        if (teams.isEmpty()) {
            return "없음";
        }
        return teams.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
    }

    private static Comparator<MatchParticipantResult> participantComparator() {
        return Comparator.comparing(MatchParticipantResult::teamId)
                .thenComparing(MatchParticipantResult::playerName);
    }

    public record PlayerStatusRow(
            UUID playerId,
            String playerName,
            TeamId teamId,
            long diamond,
            long emerald,
            long income,
            int towerCount,
            String jobName
    ) {
    }

    public record JobStatisticsRow(
            String jobId,
            String displayName,
            JobStatisticsEntry entry,
            boolean registered
    ) {
    }

    private enum AvatarVariant {
        COMPACT(1, 10, 25),
        RESULT(2, 18, 20);

        private final int pixelScale;
        private final int imageSize;
        private final int yOffset;

        AvatarVariant(int pixelScale, int imageSize, int yOffset) {
            this.pixelScale = pixelScale;
            this.imageSize = imageSize;
            this.yOffset = yOffset;
        }

        int pixelScale() {
            return pixelScale;
        }

        int imageSize() {
            return imageSize;
        }

        int yOffset() {
            return yOffset;
        }
    }

    private record SmallAvatarKey(String playerName, AvatarVariant variant) {
    }

}
