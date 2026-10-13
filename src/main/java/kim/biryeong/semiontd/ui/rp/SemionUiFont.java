package kim.biryeong.semiontd.ui.rp;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.resourcepack.extras.api.format.font.FontAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.font.SpaceProvider;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class SemionUiFont {
    private static final Identifier FONT_ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "ui");
    private static final Style STYLE = Style.EMPTY.withColor(0xFFFFFF).withFont(new net.minecraft.network.chat.FontDescription.Resource(FONT_ID)).withShadowColor(0).withBold(false).withItalic(false).withUnderlined(false).withObfuscated(false);
    private static final char SPACE_1 = '\uE100';
    private static final char SPACE_5 = '\uE101';
    private static final char SPACE_10 = '\uE102';
    private static final char SPACE_20 = '\uE103';
    private static final char SPACE_50 = '\uE104';
    private static final char SPACE_100 = '\uE105';
    private static final char SPACE_HALF = '\uE106';

    private SemionUiFont() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean usesFont(Identifier id) {
        return FONT_ID.equals(id);
    }

    public static int advance(int codePoint) {
        if (codePoint >= 0xE110 && codePoint <= 0xE115) return -new int[]{1, 5, 10, 20, 50, 100}[codePoint - 0xE110];
        return switch (codePoint) {
            case SPACE_1 -> 1;
            case SPACE_5 -> 5;
            case SPACE_10 -> 10;
            case SPACE_20 -> 20;
            case SPACE_50 -> 50;
            case SPACE_100 -> 100;
            default -> 0;
        };
    }

    public static double preciseAdvance(int codePoint) {
        return codePoint == SPACE_HALF ? 0.5 : advance(codePoint);
    }

    public static MutableComponent halfPixel() {
        return Component.literal(Character.toString(SPACE_HALF)).setStyle(STYLE);
    }

    public static void init() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(SemionUiFont::createFont);
    }

    public static MutableComponent space(int width) {
        if (width <= 0) {
            return Component.empty();
        }

        StringBuilder builder = new StringBuilder();
        int remaining = width;
        while (remaining > 0) {
            if (remaining >= 100) {
                builder.append(SPACE_100);
                remaining -= 100;
            } else if (remaining >= 50) {
                builder.append(SPACE_50);
                remaining -= 50;
            } else if (remaining >= 20) {
                builder.append(SPACE_20);
                remaining -= 20;
            } else if (remaining >= 10) {
                builder.append(SPACE_10);
                remaining -= 10;
            } else if (remaining >= 5) {
                builder.append(SPACE_5);
                remaining -= 5;
            } else {
                builder.append(SPACE_1);
                remaining -= 1;
            }
        }
        return Component.literal(builder.toString()).setStyle(STYLE);
    }

    public static MutableComponent offset(int width) {
        if (width >= 0) return space(width);
        StringBuilder result = new StringBuilder();
        int remaining = -width;
        int[] advances = {1, 5, 10, 20, 50, 100};
        for (int i = advances.length - 1; i >= 0; i--) {
            while (remaining >= advances[i]) {
                result.append((char) (0xE110 + i));
                remaining -= advances[i];
            }
        }
        return Component.literal(result.toString()).setStyle(STYLE);
    }

    private static void createFont(ResourcePackBuilder builder) {
        FontAsset font = FontAsset.builder()
                .add(SpaceProvider.builder()
                        .add(SPACE_1, 1)
                        .add(SPACE_5, 5)
                        .add(SPACE_10, 10)
                        .add(SPACE_20, 20)
                        .add(SPACE_50, 50)
                        .add(SPACE_100, 100))
                .add(new SpaceProvider(java.util.Map.of("\uE110", -1F, "\uE111", -5F, "\uE112", -10F,
                        "\uE113", -20F, "\uE114", -50F, "\uE115", -100F)))
                .add(new SpaceProvider(java.util.Map.of(Character.toString(SPACE_HALF), 0.5F)))
                .build();
        builder.addData("assets/" + SemionTd.MOD_ID + "/font/ui.json", font);
    }
}
