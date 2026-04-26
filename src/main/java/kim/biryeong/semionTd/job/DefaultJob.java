package kim.biryeong.semionTd.job;

import java.util.List;
import kim.biryeong.semionTd.SemionTd;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DefaultJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "recruit");

    public DefaultJob() {
        super(
                ID,
                Component.literal("Recruit"),
                List.of(Component.literal("Baseline job with no modifiers."))
        );
    }
}
