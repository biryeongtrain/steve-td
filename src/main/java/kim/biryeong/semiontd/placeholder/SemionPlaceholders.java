package kim.biryeong.semiontd.placeholder;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SemionPlaceholders {
    public static final ResourceLocation SELECTED_JOB = id("selected_job");
    public static final ResourceLocation SELECTED_JOB_ID = id("selected_job_id");
    public static final ResourceLocation JOB = id("job");
    public static final ResourceLocation JOB_ID = id("job_id");
    public static final ResourceLocation RATING_ELO = id("rating_elo");
    public static final ResourceLocation RATING_GAMES = id("rating_games");
    public static final ResourceLocation RATING_WINS = id("rating_wins");
    public static final ResourceLocation RATING_LOSSES = id("rating_losses");

    private SemionPlaceholders() {
    }

    public static void register(SemionGameManager gameManager) {
        Placeholders.register(SELECTED_JOB, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).displayName());
        });
        Placeholders.register(SELECTED_JOB_ID, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).id().toString());
        });
        Placeholders.register(JOB, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).displayName());
        });
        Placeholders.register(JOB_ID, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(Component.literal(selectedJob(gameManager, context.player().getUUID()).id().toString()));
        });
        Placeholders.register(RATING_ELO, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return ratingPlaceholder(gameManager, context.player().getUUID(), SemionPlaceholders::ratingEloText);
        });
        Placeholders.register(RATING_GAMES, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return ratingPlaceholder(gameManager, context.player().getUUID(), SemionPlaceholders::ratingGamesText);
        });
        Placeholders.register(RATING_WINS, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return ratingPlaceholder(gameManager, context.player().getUUID(), SemionPlaceholders::ratingWinsText);
        });
        Placeholders.register(RATING_LOSSES, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return ratingPlaceholder(gameManager, context.player().getUUID(), SemionPlaceholders::ratingLossesText);
        });
    }

    public static String ratingEloText(Optional<PlayerRatingProfile> profile) {
        return Integer.toString(profile.map(PlayerRatingProfile::displayElo).orElse(PlayerRatingProfile.INITIAL_DISPLAY_ELO));
    }

    public static String ratingGamesText(Optional<PlayerRatingProfile> profile) {
        return Integer.toString(profile.map(PlayerRatingProfile::gamesPlayed).orElse(0));
    }

    public static String ratingWinsText(Optional<PlayerRatingProfile> profile) {
        return Integer.toString(profile.map(PlayerRatingProfile::wins).orElse(0));
    }

    public static String ratingLossesText(Optional<PlayerRatingProfile> profile) {
        return Integer.toString(profile.map(PlayerRatingProfile::losses).orElse(0));
    }

    private static PlaceholderResult ratingPlaceholder(
            SemionGameManager gameManager,
            UUID playerId,
            RatingPlaceholderFormatter formatter
    ) {
        return PlaceholderResult.value(formatter.format(gameManager.ratingProfile(playerId)));
    }

    private static SemionJob selectedJob(SemionGameManager gameManager, UUID playerId) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            return JobRegistry.defaultJob();
        }
        SemionPlayer activePlayer = game.players().get(playerId);
        if (activePlayer != null) {
            return activePlayer.job().orElse(JobRegistry.defaultJob());
        }
        return game.selectedJobOrDefault(playerId);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }

    private interface RatingPlaceholderFormatter {
        String format(Optional<PlayerRatingProfile> profile);
    }
}
