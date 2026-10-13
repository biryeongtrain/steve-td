package kim.biryeong.semiontd.ui;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import de.tomalbrc.avatarrenderer.impl.AvatarRenderer;
import de.tomalbrc.avatarrenderer.impl.SkinLoader;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.network.chat.Component;

final class UiPlayerAvatarService {
    private static final ConcurrentMap<SmallAvatarKey, Component> SMALL_AVATAR_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> AVATAR_LOAD_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService AVATAR_LOADER = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().name("semiontd-avatar-loader-", 0).factory());

    private UiPlayerAvatarService() {}

    static Component avatarComponent(String playerName, AvatarVariant variant) {
        SmallAvatarKey key = new SmallAvatarKey(playerName, variant);
        Component cached = SMALL_AVATAR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        loadAvatar(playerName);
        SmallAvatarKey defaultKey = new SmallAvatarKey("Steve", variant);
        return SMALL_AVATAR_CACHE.computeIfAbsent(defaultKey, UiPlayerAvatarService::defaultSmallAvatar);
    }

    static boolean isRemoteProfileName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9') && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static void loadAvatar(String playerName) {
        if (!isRemoteProfileName(playerName) || !AVATAR_LOAD_REQUESTS.add(playerName)) {
            return;
        }
        AVATAR_LOADER.execute(() -> {
            try {
                SkinLoader.load(playerName).ifPresent(skin -> {
                    for (AvatarVariant variant : AvatarVariant.values()) {
                        SmallAvatarKey key = new SmallAvatarKey(playerName, variant);
                        SMALL_AVATAR_CACHE.put(key, AvatarRenderer.asTextComponent(
                                avatarImage(skin, variant),
                                variant.yOffset()
                        ));
                    }
                });
            } catch (RuntimeException ignored) {
                // Keep the bundled Steve fallback.
            }
        });
    }

    private static Component defaultSmallAvatar(SmallAvatarKey key) {
        try (var stream = AvatarRendererMod.class.getResourceAsStream("/steve.png")) {
            if (stream == null) {
                return Component.empty();
            }
            BufferedImage skin = javax.imageio.ImageIO.read(stream);
            if (skin == null) {
                return Component.empty();
            }
            return AvatarRenderer.asTextComponent(avatarImage(skin, key.variant()), key.variant().yOffset());
        } catch (java.io.IOException exception) {
            return Component.empty();
        }
    }

    private static BufferedImage avatarImage(BufferedImage skin, AvatarVariant variant) {
        BufferedImage face = new BufferedImage(variant.imageSize(), variant.imageSize(), BufferedImage.TYPE_INT_ARGB);
        boolean hasFaceOverlay = skin.getHeight() >= 64;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int color = skinPixel(skin, 8 + x, 8 + y);
                if (hasFaceOverlay) {
                    int overlay = skinPixel(skin, 40 + x, 8 + y);
                    if ((overlay >>> 24) > 16) {
                        color = overlay;
                    }
                }
                if ((color >>> 24) != 0) {
                    int targetX = 1 + x * variant.pixelScale();
                    int targetY = 1 + y * variant.pixelScale();
                    for (int dy = 0; dy < variant.pixelScale(); dy++) {
                        for (int dx = 0; dx < variant.pixelScale(); dx++) {
                            face.setRGB(targetX + dx, targetY + dy, color);
                        }
                    }
                }
            }
        }

        BufferedImage outlined = new BufferedImage(variant.imageSize(), variant.imageSize(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < face.getHeight(); y++) {
            for (int x = 0; x < face.getWidth(); x++) {
                if ((face.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ox = x + dx;
                        int oy = y + dy;
                        if (ox >= 0 && ox < outlined.getWidth() && oy >= 0 && oy < outlined.getHeight()
                                && (outlined.getRGB(ox, oy) >>> 24) == 0) {
                            outlined.setRGB(ox, oy, 0xFF000000);
                        }
                    }
                }
            }
        }
        for (int y = 0; y < face.getHeight(); y++) {
            for (int x = 0; x < face.getWidth(); x++) {
                int color = face.getRGB(x, y);
                if ((color >>> 24) != 0) {
                    outlined.setRGB(x, y, color);
                }
            }
        }
        return outlined;
    }

    private static int skinPixel(BufferedImage skin, int x, int y) {
        if (x < 0 || y < 0 || x >= skin.getWidth() || y >= skin.getHeight()) {
            return 0;
        }
        return skin.getRGB(x, y);
    }

    enum AvatarVariant {
        COMPACT(1, 10, 25),
        RESULT(2, 18, 20);

        private final int pixelScale;
        private final int imageSize;
        private final int yOffset;

        AvatarVariant(int pixelScale, int imageSize, int yOffset) {
            this.pixelScale = pixelScale;
            this.imageSize = imageSize;
            this.yOffset = yOffset;
        }

        int pixelScale() {
            return pixelScale;
        }

        int imageSize() {
            return imageSize;
        }

        int yOffset() {
            return yOffset;
        }
    }

    private record SmallAvatarKey(String playerName, AvatarVariant variant) {
    }

}
