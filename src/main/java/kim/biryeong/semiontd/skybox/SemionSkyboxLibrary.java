package kim.biryeong.semiontd.skybox;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class SemionSkyboxLibrary {
    static final int FOG_BYPASS_ALPHA = 252;

    private final List<SemionSkybox> skyboxes;
    private final Map<String, SemionSkybox> byId;

    public SemionSkyboxLibrary(List<SemionSkybox> skyboxes) {
        this.skyboxes = List.copyOf(skyboxes);
        Map<String, SemionSkybox> indexed = new LinkedHashMap<>();
        for (SemionSkybox skybox : this.skyboxes) {
            indexed.put(skybox.id(), skybox);
        }
        this.byId = Map.copyOf(indexed);
    }

    public static SemionSkyboxLibrary load(Path skyboxDir, Logger logger) {
        try {
            Files.createDirectories(skyboxDir);
        } catch (IOException exception) {
            logger.warn("Failed to create Semion TD skybox directory {}; skyboxes are disabled.", skyboxDir, exception);
            return empty();
        }

        try (var paths = Files.walk(skyboxDir)) {
            List<Path> textures = paths
                    .filter(Files::isRegularFile)
                    .filter(SemionSkyboxLibrary::isPng)
                    .sorted(Comparator.comparing(path -> skyboxDir.relativize(path).toString()))
                    .toList();
            Map<String, SemionSkybox> loaded = new LinkedHashMap<>();
            for (Path texture : textures) {
                readSkybox(skyboxDir, texture, logger).ifPresent(skybox -> {
                    if (loaded.putIfAbsent(skybox.id(), skybox) != null) {
                        logger.warn("Skipping duplicate Semion TD skybox id {} from {}.", skybox.id(), texture);
                    }
                });
            }
            return new SemionSkyboxLibrary(List.copyOf(loaded.values()));
        } catch (IOException exception) {
            logger.warn("Failed to scan Semion TD skybox directory {}; skyboxes are disabled.", skyboxDir, exception);
            return empty();
        }
    }

    public static SemionSkyboxLibrary empty() {
        return new SemionSkyboxLibrary(List.of());
    }

    private static Optional<SemionSkybox> readSkybox(Path skyboxDir, Path texture, Logger logger) {
        try {
            Path relative = skyboxDir.relativize(texture);
            String id = sanitizeId(relative);
            if (id.isBlank()) {
                logger.warn("Skipping Semion TD skybox with unusable file name: {}", texture);
                return Optional.empty();
            }
            byte[] data = normalizeTexture(texture);
            return Optional.of(new SemionSkybox(
                    id,
                    displayName(relative),
                    ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "skybox/" + id),
                    data
            ));
        } catch (IOException | IllegalArgumentException exception) {
            logger.warn("Skipping invalid Semion TD skybox texture {}.", texture, exception);
            return Optional.empty();
        }
    }

    private static byte[] normalizeTexture(Path texture) throws IOException {
        BufferedImage source = ImageIO.read(texture.toFile());
        if (source == null) {
            throw new IOException("Unsupported PNG image.");
        }
        if (source.getWidth() != source.getHeight() * 2) {
            throw new IOException("Skybox texture must use a 2:1 atlas, but was "
                    + source.getWidth() + "x" + source.getHeight() + ".");
        }

        BufferedImage normalized = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int color = source.getRGB(x, y);
                int alpha = color >>> 24;
                normalized.setRGB(x, y, alpha == 0 ? 0 : (FOG_BYPASS_ALPHA << 24) | (color & 0x00FFFFFF));
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(normalized, "png", output)) {
            throw new IOException("PNG encoder is unavailable.");
        }
        return output.toByteArray();
    }

    private static boolean isPng(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private static String sanitizeId(Path relativePath) {
        String raw = relativePath.toString().replace('\\', '/');
        if (raw.toLowerCase(Locale.ROOT).endsWith(".png")) {
            raw = raw.substring(0, raw.length() - 4);
        }
        raw = raw.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean separator = false;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (character == '/') {
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '/') {
                    builder.append('/');
                }
                separator = false;
            } else if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-') {
                builder.append(character);
                separator = false;
            } else if (!separator && !builder.isEmpty() && builder.charAt(builder.length() - 1) != '/') {
                builder.append('_');
                separator = true;
            }
        }
        while (!builder.isEmpty() && (builder.charAt(builder.length() - 1) == '_'
                || builder.charAt(builder.length() - 1) == '/')) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    private static String displayName(Path relativePath) {
        String name = relativePath.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replace('_', ' ').replace('-', ' ').trim();
    }

    public List<SemionSkybox> skyboxes() {
        return skyboxes;
    }

    public Optional<SemionSkybox> skybox(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<SemionSkybox> defaultSkybox() {
        return skyboxes.stream().findFirst();
    }

    public boolean isEmpty() {
        return skyboxes.isEmpty();
    }
}
