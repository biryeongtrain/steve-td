package kim.biryeong.semiontd.rating;

import java.nio.file.Path;
import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.persistence.AppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileRatingEventRepository;
import kim.biryeong.semiontd.persistence.FileRatingRepository;
import kim.biryeong.semiontd.persistence.RatingEventRepository;
import kim.biryeong.semiontd.persistence.RatingRepository;
import net.minecraft.server.MinecraftServer;

public final class RatingService {
    private static final String RATING_SUBSYSTEM = "rating";

    private final RatingRepository ratingRepository;
    private final RatingEventRepository ratingEventRepository;
    private final AppliedMatchRepository appliedMatchRepository;
    private final RatingCalculator ratingCalculator;

    public RatingService(Path configDir) {
        this(
                new FileRatingRepository(configDir == null ? null : configDir.resolve("ratings.json")),
                new FileRatingEventRepository(configDir == null ? null : configDir.resolve("rating-events.json")),
                new FileAppliedMatchRepository(configDir == null ? null : configDir.resolve("rating-applied-matches.json")),
                new EloRatingCalculator()
        );
    }

    public RatingService(
            RatingRepository ratingRepository,
            RatingEventRepository ratingEventRepository,
            AppliedMatchRepository appliedMatchRepository,
            RatingCalculator ratingCalculator
    ) {
        this.ratingRepository = ratingRepository;
        this.ratingEventRepository = ratingEventRepository;
        this.appliedMatchRepository = appliedMatchRepository;
        this.ratingCalculator = ratingCalculator;
    }

    public synchronized RatingMatchResult applyMatchResult(MinecraftServer server, MatchResult matchResult) {
        if (appliedMatchRepository.hasApplied(matchResult.matchId(), RATING_SUBSYSTEM)) {
            return ratingEventRepository.findMatchResult(matchResult.matchId())
                    .orElseGet(() -> RatingMatchResult.empty(matchResult.matchId()));
        }

        RatingMatchInput input = new RatingMatchInput(
                matchResult.matchId(),
                matchResult.endedAtEpochMillis(),
                participants(matchResult)
        );
        RatingMatchResult ratingResult = ratingCalculator.calculate(input);
        for (RatingAdjustment adjustment : ratingResult.adjustments()) {
            ratingRepository.saveProfile(adjustment.playerId(), adjustment.after());
        }
        ratingEventRepository.saveMatchResult(ratingResult);
        if (!appliedMatchRepository.markApplied(matchResult.matchId(), RATING_SUBSYSTEM, System.currentTimeMillis())) {
            SemionTd.LOGGER.warn("Rating was persisted but applied marker already existed for match {}.", matchResult.matchId());
        }
        return ratingResult;
    }

    private List<RatingParticipant> participants(MatchResult matchResult) {
        return matchResult.participants().stream()
                .filter(participant -> !matchResult.spectatorIds().contains(participant.playerId()))
                .map(this::participant)
                .toList();
    }

    private RatingParticipant participant(MatchParticipantResult participant) {
        PlayerRatingProfile profile = ratingRepository.findProfile(participant.playerId())
                .orElseGet(() -> PlayerRatingProfile.initial(participant.playerId(), participant.playerName()));
        return new RatingParticipant(
                participant.playerId(),
                participant.playerName(),
                participant.teamId(),
                participant.winner(),
                profile
        );
    }
}
