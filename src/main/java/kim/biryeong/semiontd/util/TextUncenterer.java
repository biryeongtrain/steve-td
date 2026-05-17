package kim.biryeong.semiontd.util;

import eu.pb4.mapcanvas.api.font.DefaultFonts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class TextUncenterer {
    private static final ResourceLocation DEFAULT_FONT_ID = ResourceLocation.withDefaultNamespace("default");
    private static final int FONT_SIZE = 8;

    private TextUncenterer() {
        throw new IllegalStateException("Utility class");
    }

    public static List<Component> getLeftAligned(Component text, int width, String language) {
        return getAligned(text, width, language, MutableComponent::append);
    }

    public static List<Component> getRightAligned(Component text, int width, String language) {
        return getAligned(text, width, language, (value, filler) -> Component.empty().append(filler).append(value));
    }

    public static List<Component> splitLines(Component text, int width, String language) {
        return getAligned(text, width, language, (value, filler) -> value);
    }

    private static List<Component> getAligned(
            Component text,
            int width,
            String language,
            BiFunction<MutableComponent, Component, Component> merger
    ) {
        // Kept for API parity with upstream body signature; 1.21.8 backport does not apply locale shaping here.
        if (language == null || language.isEmpty()) {
            language = "en_us";
        }

        int normalizedWidth = Math.max(0, width);
        List<MutableComponent> splitLines = splitStyledLines(text);
        List<Component> result = new ArrayList<>(splitLines.size());

        for (MutableComponent line : splitLines) {
            int lineWidth = getWidth(line);
            int fillerWidth = Math.max(0, normalizedWidth - lineWidth);
            result.add(merger.apply(line, filler(fillerWidth)));
        }

        return List.copyOf(result);
    }

    private static List<MutableComponent> splitStyledLines(Component text) {
        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.empty());

        text.visit((style, string) -> {
            int startIndex = 0;
            while (true) {
                int newLineIndex = string.indexOf('\n', startIndex);
                String part = newLineIndex == -1
                        ? string.substring(startIndex)
                        : string.substring(startIndex, newLineIndex);

                if (!part.isEmpty()) {
                    lines.getLast().append(Component.literal(part).setStyle(style));
                }

                if (newLineIndex == -1) {
                    break;
                }

                lines.add(Component.empty());
                startIndex = newLineIndex + 1;
            }
            return Optional.empty();
        }, Style.EMPTY);

        return lines;
    }

    private static int getWidth(Component text) {
        final int[] totalWidth = {0};
        text.visit((style, string) -> {
            if (!string.isEmpty()) {
                totalWidth[0] += getTextWidth(style, string);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return totalWidth[0];
    }

    private static int getTextWidth(Style style, String string) {
        ResourceLocation fontId = style.getFont() == null ? DEFAULT_FONT_ID : style.getFont();
        return DefaultFonts.REGISTRY.getDefaultedFont(fontId).getTextWidth(string, FONT_SIZE);
    }

    public static Component filler(int width) {
        if (width <= 0) {
            return Component.empty();
        }

        int spaceWidth = Math.max(1, DefaultFonts.REGISTRY.getDefaultedFont(DEFAULT_FONT_ID).getTextWidth(" ", FONT_SIZE));
        int spaces = (int) Math.ceil((double) width / spaceWidth);
        return Component.literal(" ".repeat(spaces));
    }
}
