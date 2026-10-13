package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionService;
import kim.biryeong.semiontd.game.StartCandidate;
import org.junit.jupiter.api.Test;

final class UiLobbyRosterSummaryTest {
    @Test
    void summaryMatchesExistingSelectionAcrossModesReadinessAndPlayerLimits() {
        for (MatchMode mode : MatchMode.values()) {
            for (int size : new int[] {0, 1, 2, 3, 4, 6, 12, 30, 31, 64, 128}) {
                List<StartCandidate> players = players(size);
                for (int readyCount = 0; readyCount <= size; readyCount++) {
                    Set<UUID> ready = new HashSet<>();
                    players.stream().limit(readyCount).forEach(player -> ready.add(player.uuid()));
                    String expected = ParticipantSelectionService.selectReady(players, ready, mode)
                            .map(plan -> "<green><bold>가능</bold></green> <dark_gray>(</dark_gray><white>"
                                    + plan.activePlayerCount() + "명</white><dark_gray>)</dark_gray>")
                            .orElse("<red><bold>불가</bold></red>");
                    UiLobbyRosterSummary summary = UiLobbyRosterSummary.from(players, ready, mode);
                    assertEquals(expected, summary.startableLabel());
                    assertEquals(size, summary.onlinePlayerCount());
                    assertEquals(readyCount, summary.readyPlayerCount());
                }
            }
        }
    }

    @Test
    void snapshotsDoNotRetainMutableRosterAndNextRefreshSeesChanges() {
        List<StartCandidate> players = players(4);
        Set<UUID> ready = new HashSet<>();
        players.forEach(player -> ready.add(player.uuid()));
        ready.add(new UUID(0, 1000));
        UiLobbyRosterSummary first = UiLobbyRosterSummary.from(players, ready, MatchMode.NORMAL);
        ready.clear();
        players.removeLast();
        UiLobbyRosterSummary second = UiLobbyRosterSummary.from(players, ready, MatchMode.NORMAL);
        assertEquals(4, first.onlinePlayerCount());
        assertEquals(5, first.readyPlayerCount());
        assertFalse(first.startableLabel().contains("불가"));
        assertEquals(3, second.onlinePlayerCount());
        assertEquals(0, second.readyPlayerCount());
        assertEquals("<red><bold>불가</bold></red>", second.startableLabel());
    }

    private static List<StartCandidate> players(int size) {
        List<StartCandidate> players = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            players.add(new StartCandidate(new UUID(0, index + 1), "player_" + index));
        }
        return players;
    }
}
