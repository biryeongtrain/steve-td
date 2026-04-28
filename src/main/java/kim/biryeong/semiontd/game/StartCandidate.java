package kim.biryeong.semiontd.game;

import java.util.Optional;
import java.util.UUID;

public record StartCandidate(
        UUID uuid,
        String name,
        Optional<TeamId> preferredTeam
) {
    public StartCandidate {
        preferredTeam = preferredTeam == null ? Optional.empty() : preferredTeam;
    }
}
