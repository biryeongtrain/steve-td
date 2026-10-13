package kim.biryeong.semiontd.game;

import java.util.Set;
import java.util.UUID;

final class GameSpectatorPlacementOrder {
    private GameSpectatorPlacementOrder() {
    }

    static int index(Set<UUID> spectatorIds, UUID spectatorId) {
        if (!spectatorIds.contains(spectatorId)) {
            return 0;
        }
        int index = 0;
        for (UUID other : spectatorIds) {
            if (other.compareTo(spectatorId) < 0) {
                index++;
            }
        }
        return index;
    }
}
