package kim.biryeong.semiontd.placeholder;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SemionPlaceholders {
    public static final Identifier SELECTED_JOB = id("selected_job");
    public static final Identifier SELECTED_JOB_ID = id("selected_job_id");
    public static final Identifier JOB = id("job");
    public static final Identifier JOB_ID = id("job_id");
    public static final Identifier RATING_ELO = id("rating_elo");
    public static final Identifier RATING_GAMES = id("rating_games");
    public static final Identifier RATING_WINS = id("rating_wins");
    public static final Identifier RATING_LOSSES = id("rating_losses");

    private SemionPlaceholders() {
    }

    public static void register(SemionGameManager gameManager) {
        Placeholders.registerServer(SELECTED_JOB, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).displayName());
        });
        Placeholders.registerServer(SELECTED_JOB_ID, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).id().toString());
        });
        Placeholders.registerServer(JOB, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).displayName());
        });
        Placeholders.registerServer(JOB_ID, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(Component.literal(selectedJob(gameManager, context.player().getUUID()).id().toString()));
        });
        Placeholders.registerServer(RATING_ELO, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return ratingPlaceholder(gameManager, context.player().getUUID(), SemionPlaceholders::ratingEloText);
        });
        Placeholders.registerServer(RATING_GAMES, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return ratingPlaceholder(gameManager, context.player().getUUID(), SemionPlaceholders::ratingGamesText);
        });
        Placeholders.registerServer(RATING_WINS, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return ratingPlaceholder(gameManager, context.player().getUUID(), SemionPlaceholders::ratingWinsText);
        });
        Placeholders.registerServer(RATING_LOSSES, (context, argument) -> {
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
            Function<Optional<PlayerRatingProfile>, String> formatter
    ) {
        return PlaceholderResult.value(formatter.apply(gameManager.ratingProfile(playerId)));
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
