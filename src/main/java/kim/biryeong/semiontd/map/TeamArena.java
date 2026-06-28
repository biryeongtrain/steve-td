package kim.biryeong.semiontd.map;

import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class TeamArena {
    private static final int TELEPORT_PRELOAD_CHUNK_RADIUS = 1;
    private final TeamId teamId;
    private final Runnable unloadAction;
    private final ServerLevel world;
    private final ArenaLayout layout;

    public TeamArena(TeamId teamId, Runnable unloadAction, ServerLevel world, ArenaLayout layout) {
        this.teamId = teamId;
        this.unloadAction = unloadAction;
        this.world = world;
        this.layout = layout;
    }

    public TeamId teamId() {
        return teamId;
    }

    public ServerLevel world() {
        return world;
    }

    public ArenaLayout layout() {
        return layout;
    }

    public void unload() {
        unloadAction.run();
    }

    public void preloadForTeleport(Vec3 position) {
        RuntimeWorldWarmup.warmChunksAround(world, BlockPos.containing(position), TELEPORT_PRELOAD_CHUNK_RADIUS);
    }
}
