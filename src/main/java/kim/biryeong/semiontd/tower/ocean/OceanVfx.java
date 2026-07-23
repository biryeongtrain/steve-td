package kim.biryeong.semiontd.tower.ocean;

import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class OceanVfx {
    private static final DustParticleOptions WATER_GLOW = new DustParticleOptions(0xB2EBF2, 0.55F);
    private static final DustParticleOptions DRY_DUST = new DustParticleOptions(0xE6B566, 1.05F);
    private static final int SUPPLY_POINTS = 9;
    private static final int RING_POINTS = 14;

    private OceanVfx() {
    }

    static void showWaterSourcePulse(ServerLevel level, Vec3 source, int tier, boolean burst) {
        if (level == null || source == null) {
            return;
        }
        showRing(level, WATER_GLOW, source, burst ? 0.58 : 0.38, RING_POINTS);
        level.sendParticles(
                ParticleTypes.SPLASH,
                source.x,
                source.y,
                source.z,
                burst ? tier * 5 : tier + 2,
                0.28,
                0.1,
                0.28,
                0.04
        );
    }

    static void showWaterSupply(ServerLevel level, Vec3 source, List<OceanTower> targets, boolean burst) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        showWaterSupplyToPositions(level, source, targets.stream().map(OceanVfx::towerDestination).toList(), burst);
    }

    static void showDehydrated(ServerLevel level, Vec3 base) {
        if (level == null || base == null) {
            return;
        }
        showRing(level, DRY_DUST, base, 0.52, RING_POINTS);
        showRing(level, DRY_DUST, base.add(0.0, 0.55, 0.0), 0.34, 10);
        level.sendParticles(ParticleTypes.WHITE_ASH, base.x, base.y + 0.45, base.z, 5, 0.28, 0.3, 0.28, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, base.x, base.y + 0.65, base.z, 3, 0.18, 0.22, 0.18, 0.008);
    }

    public static void showWaterSupplyDebug(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 forward = horizontalLook(player);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 source = player.position().add(forward.scale(2.5)).add(0.0, 1.0, 0.0);
        List<Vec3> destinations = List.of(
                player.position().add(forward.scale(5.0)).add(right.scale(-1.25)).add(0.0, 0.9, 0.0),
                player.position().add(forward.scale(5.5)).add(right.scale(1.25)).add(0.0, 0.9, 0.0)
        );
        showWaterSourcePulse(level, source, 3, true);
        showWaterSupplyToPositions(level, source, destinations, true);
    }

    public static void showDehydratedDebug(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 forward = horizontalLook(player);
        Vec3 base = player.position().add(forward.scale(3.6)).add(0.0, 0.12, 0.0);
        showDehydrated(level, base);
    }

    private static void showWaterSupplyToPositions(ServerLevel level, Vec3 source, List<Vec3> destinations, boolean burst) {
        if (level == null || source == null || destinations == null || destinations.isEmpty()) {
            return;
        }
        for (Vec3 destination : destinations) {
            Vec3 offset = destination.subtract(source);
            for (int point = 1; point <= SUPPLY_POINTS; point++) {
                double progress = point / (double) (SUPPLY_POINTS + 1);
                Vec3 position = source.add(offset.scale(progress)).add(0.0, Math.sin(Math.PI * progress) * 0.7, 0.0);
                level.sendParticles(
                        point % 3 == 0 ? WATER_GLOW : ParticleTypes.SPLASH,
                        position.x,
                        position.y,
                        position.z,
                        burst ? 2 : 1,
                        0.025,
                        0.025,
                        0.025,
                        0.01
                );
            }
            showRing(level, WATER_GLOW, destination, burst ? 0.42 : 0.3, RING_POINTS);
            level.sendParticles(ParticleTypes.SPLASH, destination.x, destination.y, destination.z,
                    burst ? 7 : 4, 0.14, 0.08, 0.14, 0.025);
        }
    }

    private static void showRing(ServerLevel level, DustParticleOptions particle, Vec3 center, double radius, int points) {
        for (int point = 0; point < points; point++) {
            double angle = Math.PI * 2.0 * point / points;
            level.sendParticles(
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius,
                    1,
                    0.0,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }

    private static Vec3 towerDestination(OceanTower target) {
        return new Vec3(target.position().x() + 0.5, target.position().y() + 1.2, target.position().z() + 0.5);
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        return horizontal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
    }
}
