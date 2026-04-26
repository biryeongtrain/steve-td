package kim.biryeong.semionTd.map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class LobbyWorld {
    private final Runnable unloadAction;
    private final ServerLevel world;
    private final Vec3 spawn;

    public LobbyWorld(Runnable unloadAction, ServerLevel world, Vec3 spawn) {
        this.unloadAction = unloadAction;
        this.world = world;
        this.spawn = spawn;
    }

    public ServerLevel world() {
        return world;
    }

    public Vec3 spawn() {
        return spawn;
    }

    public void unload() {
        unloadAction.run();
    }
}