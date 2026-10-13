package kim.biryeong.semiontd.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

public interface RuntimeArenaFixture extends CustomTestMethodInvoker {
    @Override
    default void invokeTestMethod(GameTestHelper context, Method method) {
        var bounds = context.getBounds();
        int minX = Mth.floor(bounds.minX) >> 4;
        int minZ = Mth.floor(bounds.minZ) >> 4;
        int maxX = Mth.floor(Math.nextDown(bounds.maxX)) >> 4;
        int maxZ = Mth.floor(Math.nextDown(bounds.maxZ)) >> 4;
        context.startSequence().thenWaitUntil(() -> {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    context.assertTrue(context.getLevel().areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z)),
                            "Arena entity sections must be loaded and ticking before spawning combat entities: " + x + "," + z);
                }
            }
        }).thenExecute(() -> invokeReady(context, this, method));
    }

    static void invokeReady(GameTestHelper context, Object target, Method method) {
        try {
            method.invoke(target, context);
        } catch (InvocationTargetException failure) {
            throw invocationFailure(context, method, failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw invocationFailure(context, method, failure);
        }
    }

    private static GameTestAssertException invocationFailure(GameTestHelper context, Method method, Throwable cause) {
        if (cause instanceof GameTestAssertException assertion) {
            return assertion;
        }
        var failure = new GameTestAssertException(
                Component.literal(method.getName() + ": " + cause), (int) context.getTick());
        failure.initCause(cause);
        return failure;
    }
}
