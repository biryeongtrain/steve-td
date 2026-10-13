package kim.biryeong.semiontd.entity.tower.vfx;

import java.util.List;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolSpell;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class MagicSchoolSpellVfx {
    public enum Kind { ATTACK, HIT, AREA, SHIELD, HEAL, WOUND, DOT, CONTROL }

    public record Visual(MagicSchoolSpell spell, Kind kind, Vec3 source, Vec3 center, double radius) {
    }

    private record Color(int rgb, float scale, int weight) {
        AreaVfxParticle particle() {
            return new AreaVfxParticle(new DustParticleOptions(rgb, scale), Identifier.withDefaultNamespace("dust"));
        }
    }

    private MagicSchoolSpellVfx() {}

    private static List<Color> colors(MagicSchoolSpell spell) {
        return switch (spell) {
            case EXPELLIARMUS -> single(0xD9F4FF, .85F);
            case MUGGLE_WAND -> pair(0x383838, 0x000000);
            case STUPEFY -> single(0xFF7777, 1.0F);
            case PROTEGO, PROTEGO_MAXIMA -> single(0xC5EBFF, .9F);
            case WINGARDIUM_LEVIOSA -> pair(0xFFFFFF, 0x929292);
            case EXPULSO -> single(0xEF3038, 1.0F);
            case LUMOS -> single(0xFFFFFF, .5F);
            case EPISKEY -> single(0xB4EFA9, .9F);
            case SECTUMSEMPRA -> pair(0xFFE44D, 0x65CC47);
            case BOMBARDA -> pair(0xEF3038, 0x9B40DC);
            case EXPECTO_PATRONUM -> single(0x87CEFA, 1.0F);
            case LUMOS_MAXIMA -> single(0xFFFFFF, 1.6F);
            case RENNERVATE -> single(0x4BD66A, 1.0F);
            case AVADA_KEDAVRA -> List.of(new Color(0x28D64B, 1.0F, 2), new Color(0x000000, 1.0F, 1));
            case CRUCIO -> single(0x8B1028, 1.0F);
            case IMPERIO -> pair(0xFFE44D, 0x000000);
        };
    }

    private static List<Color> single(int rgb, float scale) { return List.of(new Color(rgb, scale, 1)); }
    private static List<Color> pair(int first, int second) {
        return List.of(new Color(first, 1.0F, 1), new Color(second, 1.0F, 1));
    }

    public static void plan(Visual visual, AreaVfxOutput output) {
        if (visual.kind() == Kind.HEAL || visual.kind() == Kind.WOUND) {
            boolean heal = visual.kind() == Kind.HEAL;
            var particle = new AreaVfxParticle(heal ? ParticleTypes.HEART : ParticleTypes.SWEEP_ATTACK,
                    Identifier.withDefaultNamespace(heal ? "heart" : "sweep_attack"));
            output.sphere(particle, visual.center(), heal ? .18 : .01, heal ? 3 : 1, false);
            return;
        }
        List<Color> colors = colors(visual.spell());
        if (visual.kind() == Kind.SHIELD) colors = single(0x3986FF, .65F);
        if (visual.kind() == Kind.AREA) {
            if (visual.spell() == MagicSchoolSpell.WINGARDIUM_LEVIOSA) colors = single(0xFFFFFF, .9F);
            if (visual.spell() == MagicSchoolSpell.PROTEGO_MAXIMA) colors = single(0x163C98, 1.0F);
        }
        int weight = colors.stream().mapToInt(Color::weight).sum();
        int points = switch (visual.kind()) {
            case ATTACK -> Math.min(90, Math.max(18, (int) Math.ceil(visual.source().distanceTo(visual.center()) * 6)));
            case AREA -> 72;
            case SHIELD -> 24;
            default -> 12;
        };
        for (Color color : colors) {
            int count = Math.max(1, points * color.weight() / weight);
            var particle = color.particle();
            switch (visual.kind()) {
                case ATTACK -> output.line(particle, visual.source(), visual.center(), count, true);
                case AREA, SHIELD -> output.circle(particle, visual.center(), visual.radius(), count, false);
                case CONTROL -> output.circle(particle, visual.center(), .4, count, false);
                default -> output.sphere(particle, visual.center(), .3, count, false);
            }
        }
    }
}
