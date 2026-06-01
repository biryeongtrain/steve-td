package kim.biryeong.semiontd.rating;

import java.util.List;
import java.util.Objects;
import kim.biryeong.semiontd.game.MatchId;

public record RatingMatchInput(
        MatchId matchId,
        long endedAtEpochMillis,
        List<RatingParticipant> participants
) {
    public RatingMatchInput {
        Objects.requireNonNull(matchId, "matchId");
        participants = List.copyOf(participants);
    }
}
