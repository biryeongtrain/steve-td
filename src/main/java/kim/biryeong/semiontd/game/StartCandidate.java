package kim.biryeong.semiontd.game;

import java.util.UUID;

public record StartCandidate(
        UUID uuid,
        String name,
        int displayElo
) {
    public StartCandidate(UUID uuid, String name) {
        this(uuid, name, 1500);
    }
}
