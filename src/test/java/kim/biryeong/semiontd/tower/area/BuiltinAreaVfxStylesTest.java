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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BuiltinAreaVfxStylesTest {
    @Test
    void buffAndDebuffUseOppositeVerticalDirectionsAtChangedTargets() {
        AreaVfxStyleRegistryImpl registry = new AreaVfxStyleRegistryImpl();
        BuiltinAreaVfxStyles.register(registry);
        Vec3 target = new Vec3(2.0, 3.0, 4.0);

        RecordingOutput buff = new RecordingOutput();
        registry.find(AreaVfxStyles.BUFF).orElseThrow().plan(context(AreaVfxStyles.BUFF, target), buff);
        assertEquals(1, buff.lines.size());
        assertTrue(buff.lines.getFirst().end().y > buff.lines.getFirst().start().y);
        assertEquals(1, buff.trails);

        RecordingOutput debuff = new RecordingOutput();
        registry.find(AreaVfxStyles.DEBUFF).orElseThrow().plan(context(AreaVfxStyles.DEBUFF, target), debuff);
        assertEquals(1, debuff.lines.size());
        assertTrue(debuff.lines.getFirst().end().y < debuff.lines.getFirst().start().y);
        assertEquals(1, debuff.trails);
    }

    private static AreaVfxContext context(ResourceLocation styleId, Vec3 target) {
        AreaVfxParticle particle = new AreaVfxParticle(
                ParticleTypes.CRIT,
                ResourceLocation.fromNamespaceAndPath("minecraft", "crit")
        );
        return new AreaVfxContext(
                ResourceLocation.fromNamespaceAndPath("test", "effect"),
                styleId,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ResourceLocation.fromNamespaceAndPath("test", "tower"),
                new AreaVfxPalette(particle, particle),
                Vec3.ZERO,
                Vec3.ZERO,
                3.0,
                List.of(target),
                1,
                1,
                0,
                20L
        );
    }

    private record LineCall(Vec3 start, Vec3 end) {
    }

    private static final class RecordingOutput implements AreaVfxOutput {
        private final List<LineCall> lines = new ArrayList<>();
        private int trails;

        @Override
        public void line(AreaVfxParticle particle, Vec3 start, Vec3 end, int points, boolean essential) {
            lines.add(new LineCall(start, end));
        }

        @Override
        public void circle(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
        }

        @Override
        public void sphere(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
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
            trails++;
        }
    }
}
