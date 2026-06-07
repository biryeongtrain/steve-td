package kim.biryeong.semiontd.ui;

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class SemionTitleService {
    private static final int FADE_IN_TICKS = 10;
    private static final int STAY_TICKS = 70;
    private static final int FADE_OUT_TICKS = 20;
    private static final String EMERALD_INCOME_BOOST_ACTIVATED_MARKUP = "<red><bold>에메랄드 수급량 2배 활성화!</bold></red>";

    private SemionTitleService() {
    }

    public static void showEmeraldIncomeBoostActivated(ServerPlayer player) {
        showTitle(player, emeraldIncomeBoostActivatedMarkup());
    }

    public static String emeraldIncomeBoostActivatedMarkup() {
        return EMERALD_INCOME_BOOST_ACTIVATED_MARKUP;
    }

    private static void showTitle(ServerPlayer player, String markup) {
        if (player == null || markup == null || markup.isBlank()) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(SemionText.mini(markup)));
    }
}
