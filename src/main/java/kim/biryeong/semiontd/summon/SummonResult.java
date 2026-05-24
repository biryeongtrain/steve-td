package kim.biryeong.semiontd.summon;

import java.util.Optional;
import kim.biryeong.semiontd.game.TeamId;

public record SummonResult(
        SummonResultType type,
        String summonId,
        Optional<TeamId> targetTeam,
        Optional<Integer> targetLaneId,
        Optional<Integer> scheduledRound
) {
    public static SummonResult failure(SummonResultType type, String summonId) {
        return new SummonResult(type, summonId, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static SummonResult success(String summonId, TeamId targetTeam, int targetLaneId) {
        return new SummonResult(SummonResultType.SUCCESS, summonId, Optional.of(targetTeam), Optional.of(targetLaneId), Optional.empty());
    }

    public static SummonResult success(String summonId, TeamId targetTeam, int targetLaneId, int scheduledRound) {
        return new SummonResult(
                SummonResultType.SUCCESS,
                summonId,
                Optional.of(targetTeam),
                Optional.of(targetLaneId),
                Optional.of(Math.max(1, scheduledRound))
        );
    }
}
