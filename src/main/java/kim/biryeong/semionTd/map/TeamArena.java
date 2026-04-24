package kim.biryeong.semionTd.map;

import kim.biryeong.semionTd.game.TeamId;
import net.minecraft.server.level.ServerLevel;

public final class TeamArena {
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
}
