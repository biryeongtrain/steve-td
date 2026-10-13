package kim.biryeong.semiontd.api.area;

import java.util.Objects;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;

public record AreaVfxParticle(ParticleOptions vanilla, Identifier gcbParticleId) {
    public AreaVfxParticle {
        Objects.requireNonNull(vanilla, "vanilla");
        Objects.requireNonNull(gcbParticleId, "gcbParticleId");
    }
}
