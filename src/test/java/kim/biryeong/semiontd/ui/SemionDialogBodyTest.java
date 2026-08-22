package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.VillagerTowerJob;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsTotals;
import kim.biryeong.semiontd.statistics.TraitCombinationStatisticsEntry;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyLeaderTower;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyPolicy;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.SemionTrait;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SemionDialogBodyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dividerMarkersBecomeRenderedComponentsEvenWithoutActionButtons() {
        var bodies = SemionDialogService.actionDialogBodies("\n첫 항목\n<divider>\n둘째 항목");

        assertEquals(1, bodies.size());
        PlainMessage body = assertInstanceOf(PlainMessage.class, bodies.getFirst());
        assertFalse(body.contents().getString().contains("<divider>"));
        assertTrue(body.contents().getSiblings().size() >= 3);
    }

    @Test
    void futureAgencyLeaderUsesTopSaveButtonAndThreePolicyButtonsBelow() {
        List<TowerUpgradeOption> upgrades = List.of(
                option(FutureAgencyLeaderTower.SAVE_WORLD),
                option(FutureAgencyPolicy.AGENCY_TACTICS.upgradeId()),
                option(FutureAgencyPolicy.COMPOSITE_ARMOR.upgradeId()),
                option(FutureAgencyPolicy.REACTION_TRAINING.upgradeId()),
                option(FutureAgencyLeaderTower.PROMOTE_COMMANDER)
        );

        assertEquals(List.of(-1, 0, -1, 1, 2, 3, 4),
                SemionDialogService.futureAgencyUpgradeGrid(upgrades));
    }

    @Test
    void playerStatusUsesBoldNameOnlyForTeamLeader() {
        var leader = statusRow("leader", true);
        var member = statusRow("member", false);

        assertTrue(SemionDialogService.playerStatusName(leader).getStyle().isBold());
        assertFalse(SemionDialogService.playerStatusName(member).getStyle().isBold());
    }

    @Test
    void playerStatusBodyMovesJobLeftWithoutMovingDiamondColumn() {
        int bodyNameWidth = SemionDialogService.PLAYER_STATUS_NAME_WIDTH
                - SemionDialogService.PLAYER_STATUS_BODY_JOB_SHIFT;
        int bodyJobWidth = SemionDialogService.PLAYER_STATUS_JOB_WIDTH
                + SemionDialogService.PLAYER_STATUS_BODY_JOB_SHIFT;

        assertEquals(100, bodyNameWidth);
        assertEquals(68, bodyJobWidth);
        assertEquals(
                SemionDialogService.PLAYER_STATUS_NAME_WIDTH + SemionDialogService.PLAYER_STATUS_JOB_WIDTH,
                bodyNameWidth + bodyJobWidth
        );
    }

    @Test
    void playerStatusUsesCenteredFourHundredTwentyPixelTable() {
        List<Integer> widths = SemionDialogService.playerStatusColumnWidths();

        assertEquals(List.of(40, 132, 56, 56, 52, 44, 40), widths);
        assertEquals(420, widths.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void playerStatusKeepsOneSpaceBetweenAvatarAndPlayerName() {
        Component centered = SemionDialogService.playerStatusPlayerCell(
                Component.literal("avatar"),
                Component.literal("name"),
                132,
                10,
                24
        );
        Component player = centered.getSiblings().get(1);

        assertEquals(3, player.getSiblings().size());
        assertEquals("avatar", player.getSiblings().getFirst().getString());
        assertEquals(" ", player.getSiblings().get(1).getString());
        assertEquals("name", player.getSiblings().getLast().getString());
    }

    @Test
    void playerStatusDoesNotWaitForRemoteAvatarLoading() {
        assertTimeoutPreemptively(
                Duration.ofMillis(500),
                () -> SemionDialogService.playerStatusAvatar("no-such-player")
        );
    }

    @Test
    void playerStatusTitleTouchesHeaderAndDividerLeavesOneBlankLineBeforeBody() {
        Component headerWithDivider = SemionDialogService.playerStatusHeaderWithDivider(
                Component.literal("header"),
                Component.literal("divider")
        );
        String table = SemionDialogService.playerStatusHeaderWithBodySpacing(headerWithDivider)
                .append("\n")
                .append("body")
                .getString();
        String dialog = SemionDialogService.playerStatusDialogContents(
                Component.literal("title"),
                Component.literal("header")
        ).getString();

        assertEquals("title\nheader", dialog);
        assertEquals("header\ndivider\n\nbody", table);
    }

    @Test
    void playerStatusColumnHeaderUsesBodyColors() {
        List<Component> labels = SemionDialogService.playerStatusHeaderLabels();

        assertEquals(List.of("팀", "플레이어", "직업", "다이아", "에메랄드", "수입", "타워"),
                labels.stream().map(Component::getString).toList());
        assertEquals(List.of(
                        ChatFormatting.WHITE.getColor(),
                        ChatFormatting.WHITE.getColor(),
                        ChatFormatting.WHITE.getColor(),
                        ChatFormatting.AQUA.getColor(),
                        ChatFormatting.GREEN.getColor(),
                        ChatFormatting.YELLOW.getColor(),
                        ChatFormatting.GOLD.getColor()
                ),
                labels.stream().map(label -> label.getStyle().getColor().getValue()).toList());
    }

    @Test
    void playerStatusJobBodyUsesWhite() {
        Component job = SemionDialogService.playerStatusJob("화이트직업");

        assertEquals("화이트직업", job.getString());
        assertEquals(ChatFormatting.WHITE.getColor(), job.getStyle().getColor().getValue());
    }

    @Test
    void playerStatusLeavesOneBlankLineBetweenTeams() {
        var table = Component.literal("red");
        SemionDialogService.appendPlayerStatusTeamSpacing(table);
        table.append("\n").append("blue");

        assertEquals("red\n\nblue", table.getString());
    }

    @Test
    void jobStatisticsHeaderAndDividerUseRenderedWhiteLines() {
        HeaderMessage header = SemionDialogService.jobStatisticsHeader(Component.literal("직업 통계"));
        HeaderMessage detailHeader = SemionDialogService.jobStatisticsDetailHeader(Component.literal("주민 빌더 통계"));
        PlainMessage divider = SemionDialogService.jobStatisticsDivider();
        PlainMessage detailDivider = SemionDialogService.jobStatisticsDetailDivider();

        assertEquals("직업 통계", header.contents().getString());
        assertEquals(460, header.width());
        assertEquals(420, detailHeader.width());
        assertFalse(header.contents().getString().contains("──────"));
        assertEquals(ChatFormatting.WHITE.getColor(), divider.contents().getStyle().getColor().getValue());
        assertEquals(ChatFormatting.WHITE.getColor(), detailDivider.contents().getStyle().getColor().getValue());
        assertTrue(divider.contents().getStyle().isStrikethrough());
        assertEquals("직업 목록", SemionDialogService.jobStatisticsListHeader().contents().getString());
    }

    @Test
    void jobStatisticsCategoriesExcludeUnemployedAndKeepBuilderGroups() {
        JobStatisticsSnapshot snapshot = JobStatisticsSnapshot.empty();

        assertEquals(
                JobRegistry.officialBuilders().stream().map(job -> job.id().toString()).toList(),
                SemionDialogService.jobStatisticsCategoryRows(snapshot, true).stream()
                        .map(SemionDialogService.JobStatisticsRow::jobId)
                        .toList()
        );
        assertEquals(
                JobRegistry.creativeBuilders().stream().map(job -> job.id().toString()).toList(),
                SemionDialogService.jobStatisticsCategoryRows(snapshot, false).stream()
                        .map(SemionDialogService.JobStatisticsRow::jobId)
                        .toList()
        );
        assertFalse(SemionDialogService.jobStatisticsCategoryRows(snapshot, true).stream()
                .anyMatch(row -> row.jobId().equals(JobRegistry.defaultJob().id().toString())));
        assertFalse(SemionDialogService.jobStatisticsCategoryRows(snapshot, false).stream()
                .anyMatch(row -> row.jobId().equals(JobRegistry.defaultJob().id().toString())));
    }

    @Test
    void jobStatisticsSummaryUsesRequestedOrderWithoutRoundPassRates() {
        JobStatisticsEntry entry = new JobStatisticsEntry(
                VillagerTowerJob.ID.toString(),
                4L,
                3L,
                4L,
                8L,
                30L,
                JobStatisticsTotals.empty(),
                1_000L,
                2_000L,
                3_000L
        );
        JobStatisticsSnapshot snapshot = new JobStatisticsSnapshot(
                3_000L,
                2L,
                5L,
                1_000L,
                2_000L,
                List.of(entry)
        );
        var row = new SemionDialogService.JobStatisticsRow(
                entry.jobId(),
                "주민 빌더",
                entry,
                true
        );

        List<Component> headerCells = SemionDialogService.jobStatisticsSummaryHeaderCells();
        List<Component> summaryCells = SemionDialogService.jobStatisticsSummaryCells(snapshot, row);

        assertEquals(List.of("직업", "선택 (선택률)", "경기 (승률)", "순위 (평균 라운드)"),
                headerCells.stream().map(Component::getString).toList());
        assertEquals(List.of(
                        "주민 빌더",
                        "4회 (80.0%)",
                        "3승 1패 (75.0%)",
                        "2.0위 (R7.5)"
                ),
                summaryCells.stream().map(Component::getString).toList());
        assertFalse(summaryCells.stream().map(Component::getString).anyMatch(text -> text.contains("통과")));
        assertEquals(ChatFormatting.AQUA.getColor(), headerCells.get(0).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.DARK_GRAY.getColor(),
                headerCells.get(1).getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(ChatFormatting.GREEN.getColor(),
                headerCells.get(2).getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(ChatFormatting.DARK_GRAY.getColor(),
                headerCells.get(3).getSiblings().getFirst().getStyle().getColor().getValue());
    }

    @Test
    void jobStatisticsSummaryReservesSpaceForAverageRound() {
        List<Integer> widths = SemionDialogService.jobStatisticsSummaryColumnWidths();

        assertEquals(List.of(80, 100, 120, 120), widths);
        assertEquals(420, widths.stream().mapToInt(Integer::intValue).sum());
        assertEquals(300, widths.subList(0, 3).stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void jobStatisticsSummaryCentersHeaderAndBodyCells() {
        Component value = Component.literal("직업");
        int valueWidth = 12;
        Component centered = SemionDialogService.centeredTableCell(value, 80, valueWidth);
        int remainingWidth = 80 - valueWidth;
        int leftPaddingWidth = remainingWidth / 2;

        assertEquals(3, centered.getSiblings().size());
        assertEquals("직업", centered.getSiblings().get(1).getString());
        assertEquals(SemionUiFont.space(leftPaddingWidth).getString(),
                centered.getSiblings().getFirst().getString());
        assertEquals(SemionUiFont.space(remainingWidth - leftPaddingWidth).getString(),
                centered.getSiblings().getLast().getString());
    }

    @Test
    void jobStatisticsDetailUsesCenteredRequestedTablesThroughRoundForty() {
        List<Long> roundPasses = java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList();
        List<Long> roundAttempts = java.util.Collections.nCopies(40, 100L);
        JobStatisticsEntry entry = new JobStatisticsEntry(
                VillagerTowerJob.ID.toString(),
                10L,
                3L,
                10L,
                20L,
                300L,
                new JobStatisticsTotals(
                        1_000L, 500L, 100L, 200L,
                        1_000.0, 100.0, 800.0, 400.0,
                        300L, 200L, 2_000L, 250.0, 600.0
                ),
                1_000L,
                2_000L,
                3_000L,
                roundPasses,
                roundAttempts
        );
        JobStatisticsSnapshot snapshot = new JobStatisticsSnapshot(
                3_000L, 10L, 10L, 1_000L, 2_000L, List.of(entry)
        );

        assertEquals(
                List.of("선택(%)", "승리(%)", "평균 순위(R)"),
                SemionDialogService.jobStatisticsSampleHeaderCells().stream().map(Component::getString).toList()
        );
        assertEquals(
                List.of("10회(100.0%)", "3승(30.0%)", "2.0위(R30.0)"),
                SemionDialogService.jobStatisticsSampleCells(snapshot, entry).stream()
                        .map(Component::getString).toList()
        );

        List<List<String>> roundRows = SemionDialogService.jobStatisticsRoundRows(entry).stream()
                .map(row -> row.stream().map(Component::getString).toList())
                .toList();
        assertEquals(10, roundRows.size());
        assertEquals(List.of("R1 1.0%", "R11 11.0%", "R21 21.0%", "R31 31.0%"), roundRows.getFirst());
        assertEquals(List.of("R10 10.0%", "R20 20.0%", "R30 30.0%", "R40 40.0%"), roundRows.getLast());

        int renderedWidth = SemionDialogService.jobStatisticsRoundColumnWidths().stream()
                .mapToInt(Integer::intValue)
                .sum() + 10 * 3;
        assertEquals(380, renderedWidth);

        for (int columnCount : List.of(2, 3, 4, 5, 6)) {
            List<Integer> widths = SemionDialogService.statisticsEqualColumnWidths(columnCount);
            assertEquals(380, widths.stream().mapToInt(Integer::intValue).sum());
            assertTrue(widths.stream().mapToInt(Integer::intValue).max().orElseThrow()
                    - widths.stream().mapToInt(Integer::intValue).min().orElseThrow() <= 1);
        }
        assertEquals(List.of(76, 76, 76, 76, 76),
                SemionDialogService.statisticsEqualColumnWidths(5));
        assertEquals(List.of(64, 64, 63, 63, 63, 63),
                SemionDialogService.statisticsEqualColumnWidths(6));
    }

    @Test
    void jobStatisticsDetailUsesRequestedMetricAndTraitHeaders() {
        assertEquals(
                List.of("특성", "선택(%)", "승리(%)", "평균 순위(R)"),
                SemionDialogService.jobStatisticsTraitHeaderCells().stream().map(Component::getString).toList()
        );
        assertEquals(
                List.of("평균 처치", "평균 획득 다이아"),
                SemionDialogService.jobStatisticsCombatHeaderCells().stream().map(Component::getString).toList()
        );
        assertEquals(
                List.of("평균 소환", "최종(생산)", "보낸 위협", "받은 위협", "성공 위협(%)"),
                SemionDialogService.jobStatisticsIncomeHeaderCells().stream().map(Component::getString).toList()
        );
        assertEquals(
                List.of("라인 위협", "누수 위협", "방어율", "라인 다이아", "지원 다이아", "정리 위협"),
                SemionDialogService.jobStatisticsDefenseHeaderCells().stream().map(Component::getString).toList()
        );

    }

    @Test
    void jobStatisticsTraitLabelsHideEveryVersion() {
        TraitCombinationStatisticsEntry combination = new TraitCombinationStatisticsEntry(
                VillagerTowerJob.ID.toString(),
                "semion-td:unknown_primary",
                1,
                "semion-td:unknown_secondary",
                9,
                1L,
                0L,
                1L,
                1L,
                10L,
                null,
                null,
                List.of()
        );

        assertEquals(
                "semion-td:unknown_primary·semion-td:unknown_secondary",
                SemionDialogService.jobStatisticsTraitLabel(combination)
        );
    }

    @Test
    void jobStatisticsCategoryOverviewShowsOnlyRecordPeriod() {
        JobStatisticsSnapshot snapshot = new JobStatisticsSnapshot(
                3_000L,
                2L,
                5L,
                1_000L,
                2_000L,
                List.of()
        );
        String overview = SemionDialogService.jobStatisticsCategoryOverview(snapshot).getString();

        assertTrue(overview.startsWith("기록 기간 "));
        assertTrue(overview.contains(" ~ "));
        assertFalse(overview.contains("일반 경기"));
        assertFalse(overview.contains("참가 표본"));
    }

    @Test
    void traitSelectionTimeUsesGreenYellowAndDarkRedThresholds() {
        assertEquals("<white>남은 시간</white> <green>16초</green>", SemionDialogService.traitSelectionTimeMarkup(16));
        assertEquals("<white>남은 시간</white> <yellow>15초</yellow>", SemionDialogService.traitSelectionTimeMarkup(15));
        assertEquals("<white>남은 시간</white> <yellow>6초</yellow>", SemionDialogService.traitSelectionTimeMarkup(6));
        assertEquals("<white>남은 시간</white> <dark_red>5초</dark_red>", SemionDialogService.traitSelectionTimeMarkup(5));
        assertEquals("<white>남은 시간</white> <green>제한 없음</green>", SemionDialogService.traitSelectionTimeMarkup(-1));
    }

    @Test
    void traitSelectionSummaryUsesRequestedLayoutAndNoneColor() {
        assertEquals(
                "<gradient:#67e8f9:#a78bfa><bold>특성 선택</bold></gradient>\n"
                        + "<white>남은 시간</white> <green>30초</green>\n"
                        + "<white>주특성 100%</white> <dark_gray>|</dark_gray> <dark_red>선택 안 함</dark_red>\n"
                        + "<white>부특성 50%</white> <dark_gray>|</dark_gray> <dark_red>선택 안 함</dark_red>\n"
                        + "<divider>\n"
                        + "<white>주특성 능력</white> <dark_gray>|</dark_gray> <yellow>효과 없음</yellow>\n"
                        + "<white>부특성 능력</white> <dark_gray>|</dark_gray> <yellow>효과 없음</yellow>\n"
                        + "<divider>\n"
                        + "<gray>아래 버튼으로 주특성/부특성을 각각 선택하세요.</gray>\n"
                        + "<gray>창을 닫아도 제한 시간 안에는 /특성으로 다시 열 수 있습니다.</gray>\n"
                        + "<divider>",
                SemionDialogService.traitSelectionSummaryBody(TraitLoadout.none(), 30)
        );
    }

    @Test
    void traitSelectionSummaryUsesYellowForSelectedTrait() {
        var selectedTrait = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "semion-td-test",
                "selected"
        );

        assertTrue(SemionDialogService.traitSelectionSummaryBody(
                new TraitLoadout(selectedTrait, selectedTrait),
                30
        ).contains("<yellow>semion-td-test:selected</yellow>"));
    }

    @Test
    void primaryTraitSelectionUsesSummaryDesignWithAbilityAndThreeDescriptionLines() {
        assertEquals(
                "<gradient:#67e8f9:#a78bfa><bold>주특성 선택</bold></gradient>\n"
                        + "<white>남은 시간</white> <dark_red>0초</dark_red>\n"
                        + "<white>주특성 100%</white> <dark_gray>|</dark_gray> <dark_red>선택 안 함</dark_red>\n"
                        + "<white>능력</white> <dark_gray>|</dark_gray> <yellow>효과 없음</yellow>\n"
                        + "<divider>\n"
                        + "<gray>같은 non-none 특성은 주/부특성에 동시에 선택할 수 없습니다.</gray>\n"
                        + "<gray>버튼에 마우스를 올리면 효과와 설명이 표시됩니다.</gray>\n"
                        + "<gray>창을 닫아도 제한 시간 안에는 /특성으로 다시 열 수 있습니다.</gray>\n"
                        + "<divider>",
                SemionDialogService.traitSelectionSlotBody(TraitLoadout.none(), 0, TraitSlot.PRIMARY)
        );
    }

    @Test
    void secondaryTraitSelectionUsesSecondaryScaleAndSameDesign() {
        String body = SemionDialogService.traitSelectionSlotBody(
                TraitLoadout.none(),
                10,
                TraitSlot.SECONDARY
        );

        assertTrue(body.startsWith(
                "<gradient:#67e8f9:#a78bfa><bold>부특성 선택</bold></gradient>\n"
                        + "<white>남은 시간</white> <yellow>10초</yellow>\n"
                        + "<white>부특성 50%</white> <dark_gray>|</dark_gray> <dark_red>선택 안 함</dark_red>\n"
                        + "<white>능력</white> <dark_gray>|</dark_gray> <yellow>효과 없음</yellow>\n"
        ));
    }

    @Test
    void traitSelectionAbilityUsesWhiteLabelAndGreenValue() {
        assertEquals(
                "<white>능력</white> <dark_gray>|</dark_gray> "
                        + "<green>최대 체력 +30% · 흑마법사, 엔드 +10%</green>",
                SemionDialogService.traitSelectionAbilityLine(
                        "최대 체력 +30% · 흑마법사, 엔드 +10%"
                )
        );
    }

    @Test
    void traitSelectionAbilityUsesYellowForNoEffect() {
        assertEquals(
                "<white>능력</white> <dark_gray>|</dark_gray> <yellow>효과 없음</yellow>",
                SemionDialogService.traitSelectionAbilityLine("효과 없음", true)
        );
    }

    @Test
    void traitSelectionAbilitySupportsSlotSpecificLabel() {
        assertEquals(
                "<white>주특성 능력</white> <dark_gray>|</dark_gray> <green>최대 체력 +30%</green>",
                SemionDialogService.traitSelectionAbilityLine(
                        "주특성 능력",
                        "최대 체력 +30%",
                        false
                )
        );
    }

    @Test
    void traitTooltipUsesTraitAndSlotHeaderWithDividersAroundAbility() {
        SemionTrait trait = new SemionTrait(
                ResourceLocation.fromNamespaceAndPath("semion-td-test", "ignite"),
                1,
                Component.literal("점화"),
                List.of(Component.literal("설명")),
                Component.literal("능력"),
                Component.literal("부특성 능력")
        ) {
        };

        Component tooltip = SemionDialogService.traitTooltip(trait, TraitSlot.SECONDARY);
        String[] lines = tooltip.getString().split("\n", -1);

        assertEquals(ChatFormatting.YELLOW.getColor(), tooltip.getStyle().getColor().getValue());
        assertEquals(ChatFormatting.DARK_GRAY.getColor(), tooltip.getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(ChatFormatting.WHITE.getColor(), tooltip.getSiblings().get(1).getStyle().getColor().getValue());
        assertEquals(5, lines.length);
        assertEquals("점화 | 부특성 50%", lines[0]);
        assertFalse(lines[1].isBlank());
        assertEquals("부특성 능력", lines[2]);
        assertEquals(lines[1], lines[3]);
        assertEquals("설명", lines[4]);
    }

    @Test
    void summonShopSummaryUsesRequestedColorsAndSeparateDetailLine() {
        assertEquals(
                "<white>페이지</white> <yellow>2</yellow><white>/</white><yellow>4</yellow>"
                        + " <dark_gray>|</dark_gray> <white>소환 후보</white> <yellow>18</yellow>\n"
                        + "<gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>",
                SemionDialogService.summonShopSummary(2, 4, 18, false)
        );
    }

    @Test
    void sandboxSummonShopSummaryUsesGreenSandboxNotice() {
        assertEquals(
                "<white>페이지</white> <yellow>1</yellow><white>/</white><yellow>4</yellow>"
                        + " <dark_gray>|</dark_gray> <white>소환 후보</white> <yellow>18</yellow>\n"
                        + "<gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n"
                        + "<green>샌드박스 소환은 무료이며 수입이 증가하지 않습니다.</green>",
                SemionDialogService.summonShopSummary(1, 4, 18, true)
        );
    }

    @Test
    void summonShopNavigationRestoresActiveAndInactivePageColors() {
        StringBuilder firstPage = new StringBuilder();
        StringBuilder lastPage = new StringBuilder();

        SemionDialogService.appendSummonNavigation(firstPage, "/semiontd summonui ", 1, 4);
        SemionDialogService.appendSummonNavigation(lastPage, "/semiontd summonui ", 4, 4);

        assertEquals(
                "\n<divider>\n"
                        + "<white>페이지 이동</white> "
                        + "<dark_gray>이전</dark_gray>"
                        + " <dark_gray>|</dark_gray> "
                        + "<click:run_command:'/semiontd summonui 2'><hover:show_text:'다음'><aqua>[다음]</aqua></hover></click>"
                        + "\n<divider>",
                firstPage.toString()
        );
        assertEquals(
                "\n<divider>\n"
                        + "<white>페이지 이동</white> "
                        + "<click:run_command:'/semiontd summonui 3'><hover:show_text:'이전'><aqua>[이전]</aqua></hover></click>"
                        + " <dark_gray>|</dark_gray> "
                        + "<dark_gray>다음</dark_gray>"
                        + "\n<divider>",
                lastPage.toString()
        );
    }

    @Test
    void towerControlSummaryUsesRequestedOrderAndTeamColor() {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(120, 50, 10, 3);

        assertEquals(
                "<white>팀</white> <red>RED</red> <dark_gray>|</dark_gray> <white>라인</white> <yellow>#2</yellow>\n"
                        + "<aqua>◆ 다이아 120</aqua> <dark_gray>|</dark_gray> <green>⬢ 에메랄드 50</green>\n"
                        + "<gold>인컴 업그레이드 비용</gold> <aqua>75</aqua> <dark_gray>|</dark_gray> <dark_green>↗</dark_green> <green>에메랄드/초 3</green>\n"
                        + "<gold>타워 수</gold> <yellow>4/6</yellow> <dark_gray>|</dark_gray> <gold>타워 확장 비용</gold> <aqua>◆ 200</aqua> <white>+</white> <green>⬢ 5</green>\n\n",
                SemionDialogService.towerControlSummary(TeamId.RED, 2, economy, 75, 4, 6, 200, 5)
        );
    }

    @Test
    void towerConstructionCandidateSummaryLeavesOneBlankLineAboveAndBelow() {
        assertEquals(
                "<white> </white>\n"
                        + "<yellow>원거리</yellow> <white>계열</white> <dark_gray>|</dark_gray> "
                        + "<white>건설 후보</white> <yellow>3</yellow> <dark_gray>|</dark_gray> "
                        + "<gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n"
                        + "<white> </white>\n",
                SemionDialogService.towerConstructionCandidateSummary("원거리", 3)
        );
    }

    @Test
    void selectedTowerControlSummaryUsesCompactEmojiStatsWithBlankLines() {
        assertEquals(
                "<white> </white>\n"
                        + "<white><bold>테스트 타워</bold></white>\n"
                        + "<#fc5454>❤ 체력</#fc5454><white>: </white><#fc5454>75<dark_gray>/</dark_gray>100</#fc5454>\n"
                        + "<gradient:#ffffff:#d5fff6:#a1fbe8:#4aedd9:#20c5b5:#1aaaa7:#11727a:#145e53>💎 판매가<white>: </white>30 다이아</gradient>\n"
                        + "<white> </white>\n",
                SemionDialogService.towerControlSelectedTowerSummary("테스트 타워", 75, 100, 30)
        );
    }

    private static SemionDialogService.PlayerStatusRow statusRow(String name, boolean teamLeader) {
        return new SemionDialogService.PlayerStatusRow(
                UUID.nameUUIDFromBytes(name.getBytes()),
                name,
                TeamId.RED,
                0,
                0,
                0,
                0,
                "빌더",
                teamLeader
        );
    }

    @Test
    void leaderTargetCandidatesIncludeAquaBeyondTheFourColumnLayout() {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                new GameArena(Map.of())
        );
        game.teams().values().forEach(team -> team.activate());

        assertEquals(
                List.of(TeamId.BLUE, TeamId.GREEN, TeamId.YELLOW, TeamId.PURPLE, TeamId.AQUA),
                SemionDialogService.leaderTargetCandidates(game, TeamId.RED).stream()
                        .map(team -> team.id())
                        .toList()
        );
    }

    private static TowerUpgradeOption option(String id) {
        return new TowerUpgradeOption(id, id, FutureAgencyTowers.REBUILDER, 0);
    }

}
