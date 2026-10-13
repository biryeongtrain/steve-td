package kim.biryeong.semiontd.ui.augment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import kim.biryeong.semiontd.augment.AugmentIconCategory;
import kim.biryeong.semiontd.augment.AugmentRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class AugmentCardFrames {
    public static final int WIDTH = 108;
    public static final int BUTTON_WIDTH = 40;
    public static final int BUTTON_INSET = (WIDTH - BUTTON_WIDTH) / 2;
    public static final int ROW_HEIGHT = 9;
    public static final int CARD_ROWS = 19;
    public static final int BUTTON_ROWS = 3;
    public static final Identifier FONT = Identifier.fromNamespaceAndPath("semion-td", "augment_card_dialog");
    public static final int CARD_COLUMNS = AugmentRarity.values().length * AugmentIconCategory.values().length;
    private static final int FIRST_CARD = 0xE000;
    private static final int FIRST_BUTTON = 0xE400;
    private static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT))
            .withColor(0xFFFFFF).withShadowColor(0).withBold(false).withItalic(false);

    private AugmentCardFrames() { }

    public static void init() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(AugmentCardFrames::resources);
    }

    public static Component card(AugmentRarity rarity, AugmentIconCategory role, int row) {
        return glyph(FIRST_CARD + row * CARD_COLUMNS + rarity.ordinal() * AugmentIconCategory.values().length + role.ordinal());
    }

    public static Component button(AugmentRarity rarity, boolean enabled, int remaining, int row) {
        if (remaining < 0 || remaining > 5) throw new IllegalArgumentException("Invalid reroll count");
        return glyph(FIRST_BUTTON + row * 6 + rarity.ordinal() * 2 + (enabled ? 0 : 1));
    }

    private static Component glyph(int codePoint) {
        return Component.literal(Character.toString(codePoint)).setStyle(STYLE);
    }

    static int color(AugmentRarity rarity) {
        return switch (rarity) {
            case SILVER -> 0xC9D9EF;
            case GOLD -> 0xF3CA78;
            case PRISMATIC -> 0xC9AEFF;
        };
    }

    public static boolean isCardGlyph(int codePoint) {
        return codePoint >= FIRST_CARD && codePoint < FIRST_CARD + CARD_ROWS * CARD_COLUMNS;
    }

    static BufferedImage cards() {
        var image = new BufferedImage((WIDTH - 1) * CARD_COLUMNS, CARD_ROWS * ROW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            int height = image.getHeight();
            int center = (WIDTH - 2) / 2;
            for (int variant = 0; variant < CARD_COLUMNS; variant++) {
                int x = variant * (WIDTH - 1);
                Color accent = new Color(color(AugmentRarity.values()[variant / AugmentIconCategory.values().length]));
                graphics.setPaint(new GradientPaint(x, 0, new Color(0x202B43), x, height, new Color(0x0A1020)));
                graphics.fillRect(x, 0, WIDTH - 1, height);
                frame(graphics, x, height, accent);
                graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 55));
                graphics.drawLine(x + 12, 94, x + WIDTH - 14, 94);
                graphics.drawImage(AugmentCategoryIcons.image(
                        AugmentIconCategory.values()[variant % AugmentIconCategory.values().length], accent),
                        x + center - 24, 20, null);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void frame(Graphics2D graphics, int x, int height, Color accent) {
        var frame = (Graphics2D) graphics.create(x, 0, WIDTH - 1, height);
        try {
            var light = new Color(accent.getRed() + (255 - accent.getRed()) * 45 / 100,
                    accent.getGreen() + (255 - accent.getGreen()) * 45 / 100,
                    accent.getBlue() + (255 - accent.getBlue()) * 45 / 100);
            var shade = new Color(accent.getRed() * 48 / 100, accent.getGreen() * 48 / 100,
                    accent.getBlue() * 48 / 100);
            var rim = new Path2D.Double();
            rim.moveTo(4, 11);
            rim.lineTo(11, 4);
            rim.lineTo(WIDTH - 13, 4);
            rim.lineTo(WIDTH - 6, 11);
            rim.lineTo(WIDTH - 6, height - 8);
            rim.lineTo(WIDTH - 9, height - 5);
            rim.lineTo(7, height - 5);
            rim.lineTo(4, height - 8);
            rim.closePath();
            frame.setColor(new Color(0x070D13));
            frame.setStroke(new BasicStroke(7F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            frame.draw(rim);
            frame.setPaint(new GradientPaint(0, 3, light, 0, height - 4, shade));
            frame.setStroke(new BasicStroke(4F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            frame.draw(rim);
            frame.setStroke(new BasicStroke(1F));
            frame.setColor(light);
            frame.drawLine(11, 2, WIDTH - 13, 2);
            frame.drawLine(2, 12, 2, height - 13);
            frame.setColor(shade);
            frame.drawLine(WIDTH - 4, 12, WIDTH - 4, height - 13);
            frame.drawLine(11, height - 3, WIDTH - 13, height - 3);
            frame.setColor(new Color(accent.getRed() * 72 / 100, accent.getGreen() * 72 / 100,
                    accent.getBlue() * 72 / 100));
            for (int side = 0; side < 2; side++) {
                int edge = side == 0 ? 7 : WIDTH - 9;
                int direction = side == 0 ? 1 : -1;
                frame.drawPolyline(new int[]{edge, edge, edge + direction * 5, edge + direction * 16},
                        new int[]{25, 14, 9, 9}, 4);
                frame.drawPolyline(new int[]{edge, edge, edge + direction * 5, edge + direction * 16},
                        new int[]{height - 26, height - 8, height - 6, height - 6}, 4);
            }
        } finally {
            frame.dispose();
        }
    }

    static BufferedImage buttons() {
        int height = BUTTON_ROWS * ROW_HEIGHT;
        var image = new BufferedImage(BUTTON_WIDTH * 6, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            for (int variant = 0; variant < 6; variant++) {
                int x = variant * BUTTON_WIDTH;
                boolean enabled = variant % 2 == 0;
                Color accent = enabled ? new Color(color(AugmentRarity.values()[variant / 2])) : new Color(0x65738A);
                graphics.setPaint(new GradientPaint(x, 2, new Color(enabled ? 0x37415A : 0x192333), x, 23, new Color(0x101827)));
                graphics.fillRect(x, 2, BUTTON_WIDTH, 22);
                graphics.setStroke(new BasicStroke(1));
                graphics.setColor(accent);
                graphics.drawRect(x, 2, BUTTON_WIDTH - 1, 21);
                graphics.setPaint(AugmentCategoryIcons.gradient(accent, 8, 16));
                graphics.setStroke(new BasicStroke(1.5F));
                graphics.drawArc(x + 8, 9, 7, 7, 40, 285);
                graphics.fillPolygon(new int[]{x + 16, x + 12, x + 16}, new int[]{8, 10, 13}, 3);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void resources(ResourcePackBuilder builder) {
        var providers = new JsonArray();
        add(builder, providers, "cards", cards(), CARD_ROWS, CARD_COLUMNS, FIRST_CARD);
        add(builder, providers, "buttons", buttons(), BUTTON_ROWS, 6, FIRST_BUTTON);
        var font = new JsonObject();
        font.add("providers", providers);
        builder.addData("assets/semion-td/font/augment_card_dialog.json", font.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void add(ResourcePackBuilder builder, JsonArray providers, String name, BufferedImage image,
                            int rows, int columns, int first) {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", output);
            builder.addData("assets/semion-td/textures/font/augment_dialog_" + name + ".png", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot build augment card frames", exception);
        }
        var bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", "semion-td:font/augment_dialog_" + name + ".png");
        bitmap.addProperty("height", ROW_HEIGHT);
        bitmap.addProperty("ascent", 7);
        var chars = new JsonArray();
        for (int row = 0; row < rows; row++) {
            var text = new StringBuilder();
            for (int column = 0; column < columns; column++) text.appendCodePoint(first + row * columns + column);
            chars.add(text.toString());
        }
        bitmap.add("chars", chars);
        providers.add(bitmap);
    }
}
