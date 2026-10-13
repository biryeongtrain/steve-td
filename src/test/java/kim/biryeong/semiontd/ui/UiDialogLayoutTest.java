package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.body.PlainMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class UiDialogLayoutTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void hudRuleRetainsCompactWidthAndGrayColor() {
        Component rule = SemionText.mini(UiTextDivider.hudMarkup());
        assertEquals(36, TextUncenterer.width(rule));
        assertEquals(Optional.of(net.minecraft.network.chat.TextColor.DARK_GRAY.getValue()),
                rule.visit((style, value) -> value.isEmpty() ? Optional.empty() : Optional.of(style.getColor().getValue()), Style.EMPTY));
    }

    @Test
    void bundledFallbackMetricsCoverHangulAndStayOrdered() {
        assertEquals(8, UiFontMetrics.unifontAdvance('가'));
        assertEquals(8, UiFontMetrics.unifontAdvance('힣'));
        assertEquals(9, UiFontMetrics.unifontAdvance('漢'));
        assertEquals(-1, UiFontMetrics.unifontAdvance(0x110000));
    }

    @Test
    void customSpacingKeepsItsAdvanceInsideStyledParents() {
        for (int width : List.of(1, 5, 10, 20, 50, 100, 160, 248, 380, 472)) {
            Component spacing = Component.empty().withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC)
                    .append(SemionUiFont.space(width));
            assertEquals(width, TextUncenterer.width(spacing));
        }
    }

    @Test
    void halfPixelSpacingKeepsItsFractionUntilTheCompleteLineIsMeasured() {
        Component half = SemionUiFont.halfPixel();
        assertEquals(0.5, TextUncenterer.preciseWidth(half));
        assertEquals(1, TextUncenterer.width(half));
        Component pair = Component.empty().append(half).append(half);
        assertEquals(1.0, TextUncenterer.preciseWidth(pair));
        assertEquals(1, TextUncenterer.width(pair));
    }

    @Test
    void standaloneRulesFitTheVanillaTextArea() {
        for (int width : List.of(200, 256, 360, 420, 460, 480)) {
            PlainMessage body = HeaderMessage.divider(width);
            assertEquals(width, body.width());
            assertEquals(width - 8, TextUncenterer.width(body.contents()));
            assertTrue(body.contents().getStyle().isStrikethrough());
        }
        assertEquals(0, TextUncenterer.width(HeaderMessage.dividerComponent(-1)));
        assertEquals(160, TextUncenterer.width(HeaderMessage.dividerComponent(160)));
    }

    @Test
    void markersUseTheirContainerWidthAndPreserveTextAndStyle() {
        for (int width : List.of(256, 360, 480)) {
            PlainMessage body = assertInstanceOf(PlainMessage.class,
                    UiDialogBodyRenderer.actionDialogBodies("\n<red>first</red>\r\n<divider>\r\n<green>last</green>\r\n<divider>", width).getFirst());
            assertEquals(width, body.width());
            assertFalse(body.contents().getString().contains("<divider>"));
            var rules = body.contents().getSiblings().stream().filter(c -> c.getStyle().isStrikethrough()).toList();
            assertEquals(2, rules.size());
            rules.forEach(rule -> assertEquals(width - 8, TextUncenterer.width(rule)));
            assertTrue(body.contents().getString().startsWith("first\n"));
            assertTrue(body.contents().getString().contains("\nlast\n"));
            assertEquals(Optional.of(net.minecraft.network.chat.TextColor.RED.getValue()), body.contents().visit((style, text) ->
                    text.equals("first") ? Optional.of(style.getColor().getValue()) : Optional.empty(), Style.EMPTY));
        }
    }

}
