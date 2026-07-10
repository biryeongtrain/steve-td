package kim.biryeong.semiontd.api;

import java.util.Objects;
import kim.biryeong.semiontd.api.area.AreaEffectApi;
import kim.biryeong.semiontd.api.area.AreaVfxStyleRegistry;

public final class SemionTdApi {
    private static volatile AreaEffectApi areaEffects;
    private static volatile AreaVfxStyleRegistry areaVfxStyles;

    private SemionTdApi() {
    }

    public static AreaEffectApi areaEffects() {
        AreaEffectApi api = areaEffects;
        if (api == null) {
            throw new IllegalStateException("Semion TD area-effect API is not initialized yet");
        }
        return api;
    }

    public static AreaVfxStyleRegistry areaVfxStyles() {
        AreaVfxStyleRegistry registry = areaVfxStyles;
        if (registry == null) {
            throw new IllegalStateException("Semion TD area-VFX registry is not initialized yet");
        }
        return registry;
    }

    /** Internal bootstrap hook. Add-ons should use the accessor methods only. */
    public static synchronized void initializeInternal(AreaEffectApi effects, AreaVfxStyleRegistry styles) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(styles, "styles");
        if (areaEffects != null || areaVfxStyles != null) {
            throw new IllegalStateException("Semion TD API is already initialized");
        }
        areaEffects = effects;
        areaVfxStyles = styles;
    }
}
