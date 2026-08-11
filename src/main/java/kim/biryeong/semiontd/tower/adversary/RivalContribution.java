package kim.biryeong.semiontd.tower.adversary;

import java.util.Objects;
import java.util.UUID;

public record RivalContribution(UUID rivalId, RivalKind kind, int score) {
    public RivalContribution {
        Objects.requireNonNull(rivalId, "rivalId");
        Objects.requireNonNull(kind, "kind");
        if (score < 0) {
            throw new IllegalArgumentException("Rival contribution cannot be negative.");
        }
    }
}
