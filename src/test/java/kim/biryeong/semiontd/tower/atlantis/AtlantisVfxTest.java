package kim.biryeong.semiontd.tower.atlantis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxPalette;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStylePlanner;
import kim.biryeong.semiontd.tower.area.AreaVfxStyleRegistryImpl;
import kim.biryeong.semiontd.tower.area.BuiltinAreaVfxStyles;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Atlantis styles are referenced by id from {@link AtlantisTower}, so a style that fails to
 * register does not break the build — it silently renders nothing in game. These tests pin the
 * registration and assert each planner actually emits geometry.
 */
class AtlantisVfxTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stylesRegisterAlongsideTheBuiltinsWithoutIdCollisions() {
        AreaVfxStyleRegistryImpl registry = new AreaVfxStyleRegistryImpl();
        BuiltinAreaVfxStyles.register(registry);
        AtlantisVfx.register(registry);

        assertTrue(registry.find(AtlantisVfx.PRESSURE_ZONE).isPresent(),
                "the pressure zone style must be resolvable by the id AtlantisTower requests");
        assertTrue(registry.find(AtlantisVfx.WATER_PRESSURE).isPresent(),
                "the waterPressure style must be resolvable by the id AtlantisTower requests");
    }

    @Test
    void pressureZoneDrawsABoundaryThatIsVisibleWithNoTargets() {
        // The zone sits on the path away from the turtle, and is rendered ON_TRIGGER, so it has to
        // draw its outline even when the scan caught nothing.
        RecordingOutput output = plan(AtlantisVfx.PRESSURE_ZONE, List.of());

        assertFalse(output.circles.isEmpty(), "an empty zone must still draw its boundary");
        assertTrue(output.circles.stream().anyMatch(radius -> radius >= 2.4),
                "the outer ring must trace the zone radius, got " + output.circles);
        assertFalse(output.lines.isEmpty(), "the zone walls give the field height");
    }

    @Test
    void waterPressureDrawsABurstAndReachesEveryTargetItHit() {
        Vec3 first = new Vec3(1.0, 64.0, 0.0);
        Vec3 second = new Vec3(-1.0, 64.0, 1.0);
        RecordingOutput output = plan(AtlantisVfx.WATER_PRESSURE, List.of(first, second));

        assertFalse(output.spheres.isEmpty(), "the burst needs a core");
        assertFalse(output.circles.isEmpty(), "the burst needs an expanding rim");
        assertFalse(output.trails.isEmpty(), "the burst needs outward rays");
        assertTrue(output.lines.size() >= 2,
                "every monster the burst reached must get a connecting line, got " + output.lines.size());
    }

    private static RecordingOutput plan(ResourceLocation styleId, List<Vec3> applied) {
        AreaVfxStyleRegistryImpl registry = new AreaVfxStyleRegistryImpl();
        AtlantisVfx.register(registry);
        AreaVfxStylePlanner planner = registry.find(styleId).orElseThrow();

        Vec3 centre = new Vec3(0.0, 64.0, 0.0);
        AreaVfxContext context = new AreaVfxContext(
                ResourceLocation.fromNamespaceAndPath("semion-td", "test_effect"),
                styleId,
                UUID.nameUUIDFromBytes("atlantis-vfx-source".getBytes()),
                ResourceLocation.fromNamespaceAndPath("semion-td", "atlantis_turtle_t3"),
                testPalette(),
                centre,
                centre,
                2.5,
                applied,
                applied.size(),
                applied.size(),
                0,
                0L
        );
        RecordingOutput output = new RecordingOutput();
        planner.plan(context, output);
        assertNotNull(output);
        return output;
    }

    private static AreaVfxPalette testPalette() {
        AreaVfxParticle particle = new AreaVfxParticle(
                new DustParticleOptions(0xFFFFFF, 1.0F),
                ResourceLocation.fromNamespaceAndPath("minecraft", "bubble")
        );
        return new AreaVfxPalette(particle, particle);
    }

    /** Captures the geometry a planner emits so the tests can assert shape rather than pixels. */
    private static final class RecordingOutput implements AreaVfxOutput {
        private final List<Double> circles = new ArrayList<>();
        private final List<Double> spheres = new ArrayList<>();
        private final List<Vec3> lines = new ArrayList<>();
        private final List<Vec3> trails = new ArrayList<>();

        @Override
        public void line(AreaVfxParticle particle, Vec3 start, Vec3 end, int points, boolean essential) {
            lines.add(end);
        }

        @Override
        public void circle(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            circles.add(radius);
        }

        @Override
        public void sphere(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            spheres.add(radius);
        }

        @Override
        public void trail(AreaVfxParticle particle, Vec3 start, Vec3 control, Vec3 end, int points, boolean essential) {
            trails.add(end);
        }
    }
}
