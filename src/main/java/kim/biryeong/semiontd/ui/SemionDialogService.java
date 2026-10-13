package kim.biryeong.semiontd.ui;

import static kim.biryeong.semiontd.ui.UiJobStatisticsView.*;
import static kim.biryeong.semiontd.ui.UiDialogPresentation.*;
import static kim.biryeong.semiontd.ui.UiDialogBodyRenderer.*;
import static kim.biryeong.semiontd.ui.UiDialogTableLayout.*;

import kim.biryeong.semiontd.tower.magicschool.HogwartsTower;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolSpell;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolWizardTower;
import kim.biryeong.semiontd.tower.gamble.PokerTableTower;
import kim.biryeong.semiontd.tower.gamble.GamblerTower;
import kim.biryeong.semiontd.tower.gamble.GambleBalance;
import kim.biryeong.semiontd.tower.gamble.GambleTowers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.buildguide.BuildGuideService;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.demonlord.DemonLordBinding;
import kim.biryeong.semiontd.tower.demonlord.DemonLordIncome;
import kim.biryeong.semiontd.tower.income.IncomeTower;
import kim.biryeong.semiontd.tower.income.IncomeTowerBalance;
import kim.biryeong.semiontd.tower.income.IncomeTowerService;
import kim.biryeong.semiontd.tower.demonlord.DemonLordLoadout;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.input.NumberRangeInput;
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
import kim.biryeong.semiontd.tower.developer.DeveloperTower;
import kim.biryeong.semiontd.tower.developer.DeveloperTowers;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowerState;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyLeaderTower;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyPolicy;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;
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
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.trait.SemionTrait;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitRegistry;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import net.minecraft.ChatFormatting;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsState;
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
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.ui.dialog.body.HeaderMessage.dividerComponent;

public final class SemionDialogService {
    public static List<JobStatisticsRow> jobStatisticsRows(JobStatisticsSnapshot snapshot) {
        return UiJobStatisticsView.jobStatisticsRows(snapshot);
    }

    private static final int BODY_WIDTH = 256;
    private static final int AUGMENT_BODY_WIDTH = 360;
    private static final int TITLE_HEADER_WIDTH = 200;
    private static final int PLAYER_STATUS_WIDTH = 480;

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

