package kim.biryeong.semiontd.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class PlayerTeleportTransitions {
    private PlayerTeleportTransitions() {
    }

    public static TeleportTransition preservingFacing(ServerLevel world, Vec3 position, Vec3 deltaMovement, ServerPlayer player) {
        return preservingRotation(world, position, deltaMovement, player.getYRot(), player.getXRot());
    }

    public static TeleportTransition preservingRotation(ServerLevel world, Vec3 position, Vec3 deltaMovement, float yRot, float xRot) {
        return new TeleportTransition(
                world,
                position,
                deltaMovement,
                yRot,
                xRot,
                TeleportTransition.DO_NOTHING
        );
    }
}
