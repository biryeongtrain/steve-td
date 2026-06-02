package kim.biryeong.semiontd.rating;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.persistence.AppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileRatingEventRepository;
import kim.biryeong.semiontd.persistence.FileRatingRepository;
import kim.biryeong.semiontd.persistence.PersistenceException;
import kim.biryeong.semiontd.persistence.RatingEventRepository;
import kim.biryeong.semiontd.persistence.RatingRepository;
import net.minecraft.server.MinecraftServer;

public final class RatingService {
    private static final String RATING_SUBSYSTEM = "rating";

    private final RatingRepository ratingRepository;
    private final RatingEventRepository ratingEventRepository;
    private final AppliedMatchRepository appliedMatchRepository;
    private final RatingCalculator ratingCalculator;
    private final RatingConfig ratingConfig;
    private final RatingEligibilityPolicy eligibilityPolicy;

    public RatingService(Path configDir) {
        this(
                new FileRatingRepository(configDir == null ? null : configDir.resolve("ratings.json")),
                new FileRatingEventRepository(configDir == null ? null : configDir.resolve("rating-events.json")),
                new FileAppliedMatchRepository(configDir == null ? null : configDir.resolve("rating-applied-matches.json")),
                RatingConfig.defaultConfig()
        );
    }

    public RatingService(
            RatingRepository ratingRepository,
            RatingEventRepository ratingEventRepository,
            AppliedMatchRepository appliedMatchRepository,
            RatingCalculator ratingCalculator
    ) {
        this(
                ratingRepository,
                ratingEventRepository,
                appliedMatchRepository,
                ratingCalculator,
                RatingConfig.defaultConfig(),
                new RatingEligibilityPolicy(RatingConfig.defaultConfig())
        );
    }

    public RatingService(
            RatingRepository ratingRepository,
            RatingEventRepository ratingEventRepository,
            AppliedMatchRepository appliedMatchRepository
    ) {
        this(ratingRepository, ratingEventRepository, appliedMatchRepository, RatingConfig.defaultConfig());
    }

    public RatingService(
            RatingRepository ratingRepository,
            RatingEventRepository ratingEventRepository,
            AppliedMatchRepository appliedMatchRepository,
            RatingConfig ratingConfig
    ) {
        this(
                ratingRepository,
                ratingEventRepository,
                appliedMatchRepository,
                new EloRatingCalculator(ratingConfig),
                ratingConfig == null ? RatingConfig.defaultConfig() : ratingConfig,
                new RatingEligibilityPolicy(ratingConfig == null ? RatingConfig.defaultConfig() : ratingConfig)
        );
    }

    public RatingService(
            RatingRepository ratingRepository,
            RatingEventRepository ratingEventRepository,
            AppliedMatchRepository appliedMatchRepository,
            RatingCalculator ratingCalculator,
            RatingConfig ratingConfig,
            RatingEligibilityPolicy eligibilityPolicy
    ) {
        this.ratingRepository = ratingRepository;
        this.ratingEventRepository = ratingEventRepository;
        this.appliedMatchRepository = appliedMatchRepository;
        this.ratingCalculator = ratingCalculator;
        this.ratingConfig = ratingConfig == null ? RatingConfig.defaultConfig() : ratingConfig;
        this.eligibilityPolicy = eligibilityPolicy == null ? new RatingEligibilityPolicy(this.ratingConfig) : eligibilityPolicy;
    }

