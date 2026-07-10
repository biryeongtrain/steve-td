package kim.biryeong.semiontd.tower.area;

import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;

public final class BuiltinAreaVfxStyles {
    private static final AreaVfxParticle BUFF_PARTICLE = particle(0x66D975, 1.35F, "happy_villager");
    private static final AreaVfxParticle DEBUFF_PARTICLE = particle(0xC04478, 1.5F, "witch");

    private BuiltinAreaVfxStyles() {
    }

    public static void register(AreaVfxStyleRegistryImpl registry) {
        registry.register(AreaVfxStyles.NONE, (context, output) -> {
        });
        registry.register(AreaVfxStyles.SPLASH, BuiltinAreaVfxStyles::splash);
        registry.register(AreaVfxStyles.PULSE, BuiltinAreaVfxStyles::pulse);
        registry.register(AreaVfxStyles.CORPSE_EXPLOSION, BuiltinAreaVfxStyles::explosion);
        registry.register(AreaVfxStyles.BUFF, BuiltinAreaVfxStyles::buff);
        registry.register(AreaVfxStyles.DEBUFF, BuiltinAreaVfxStyles::debuff);
    }

    private static void splash(AreaVfxContext context, AreaVfxOutput output) {
        int outline = outlinePoints(context.radius());
        output.circle(context.palette().primary(), context.center(), context.radius(), outline, true);
        output.circle(context.palette().accent(), context.center(), context.radius() * 0.72, Math.max(12, outline / 2), false);
        output.sphere(context.palette().accent(), context.center().add(0.0, 0.3, 0.0),
                Math.max(0.25, Math.min(0.55, context.radius() * 0.18)), 14, false);
        hitTrails(context, output, 12);
    }

    private static void pulse(AreaVfxContext context, AreaVfxOutput output) {
        int outline = outlinePoints(context.radius());
        output.circle(context.palette().primary(), context.center(), context.radius(), outline, true);
        output.sphere(context.palette().accent(), context.center().add(0.0, 0.2, 0.0),
                Math.max(0.35, context.radius() * 0.55), Math.max(16, outline / 2), false);
        hitTrails(context, output, 10);
    }

    private static void explosion(AreaVfxContext context, AreaVfxOutput output) {
        int outline = outlinePoints(context.radius());
        output.circle(context.palette().primary(), context.center(), context.radius(), outline, true);
        output.sphere(context.palette().accent(), context.center().add(0.0, 0.35, 0.0),
                context.radius(), Math.max(24, outline), false);
        hitTrails(context, output, 12);
    }

    private static void buff(AreaVfxContext context, AreaVfxOutput output) {
        int outline = Math.max(24, outlinePoints(context.radius()) / 2);
        output.circle(BUFF_PARTICLE, context.center().add(0.0, 0.08, 0.0), context.radius(), outline, true);
        for (var hit : context.sampledAppliedPositions()) {
            var base = hit.add(0.0, 0.08, 0.0);
            var top = hit.add(0.0, 1.35, 0.0);
            var control = context.center().lerp(hit, 0.5).add(0.0, 0.85, 0.0);
            output.trail(BUFF_PARTICLE, context.center().add(0.0, 0.3, 0.0), control, base, 12, false);
            output.line(BUFF_PARTICLE, base, top, 10, true);
            output.sphere(context.palette().accent(), top, 0.24, 8, false);
        }
    }

    private static void debuff(AreaVfxContext context, AreaVfxOutput output) {
        int outline = Math.max(24, outlinePoints(context.radius()) / 2);
        output.circle(DEBUFF_PARTICLE, context.center().add(0.0, 0.08, 0.0), context.radius(), outline, true);
        output.circle(DEBUFF_PARTICLE, context.center().add(0.0, 0.16, 0.0), context.radius() * 0.58,
                Math.max(14, outline / 2), false);
        for (var hit : context.sampledAppliedPositions()) {
            var top = hit.add(0.0, 1.35, 0.0);
            var base = hit.add(0.0, 0.08, 0.0);
            var control = context.center().lerp(hit, 0.5).add(0.0, 0.75, 0.0);
            output.trail(DEBUFF_PARTICLE, context.center().add(0.0, 0.3, 0.0), control, top, 12, false);
            output.line(DEBUFF_PARTICLE, top, base, 10, true);
            output.sphere(DEBUFF_PARTICLE, base.add(0.0, 0.2, 0.0), 0.24, 8, false);
        }
    }

    private static void hitTrails(AreaVfxContext context, AreaVfxOutput output, int points) {
        for (var hit : context.sampledAppliedPositions()) {
            output.line(context.palette().accent(), context.center().add(0.0, 0.25, 0.0), hit, points, false);
        }
    }

    private static int outlinePoints(double radius) {
        return Math.max(18, Math.min(96, (int) Math.ceil(radius * 18.0)));
    }

    private static AreaVfxParticle particle(int color, float scale, String id) {
        return new AreaVfxParticle(
                new DustParticleOptions(color, scale),
                ResourceLocation.fromNamespaceAndPath("minecraft", id)
        );
    }
}
