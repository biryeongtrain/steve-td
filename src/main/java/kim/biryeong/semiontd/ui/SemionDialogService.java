package kim.biryeong.semiontd.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

public final class SemionDialogService {
    private static final int BODY_WIDTH = 360;

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

    private void show(ServerPlayer player, String title, String body) {
        Dialog dialog = new NoticeDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        List.<DialogBody>of(new PlainMessage(Component.literal(body), BODY_WIDTH)),
                        List.of()
                ),
                NoticeDialog.DEFAULT_ACTION
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
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
