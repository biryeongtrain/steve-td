package kim.biryeong.semiontd.ui;

import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import net.minecraft.network.chat.Component;

public final class UiTextDivider {
    private static final int HUD_WIDTH = 36;

    private UiTextDivider() {
    }

    public static Component line(int width) {
        return SemionUiFont.space(Math.max(0, width))
                .withStyle(style -> style.withStrikethrough(true));
    }

    public static String hudMarkup() {
        return "<dark_gray><font:semion-td:ui><!bold><!italic><strikethrough>"
                + SemionUiFont.space(HUD_WIDTH).getString()
                + "</strikethrough></!italic></!bold></font></dark_gray>";
    }
}
