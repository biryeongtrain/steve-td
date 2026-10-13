package kim.biryeong.semiontd.compat;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class AvatarRendererLifecycleTest implements net.fabricmc.api.ModInitializer {
    private static ExecutorService executor;
    private static java.util.concurrent.Future<Boolean> worker;

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var field = AvatarRendererMod.class.getDeclaredField("EXECUTOR");
                field.setAccessible(true);
                executor = (ExecutorService) field.get(null);
                worker = executor.submit(() -> Thread.currentThread().isDaemon()
                        && Thread.currentThread().getName().startsWith("semion-avatar-renderer-"));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Actual avatar executor must be available", failure);
            }
        });
    }

    @GameTest
    public void avatarWorkerRunsWithoutKeepingTheStoppedServerAlive(GameTestHelper helper) throws Exception {
        helper.assertTrue(worker.get(5, TimeUnit.SECONDS),
                Component.literal("Actual avatar tasks must use the managed daemon worker factory"));
        helper.assertTrue(!executor.isShutdown(), Component.literal("Avatar executor must remain usable while server runs"));
        helper.succeed();
    }
}
