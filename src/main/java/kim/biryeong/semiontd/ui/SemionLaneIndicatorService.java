package kim.biryeong.semiontd.ui;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.gcbserver.packet.s2c.GCBParticleS2CPacket;
import kim.biryeong.gcbserver.player.GCBPlayer;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class SemionLaneIndicatorService {
    private static final DustParticleOptions DIRECTION_PARTICLE = new DustParticleOptions(0xFFD54F, 2.25F);
    private static final int DIRECTION_ARROW_COUNT = 4;
    private static final int DIRECTION_LOOP_TICKS = 200;
    private static final double DIRECTION_HEIGHT = 1.0;
    private static final double DIRECTION_WING_LENGTH = 2.5;
    private static final double DIRECTION_WING_WIDTH = 1.75;

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

    public static void showDirection(ServerPlayer player, PlayerLane lane, int animationTick) {
        if (player == null || lane == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (DirectionArrow arrow : directionArrows(lane.laneLayout(), animationTick)) {
            drawDirectionLine(player, level, arrow.leftWing, arrow.head);
            drawDirectionLine(player, level, arrow.rightWing, arrow.head);
        }
    }

    static List<DirectionArrow> directionArrows(LaneRegionLayout layout, int animationTick) {
        double offset = Math.floorMod(animationTick, DIRECTION_LOOP_TICKS) / (double) DIRECTION_LOOP_TICKS;
        List<DirectionArrow> arrows = new ArrayList<>(DIRECTION_ARROW_COUNT);
        for (int index = 0; index < DIRECTION_ARROW_COUNT; index++) {
            double progress = (offset + index / (double) DIRECTION_ARROW_COUNT) % 1.0;
            Vec3 head = layout.positionAt(progress).add(0.0, DIRECTION_HEIGHT, 0.0);
            Vec3 previous = layout.positionAt(Math.max(0.0, progress - 0.025)).add(0.0, DIRECTION_HEIGHT, 0.0);
            Vec3 forward = horizontalDirection(previous, head);
            if (forward.lengthSqr() == 0.0) {
                Vec3 next = layout.positionAt(Math.min(1.0, progress + 0.025)).add(0.0, DIRECTION_HEIGHT, 0.0);
                forward = horizontalDirection(head, next);
            }
            Vec3 wingCenter = head.subtract(forward.scale(DIRECTION_WING_LENGTH));
            Vec3 side = new Vec3(-forward.z, 0.0, forward.x).scale(DIRECTION_WING_WIDTH);
            arrows.add(new DirectionArrow(head, wingCenter.add(side), wingCenter.subtract(side)));
        }
        return List.copyOf(arrows);
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 direction = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        return direction.lengthSqr() == 0.0 ? Vec3.ZERO : direction.normalize();
    }

    private static void drawDirectionLine(ServerPlayer player, ServerLevel level, Vec3 from, Vec3 to) {
        for (int index = 0; index <= 8; index++) {
            Vec3 point = from.lerp(to, index / 8.0);
            level.sendParticles(player, DIRECTION_PARTICLE, true, true,
                    point.x, point.y, point.z, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    record DirectionArrow(Vec3 head, Vec3 leftWing, Vec3 rightWing) {
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
