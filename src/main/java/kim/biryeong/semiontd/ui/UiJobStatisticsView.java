package kim.biryeong.semiontd.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.trait.TraitRegistry;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import net.minecraft.ChatFormatting;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsState;
import kim.biryeong.semiontd.statistics.JobStatisticsTotals;
import kim.biryeong.semiontd.statistics.TraitCombinationStatisticsEntry;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import kim.biryeong.semiontd.ui.SemionDialogService.JobStatisticsRow;
import static kim.biryeong.semiontd.ui.UiDialogTableLayout.*;

final class UiJobStatisticsView {
    private UiJobStatisticsView() {
    }

    static final int JOB_STATISTICS_WIDTH = 460;

    static final int JOB_STATISTICS_DETAIL_WIDTH = 420;

    static final int JOB_STATISTICS_DETAIL_CONTENT_WIDTH = JOB_STATISTICS_DETAIL_WIDTH - 23;

    static final int JOB_STATISTICS_DETAIL_TABLE_WIDTH = 380;

    static final int JOB_STATISTICS_JOB_WIDTH = 80;

    static final int JOB_STATISTICS_SELECTION_WIDTH = 100;

    static final int JOB_STATISTICS_GAME_WIDTH = 120;

    static final int JOB_STATISTICS_PLACEMENT_WIDTH = 120;

    static final int JOB_STATISTICS_SEPARATOR_WIDTH = 10;

    static final DateTimeFormatter STATISTICS_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

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
        return new PlainMessage(HeaderMessage.dividerComponent(JOB_STATISTICS_DETAIL_TABLE_WIDTH), JOB_STATISTICS_DETAIL_WIDTH);
    }

    static void appendJobStatisticsState(List<DialogBody> bodies, JobStatisticsState state) {
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

    static Component statisticsOverview(JobStatisticsSnapshot snapshot) {
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
                        .append(Component.literal("(R)").withStyle(ChatFormatting.DARK_GRAY))
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

    static Component jobStatisticsSummaryTableRow(
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

    static Component jobStatisticsRoundCell(JobStatisticsEntry entry, int round) {
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

    static List<Component> statisticsHeaderCells(String... labels) {
        return java.util.Arrays.stream(labels)
                .map(label -> (Component) Component.literal(label).withStyle(ChatFormatting.GRAY))
                .toList();
    }

    static Component statisticsHeaderCell(
            String label,
            String parenthetical,
            ChatFormatting parentheticalColor
    ) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
                .append(Component.literal("(" + parenthetical + ")").withStyle(parentheticalColor));
    }

    static Component statisticsValueCell(
            String value,
            String parenthetical,
            ChatFormatting parentheticalColor
    ) {
        return Component.literal(value).withStyle(ChatFormatting.WHITE)
                .append(Component.literal("(" + parenthetical + ")").withStyle(parentheticalColor));
    }

    static Component statisticsNumber(OptionalDouble value) {
        return Component.literal(formatAverage(value, "")).withStyle(ChatFormatting.WHITE);
    }

    static List<Integer> statisticsEqualColumnWidths(int columnCount) {
        return statisticsEqualColumnWidths(columnCount, false);
    }

    static List<Integer> statisticsEqualColumnWidths(int columnCount, boolean separated) {
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

    static Component centeredStatisticsRows(
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

    static Component centeredStatisticsRow(
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

    static Component statisticsTableSeparator() {
        return Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY);
    }

    static JobStatisticsEntry emptyJobStatisticsEntry(String jobId) {
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

    static void addStatisticsTable(List<DialogBody> bodies, Component table) {
        bodies.add(new PlainMessage(table, JOB_STATISTICS_DETAIL_WIDTH));
    }

    static void addStatisticsSectionWithHeaderAndBody(
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

    static void addStatisticsSectionWithBody(
            List<DialogBody> bodies,
            String title,
            Component body
    ) {
        addStatisticsTable(bodies, statisticsSectionHeader(title));
        addStatisticsTable(bodies, body);
    }

    static Component statisticsSectionHeader(String title) {
        return new HeaderMessage(
                Component.literal(title).withStyle(ChatFormatting.YELLOW),
                JOB_STATISTICS_DETAIL_TABLE_WIDTH + 8
        ).asVanillaComponent();
    }

    static void addCenteredStatisticsLine(List<DialogBody> bodies, Component line) {
        bodies.add(new PlainMessage(
                centeredTableCell(line, JOB_STATISTICS_DETAIL_CONTENT_WIDTH),
                JOB_STATISTICS_DETAIL_WIDTH
        ));
    }

    static void addTraitCombinationStatistics(
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

    static Component fitStatisticsLabel(String label, int width) {
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

    static String formatPercent(OptionalDouble value) {
        if (value.isEmpty() || !Double.isFinite(value.getAsDouble())) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%%", value.getAsDouble() * 100.0);
    }

    static String formatAverage(OptionalDouble value, String suffix) {
        if (value.isEmpty() || !Double.isFinite(value.getAsDouble())) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%s", value.getAsDouble(), suffix);
    }

    static String formatRound(OptionalDouble value) {
        if (value.isEmpty() || !Double.isFinite(value.getAsDouble())) {
            return "-";
        }
        return String.format(Locale.ROOT, "R%.1f", value.getAsDouble());
    }

    static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static String formatTime(long epochMillis) {
        return epochMillis <= 0L ? "-" : STATISTICS_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String traitName(net.minecraft.resources.Identifier traitId) {
        return TraitRegistry.find(traitId)
                .map(trait -> trait.displayName().getString())
                .orElse(traitId.toString());
    }

    private static String traitName(String traitId) {
        net.minecraft.resources.Identifier parsed =
                traitId == null ? null : net.minecraft.resources.Identifier.tryParse(traitId);
        return parsed == null ? String.valueOf(traitId) : traitName(parsed);
    }
}
