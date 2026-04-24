package kim.biryeong.semionTd.game;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

public final class VanillaTeamBridge {
    private static final String TEAM_PREFIX = "semion_";
    private static final String SPECTATOR_TEAM = "semion_spectator";

    private VanillaTeamBridge() {
    }

    public static void ensureTeams(MinecraftServer server) {
        for (TeamId teamId : TeamId.values()) {
            getOrCreateTeam(server.getScoreboard(), teamId);
        }
        getOrCreateSpectatorTeam(server.getScoreboard());
    }

    public static void assignPlayer(MinecraftServer server, ServerPlayer player, TeamId teamId) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = getOrCreateTeam(scoreboard, teamId);
        String playerName = player.getGameProfile().getName();
        scoreboard.removePlayerFromTeam(playerName);
        scoreboard.addPlayerToTeam(playerName, team);
    }

    public static void assignSpectator(MinecraftServer server, ServerPlayer player) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = getOrCreateSpectatorTeam(scoreboard);
        String playerName = player.getGameProfile().getName();
        scoreboard.removePlayerFromTeam(playerName);
        scoreboard.addPlayerToTeam(playerName, team);
    }

    public static Optional<TeamId> teamForPlayer(MinecraftServer server, ServerPlayer player) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(player.getGameProfile().getName());
        if (team == null) {
            return Optional.empty();
        }
        return teamIdFromScoreboardName(team.getName());
    }

    private static PlayerTeam getOrCreateTeam(ServerScoreboard scoreboard, TeamId teamId) {
        String teamName = scoreboardTeamName(teamId);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        team.setDisplayName(Component.literal("Semion " + teamId.name()));
        team.setColor(color(teamId));
        team.setAllowFriendlyFire(false);
        team.setSeeFriendlyInvisibles(true);
        return team;
    }

    private static PlayerTeam getOrCreateSpectatorTeam(ServerScoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(SPECTATOR_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(SPECTATOR_TEAM);
        }

        team.setDisplayName(Component.literal("Semion Spectator"));
        team.setColor(ChatFormatting.GRAY);
        team.setAllowFriendlyFire(false);
        team.setSeeFriendlyInvisibles(true);
        return team;
    }

    private static Optional<TeamId> teamIdFromScoreboardName(String teamName) {
        if (!teamName.startsWith(TEAM_PREFIX)) {
            return Optional.empty();
        }

        String rawTeam = teamName.substring(TEAM_PREFIX.length());
        try {
            return Optional.of(TeamId.valueOf(rawTeam.toUpperCase()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String scoreboardTeamName(TeamId teamId) {
        return TEAM_PREFIX + teamId.name().toLowerCase();
    }

    private static ChatFormatting color(TeamId teamId) {
        return switch (teamId) {
            case RED -> ChatFormatting.RED;
            case BLUE -> ChatFormatting.BLUE;
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.YELLOW;
        };
    }
}
