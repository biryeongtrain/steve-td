package kim.biryeong.semiontd.api.area;

import net.minecraft.world.phys.Vec3;

public interface AreaVfxOutput {
    void line(AreaVfxParticle particle, Vec3 start, Vec3 end, int points, boolean essential);

    void circle(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential);

    void sphere(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential);

    void trail(AreaVfxParticle particle, Vec3 start, Vec3 control, Vec3 end, int points, boolean essential);
}