        List<MatchParticipantResult> losers = orderedParticipants.stream()
                .filter(participant -> !participant.winner())
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
            net.minecraft.resources.Identifier traitId,
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
            net.minecraft.resources.Identifier traitId
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
        boolean demonLord = job instanceof DemonLordTowerJob;
        if (demonLord && selectedTower == null && group == null) {
            showDemonLordMenu(player, body, entries.size());
            return;
        }
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
        } else if (entries.isEmpty() && job instanceof kim.biryeong.semiontd.job.BlueprintTowerJob) {
            body.append("<gray>아직 설계도가 없습니다. [설계하기]로 타워를 설계하세요.</gray>\n");
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
            if (job instanceof kim.biryeong.semiontd.job.BlueprintTowerJob) {
                // 빌더 빌더: 설계도 버튼 위에 설계 창과 목록 창을 여는 버튼을 둡니다.
                actions.add(actionButton(
                        Component.literal("설계하기"),
                        "/semiontd blueprint design",
                        Component.literal("능력치·모듈·겉모습을 골라 새 타워를 설계합니다. 저장하면 계정에 남습니다."),
                        COMPACT_BUTTON_WIDTH));
                actions.add(actionButton(
                        Component.literal("내 설계도"),
                        "/semiontd blueprint",
                        Component.literal("계정에 저장한 설계도를 보고 지웁니다."),
                        COMPACT_BUTTON_WIDTH));
            }
            for (ProductionTowerCatalog.CatalogEntry entry : entries) {
                long mineralCost = ProductionTowerService.placementCost(
                        game.playerLane(player.getUUID()).orElse(null), entry.type());
                String augmentPreview = AugmentService.placementPreview(game, player, entry.type());
                if (!augmentPreview.isEmpty()) {
                    body.append("<gray>").append(augmentPreview).append("</gray>\n");
                }
                boolean recommended = buildGuideService != null && TowerPlacementPositions.resolveGrid(game.playerLane(player.getUUID()).orElse(null), player.blockPosition())
                        .map(position -> buildGuideService.isRecommendedTower(game, player.getUUID(), game.currentRound(), position, entry.type().id()))
                        .orElse(false);
                String command = HeroPartyTowers.role(entry.type())
                        .filter(role -> !HeroPartyStates.state(player.getUUID()).isCommitted(role))
                        .map(role -> "/semiontd hero companion " + role.id())
                        .orElse("/semiontd tower build " + entry.type().id());
                actions.add(towerButton(entry, mineralCost, economy.diamond() >= mineralCost, recommended, command));
            }
            if (group != null && (groups.size() >= 2 || demonLord)) {
                actions.add(actionButton(
                        Component.literal("← 분류 선택"),
                        "/semiontd tower ui",
                        Component.literal("계열 선택 화면으로 돌아갑니다."),
                        COMPACT_BUTTON_WIDTH
                ));
            }
        } else {
            for (TowerUpgradeOption option : upgrades) {
                if (selectedTower instanceof PokerTableTower poker) {
                    actions.add(actionButton("포커 베팅 · 200~1000 다이아", PokerTableDialog.command(poker),
                            "슬라이더로 금액을 정해 카드 3장을 뽑습니다. 한 번만 베팅할 수 있습니다."));
                    continue;
                }
                boolean mineralAffordable = economy.diamond() >= ProductionTowerService.upgradeCost(selectedTower, option);
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
                AugmentService.upgradeAction(game, game.players().get(player.getUUID()), selectedTower, option)
                        .ifPresent(button -> actions.add(actionButton(button.label(), button.command(), button.description())));
            }
        }
        showActions(player, "세미온 TD 타워", body.toString(), actions, 3);
    }

    /** 마왕 타워 관리 창에서 증강 추가 타워를 모아 두는 묶음 이름. */
    public static final String DEMON_LORD_EXTRA_GROUP = "기타 타워";

    /**
     * 마왕의 타워 관리 첫 화면.
     *
     * <p>마왕은 레인에 스킬을 짓지 않으므로, 타워 목록 대신 [스킬 배정]·[스탯 배정] 버튼을 둡니다.
     * 증강으로 설치할 수 있는 타워가 생기면 [기타 타워] 버튼이 함께 나타납니다.
     */
    private void showDemonLordMenu(ServerPlayer player, StringBuilder body, int extraTowerCount) {
        DemonLordState state = DemonLordStates.get(player.getUUID());
        body.append(demonLordLoadoutSummary(state));
        ArrayList<ActionButton> actions = new ArrayList<>();
        actions.add(actionButton(
                Component.literal("스킬 배정"),
                "/semiontd demonlord skills",
                Component.literal("1~4, 마검 우클릭, F, Q 슬롯에 스킬을 사고, 올리고, 뺍니다."),
                COMPACT_BUTTON_WIDTH));
        int unspent = state == null ? 0 : state.unspentPoints();
        actions.add(actionButton(
                Component.literal(unspent > 0 ? "스탯 배정 (" + unspent + ")" : "스탯 배정"),
                "/semiontd demonlord stats",
                Component.literal("레벨업으로 받은 스탯 포인트를 분배합니다."),
                COMPACT_BUTTON_WIDTH));
        if (extraTowerCount > 0) {
            actions.add(actionButton(
                    Component.literal(DEMON_LORD_EXTRA_GROUP + " (" + extraTowerCount + ")"),
                    "/semiontd tower ui " + DEMON_LORD_EXTRA_GROUP,
                    Component.literal("증강으로 설치할 수 있게 된 타워 " + extraTowerCount + "종을 봅니다."),
                    COMPACT_BUTTON_WIDTH));
        }
        actions.add(actionButton(
                Component.literal("인컴 타워"),
                "/semiontd income",
                Component.literal("인컴 유닛을 레인에 세웁니다. 준비 시간이 끝날 때마다 적 레인을 공격합니다."),
                COMPACT_BUTTON_WIDTH));
        showActions(player, "세미온 TD 타워", body.toString(), actions, 2);
    }

    static String demonLordLoadoutSummary(DemonLordState state) {
        StringBuilder summary = new StringBuilder("<light_purple><bold>스킬 슬롯</bold></light_purple>\n");
        for (DemonLordBinding binding : DemonLordBinding.values()) {
            summary.append("<gray>[").append(binding.label()).append("]</gray> ");
            Optional<DemonLordLoadout.Slot> slot = state == null ? Optional.empty() : state.loadout().slot(binding);
            if (slot.isEmpty()) {
                summary.append("<dark_gray>비어 있음</dark_gray>\n");
            } else {
                summary.append("<white>").append(slot.get().skill().displayName()).append("</white> <yellow>")
                        .append(slot.get().tier()).append("티어</yellow>\n");
            }
        }
        return summary.toString();
    }

    /**
     * 마왕의 인컴 설정 창. 막대로 비율을 고르고 [저장]을 누르면 적용됩니다.
     *
     * <p>일반 빌더의 소환 상점 자리를 대신합니다. 마왕은 인컴 유닛을 직접 고르지 않습니다.
     */
    public void showDemonLordIncome(ServerPlayer player, SemionGame game) {
        DemonLordState state = DemonLordStates.get(player.getUUID());
        if (state == null) {
            show(player, "마왕 인컴", "<red>마왕 상태가 없습니다.</red>");
            return;
        }
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        long emerald = semionPlayer == null ? 0L : semionPlayer.economy().emerald();
        long cap = game.economyConfig().emeraldCapForRound(game.currentRound());
        int percent = (int) Math.round(state.autoIncomeThreshold() * 100.0);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>인컴 자동 전송</bold></gradient>\n");
        body.append("<gray>에메랄드가 한도의 설정 비율 이상 차면, 살 수 있는 가장 비싼 인컴 유닛을 자동으로 보냅니다.</gray>\n");
        body.append("<green>현재 에메랄드</green> <white>").append(emerald).append(" / ").append(cap).append("</white>\n");
        body.append("<aqua>상태</aqua> <white>")
                .append(state.autoIncomeEnabled() ? "켜짐 · " + percent + "% 이상" : "꺼짐")
                .append("</white>\n");

        NumberRangeInput slider = new NumberRangeInput(
                200,
                Component.literal("전송 기준"),
                "options.percent_value",
                new NumberRangeInput.RangeInfo(0.0f, 100.0f, Optional.of((float) percent), Optional.of(5.0f)));
        List<Input> inputs = List.of(new Input("threshold", slider));
        List<ActionButton> actions = new ArrayList<>();
        actions.add(new ActionButton(
                new CommonButtonData(Component.literal("저장"),
                        Optional.of(Component.literal("막대로 고른 비율을 적용합니다.")), COMPACT_BUTTON_WIDTH),
                Optional.of(new CommandTemplate(demonLordIncomeTemplate()))));
        actions.add(actionButton(
                Component.literal(state.autoIncomeEnabled() ? "자동 전송 끄기" : "자동 전송 켜기"),
                "/semiontd demonlord income " + (state.autoIncomeEnabled() ? "off" : "on"),
                Component.literal("자동 전송을 " + (state.autoIncomeEnabled() ? "멈춥니다." : "다시 켭니다.")),
                COMPACT_BUTTON_WIDTH));
        Dialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        Component.literal("마왕 인컴"),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        actionDialogBodies(body.toString(), BODY_WIDTH),
                        inputs
                ),
                actions,
                Optional.of(actionButton("닫기", "", "창을 닫습니다.")),
                2
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private static ParsedTemplate demonLordIncomeTemplate() {
        return ParsedTemplate.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                        new com.google.gson.JsonPrimitive("/semiontd demonlord income set $(threshold)"))
                .getOrThrow();
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
        if (tower instanceof IncomeTower incomeTower) {
            showIncomeTowerDetails(player, game, incomeTower);
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
        body.append(formatTowerDamageStats(tower, combatStatsEntity.orElse(null), currentDamage)).append('\n');
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
                if (tower instanceof PokerTableTower poker) {
                    actions.add(actionButton("포커 베팅 · 200~1000 다이아", PokerTableDialog.command(poker),
                            "슬라이더로 금액을 정해 카드 3장을 뽑습니다. 한 번만 베팅할 수 있습니다."));
                    continue;
                }
                boolean mineralAffordable = semionPlayer.economy().diamond() >= ProductionTowerService.upgradeCost(tower, option);
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
                AugmentService.upgradeAction(game, semionPlayer, tower, option)
                        .ifPresent(button -> actions.add(actionButton(button.label(), button.command(), button.description())));
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
            if (tower instanceof kim.biryeong.semiontd.tower.plant.GardenerTower) {
                actions.add(actionButton("스킬 강화", "/semiontd gardener", "꽃밭 치유·지배·생기 흡수를 강화합니다."));
            }
            if (tower instanceof HeroTower) {
                actions.add(actionButton("용사 상점", "/semiontd hero shop", "장비를 구매·강화·교체합니다."));
                actions.add(actionButton("현재 퀘스트", "/semiontd hero quest", "현재 웨이브 퀘스트를 확인합니다."));
                actions.add(actionButton("파티 현황", "/semiontd hero party", "확정된 동료와 성장치를 확인합니다."));
            }
            if (tower instanceof DeveloperTower && DeveloperTowers.isGrowthTower(tower.type())) {
                actions.add(actionButton(
                        "패치 콘솔",
                        "/semiontd developer console "
                                + managementPosition.x() + " "
                                + managementPosition.y() + " "
                                + managementPosition.z(),
                        Component.literal("정식 패치·핫픽스·긴급 점검·최적화·재현·버전 고정을 이 타워에 적용합니다."),
                        BUTTON_WIDTH
                ));
            }
            if (tower instanceof HogwartsTower) {
                actions.add(actionButton(
                        "커리큘럼",
                        "/semiontd magic_school curriculum "
                                + managementPosition.x() + " "
                                + managementPosition.y() + " "
                                + managementPosition.z(),
                        "마법학교의 커리큘럼을 확인합니다."
                ));
                actions.add(actionButton("마법사 스킨", "/semiontd magic_school skin "
                        + managementPosition.x() + " " + managementPosition.y() + " " + managementPosition.z(),
                        "신입생과 기숙사별 마법사의 스킨을 설정합니다. T3는 T2의 스킨을 공유합니다."));
            }
            if (tower instanceof MagicSchoolWizardTower) {
                actions.add(actionButton(
                        "주문",
                        "/semiontd magic_school spells "
                                + managementPosition.x() + " "
                                + managementPosition.y() + " "
                                + managementPosition.z(),
                        "이 마법사가 사용할 주문을 확인하고 지정합니다."
                ));
            }
            if (!tower.isTemporaryCopy() && tower.canBeSold()) {
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
        showActions(player, "세미온 TD 타워 상세", actionDialogBodies(body.toString(), BODY_WIDTH), actions, actionColumns);
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
                + ProductionTowerService.placementCost(game.playerLane(player.getUUID()).orElse(null), type)
                + " 다이아</aqua>\n\n"
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
        // 마왕은 인컴 몹을 사지 않고 인컴 타워를 세웁니다. 소환 상점 대신 인컴 타워 창을 엽니다.
        if (!game.summonsAreFree() && DemonLordIncome.isDemonLord(semionPlayer)) {
            showIncomeTowerShop(player, game);
            return;
        }
        long emerald = semionPlayer == null ? 0 : semionPlayer.economy().emerald();
        boolean freeSummons = game.summonsAreFree();
        // 침공군 유닛은 마왕의 인컴 타워 전용입니다. 무료 소환(샌드박스)에서만 시험 삼아 부를 수 있습니다.
        List<SummonMonsterType> summons = sortedSummons(game.summonShop().all().stream()
                .filter(type -> freeSummons || !IncomeTowerBalance.isUnit(type.id()))
                .toList());
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
                    var quote = game.augmentsEnabled() && !freeSummons && semionPlayer != null
                            ? AugmentEconomyService.previewPurchase(game, semionPlayer, type).orElse(null) : null;
                    boolean affordable = freeSummons || emerald >= (quote == null ? type.gasCost() : quote.emeraldCost());
                    Component label = summonButtonLabel(type, affordable);
                    if (quote != null) {
                        label = label.copy().append(Component.literal(" · " + quote.emeraldCost() + "◆"));
                    }
                    return actionButton(
                            label,
                            "/semiontd summon " + type.id(),
                            summonTooltip(type, affordable, freeSummons, quote),
                            SUMMON_BUTTON_WIDTH
                    );
                })
                .collect(Collectors.toCollection(ArrayList::new));
        if (game.augmentsEnabled() && game.phase() == kim.biryeong.semiontd.game.RoundPhase.PREPARE_AND_SUMMON
                && AugmentEconomyService.hasPurchaseOptions(semionPlayer)) {
            actions.add(actionButton("인컴 계약", "/semiontd augment ui contracts", "다음 적격 구매에 적용할 증강 계약을 설정합니다."));
        }
        showActions(player, "세미온 TD 소환", body.toString(), actions, SUMMON_COLUMNS);
    }

    /** 인컴 타워 설치 창. 에코 샤드(인컴 타워)로 엽니다. 서 있는 자리에 설치합니다. */
    public void showIncomeTowerShop(ServerPlayer player, SemionGame game) {
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer == null) {
            show(player, "인컴 타워", "<red>현재 게임 참가자가 아닙니다.</red>");
            return;
        }
        long emerald = semionPlayer.economy().emerald();
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>인컴 타워</bold></gradient>\n");
        body.append("<gray>서 있는 자리에 유닛을 세웁니다. 준비 시간이 끝날 때마다 그 유닛이 적 레인으로 한 마리씩 공격을 갑니다(소모되지 않음).</gray>\n");
        body.append("<gray>타워 수를 차지하고, 설치·레벨업은 에메랄드를 씁니다. 가진 동안 레벨만큼 라운드 인컴이 오릅니다.</gray>\n");
        body.append("<divider>\n");
        body.append("<white>에메랄드</white> <green>").append(emerald).append("</green> <dark_gray>|</dark_gray> ")
                .append("<white>타워</white> <yellow>").append(game.towerCapacityUsed(player.getUUID())).append('/')
                .append(game.towerLimitForPlayer(player.getUUID())).append("</yellow>\n");
        if (game.phase() != kim.biryeong.semiontd.game.RoundPhase.PREPARE_AND_SUMMON) {
            body.append("<red>설치는 준비 단계에서만 할 수 있습니다.</red>\n");
        }
        ArrayList<ActionButton> actions = new ArrayList<>();
        for (SummonMonsterType unit : IncomeTowerService.units(game)) {
            long cost = IncomeTowerService.buildCost(game, unit);
            boolean affordable = emerald >= cost;
            Component label = Component.literal(unit.displayName())
                    .withStyle(affordable ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)
                    .append(Component.literal(" " + cost + "◆").withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED));
            Component tooltip = Component.literal(unit.displayName() + " · " + (unit.tier().ordinal() + 1) + "단계"
                    + "\n설치 " + cost + " 에메랄드 · 레벨당 인컴 +" + unit.incomeGain()
                    + " · 타워 수 " + kim.biryeong.semiontd.tower.TowerCapacity.slotCost(IncomeTowerService.towerType(unit))
                    + "\n체력 " + Math.round(unit.maxHealth() * IncomeTowerBalance.statMultiplier(1))
                    + " · 공격력 " + Math.round(unit.attackDamage() * IncomeTowerBalance.statMultiplier(1))
                    + (IncomeTowerService.description(unit).isEmpty() ? "" : "\n" + IncomeTowerService.description(unit).getFirst()));
            actions.add(actionButton(label, "/semiontd income build " + unit.id(), tooltip, COMPACT_BUTTON_WIDTH));
        }
        showActions(player, "세미온 TD 인컴 타워", body.toString(), actions, 2);
    }

    public void showIncomeTowerDetails(ServerPlayer player, SemionGame game, IncomeTower tower) {
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        boolean owned = semionPlayer != null && tower.ownerPlayer().equals(player.getUUID());
        SummonMonsterType unit = IncomeTowerService.unit(game, tower.summonId()).orElse(null);
        double healthScale = IncomeTowerBalance.healthMultiplier(tower.level(), game.currentRound());
        double attackScale = IncomeTowerBalance.attackDamageMultiplier(tower.level(), game.currentRound());
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>인컴 타워</bold></gradient>\n");
        body.append("<white><bold>").append(tower.type().displayName()).append("</bold></white> <yellow>레벨 ")
                .append(tower.level()).append('/').append(IncomeTowerBalance.MAX_LEVEL).append("</yellow>\n");
        body.append("<divider>\n");
        if (unit != null) {
            body.append("<white>보내는 유닛</white> 체력 <red>").append(Math.round(unit.maxHealth() * healthScale))
                    .append("</red> · 공격력 <gold>").append(Math.round(unit.attackDamage() * attackScale))
                    .append("</gold> <dark_gray>(이번 라운드 기준)</dark_gray>\n");
            for (String line : IncomeTowerService.description(unit)) {
                body.append("<gray>").append(line).append("</gray>\n");
            }
        }
        body.append("<white>라운드 인컴</white> <aqua>+").append(IncomeTowerService.incomeOf(game, tower)).append("</aqua>\n");
        body.append("<white>판매 시 환불</white> <green>").append(IncomeTowerBalance.sellRefund(tower.paidEmerald()))
                .append(" 에메랄드</green> <dark_gray>(올린 인컴은 사라집니다)</dark_gray>\n");
        if (!owned) {
            body.append("\n<red>자신이 설치한 인컴 타워만 관리할 수 있습니다.</red>\n");
            show(player, "세미온 TD 인컴 타워", body.toString());
            return;
        }
        var position = tower.managementPosition();
        String coordinates = position.x() + " " + position.y() + " " + position.z();
        ArrayList<ActionButton> actions = new ArrayList<>();
        if (tower.maxLevel()) {
            actions.add(actionButton(Component.literal("최고 레벨").withStyle(ChatFormatting.GOLD), "",
                    Component.literal("더 올릴 수 없습니다."), BUTTON_WIDTH));
        } else {
            long cost = IncomeTowerService.upgradeCost(game, tower);
            boolean affordable = semionPlayer.economy().emerald() >= cost;
            actions.add(actionButton(
                    Component.literal("레벨업 → 레벨 " + (tower.level() + 1) + " (" + cost + "◆)")
                            .withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED),
                    "/semiontd income upgrade " + coordinates,
                    Component.literal("보내는 유닛이 세지고 라운드 인컴이 +" + (unit == null ? 0 : unit.incomeGain()) + " 오릅니다."),
                    BUTTON_WIDTH));
        }
        actions.add(actionButton(Component.literal("판매").withStyle(ChatFormatting.RED), "/semiontd income sell " + coordinates,
                Component.literal("에메랄드 " + IncomeTowerBalance.sellRefund(tower.paidEmerald()) + " 환불, 인컴 -"
                        + IncomeTowerService.incomeOf(game, tower)), BUTTON_WIDTH));
        showActions(player, "세미온 TD 인컴 타워", body.toString(), actions, 1);
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
        Tower runtimeTower = entity.runtimeTower();
        boolean fullOperationActive = runtimeTower != null && FrostFullOperationService.isActive(
                runtimeTower.ownerPlayer(), entity.level().getGameTime());
        double incomingDamageReduction = FrostFullOperationService.displayedDamageReduction(
                runtimeTower == null ? null : runtimeTower.ownerPlayer(),
                entity.level().getGameTime(),
                entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION)
        );
        if (incomingDamageReduction > 0.0) {
            body.append("<blue>🛡 받는 피해 ")
                    .append(oneDecimal((1.0 - incomingDamageReduction) * 100.0))
                    .append("%</blue>")
                    .append(" <dark_gray>(</dark_gray><green>-")
                    .append(percent(incomingDamageReduction))
                    .append("</green><dark_gray>)</dark_gray>\n");
        }

        StringBuilder effects = new StringBuilder();
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RENNERVATE_DAMAGE_BONUS, "<green>레네르바테 공격력 +", "</green>");
        double schoolProtection = kim.biryeong.semiontd.tower.magicschool.MagicSchoolSpellCombat.protection(entity);
        if (schoolProtection > 0) effects.append("<blue>마법학교 보호: 받는 피해 -")
                .append(java.math.BigDecimal.valueOf(schoolProtection * 100.0)
                        .setScale(5, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
                .append("% (곱연산 · 라운드 종료까지)</blue>\n");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_DAMAGE_BONUS, "<green>⚔ 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_ATTACK_SPEED_BONUS, "<green>⚡ 공속 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RANGE_BONUS, "<green>🎯 사거리 증가 +", "</green>");
        if (!fullOperationActive) {
            appendTimedEffect(effects, entity, TimedEffectType.TOWER_DAMAGE_REDUCTION, "<blue>🛡 받피 감소 +", "</blue>");
        }
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
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_ATTACK_SPEED_MULTIPLICATIVE_REDUCTION, "<red>⚡ 공격 속도 -", "</red>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RANGE_REDUCTION, "<red>🎯 사거리 감소 -", "</red>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RANGE_MULTIPLICATIVE_REDUCTION, "<red>🎯 사거리 -", "</red>");
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
        appendTimedEffectValue(effects, entity, TimedEffectType.TOWER_FLAT_MAGIC_DAMAGE_BONUS,
                "<green>✦ 마법 공격력 증가 +", "</green>");
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
        lines.addAll(AugmentCombat.detailLines(tower));
        return lines;
    }

    static String formatTowerDamage(Tower tower, double currentDamage) {
        if (tower != null && tower.primaryDamageType() == DamageType.MAGIC) {
            return formatMagicDamage(currentDamage, "");
        }
        return formatAttackDamage(currentDamage, "");
    }

    static String formatTowerDamageStats(Tower tower, SemionTowerEntity entity, double currentDamage) {
        if (tower instanceof MagicSchoolWizardTower wizard
                && wizard.selectedSpell() == kim.biryeong.semiontd.tower.magicschool.MagicSchoolSpell.AVADA_KEDAVRA) {
            return "<aqua>마법 피해: 대상 최대 체력의 "
                    + percent(wizard.selectedSpell().value("maxHealthMultiplier")) + "</aqua>";
        }
        if (tower instanceof GamblerTower gambler) {
            GamblerTower.AttackDamage damage = gambler.currentAttackDamage(entity);
            return formatAttackDamage(damage.physical(), "")
                    + formatIncrease(tower.type().damage(), damage.physical()) + "\n"
                    + formatMagicDamage(damage.magic(), "")
                    + formatIncrease(GambleBalance.baseMagicDamage(tower.type()), damage.magic());
        }
        return formatTowerDamage(tower, currentDamage) + formatIncrease(towerPrimaryDamage(tower), currentDamage);
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
        if (GambleTowers.isGambler(type)) {
            return formatAttackDamage(type.damage(), "") + "\n"
                    + formatMagicDamage(GambleBalance.baseMagicDamage(type), "");
        }
        return formatTowerTypeDamage(type, towerTypePrimaryDamage(type));
    }

    static double towerPrimaryDamage(Tower tower) {
        if (tower == null) {
            return 0.0;
        }
        return primaryDamage(tower.type(), tower.primaryDamageType());
    }

    public static double currentTowerPrimaryDamage(Tower tower, SemionTowerEntity towerEntity) {
        if (tower == null) {
            return 0.0;
        }
        if (tower instanceof GamblerTower gambler) {
            return gambler.currentAttackDamage(towerEntity).physical();
        }
        double baseDamage = towerPrimaryDamage(tower);
        if (tower instanceof MagicSchoolWizardTower wizard) {
            var target = towerEntity == null ? null : towerEntity.currentAttackTarget();
            double attack = towerEntity == null ? wizard.modifyAttackDamage(null, null, baseDamage)
                    : wizard.resolveBasicAttackOutgoingDamage(towerEntity, target, towerEntity.attackDamageAmount(target));
            return wizard.spellPrimaryDamage(target, attack);
        }
        if (tower.primaryDamageType() == DamageType.MAGIC && !SuccubusTowers.isSuccubusTower(tower.type())
                && !(tower instanceof MagicSchoolWizardTower)) {
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
        for (String line : lines) {
            String formatted = PirateTowers.isPirateTower(tower.type()) ? PirateTowers.highlightAbilityNumbers(line)
                    : tower instanceof MagicSchoolWizardTower
                            ? MagicSchoolSpell.highlightAttackCoefficients(line) : line;
            body.append("<dark_gray>-</dark_gray> <green>").append(formatted).append("</green>\n");
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

    public void showAugment(ServerPlayer player, AugmentService.Screen screen) {
        List<DialogBody> bodies = new ArrayList<>();
        if (!screen.body().isBlank()) {
            bodies.add(new PlainMessage(miniMessage(screen.body()), AUGMENT_BODY_WIDTH));
        }
        if (screen.cards().size() == 3) {
            bodies.addAll(UiAugmentOfferView.bodies(screen.cards()));
        } else {
            for (AugmentService.CardLine card : screen.cards()) {
                bodies.add(new PlainMessage(miniMessage(card.text()), AUGMENT_BODY_WIDTH));
            }
        }
        List<ActionButton> actions = screen.buttons().stream()
                .map(button -> new ActionButton(new net.minecraft.server.dialog.CommonButtonData(
                        Component.literal(button.label()), Optional.empty(),
                        screen.cards().size() == 3 ? UiAugmentOfferView.CARD_WIDTH : COMPACT_BUTTON_WIDTH),
                        button.command() == null || button.command().isBlank() ? Optional.empty()
                                : Optional.of(new net.minecraft.server.dialog.action.StaticAction(
                                        new net.minecraft.network.chat.ClickEvent.RunCommand(button.command())))))
                .toList();
        showActions(player, miniMessage(screen.title()), bodies, actions, screen.columns());
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

    static String jobManagementCommand(SemionJob job) {
        return "/semiontd job " + (JobRegistry.isEnabled(job) ? "disable " : "enable ") + job.id().getPath();
    }

    private static ActionButton jobManagementButton(SemionJob job) {
        boolean enabled = JobRegistry.isEnabled(job);
        Component label = Component.literal((enabled ? "켜짐 · " : "꺼짐 · ") + job.displayName().getString())
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        String command = jobManagementCommand(job);
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

    private static String traitName(net.minecraft.resources.Identifier traitId) {
        return TraitRegistry.find(traitId)
                .map(trait -> trait.displayName().getString())
                .orElse(traitId.toString());
    }

    private static String traitEffectSummary(net.minecraft.resources.Identifier traitId, TraitSlot slot) {
        return TraitRegistry.find(traitId)
                .map(trait -> trait.effectSummary(slot).getString())
                .orElse("효과 정보 없음");
    }

    private static String traitName(String traitId) {
        net.minecraft.resources.Identifier parsed =
                traitId == null ? null : net.minecraft.resources.Identifier.tryParse(traitId);
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
        appendTowerDescription(tooltip, PirateTowers.isPirateTower(type) ? type.description() : type.description().stream().limit(2).toList());
        return tooltip;
    }

    private static Component upgradeTooltip(TowerUpgradeOption option, boolean affordable, boolean recommended, Tower currentTower) {
        long mineralCost = ProductionTowerService.upgradeCost(currentTower, option);
        Optional<ProductionTowerCatalog.CatalogEntry> target = ProductionTowerCatalog.entry(option.targetType());
        if (target.isEmpty()) {
            return Component.literal("대상 타워를 찾을 수 없습니다.\n비용 " + mineralCost + " 다이아");
        }
        var entry = target.get();
        var type = entry.type();
        List<String> customLines = currentTower.upgradeTooltipLines(option);
        boolean actionOnly = type.id().equals(currentTower.type().id()) && !customLines.isEmpty();
        double attacksPerSecond = 20.0 / Math.max(1, type.attackIntervalTicks());
        int capacityDelta = TowerCapacity.slotCost(type) - TowerCapacity.slotCost(currentTower.type());
        String capacityLine = capacityDelta == 0 ? "" : "<yellow>타워 수 +" + capacityDelta + "</yellow>\n";
        MutableComponent tooltip = mutableMiniMessage("<yellow><bold>" + option.displayName() + "</bold></yellow>\n" + (recommended ? "<blue>빌드 추천</blue>\n" : "") + "<gray>대상</gray> <white>" + type.displayName() + "</white>\n" + DIAMOND_GRADIENT + "\uD83D\uDC8E " + mineralCost + " 다이아" + GRADIENT_CLOSE + (affordable ? " <green>(구매 가능)</green>" : " <red>(부족)</red>") + "\n" + capacityLine + advExperienceRequirementLine(currentTower, option));
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
        if (!VillagerAdvStates.isAdvTower(tower) || option == null
                || tower.augmentSnapshot().has("job_villager_adv_towers_g1")) {
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
        return summonTooltip(type, affordable, sandbox, null);
    }

    private static Component summonTooltip(SummonMonsterType type, boolean affordable, boolean sandbox, AugmentEconomyService.PurchasePlan quote) {
        double attacksPerSecond = 20.0 / 13.0;
        MutableComponent tooltip = mutableMiniMessage("<yellow><bold>" + type.displayName() + "</bold></yellow> <dark_gray>|</dark_gray> <gray>" + roleList(type) + "</gray>\n");
        tooltip.append(dividerComponent(160)).append(Component.literal("\n"));
        if (sandbox) {
            tooltip.append(mutableMiniMessage("<green>◆ 무료</green>\n<gray>수입 증가 없음</gray>\n" + formatKillReward(type.mineralReward(), "") + "\n"));
        } else if (quote != null) {
            tooltip.append(Component.literal(AugmentService.purchaseSummary(quote) + "\n"));
            tooltip.append(Component.literal("아래 능력치는 증강 배율 적용 전 기본값입니다.\n").withStyle(ChatFormatting.GRAY));
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
                if (line.startsWith(" ▶ ")) {
                    body.append("<gray>").append(line).append("</gray>\n");
                } else {
                    body.append("<dark_gray>-</dark_gray> <gray>").append(line).append("</gray>\n");
                }
            }
        }
    }

    private static void appendTowerDescription(MutableComponent tooltip, List<String> description) {
        if (description.isEmpty()) {
            return;
        }
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal(line.startsWith(" ▶ ") ? "\n" : "\n- ").withStyle(ChatFormatting.DARK_GRAY));
                tooltip.append(miniMessage("<gray>" + line + "</gray>"));
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

    private static String percent(double value) {
        return oneDecimal(value * 100.0) + "%";
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static Component participantResultBody(
            MatchParticipantResult participant,
            MatchProgressionReward reward
    ) {
        var stats = participant.stats();
        MutableComponent body = Component.empty()
                .append(TextUncenterer.filler(8))
                .append(UiPlayerAvatarService.avatarComponent(participant.playerName(), UiPlayerAvatarService.AvatarVariant.RESULT))
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
        Component divider = dividerComponent(HeaderMessage.contentWidth(PLAYER_STATUS_WIDTH));

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
                UiPlayerAvatarService.AvatarVariant.COMPACT.imageSize(),
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

    static Component playerStatusAvatar(String playerName) {
        return UiPlayerAvatarService.avatarComponent(playerName, UiPlayerAvatarService.AvatarVariant.COMPACT);
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

}
