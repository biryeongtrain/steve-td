package kim.biryeong.semiontd.skybox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.List;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

final class SemionSkyboxLibraryTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsAtlasAndNormalizesFogMarkerAlpha() throws Exception {
        BufferedImage source = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFF8040);
        source.setRGB(1, 0, 0x00000000);
        ImageIO.write(source, "png", tempDir.resolve("Red Nebula.png").toFile());

        SemionSkyboxLibrary library = SemionSkyboxLibrary.load(tempDir, LoggerFactory.getLogger("skybox-test"));

        assertEquals(1, library.skyboxes().size());
        SemionSkybox skybox = library.skyboxes().getFirst();
        assertEquals("red_nebula", skybox.id());
        assertEquals("Red Nebula", skybox.displayName());
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(skybox.textureData()));
        assertEquals(SemionSkyboxLibrary.FOG_BYPASS_ALPHA, normalized.getRGB(0, 0) >>> 24);
        assertEquals(0, normalized.getRGB(1, 0) >>> 24);
    }

    @Test
    void rejectsTexturesThatDoNotUseTwoToOneAtlas() throws Exception {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(source, "png", tempDir.resolve("invalid.png").toFile());

        SemionSkyboxLibrary library = SemionSkyboxLibrary.load(tempDir, LoggerFactory.getLogger("skybox-test"));

        assertTrue(library.isEmpty());
    }

    @Test
    void resourcePackContainsPerSkyboxModelAndFogShaders() throws Exception {
        BufferedImage source = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFFFFFF);
        ImageIO.write(source, "png", tempDir.resolve("space.png").toFile());
        SemionSkyboxLibrary library = SemionSkyboxLibrary.load(tempDir, LoggerFactory.getLogger("skybox-test"));
        CapturingBuilder builder = new CapturingBuilder();

        SemionSkyboxResourcePack.addToResourcePack(library, builder, LoggerFactory.getLogger("skybox-test"));

        assertNotNull(builder.getData("assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.fsh"));
        assertNotNull(builder.getData("assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.vsh"));
        assertNotNull(builder.getData("assets/semion-td/textures/item/skybox/space.png"));
        assertNotNull(builder.getData("assets/semion-td/models/item/skybox/space.json"));
        assertNotNull(builder.getData("assets/semion-td/items/skybox/space.json"));
    }

    @Test
    void vertexShaderMergePreservesExistingHudPatch() {
        String existing = """
                out vec4 vertexColor;
                //Hud
                void main() {
                    texCoord0 = UV0;
                    if (make_hud()) {
                        return;
                    }
                }
                """;

        String patched = SemionSkyboxResourcePack.patchVertexShader(existing, LoggerFactory.getLogger("skybox-test"));

        assertTrue(patched.contains("//Hud"));
        assertTrue(patched.contains("out vec4 originalColor;"));
        assertTrue(patched.contains("originalColor = Color;"));
    }

    @Test
    void serviceUsesReplacementLibrary() {
        SemionSkybox replacement = new SemionSkybox(
                "replacement",
                "Replacement",
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "skybox/replacement"),
                new byte[] {1}
        );
        SemionSkyboxService service = new SemionSkyboxService(
                SemionSkyboxLibrary.empty(),
                new kim.biryeong.semiontd.game.SemionGameManager()
        );

        service.replaceLibrary(new SemionSkyboxLibrary(List.of(replacement)));

        assertEquals(List.of(replacement), service.availableSkyboxes());
    }

    private static final class CapturingBuilder implements ResourcePackBuilder {
        private final Map<String, byte[]> data = new HashMap<>();

        @Override
        public boolean addData(String path, byte[] value) {
            data.put(path, value);
            return true;
        }

        @Override
        public boolean copyAssets(String modId) {
            return false;
        }

        @Override
        public boolean copyFromPath(Path path, String targetPrefix, boolean override) {
            return false;
        }

        @Override
        public byte @Nullable [] getData(String path) {
            return data.get(path);
        }

        @Override
        public byte @Nullable [] getDataOrSource(String path) {
            return data.get(path);
        }

        @Override
        public void forEachFile(BiConsumer<String, byte[]> consumer) {
            data.forEach(consumer);
        }

        @Override
        public boolean addAssetsSource(String modId) {
            return false;
        }

        @Override
        public void addWriteConverter(BiFunction<String, byte[], byte @Nullable []> converter) {
        }

        @Override
        public void addPreFinishTask(Consumer<ResourcePackBuilder> consumer) {
        }
    }
}
