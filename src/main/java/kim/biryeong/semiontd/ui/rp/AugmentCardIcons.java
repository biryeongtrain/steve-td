package kim.biryeong.semiontd.ui.rp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import kim.biryeong.semiontd.augment.AugmentDisplayRole;
import kim.biryeong.semiontd.augment.AugmentRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class AugmentCardIcons {
    public static final String SOURCE = "/semiontd/ui/augment-icons.png";
    public static final Identifier FONT = Identifier.fromNamespaceAndPath("semion-td", "augment_cards");
    private static final int FIRST = 0xE300;
    private static final int HEIGHT = 48;
    private static final Atlas ATLAS = load();
    private static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT))
            .withColor(0xFFFFFF).withShadowColor(0).withBold(false).withItalic(false);

    private AugmentCardIcons() {
    }

    public static boolean available() {
        return ATLAS != null;
    }

    public static void init() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> {
            if (ATLAS == null) return;
            builder.addData("assets/semion-td/textures/font/augment-icons.png", ATLAS.bytes());
            builder.addData("assets/semion-td/font/augment_cards.json",
                    fontDefinition().toString().getBytes(StandardCharsets.UTF_8));
        });
    }

    public static Icon icon(AugmentRarity rarity, AugmentDisplayRole role) {
        if (ATLAS == null) return new Icon(Component.empty(), 0);
        int index = rarity.ordinal() * 3 + role.ordinal();
        return new Icon(Component.literal(Character.toString(FIRST + index)).setStyle(STYLE), ATLAS.advances()[index]);
    }

    public static JsonObject fontDefinition() {
        JsonObject bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", "semion-td:font/augment-icons.png");
        bitmap.addProperty("height", HEIGHT);
        bitmap.addProperty("ascent", 46);
        JsonArray rows = new JsonArray();
        for (int row = 0; row < 3; row++) {
            StringBuilder chars = new StringBuilder();
            for (int column = 0; column < 3; column++) chars.appendCodePoint(FIRST + row * 3 + column);
            rows.add(chars.toString());
        }
        bitmap.add("chars", rows);
        JsonArray providers = new JsonArray();
        providers.add(bitmap);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        return font;
    }

    public static void validateDimensions(int width, int height) {
        if (width < 3 || width != height || width % 3 != 0) {
            throw new IllegalArgumentException("Augment icons must be a square 3 by 3 atlas");
        }
    }

    private static Atlas load() {
        try (var input = AugmentCardIcons.class.getResourceAsStream(SOURCE)) {
            if (input == null) return null;
            byte[] bytes = input.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new IllegalArgumentException("Augment icon atlas is not a PNG image");
            validateDimensions(image.getWidth(), image.getHeight());
            int cell = image.getWidth() / 3;
            int[] advances = new int[9];
            for (int index = 0; index < 9; index++) {
                int right = 0;
                for (int x = 0; x < cell; x++) {
                    for (int y = 0; y < cell; y++) {
                        if ((image.getRGB(index % 3 * cell + x, index / 3 * cell + y) >>> 24) != 0) {
                            right = Math.max(right, x + 1);
                        }
                    }
                }
                advances[index] = (int) (right * ((float) HEIGHT / cell) + 0.5F) + 1;
            }
            return new Atlas(bytes, advances);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load augment icon atlas", exception);
        }
    }

    private record Atlas(byte[] bytes, int[] advances) {
    }

    public record Icon(Component text, int width) {
    }
}
