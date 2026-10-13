package kim.biryeong.semiontd.ui.augment;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.HashSet;
import kim.biryeong.semiontd.augment.AugmentIconCategory;
import org.junit.jupiter.api.Test;

final class AugmentCardFramesTest {
    @Test
    void narrowerTallerCardsKeepFullClickAdvanceAndReadableTextRows() {
        var cards = AugmentCardFrames.cards();
        assertEquals(107 * AugmentCardFrames.CARD_COLUMNS, cards.getWidth());
        assertEquals(171, cards.getHeight());
        assertEquals(108, AugmentCardFrames.WIDTH);
        assertTrue(cards.getHeight() > 162);
        for (int variant = 0; variant < AugmentCardFrames.CARD_COLUMNS; variant++) {
            for (int row = 0; row < AugmentCardFrames.CARD_ROWS; row++) {
                assertEquals(255, cards.getRGB(variant * 107 + 106, row * 9 + 4) >>> 24,
                        "Every bitmap slice must retain the full compact card click advance");
            }
        }
        assertEquals(344, AugmentCardDialog.CONTENT_WIDTH);
        assertEquals(207, AugmentCardDialog.TOTAL_ROWS * 9);
    }

    @Test
    void metallicRimsKeepRarityColorsAndDarkReadableTextInterior() {
        var cards = AugmentCardFrames.cards();
        var rarityColors = new HashSet<Integer>();
        for (int rarity = 0; rarity < 3; rarity++) {
            for (int category = 0; category < AugmentIconCategory.values().length; category++) {
                int x = (rarity * AugmentIconCategory.values().length + category) * 107;
                var upper = new Color(cards.getRGB(x + 53, 4));
                var lower = new Color(cards.getRGB(x + 53, 166));
                assertTrue(upper.getRed() + upper.getGreen() + upper.getBlue()
                                > lower.getRed() + lower.getGreen() + lower.getBlue() + 100,
                        "Metal rims need distinct lit upper and shaded lower faces");
                rarityColors.add(upper.getRGB());
                for (int y = 99; y < 162; y++) {
                    int reference = cards.getRGB(x + 53, y);
                    var background = new Color(reference);
                    assertTrue(Math.max(background.getRed(), Math.max(background.getGreen(), background.getBlue())) < 65);
                    for (int innerX = 9; innerX < 99; innerX++) {
                        assertEquals(reference, cards.getRGB(x + innerX, y),
                                "Frame decoration must remain outside the description text area at " + innerX + "," + y);
                    }
                }
            }
        }
        assertEquals(3, rarityColors.size(), "All three rarities retain different metal colors");
    }

    @Test
    void compactButtonsKeepDistinctStatesAndLeaveNormalFontCounterAreaEmpty() {
        var buttons = AugmentCardFrames.buttons();
        assertEquals(27, buttons.getHeight());
        assertEquals(240, buttons.getWidth());
        assertEquals(40, AugmentCardFrames.BUTTON_WIDTH);
        assertEquals(34, AugmentCardFrames.BUTTON_INSET);
        for (int rarity = 0; rarity < 3; rarity++) {
            assertNotEquals(buttons.getRGB(rarity * 80, 2), buttons.getRGB(rarity * 80 + 40, 2),
                    "An exhausted reroll needs a visibly different inactive border");
        }
        for (int variant = 0; variant < 6; variant++) {
            for (int y = 0; y < 27; y++) {
                assertEquals(y >= 2 && y <= 23 ? 255 : 0, buttons.getRGB(variant * 40 + 39, y) >>> 24);
            }
            for (int y = 3; y < 23; y++) {
                for (int x = 25; x < 31; x++) {
                    assertEquals(buttons.getRGB(variant * 40 + 35, y), buttons.getRGB(variant * 40 + x, y),
                            "Remaining counts must not be baked into the image");
                }
            }
        }
    }

    @Test
    void generatedMetalArtKeepsHighlightShadowContrastAndSoftAlphaAcrossRarities() {
        for (var category : AugmentIconCategory.values()) {
            var neutral = AugmentCategoryIcons.image(category, Color.WHITE);
            for (var rarity : kim.biryeong.semiontd.augment.AugmentRarity.values()) {
                var icon = AugmentCategoryIcons.image(category, new Color(AugmentCardFrames.color(rarity)));
                int darkest = 255, brightest = 0, softEdges = 0, opaque = 0;
                for (int y = 0; y < 49; y++) {
                    for (int x = 0; x < 49; x++) {
                        var pixel = new Color(icon.getRGB(x, y), true);
                        assertEquals(neutral.getRGB(x, y) >>> 24, pixel.getAlpha(), "Rarity tint preserves alpha");
                        if (pixel.getAlpha() > 0 && pixel.getAlpha() < 255) softEdges++;
                        if (pixel.getAlpha() < 250) continue;
                        opaque++;
                        int brightness = (pixel.getRed() + pixel.getGreen() + pixel.getBlue()) / 3;
                        darkest = Math.min(darkest, brightness);
                        brightest = Math.max(brightest, brightness);
                    }
                }
                assertTrue(opaque > 40 && softEdges > 10, category + " retains a solid silhouette and antialiased edges");
                assertTrue(brightest > 180 && brightest - darkest > 70,
                        category + " retains generated metallic highlights and deep local shadows");
            }
        }
    }

    @Test
    void rarityTintDoesNotFlattenSourceLuminanceOrChangeTransparency() {
        var source = new java.awt.image.BufferedImage(4, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFF202020);
        source.setRGB(1, 0, 0xFFE0E0E0);
        source.setRGB(2, 0, 0x80808080);
        source.setRGB(3, 0, 0x00FFFFFF);
        var tinted = AugmentCategoryIcons.tint(source, new Color(0xF3CA78));
        var shadow = new Color(tinted.getRGB(0, 0));
        var highlight = new Color(tinted.getRGB(1, 0));
        assertTrue(highlight.getRed() > shadow.getRed() * 5);
        assertTrue(highlight.getGreen() > shadow.getGreen() * 5);
        assertTrue(highlight.getBlue() > shadow.getBlue() * 5);
        assertTrue(highlight.getRed() > highlight.getBlue());
        assertEquals(128, tinted.getRGB(2, 0) >>> 24);
        assertEquals(0, tinted.getRGB(3, 0));
    }

    @Test
    void generatedArtKeepsCenteredBoundsBreathingRoomAndDistinctSilhouettes() {
        var silhouettes = new HashSet<Integer>();
        for (var role : AugmentIconCategory.values()) {
            var icon = AugmentCategoryIcons.image(role, Color.WHITE);
            int left = 49, right = -1, top = 49, bottom = -1;
            int fingerprint = 1;
            for (int y = 0; y < 49; y++) {
                for (int x = 0; x < 49; x++) {
                    int alpha = icon.getRGB(x, y) >>> 24;
                    fingerprint = 31 * fingerprint + alpha;
                    if (alpha < 128) continue;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            assertTrue(right > left && bottom > top);
            assertEquals(24, (left + right) / 2.0, 1.5, "Generated silhouette bounds remain centered");
            assertEquals(24, (top + bottom) / 2.0, 1.5, "Generated silhouette bounds remain centered");
            assertTrue(left >= 4 && right <= 44 && top >= 4 && bottom <= 44,
                    "Generated artwork retains about twelve percent canvas padding");
            silhouettes.add(fingerprint);
        }
        assertEquals(AugmentIconCategory.values().length, silhouettes.size());
    }
}
