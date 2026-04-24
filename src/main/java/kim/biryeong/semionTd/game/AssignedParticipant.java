package kim.biryeong.semionTd.game;

import java.util.UUID;

public record AssignedParticipant(
        UUID uuid,
        String name,
        TeamId teamId,
        int laneId
) {
}
