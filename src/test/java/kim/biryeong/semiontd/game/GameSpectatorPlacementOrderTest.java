package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GameSpectatorPlacementOrderTest {
    @Test
    void rankMatchesNaturalUuidOrderAcrossSignedBoundaries() {
        Set<UUID> ids = new HashSet<>(Set.of(new UUID(Long.MIN_VALUE, 0), new UUID(Long.MAX_VALUE, 0),
                new UUID(0, Long.MIN_VALUE), new UUID(0, Long.MAX_VALUE), new UUID(0, 0)));
        Random random = new Random(2319);
        for (int i = 0; i < 100; i++) {
            ids.add(new UUID(random.nextLong(), random.nextLong()));
        }
        var expected = ids.stream().sorted().toList();
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(i, GameSpectatorPlacementOrder.index(ids, expected.get(i)));
        }
    }

    @Test
    void rejoiningEliminatedAndRemovedSpectatorsUseCurrentMembership() {
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        UUID third = new UUID(0, 3);
        Set<UUID> ids = new HashSet<>();
        assertEquals(0, GameSpectatorPlacementOrder.index(ids, second));
        ids.add(third);
        ids.add(second);
        assertEquals(0, GameSpectatorPlacementOrder.index(ids, second));
        assertEquals(1, GameSpectatorPlacementOrder.index(ids, third));
        ids.add(first);
        assertEquals(1, GameSpectatorPlacementOrder.index(ids, second));
        assertEquals(2, GameSpectatorPlacementOrder.index(ids, third));
        ids.remove(second);
        assertEquals(0, GameSpectatorPlacementOrder.index(ids, second));
        assertEquals(1, GameSpectatorPlacementOrder.index(ids, third));
        ids.clear();
        assertEquals(0, GameSpectatorPlacementOrder.index(ids, third));
    }
}
