package kim.biryeong.semiontd.tower.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxPalette;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.entity.tower.vfx.BuilderPalette;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BuiltinAreaVfxStylesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void buffAndDebuffUseOppositeVerticalDirectionsAtChangedTargets() {
        AreaVfxStyleRegistryImpl registry = new AreaVfxStyleRegistryImpl();
        BuiltinAreaVfxStyles.register(registry);
        Vec3 target = new Vec3(2.0, 3.0, 4.0);

        RecordingOutput buff = new RecordingOutput();
        registry.find(AreaVfxStyles.BUFF).orElseThrow().plan(context(AreaVfxStyles.BUFF, target), buff);
        assertEquals(1, buff.lines.size());
        assertTrue(buff.lines.getFirst().end().y > buff.lines.getFirst().start().y);
        assertEquals(1, buff.trails.size());

        RecordingOutput debuff = new RecordingOutput();
        registry.find(AreaVfxStyles.DEBUFF).orElseThrow().plan(context(AreaVfxStyles.DEBUFF, target), debuff);
        assertEquals(1, debuff.lines.size());
        assertTrue(debuff.lines.getFirst().end().y < debuff.lines.getFirst().start().y);
        assertEquals(1, debuff.trails.size());
    }

    @Test
    void dragonBreathExpandsFromMagentaCenterToPurpleOuterRing() {
        AreaVfxStyleRegistryImpl registry = new AreaVfxStyleRegistryImpl();
        BuiltinAreaVfxStyles.register(registry);
        RecordingOutput output = new RecordingOutput();

        registry.find(AreaVfxStyles.DRAGON_BREATH).orElseThrow()
                .plan(context(AreaVfxStyles.DRAGON_BREATH, Vec3.ZERO), output);

        assertEquals(3, output.circles.size());
        assertTrue(output.circles.get(0).radius() < output.circles.get(1).radius());
        assertTrue(output.circles.get(1).radius() < output.circles.get(2).radius());
        assertEquals(24, output.trails.size());

        DustParticleOptions centerColor = (DustParticleOptions) output.circles.get(0).particle().vanilla();
        DustParticleOptions outerColor = (DustParticleOptions) output.circles.get(2).particle().vanilla();
        assertTrue(centerColor.getColor().x > outerColor.getColor().x);
        assertTrue(centerColor.getColor().z > 0.8F);
        assertTrue(outerColor.getColor().z > 0.7F);

        TrailCall firstRay = output.trails.getFirst();
        TrailCall outerHalf = output.trails.get(1);
        assertTrue(firstRay.start().distanceTo(Vec3.ZERO) < firstRay.end().distanceTo(Vec3.ZERO));
        assertTrue(outerHalf.start().distanceTo(Vec3.ZERO) < outerHalf.end().distanceTo(Vec3.ZERO));
    }

    @Test
    void insectExplosionKeepsItsRangeAndBoundedOutwardDroplets() {
        AreaVfxStyleRegistryImpl registry = new AreaVfxStyleRegistryImpl();
        BuiltinAreaVfxStyles.register(registry);
        for (double radius : List.of(2.0, 3.0, 20.0)) {
            RecordingOutput output = new RecordingOutput();
            AreaVfxContext visual = context(AreaVfxStyles.INSECT_EXPLOSION, Vec3.ZERO, radius);
            registry.find(AreaVfxStyles.INSECT_EXPLOSION).orElseThrow().plan(visual, output);
            assertEquals(1, output.circles.size());
            assertEquals(radius, output.circles.getFirst().radius());
            assertEquals(BuilderPalette.INSECT.areaPalette().primary(), output.circles.getFirst().particle());
            assertEquals(6, output.trails.size());
            assertEquals(7, output.spheres.size());
            assertTrue(output.points <= 202, "Large radii must not grow particle cost without a limit.");
            for (TrailCall trail : output.trails) {
                assertEquals(BuilderPalette.INSECT.areaPalette().accent(), trail.particle());
                assertTrue(trail.start().distanceTo(Vec3.ZERO) < trail.end().distanceTo(Vec3.ZERO));
                assertTrue(Math.hypot(trail.end().x, trail.end().z) < radius);
            }
        }
    }

    private static AreaVfxContext context(ResourceLocation styleId, Vec3 target) {
        return context(styleId, target, 3.0);
    }

    private static AreaVfxContext context(ResourceLocation styleId, Vec3 target, double radius) {
        AreaVfxParticle particle = new AreaVfxParticle(
                ParticleTypes.CRIT,
                ResourceLocation.fromNamespaceAndPath("minecraft", "crit")
        );
        return new AreaVfxContext(
                ResourceLocation.fromNamespaceAndPath("test", "effect"),
                styleId,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ResourceLocation.fromNamespaceAndPath("test", "tower"),
                styleId.equals(AreaVfxStyles.INSECT_EXPLOSION)
                        ? BuilderPalette.INSECT.areaPalette() : new AreaVfxPalette(particle, particle),
                Vec3.ZERO,
                Vec3.ZERO,
                radius,
                List.of(target),
                1,
                1,
                0,
                20L
        );
    }

    private record LineCall(Vec3 start, Vec3 end) {
    }

    private record CircleCall(AreaVfxParticle particle, double radius) {
    }

    private record TrailCall(AreaVfxParticle particle, Vec3 start, Vec3 end) {
    }

    private record SphereCall(Vec3 center, double radius) {
    }

    private static final class RecordingOutput implements AreaVfxOutput {
        private final List<LineCall> lines = new ArrayList<>();
        private final List<CircleCall> circles = new ArrayList<>();
        private final List<TrailCall> trails = new ArrayList<>();
        private final List<SphereCall> spheres = new ArrayList<>();
        private int points;

        @Override
        public void line(AreaVfxParticle particle, Vec3 start, Vec3 end, int points, boolean essential) {
            lines.add(new LineCall(start, end));
            this.points += points;
        }

        @Override
        public void circle(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            circles.add(new CircleCall(particle, radius));
            this.points += points;
        }

        @Override
        public void sphere(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            spheres.add(new SphereCall(center, radius));
            this.points += points;
        }

        @Override
        public void trail(
                AreaVfxParticle particle,
                Vec3 start,
                Vec3 control,
                Vec3 end,
                int points,
                boolean essential
        ) {
            trails.add(new TrailCall(particle, start, end));
            this.points += points;
        }
    }
}
