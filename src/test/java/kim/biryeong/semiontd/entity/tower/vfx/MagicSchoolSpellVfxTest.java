package kim.biryeong.semiontd.entity.tower.vfx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolSpell;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MagicSchoolSpellVfxTest {
    @BeforeAll
    static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void everySpellUsesRequestedDustColorsOnAttacksAndHits() {
        var expected = Map.ofEntries(
                Map.entry(MagicSchoolSpell.EXPELLIARMUS, List.of(0xD9F4FF)),
                Map.entry(MagicSchoolSpell.MUGGLE_WAND, List.of(0x383838, 0)),
                Map.entry(MagicSchoolSpell.STUPEFY, List.of(0xFF7777)),
                Map.entry(MagicSchoolSpell.PROTEGO, List.of(0xC5EBFF)),
                Map.entry(MagicSchoolSpell.WINGARDIUM_LEVIOSA, List.of(0xFFFFFF, 0x929292)),
                Map.entry(MagicSchoolSpell.EXPULSO, List.of(0xEF3038)),
                Map.entry(MagicSchoolSpell.LUMOS, List.of(0xFFFFFF)),
                Map.entry(MagicSchoolSpell.EPISKEY, List.of(0xB4EFA9)),
                Map.entry(MagicSchoolSpell.SECTUMSEMPRA, List.of(0xFFE44D, 0x65CC47)),
                Map.entry(MagicSchoolSpell.BOMBARDA, List.of(0xEF3038, 0x9B40DC)),
                Map.entry(MagicSchoolSpell.PROTEGO_MAXIMA, List.of(0xC5EBFF)),
                Map.entry(MagicSchoolSpell.EXPECTO_PATRONUM, List.of(0x87CEFA)),
                Map.entry(MagicSchoolSpell.LUMOS_MAXIMA, List.of(0xFFFFFF)),
                Map.entry(MagicSchoolSpell.RENNERVATE, List.of(0x4BD66A)),
                Map.entry(MagicSchoolSpell.AVADA_KEDAVRA, List.of(0x28D64B, 0)),
                Map.entry(MagicSchoolSpell.CRUCIO, List.of(0x8B1028)),
                Map.entry(MagicSchoolSpell.IMPERIO, List.of(0xFFE44D, 0)));
        for (var spell : MagicSchoolSpell.values()) {
            for (var kind : List.of(MagicSchoolSpellVfx.Kind.ATTACK, MagicSchoolSpellVfx.Kind.HIT)) {
                var out = plan(spell, kind, 0);
                assertEquals(expected.get(spell), out.calls.stream().map(Call::rgb).toList(), spell + " " + kind);
                if (spell == MagicSchoolSpell.AVADA_KEDAVRA) assertEquals(out.calls.get(1).points * 2, out.calls.get(0).points);
                if (spell == MagicSchoolSpell.LUMOS) assertEquals(.5F, out.calls.getFirst().dust().getScale());
                if (spell == MagicSchoolSpell.LUMOS_MAXIMA) assertEquals(1.6F, out.calls.getFirst().dust().getScale());
            }
        }
    }

    @Test
    void areaAndShieldGeometryUsesTheActualRadiusAndDistinctColors() {
        for (var spell : List.of(MagicSchoolSpell.EXPULSO, MagicSchoolSpell.BOMBARDA)) {
            assertEquals(plan(spell, MagicSchoolSpellVfx.Kind.ATTACK, 0).calls.stream().map(Call::rgb).toList(),
                    plan(spell, MagicSchoolSpellVfx.Kind.AREA, 2.5).calls.stream().map(Call::rgb).toList());
        }
        assertEquals(0xFFFFFF, plan(MagicSchoolSpell.WINGARDIUM_LEVIOSA, MagicSchoolSpellVfx.Kind.AREA, 6).calls.getFirst().rgb());
        assertEquals(0x163C98, plan(MagicSchoolSpell.PROTEGO_MAXIMA, MagicSchoolSpellVfx.Kind.AREA, 6).calls.getFirst().rgb());
        for (var spell : List.of(MagicSchoolSpell.PROTEGO, MagicSchoolSpell.PROTEGO_MAXIMA)) {
            var call = plan(spell, MagicSchoolSpellVfx.Kind.SHIELD, .7).calls.getFirst();
            assertEquals(0x3986FF, call.rgb());
            assertEquals("circle", call.shape);
            assertEquals(.7, call.radius);
        }
        for (var call : plan(MagicSchoolSpell.BOMBARDA, MagicSchoolSpellVfx.Kind.AREA, 2.5).calls) {
            assertEquals(2.5, call.radius);
            assertFalse(call.essential, "Area decoration must respect the shared soft budget.");
        }
        assertTrue(plan(MagicSchoolSpell.EXPULSO, MagicSchoolSpellVfx.Kind.ATTACK, 0).calls.getFirst().essential);
    }

    @Test
    void persistentEffectsStayOnTheTargetAndHealingUsesHearts() {
        var dot = plan(MagicSchoolSpell.CRUCIO, MagicSchoolSpellVfx.Kind.DOT, 0).calls.getFirst();
        assertEquals(0x8B1028, dot.rgb());
        assertEquals(new Vec3(6, 2, 0), dot.center);
        assertEquals("sphere", dot.shape);
        var control = plan(MagicSchoolSpell.IMPERIO, MagicSchoolSpellVfx.Kind.CONTROL, 0);
        assertEquals(List.of(0xFFE44D, 0), control.calls.stream().map(Call::rgb).toList());
        assertTrue(control.calls.stream().allMatch(call -> call.shape.equals("circle")));
        assertEquals(ParticleTypes.HEART, plan(MagicSchoolSpell.EPISKEY, MagicSchoolSpellVfx.Kind.HEAL, 0).calls.getFirst().particle.vanilla());
        assertEquals(ParticleTypes.SWEEP_ATTACK, plan(MagicSchoolSpell.SECTUMSEMPRA, MagicSchoolSpellVfx.Kind.WOUND, 0).calls.getFirst().particle.vanilla());
    }

    private static Output plan(MagicSchoolSpell spell, MagicSchoolSpellVfx.Kind kind, double radius) {
        var out = new Output();
        MagicSchoolSpellVfx.plan(new MagicSchoolSpellVfx.Visual(spell, kind, new Vec3(0, 2, 0), new Vec3(6, 2, 0), radius), out);
        return out;
    }

    private record Call(String shape, AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
        DustParticleOptions dust() { return (DustParticleOptions) particle.vanilla(); }
        int rgb() {
            var color = dust().getColor();
            return Math.round(color.x * 255) << 16 | Math.round(color.y * 255) << 8 | Math.round(color.z * 255);
        }
    }

    private static final class Output implements AreaVfxOutput {
        final List<Call> calls = new ArrayList<>();
        public void line(AreaVfxParticle p, Vec3 a, Vec3 b, int n, boolean e) { calls.add(new Call("line", p, b, 0, n, e)); }
        public void circle(AreaVfxParticle p, Vec3 c, double r, int n, boolean e) { calls.add(new Call("circle", p, c, r, n, e)); }
        public void sphere(AreaVfxParticle p, Vec3 c, double r, int n, boolean e) { calls.add(new Call("sphere", p, c, r, n, e)); }
        public void trail(AreaVfxParticle p, Vec3 a, Vec3 c, Vec3 b, int n, boolean e) { fail("Unexpected trail"); }
    }
}
