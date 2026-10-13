package kim.biryeong.semiontd.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.advancement.SemionAdvancementFilter;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 26.3 loads advancements as a registry; the old ServerAdvancementManager.apply hook is gone.
@Mixin(FileToIdConverter.class)
abstract class ServerAdvancementManagerMixin {
    @Shadow @Final private String prefix;

    @Inject(method = "listMatchingResources", at = @At("RETURN"), cancellable = true)
    private void semiontd$keepSemionAdvancementsOnly(
            ResourceManager resourceManager,
            CallbackInfoReturnable<Map<Identifier, Resource>> callback
    ) {
        if (!"advancement".equals(prefix)) {
            return;
        }
        FileToIdConverter converter = (FileToIdConverter) (Object) this;
        Map<Identifier, Resource> resources = new LinkedHashMap<>(callback.getReturnValue());
        Map<Identifier, Optional<Identifier>> parents = new LinkedHashMap<>();
        for (var entry : resources.entrySet()) {
            Identifier id = converter.fileToId(entry.getKey());
            if (!id.getNamespace().equals(SemionTd.MOD_ID)) {
                continue;
            }
            Optional<Identifier> parent = Optional.empty();
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("parent")) {
                    parent = Optional.of(Identifier.parse(json.get("parent").getAsString()));
                }
            } catch (IOException | RuntimeException exception) {
                // Keep malformed Semion resources for the normal registry loader to diagnose.
                SemionTd.LOGGER.warn("Could not inspect advancement parent for {}; leaving registry validation to Minecraft.", id, exception);
            }
            parents.put(id, parent);
        }
        var retained = SemionAdvancementFilter.retainedIds(parents);
        resources.keySet().removeIf(path -> !retained.contains(converter.fileToId(path)));
        callback.setReturnValue(resources);
    }
}
