package kim.biryeong.semiontd.util;

import eu.pb4.mapcanvas.api.font.DefaultFonts;
import kim.biryeong.semiontd.ui.UiFontMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;

public final class TextUncenterer {
    private static final Identifier DEFAULT_FONT_ID = Identifier.withDefaultNamespace("default");
    private static final int FONT_SIZE = 8;

    private TextUncenterer() {
        throw new IllegalStateException("Utility class");
    }

    public static List<Component> getLeftAligned(Component text, int width, String language) {
        return getAligned(text, width, language, (value, filler) -> Component.empty().append(value).append(filler));
    }

    public static List<Component> getRightAligned(Component text, int width, String language) {
        return getAligned(text, width, language, (value, filler) -> Component.empty().append(filler).append(value));
    }

    public static List<Component> getLeftAlignedNoWrap(Component text, int width, String language) {
        return getExplicitLineAligned(text, width, language, (value, filler) -> Component.empty().append(value).append(filler));
    }

    public static List<Component> getRightAlignedNoWrap(Component text, int width, String language) {
        return getExplicitLineAligned(text, width, language, (value, filler) -> Component.empty().append(filler).append(value));
    }

    public static List<Component> splitLines(Component text, int width, String language) {
        return getAligned(text, width, language, (value, filler) -> value);
    }

    private static List<Component> getAligned(
            Component text,
            int width,
            String language,
            Merger merger
    ) {
        // Kept for API parity with upstream body signature; 1.21.8 backport does not apply locale shaping here.
        if (language == null || language.isEmpty()) {
            language = "en_us";
        }

        int normalizedWidth = Math.max(0, width);
        List<Component> result = new ArrayList<>();
        LineBuilder line = new LineBuilder();

        text.visit((style, string) -> {
            acceptStyledText(result, line, style, string, normalizedWidth, merger);
            return Optional.empty();
        }, Style.EMPTY);

        flushLine(result, line, normalizedWidth, merger, false);
        return List.copyOf(result);
    }

    private static List<Component> getExplicitLineAligned(
            Component text,
            int width,
            String language,
            Merger merger
    ) {
        // Kept for API parity with upstream body signature; 1.21.8 backport does not apply locale shaping here.
        if (language == null || language.isEmpty()) {
            language = "en_us";
        }

        int normalizedWidth = Math.max(0, width);
        List<MutableComponent> splitLines = splitExplicitLines(text);
        List<Component> result = new ArrayList<>(splitLines.size());
        for (MutableComponent line : splitLines) {
            int fillerWidth = Math.max(0, normalizedWidth - width(line));
            result.add(merger.apply(line, filler(fillerWidth)));
        }
        return List.copyOf(result);
    }

    private static List<MutableComponent> splitExplicitLines(Component text) {
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

    private static void acceptStyledText(
            List<Component> result,
            LineBuilder line,
            Style style,
            String string,
            int width,
            Merger merger
    ) {
        int newLine = string.indexOf('\n');
        String text = newLine == -1 ? string : string.substring(0, newLine);
        if (!text.isEmpty()) {
            appendWrappedText(result, line, style, text, width, merger);
        }

        if (newLine != -1) {
            flushLine(result, line, width, merger, true);
            acceptStyledText(result, line, style, string.substring(newLine + 1), width, merger);
        }
    }

    private static void appendWrappedText(
            List<Component> result,
            LineBuilder line,
            Style style,
            String text,
            int width,
            Merger merger
    ) {
        MutableComponent component = Component.literal(text).setStyle(style);
        if (width(Component.empty().append(line.component).append(component)) <= width) {
            line.component.append(component);
            return;
        }

        int spaceIndex = text.indexOf(' ');
        if (spaceIndex != -1) {
            String remaining = text;
            while (spaceIndex != -1) {
                appendWrappedText(result, line, style, remaining.substring(0, spaceIndex), width, merger);
                if (!line.component.getSiblings().isEmpty()) {
                    line.component.append(Component.literal(" ").setStyle(style));
                }
                remaining = remaining.substring(spaceIndex + 1);
                spaceIndex = remaining.indexOf(' ');
            }
            appendWrappedText(result, line, style, remaining, width, merger);
            return;
        }

        if (!line.component.getSiblings().isEmpty()) {
            trimTrailingSpace(line.component);
            flushLine(result, line, width, merger, false);
        }
        line.component.append(component);
    }

    public static int width(Component text) {
        return (int) Math.ceil(preciseWidth(text));
    }

    public static double preciseWidth(Component text) {
        final double[] totalWidth = {0};
        text.visit((style, string) -> {
            if (!string.isEmpty()) {
                totalWidth[0] += getTextWidth(style, string);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return totalWidth[0];
    }

    private static double getTextWidth(Style style, String string) {
        Identifier fontId = style.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource font
                ? font.id() : DEFAULT_FONT_ID;
        if (SemionUiFont.usesFont(fontId)) {
            return string.codePoints().mapToDouble(codePoint -> SemionUiFont.preciseAdvance(codePoint) + (style.isBold() ? 1 : 0)).sum();
        }
        var font = DefaultFonts.REGISTRY.getDefaultedFont(fontId);
        return string.codePoints().mapToDouble(codePoint -> {
            if (DEFAULT_FONT_ID.equals(fontId) && !font.containsGlyph(codePoint)) {
                int advance = UiFontMetrics.unifontAdvance(codePoint);
                if (advance >= 0) {
                    return advance + (style.isBold() ? 0.5 : 0);
                }
            }
            return font.getGlyphWidth(codePoint, FONT_SIZE, 2) + (style.isBold() ? 1 : 0);
        }).sum();
    }

    public static Component filler(int width) {
        if (width <= 0) {
            return Component.empty();
        }

        return SemionUiFont.space(width);
    }

    private static void flushLine(List<Component> result, LineBuilder line, int width, Merger merger, boolean includeEmptyLine) {
        trimTrailingSpace(line.component);
        if (!includeEmptyLine && line.component.getSiblings().isEmpty()) {
            return;
        }

        int fillerWidth = Math.max(0, width - width(line.component));
        result.add(merger.apply(line.component, filler(fillerWidth)));
        line.component = Component.empty();
    }

    private static void trimTrailingSpace(MutableComponent line) {
        if (!line.getSiblings().isEmpty()
                && line.getSiblings().getLast().getContents() instanceof PlainTextContents contents
                && contents.text().equals(" ")) {
            line.getSiblings().removeLast();
        }
    }

    private static final class LineBuilder {
        private MutableComponent component = Component.empty();
    }

    private interface Merger {
        Component apply(MutableComponent text, Component filler);
    }
}
