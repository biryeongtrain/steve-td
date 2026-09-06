package kim.biryeong.semiontd.tower.pirate;

import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Short, event-driven visual feedback for pirate tower abilities. */
final class PirateTowerVfx {
    private PirateTowerVfx() {
    }

    static void showChestAcceleration(SemionTowerEntity tower) {
        if (tower == null || !(tower.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                tower.getX(), tower.getY() + Math.max(0.35, tower.getBbHeight() * 0.45), tower.getZ(),
                12, 0.25, 0.35, 0.25, 0.015
        );
    }

    static void showAdmiralDiamondGain(SemionTowerEntity tower) {
        if (tower == null || !(tower.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                tower.getX(), tower.getY() + Math.max(0.5, tower.getBbHeight() * 0.55), tower.getZ(),
                16, 0.42, Math.max(0.45, tower.getBbHeight() * 0.42), 0.42, 0.02
        );
    }

    static void showAdmiralStatGain(SemionTowerEntity tower) {
        if (tower == null || !(tower.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(
                ParticleTypes.END_ROD,
                tower.getX(), tower.getY() + Math.max(0.5, tower.getBbHeight() * 0.55), tower.getZ(),
                14, 0.42, Math.max(0.45, tower.getBbHeight() * 0.42), 0.42, 0.015
        );
    }

    static void showAnchorGuard(SemionTowerEntity tower) {
        if (tower == null || !(tower.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(
                ParticleTypes.BUBBLE,
                tower.getX(), tower.getY() + tower.getBbHeight() + 0.25, tower.getZ(),
                10, 0.28, 0.18, 0.28, 0.01
        );
    }
}
