package kim.biryeong.semiontd.gametest;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SemionAdvancementGameTest {
    @GameTest
    public void vanillaAdvancementsAreNotLoaded(GameTestHelper context) {
        List<ResourceLocation> vanillaAdvancements = context.getLevel().getServer().getAdvancements()
                .getAllAdvancements()
                .stream()
                .map(AdvancementHolder::id)
                .filter(id -> id.getNamespace().equals("minecraft"))
                .toList();
        if (!vanillaAdvancements.isEmpty()) {
            context.fail(Component.literal("Vanilla advancements should be removed: " + vanillaAdvancements));
            return;
        }
        Map<ResourceLocation, AdvancementHolder> loadedAdvancements = context.getLevel().getServer().getAdvancements()
                .getAllAdvancements()
                .stream()
                .collect(java.util.stream.Collectors.toMap(AdvancementHolder::id, advancement -> advancement));
        List<ResourceLocation> missingParents = loadedAdvancements.values().stream()
                .filter(advancement -> advancement.value().parent()
                        .filter(parent -> !loadedAdvancements.containsKey(parent))
                        .isPresent())
                .map(AdvancementHolder::id)
                .toList();
        if (!missingParents.isEmpty()) {
            context.fail(Component.literal("Advancements with removed parents should not remain: " + missingParents));
            return;
        }
        context.succeed();
    }
}
