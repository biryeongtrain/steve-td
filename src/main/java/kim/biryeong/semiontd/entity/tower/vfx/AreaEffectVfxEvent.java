package kim.biryeong.semiontd.entity.tower.vfx;

import java.util.Objects;
import kim.biryeong.semiontd.api.area.AreaVfxContext;

public record AreaEffectVfxEvent(VfxLaneKey lane, AreaVfxContext visual) {
    public AreaEffectVfxEvent {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(visual, "visual");
    }
}
