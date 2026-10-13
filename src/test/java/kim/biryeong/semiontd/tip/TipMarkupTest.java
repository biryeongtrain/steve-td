package kim.biryeong.semiontd.tip;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import kim.biryeong.semiontd.config.TipConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TipMarkupTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bundledRequestTipShowsLiteralAngleBrackets() {
        var tip = SemionTipService.renderMarkup(TipConfig.defaultConfig().messages().get(1));
        assertTrue(tip.getString().contains("/요청 <수량>"));
        assertFalse(tip.getString().contains("&lt;"));
        assertFalse(tip.getString().contains("\\"));
    }

    @Test
    void savedHtmlEscapesRemainLiteralInsideExistingStyles() {
        var tip = SemionTipService.renderMarkup("<yellow>/요청 &lt;수량&gt; &lt;red&gt;</yellow>");
        assertEquals("/요청 <수량> <red>", tip.getString());
        assertEquals(Optional.of(TextColor.YELLOW.getValue()), tip.visit((style, text) ->
                text.isEmpty() ? Optional.empty() : Optional.of(style.getColor().getValue()), Style.EMPTY));
    }
}
