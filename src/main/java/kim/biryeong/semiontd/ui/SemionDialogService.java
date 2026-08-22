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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
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
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowerState;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyLeaderTower;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyPolicy;
import kim.biryeong.semiontd.tower.queen.QueenCard;
import kim.biryeong.semiontd.tower.queen.QueenStates;
import kim.biryeong.semiontd.tower.queen.QueenTowers;
import kim.biryeong.semiontd.tower.hero.HeroCompanionRole;
import kim.biryeong.semiontd.tower.hero.HeroPartyBalance;
import kim.biryeong.semiontd.tower.hero.HeroPartyState;
import kim.biryeong.semiontd.tower.hero.HeroPartyStates;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import kim.biryeong.semiontd.tower.hero.HeroTower;
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
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.ui.dialog.body.HeaderMessage.dividerComponent;

public final class SemionDialogService {
    private static final int BODY_WIDTH = 256;
    private static final int TITLE_HEADER_WIDTH = 200;
    private static final int PLAYER_STATUS_WIDTH = 480;
    private static final int JOB_STATISTICS_WIDTH = 460;
    private static final int JOB_STATISTICS_DETAIL_WIDTH = 420;
    private static final int JOB_STATISTICS_DETAIL_CONTENT_WIDTH = JOB_STATISTICS_DETAIL_WIDTH - 23;
    private static final int JOB_STATISTICS_DETAIL_TABLE_WIDTH = 380;
    private static final int JOB_STATISTICS_JOB_WIDTH = 80;
    private static final int JOB_STATISTICS_SELECTION_WIDTH = 100;
    private static final int JOB_STATISTICS_GAME_WIDTH = 120;
    private static final int JOB_STATISTICS_PLACEMENT_WIDTH = 120;
    private static final int JOB_STATISTICS_SEPARATOR_WIDTH = 10;
    private static final int PLAYER_STATUS_TEAM_WIDTH = 40;
    private static final int PLAYER_STATUS_AVATAR_WIDTH = 20;
    private static final int PLAYER_STATUS_AVATAR_NAME_GAP = 4;
    static final int PLAYER_STATUS_NAME_WIDTH = 112;
    static final int PLAYER_STATUS_JOB_WIDTH = 56;
    static final int PLAYER_STATUS_BODY_JOB_SHIFT = 12;
    private static final int PLAYER_STATUS_DIAMOND_WIDTH = 56;
    private static final int PLAYER_STATUS_EMERALD_WIDTH = 52;
    private static final int PLAYER_STATUS_INCOME_WIDTH = 44;
    private static final int PLAYER_STATUS_TOWER_WIDTH = 40;
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
    private static final String DIAMOND_GRADIENT = "<gradient:#ffffff:#d5fff6:#a1fbe8:#4aedd9:#20c5b5:#1aaaa7:#11727a:#145e53>";
    private static final String GRADIENT_CLOSE = "</gradient>";
    private static final DateTimeFormatter STATISTICS_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final ConcurrentMap<SmallAvatarKey, Component> SMALL_AVATAR_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> AVATAR_LOAD_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService AVATAR_LOADER = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().name("semiontd-avatar-loader-", 0).factory());

    public void showGameStatus(ServerPlayer player, SemionGame game) {
        ArrayList<DialogBody> bodies = new ArrayList<>();
        Component title = new HeaderMessage(
                miniMessage("<gradient:#facc15:#22d3ee><bold>플레이어 현황</bold></gradient>"),
                PLAYER_STATUS_WIDTH
        ).asVanillaComponent();
        bodies.add(new PlainMessage(
                playerStatusDialogContents(title, playerStatusTable(playerStatusRows(game))),
                PLAYER_STATUS_WIDTH
        ));
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
                    SemionTeam team = game.teams().get(semionPlayer.teamId());
                    return new PlayerStatusRow(
                            semionPlayer.uuid(),
                            semionPlayer.name(),
                            semionPlayer.teamId(),
                            economy.diamond(),
                            economy.emerald(),
                            economy.income(),
                            game.towerCapacityUsed(semionPlayer.uuid()),
                            job.displayName().getString(),
                            team != null && team.hasLeader(semionPlayer.uuid())
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
        SemionJob currentJob = displayedJob(player, game);
        String body = "<gradient:#67e8f9:#a78bfa><bold>직업 선택</bold></gradient>\n"
                + "<gray>현재 선택</gray> <yellow>" + currentJob.displayName().getString() + "</yellow>\n"
                + "<gray>빌더 분류를 선택하세요.</gray>";
        List<ActionButton> actions = List.of(
                actionButton(
                        jobCategoryLabel("공식 빌더", JobRegistry.officialBuilders()),
                        "/semiontd job ui official",
                        "세미온 TD의 기본 플레이를 담은 빌더입니다."
                ),
                actionButton(
                        jobCategoryLabel("창작 빌더", JobRegistry.creativeBuilders()),
                        "/semiontd job ui creative",
                        "색다른 규칙과 운영을 담은 빌더입니다."
                )
        );

        showActions(player, "세미온 TD 직업", body, actions, 2);
    }

    public void showJobSelection(ServerPlayer player, SemionGame game, boolean official) {
        SemionJob currentJob = displayedJob(player, game);
        String category = official ? "공식 빌더" : "창작 빌더";
        String body = "<gradient:#67e8f9:#a78bfa><bold>" + category + "</bold></gradient>\n"
                + "<gray>현재 선택</gray> <yellow>" + currentJob.displayName().getString() + "</yellow>\n"
                + "<gray>버튼에 마우스를 올려 운영 방법을 확인하세요.</gray>";
        ArrayList<ActionButton> actions = new ArrayList<>();
        List<SemionJob> jobs = official ? JobRegistry.officialBuilders() : JobRegistry.creativeBuilders();
        for (SemionJob job : jobs) {
            actions.add(jobButton(job, currentJob.id().equals(job.id())));
        }
        actions.add(actionButton("← 분류 선택", "/semiontd job ui", "빌더 분류 화면으로 돌아갑니다."));

        showActions(player, "세미온 TD " + category, body, actions, 2);
    }

    private static SemionJob displayedJob(ServerPlayer player, SemionGame game) {
        SemionPlayer participant = game.players().get(player.getUUID());
        return participant == null
                ? game.selectedJobOrDefault(player.getUUID())
                : participant.job().orElse(JobRegistry.defaultJob());
    }

    public void showJobManagement(ServerPlayer player) {
        List<ActionButton> actions = List.of(
                actionButton(
                        jobCategoryLabel("공식 빌더", JobRegistry.officialBuilders()),
                        "/semiontd job manage official",
                        "공식 빌더 킬스위치를 관리합니다."
                ),
                actionButton(
                        jobCategoryLabel("창작 빌더", JobRegistry.creativeBuilders()),
                        "/semiontd job manage creative",
                        "창작 빌더 킬스위치를 관리합니다."
                )
        );
        showActions(
                player,
                "세미온 TD 직업 관리",
                "<gradient:#67e8f9:#a78bfa><bold>직업 킬스위치</bold></gradient>\n"
                        + "<gray>관리할 빌더 분류를 선택하세요.</gray>",
                actions,
                2
        );
    }

    public void showJobManagement(ServerPlayer player, boolean official) {
        String category = official ? "공식 빌더" : "창작 빌더";
        List<SemionJob> jobs = official ? JobRegistry.officialBuilders() : JobRegistry.creativeBuilders();
        ArrayList<ActionButton> actions = new ArrayList<>();
        for (SemionJob job : jobs) {
            actions.add(jobManagementButton(job));
        }
        actions.add(actionButton("← 분류 선택", "/semiontd job manage", "빌더 분류 화면으로 돌아갑니다."));
        showActions(
                player,
                "세미온 TD " + category + " 관리",
                "<gradient:#67e8f9:#a78bfa><bold>" + category + " 킬스위치</bold></gradient>\n"
                        + "<gray>버튼을 눌러 활성 상태를 전환하세요.</gray>",
                actions,
                2
        );
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
        bodies.add(jobStatisticsHeader(
                miniMessage("<gradient:#67e8f9:#facc15><bold>직업 통계</bold></gradient>")
        ));
        bodies.add(new PlainMessage(statisticsOverview(snapshot), JOB_STATISTICS_WIDTH));
        appendJobStatisticsState(bodies, state);
        bodies.add(jobStatisticsListHeader());
        bodies.add(new PlainMessage(
                Component.literal("빌더 분류를 선택하세요.").withStyle(ChatFormatting.GRAY),
                JOB_STATISTICS_WIDTH
        ));

        List<ActionButton> actions = List.of(
                actionButton(
                        jobCategoryLabel("공식 빌더", JobRegistry.officialBuilders()),
                        "/semiontd job stats official",
                        "공식 빌더의 직업 통계를 표시합니다."
                ),
                actionButton(
                        jobCategoryLabel("창작 빌더", JobRegistry.creativeBuilders()),
                        "/semiontd job stats creative",
                        "창작 빌더의 직업 통계를 표시합니다."
                )
        );
        showActions(player, "세미온 TD 직업 통계", bodies, actions, 2);
    }

    public void showJobStatistics(
            ServerPlayer player,
            JobStatisticsSnapshot snapshot,
            JobStatisticsState state,
            boolean official
    ) {
        if (snapshot.participantAppearances() == 0L && state == JobStatisticsState.LOADING) {
            show(player, "세미온 TD 직업 통계", "<yellow>직업 통계를 집계하고 있습니다.</yellow>");
            return;
        }
        if (snapshot.participantAppearances() == 0L && state == JobStatisticsState.FAILED) {
            show(player, "세미온 TD 직업 통계", "<red>직업 통계를 불러오지 못했습니다.</red>");
            return;
        }

        String category = official ? "공식 빌더" : "창작 빌더";
        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(jobStatisticsHeader(
                miniMessage("<gradient:#67e8f9:#facc15><bold>" + category + " 통계</bold></gradient>")
        ));
        bodies.add(new PlainMessage(jobStatisticsCategoryOverview(snapshot), JOB_STATISTICS_WIDTH));
        appendJobStatisticsState(bodies, state);

        List<JobStatisticsRow> rows = jobStatisticsCategoryRows(snapshot, official);
        addJobStatisticsSummary(bodies, snapshot, rows);
        ArrayList<ActionButton> actions = new ArrayList<>();
        for (JobStatisticsRow row : rows) {
            actions.add(jobStatisticsButton(row));
        }
        actions.add(actionButton("← 분류 선택", "/semiontd job stats", "빌더 분류 화면으로 돌아갑니다."));
        showActions(player, "세미온 TD " + category + " 통계", bodies, actions, 3);
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
        ArrayList<DialogBody> bodies = new ArrayList<>();
        bodies.add(jobStatisticsDetailHeader(
                Component.literal(row.displayName() + " 통계").withStyle(ChatFormatting.AQUA)
        ));
        if (state != JobStatisticsState.READY && snapshot.participantAppearances() > 0L) {
            addCenteredStatisticsLine(bodies,
                    Component.literal("마지막 정상 통계를 표시합니다.").withStyle(ChatFormatting.YELLOW)
            );
        }

        if (entry.appearances() > 0L) {
            addCenteredStatisticsLine(bodies, Component.literal("기록 기간 ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(formatTime(entry.firstMatchAtEpochMillis())
                            + " ~ " + formatTime(entry.lastMatchAtEpochMillis()))
                            .withStyle(ChatFormatting.WHITE)));
        }
        addStatisticsSectionWithHeaderAndBody(
                bodies,
                "표본",
                centeredStatisticsRow(statisticsEqualColumnWidths(3), jobStatisticsSampleHeaderCells(), false),
                centeredStatisticsRow(statisticsEqualColumnWidths(3), jobStatisticsSampleCells(snapshot, entry), false)
        );

        addStatisticsSectionWithBody(bodies, "라운드 통과율(%)", jobStatisticsRoundBody(entry));

        addTraitCombinationStatistics(bodies, snapshot.traitCombinationsForJob(jobId), entry.appearances());

        addStatisticsSectionWithHeaderAndBody(
                bodies,
                "전투",
                centeredStatisticsRow(statisticsEqualColumnWidths(2), jobStatisticsCombatHeaderCells(), false),
                jobStatisticsCombatBody(entry)
        );

        addStatisticsSectionWithHeaderAndBody(
                bodies,
                "인컴 소환",
                centeredStatisticsRow(statisticsEqualColumnWidths(5), jobStatisticsIncomeHeaderCells(), false),
                jobStatisticsIncomeBody(entry)
        );

        addStatisticsSectionWithHeaderAndBody(
                bodies,
                "라인 방어·지원",
                centeredStatisticsRow(statisticsEqualColumnWidths(6), jobStatisticsDefenseHeaderCells(), false),
                jobStatisticsDefenseBody(entry)
        );
        bodies.add(jobStatisticsDetailDivider());

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

    static List<JobStatisticsRow> jobStatisticsCategoryRows(JobStatisticsSnapshot snapshot, boolean official) {
        java.util.Set<String> categoryIds = (official
                ? JobRegistry.officialBuilders()
                : JobRegistry.creativeBuilders()).stream()
                .map(job -> job.id().toString())
                .collect(java.util.stream.Collectors.toSet());
        return jobStatisticsRows(snapshot).stream()
                .filter(JobStatisticsRow::registered)
                .filter(row -> categoryIds.contains(row.jobId()))
                .toList();
    }

    public void showTraitSelection(ServerPlayer player, TraitLoadout loadout, int secondsRemaining) {
        String body = traitSelectionSummaryBody(loadout, secondsRemaining);
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
        String body = traitSelectionSlotBody(loadout, secondsRemaining, slot);
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

    static String traitSelectionSummaryBody(TraitLoadout loadout, int secondsRemaining) {
        String reopenHint = secondsRemaining < 0
                ? "샌드박스에서는 /특성으로 언제든 다시 열 수 있습니다."
                : "창을 닫아도 제한 시간 안에는 /특성으로 다시 열 수 있습니다.";
        return "<gradient:#67e8f9:#a78bfa><bold>특성 선택</bold></gradient>\n"
                + traitSelectionTimeMarkup(secondsRemaining) + "\n"
                + traitSelectionLoadoutLine("주특성 100%", loadout.primaryTraitId()) + "\n"
                + traitSelectionLoadoutLine("부특성 50%", loadout.secondaryTraitId()) + "\n"
                + "<divider>\n"
                + traitSelectionSummaryAbilityLine("주특성 능력", loadout.primaryTraitId(), TraitSlot.PRIMARY) + "\n"
                + traitSelectionSummaryAbilityLine("부특성 능력", loadout.secondaryTraitId(), TraitSlot.SECONDARY) + "\n"
                + "<divider>\n"
                + "<gray>아래 버튼으로 주특성/부특성을 각각 선택하세요.</gray>\n"
                + "<gray>" + reopenHint + "</gray>\n"
                + "<divider>";
    }

    static String traitSelectionSlotBody(TraitLoadout loadout, int secondsRemaining, TraitSlot slot) {
        String reopenHint = secondsRemaining < 0
                ? "샌드박스에서는 /특성으로 언제든 다시 열 수 있습니다."
                : "창을 닫아도 제한 시간 안에는 /특성으로 다시 열 수 있습니다.";
        var traitId = loadout.traitId(slot);
        boolean noEffect = TraitLoadout.isNone(traitId);
        String ability = noEffect
                ? "효과 없음"
                : traitEffectSummary(traitId, slot);
        return "<gradient:#67e8f9:#a78bfa><bold>" + slot.displayName() + " 선택</bold></gradient>\n"
                + traitSelectionTimeMarkup(secondsRemaining) + "\n"
                + traitSelectionLoadoutLine(
                        slot.displayName() + " " + Math.round(slot.effectScale() * 100.0D) + "%",
                        traitId
                ) + "\n"
                + traitSelectionAbilityLine(ability, noEffect) + "\n"
                + "<divider>\n"
                + "<gray>같은 non-none 특성은 주/부특성에 동시에 선택할 수 없습니다.</gray>\n"
                + "<gray>버튼에 마우스를 올리면 효과와 설명이 표시됩니다.</gray>\n"
                + "<gray>" + reopenHint + "</gray>\n"
                + "<divider>";
    }

    static String traitSelectionAbilityLine(String ability) {
        return traitSelectionAbilityLine(ability, false);
    }

    static String traitSelectionAbilityLine(String ability, boolean noEffect) {
        return traitSelectionAbilityLine("능력", ability, noEffect);
    }

    private static String traitSelectionSummaryAbilityLine(
            String label,
            net.minecraft.resources.ResourceLocation traitId,
            TraitSlot slot
    ) {
        boolean noEffect = TraitLoadout.isNone(traitId);
        String ability = noEffect ? "효과 없음" : traitEffectSummary(traitId, slot);
        return traitSelectionAbilityLine(label, ability, noEffect);
    }

    static String traitSelectionAbilityLine(String label, String ability, boolean noEffect) {
        String color = noEffect ? "yellow" : "green";
        return "<white>" + label + "</white> <dark_gray>|</dark_gray> <" + color + ">"
                + ability + "</" + color + ">";
    }

    static String traitSelectionTimeMarkup(int secondsRemaining) {
        String color;
        if (secondsRemaining >= 0 && secondsRemaining <= 5) {
            color = "dark_red";
        } else if (secondsRemaining >= 0 && secondsRemaining <= 15) {
            color = "yellow";
        } else {
            color = "green";
        }
        String selectionTime = secondsRemaining < 0 ? "제한 없음" : secondsRemaining + "초";
        return "<white>남은 시간</white> <" + color + ">" + selectionTime + "</" + color + ">";
    }

    private static String traitSelectionLoadoutLine(
            String label,
            net.minecraft.resources.ResourceLocation traitId
    ) {
        String color = TraitLoadout.isNone(traitId) ? "dark_red" : "yellow";
        String name = TraitLoadout.isNone(traitId) ? "선택 안 함" : traitName(traitId);
        return "<white>" + label + "</white> <dark_gray>|</dark_gray> <"
                + color + ">" + name + "</" + color + ">";
    }

    public void showTowerControl(ServerPlayer player, SemionGame game) {
        showTowerControl(player, game, null);
    }

    public void showTowerControl(ServerPlayer player, SemionGame game, BuildGuideService buildGuideService) {
        showTowerControl(player, game, buildGuideService, null);
    }

    /**
     * @param group 직업이 타워를 분류하는 경우 그 분류 이름. null 이면 분류 선택 화면부터 보여줍니다.
     */
    public void showTowerControl(ServerPlayer player, SemionGame game, BuildGuideService buildGuideService, String group) {
        var semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer == null) {
            show(player, "세미온 TD 타워", "<red>현재 게임 참가자가 아닙니다.</red>");
            return;
        }

        PlayerEconomy economy = semionPlayer.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        long nextTowerLimitDiamondCost = game.nextTowerLimitPurchaseDiamondCost(player.getUUID());
        long nextTowerLimitEmeraldCost = game.nextTowerLimitPurchaseEmeraldCost(player.getUUID());
        int towerCount = game.towerCapacityUsed(player.getUUID());
        int towerLimit = game.towerLimitForPlayer(player.getUUID());
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 관리</bold></gradient>\n");
        body.append(towerControlSummary(
                semionPlayer.teamId(),
                semionPlayer.laneId(),
                economy,
                nextGasUpgradeCost,
                towerCount,
                towerLimit,
                nextTowerLimitDiamondCost,
                nextTowerLimitEmeraldCost
        ));
        body.append(commandLink("인컴 업그레이드", "/semiontd emeraldup", "green"));
        body.append("  ");
        body.append(commandLink("타워 수 +" + game.economyConfig().towerLimit().purchaseIncreaseAmount(), "/semiontd tower limitup", "yellow"));
        body.append("  ");
        body.append(commandLink("상태 보기", "/semiontd ui", "aqua"));
        body.append("\n<divider>\n\n");

        Tower selectedTower = game.playerLane(player.getUUID())
                .flatMap(lane -> TowerPlacementPositions.resolveGrid(lane, player.blockPosition())
                        .map(lane::towerAt))
                .orElse(null);
        if (selectedTower != null) {
            body.append(towerControlSelectedTowerSummary(
                    selectedTower.type().displayName(),
                    selectedTower.health(),
                    selectedTower.currentMaxHealth(),
                    selectedTower.sellRefundAmount()
            ));
        }

        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerService.availableTowers(game, player.getUUID());
        SemionJob job = semionPlayer.job().orElse(JobRegistry.defaultJob());
        LinkedHashMap<String, List<ProductionTowerCatalog.CatalogEntry>> groups = towerGroups(job, entries);
        boolean showGroupPicker = selectedTower == null && group == null && groups.size() >= 2;
        if (selectedTower == null && group != null && !groups.isEmpty()) {
            entries = groups.getOrDefault(group, List.of());
        }
        List<TowerUpgradeOption> upgrades = selectedTower == null
                ? List.of()
                : ProductionTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (!upgrades.isEmpty()) {
            body.append("<gray>아래 버튼에서 업그레이드를 선택하세요.</gray>\n");
            body.append("<white> </white>\n");
        } else if (selectedTower != null) {
            body.append("<gray>현재 위치 타워는 더 이상 업그레이드할 수 없습니다.</gray>\n");
        } else if (showGroupPicker) {
            body.append("<gray>먼저 분류를 고르세요.</gray> <yellow>").append(groups.size()).append("</yellow><gray>개 계열</gray>\n");
        } else if (entries.isEmpty()) {
            body.append("<red>사용할 수 있는 타워가 없습니다.</red>\n");
        } else {
            body.append(towerConstructionCandidateSummary(group, entries.size()));
        }
        if (selectedTower == null && entries.stream()
                .anyMatch(entry -> entry.type().id().equals(QueenTowers.RANDOM_CARD_SOLDIER.id()))) {
            QueenCard nextCard = QueenStates.state(player.getUUID()).peekNextCard();
            body.append("<light_purple>다음 카드</light_purple> <white>")
                    .append(nextCard.label()).append(" (").append(nextCard.suit().displayName()).append(")</white>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (showGroupPicker) {
            for (Map.Entry<String, List<ProductionTowerCatalog.CatalogEntry>> groupEntry : groups.entrySet()) {
                actions.add(actionButton(
                        Component.literal(groupEntry.getKey() + " (" + groupEntry.getValue().size() + ")"),
                        "/semiontd tower ui " + groupEntry.getKey(),
                        Component.literal(groupEntry.getKey() + " 계열 타워 " + groupEntry.getValue().size() + "종을 봅니다."),
                        COMPACT_BUTTON_WIDTH
                ));
            }
        } else if (selectedTower == null) {
            for (ProductionTowerCatalog.CatalogEntry entry : entries) {
                long mineralCost = Math.max(0, entry.type().mineralCost());
                boolean recommended = buildGuideService != null && TowerPlacementPositions.resolveGrid(game.playerLane(player.getUUID()).orElse(null), player.blockPosition())
                        .map(position -> buildGuideService.isRecommendedTower(game, player.getUUID(), game.currentRound(), position, entry.type().id()))
                        .orElse(false);
                String command = HeroPartyTowers.role(entry.type())
                        .filter(role -> !HeroPartyStates.state(player.getUUID()).isCommitted(role))
                        .map(role -> "/semiontd hero companion " + role.id())
                        .orElse("/semiontd tower build " + entry.type().id());
                actions.add(towerButton(entry, mineralCost, economy.diamond() >= mineralCost, recommended, command));
            }
            if (group != null && groups.size() >= 2) {
                actions.add(actionButton(
                        Component.literal("← 분류 선택"),
                        "/semiontd tower ui",
                        Component.literal("계열 선택 화면으로 돌아갑니다."),
                        COMPACT_BUTTON_WIDTH
                ));
            }
        } else {
            for (TowerUpgradeOption option : upgrades) {
                boolean mineralAffordable = economy.diamond() >= option.mineralCost();
                boolean requirementsMet = selectedTower.meetsUpgradeRequirements(
                        game.playerLane(player.getUUID()).orElse(null), option);
                boolean recommended = buildGuideService != null
                        && buildGuideService.isRecommendedUpgrade(game, player.getUUID(), game.currentRound(), selectedTower.managementPosition(), option.id());
                actions.add(actionButton(
                        upgradeButtonLabel(option, mineralAffordable && requirementsMet
                                && advExperienceAffordable(selectedTower, option), recommended),
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
        double baseDamage = towerPrimaryDamage(tower);
        double currentDamage = previewEndTower != null
                ? previewEndTower.previewHatchedAttackDamage()
                : currentTowerPrimaryDamage(tower, combatStatsEntity.orElse(null));
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
        body.append("<white>팀</white> ").append(teamMarkup(tower.teamId())).append(" <dark_gray>|</dark_gray> ").append("<white>라인</white> <yellow>#").append(tower.laneId()).append("</yellow>\n");
        body.append("<divider>\n");
        body.append(formatHealth(tower.health(), currentMaxHealth, "")).append(formatIncrease(tower.type().maxHealth(), currentMaxHealth)).append('\n');
        body.append(formatTowerDamage(tower, currentDamage)).append(formatIncrease(baseDamage, currentDamage)).append('\n');
        double baseAttacksPerSecond = 20.0 / Math.max(1, tower.type().attackIntervalTicks());
        double currentAttacksPerSecond = 20.0 / Math.max(1, currentAttackIntervalTicks);
        body.append(formatAttackSpeed(currentAttacksPerSecond, currentAttackIntervalTicks, "")).append(formatIncrease(baseAttacksPerSecond, currentAttacksPerSecond)).append('\n');
        body.append(formatAttackRange(currentRange, "")).append(formatIncrease(tower.type().range(), currentRange)).append(" <dark_gray>|</dark_gray> ").append(formatAggroPriority(tower.type().aggroPriority(), "")).append('\n');
        body.append("<divider>\n");
        towerEntity.ifPresent(entity -> {int beforeLength = body.length(); appendTowerTimedEffects(body, entity); if (body.length() > beforeLength) {body.append("<divider>\n");}});
        int beforeRuntimeLength = body.length();
        appendTowerRuntimeDetails(body, tower);
        if (body.length() > beforeRuntimeLength) {body.append("<divider>\n");}
        body.append(formatSellPrice(tower.sellRefundAmount(), "")).append('\n');
        body.append("<divider>\n");
        appendTowerDescription(body, tower.type().description());
        body.append("<divider>\n");
        if (!ownedByPlayer) {
            body.append("\n<red>자신이 설치한 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        } else if (!sameLane) {
            body.append("\n<red>현재 담당 라인의 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        int actionColumns = 2;
        if (ownedByPlayer && sameLane) {
            var managementPosition = tower.managementPosition();
            List<TowerUpgradeOption> upgrades = ProductionTowerService.availableUpgrades(game, player.getUUID(), managementPosition);
            for (TowerUpgradeOption option : upgrades) {
                boolean mineralAffordable = semionPlayer.economy().diamond() >= option.mineralCost();
                boolean requirementsMet = tower.meetsUpgradeRequirements(
                        game.playerLane(player.getUUID()).orElse(null), option);
                boolean recommended = buildGuideService != null
                        && buildGuideService.isRecommendedUpgrade(game, player.getUUID(), game.currentRound(), managementPosition, option.id());
                actions.add(actionButton(
                        upgradeButtonLabel(option, mineralAffordable && requirementsMet
                                && advExperienceAffordable(tower, option), recommended),
                        "/semiontd tower upgrade "
                                + option.id() + " "
                                + managementPosition.x() + " "
                                + managementPosition.y() + " "
                                + managementPosition.z(),
                        upgradeTooltip(option, mineralAffordable, recommended, tower),
                        COMPACT_BUTTON_WIDTH
                ));
            }
            if (tower instanceof FutureAgencyLeaderTower) {
                List<ActionButton> upgradeActions = List.copyOf(actions);
                List<Integer> layout = futureAgencyUpgradeGrid(upgrades);
                actions.clear();
                for (int index : layout) {
                    actions.add(index < 0 ? actionSpacer() : upgradeActions.get(index));
                }
                actionColumns = 3;
            }
            if (tower instanceof HeroTower) {
                actions.add(actionButton("용사 상점", "/semiontd hero shop", "장비를 구매·강화·교체합니다."));
                actions.add(actionButton("현재 퀘스트", "/semiontd hero quest", "현재 웨이브 퀘스트를 확인합니다."));
                actions.add(actionButton("파티 현황", "/semiontd hero party", "확정된 동료와 성장치를 확인합니다."));
            }
            if (tower.canBeSold()) {
                actions.add(actionButton(
                        tower.saleActionLabel(),
                        "/semiontd tower sell "
                                + managementPosition.x() + " "
                                + managementPosition.y() + " "
                                + managementPosition.z(),
                        Component.literal(tower.sellRefundAmount() > 0
                                ? "이 타워를 판매하고 환불을 받습니다."
                                : "이 타워를 제거합니다."),
                        BUTTON_WIDTH
                ));
            }
        }
        showActions(player, "세미온 TD 타워 상세", actionDialogBodies(body.toString()), actions, actionColumns);
    }

    public void showHeroCompanionConfirmation(ServerPlayer player, SemionGame game, HeroCompanionRole role) {
        if (player == null || game == null || role == null) {
            return;
        }
        HeroPartyState state = HeroPartyStates.state(player.getUUID());
        if (!HeroPartyStates.hasActiveHero(game, player.getUUID())) {
            show(player, "동료 선택", "<red>용사를 먼저 설치해야 합니다.</red>");
            return;
        }
        if (!state.canCommit(role)) {
            show(player, "동료 선택", "<red>이미 네 종류의 동료를 확정했습니다.</red>");
            return;
        }
        TowerType defaults = HeroPartyTowers.companion(role, 1);
        TowerType type = ProductionTowerCatalog.find(defaults.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(defaults);
        String body = "<yellow><bold>" + role.displayName() + "</bold></yellow>를 동료로 선택합니다.\n\n"
                + "<gray>설치에 성공하면 이 경기에서는 판매해도 동료 종류가 유지됩니다.</gray>\n"
                + "<gray>타워 수</gray> <yellow>" + TowerCapacity.slotCost(type) + "</yellow> <dark_gray>|</dark_gray> <gray>가격</gray> <aqua>"
                + type.mineralCost() + " 다이아</aqua>\n\n"
                + "<red>최대 네 종류만 선택할 수 있습니다.</red>";
        List<ActionButton> actions = List.of(
                actionButton("선택 확정", "/semiontd tower build " + type.id(), "현재 위치에 설치하며, 성공 시 동료가 확정됩니다."),
                actionButton("취소", "/semiontd tower ui", "타워 관리로 돌아갑니다.")
        );
        showActions(player, "용사 동료 선택", body, actions, 2);
    }

    public void showHeroQuest(ServerPlayer player, SemionGame game) {
        if (player == null || game == null) {
            return;
        }
        HeroPartyState.HeroQuestSnapshot quest = HeroPartyStates.state(player.getUUID()).quest();
        if (quest == null) {
            show(player, "용사 퀘스트", "<gray>현재 배정된 퀘스트가 없습니다.</gray>");
            return;
        }
        String status = quest.completed() ? "<green>완료</green>" : quest.failed() ? "<red>실패</red>" : "<yellow>진행 중</yellow>";
        String body = "<gold><bold>라운드 " + quest.round() + " 퀘스트</bold></gold>\n"
                + "<white>" + quest.label() + "</white>\n"
                + "<gray>진행</gray> <yellow>" + oneDecimal(quest.progress()) + "/" + oneDecimal(quest.target()) + "</yellow>\n"
                + "<gray>보상</gray> <aqua>모험 점수 " + quest.reward() + "</aqua>\n"
                + "<gray>상태</gray> " + status;
        showActions(player, "용사 퀘스트", body, List.of(actionButton("용사 상점", "/semiontd hero shop", "용사 상점을 엽니다.")), 1);
    }

    public void showHeroParty(ServerPlayer player, SemionGame game) {
        if (player == null || game == null) {
            return;
        }
        HeroPartyState state = HeroPartyStates.state(player.getUUID());
        String companions = state.committedCompanions().isEmpty()
                ? "<gray>없음</gray>"
                : state.committedCompanions().stream()
                        .sorted()
                        .map(role -> "<yellow>" + role.displayName() + "</yellow>")
                        .collect(Collectors.joining(", "));
        String body = "<gold><bold>용사 파티</bold></gold>\n"
                + "<gray>확정 동료</gray> " + companions + " <dark_gray>(" + state.committedCompanions().size() + "/" + HeroPartyBalance.MAX_COMPANIONS + ")</dark_gray>\n"
                + "<gray>모험 점수</gray> <aqua>" + state.adventurePoints() + "</aqua>\n"
                + "<gray>공격·회복 보너스</gray> <green>+" + oneDecimal((HeroPartyBalance.partyDamageMultiplier(state.adventurePoints()) - 1.0) * 100.0) + "%</green>\n"
                + "<gray>최대 체력 보너스</gray> <green>+" + oneDecimal((HeroPartyBalance.partyHealthMultiplier(state.adventurePoints()) - 1.0) * 100.0) + "%</green>";
        showActions(player, "용사 파티", body, List.of(
                actionButton("용사 상점", "/semiontd hero shop", "용사 상점을 엽니다."),
                actionButton("동료 스킨", "/semiontd hero skin", "동료별 플레이어 스킨을 설정합니다.")
        ), 2);
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

    public void showSandboxRoundControl(ServerPlayer player, SemionGame game) {
        if (game == null || !game.isSandboxMode() || !game.isActiveParticipant(player.getUUID())) {
            show(player, "세미온 TD 샌드박스", "<red>진행 중인 본인 샌드박스가 없습니다.</red>");
            return;
        }
        int currentRound = game.currentRound();
        ArrayList<ActionButton> actions = new ArrayList<>();
        if (currentRound > 1) {
            actions.add(actionButton(
                    "이전 라운드",
                    "/semiontd sandbox round " + (currentRound - 1),
                    "현재 몹을 정리하고 " + (currentRound - 1) + "라운드 준비 단계로 이동합니다."
            ));
        }
        if (currentRound < Integer.MAX_VALUE) {
            actions.add(actionButton(
                    "다음 라운드",
                    "/semiontd sandbox round " + (currentRound + 1),
                    "현재 몹을 정리하고 " + (currentRound + 1) + "라운드 준비 단계로 이동합니다."
            ));
        }
        showActions(
                player,
                "세미온 TD 샌드박스",
                statusControlBodies(
                        miniMessage("<gradient:#facc15:#fb923c><bold>라운드 이동</bold></gradient>"),
                        miniMessage("<white>현재 라운드</white> <gold>" + currentRound + "</gold>"),
                        miniMessage("<gray>타워·보스 체력·자원은 유지되며 현재 몹과 예약 소환은 제거됩니다.</gray>")
                ),
                actions,
                2
        );
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
        StringBuilder status = new StringBuilder();
        status.append("<white>내 팀</white> ").append(teamMarkup(semionPlayer.teamId())).append("\n");
        status.append("<white>현재 타깃</white> ")
                .append(leaderTargeting.targetTeamId().map(SemionDialogService::teamMarkup).orElse("<dark_gray>없음</dark_gray>"))
                .append("\n");
        if (!leaderTargeting.canUse()) {
            Component cooldown = miniMessage(new StringBuilder("<red>쿨타임: </red><yellow>")
                    .append(leaderTargeting.cooldownRemainingRounds())
                    .append("라운드</yellow>")
                    .toString());
            showActions(
                    player,
                    "세미온 TD 팀장",
                    statusControlBodies(
                            miniMessage("<gradient:#facc15:#fb923c><bold>팀장 타깃 지정</bold></gradient>"),
                            miniMessage(status.toString().stripTrailing()),
                            cooldown
                    ),
                    List.of(),
                    TEAM_TARGET_COLUMNS
            );
            return;
        }

        ArrayList<ActionButton> actions = leaderTargetCandidates(game, semionPlayer.teamId()).stream()
                .map(candidate -> actionButton(
                        teamButtonLabel(candidate.id()),
                        "/semiontd leader target " + candidate.id().name().toLowerCase(java.util.Locale.ROOT),
                        Component.literal(candidate.id().name() + " 팀으로 이후 견제 유닛을 보냅니다."),
                        TEAM_TARGET_BUTTON_WIDTH
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        showActions(
                player,
                "세미온 TD 팀장",
                statusControlBodies(
                        miniMessage("<gradient:#facc15:#fb923c><bold>팀장 타깃 지정</bold></gradient>"),
                        miniMessage(status.toString().stripTrailing()),
                        miniMessage("<gray>견제 유닛을 보낼 팀을 선택하세요.</gray>")
                ),
                actions,
                TEAM_TARGET_COLUMNS
        );
    }

    private static List<DialogBody> statusControlBodies(
            Component title,
            Component status,
            Component description
    ) {
        Component header = Component.empty()
                .append(new HeaderMessage(title, BODY_WIDTH).asVanillaComponent())
                .append("\n")
                .append(status)
                .append("\n\n")
                .append(description);
        return List.of(
                new PlainMessage(header, BODY_WIDTH),
                HeaderMessage.divider(BODY_WIDTH)
        );
    }

    static List<SemionTeam> leaderTargetCandidates(SemionGame game, TeamId ownTeamId) {
        return game.teams().values().stream()
                .filter(candidate -> candidate.active() && !candidate.eliminated())
                .filter(candidate -> candidate.id() != ownTeamId)
                .sorted(Comparator.comparing(SemionTeam::id))
                .toList();
    }

    public void showSummonShop(ServerPlayer player, SemionGame game, int page) {
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        long emerald = semionPlayer == null ? 0 : semionPlayer.economy().emerald();
        boolean freeSummons = game.summonsAreFree();
        List<SummonMonsterType> summons = sortedSummons(game.summonShop().all());
        int pageCount = pageCount(summons.size());
        int safePage = clampPage(page, pageCount);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append(summonShopSummary(safePage, pageCount, summons.size(), freeSummons));
        appendSummonNavigation(body, "/semiontd summonui ", safePage, pageCount);

        ArrayList<ActionButton> actions = summons.stream()
                .skip((long) (safePage - 1) * SUMMON_PAGE_SIZE)
                .limit(SUMMON_PAGE_SIZE)
                .map(type -> {
                    boolean affordable = freeSummons || emerald >= type.gasCost();
                    return actionButton(
                            summonButtonLabel(type, affordable),
                            "/semiontd summon " + type.id(),
                            summonTooltip(type, affordable, freeSummons),
                            SUMMON_BUTTON_WIDTH
                    );
                })
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
        body.append(summonShopSummary(safePage, pageCount, summons.size(), true));
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

    static String summonShopSummary(int page, int pageCount, int summonCount, boolean sandbox) {
        StringBuilder summary = new StringBuilder();
        summary.append("<white>페이지</white> <yellow>").append(page)
                .append("</yellow><white>/</white><yellow>").append(pageCount).append("</yellow>")
                .append(" <dark_gray>|</dark_gray> <white>소환 후보</white> <yellow>")
                .append(summonCount).append("</yellow>\n")
                .append("<gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>");
        if (sandbox) {
            summary.append("\n<green>샌드박스 소환은 무료이며 수입이 증가하지 않습니다.</green>");
        }
        return summary.toString();
    }

    static void appendSummonNavigation(StringBuilder body, String commandPrefix, int page, int pageCount) {
        body.append("\n<divider>\n");
        body.append("<white>페이지 이동</white> ");
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
        body.append("\n<divider>");
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
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_FLAT_RANGE_BONUS,
                "<green>🎯 사거리 증가 +", "칸</green>");
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_FLAT_RANGE_REDUCTION,
                "<red>🎯 사거리 감소 -", "칸</red>");
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_HEALTH_REGEN_PER_SECOND,
                "<green>❤ 초당 회복 +", "/초</green>");
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_HEALTH_LOSS_PER_SECOND,
                "<red>❤ 초당 체력 감소 -", "/초</red>");
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_FLAT_DAMAGE_BONUS,
                "<green>⚔ 공격력 증가 +", "</green>");
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION,
                "<red>⚔ 공격력 감소 -", "</red>");
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS,
                "<green>❤ 최대 체력 증가 +", "</green>");
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_FLAT_MAX_HEALTH_REDUCTION,
                "<red>❤ 최대 체력 감소 -", "</red>");
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
        lines.addAll(SuccubusDreams.detailLines(tower));
        lines.addAll(tower.runtimeDetailLines());
        return lines;
    }

    static String formatTowerDamage(Tower tower, double currentDamage) {
        if (tower != null && tower.primaryDamageType() == DamageType.MAGIC) {
            return formatMagicDamage(currentDamage, "");
        }
        return formatAttackDamage(currentDamage, "");
    }

    static String formatTowerTypeDamage(TowerType type, double damage) {
        if (type != null && type.primaryDamageType() == DamageType.MAGIC) {
            return formatMagicDamage(damage, "");
        }
        return formatAttackDamage(damage, "");
    }

    static double towerTypePrimaryDamage(TowerType type) {
        if (type == null) {
            return 0.0;
        }
        return primaryDamage(type, type.primaryDamageType());
    }

    static String formatTowerTypePrimaryDamage(TowerType type) {
        return formatTowerTypeDamage(type, towerTypePrimaryDamage(type));
    }

    static double towerPrimaryDamage(Tower tower) {
        if (tower == null) {
            return 0.0;
        }
        return primaryDamage(tower.type(), tower.primaryDamageType());
    }

    static double currentTowerPrimaryDamage(Tower tower, SemionTowerEntity towerEntity) {
        if (tower == null) {
            return 0.0;
        }
        double baseDamage = towerPrimaryDamage(tower);
        if (tower.primaryDamageType() == DamageType.MAGIC && !SuccubusTowers.isSuccubusTower(tower.type())) {
            return towerEntity == null
                    ? baseDamage
                    : tower.resolveOutgoingDamage(towerEntity, null, baseDamage);
        }
        return towerEntity == null
                ? tower.modifyAttackDamage(null, null, baseDamage)
                : tower.resolveBasicAttackOutgoingDamage(towerEntity, null, towerEntity.attackDamageAmount(null));
    }

    private static double primaryDamage(TowerType type, DamageType damageType) {
        if (damageType == DamageType.MAGIC) {
            double magicDamage = TowerBalanceRuntime.ability(type.id(), "magicDamage", Double.NaN);
            if (Double.isFinite(magicDamage)) {
                return magicDamage;
            }
        }
        return type.damage();
    }

    private static void appendTowerRuntimeDetails(StringBuilder body, Tower tower) {
        List<String> lines = towerRuntimeDetailLines(tower).stream().filter(line -> line != null && !line.isBlank()).toList();
        if (lines.isEmpty()) {
            return;
        }
        body.append("<yellow>⭐ 고유 능력</yellow>\n");
        for (String line : lines) {body.append("<dark_gray>-</dark_gray> <green>").append(line).append("</green>\n");
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

    private static void appendTimedEffectValue(
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
                .append(oneDecimal(magnitude))
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
            showActions(player, title, actionDialogBodies(body), actions, columns);
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

    static List<DialogBody> actionDialogBodies(String body) {
        return actionDialogBodies(body, () -> dividerComponent(BODY_WIDTH));
    }

    static List<DialogBody> actionDialogBodies(String body, Supplier<Component> dividerFactory) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        int split = body.indexOf('\n');
        if (split < 0) {
            return List.of(new HeaderMessage(miniMessage(body), BODY_WIDTH));
        }
        ArrayList<DialogBody> bodies = new ArrayList<>();
        MutableComponent contentComponent = Component.empty();
        String header = body.substring(0, split);
        String content = body.substring(split + 1);
        if (!header.isBlank()) {
            contentComponent.append(new HeaderMessage(miniMessage(header), BODY_WIDTH).asVanillaComponent());
        }
        String[] sections = content.split("(?m)^<divider>\\n?", -1);
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].strip();
            if (!section.isBlank()) {
                if (!contentComponent.getSiblings().isEmpty()) {
                    contentComponent.append("\n");
                }
                contentComponent.append(miniMessage(section));
            }
            if (i < sections.length - 1) {
                contentComponent.append("\n");
                contentComponent.append(dividerFactory.get());
            }
        }
        if (!contentComponent.getSiblings().isEmpty()) {
            bodies.add(new PlainMessage(contentComponent, BODY_WIDTH));
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

    private static ActionButton actionSpacer() {
        return new ActionButton(new CommonButtonData(Component.empty(), Optional.empty(), 1), Optional.empty());
    }

    static List<Integer> futureAgencyUpgradeGrid(List<TowerUpgradeOption> upgrades) {
        int saveIndex = -1;
        ArrayList<Integer> policies = new ArrayList<>(3);
        ArrayList<Integer> others = new ArrayList<>();
        for (int index = 0; index < upgrades.size(); index++) {
            TowerUpgradeOption option = upgrades.get(index);
            if (FutureAgencyLeaderTower.SAVE_WORLD.equals(option.id())) saveIndex = index;
            else if (FutureAgencyPolicy.fromUpgradeId(option.id()).isPresent()) policies.add(index);
            else others.add(index);
        }
        if (saveIndex < 0) {
            return java.util.stream.IntStream.range(0, upgrades.size()).boxed().toList();
        }
        ArrayList<Integer> layout = new ArrayList<>(upgrades.size() + 2);
        layout.add(-1);
        layout.add(saveIndex);
        layout.add(-1);
        layout.addAll(policies);
        while (!policies.isEmpty() && layout.size() < 6) layout.add(-1);
        layout.addAll(others);
        return List.copyOf(layout);
    }

    private static String commandLink(String label, String command, String color) {
        return "<click:run_command:'" + command + "'><hover:show_text:'" + label + "'><" + color + ">[" + label + "]</" + color + "></hover></click>";
    }

    /**
     * 직업이 모든 건설 후보에 분류를 붙였을 때만 분류 맵을 돌려줍니다. 하나라도 빠지면 기존 평면 목록을
     * 유지하도록 빈 맵을 돌려줍니다.
     */
    private static LinkedHashMap<String, List<ProductionTowerCatalog.CatalogEntry>> towerGroups(
            SemionJob job,
            List<ProductionTowerCatalog.CatalogEntry> entries
    ) {
        LinkedHashMap<String, List<ProductionTowerCatalog.CatalogEntry>> groups = new LinkedHashMap<>();
        for (ProductionTowerCatalog.CatalogEntry entry : entries) {
            String group = job.towerGroup(entry.type());
            if (group == null || group.isBlank()) {
                return new LinkedHashMap<>();
            }
            groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(entry);
        }
        return groups;
    }

    private static ActionButton towerButton(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable, boolean recommended) {
        return towerButton(entry, mineralCost, affordable, recommended, "/semiontd tower build " + entry.type().id());
    }

    private static ActionButton towerButton(
            ProductionTowerCatalog.CatalogEntry entry,
            long mineralCost,
            boolean affordable,
            boolean recommended,
            String command
    ) {
        return actionButton(
                towerButtonLabel(entry, affordable, recommended),
                command,
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

    private static ActionButton jobManagementButton(SemionJob job) {
        boolean enabled = JobRegistry.isEnabled(job);
        Component label = Component.literal((enabled ? "켜짐 · " : "꺼짐 · ") + job.displayName().getString())
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        String command = "/semiontd job " + (enabled ? "disable " : "enable ") + job.id();
        String tooltip = enabled
                ? "클릭하면 이 직업을 비활성화합니다. 진행 중인 경기는 유지됩니다."
                : "클릭하면 이 직업을 다시 활성화합니다.";
        return actionButton(label, command, Component.literal(tooltip), BUTTON_WIDTH);
    }

    private static ActionButton traitButton(SemionTrait trait, TraitSlot slot, boolean selected) {
        return actionButton(
                traitButtonLabel(trait, selected),
                traitSelectionCommand(trait, slot),
                traitTooltip(trait, slot),
                TRAIT_BUTTON_WIDTH
        );
    }

    public static String jobSelectionCommand(SemionJob job) {
        return JobRegistry.isEnabled(job) ? "/semiontd job select " + job.id().getPath() : "";
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
        if (!JobRegistry.isEnabled(job)) {
            return Component.literal("✕ " + job.displayName().getString() + " (비활성화)")
                    .withStyle(ChatFormatting.RED);
        }
        String prefix = selected ? "✓ " : "";
        return Component.literal(prefix + job.displayName().getString())
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    static Component jobTooltip(SemionJob job, boolean selected) {
        if (!JobRegistry.isEnabled(job)) {
            MutableComponent tooltip = job.displayName().copy().withStyle(ChatFormatting.RED)
                    .append(Component.literal("\n관리자에 의해 비활성화된 직업입니다.").withStyle(ChatFormatting.RED));
            for (Component line : job.description()) {
                tooltip.append(Component.literal("\n").withStyle(ChatFormatting.GRAY).append(line.copy()));
            }
            return tooltip;
        }
        MutableComponent tooltip = job.displayName().copy()
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA);
        if (selected) {
            tooltip.append(Component.literal("\n현재 선택된 직업입니다.").withStyle(ChatFormatting.GREEN));
        }
        for (Component line : job.description()) {
            tooltip.append(Component.literal("\n").withStyle(ChatFormatting.GRAY).append(line.copy()));
        }
        return tooltip;
    }

    private static String jobCategoryLabel(String category, List<SemionJob> jobs) {
        long enabled = jobs.stream().filter(JobRegistry::isEnabled).count();
        return category + " (활성 " + enabled + "/" + jobs.size() + ")";
    }

    private static Component traitButtonLabel(SemionTrait trait, boolean selected) {
        return trait.displayName().copy()
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    static Component traitTooltip(SemionTrait trait, TraitSlot slot) {
        MutableComponent tooltip = trait.displayName().copy()
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(slot.displayName() + " " + Math.round(slot.effectScale() * 100.0D) + "%")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(dividerComponent(160))
                .append(Component.literal("\n"))
                .append(trait.effectSummary(slot).copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(dividerComponent(160));
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

    private static Component towerTooltip(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable, boolean recommended) {
        var type = entry.type();
        double attacksPerSecond = 20.0 / Math.max(1, type.attackIntervalTicks());
        MutableComponent tooltip = mutableMiniMessage("<white><bold>" + type.displayName() + "</bold></white>\n" + (recommended ? "<blue>빌드 추천</blue>\n" : "") + DIAMOND_GRADIENT + "\uD83D\uDC8E " + mineralCost + " 다이아" + GRADIENT_CLOSE + (affordable ? " <green>(구매 가능)</green>" : " <red>(부족)</red>") + "\n<yellow>타워 수 " + TowerCapacity.slotCost(type) + "</yellow>\n");
        tooltip.append(dividerComponent(160)).append(Component.literal("\n"));
        tooltip.append(mutableMiniMessage(formatHealth(type.maxHealth(), "") + "\n" + formatTowerTypePrimaryDamage(type) + "\n" + formatAttackSpeed(attacksPerSecond, type.attackIntervalTicks(), "") + "\n" + formatAttackRange(type.range(), "") + " <dark_gray>|</dark_gray> " + formatAggroPriority(type.aggroPriority(), "") + "\n"));
        tooltip.append(dividerComponent(160));
        appendTowerDescription(tooltip, type.description().stream().limit(2).toList());
        return tooltip;
    }

    private static Component upgradeTooltip(TowerUpgradeOption option, boolean affordable, boolean recommended, Tower currentTower) {
        Optional<ProductionTowerCatalog.CatalogEntry> target = ProductionTowerCatalog.entry(option.targetType());
        if (target.isEmpty()) {
            return Component.literal("대상 타워를 찾을 수 없습니다.\n비용 " + option.mineralCost() + " 다이아");
        }
        var entry = target.get();
        var type = entry.type();
        List<String> customLines = currentTower.upgradeTooltipLines(option);
        boolean actionOnly = type.id().equals(currentTower.type().id()) && !customLines.isEmpty();
        double attacksPerSecond = 20.0 / Math.max(1, type.attackIntervalTicks());
        int capacityDelta = TowerCapacity.slotCost(type) - TowerCapacity.slotCost(currentTower.type());
        String capacityLine = capacityDelta == 0 ? "" : "<yellow>타워 수 +" + capacityDelta + "</yellow>\n";
        MutableComponent tooltip = mutableMiniMessage("<yellow><bold>" + option.displayName() + "</bold></yellow>\n" + (recommended ? "<blue>빌드 추천</blue>\n" : "") + "<gray>대상</gray> <white>" + type.displayName() + "</white>\n" + DIAMOND_GRADIENT + "\uD83D\uDC8E " + option.mineralCost() + " 다이아" + GRADIENT_CLOSE + (affordable ? " <green>(구매 가능)</green>" : " <red>(부족)</red>") + "\n" + capacityLine + advExperienceRequirementLine(currentTower, option));
        if (!actionOnly) {
            tooltip.append(dividerComponent(160)).append(Component.literal("\n"));
            tooltip.append(mutableMiniMessage(formatHealth(type.maxHealth(), "") + "\n" + formatTowerTypePrimaryDamage(type) + "\n" + formatAttackSpeed(attacksPerSecond, type.attackIntervalTicks(), "") + "\n" + formatAttackRange(type.range(), "") + " <dark_gray>|</dark_gray> " + formatAggroPriority(type.aggroPriority(), "") + "\n"));
            tooltip.append(dividerComponent(160));
            appendTowerDescription(tooltip, type.description());
        }
        for (String line : customLines) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal("\n")).append(mutableMiniMessage(line));
            }
        }
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
            case TOWER_SELL -> "<red>타워 판매</red> <white>" + BuildGuideService.subjectDisplayName(action) + "</white>"
                    + " <gray>" + buildPositionLabel(action) + "</gray>"
                    + " <dark_gray>|</dark_gray> <aqua>💎 +" + action.incomeGain() + "</aqua>";
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
        return summonTooltip(type, affordable, false);
    }

    private static Component summonTooltip(SummonMonsterType type, boolean affordable, boolean sandbox) {
        double attacksPerSecond = 20.0 / 13.0;
        MutableComponent tooltip = mutableMiniMessage("<yellow><bold>" + type.displayName() + "</bold></yellow> <dark_gray>|</dark_gray> <gray>" + roleList(type) + "</gray>\n");
        tooltip.append(dividerComponent(160)).append(Component.literal("\n"));
        if (sandbox) {
            tooltip.append(mutableMiniMessage("<green>◆ 무료</green>\n<gray>수입 증가 없음</gray>\n" + formatKillReward(type.mineralReward(), "") + "\n"));
        } else {
            tooltip.append(mutableMiniMessage(formatEmerald(type.gasCost(), affordable, "") + "\n" + formatKillReward(type.mineralReward(), "") + "\n" + formatIncome(type.incomeGain(), type.incomeRatio(), "") + "\n"));
        }
        tooltip.append(dividerComponent(160)).append(Component.literal("\n"));
        tooltip.append(mutableMiniMessage(formatHealth(type.maxHealth(), "") + "\n" + formatAttackDamage(type.attackDamage(), "") + "\n" + formatAttackSpeed(attacksPerSecond, 13, "") + "\n" + formatDefense(type.armor(), "") + " <dark_gray>|</dark_gray> " + formatResistance(type.resistance(), "") + "\n" + formatAggroPriority(type.targetRolePriority(), "") + "\n"));
        tooltip.append(dividerComponent(160)).append(Component.literal("\n"));
        tooltip.append(mutableMiniMessage(formatDamageType(damageTypeLabel(type.damageType()), "") + " <dark_gray>|</dark_gray> " + formatAttackKind(attackKindIcon(type.attackKind()), attackKindLabel(type.attackKind()), "") + "\n" + formatSize(type.dimensions().width(), type.dimensions().height(), "") + " <dark_gray>|</dark_gray> " + formatAbility(abilityActivationList(type), "") + "\n"));
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
        if (description == null || description.stream().noneMatch(line -> line != null && !line.isBlank())) {
            return;
        }
        tooltip.append(dividerComponent(160));
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
        return decoratedHeader(title, width, ChatFormatting.DARK_GRAY);
    }

    private static PlainMessage decoratedHeader(Component title, int width, ChatFormatting sideColor) {
        Component side = Component.literal("──────").withStyle(sideColor);
        return new PlainMessage(
                Component.empty().append(side).append(" ").append(title).append(" ").append(side),
                width
        );
    }

    static HeaderMessage jobStatisticsHeader(Component title) {
        return new HeaderMessage(title, JOB_STATISTICS_WIDTH);
    }

    static HeaderMessage jobStatisticsDetailHeader(Component title) {
        return new HeaderMessage(title, JOB_STATISTICS_DETAIL_WIDTH);
    }

    static HeaderMessage jobStatisticsListHeader() {
        return new HeaderMessage(
                Component.literal("직업 목록").withStyle(ChatFormatting.YELLOW),
                JOB_STATISTICS_WIDTH
        );
    }

    static PlainMessage jobStatisticsDivider() {
        return HeaderMessage.divider(JOB_STATISTICS_WIDTH);
    }

    static PlainMessage jobStatisticsDetailDivider() {
        return HeaderMessage.divider(JOB_STATISTICS_DETAIL_TABLE_WIDTH);
    }

    private static void appendJobStatisticsState(List<DialogBody> bodies, JobStatisticsState state) {
        if (state == JobStatisticsState.LOADING) {
            bodies.add(new PlainMessage(
                    Component.literal("재집계 중입니다. 마지막 정상 통계를 표시합니다.")
                            .withStyle(ChatFormatting.YELLOW),
                    JOB_STATISTICS_WIDTH
            ));
        } else if (state == JobStatisticsState.FAILED) {
            bodies.add(new PlainMessage(
                    Component.literal("최근 갱신에 실패했습니다. 마지막 정상 통계를 표시합니다.")
                            .withStyle(ChatFormatting.RED),
                    JOB_STATISTICS_WIDTH
            ));
        }
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

    static Component playerStatusTable(List<PlayerStatusRow> rows) {
        StringBuilder body = new StringBuilder("\n");
        body.append("<divider>\n");
        Component divider = ((PlainMessage) actionDialogBodies(
                body.toString(),
                () -> dividerComponent(PLAYER_STATUS_WIDTH)
        ).getFirst())
                .contents()
                .getSiblings()
                .getLast();

        MutableComponent table = playerStatusHeaderWithBodySpacing(playerStatusHeader(divider));

        TeamId currentTeam = null;
        for (PlayerStatusRow row : rows) {
            if (currentTeam != null && row.teamId() != currentTeam) {
                appendPlayerStatusTeamSpacing(table);
            }
            table.append("\n")
                    .append(playerStatusBody(row));
            currentTeam = row.teamId();
        }

        return table;
    }

    static MutableComponent playerStatusDialogContents(Component title, Component table) {
        return Component.empty()
                .append(title)
                .append("\n")
                .append(table);
    }

    static MutableComponent playerStatusHeaderWithBodySpacing(Component header) {
        return header.copy().append("\n");
    }

    static Component playerStatusHeader(Component divider) {
        List<Component> labels = playerStatusHeaderLabels();
        Component header = playerStatusTableRow(
                labels.get(0),
                centeredTableCell(
                        labels.get(1),
                        PLAYER_STATUS_AVATAR_WIDTH + PLAYER_STATUS_NAME_WIDTH
                ),
                labels.get(2),
                labels.get(3),
                labels.get(4),
                labels.get(5),
                labels.get(6),
                PLAYER_STATUS_JOB_WIDTH
        );
        return playerStatusHeaderWithDivider(header, divider);
    }

    static List<Component> playerStatusHeaderLabels() {
        return List.of(
                Component.literal("팀").withStyle(ChatFormatting.WHITE),
                Component.literal("플레이어").withStyle(ChatFormatting.WHITE),
                Component.literal("직업").withStyle(ChatFormatting.WHITE),
                Component.literal("다이아").withStyle(ChatFormatting.AQUA),
                Component.literal("에메랄드").withStyle(ChatFormatting.GREEN),
                Component.literal("수입").withStyle(ChatFormatting.YELLOW),
                Component.literal("타워").withStyle(ChatFormatting.GOLD)
        );
    }

    static MutableComponent playerStatusHeaderWithDivider(Component header, Component divider) {
        return Component.empty()
                .append(header)
                .append("\n")
                .append(divider.copy());
    }

    static void appendPlayerStatusTeamSpacing(MutableComponent table) {
        table.append("\n");
    }

    static Component playerStatusJob(String jobName) {
        return Component.literal(jobName).withStyle(ChatFormatting.WHITE);
    }

    private static Component playerStatusBody(PlayerStatusRow row) {
        Component name = playerStatusName(row);
        Component playerCell = playerStatusPlayerCell(
                playerStatusAvatar(row.playerName()),
                name,
                PLAYER_STATUS_AVATAR_WIDTH + PLAYER_STATUS_NAME_WIDTH - PLAYER_STATUS_BODY_JOB_SHIFT,
                AvatarVariant.COMPACT.imageSize,
                TextUncenterer.width(name)
        );
        return playerStatusTableRow(
                Component.literal(row.teamId().name())
                        .withStyle(teamColor(row.teamId()), ChatFormatting.BOLD),
                playerCell,
                playerStatusJob(row.jobName()),
                Component.literal(Long.toString(row.diamond()))
                        .withStyle(ChatFormatting.AQUA),
                Component.literal(Long.toString(row.emerald()))
                        .withStyle(ChatFormatting.GREEN),
                Component.literal(Long.toString(row.income()))
                        .withStyle(ChatFormatting.YELLOW),
                Component.literal(Integer.toString(row.towerCount()))
                        .withStyle(ChatFormatting.GOLD),
                PLAYER_STATUS_JOB_WIDTH + PLAYER_STATUS_BODY_JOB_SHIFT
        );
    }

    static Component playerStatusPlayerCell(
            Component avatar,
            Component playerName,
            int width,
            int avatarWidth,
            int playerNameWidth
    ) {
        Component value = Component.empty()
                .append(avatar)
                .append(Component.literal(" "))
                .append(playerName);
        return centeredTableCell(
                value,
                width,
                avatarWidth + PLAYER_STATUS_AVATAR_NAME_GAP + playerNameWidth
        );
    }

    static Component playerStatusName(PlayerStatusRow row) {
        MutableComponent name = Component.literal(row.playerName())
                .withStyle(teamColor(row.teamId()));
        return row.teamLeader() ? name.withStyle(ChatFormatting.BOLD) : name;
    }

    private static MutableComponent playerStatusTableRow(
            Component team,
            Component playerCell,
            Component job,
            Component diamond,
            Component emerald,
            Component income,
            Component towerCount,
            int jobWidth
    ) {
        return Component.empty()
                .append(centeredTableCell(team, PLAYER_STATUS_TEAM_WIDTH))
                .append(playerCell)
                .append(centeredTableCell(job, jobWidth))
                .append(centeredTableCell(diamond, PLAYER_STATUS_DIAMOND_WIDTH))
                .append(centeredTableCell(emerald, PLAYER_STATUS_EMERALD_WIDTH))
                .append(centeredTableCell(income, PLAYER_STATUS_INCOME_WIDTH))
                .append(centeredTableCell(towerCount, PLAYER_STATUS_TOWER_WIDTH));
    }

    static List<Integer> playerStatusColumnWidths() {
        return List.of(
                PLAYER_STATUS_TEAM_WIDTH,
                PLAYER_STATUS_AVATAR_WIDTH + PLAYER_STATUS_NAME_WIDTH,
                PLAYER_STATUS_JOB_WIDTH,
                PLAYER_STATUS_DIAMOND_WIDTH,
                PLAYER_STATUS_EMERALD_WIDTH,
                PLAYER_STATUS_INCOME_WIDTH,
                PLAYER_STATUS_TOWER_WIDTH
        );
    }

    private static Component centeredTableCell(Component value, int width) {
        return centeredTableCell(value, width, TextUncenterer.width(value));
    }

    static Component centeredTableCell(Component value, int width, int valueWidth) {
        int remainingWidth = Math.max(0, width - valueWidth);
        int leftPaddingWidth = remainingWidth / 2;
        return Component.empty()
                .append(TextUncenterer.filler(leftPaddingWidth))
                .append(value)
                .append(TextUncenterer.filler(remainingWidth - leftPaddingWidth));
    }

    private static Component avatarComponent(String playerName, AvatarVariant variant) {
        SmallAvatarKey key = new SmallAvatarKey(playerName, variant);
        Component cached = SMALL_AVATAR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        loadAvatar(playerName);
        SmallAvatarKey defaultKey = new SmallAvatarKey("Steve", variant);
        return SMALL_AVATAR_CACHE.computeIfAbsent(defaultKey, SemionDialogService::defaultSmallAvatar);
    }

    static Component playerStatusAvatar(String playerName) {
        return avatarComponent(playerName, AvatarVariant.COMPACT);
    }

    private static void loadAvatar(String playerName) {
        if (!AVATAR_LOAD_REQUESTS.add(playerName)) {
            return;
        }
        AVATAR_LOADER.execute(() -> {
            try {
                SkinLoader.load(playerName).ifPresent(skin -> {
                    for (AvatarVariant variant : AvatarVariant.values()) {
                        SmallAvatarKey key = new SmallAvatarKey(playerName, variant);
                        SMALL_AVATAR_CACHE.put(key, AvatarRenderer.asTextComponent(
                                avatarImage(skin, variant),
                                variant.yOffset()
                        ));
                    }
                });
            } catch (RuntimeException ignored) {
                // Keep the bundled Steve fallback.
            }
        });
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

    static Component jobStatisticsCategoryOverview(JobStatisticsSnapshot snapshot) {
        String period = snapshot.participantAppearances() > 0L
                ? formatTime(snapshot.firstMatchAtEpochMillis()) + " ~ " + formatTime(snapshot.lastMatchAtEpochMillis())
                : "-";
        return Component.literal("기록 기간 ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(period).withStyle(ChatFormatting.WHITE));
    }

    static Component jobStatisticsSummaryHeader() {
        List<Component> cells = jobStatisticsSummaryHeaderCells();
        return jobStatisticsSummaryTableRow(cells.get(0), cells.get(1), cells.get(2), cells.get(3));
    }

    static void addJobStatisticsSummary(
            List<DialogBody> bodies,
            JobStatisticsSnapshot snapshot,
            List<JobStatisticsRow> rows
    ) {
        Component header = Component.empty()
                .append(new HeaderMessage(
                        Component.literal("직업별 요약").withStyle(ChatFormatting.YELLOW),
                        JOB_STATISTICS_WIDTH
                ).asVanillaComponent())
                .append("\n")
                .append(jobStatisticsSummaryHeader())
                .append("\n")
                .append(jobStatisticsDivider().contents().copy());

        MutableComponent body = Component.empty();
        for (JobStatisticsRow row : rows) {
            if (!body.getString().isEmpty()) {
                body.append("\n");
            }
            body.append(jobStatisticsSummaryLine(snapshot, row));
        }

        bodies.add(new PlainMessage(header, JOB_STATISTICS_WIDTH));
        bodies.add(new PlainMessage(body, JOB_STATISTICS_WIDTH));
        bodies.add(jobStatisticsDivider());
    }

    static List<Component> jobStatisticsSummaryHeaderCells() {
        return List.of(
                Component.literal("직업").withStyle(ChatFormatting.AQUA),
                Component.literal("선택 ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("(선택률)").withStyle(ChatFormatting.DARK_GRAY)),
                Component.literal("경기 ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("(승률)").withStyle(ChatFormatting.GREEN)),
                Component.literal("순위 ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("(평균 라운드)").withStyle(ChatFormatting.DARK_GRAY))
        );
    }

    static Component jobStatisticsSummaryLine(JobStatisticsSnapshot snapshot, JobStatisticsRow row) {
        List<Component> cells = jobStatisticsSummaryCells(snapshot, row);
        return jobStatisticsSummaryTableRow(cells.get(0), cells.get(1), cells.get(2), cells.get(3));
    }

    static List<Component> jobStatisticsSummaryCells(JobStatisticsSnapshot snapshot, JobStatisticsRow row) {
        JobStatisticsEntry entry = row.entry();
        MutableComponent job = Component.literal(row.displayName()).withStyle(ChatFormatting.AQUA);
        if (!row.registered()) {
            job.append(Component.literal(" [미등록]").withStyle(ChatFormatting.DARK_GRAY));
        }
        Component selection = Component.literal(formatCount(entry.appearances()) + "회")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" (" + formatPercent(snapshot.selectionRate(entry)) + ")")
                        .withStyle(ChatFormatting.DARK_GRAY));
        Component game = Component.literal(formatCount(entry.wins()) + "승 "
                        + formatCount(entry.appearances() - entry.wins()) + "패")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" (" + formatPercent(entry.winRate()) + ")")
                        .withStyle(ChatFormatting.GREEN));
        Component placement = Component.literal(formatAverage(entry.averagePlacement(), "위"))
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" (" + formatRound(entry.averageFinalRound()) + ")")
                        .withStyle(ChatFormatting.DARK_GRAY));
        return List.of(job, selection, game, placement);
    }

    static List<Integer> jobStatisticsSummaryColumnWidths() {
        return List.of(
                JOB_STATISTICS_JOB_WIDTH,
                JOB_STATISTICS_SELECTION_WIDTH,
                JOB_STATISTICS_GAME_WIDTH,
                JOB_STATISTICS_PLACEMENT_WIDTH
        );
    }

    private static Component jobStatisticsSummaryTableRow(
            Component job,
            Component selection,
            Component game,
            Component placement
    ) {
        return Component.empty()
                .append(centeredTableCell(job, JOB_STATISTICS_JOB_WIDTH))
                .append(centeredTableCell(selection, JOB_STATISTICS_SELECTION_WIDTH))
                .append(centeredTableCell(game, JOB_STATISTICS_GAME_WIDTH))
                .append(centeredTableCell(placement, JOB_STATISTICS_PLACEMENT_WIDTH));
    }

    static List<Component> jobStatisticsSampleHeaderCells() {
        return List.of(
                statisticsHeaderCell("선택", "%", ChatFormatting.DARK_GRAY),
                statisticsHeaderCell("승리", "%", ChatFormatting.GREEN),
                statisticsHeaderCell("평균 순위", "R", ChatFormatting.DARK_GRAY)
        );
    }

    static List<Component> jobStatisticsSampleCells(
            JobStatisticsSnapshot snapshot,
            JobStatisticsEntry entry
    ) {
        return List.of(
                statisticsValueCell(formatCount(entry.appearances()) + "회",
                        formatPercent(snapshot.selectionRate(entry)), ChatFormatting.DARK_GRAY),
                statisticsValueCell(formatCount(entry.wins()) + "승",
                        formatPercent(entry.winRate()), ChatFormatting.GREEN),
                statisticsValueCell(formatAverage(entry.averagePlacement(), "위"),
                        formatRound(entry.averageFinalRound()), ChatFormatting.DARK_GRAY)
        );
    }

    static List<List<Component>> jobStatisticsRoundRows(JobStatisticsEntry entry) {
        ArrayList<List<Component>> rows = new ArrayList<>(10);
        for (int offset = 0; offset < 10; offset++) {
            ArrayList<Component> row = new ArrayList<>(4);
            for (int firstRound = 1; firstRound <= JobStatisticsEntry.MAX_TRACKED_ROUND; firstRound += 10) {
                row.add(jobStatisticsRoundCell(entry, firstRound + offset));
            }
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static Component jobStatisticsRoundCell(JobStatisticsEntry entry, int round) {
        return Component.literal("R" + round + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatPercent(entry.roundPassRate(round)))
                        .withStyle(ChatFormatting.AQUA));
    }

    static List<Integer> jobStatisticsRoundColumnWidths() {
        return statisticsEqualColumnWidths(4, true);
    }

    static Component jobStatisticsRoundBody(JobStatisticsEntry entry) {
        return centeredStatisticsRows(
                jobStatisticsRoundColumnWidths(),
                jobStatisticsRoundRows(entry),
                true
        );
    }

    static Component jobStatisticsCombatBody(JobStatisticsEntry entry) {
        JobStatisticsTotals totals = entry.totals();
        return centeredStatisticsRows(
                statisticsEqualColumnWidths(2),
                List.of(List.of(
                        statisticsNumber(entry.averageValue(totals.monsterKills())),
                        statisticsNumber(entry.averageValue(totals.killMinerals()))
                )),
                false
        );
    }

    static List<Component> jobStatisticsCombatHeaderCells() {
        return statisticsHeaderCells("평균 처치", "평균 획득 다이아");
    }

    static Component jobStatisticsIncomeBody(JobStatisticsEntry entry) {
        JobStatisticsTotals totals = entry.totals();
        return centeredStatisticsRows(
                statisticsEqualColumnWidths(5),
                List.of(List.of(
                        statisticsNumber(entry.averageValue(totals.summonedMonsters())),
                        statisticsValueCell(formatAverage(entry.averageValue(totals.finalIncome()), ""),
                                formatAverage(entry.averageValue(totals.incomeGenerated()), ""),
                                ChatFormatting.DARK_GRAY),
                        statisticsNumber(entry.averageValue(totals.sentIncomeThreat())),
                        statisticsNumber(entry.averageValue(totals.incomingIncomeThreat())),
                        statisticsValueCell(formatAverage(
                                entry.averageValue(totals.incomeAttackSuccessThreat()), ""),
                                formatPercent(entry.incomeAttackSuccessRate()), ChatFormatting.GREEN)
                )),
                false
        );
    }

    static List<Component> jobStatisticsIncomeHeaderCells() {
        return statisticsHeaderCells(
                "평균 소환", "최종(생산)", "보낸 위협", "받은 위협", "성공 위협(%)"
        );
    }

    static Component jobStatisticsDefenseBody(JobStatisticsEntry entry) {
        JobStatisticsTotals totals = entry.totals();
        return centeredStatisticsRows(
                statisticsEqualColumnWidths(6),
                List.of(List.of(
                        statisticsNumber(entry.averageValue(totals.ownLaneIncomingThreat())),
                        statisticsNumber(entry.averageValue(totals.ownLaneLeakedThreat())),
                        Component.literal(formatPercent(entry.defenseSuccessRate())).withStyle(ChatFormatting.GREEN),
                        statisticsNumber(entry.averageValue(totals.ownLaneDiamondGain())),
                        statisticsNumber(entry.averageValue(totals.assistClearDiamondGain())),
                        statisticsNumber(entry.averageValue(totals.assistClearThreat()))
                )),
                false
        );
    }

    static List<Component> jobStatisticsDefenseHeaderCells() {
        return statisticsHeaderCells(
                "라인 위협", "누수 위협", "방어율", "라인 다이아", "지원 다이아", "정리 위협"
        );
    }

    private static List<Component> statisticsHeaderCells(String... labels) {
        return java.util.Arrays.stream(labels)
                .map(label -> (Component) Component.literal(label).withStyle(ChatFormatting.GRAY))
                .toList();
    }

    private static Component statisticsHeaderCell(
            String label,
            String parenthetical,
            ChatFormatting parentheticalColor
    ) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
                .append(Component.literal("(" + parenthetical + ")").withStyle(parentheticalColor));
    }

    private static Component statisticsValueCell(
            String value,
            String parenthetical,
            ChatFormatting parentheticalColor
    ) {
        return Component.literal(value).withStyle(ChatFormatting.WHITE)
                .append(Component.literal("(" + parenthetical + ")").withStyle(parentheticalColor));
    }

    private static Component statisticsNumber(OptionalDouble value) {
        return Component.literal(formatAverage(value, "")).withStyle(ChatFormatting.WHITE);
    }

    static List<Integer> statisticsEqualColumnWidths(int columnCount) {
        return statisticsEqualColumnWidths(columnCount, false);
    }

    private static List<Integer> statisticsEqualColumnWidths(int columnCount, boolean separated) {
        int availableWidth = JOB_STATISTICS_DETAIL_TABLE_WIDTH
                - (separated ? JOB_STATISTICS_SEPARATOR_WIDTH * Math.max(0, columnCount - 1) : 0);
        int baseWidth = availableWidth / columnCount;
        int remainder = availableWidth % columnCount;
        ArrayList<Integer> widths = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            widths.add(baseWidth + (index < remainder ? 1 : 0));
        }
        return List.copyOf(widths);
    }

    private static Component centeredStatisticsRows(
            List<Integer> widths,
            List<List<Component>> rows,
            boolean separated
    ) {
        MutableComponent table = Component.empty();
        for (List<Component> row : rows) {
            if (!table.getString().isEmpty()) {
                table.append("\n");
            }
            table.append(centeredStatisticsRow(widths, row, separated));
        }
        return table;
    }

    private static Component centeredStatisticsRow(
            List<Integer> widths,
            List<Component> cells,
            boolean separated
    ) {
        if (widths.size() != cells.size()) {
            throw new IllegalArgumentException("Statistics table width and cell counts must match.");
        }
        MutableComponent row = Component.empty();
        for (int index = 0; index < cells.size(); index++) {
            if (separated && index > 0) {
                row.append(statisticsTableSeparator());
            }
            row.append(centeredTableCell(cells.get(index), widths.get(index)));
        }
        return row;
    }

    private static Component statisticsTableSeparator() {
        return Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY);
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

    private static void addStatisticsTable(List<DialogBody> bodies, Component table) {
        bodies.add(new PlainMessage(table, JOB_STATISTICS_DETAIL_WIDTH));
    }

    private static void addStatisticsSectionWithHeaderAndBody(
            List<DialogBody> bodies,
            String title,
            Component header,
            Component body
    ) {
        addStatisticsTable(bodies, Component.empty()
                .append(statisticsSectionHeader(title))
                .append("\n")
                .append(header)
                .append("\n")
                .append(HeaderMessage.dividerComponent(JOB_STATISTICS_DETAIL_TABLE_WIDTH)));
        addStatisticsTable(bodies, body);
    }

    private static void addStatisticsSectionWithBody(
            List<DialogBody> bodies,
            String title,
            Component body
    ) {
        addStatisticsTable(bodies, statisticsSectionHeader(title));
        addStatisticsTable(bodies, body);
    }

    private static Component statisticsSectionHeader(String title) {
        return new HeaderMessage(
                Component.literal(title).withStyle(ChatFormatting.YELLOW),
                JOB_STATISTICS_DETAIL_TABLE_WIDTH + 23
        ).asVanillaComponent();
    }

    private static void addCenteredStatisticsLine(List<DialogBody> bodies, Component line) {
        bodies.add(new PlainMessage(
                centeredTableCell(line, JOB_STATISTICS_DETAIL_CONTENT_WIDTH),
                JOB_STATISTICS_DETAIL_WIDTH
        ));
    }

    private static void addTraitCombinationStatistics(
            List<DialogBody> bodies,
            List<TraitCombinationStatisticsEntry> combinations,
            long jobAppearances
    ) {
        addStatisticsSectionWithHeaderAndBody(
                bodies,
                "특성 조합",
                centeredStatisticsRow(
                        statisticsEqualColumnWidths(4),
                        jobStatisticsTraitHeaderCells(),
                        false
                ),
                jobStatisticsTraitBody(combinations, jobAppearances)
        );
    }

    static Component jobStatisticsTraitBody(
            List<TraitCombinationStatisticsEntry> combinations,
            long jobAppearances
    ) {
        List<Integer> widths = statisticsEqualColumnWidths(4);
        if (combinations.isEmpty()) {
            return centeredStatisticsRows(widths, List.of(List.of(
                    statisticsNumber(OptionalDouble.empty()),
                    statisticsNumber(OptionalDouble.empty()),
                    statisticsNumber(OptionalDouble.empty()),
                    statisticsNumber(OptionalDouble.empty())
            )), false);
        }
        int visibleCount = Math.min(8, combinations.size());
        ArrayList<List<Component>> rows = new ArrayList<>(visibleCount);
        for (int index = 0; index < visibleCount; index++) {
            TraitCombinationStatisticsEntry combination = combinations.get(index);
            rows.add(List.of(
                    fitStatisticsLabel(jobStatisticsTraitLabel(combination), widths.getFirst()),
                    statisticsValueCell(formatCount(combination.appearances()) + "회",
                            formatPercent(combination.selectionRate(jobAppearances)), ChatFormatting.DARK_GRAY),
                    statisticsValueCell(formatCount(combination.wins()) + "승",
                            formatPercent(combination.winRate()), ChatFormatting.GREEN),
                    statisticsValueCell(formatAverage(combination.averagePlacement(), "위"),
                            formatRound(combination.averageFinalRound()), ChatFormatting.DARK_GRAY)
            ));
        }
        return centeredStatisticsRows(widths, rows, false);
    }

    static String jobStatisticsTraitLabel(TraitCombinationStatisticsEntry combination) {
        return traitName(combination.primaryTraitId()) + "·" + traitName(combination.secondaryTraitId());
    }

    private static Component fitStatisticsLabel(String label, int width) {
        Component fullLabel = Component.literal(label).withStyle(ChatFormatting.WHITE);
        if (TextUncenterer.width(fullLabel) <= width) {
            return fullLabel;
        }
        String fitted = label;
        while (!fitted.isEmpty()) {
            int lastCodePoint = fitted.offsetByCodePoints(0, fitted.codePointCount(0, fitted.length()) - 1);
            fitted = fitted.substring(0, lastCodePoint);
            Component abbreviated = Component.literal(fitted + "…").withStyle(ChatFormatting.WHITE);
            if (TextUncenterer.width(abbreviated) <= width) {
                return abbreviated;
            }
        }
        return Component.literal("…").withStyle(ChatFormatting.WHITE);
    }

    static List<Component> jobStatisticsTraitHeaderCells() {
        return List.of(
                Component.literal("특성").withStyle(ChatFormatting.GRAY),
                statisticsHeaderCell("선택", "%", ChatFormatting.DARK_GRAY),
                statisticsHeaderCell("승리", "%", ChatFormatting.GREEN),
                statisticsHeaderCell("평균 순위", "R", ChatFormatting.DARK_GRAY)
        );
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
            case AQUA -> "aqua";
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
            case AQUA -> ChatFormatting.AQUA;
        };
    }

    static String towerControlSummary(
            TeamId teamId,
            int laneId,
            PlayerEconomy economy,
            long nextGasUpgradeCost,
            int towerCount,
            int towerLimit,
            long nextTowerLimitDiamondCost,
            long nextTowerLimitEmeraldCost
    ) {
        StringBuilder body = new StringBuilder();
        body.append("<white>팀</white> ").append(teamMarkup(teamId))
                .append(" <dark_gray>|</dark_gray> <white>라인</white> <yellow>#")
                .append(laneId).append("</yellow>\n");
        body.append(SemionHudTextService.diamondMarkup(economy.diamond()));
        body.append(" <dark_gray>|</dark_gray> ")
                .append(SemionHudTextService.emeraldMarkup(economy.emerald()))
                .append("\n");
        body.append("<gold>인컴 업그레이드 비용</gold> ");
        if (nextGasUpgradeCost >= 0) {
            body.append("<aqua>").append(nextGasUpgradeCost).append("</aqua>");
        } else {
            body.append("<white>최대</white>");
        }
        body.append(" <dark_gray>|</dark_gray> ")
                .append(SemionHudTextService.emeraldRateMarkup(economy.emeraldPerSec()))
                .append("\n");
        body.append("<gold>타워 수</gold> <yellow>").append(towerCount).append("/").append(towerLimit).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gold>타워 확장 비용</gold> ")
                .append(formatTowerLimitPurchaseCost(nextTowerLimitDiamondCost, nextTowerLimitEmeraldCost))
                .append("\n\n");
        return body.toString();
    }

    static String towerConstructionCandidateSummary(String group, int entryCount) {
        StringBuilder body = new StringBuilder("<white> </white>\n");
        if (group != null) {
            body.append("<yellow>").append(group).append("</yellow> <white>계열</white> <dark_gray>|</dark_gray> ");
        }
        body.append("<white>건설 후보</white> <yellow>").append(entryCount).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n");
        body.append("<white> </white>\n");
        return body.toString();
    }

    static String towerControlSelectedTowerSummary(
            String displayName,
            double health,
            double maxHealth,
            long sellRefundAmount
    ) {
        return "<white> </white>\n"
                + "<white><bold>" + displayName + "</bold></white>\n"
                + formatHealth(health, maxHealth, "") + "\n"
                + formatSellPrice(sellRefundAmount, "") + "\n"
                + "<white> </white>\n";
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
            return "<white>최대</white>";
        }
        return "<aqua>◆ " + diamondCost + "</aqua> <white>+</white> <green>⬢ "
                + emeraldCost + "</green>";
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
            String jobName,
            boolean teamLeader
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
