package kim.biryeong.semiontd.mixin;

import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerAdvancementManager.class)
abstract class ServerAdvancementManagerMixin {
    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD")
    )
    private void semiontd$removeVanillaAdvancements(
            Map<ResourceLocation, Advancement> advancements,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci
    ) {
        advancements.keySet().removeIf(id -> id.getNamespace().equals("minecraft"));
        while (advancements.entrySet().removeIf(entry -> entry.getValue().parent()
                .filter(parent -> !advancements.containsKey(parent))
                .isPresent())) {
        }
    }
}
