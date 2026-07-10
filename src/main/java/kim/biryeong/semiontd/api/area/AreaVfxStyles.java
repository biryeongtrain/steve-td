package kim.biryeong.semiontd.api.area;

import kim.biryeong.semiontd.SemionTd;
import net.minecraft.resources.ResourceLocation;

public final class AreaVfxStyles {
    public static final ResourceLocation NONE = id("none");
    public static final ResourceLocation SPLASH = id("splash");
    public static final ResourceLocation PULSE = id("pulse");
    public static final ResourceLocation CORPSE_EXPLOSION = id("corpse_explosion");
    public static final ResourceLocation BUFF = id("buff");
    public static final ResourceLocation DEBUFF = id("debuff");

    private AreaVfxStyles() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
