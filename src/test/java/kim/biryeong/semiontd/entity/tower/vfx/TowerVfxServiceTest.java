package kim.biryeong.semiontd.entity.tower.vfx;

import kim.biryeong.gcbserver.packet.s2c.GCBParticleS2CPacket;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerVfxServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void samplesLineWithEndpointsAndFixedParticleBudget() {
        Vec3 start = new Vec3(1, 2, 3);
        Vec3 end = new Vec3(5, 6, 7);
        var points = TowerVfxService.collectLinePoints(start, end, 5);
        assertEquals(5, points.size());
        assertEquals(start, points.getFirst());
        assertEquals(new Vec3(3, 4, 5), points.get(2));
        assertEquals(end, points.getLast());
    }

    @Test
    void handlesEmptySingleAndCoincidentLineSamples() {
        Vec3 point = new Vec3(1, 2, 3);
        assertEquals(java.util.List.of(), TowerVfxService.collectLinePoints(point, Vec3.ZERO, 0));
        assertEquals(java.util.List.of(point), TowerVfxService.collectLinePoints(point, Vec3.ZERO, 1));
        assertEquals(java.util.Collections.nCopies(4, point), TowerVfxService.collectLinePoints(point, point, 4));
    }
    @Test
    void preservesDustColorAndScaleForGcb() {
        var payload = TowerVfxService.gcbPayload(
                new DustParticleOptions(0x512DA8, 1.15F),
                "minecraft:witch",
                new GCBParticleS2CPacket.Sphere(
                        GCBParticleS2CPacket.Vec.UNIT_Y,
                        1,
                        GCBParticleS2CPacket.ShapeOptions.DEFAULT,
                        GCBParticleS2CPacket.Vec.ZERO
                )
        );

        assertEquals("dust_color_transition", payload.particle());
        assertEquals(0x512DA8 + "," + 0x512DA8 + ",1.15", payload.data());
    }
}
