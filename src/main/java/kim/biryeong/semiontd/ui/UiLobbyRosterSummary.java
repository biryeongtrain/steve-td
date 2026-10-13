package kim.biryeong.semiontd.ui;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionService;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.StartCandidate;
import net.minecraft.server.MinecraftServer;

record UiLobbyRosterSummary(int onlinePlayerCount, int readyPlayerCount, String startableLabel) {
    static UiLobbyRosterSummary capture(MinecraftServer server, SemionGame game, MatchMode matchMode) {
        List<StartCandidate> candidates = server.getPlayerList().getPlayers().stream()
                .map(player -> new StartCandidate(player.getUUID(), player.getGameProfile().name()))
                .toList();
        return from(candidates, game.readyPlayerIds(), matchMode);
    }

    static UiLobbyRosterSummary from(List<StartCandidate> candidates, Set<UUID> readyPlayerIds, MatchMode matchMode) {
        String startable = ParticipantSelectionService.selectReady(candidates, readyPlayerIds, matchMode)
                .map(plan -> "<green><bold>가능</bold></green> <dark_gray>(</dark_gray><white>"
                        + plan.activePlayerCount()
                        + "명</white><dark_gray>)</dark_gray>")
                .orElse("<red><bold>불가</bold></red>");
        return new UiLobbyRosterSummary(candidates.size(), readyPlayerIds.size(), startable);
    }
}
