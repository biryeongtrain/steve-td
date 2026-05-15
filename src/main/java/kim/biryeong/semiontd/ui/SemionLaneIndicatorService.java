package kim.biryeong.semiontd.ui;

import kim.biryeong.gcbserver.packet.s2c.GCBParticleS2CPacket;
import kim.biryeong.gcbserver.player.GCBPlayer;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SemionLaneIndicatorService {
    private SemionLaneIndicatorService() {
    }

    public static void showLane(ServerPlayer player, PlayerLane lane) {
        if (player == null || lane == null) {
            return;
        }
        if (player instanceof GCBPlayer gcbPlayer && gcbPlayer.gcb$hasMod()) {
            showGcbLane(player, lane.laneLayout());
            return;
        }
        showVanillaLane(player, lane.laneLayout());
    }

    private static void showGcbLane(ServerPlayer player, LaneRegionLayout layout) {
        BlockPos min = layout.laneArea().min();
        BlockPos max = layout.laneArea().max();
        double y = min.getY() + 1.1;
        var options = new GCBParticleS2CPacket.ShapeOptions(
                0.0,
                1.6,
                GCBParticleS2CPacket.Vec.UNIT_X,
                GCBParticleS2CPacket.Vec.UNIT_Y,
                GCBParticleS2CPacket.Vec.UNIT_Z,
                player.getUUID()
        );
        new GCBParticleS2CPacket(
                "minecraft:end_rod",
                GCBParticleS2CPacket.Vec.ZERO,
                1,
                0.0,
                true,
                "",
                new GCBParticleS2CPacket.Line(
                        0.25,
                        options,
                        new GCBParticleS2CPacket.Vec(min.getX() + 0.5, y, min.getZ() + 0.5),
                        new GCBParticleS2CPacket.Vec(max.getX() + 0.5, y, min.getZ() + 0.5),
                        new GCBParticleS2CPacket.Vec(max.getX() + 0.5, y, max.getZ() + 0.5),
                        new GCBParticleS2CPacket.Vec(min.getX() + 0.5, y, max.getZ() + 0.5),
                        new GCBParticleS2CPacket.Vec(min.getX() + 0.5, y, min.getZ() + 0.5)
                )
        ).send(player);
    }

    private static void showVanillaLane(ServerPlayer player, LaneRegionLayout layout) {
        BlockPos min = layout.laneArea().min();
        BlockPos max = layout.laneArea().max();
        double y = min.getY() + 1.1;
        drawVanillaLine(player, min.getX() + 0.5, y, min.getZ() + 0.5, max.getX() + 0.5, y, min.getZ() + 0.5);
        drawVanillaLine(player, max.getX() + 0.5, y, min.getZ() + 0.5, max.getX() + 0.5, y, max.getZ() + 0.5);
        drawVanillaLine(player, max.getX() + 0.5, y, max.getZ() + 0.5, min.getX() + 0.5, y, max.getZ() + 0.5);
        drawVanillaLine(player, min.getX() + 0.5, y, max.getZ() + 0.5, min.getX() + 0.5, y, min.getZ() + 0.5);
    }

    private static void drawVanillaLine(
            ServerPlayer player,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
        int points = Math.max(2, (int) Math.ceil(Math.hypot(endX - startX, endZ - startZ) / 2.0));
        for (int index = 0; index <= points; index++) {
            double t = index / (double) points;
            double x = startX + (endX - startX) * t;
            double y = startY + (endY - startY) * t;
            double z = startZ + (endZ - startZ) * t;
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(player, ParticleTypes.END_ROD, true, true, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }
}
