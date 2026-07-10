package kim.biryeong.semiontd.api.area;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record AreaVfxSpec(ResourceLocation styleId, AreaVfxRenderPolicy renderPolicy) {
    private static final AreaVfxSpec NONE = new AreaVfxSpec(AreaVfxStyles.NONE, AreaVfxRenderPolicy.ON_CHANGE);

    public AreaVfxSpec {
        Objects.requireNonNull(styleId, "styleId");
        Objects.requireNonNull(renderPolicy, "renderPolicy");
    }

    public static AreaVfxSpec none() {
        return NONE;
    }

    public static AreaVfxSpec onTrigger(ResourceLocation styleId) {
        return new AreaVfxSpec(styleId, AreaVfxRenderPolicy.ON_TRIGGER);
    }

    public static AreaVfxSpec onChange(ResourceLocation styleId) {
        return new AreaVfxSpec(styleId, AreaVfxRenderPolicy.ON_CHANGE);
    }
}
