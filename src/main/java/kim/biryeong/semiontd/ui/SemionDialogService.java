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
        body.append("Round: ").append(game.currentRound()).append('\n');
        body.append("Phase: ").append(phaseLabel(game.phase())).append('\n');
        body.append("Players: ").append(game.players().size()).append('\n');
        body.append("Spectators: ").append(game.spectatorCount()).append('\n');
        body.append('\n');
        body.append("Teams\n");
        for (SemionTeam team : game.teams().values()) {
            if (!team.active()) {
                continue;
            }
            body.append(" - ")
                    .append(team.id().name())
                    .append(": ")
                    .append(team.eliminated() ? "ELIMINATED" : "ALIVE")
                    .append(", players=")
                    .append(team.memberIds().size())
                    .append(", bossHp=")
                    .append(Math.round(team.laneGroup().boss().health()))
                    .append('\n');
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer != null) {
            PlayerEconomy economy = semionPlayer.economy();
            body.append('\n');
            body.append("Your Economy\n");
            body.append("Mineral: ").append(economy.mineral()).append('\n');
            body.append("Gas: ").append(economy.gas()).append('\n');
            body.append("Income: ").append(economy.income()).append('\n');
            body.append("Gas/sec: ").append(economy.gasPerSec()).append('\n');
        }

        show(player, "Semion TD Status", body.toString());
    }

    public void showMatchResult(
            ServerPlayer player,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        StringBuilder body = new StringBuilder();
        body.append("Final round: ").append(matchResult.finalRound()).append('\n');
        body.append("Winner team: ").append(teamList(matchResult.winningTeams())).append('\n');
        body.append('\n');

        List<MatchParticipantResult> losers = matchResult.participants().stream()
                .filter(participant -> !participant.winner())
                .sorted(participantComparator())
                .toList();
        body.append("Defeated players\n");
        if (losers.isEmpty()) {
            body.append(" - none\n");
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
        body.append("Player stats\n");
        for (MatchParticipantResult participant : matchResult.participants().stream()
                .sorted(participantComparator())
                .toList()) {
            var stats = participant.stats();
            body.append(" - ")
                    .append(participant.playerName())
                    .append(" [")
                    .append(participant.teamId().name())
                    .append(participant.winner() ? ", WIN" : ", LOSS")
                    .append("]")
                    .append(": kills=")
                    .append(stats.monsterKills())
                    .append(", income=")
                    .append(stats.finalIncome())
                    .append(", summons=")
                    .append(stats.summonedMonsters())
                    .append(", killMinerals=")
                    .append(stats.killMinerals());

            MatchProgressionReward reward = rewards.get(participant.playerId());
            if (reward != null) {
                body.append(", cosmetic+=").append(reward.currencyAwarded());
            }
            body.append('\n');
        }

        show(player, "Semion TD Result", body.toString());
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
            return "none";
        }
        return teams.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
    }

    private static Comparator<MatchParticipantResult> participantComparator() {
        return Comparator.comparing(MatchParticipantResult::teamId)
                .thenComparing(MatchParticipantResult::playerName);
    }

    private static String phaseLabel(RoundPhase phase) {
        return switch (phase) {
            case WAITING -> "Waiting";
            case PREPARE_AND_SUMMON -> "Prepare / Summon";
            case LANE_WAVE -> "Wave";
            case ROUND_PAYOUT -> "Payout";
            case ENDED -> "Ended";
        };
    }
}
