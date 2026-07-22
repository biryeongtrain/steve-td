package kim.biryeong.semiontd.tower.ocean;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class OceanVfx {
    private static final int STREAM_POINTS = 5;

    private OceanVfx() {
    }

    static void showWaterTransfer(ServerLevel level, Vec3 source, List<OceanTower> targets, boolean burst) {
        if (level == null || source == null || targets == null || targets.isEmpty()) {
            return;
        }
        level.sendParticles(
                ParticleTypes.BUBBLE_POP,
                source.x,
                source.y,
                source.z,
                burst ? 8 : 3,
                0.18,
                0.18,
                0.18,
                0.02
        );
        for (OceanTower target : targets) {
            Vec3 destination = new Vec3(
                    target.position().x() + 0.5,
                    target.position().y() + 1.2,
                    target.position().z() + 0.5
            );
            Vec3 offset = destination.subtract(source);
            for (int point = 1; point <= STREAM_POINTS; point++) {
                Vec3 position = source.add(offset.scale(point / (double) (STREAM_POINTS + 1)));
                level.sendParticles(
                        ParticleTypes.NAUTILUS,
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
            level.sendParticles(
                    ParticleTypes.SPLASH,
                    destination.x,
                    destination.y,
                    destination.z,
                    burst ? 5 : 2,
                    0.14,
                    0.12,
                    0.14,
                    0.02
            );
        }
    }
}
