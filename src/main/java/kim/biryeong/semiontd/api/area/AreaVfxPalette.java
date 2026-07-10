package kim.biryeong.semiontd.api.area;

import java.util.Objects;

public record AreaVfxPalette(AreaVfxParticle primary, AreaVfxParticle accent) {
    public AreaVfxPalette {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(accent, "accent");
    }
}
