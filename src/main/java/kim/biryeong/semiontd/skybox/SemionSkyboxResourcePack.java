package kim.biryeong.semiontd.skybox;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.Objects;
import java.util.function.Supplier;
import kim.biryeong.semiontd.SemionTd;
import org.slf4j.Logger;

public final class SemionSkyboxResourcePack {
    private static final java.util.List<String> SHADER_NAMES = java.util.List.of("entity", "item");
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
                    "north": {"uv": [12.001, 8.001, 15.999, 15.999], "texture": "#0"},
                    "east": {"uv": [8.001, 8.001, 11.999, 15.999], "texture": "#0"},
                    "south": {"uv": [4.001, 8.001, 7.999, 15.999], "texture": "#0"},
                    "west": {"uv": [0.001, 8.001, 3.999, 15.999], "texture": "#0"},
                    "up": {"uv": [4.001, 0.001, 7.999, 7.999], "texture": "#0"},
                    "down": {"uv": [8.001, 7.999, 11.999, 0.001], "texture": "#0"}
                  }
                }
              ]
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
        // Preserve the generated 26.3 pipelines, including Danta HUD and OIT.
        for (String name : SHADER_NAMES) {
            String prefix = "assets/minecraft/shaders/core/" + name;
            String vertex = patchVertexShader(builder.getStringDataOrSource(prefix + ".vsh"), logger);
            String fragment = patchFragmentShader(builder.getStringDataOrSource(prefix + ".fsh"), logger);
            builder.addStringData(prefix + ".vsh", vertex);
            builder.addStringData(prefix + ".fsh", fragment);
        }
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
        requireModernShader(source);
        if (source.contains("semionSkyboxFarDepth")) {
            return source;
        }
        String main = "void main() {";
        String projection = "gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);";
        if (!source.contains(main) || !source.contains(projection)) {
            throw new IllegalStateException("Cannot merge skybox vertex shader: projection entry point missing");
        }
        String patched = source;
        if (!patched.contains("semionSkyboxOriginalColor")) {
            requireFreeVarying(patched);
            patched = patched.replace(main, "layout(location = 8) out vec4 semionSkyboxOriginalColor;\n\n"
                    + main + "\n    semionSkyboxOriginalColor = Color;");
        }
        if (!patched.contains("uniform sampler2D Sampler0;")) {
            patched = patched.replace(main, "uniform sampler2D Sampler0;\n\n" + main);
        }
        return patched.replace(projection, projection + "\n" + """
                    ivec2 semionSkyboxTextureSize = textureSize(Sampler0, 0);
                    ivec2 semionSkyboxTexel = clamp(ivec2(UV0 * vec2(semionSkyboxTextureSize)),
                            ivec2(0), semionSkyboxTextureSize - ivec2(1));
                    bool semionSkyboxFarDepth = abs(texelFetch(Sampler0, semionSkyboxTexel, 0).a
                            - (252.0 / 255.0)) < (0.5 / 255.0);
                    if (semionSkyboxFarDepth && ProjMat[3][3] == 0.0) {
                        #ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
                        gl_Position.z = gl_Position.w * 1.0e-6;
                        #else
                        gl_Position.z = gl_Position.w * (-1.0 + 1.0e-6);
                        #endif
                    }
                """);
    }

    static String patchFragmentShader(String source, Logger logger) {
        requireModernShader(source);
        if (source.contains("semionSkyboxOriginalColor")) {
            return source;
        }
        requireFreeVarying(source);
        String main = "void main() {";
        if (!source.contains(main) || !source.contains("Sampler0") || !source.contains("texCoord0")) {
            throw new IllegalStateException("Cannot merge skybox fragment shader: texture entry point missing");
        }

        // Keep cutoff, overlay, glint and every OIT phase in the base shader. Only
        // the alpha-252 atlas pixels bypass cardinal light, the lightmap and fog.
        String patched = replaceInputUses(source, "vertexColor", "semionSkyboxOriginalColor");
        patched = replaceInputUses(patched, "vertexPerFaceColorBack", "semionSkyboxOriginalColor");
        patched = replaceInputUses(patched, "vertexPerFaceColorFront", "semionSkyboxOriginalColor");
        patched = replaceInputUses(patched, "lightMapColor", "vec4(1.0)");
        patched = replaceInputUses(patched, "overlayColor", "vec4(0.0, 0.0, 0.0, 1.0)");
        patched = patched.replace("apply_fog(", "semion_skybox_fog(");

        String support = """
                vec4 semion_skybox_fog(vec4 color, float sphericalDistance, float cylindricalDistance,
                        float environmentalStart, float environmentalEnd, float renderStart,
                        float renderEnd, vec4 fogColor) {
                    return semionSkyboxMarker ? color : apply_fog(color, sphericalDistance,
                            cylindricalDistance, environmentalStart, environmentalEnd,
                            renderStart, renderEnd, fogColor);
                }

                """;
        // The marker must also exist in OIT_ALPHA_ONLY, even when the first
        // base helper function is inside an alpha-phase preprocessor guard.
        java.util.regex.Matcher header = java.util.regex.Pattern.compile(
                "(?m)^#(?:version|extension)[^\\n]*(?:\\n|$)").matcher(patched);
        int declarationPosition = 0;
        while (header.find()) {
            declarationPosition = header.end();
        }
        String declarations = "layout(location = 8) in vec4 semionSkyboxOriginalColor;\n"
                + "bool semionSkyboxMarker;\n\n";
        patched = patched.substring(0, declarationPosition) + declarations
                + patched.substring(declarationPosition);
        // The fog wrapper shares the base helper's preprocessor context.
        java.util.regex.Matcher function = java.util.regex.Pattern.compile(
                "(?m)^\\w+\\s+\\w+\\s*\\([^;]*?\\)\\s*\\{").matcher(patched);
        int firstFunction = function.find() ? function.start() : patched.indexOf(main);
        patched = patched.substring(0, firstFunction) + support + patched.substring(firstFunction);
        return patched.replace(main, main + "\n    semionSkyboxMarker = abs(texture(Sampler0, texCoord0).a"
                + " - (252.0 / 255.0)) < (0.5 / 255.0);");
    }

    private static String replaceInputUses(String source, String input, String skyboxValue) {
        if (!source.contains("in vec4 " + input + ";")) {
            return source;
        }
        String expression = "(semionSkyboxMarker ? " + skyboxValue + " : " + input + ")";
        String patched = source.replaceAll("\\b" + input + "\\b", java.util.regex.Matcher.quoteReplacement(expression));
        return patched.replace("in vec4 " + expression + ";", "in vec4 " + input + ";");
    }

    private static void requireModernShader(String source) {
        if (source == null || !source.contains("#version 330")) {
            throw new IllegalStateException("Semion skyboxes require the generated Minecraft 26.3 shader sources");
        }
    }

    private static void requireFreeVarying(String source) {
        if (java.util.regex.Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*8\\s*\\)").matcher(source).find()) {
            throw new IllegalStateException("Skybox varying location 8 conflicts with an existing shader extension");
        }
    }
}
