package kim.biryeong.semiontd.ui;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonShop;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerBehavior;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import net.kyori.adventure.platform.modcommon.impl.NonWrappingComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
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
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final int BODY_WIDTH = 360;
    private static final int BUTTON_WIDTH = 180;
    private static final int COMPACT_BUTTON_WIDTH = 118;
    private static final int SUMMON_BUTTON_WIDTH = 82;

    public void showGameStatus(ServerPlayer player, SemionGame game) {
        StringBuilder body = new StringBuilder();
        body.append("라운드: ").append(game.currentRound()).append('\n');
        body.append("단계: ").append(phaseLabel(game.phase())).append('\n');
        body.append("플레이어: ").append(game.players().size()).append('\n');
        body.append("관전자: ").append(game.spectatorCount()).append('\n');
        body.append('\n');
        body.append("팀 상태\n");
        for (SemionTeam team : game.teams().values()) {
            if (!team.active()) {
                continue;
            }
            body.append(" - ")
                    .append(team.id().name())
                    .append(": ")
                    .append(team.eliminated() ? "탈락" : "생존")
                    .append(", 인원=")
                    .append(team.memberIds().size())
                    .append(", 보스HP=")
                    .append(Math.round(team.laneGroup().boss().health()))
                    .append('\n');
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer != null) {
            PlayerEconomy economy = semionPlayer.economy();
            body.append('\n');
            body.append("내 정보\n");
            body.append("팀: ").append(semionPlayer.teamId().name()).append('\n');
            body.append("라인: ").append(semionPlayer.laneId()).append('\n');
            body.append("다이아: ").append(economy.diamond()).append('\n');
            body.append("에메랄드: ").append(economy.emerald()).append('\n');
            body.append("수입: ").append(economy.income()).append('\n');
            body.append("에메랄드/초: ").append(economy.emeraldPerSec()).append('\n');
        }

        show(player, "세미온 TD 상태", body.toString());
    }

    public void showMatchResult(
            ServerPlayer player,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        MutableComponent body = mutableMiniMessage("<gradient:#facc15:#22d3ee><bold>경기 결과</bold></gradient>\n")
                .append(miniMessage("<gray>최종 라운드</gray> <white>" + matchResult.finalRound() + "</white>\n"))
                .append(miniMessage("<gray>승리 팀</gray> <gold>" + teamList(matchResult.winningTeams()) + "</gold>\n\n"));

        List<MatchParticipantResult> orderedParticipants = matchResult.participants().stream()
                .sorted(participantComparator())
                .toList();

        body = body.append(miniMessage("<yellow><bold>참가자 기록</bold></yellow>\n"));
        int avatarOffset = 0;
        for (MatchParticipantResult participant : orderedParticipants) {
            var stats = participant.stats();
            body = body.append(avatarComponent(participant, avatarOffset++));
            body = body.append(miniMessage(" <white>" + participant.playerName() + "</white>"
                    + " <dark_gray>[</dark_gray>"
                    + (participant.winner() ? "<gold>승리</gold>" : "<gray>패배</gray>")
                    + " <aqua>" + participant.teamId().name() + "</aqua>"
                    + "<dark_gray>]</dark_gray>"
                    + " <gray>처치</gray> <red>" + stats.monsterKills() + "</red>"
                    + " <gray>수입</gray> <green>" + stats.finalIncome() + "</green>"
                    + " <gray>소환</gray> <light_purple>" + stats.summonedMonsters() + "</light_purple>"
                    + " <gray>처치다이아</gray> <aqua>" + stats.killMinerals() + "</aqua>"));
            MatchProgressionReward reward = rewards.get(participant.playerId());
            if (reward != null) {
                body = body.append(miniMessage(" <gray>꾸미기재화</gray> <gold>+" + reward.currencyAwarded() + "</gold>"));
            }
            body = body.append(Component.literal("\n"));
        }

        List<MatchParticipantResult> losers = matchResult.participants().stream()
                .filter(participant -> !participant.winner())
                .sorted(participantComparator())
                .toList();
        body = body.append(miniMessage("\n<red><bold>탈락 플레이어</bold></red>\n"));
        if (losers.isEmpty()) {
            body = body.append(miniMessage("<gray>- 없음</gray>\n"));
        } else {
            for (MatchParticipantResult participant : losers) {
                body = body.append(miniMessage("<gray>- </gray><white>" + participant.playerName() + "</white>"
                        + " <dark_gray>[</dark_gray><aqua>" + participant.teamId().name() + "</aqua><dark_gray>]</dark_gray>\n"));
            }
        }

        show(player, "세미온 TD 결과", body);
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

    public void showTowerControl(ServerPlayer player, SemionGame game) {
        var semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer == null) {
            show(player, "세미온 TD 타워", "<red>현재 게임 참가자가 아닙니다.</red>");
            return;
        }

        PlayerEconomy economy = semionPlayer.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 관리</bold></gradient>\n");
        body.append("<gray>라인</gray> <white>").append(semionPlayer.teamId()).append(" #").append(semionPlayer.laneId()).append("</white>\n");
        body.append("<gray>다이아</gray> <aqua>").append(economy.diamond()).append("</aqua> ");
        body.append("<gray>에메랄드</gray> <green>").append(economy.emerald()).append("</green>\n");
        body.append("<gray>에메랄드/초</gray> <green>").append(economy.emeraldPerSec()).append("</green>");
        body.append(" <dark_gray>|</dark_gray> <gray>다음 인컴 업글</gray> <gold>")
                .append(nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대")
                .append("</gold>\n\n");

        Tower selectedTower = game.playerLane(player.getUUID())
                .map(lane -> lane.towerAt(GridPosition.from(player.blockPosition())))
                .orElse(null);
        if (selectedTower != null) {
            body.append("<yellow>현재 위치 타워</yellow> <white>").append(selectedTower.type().displayName()).append("</white>\n");
            body.append("<gray>레벨</gray> <white>").append(selectedTower.level()).append("</white>");
            body.append(" <gray>체력</gray> <red>").append(Math.round(selectedTower.health())).append("</red>");
            body.append("<dark_gray>/</dark_gray><red>").append(Math.round(selectedTower.maxHealth())).append("</red>");
            body.append(" <gray>판매 환불</gray> <gold>").append(selectedTower.sellRefundAmount()).append("</gold>\n\n");
        }

        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerService.availableTowers(game, player.getUUID());
        JobContext jobContext = new JobContext(game, semionPlayer);
        SemionJob job = semionPlayer.job().orElse(JobRegistry.defaultJob());
        List<TowerUpgradeOption> upgrades = selectedTower == null
                ? List.of()
                : ProductionTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (!upgrades.isEmpty()) {
            body.append("<gray>아래 버튼에서 업그레이드 분기를 선택하세요.</gray>\n");
        } else if (selectedTower != null) {
            body.append("<gray>현재 위치 타워는 더 이상 업그레이드할 수 없습니다.</gray>\n");
        } else if (entries.isEmpty()) {
            body.append("<red>현재 직업으로 사용할 수 있는 타워가 없습니다.</red>\n");
        } else {
            body.append("<gray>건설 후보</gray> <white>").append(entries.size()).append("</white>");
            body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (selectedTower == null) {
            for (ProductionTowerCatalog.CatalogEntry entry : entries) {
                long mineralCost = Math.max(0, job.modifyTowerMineralCost(jobContext, entry.type(), entry.type().mineralCost()));
                actions.add(towerButton(entry, mineralCost, economy.diamond() >= mineralCost));
            }
        } else {
            for (TowerUpgradeOption option : upgrades) {
                actions.add(actionButton(
                        option.displayName(),
                        "/semiontd tower upgrade " + option.id(),
                        upgradeTooltip(option),
                        COMPACT_BUTTON_WIDTH
                ));
            }
        }
        actions.add(actionButton("인컴 업그레이드", "/semiontd emeraldup", "에메랄드 생산량을 올립니다."));
        actions.add(actionButton("상태 보기", "/semiontd ui", "현재 경기 상태를 엽니다."));
        showActions(player, "세미온 TD 타워", body.toString(), actions, 3);
    }

    public void showTowerDetails(ServerPlayer player, SemionGame game, Tower tower) {
        if (tower == null) {
            show(player, "세미온 TD 타워", "<red>타워 정보를 찾을 수 없습니다.</red>");
            return;
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        boolean ownedByPlayer = tower.ownerPlayer().equals(player.getUUID());
        boolean sameLane = semionPlayer != null
                && semionPlayer.teamId() == tower.teamId()
                && semionPlayer.laneId() == tower.laneId();

        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 상세 정보</bold></gradient>\n");
        body.append("<yellow>").append(tower.type().displayName()).append("</yellow>\n");
        body.append("<gray>팀</gray> <white>").append(tower.teamId()).append("</white>");
        body.append(" <gray>라인</gray> <white>#").append(tower.laneId()).append("</white>\n");
        body.append("<gray>위치</gray> <white>")
                .append(tower.position().x()).append(", ")
                .append(tower.position().y()).append(", ")
                .append(tower.position().z()).append("</white>\n");
        body.append("<gray>레벨</gray> <white>").append(tower.level()).append("</white>");
        body.append(" <gray>체력</gray> <red>").append(Math.round(tower.health())).append("</red>");
        body.append("<dark_gray>/</dark_gray><red>").append(Math.round(tower.maxHealth())).append("</red>\n");
        body.append("<gray>피해</gray> <red>").append(Math.round(tower.type().damage())).append("</red>");
        body.append(" <gray>사거리</gray> <aqua>").append(oneDecimal(tower.type().range())).append("</aqua>");
        body.append(" <gray>공속</gray> <white>").append(tower.type().attackIntervalTicks()).append("틱</white>\n");
        body.append("<gray>판매 환불</gray> <gold>").append(tower.sellRefundAmount()).append(" 다이아</gold>\n");
        ProductionTowerCatalog.behavior(tower.type()).ifPresent(behavior -> {
            body.append("<gray>특성</gray> <white>").append(behavior.mechanicName()).append("</white>");
            body.append(" <gray>스플래시</gray> <white>").append(oneDecimal(behavior.splashRadius())).append("</white>\n");
        });
        for (String line : tower.type().description()) {
            body.append("<dark_gray>-</dark_gray> <gray>").append(line).append("</gray>\n");
        }
        if (!ownedByPlayer) {
            body.append("\n<red>자신이 설치한 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        } else if (!sameLane) {
            body.append("\n<red>현재 담당 라인의 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (ownedByPlayer && sameLane) {
            for (TowerUpgradeOption option : ProductionTowerService.availableUpgrades(game, player.getUUID(), tower.position())) {
                actions.add(actionButton(
                        option.displayName(),
                        "/semiontd tower upgrade "
                                + option.id() + " "
                                + tower.position().x() + " "
                                + tower.position().y() + " "
                                + tower.position().z(),
                        upgradeTooltip(option),
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
        body.append("<gray>건설 후보</gray> <white>")
                .append(ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count())
                .append("</white>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n");

        List<ActionButton> actions = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(entry -> towerButton(entry, entry.type().mineralCost(), true))
                .toList();
        showActions(player, "세미온 TD 타워", body.toString(), actions, 3);
    }

    public void showSummonShop(ServerPlayer player, SemionGame game) {
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append("<gray>티어별 역할 분포입니다. 상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n\n");
        body.append(summonTierTable(game.summonShop().all()));

        List<ActionButton> actions = game.summonShop().all().stream()
                .sorted(Comparator.comparing(SummonMonsterType::tier)
                        .thenComparing(type -> primaryRole(type).ordinal())
                        .thenComparingLong(SummonMonsterType::gasCost)
                        .thenComparing(SummonMonsterType::displayName))
                .map(type -> actionButton(
                        summonButtonLabel(type),
                        "/semiontd summon " + type.id(),
                        summonTooltip(type),
                        SUMMON_BUTTON_WIDTH
                ))
                .toList();
        showActions(player, "세미온 TD 소환", body.toString(), actions, 5);
    }

    public void showDebugSummonShop(ServerPlayer player) {
        SummonShop summonShop = new SummonShop();
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append("<gray>티어별 역할 분포입니다. 상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n\n");
        body.append(summonTierTable(summonShop.all()));

        List<ActionButton> actions = summonShop.all().stream()
                .sorted(Comparator.comparing(SummonMonsterType::tier)
                        .thenComparing(type -> primaryRole(type).ordinal())
                        .thenComparingLong(SummonMonsterType::gasCost)
                        .thenComparing(SummonMonsterType::displayName))
                .map(type -> actionButton(
                        summonButtonLabel(type),
                        "/semiontd summon " + type.id(),
                        summonTooltip(type),
                        SUMMON_BUTTON_WIDTH
                ))
                .toList();
        showActions(player, "세미온 TD 소환", body.toString(), actions, 5);
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
                        List.of(new HeaderMessage(miniMessage(body), BODY_WIDTH)),
                        List.of()
                ),
                actions,
                Optional.of(actionButton("닫기", "", "창을 닫습니다.")),
                columns
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
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

    private static ActionButton towerButton(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable) {
        return actionButton(
                towerButtonLabel(entry, affordable),
                "/semiontd tower build " + entry.type().id(),
                towerTooltip(entry, mineralCost, affordable),
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

    public static String jobSelectionCommand(SemionJob job) {
        return "/semiontd job select " + job.id().getPath();
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

    public static Component towerButtonLabel(ProductionTowerCatalog.CatalogEntry entry, boolean affordable) {
        return Component.literal(entry.type().displayName())
                .withStyle(affordable ? ChatFormatting.WHITE : ChatFormatting.RED);
    }

    private static Component towerTooltip(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable) {
        var type = entry.type();
        var behavior = entry.behavior();
        String upgradeSummary = type.upgradeOptions().isEmpty()
                ? "업그레이드 없음"
                : type.upgradeOptions().stream()
                .map(option -> option.displayName() + " -> " + option.targetType().displayName())
                .collect(Collectors.joining(" / "));
        MutableComponent tooltip = Component.literal(type.displayName())
                .append(Component.literal("\n비용 " + mineralCost + " 다이아" + (affordable ? "" : " (부족)")))
                .append(Component.literal("\n분류 " + towerCategoryLabel(type.category()) + " / 티어 " + entry.tier()))
                .append(Component.literal("\n체력 " + Math.round(type.maxHealth()) + " / 어그로 " + type.aggroPriority()))
                .append(Component.literal("\n피해 " + oneDecimal(type.damage()) + " / 사거리 " + oneDecimal(type.range())))
                .append(Component.literal("\n공속 " + type.attackIntervalTicks() + "틱 (" + attacksPerSecond(type.attackIntervalTicks()) + "회/초)"))
                .append(Component.literal("\n스플래시 " + oneDecimal(behavior.splashRadius()) + "칸 x" + oneDecimal(behavior.splashDamageMultiplier())))
                .append(Component.literal("\n팩션 " + factionLabel(behavior.faction()) + " / 특성 " + behavior.mechanicName()))
                .append(Component.literal("\n분기 " + upgradeSummary));
        appendDescription(tooltip, type.description());
        appendTowerBehaviorDetails(tooltip, behavior);
        return tooltip;
    }

    private static Component upgradeTooltip(TowerUpgradeOption option) {
        Optional<ProductionTowerCatalog.CatalogEntry> target = ProductionTowerCatalog.entry(option.targetType());
        if (target.isEmpty()) {
            return Component.literal("대상 타워를 찾을 수 없습니다.\n비용 " + option.mineralCost() + " 다이아");
        }
        var entry = target.get();
        var type = entry.type();
        MutableComponent tooltip = Component.literal(option.displayName())
                .append(Component.literal("\n대상 " + type.displayName()))
                .append(Component.literal("\n비용 " + option.mineralCost() + " 다이아"))
                .append(Component.literal("\n체력 " + Math.round(type.maxHealth()) + " / 어그로 " + type.aggroPriority()))
                .append(Component.literal("\n피해 " + oneDecimal(type.damage()) + " / 사거리 " + oneDecimal(type.range())))
                .append(Component.literal("\n공속 " + type.attackIntervalTicks() + "틱 (" + attacksPerSecond(type.attackIntervalTicks()) + "회/초)"))
                .append(Component.literal("\n스플래시 " + oneDecimal(entry.behavior().splashRadius()) + "칸 x" + oneDecimal(entry.behavior().splashDamageMultiplier())))
                .append(Component.literal("\n특성 " + entry.behavior().mechanicName()));
        appendDescription(tooltip, type.description());
        appendTowerBehaviorDetails(tooltip, entry.behavior());
        return tooltip;
    }

    private static String summonTierTable(java.util.Collection<SummonMonsterType> summons) {
        Map<SummonTier, Map<SummonRole, List<SummonMonsterType>>> grouped = new EnumMap<>(SummonTier.class);
        for (SummonTier tier : SummonTier.values()) {
            grouped.put(tier, new EnumMap<>(SummonRole.class));
        }
        for (SummonMonsterType type : summons) {
            grouped.get(type.tier())
                    .computeIfAbsent(primaryRole(type), ignored -> new ArrayList<>())
                    .add(type);
        }

        SummonRole[] columns = {SummonRole.SWARM, SummonRole.RUSH, SummonRole.TANK, SummonRole.SIEGE, SummonRole.SUPPORT, SummonRole.DISRUPTOR};
        StringBuilder table = new StringBuilder();
        table.append("<dark_gray>|</dark_gray> <gray>티어</gray> ");
        for (SummonRole role : columns) {
            table.append("<dark_gray>|</dark_gray> <aqua>").append(roleLabel(role)).append("</aqua> ");
        }
        table.append("<dark_gray>|</dark_gray>\n");
        for (SummonTier tier : SummonTier.values()) {
            Map<SummonRole, List<SummonMonsterType>> row = grouped.getOrDefault(tier, Map.of());
            boolean hasAny = row.values().stream().anyMatch(list -> !list.isEmpty());
            if (!hasAny) {
                continue;
            }
            table.append("<dark_gray>|</dark_gray> <gold>").append(tier.name()).append("</gold> ");
            for (SummonRole role : columns) {
                List<SummonMonsterType> values = row.getOrDefault(role, List.of());
                table.append("<dark_gray>|</dark_gray> ");
                if (values.isEmpty()) {
                    table.append("<gray>-</gray> ");
                } else {
                    table.append("<white>").append(values.size()).append("</white>");
                    table.append("<gray>x</gray> ");
                }
            }
            table.append("<dark_gray>|</dark_gray>\n");
        }
        return table.toString();
    }

    private static String summonButtonLabel(SummonMonsterType type) {
        return type.tier().name() + " " + type.displayName().split("\\s+", 2)[0];
    }

    private static Component summonTooltip(SummonMonsterType type) {
        MutableComponent tooltip = Component.literal(type.displayName())
                .append(Component.literal("\n티어 " + type.tier().name() + " / 역할 " + roleList(type)))
                .append(Component.literal("\n비용 " + type.gasCost() + " 에메랄드 / 수입 +" + type.incomeGain()))
                .append(Component.literal("\n수입 효율 " + oneDecimal(type.incomeRatio() * 100.0) + "% / 처치 보상 " + type.mineralReward() + " 다이아"))
                .append(Component.literal("\n체력 " + Math.round(type.maxHealth()) + " / 방어 " + oneDecimal(type.armor()) + " / 저항 " + oneDecimal(type.resistance())))
                .append(Component.literal("\n공격 " + oneDecimal(type.attackDamage()) + " / 방식 " + attackKindLabel(type.attackKind()) + " / 피해 " + damageTypeLabel(type.damageType())))
                .append(Component.literal("\n공속 13틱 (" + attacksPerSecond(13) + "회/초)"))
                .append(Component.literal("\n크기 " + oneDecimal(type.dimensions().width()) + "x" + oneDecimal(type.dimensions().height())))
                .append(Component.literal("\n타겟 우선도 " + oneDecimal(type.targetRolePriority())))
                .append(Component.literal("\n능력 발동 " + abilityActivationList(type)));
        appendDescription(tooltip, type.description());
        return tooltip;
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

    private static void appendTowerBehaviorDetails(MutableComponent tooltip, ProductionTowerBehavior behavior) {
        tooltip.append(Component.literal("\n\n특성 상세"));
        if (behavior.maxStacks() > 0) {
            tooltip.append(Component.literal("\n- 최대 " + behavior.maxStacks() + "중첩"));
            if (behavior.damagePerStack() > 0.0) {
                tooltip.append(Component.literal(" / 중첩당 피해 +" + oneDecimal(behavior.damagePerStack() * 100.0) + "%"));
            }
            if (behavior.attackSpeedPerStack() > 0.0) {
                tooltip.append(Component.literal(" / 중첩당 공속 +" + oneDecimal(behavior.attackSpeedPerStack() * 100.0) + "%"));
            }
            tooltip.append(Component.literal("\n- 중첩 조건 "
                    + (behavior.stackOnHit() ? "명중" : "")
                    + (behavior.stackOnHit() && behavior.stackOnKill() ? " + " : "")
                    + (behavior.stackOnKill() ? "처치" : "")));
        }
        if (behavior.killSplashRadius() > 0.0 && behavior.killSplashDamageMultiplier() > 0.0) {
            tooltip.append(Component.literal("\n- 처치 시 추가 폭발 " + oneDecimal(behavior.killSplashRadius())
                    + "칸 x" + oneDecimal(behavior.killSplashDamageMultiplier())));
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

    private static String towerCategoryLabel(TowerCategory category) {
        return switch (category) {
            case DIRECT -> "공격";
            case SUPPORT -> "지원";
            case PRODUCER -> "생산";
            case SUMMONER -> "소환";
        };
    }

    private static String attackKindLabel(AttackKind attackKind) {
        return switch (attackKind) {
            case MELEE -> "근접";
            case RANGED -> "원거리";
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

    private static String factionLabel(kim.biryeong.semiontd.tower.TowerFaction faction) {
        return switch (faction) {
            case VILLAGER -> "주민";
            case UNDEAD -> "언데드";
            case BEAST -> "동물";
        };
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static Component miniMessage(String text) {
        return NonWrappingComponentSerializer.INSTANCE.serialize(MINI_MESSAGE.deserialize(text));
    }

    private static MutableComponent mutableMiniMessage(String text) {
        return Component.empty().append(miniMessage(text));
    }

    private static Component avatarComponent(MatchParticipantResult participant, int offset) {
        Component avatar = AvatarRendererMod.computeNow(participant.playerName(), offset, false);
        return avatar == null ? Component.empty() : avatar;
    }

    private static long nextGasUpgradeCost(SemionGame game, PlayerEconomy economy) {
        var config = game.economyConfig().gasProduction();
        if (economy.emeraldProductionUpgradeCount() >= config.maxUpgradeCount()) {
            return -1;
        }
        return config.upgradeCost(economy.emeraldProductionUpgradeCount());
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

    private static String phaseLabel(RoundPhase phase) {
        return switch (phase) {
            case WAITING -> "대기";
            case PREPARE_AND_SUMMON -> "준비/소환";
            case LANE_WAVE -> "웨이브";
            case ROUND_PAYOUT -> "정산";
            case ENDED -> "종료";
        };
    }
}
