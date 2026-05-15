package kim.biryeong.semiontd.ui;

import net.kyori.adventure.platform.modcommon.impl.NonWrappingComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.chat.Component;

public final class SemionText {
    public static final String BRAND_MARKUP = "<gradient:#67e8f9:#a78bfa><bold>Semion TD</bold></gradient>";
    public static final String PREFIX_MARKUP = "<dark_gray>[</dark_gray>" + BRAND_MARKUP + "<dark_gray>]</dark_gray> ";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private SemionText() {
    }

    public static Component mini(String markup) {
        return NonWrappingComponentSerializer.INSTANCE.serialize(MINI_MESSAGE.deserialize(markup));
    }

    public static Component prefix() {
        return mini(PREFIX_MARKUP);
    }

    public static Component prefixed(Component message) {
        return Component.empty().append(prefix()).append(message);
    }

    public static Component prefixedPlain(String message) {
        return prefixed(Component.literal(message));
    }

    public static Component prefixedMini(String markup) {
        return mini(PREFIX_MARKUP + markup);
    }
}
