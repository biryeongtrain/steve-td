package kim.biryeong.semiontd.ui.augment;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import kim.biryeong.semiontd.augment.AugmentIconCategory;

final class AugmentCategoryIcons {
    private static final Map<AugmentIconCategory, BufferedImage> ART = load();

    private AugmentCategoryIcons() { }

    static GradientPaint gradient(Color accent, int top, int bottom) {
        var light = new Color(accent.getRed() + (255 - accent.getRed()) * 18 / 100,
                accent.getGreen() + (255 - accent.getGreen()) * 18 / 100,
                accent.getBlue() + (255 - accent.getBlue()) * 18 / 100);
        var shade = new Color(accent.getRed() * 84 / 100, accent.getGreen() * 84 / 100,
                accent.getBlue() * 84 / 100);
        return new GradientPaint(0, top, light, 0, bottom, shade);
    }

    static BufferedImage image(AugmentIconCategory category, Color accent) {
        return tint(ART.get(category), accent);
    }

    static BufferedImage tint(BufferedImage source, Color accent) {
        var image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                int alpha = pixel >>> 24;
                if (alpha == 0) continue;
                double luminance = .2126 * (pixel >> 16 & 255) + .7152 * (pixel >> 8 & 255) + .0722 * (pixel & 255);
                int red = (int) Math.round(luminance * (.35 + .65 * accent.getRed() / 255.0));
                int green = (int) Math.round(luminance * (.35 + .65 * accent.getGreen() / 255.0));
                int blue = (int) Math.round(luminance * (.35 + .65 * accent.getBlue() / 255.0));
                image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
            }
        }
        return image;
    }

    private static Map<AugmentIconCategory, BufferedImage> load() {
        var result = new EnumMap<AugmentIconCategory, BufferedImage>(AugmentIconCategory.class);
        for (var category : AugmentIconCategory.values()) {
            String name = switch (category) {
                case DAMAGE -> "attack";
                case GUARD -> "protection";
                case VITALITY -> "health";
                case REVIVAL -> "revival";
                case TEMPO -> "speed";
                case REACH -> "aim";
                case CONTROL -> "control";
                case GROWTH -> "growth";
                case RESOURCE -> "resources";
                case DEPLOY -> "summon";
                case SYNERGY -> "synergy";
                case ENHANCEMENT -> "enhancement";
                case FORTUNE -> "chance";
            };
            String resource = "/semiontd/ui/augment-icons/" + name + ".png";
            try (var stream = AugmentCategoryIcons.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Missing augment artwork: " + resource);
                var source = ImageIO.read(stream);
                if (source == null || source.getWidth() != 256 || source.getHeight() != 256
                        || !source.getColorModel().hasAlpha()) {
                    throw new IllegalStateException("Invalid augment artwork: " + resource);
                }
                var icon = new BufferedImage(49, 49, BufferedImage.TYPE_INT_ARGB_PRE);
                var graphics = icon.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                    graphics.drawImage(source, 0, 0, 49, 49, null);
                } finally {
                    graphics.dispose();
                }
                result.put(category, icon);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read augment artwork: " + resource, exception);
            }
        }
        return Map.copyOf(result);
    }
}
