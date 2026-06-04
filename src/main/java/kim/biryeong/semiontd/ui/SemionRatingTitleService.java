package kim.biryeong.semiontd.ui;

import kim.biryeong.semiontd.rating.RatingAdjustment;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class SemionRatingTitleService {
    private static final int FADE_IN_TICKS = 10;
    private static final int STAY_TICKS = 70;
    private static final int FADE_OUT_TICKS = 20;

    private SemionRatingTitleService() {
    }

    public static void showRatingChange(ServerPlayer player, RatingAdjustment adjustment) {
        if (player == null || adjustment == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(SemionText.mini(titleMarkupFor(adjustment))));
    }

    public static String titleMarkupFor(RatingAdjustment adjustment) {
        return "<gray>점수 : </gray><white>"
                + adjustment.after().displayElo()
                + "</white><yellow>("
                + signedOffset(adjustment.displayEloDelta())
                + ")</yellow>";
    }

    public static String signedOffset(int offset) {
        if (offset > 0) {
            return "+" + offset;
        }
        return Integer.toString(offset);
    }
}
