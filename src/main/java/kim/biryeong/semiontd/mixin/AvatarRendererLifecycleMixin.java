package kim.biryeong.semiontd.mixin;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AvatarRendererMod.class, remap = false)
public abstract class AvatarRendererLifecycleMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/Executors;newFixedThreadPool(I)Ljava/util/concurrent/ExecutorService;"))
    private static ExecutorService semion$managedAvatarExecutor(int threads) {
        var executor = Executors.newFixedThreadPool(threads,
                Thread.ofPlatform().daemon().name("semion-avatar-renderer-", 0).factory());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> executor.shutdown());
        return executor;
    }
}
