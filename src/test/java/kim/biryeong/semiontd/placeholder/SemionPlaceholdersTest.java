package kim.biryeong.semiontd.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import org.junit.jupiter.api.Test;

final class SemionPlaceholdersTest {
    @Test
    void ratingPlaceholderIdsUseSemionNamespace() {
        assertEquals("semion-td:rating_elo", SemionPlaceholders.RATING_ELO.toString());
        assertEquals("semion-td:rating_games", SemionPlaceholders.RATING_GAMES.toString());
        assertEquals("semion-td:rating_wins", SemionPlaceholders.RATING_WINS.toString());
        assertEquals("semion-td:rating_losses", SemionPlaceholders.RATING_LOSSES.toString());
    }

    @Test
    void ratingPlaceholderTextUsesProfileOrDefaultValues() {
        UUID playerId = UUID.nameUUIDFromBytes("placeholder-player".getBytes());
        PlayerRatingProfile profile = PlayerRatingProfile.initial(playerId, "placeholder");

        assertEquals("1500", SemionPlaceholders.ratingEloText(Optional.of(profile)));
        assertEquals("0", SemionPlaceholders.ratingGamesText(Optional.empty()));
    }
}
