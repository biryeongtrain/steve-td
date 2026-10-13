package kim.biryeong.semiontd.api.area;

import kim.biryeong.semiontd.SemionTd;
import net.minecraft.resources.Identifier;

public final class AreaVfxStyles {
    public static final Identifier NONE = id("none");
    public static final Identifier SPLASH = id("splash");
    public static final Identifier PULSE = id("pulse");
    public static final Identifier CORPSE_EXPLOSION = id("corpse_explosion");
    public static final Identifier INSECT_EXPLOSION = id("insect_explosion");
    public static final Identifier BUFF = id("buff");
    public static final Identifier DEBUFF = id("debuff");
    public static final Identifier DRAGON_BREATH = id("dragon_breath");

    private AreaVfxStyles() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
