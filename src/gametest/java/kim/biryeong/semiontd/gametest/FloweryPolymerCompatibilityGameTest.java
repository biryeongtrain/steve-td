package kim.biryeong.semiontd.gametest;

import com.faboslav.friendsandfoes.common.api.MoobloomVariantManager;
import me.drex.fafpatch.impl.entity.holder.MoobloomElementHolder;
import me.drex.fafpatch.impl.entity.model.EntityModels;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class FloweryPolymerCompatibilityGameTest {
    @GameTest
    public void everyFloweryVariantHasAdultAndBabyPolymerModels(GameTestHelper context) {
        var flowery = FabricLoader.getInstance().getModContainer("flowerymooblooms").orElseThrow();
        var friends = FabricLoader.getInstance().getModContainer("friendsandfoes").orElseThrow();
        int checked = 0;
        for (var variant : MoobloomVariantManager.MOOBLOOM_VARIANT_MANAGER.getMoobloomVariants()) {
            for (boolean baby : new boolean[] {false, true}) {
                var state = new MoobloomElementHolder.RenderState(variant.getName(), baby);
                if (!EntityModels.MOOBLOOM.containsKey(state)) {
                    context.fail(Component.literal("Missing Polymer Moobloom model: " + state));
                    return;
                }
                String texture = "assets/friendsandfoes/textures/entity/moobloom/moobloom_"
                        + variant.getName() + (baby ? "_baby" : "") + ".png";
                if (flowery.findPath(texture).isEmpty() && friends.findPath(texture).isEmpty()) {
                    context.fail(Component.literal("Missing Flowery texture: " + texture));
                    return;
                }
                checked++;
            }
        }
        if (checked <= 2) {
            context.fail(Component.literal("Flowery variants were not registered before Polymer models loaded"));
            return;
        }
        context.succeed();
    }
}
