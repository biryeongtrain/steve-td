package kim.biryeong.semiontd.ui.rp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Original pixel artwork generated into Polymer's pack, independent of licensed asset directories. */
public final class GambleGlyphs {
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "gamble");
    private static final Style STYLE = Style.EMPTY.withFont(FONT).withColor(0xFFFFFF).withShadowColor(0);
    private static final int CARD = 0xE200;
    private static final int BACK = 0xE234;
    private static final int DIE = 0xE240;
    private static final int SLOT = 0xE250;
    private static final Map<Character, String> DIGITS = Map.ofEntries(
            Map.entry('0', "111101101101111"), Map.entry('1', "010110010010111"),
            Map.entry('2', "111001111100111"), Map.entry('3', "111001111001111"),
            Map.entry('4', "101101111001001"), Map.entry('5', "111100111001111"),
            Map.entry('6', "111100111101111"), Map.entry('7', "111001010010010"),
            Map.entry('8', "111101111101111"), Map.entry('9', "111101111001111"),
            Map.entry('J', "001001001101111"), Map.entry('Q', "111101101111001"),
            Map.entry('K', "101101110101101"), Map.entry('A', "010101111101101"));

    private GambleGlyphs() {
    }

    public static void init() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(GambleGlyphs::createPackAssets);
    }

    public static Component card(int card) {
        if (card < -1 || card >= 52) throw new IllegalArgumentException("Card glyph must be -1 or 0..51");
        return glyph(card < 0 ? BACK : CARD + card);
    }

    public static Component die(int face) {
        if (face < 1 || face > 6) throw new IllegalArgumentException("Die glyph must be 1..6");
        return glyph(DIE + face - 1);
    }

    public static Component slot(int symbol) {
        if (symbol < 0 || symbol >= 6) throw new IllegalArgumentException("Slot glyph must be 0..5");
        return glyph(SLOT + symbol);
    }

    private static Component glyph(int codePoint) {
        return Component.literal(Character.toString(codePoint)).setStyle(STYLE);
    }

    public static JsonObject fontDefinition() {
        JsonArray providers = new JsonArray();
        String[] cards = new String[4];
        for (int suit = 0; suit < 4; suit++) cards[suit] = characters(CARD + suit * 13, 13);
        providers.add(bitmap("cards", 24, 24, cards));
        providers.add(bitmap("card_back", 24, 24, characters(BACK, 1)));
        providers.add(bitmap("dice", 24, 24, characters(DIE, 6)));
        providers.add(bitmap("slots", 24, 24, characters(SLOT, 6)));
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        return font;
    }

    private static JsonObject bitmap(String name, int height, int ascent, String... rows) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", "semion-td:font/gamble/" + name + ".png");
        provider.addProperty("height", height);
        provider.addProperty("ascent", ascent);
        JsonArray chars = new JsonArray();
        for (String row : rows) chars.add(row);
        provider.add("chars", chars);
        return provider;
    }

    private static String characters(int first, int count) {
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < count; i++) chars.appendCodePoint(first + i);
        return chars.toString();
    }

    private static void createPackAssets(ResourcePackBuilder builder) {
        builder.addData("assets/semion-td/font/gamble.json", fontDefinition().toString().getBytes(StandardCharsets.UTF_8));
        for (String name : new String[]{"cards", "card_back", "dice", "slots"}) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(atlas(name), "png", bytes);
                builder.addData("assets/semion-td/textures/font/gamble/" + name + ".png", bytes.toByteArray());
            } catch (IOException exception) {
                throw new IllegalStateException("Could not generate gamble font atlas " + name, exception);
            }
        }
    }

    public static BufferedImage atlas(String name) {
        int width = switch (name) { case "cards" -> 40 * 13; case "card_back" -> 40; case "dice" -> 24 * 6; case "slots" -> 32 * 6; default -> throw new IllegalArgumentException(name); };
        int height = switch (name) { case "cards" -> 56 * 4; case "card_back" -> 56; case "dice" -> 24; default -> 32; };
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            switch (name) {
                case "cards" -> {
                    for (int suit = 0; suit < 4; suit++) {
                        for (int rank = 0; rank < 13; rank++) drawCard(graphics, rank * 40, suit * 56, suit, rank + 2);
                    }
                }
                case "card_back" -> {
                    panel(graphics, 1, 1, 37, 54, 0xE9C96B, 0x222B48);
                    graphics.setColor(new Color(0x526187));
                    for (int y = 7; y < 50; y += 6) for (int x = 6; x < 34; x += 6) graphics.fillRect(x, y, 3, 3);
                    suit(graphics, 20, 27, 2, 0xE9C96B);
                }
                case "dice" -> { for (int face = 1; face <= 6; face++) drawDie(graphics, (face - 1) * 24, face); }
                case "slots" -> { for (int symbol = 0; symbol < 6; symbol++) drawSlot(graphics, symbol * 32, symbol); }
                default -> throw new IllegalArgumentException(name);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void panel(Graphics2D g, int x, int y, int width, int height, int edge, int fill) {
        g.setColor(new Color(edge));
        g.fillRoundRect(x, y, width, height, 5, 5);
        g.setColor(new Color(fill));
        g.fillRect(x + 2, y + 2, width - 4, height - 4);
    }

    private static void drawCard(Graphics2D g, int x, int y, int suit, int rank) {
        panel(g, x + 1, y + 1, 37, 54, 0xCAA85D, 0xFFF9E9);
        int color = suit == 1 || suit == 2 ? 0xBF3546 : 0x242E46;
        String label = switch (rank) { case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; case 14 -> "A"; default -> Integer.toString(rank); };
        digits(g, x + 5, y + 5, label, color);
        digits(g, x + 34 - label.length() * 8, y + 41, label, color);
        suit(g, x + 20, y + 28, suit, color);
    }

    private static void digits(Graphics2D g, int x, int y, String text, int color) {
        g.setColor(new Color(color));
        for (int c = 0; c < text.length(); c++) {
            String pixels = DIGITS.get(text.charAt(c));
            for (int i = 0; i < 15; i++) if (pixels.charAt(i) == '1') g.fillRect(x + c * 8 + i % 3 * 2, y + i / 3 * 2, 2, 2);
        }
    }

    private static void suit(Graphics2D g, int x, int y, int suit, int color) {
        g.setColor(new Color(color));
        switch (suit) {
            case 0 -> {
                g.fillPolygon(new int[]{x, x - 8, x + 8}, new int[]{y - 10, y + 1, y + 1}, 3);
                g.fillOval(x - 8, y - 2, 9, 8); g.fillOval(x - 1, y - 2, 9, 8); g.fillRect(x - 2, y + 3, 4, 7);
            }
            case 1 -> {
                g.fillOval(x - 8, y - 7, 9, 9); g.fillOval(x - 1, y - 7, 9, 9);
                g.fillPolygon(new int[]{x - 8, x + 8, x}, new int[]{y - 2, y - 2, y + 9}, 3);
            }
            case 2 -> g.fillPolygon(new int[]{x, x + 8, x, x - 8}, new int[]{y - 10, y, y + 10, y}, 4);
            case 3 -> {
                g.fillOval(x - 5, y - 10, 10, 10); g.fillOval(x - 9, y - 2, 10, 10);
                g.fillOval(x - 1, y - 2, 10, 10); g.fillRect(x - 2, y + 3, 4, 7);
            }
            default -> throw new IllegalArgumentException("Suit " + suit);
        }
    }

    private static void drawDie(Graphics2D g, int x, int face) {
        panel(g, x + 1, 1, 21, 22, 0xD6B86B, 0xFFF7DC);
        g.setColor(new Color(0x273248));
        if (face % 2 == 1) g.fillRect(x + 10, 10, 4, 4);
        if (face >= 2) { g.fillRect(x + 5, 5, 4, 4); g.fillRect(x + 15, 15, 4, 4); }
        if (face >= 4) { g.fillRect(x + 15, 5, 4, 4); g.fillRect(x + 5, 15, 4, 4); }
        if (face == 6) { g.fillRect(x + 5, 10, 4, 4); g.fillRect(x + 15, 10, 4, 4); }
    }

    private static void drawSlot(Graphics2D g, int x, int symbol) {
        panel(g, x + 1, 1, 29, 30, 0xDDBA65, 0x202B45);
        int[] colors = {0xCCD1DB, 0xDBE4E8, 0xCC7D4C, 0xFFD65E, 0x42DF8A, 0x6AE7EB};
        g.setColor(new Color(colors[symbol]));
        if (symbol == 0) {
            g.fillPolygon(new int[]{x + 10, x + 19, x + 23, x + 18, x + 9}, new int[]{10, 8, 17, 23, 20}, 5);
        } else if (symbol < 4) {
            g.fillPolygon(new int[]{x + 9, x + 23, x + 26, x + 5}, new int[]{11, 11, 22, 22}, 4);
            g.setColor(new Color(colors[symbol]).brighter());
            g.fillRect(x + 10, 11, 12, 3);
        } else {
            g.fillPolygon(new int[]{x + 10, x + 22, x + 26, x + 16, x + 6}, new int[]{8, 8, 14, 25, 14}, 5);
            g.setColor(new Color(0xE3FFEF));
            g.drawLine(x + 11, 10, x + 21, 10); g.drawLine(x + 11, 11, x + 16, 21);
        }
    }
}
