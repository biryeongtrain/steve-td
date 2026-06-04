package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingAdjustment;
import kim.biryeong.semiontd.rating.RatingSystemId;
import org.junit.jupiter.api.Test;

final class SemionRatingTitleServiceTest {
    @Test
    void titleMarkupShowsCurrentScoreInWhiteAndPositiveOffsetInYellow() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RatingAdjustment adjustment = adjustment(playerId, 1500, 1516, 16);

        assertEquals(
                "<gray>점수 : </gray><white>1516</white><yellow>(+16)</yellow>",
                SemionRatingTitleService.titleMarkupFor(adjustment)
        );
    }

    @Test
    void signedOffsetKeepsNegativeSignAndDoesNotAddPlusToZero() {
        assertEquals("-13", SemionRatingTitleService.signedOffset(-13));
        assertEquals("0", SemionRatingTitleService.signedOffset(0));
    }

    private static RatingAdjustment adjustment(UUID playerId, int beforeElo, int afterElo, int delta) {
        PlayerRatingProfile before = profile(playerId, beforeElo, 0);
        PlayerRatingProfile after = profile(playerId, afterElo, 1);
        return new RatingAdjustment(
                playerId,
                "player",
                TeamId.RED,
                true,
                before,
                after,
                delta,
                delta
        );
    }

    private static PlayerRatingProfile profile(UUID playerId, int displayElo, int gamesPlayed) {
        return new PlayerRatingProfile(
                playerId,
                "player",
                RatingSystemId.ELO,
                1,
                gamesPlayed,
                gamesPlayed,
                0,
                displayElo,
                350.0,
                displayElo,
                new MatchId(1L),
                1000L
        );
    }
}
