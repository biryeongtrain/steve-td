package kim.biryeong.semiontd.tower.succubus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class SuccubusVfxTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allStylesRegisterAndEmitFiniteBudgetedGeometry() {
        CapturingRegistry registry = new CapturingRegistry();
        SuccubusVfx.register(registry);

        for (ResourceLocation id : List.of(SuccubusVfx.STACK, SuccubusVfx.SLEEP,
                SuccubusVfx.SLEEP_SMOKE, SuccubusVfx.ABSORB)) {
            CountingOutput output = new CountingOutput();
            registry.find(id).orElseThrow().plan(context(id), output);
            assertTrue(output.instructions > 0, id + " must emit geometry");
            assertTrue(output.points <= 100, id + " exceeds its particle instruction budget");
            assertTrue(output.finite, id + " emitted a non-finite coordinate");
            if (id.equals(SuccubusVfx.SLEEP_SMOKE)) {
                assertFalse(output.essential, "sleep smoke must yield to the non-essential particle budget");
            }
        }
    }

    private static AreaVfxContext context(ResourceLocation style) {
        AreaVfxParticle particle = new AreaVfxParticle(new DustParticleOptions(0xFFFFFF, 1.0F),
                ResourceLocation.fromNamespaceAndPath("minecraft", "witch"));
        return new AreaVfxContext(ResourceLocation.fromNamespaceAndPath("semion-td", "test"), style,
                UUID.randomUUID(), ResourceLocation.fromNamespaceAndPath("semion-td", "succubus"),
                new AreaVfxPalette(particle, particle), new Vec3(0.0, 64.0, 0.0),
                new Vec3(3.0, 64.0, 0.0), 3.0, List.of(), 1, 1, 0, 1L);
    }

    private static final class CapturingRegistry implements AreaVfxStyleRegistry {
        private final Map<ResourceLocation, AreaVfxStylePlanner> planners = new HashMap<>();
        @Override public void register(ResourceLocation id, AreaVfxStylePlanner planner) {planners.put(id, planner);}
        @Override public Optional<AreaVfxStylePlanner> find(ResourceLocation id) {return Optional.ofNullable(planners.get(id));}
        @Override public boolean frozen() {return false;}
    }

    private static final class CountingOutput implements AreaVfxOutput {
        private int instructions;
        private int points;
        private boolean finite = true;
        private boolean essential;

        @Override public void line(AreaVfxParticle particle, Vec3 start, Vec3 end, int count, boolean essential) {
            record(count, essential, start, end);
        }
        @Override public void circle(AreaVfxParticle particle, Vec3 center, double radius, int count, boolean essential) {
            record(count, essential, center); finite &= Double.isFinite(radius);
        }
        @Override public void sphere(AreaVfxParticle particle, Vec3 center, double radius, int count, boolean essential) {
            record(count, essential, center); finite &= Double.isFinite(radius);
        }
        @Override public void trail(AreaVfxParticle particle, Vec3 start, Vec3 control, Vec3 end, int count, boolean essential) {
            record(count, essential, start, control, end);
        }
        private void record(int count, boolean essential, Vec3... positions) {
            instructions++;
            points += count;
            this.essential |= essential;
            for (Vec3 position : positions) {
                finite &= Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
            }
        }
    }
}
