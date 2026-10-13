package kim.biryeong.semiontd.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.core.BlockPos;

final class CommandGameStatus {
    private CommandGameStatus() {
    }

    static List<String> statusLines(SemionGameManager gameManager) {
        List<String> lines = new ArrayList<>();
        SemionGame game = gameManager.activeGame().orElse(null);
        boolean lobbyLoaded = gameManager.lobbyWorld().isPresent();
        if (game == null) {
            lines.add("상태 activeGame=false, phase=NONE, matchMode="
                    + gameManager.matchMode()
                    + ", lobbyLoaded=" + lobbyLoaded
                    + ", arenaLoaded=false");
            gameManager.lastMatchResult().ifPresent(result -> lines.add("최근 결과 winners="
                    + winnersText(result)
                    + ", finalRound=" + result.finalRound()
                    + ", participants=" + result.participantCount()
                    + ", spectators=" + result.spectatorIds().size()));
            return lines;
        }

        lines.add("상태 activeGame=true, phase="
                + game.phase()
                + ", round=" + game.currentRound()
                + ", matchMode=" + gameManager.matchMode()
                + ", rosterLocked=" + game.rosterLocked());
        lines.add("운영 상태 ready="
                + game.readyPlayerCount()
                + ", activeParticipants=" + game.players().size()
                + ", spectators=" + game.spectatorCount()
                + ", lobbyLoaded=" + lobbyLoaded
                + ", arenaLoaded=" + loadedArenaCount(game) + "/" + TeamId.values().length);
        lines.addAll(teamStatusLines(game));
        return lines;
    }

    static List<String> teamStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        for (SemionTeam team : game.teams().values()) {
            lines.add("팀 " + team.id()
                    + " active=" + team.active()
                    + ", eliminated=" + team.eliminated()
                    + ", arenaLoaded=" + game.arena().teamArena(team.id()).isPresent()
                    + ", players=" + team.memberIds().size()
                    + ", lanes=" + team.laneGroup().lanes().size()
                    + ", boss=" + bossHealthStatus(team));
        }
        return lines;
    }

    static List<String> laneStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        for (SemionTeam team : game.teams().values()) {
            if (!team.active()) {
                continue;
            }
            for (PlayerLane lane : team.laneGroup().lanes()) {
                SemionPlayer owner = game.players().get(lane.ownerPlayer());
                BlockPos areaMin = lane.laneLayout().laneArea().min();
                BlockPos areaMax = lane.laneLayout().laneArea().max();
                BlockPos towerSample = centerBlockPos(areaMin, areaMax);
                lines.add("라인 " + team.id()
                        + "#" + lane.laneId()
                        + " player=" + (owner == null ? lane.ownerPlayer() : owner.name())
                        + ", towerSample=" + blockPosText(towerSample)
                        + ", laneArea=" + blockPosText(areaMin) + ".." + blockPosText(areaMax)
                        + ", monsters=" + lane.activeMonsters().size()
                        + ", towers=" + lane.towers().size());
            }
        }
        if (lines.isEmpty()) {
            lines.add("활성 라인 없음");
        }
        return lines;
    }

    static List<String> playerStatusLines(SemionGame game) {
        List<String> lines = new ArrayList<>();
        if (game.players().isEmpty()) {
            lines.add("참가자 없음");
        } else {
            game.players().values().stream()
                    .sorted(java.util.Comparator.comparing(SemionPlayer::name))
                    .forEach(player -> lines.add("참가자 "
                            + player.name()
                            + " uuid=" + player.uuid()
                            + ", team=" + player.teamId()
                            + ", lane=" + player.laneId()
                            + ", eliminated=" + game.teams().get(player.teamId()).eliminated()));
        }

        List<UUID> spectators = game.matchSpectatorIds().stream()
                .filter(spectatorId -> !game.players().containsKey(spectatorId))
                .sorted()
                .toList();
        if (spectators.isEmpty()) {
            lines.add("관전자 없음");
        } else {
            spectators.forEach(spectatorId -> lines.add("관전자 uuid=" + spectatorId));
        }
        return lines;
    }

    private static int loadedArenaCount(SemionGame game) {
        int loaded = 0;
        for (TeamId teamId : TeamId.values()) {
            if (game.arena().teamArena(teamId).isPresent()) {
                loaded++;
            }
        }
        return loaded;
    }

    private static String bossHealthStatus(SemionTeam team) {
        if (team.eliminated()) {
            return "ELIMINATED";
        }
        return Math.round(team.laneGroup().boss().health())
                + "/"
                + Math.round(team.laneGroup().boss().maxHealth());
    }

    private static String blockPosText(BlockPos blockPos) {
        return blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
    }

    private static BlockPos centerBlockPos(BlockPos min, BlockPos max) {
        return new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2)
        );
    }

    private static String winnersText(MatchResult result) {
        if (result.winningTeams().isEmpty()) {
            return "none";
        }
        return result.winningTeams().stream()
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

}
