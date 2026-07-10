package kim.biryeong.semiontd.entity.tower.vfx;

import java.util.Objects;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record VfxLaneKey(ResourceKey<Level> level, TeamId teamId, int laneId) {
    public VfxLaneKey {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(teamId, "teamId");
    }
}
