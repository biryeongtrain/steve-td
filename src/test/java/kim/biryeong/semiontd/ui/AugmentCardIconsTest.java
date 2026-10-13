package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import kim.biryeong.semiontd.ui.rp.AugmentCardIcons;
import org.junit.jupiter.api.Test;

final class AugmentCardIconsTest {
    @Test
    void atlasHasNineDistinctGlyphsAndAnExplicitLocalTextureReference() {
        var provider = AugmentCardIcons.fontDefinition().getAsJsonArray("providers").get(0).getAsJsonObject();
        assertEquals("semion-td:font/augment-icons.png", provider.get("file").getAsString());
        assertEquals(48, provider.get("height").getAsInt());
        var points = new HashSet<Integer>();
        for (var row : provider.getAsJsonArray("chars")) {
            assertEquals(3, row.getAsString().codePointCount(0, row.getAsString().length()));
            row.getAsString().codePoints().forEach(points::add);
        }
        assertEquals(9, points.size());
    }

    @Test
    void acceptsBothRequestedAndGeneratedSquareAtlasesButRejectsBrokenGrids() {
        AugmentCardIcons.validateDimensions(768, 768);
        AugmentCardIcons.validateDimensions(1254, 1254);
        assertThrows(IllegalArgumentException.class, () -> AugmentCardIcons.validateDimensions(1254, 768));
        assertThrows(IllegalArgumentException.class, () -> AugmentCardIcons.validateDimensions(1024, 1024));
    }
}
