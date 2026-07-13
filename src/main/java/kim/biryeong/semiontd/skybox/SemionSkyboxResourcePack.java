package kim.biryeong.semiontd.skybox;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.Objects;
import java.util.function.Supplier;
import kim.biryeong.semiontd.SemionTd;
import org.slf4j.Logger;

public final class SemionSkyboxResourcePack {
    private static final String VERTEX_SHADER_PATH =
            "assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.vsh";
    private static final String FRAGMENT_SHADER_PATH =
            "assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.fsh";
    private static final String BASE_MODEL = """
            {
              "format_version": "1.21.6",
              "credit": "Semion TD skybox template",
              "textures": {
                "0": "minecraft:item/stick",
                "particle": "#0"
              },
              "elements": [
                {
                  "from": [16, 0, 0],
                  "to": [0, 16, 16],
                  "faces": {
                    "north": {"uv": [12, 8, 16, 16], "texture": "#0"},
                    "east": {"uv": [8, 8, 12, 16], "texture": "#0"},
                    "south": {"uv": [4, 8, 8, 16], "texture": "#0"},
                    "west": {"uv": [0, 8, 4, 16], "texture": "#0"},
                    "up": {"uv": [4, 0, 8, 8], "texture": "#0"},
                    "down": {"uv": [8, 8, 12, 0], "texture": "#0"}
                  }
                }
              ]
            }
            """;

    private static final String VERTEX_SHADER = """
            #version 150

            #moj_import <minecraft:light.glsl>
            #moj_import <minecraft:fog.glsl>
            #moj_import <minecraft:dynamictransforms.glsl>
            #moj_import <minecraft:projection.glsl>

            in vec3 Position;
            in vec4 Color;
            in vec2 UV0;
            in vec2 UV1;
            in ivec2 UV2;
            in vec3 Normal;

            uniform sampler2D Sampler2;

            out float sphericalVertexDistance;
            out float cylindricalVertexDistance;
            out vec4 vertexColor;
            out vec4 originalColor;
            out vec2 texCoord0;
            out vec2 texCoord1;
            out vec2 texCoord2;

            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

                sphericalVertexDistance = fog_spherical_distance(Position);
                cylindricalVertexDistance = fog_cylindrical_distance(Position);
                vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * texelFetch(Sampler2, UV2 / 16, 0);
                originalColor = Color;
                texCoord0 = UV0;
                texCoord1 = UV1;
                texCoord2 = UV2;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150

            #moj_import <minecraft:fog.glsl>
            #moj_import <minecraft:dynamictransforms.glsl>

            uniform sampler2D Sampler0;

            in float sphericalVertexDistance;
            in float cylindricalVertexDistance;
            in vec4 vertexColor;
            in vec4 originalColor;
            in vec2 texCoord0;
            in vec2 texCoord1;

            out vec4 fragColor;

            void main() {
                vec4 textureColor = texture(Sampler0, texCoord0);
                vec4 color = textureColor * vertexColor * ColorModulator;
                if (color.a < 0.1) {
                    discard;
                }
                if (abs(textureColor.a - (252.0 / 255.0)) < (0.5 / 255.0)) {
                    fragColor = textureColor * originalColor * ColorModulator;
                } else {
                    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
                }
            }
            """;

    private SemionSkyboxResourcePack() {
    }

    public static void register(SemionSkyboxLibrary library, Logger logger) {
        register(() -> library, logger);
    }

    public static void register(Supplier<SemionSkyboxLibrary> librarySupplier, Logger logger) {
        Objects.requireNonNull(librarySupplier, "librarySupplier");
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder ->
                addToResourcePack(librarySupplier.get(), builder, logger));
    }

    public static void addToResourcePack(SemionSkyboxLibrary library, ResourcePackBuilder builder, Logger logger) {
        if (library.isEmpty()) {
            return;
        }
        builder.addStringData(VERTEX_SHADER_PATH, patchVertexShader(builder.getStringDataOrSource(VERTEX_SHADER_PATH), logger));
        builder.addStringData(FRAGMENT_SHADER_PATH, patchFragmentShader(builder.getStringDataOrSource(FRAGMENT_SHADER_PATH), logger));
        builder.addStringData("assets/" + SemionTd.MOD_ID + "/models/item/skybox_base.json", BASE_MODEL);

        for (SemionSkybox skybox : library.skyboxes()) {
            String id = skybox.id();
            String textureId = SemionTd.MOD_ID + ":item/skybox/" + id;
            String modelId = SemionTd.MOD_ID + ":item/skybox/" + id;
            builder.addData(
                    "assets/" + SemionTd.MOD_ID + "/textures/item/skybox/" + id + ".png",
                    skybox.textureData()
            );
            builder.addStringData(
                    "assets/" + SemionTd.MOD_ID + "/models/item/skybox/" + id + ".json",
                    "{\n  \"parent\": \"" + SemionTd.MOD_ID + ":item/skybox_base\",\n"
                            + "  \"textures\": {\"0\": \"" + textureId + "\"}\n}\n"
            );
            builder.addStringData(
                    "assets/" + SemionTd.MOD_ID + "/items/skybox/" + id + ".json",
                    "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"" + modelId + "\"}\n}\n"
            );
        }
        logger.info("Added {} Semion TD skybox(es) to the generated resource pack.", library.skyboxes().size());
    }

    static String patchVertexShader(String source, Logger logger) {
        if (source == null || source.isBlank()) {
            return VERTEX_SHADER;
        }
        if (source.contains("out vec4 originalColor;")) {
            return source;
        }
        String patched = source.replace(
                "out vec4 vertexColor;",
                "out vec4 vertexColor;\nout vec4 originalColor;"
        ).replace(
                "    texCoord0 = UV0;",
                "    originalColor = Color;\n    texCoord0 = UV0;"
        );
        if (patched.equals(source) || !patched.contains("originalColor = Color;")) {
            logger.warn("Could not merge the Semion TD skybox vertex shader with the generated pack; using the 1.21.8 base shader.");
            return VERTEX_SHADER;
        }
        return patched;
    }

    static String patchFragmentShader(String source, Logger logger) {
        if (source == null || source.isBlank()) {
            return FRAGMENT_SHADER;
        }
        if (source.contains("252.0 / 255.0")) {
            return source;
        }
        String patched = source.replace(
                "in vec4 vertexColor;",
                "in vec4 vertexColor;\nin vec4 originalColor;"
        ).replace(
                "    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;",
                "    vec4 textureColor = texture(Sampler0, texCoord0);\n"
                        + "    vec4 color = textureColor * vertexColor * ColorModulator;"
        );
        String fogLine = "    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, "
                + "FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);";
        String fogBranch = "    if (abs(textureColor.a - (252.0 / 255.0)) < (0.5 / 255.0)) {\n"
                + "        fragColor = textureColor * originalColor * ColorModulator;\n"
                + "    } else {\n"
                + "        fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, "
                + "FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);\n"
                + "    }";
        patched = patched.replace(fogLine, fogBranch);
        if (patched.equals(source) || !patched.contains("252.0 / 255.0")) {
            logger.warn("Could not merge the Semion TD skybox fragment shader with the generated pack; using the 1.21.8 base shader.");
            return FRAGMENT_SHADER;
        }
        return patched;
    }
}
