package kim.biryeong.semiontd.tower.thunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxPalette;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStylePlanner;
import kim.biryeong.semiontd.api.area.AreaVfxStyleRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Geometry checks for the 람쥐썬더 area styles.
 *
 * <p>VFX has no in-game assertion: a bolt that ends a block short of its target, or one that emits
 * {@code NaN} because the source and the target occupy the same position, looks like nothing at all
 * rather than like a failure. These tests pin the two properties that make the styles readable —
 * bolts terminate exactly on what they are about, and every emitted coordinate is finite.
 */
class ThunderVfxTest {
    private static final Vec3 CENTER = new Vec3(10.0, 64.0, -4.0);
    private static final double RADIUS = 3.5;

    /** See {@code ThunderPowerTest}: touching Minecraft classes without this poisons the fork. */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bothStylesRegisterUnderTheThunderNamespace() {
        CapturingRegistry registry = new CapturingRegistry();
        ThunderVfx.register(registry);

        assertTrue(registry.planners.containsKey(ThunderVfx.ARC));
        assertTrue(registry.planners.containsKey(ThunderVfx.DISCHARGE));
        assertEquals("semion-td", ThunderVfx.ARC.getNamespace());
        assertEquals("semion-td", ThunderVfx.DISCHARGE.getNamespace());
    }

    @Test
    void everyArcBoltTerminatesExactlyOnItsTarget() {
        List<Vec3> hits = List.of(
                CENTER.add(2.0, 0.0, 0.5),
                CENTER.add(-1.5, 0.3, 2.2),
                CENTER.add(0.2, -0.1, -2.8)
        );
        CapturingOutput output = planArc(hits);

        for (Vec3 hit : hits) {
            Vec3 target = hit.add(0.0, 0.55, 0.0);
            assertTrue(output.hasLineEndingAt(target),
                    "a chain bolt that stops short of the monster reads as hitting nothing: " + target);
        }
    }

    @Test
    void arcBoltsStartFromTheStruckMonsterNotTheTower() {
        Vec3 hit = CENTER.add(2.0, 0.0, 0.5);
        CapturingOutput output = planArc(List.of(hit));

        // The origin is the struck monster lifted to chest height, which is what makes the splash
        // legible as "it jumped from there" rather than "the tower shot again".
        assertTrue(output.hasLineStartingAt(CENTER.add(0.0, 0.45, 0.0)));
    }

    @Test
    void arcEmitsOnlyFiniteCoordinates() {
        CapturingOutput output = planArc(List.of(
                CENTER.add(2.0, 0.0, 0.5),
                CENTER.add(-1.5, 0.3, 2.2)
        ));
        assertFalse(output.points.isEmpty());
        output.assertAllFinite();
    }

    @Test
    void dischargeEmitsOnlyFiniteCoordinates() {
        CapturingOutput output = plan(ThunderVfx.DISCHARGE, List.of(
                CENTER.add(1.0, 0.0, 1.0),
                CENTER.add(-2.0, 0.0, 0.4)
        ));
        assertFalse(output.points.isEmpty());
        output.assertAllFinite();
    }

    /**
     * A monster standing exactly on the effect centre is the degenerate case for the bolt maths:
     * the direction vector is undefined, so normalising it would produce {@code NaN} for every
     * point in the bolt.
     */
    @Test
    void aTargetAtTheExactCentreDoesNotProduceNaN() {
        CapturingOutput arc = planArc(List.of(CENTER.add(0.0, -0.45, 0.0)));
        arc.assertAllFinite();

        CapturingOutput discharge = plan(ThunderVfx.DISCHARGE, List.of(CENTER.add(0.0, -0.5, 0.0)));
        discharge.assertAllFinite();
    }

    @Test
    void dischargeIsDrawnEvenWhenItCaughtNothing() {
        CapturingOutput output = plan(ThunderVfx.DISCHARGE, List.of());
        assertFalse(output.points.isEmpty(),
                "the tank still died on that tile, so the burst has to be visible with zero hits");
        output.assertAllFinite();
    }

    // ------------------------------------------------------------------ harness

    private static CapturingOutput planArc(List<Vec3> hits) {
        return plan(ThunderVfx.ARC, hits);
    }

    private static CapturingOutput plan(ResourceLocation styleId, List<Vec3> hits) {
        CapturingRegistry registry = new CapturingRegistry();
        ThunderVfx.register(registry);
        AreaVfxStylePlanner planner = registry.planners.get(styleId);
        assertTrue(planner != null, "style not registered: " + styleId);

        CapturingOutput output = new CapturingOutput();
        planner.plan(context(styleId, hits), output);
        return output;
    }

    private static AreaVfxContext context(ResourceLocation styleId, List<Vec3> hits) {
        AreaVfxParticle particle = new AreaVfxParticle(
                new DustParticleOptions(0xFFFFFF, 1.0F),
                ResourceLocation.fromNamespaceAndPath("minecraft", "electric_spark")
        );
        return new AreaVfxContext(
                ResourceLocation.fromNamespaceAndPath("semion-td", "test_effect"),
                styleId,
                UUID.nameUUIDFromBytes("thunder-tower".getBytes()),
                ResourceLocation.fromNamespaceAndPath("semion-td", "thunder_squirrel_t3"),
                new AreaVfxPalette(particle, particle),
                CENTER,
                CENTER,
                RADIUS,
                hits,
                hits.size(),
                hits.size(),
                0,
                120L
        );
    }

    private static final class CapturingRegistry implements AreaVfxStyleRegistry {
        private final Map<ResourceLocation, AreaVfxStylePlanner> planners = new HashMap<>();

        @Override
        public void register(ResourceLocation id, AreaVfxStylePlanner planner) {
            planners.put(id, planner);
        }

        @Override
        public Optional<AreaVfxStylePlanner> find(ResourceLocation id) {
            return Optional.ofNullable(planners.get(id));
        }

        @Override
        public boolean frozen() {
            return false;
        }
    }

    private static final class CapturingOutput implements AreaVfxOutput {
        private final List<Vec3> points = new ArrayList<>();
        private final List<Vec3> lineStarts = new ArrayList<>();
        private final List<Vec3> lineEnds = new ArrayList<>();

        @Override
        public void line(AreaVfxParticle particle, Vec3 start, Vec3 end, int points, boolean essential) {
            lineStarts.add(start);
            lineEnds.add(end);
            record(start, end);
        }

        @Override
        public void circle(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            record(center);
            assertTrue(Double.isFinite(radius), "circle radius must be finite");
        }

        @Override
        public void sphere(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            record(center);
            assertTrue(Double.isFinite(radius), "sphere radius must be finite");
        }

        @Override
        public void trail(AreaVfxParticle particle, Vec3 start, Vec3 control, Vec3 end, int points, boolean essential) {
            lineStarts.add(start);
            lineEnds.add(end);
            record(start, control, end);
        }

        private void record(Vec3... values) {
            for (Vec3 value : values) {
                points.add(value);
            }
        }

        private boolean hasLineEndingAt(Vec3 expected) {
            return lineEnds.stream().anyMatch(end -> end.distanceTo(expected) < 1.0E-6);
        }

        private boolean hasLineStartingAt(Vec3 expected) {
            return lineStarts.stream().anyMatch(start -> start.distanceTo(expected) < 1.0E-6);
        }

        private void assertAllFinite() {
            for (Vec3 point : points) {
                assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z),
                        "non-finite particle position: " + point);
            }
        }
    }
}
