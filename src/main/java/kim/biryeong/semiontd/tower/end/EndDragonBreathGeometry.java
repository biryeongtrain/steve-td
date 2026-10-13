package kim.biryeong.semiontd.tower.end;

import net.minecraft.world.phys.Vec3;

final class EndDragonBreathGeometry {
    private static final Vec3 HOVER_MOUTH = hoverMouthOffset();

    private EndDragonBreathGeometry() {}

    static Vec3 mouth(Vec3 body, Vec3 direction) {
        return body.add(direction.normalize().scale(-HOVER_MOUTH.z)).add(0, HOVER_MOUTH.y, 0);
    }

    static Vec3 impact(Vec3 mouth, Vec3 ground, Vec3 direction) {
        Vec3 forward = direction.normalize();
        double drop = Math.max(0, mouth.y - ground.y);
        double advance = Math.max(drop, ground.subtract(mouth).dot(forward));
        return ground.add(forward.scale(advance - ground.subtract(mouth).dot(forward)));
    }

    static Vec3 point(Vec3 mouth, Vec3 ground, Vec3 direction, double progress) {
        return mouth.lerp(impact(mouth, ground, direction), Math.clamp(progress, 0, 1));
    }

    private static Vec3 hoverMouthOffset() {
        double y = 20;
        double z = -12;
        for (int part = 0; part < 5; part++) {
            double angle = Math.toRadians(part * 7.5);
            y += Math.sin(angle) * 10;
            z -= Math.cos(angle) * 10;
        }
        double head = Math.PI / 4;
        y += 4 * Math.cos(head) + 24 * Math.sin(head);
        z += 4 * Math.sin(head) - 24 * Math.cos(head);
        double hoverBob = .175;
        double rootPitch = Math.toRadians(hoverBob * 2);
        double worldY = 1.501 - ((hoverBob - 2) * 16 + y * Math.cos(rootPitch) - z * Math.sin(rootPitch)) / 16;
        double worldZ = 1 + (-48 + y * Math.sin(rootPitch) + z * Math.cos(rootPitch)) / 16;
        return new Vec3(0, worldY, worldZ);
    }
}