    public synchronized RatingMatchResult applyMatchResult(MinecraftServer server, MatchResult matchResult) {
        if (appliedMatchRepository.hasApplied(matchResult.matchId(), RATING_SUBSYSTEM)) {
            return ratingEventRepository.findMatchResult(matchResult.matchId())
                    .orElseGet(() -> RatingMatchResult.empty(matchResult.matchId()));
        }
        Optional<RatingMatchResult> existingEvent = ratingEventRepository.findMatchResult(matchResult.matchId());
        if (existingEvent.isPresent()) {
            appliedMatchRepository.markApplied(matchResult.matchId(), RATING_SUBSYSTEM, System.currentTimeMillis());
            return existingEvent.get();
        }
        String skippedReason = eligibilityPolicy.skippedReason(matchResult);
        if (!skippedReason.isEmpty()) {
            SemionTd.LOGGER.info("Skipping rating for match {}: {}.", matchResult.matchId(), skippedReason);
            return RatingMatchResult.empty(matchResult.matchId());
        }
        if (hasProfileAlreadyUpdatedForMatch(matchResult)) {
            SemionTd.LOGGER.error(
                    "Rating profile for match {} already exists but no rating event was found. Manual repair is required before retrying rating application.",
                    matchResult.matchId()
            );
            throw new PersistenceException(
                    "Rating profile for match " + matchResult.matchId()
                            + " already exists without a rating event; refusing to mark the match applied."
            );
        }

        RatingMatchInput input = new RatingMatchInput(
                matchResult.matchId(),
                matchResult.endedAtEpochMillis(),
                participants(matchResult)
        );
        RatingMatchResult ratingResult = ratingCalculator.calculate(input);
        for (RatingAdjustment adjustment : ratingResult.adjustments()) {
            Optional<PlayerRatingProfile> current = ratingRepository.findProfile(adjustment.playerId());
            if (current.map(profile -> matchResult.matchId().equals(profile.lastUpdatedMatchId())).orElse(false)) {
                SemionTd.LOGGER.warn(
                        "Skipping duplicate rating profile write for player {} and match {} because the profile already references that match.",
                        adjustment.playerId(),
                        matchResult.matchId()
                );
                continue;
            }
            ratingRepository.saveProfile(adjustment.playerId(), adjustment.after());
        }
        ratingEventRepository.saveMatchResult(ratingResult);
        if (!appliedMatchRepository.markApplied(matchResult.matchId(), RATING_SUBSYSTEM, System.currentTimeMillis())) {
            SemionTd.LOGGER.warn("Rating was persisted but applied marker already existed for match {}.", matchResult.matchId());
        }
        return ratingResult;
    }

    public Optional<PlayerRatingProfile> profile(UUID playerId) {
        return ratingRepository.findProfile(playerId);
    }

    public List<PlayerRatingProfile> topProfiles() {
        return topProfiles(ratingConfig.leaderboardLimit());
    }

    public List<PlayerRatingProfile> topProfiles(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int boundedLimit = Math.min(100, limit);
        return ratingRepository.findAllProfiles().values().stream()
                .filter(profile -> profile.gamesPlayed() > 0)
                .sorted(Comparator
                        .comparingInt(PlayerRatingProfile::displayElo).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerRatingProfile::gamesPlayed).reversed())
                        .thenComparing(Comparator.comparingLong(PlayerRatingProfile::updatedAtEpochMillis).reversed())
                        .thenComparing(PlayerRatingProfile::lastKnownName, String.CASE_INSENSITIVE_ORDER))
                .limit(boundedLimit)
                .toList();
    }

    public RatingConfig ratingConfig() {
        return ratingConfig;
    }

    private List<RatingParticipant> participants(MatchResult matchResult) {
        return eligibilityPolicy.ratingParticipants(matchResult).stream()
                .map(this::participant)
                .toList();
    }

    private boolean hasProfileAlreadyUpdatedForMatch(MatchResult matchResult) {
        for (MatchParticipantResult participant : eligibilityPolicy.ratingParticipants(matchResult)) {
            Optional<PlayerRatingProfile> profile = ratingRepository.findProfile(participant.playerId());
            if (profile.map(existing -> matchResult.matchId().equals(existing.lastUpdatedMatchId())).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private RatingParticipant participant(MatchParticipantResult participant) {
        PlayerRatingProfile profile = ratingRepository.findProfile(participant.playerId())
                .orElseGet(() -> PlayerRatingProfile.initial(participant.playerId(), participant.playerName(), ratingConfig));
        return new RatingParticipant(
                participant.playerId(),
                participant.playerName(),
                participant.teamId(),
                participant.winner(),
                profile,
                participant.stats()
        );
    }
}
