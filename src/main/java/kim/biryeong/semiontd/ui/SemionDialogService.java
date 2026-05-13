package kim.biryeong.semiontd.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import net.kyori.adventure.platform.modcommon.impl.NonWrappingComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
        StringBuilder body = new StringBuilder();
        body.append("최종 라운드: ").append(matchResult.finalRound()).append('\n');
        body.append("승리 팀: ").append(teamList(matchResult.winningTeams())).append('\n');
        body.append('\n');

        List<MatchParticipantResult> losers = matchResult.participants().stream()
                .filter(participant -> !participant.winner())
                .sorted(participantComparator())
                .toList();
        body.append("탈락 플레이어\n");
        if (losers.isEmpty()) {
            body.append(" - 없음\n");
        } else {
            for (MatchParticipantResult participant : losers) {
                body.append(" - ")
                        .append(participant.playerName())
                        .append(" [")
                        .append(participant.teamId().name())
                        .append("]\n");
            }
        }

        body.append('\n');
        body.append("참가자 기록\n");
        for (MatchParticipantResult participant : matchResult.participants().stream()
                .sorted(participantComparator())
                .toList()) {
            var stats = participant.stats();
            body.append(" - ")
                    .append(participant.playerName())
                    .append(" [")
                    .append(participant.teamId().name())
                    .append(participant.winner() ? ", 승리" : ", 패배")
                    .append("]")
                    .append(": 처치=")
                    .append(stats.monsterKills())
                    .append(", 수입=")
                    .append(stats.finalIncome())
                    .append(", 소환=")
                    .append(stats.summonedMonsters())
                    .append(", 처치다이아=")
                    .append(stats.killMinerals());

            MatchProgressionReward reward = rewards.get(participant.playerId());
            if (reward != null) {
                body.append(", 꾸미기재화+=").append(reward.currencyAwarded());
            }
            body.append('\n');
        }

        show(player, "세미온 TD 결과", body.toString());
    }

    public void showLastResult(ServerPlayer player, MatchResult matchResult) {
        showMatchResult(player, matchResult, Map.of());
    }

    public void showJobSelection(ServerPlayer player, SemionGame game) {
        SemionJob currentJob = game.selectedJobOrDefault(player.getUUID());
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#67e8f9:#a78bfa><bold>직업 선택</bold></gradient>\n");
        body.append("<gray>현재 선택</gray> <yellow>").append(currentJob.displayName().getString()).append("</yellow>\n\n");
        for (SemionJob job : JobRegistry.all()) {
            body.append("<aqua>").append(job.displayName().getString()).append("</aqua>\n");
            for (Component line : job.description()) {
                body.append("<gray>- ").append(line.getString()).append("</gray>\n");
            }
            body.append('\n');
        }

        List<ActionButton> actions = JobRegistry.all().stream()
                .map(job -> actionButton(
                        job.displayName().getString(),
                        "/semiontd job select " + job.id(),
                        "이 직업으로 플레이합니다."
                ))
                .toList();
        showActions(player, "세미온 TD 직업", body.toString(), actions);
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
        if (entries.isEmpty()) {
            body.append("<red>현재 직업으로 사용할 수 있는 타워가 없습니다.</red>\n");
        } else {
            body.append("<gray>현재 위치에서 설치할 타워를 선택하세요.</gray>\n");
            for (ProductionTowerCatalog.CatalogEntry entry : entries) {
                body.append("<aqua>").append(entry.type().displayName()).append("</aqua>")
                        .append(" <gray>비용</gray> <gold>").append(entry.type().mineralCost()).append("</gold>")
                        .append(" <gray>사거리</gray> <white>").append(Math.round(entry.type().range())).append("</white>")
                        .append(" <gray>스플래시</gray> <white>").append(entry.behavior().splashRadius()).append("</white>")
                        .append(" <gray>특성</gray> <green>").append(entry.behavior().mechanicName()).append("</green>\n");
            }
        }

        java.util.ArrayList<ActionButton> actions = new java.util.ArrayList<>();
        for (ProductionTowerCatalog.CatalogEntry entry : entries) {
            actions.add(actionButton(
                    entry.type().displayName(),
                    "/semiontd tower build " + entry.type().id(),
                    "현재 위치에 설치합니다."
            ));
        }
        actions.add(actionButton("인컴 업그레이드", "/semiontd emeraldup", "에메랄드 생산량을 올립니다."));
        actions.add(actionButton("현재 타워 판매", "/semiontd tower sell", "현재 위치의 내 타워를 판매합니다."));
        actions.add(actionButton("상태 보기", "/semiontd ui", "현재 경기 상태를 엽니다."));
        showActions(player, "세미온 TD 타워", body.toString(), actions);
    }

    public void showSummonShop(ServerPlayer player, SemionGame game) {
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append("<gray>에메랄드를 사용해 상대 라인에 압박 몹을 보냅니다.</gray>\n\n");
        for (SummonMonsterType type : game.summonShop().all()) {
            body.append("<light_purple>").append(type.displayName()).append("</light_purple>")
                    .append(" <gray>비용</gray> <green>").append(type.gasCost()).append("</green>")
                    .append(" <gray>수입</gray> <gold>+").append(type.incomeGain()).append("</gold>")
                    .append(" <gray>체력</gray> <white>").append(Math.round(type.maxHealth())).append("</white>\n");
        }

        List<ActionButton> actions = game.summonShop().all().stream()
                .map(type -> actionButton(
                        type.displayName(),
                        "/semiontd summon " + type.id(),
                        "이 견제 몹을 소환합니다."
                ))
                .toList();
        showActions(player, "세미온 TD 소환", body.toString(), actions);
    }

    private void show(ServerPlayer player, String title, String body) {
        Dialog dialog = new NoticeDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        List.<DialogBody>of(new PlainMessage(miniMessage(body), BODY_WIDTH)),
                        List.of()
                ),
                NoticeDialog.DEFAULT_ACTION
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private void showActions(ServerPlayer player, String title, String body, List<ActionButton> actions) {
        Dialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        List.<DialogBody>of(new PlainMessage(miniMessage(body), BODY_WIDTH)),
                        List.of()
                ),
                actions,
                Optional.of(actionButton("닫기", "", "창을 닫습니다.")),
                2
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private static ActionButton actionButton(String label, String command, String tooltip) {
        Optional<net.minecraft.server.dialog.action.Action> action = command == null || command.isBlank()
                ? Optional.empty()
                : Optional.of(new StaticAction(new ClickEvent.RunCommand(command)));
        return new ActionButton(
                new CommonButtonData(Component.literal(label), Optional.of(Component.literal(tooltip)), BUTTON_WIDTH),
                action
        );
    }

    private static Component miniMessage(String text) {
        return NonWrappingComponentSerializer.INSTANCE.serialize(MINI_MESSAGE.deserialize(text));
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
