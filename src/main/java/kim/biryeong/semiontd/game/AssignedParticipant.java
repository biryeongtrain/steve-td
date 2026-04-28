package kim.biryeong.semiontd.game;

import java.util.UUID;

public record AssignedParticipant(
        UUID uuid,
        String name,
        TeamId teamId,
        int laneId
) {
}
