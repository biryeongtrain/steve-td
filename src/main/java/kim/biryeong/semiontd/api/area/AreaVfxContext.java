package kim.biryeong.semiontd.api.area;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record AreaVfxContext(
        ResourceLocation effectId,
        ResourceLocation styleId,
        UUID sourceTowerId,
        ResourceLocation sourceTowerTypeId,
        AreaVfxPalette palette,
        Vec3 source,
        Vec3 center,
        double radius,
        List<Vec3> sampledAppliedPositions,
        int candidateCount,
        int appliedCount,
        int killedCount,
        long gameTime
) {
    public AreaVfxContext {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(styleId, "styleId");
        Objects.requireNonNull(sourceTowerId, "sourceTowerId");
        Objects.requireNonNull(sourceTowerTypeId, "sourceTowerTypeId");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(center, "center");
        sampledAppliedPositions = sampledAppliedPositions == null ? List.of() : List.copyOf(sampledAppliedPositions);
        candidateCount = Math.max(0, candidateCount);
        appliedCount = Math.max(0, appliedCount);
        killedCount = Math.max(0, killedCount);
    }
}
