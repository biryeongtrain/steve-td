package kim.biryeong.semiontd.tower.thunder;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStyleRegistry;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Area VFX styles for the 람쥐썬더 family.
 *
 * <p>Both styles are built from jagged bolts rather than the smooth rings and arcs the shared
 * builtin styles use. A circle reads as "an area was affected"; this family needs the player to read
 * <em>where the current went</em>, because the two abilities differ in exactly that: the chain jumps
 * outward from the monster that was struck, while the discharge dumps everything the tank was
 * holding into the ground it is standing on.
 *
 * <ul>
 *   <li>{@link #ARC} — the 뇌신 람쥐썬더 chain. Bolts leave the struck monster, so the origin of the
 *       splash is unambiguous even in a dense pack.
 *   <li>{@link #DISCHARGE} — the 대지 아르마딜로 death burst. Bolts climb off the rim instead of
 *       arcing between monsters, which keeps it visually distinct from the chain at a glance.
 * </ul>
 */
public final class ThunderVfx {
    public enum DebugKind {
        ARC,
        DISCHARGE
    }

    public static final ResourceLocation ARC = id("thunder_arc");
    public static final ResourceLocation DISCHARGE = id("thunder_discharge");

    /** Near-white, used only for the flash at each end of a bolt. */
    private static final AreaVfxParticle CORE = particle(0xFFFBE0, 1.0F, "electric_spark");
    private static final AreaVfxParticle BOLT = particle(0x7DD3FC, 1.15F, "electric_spark");
    private static final AreaVfxParticle RIM = particle(0x38BDF8, 0.85F, "electric_spark");

    /** Yellow so the tank's death nuke never gets mistaken for a squirrel's chain. */
    private static final AreaVfxParticle DISCHARGE_RIM = particle(0xFACC15, 1.3F, "electric_spark");

    private static final int BOLT_SEGMENTS = 5;
    private static final int BOLT_SEGMENT_POINTS = 3;
    private static final int DISCHARGE_ARMS = 8;

    private ThunderVfx() {
    }

    public static void register(AreaVfxStyleRegistry registry) {
        registry.register(ARC, ThunderVfx::arc);
        registry.register(DISCHARGE, ThunderVfx::discharge);
    }

    public static void showDebug(SemionTowerEntity source, DebugKind kind) {
        Vec3 center = source.position().add(0.0, 0.08, 0.0);
        ResourceLocation style = kind == DebugKind.ARC ? ARC : DISCHARGE;
        double radius = kind == DebugKind.ARC ? 3.5 : 4.0;
        List<Vec3> hits = kind == DebugKind.ARC
                ? List.of(center.add(2.4, 0.0, 0.7), center.add(-1.6, 0.0, 2.2), center.add(0.4, 0.0, -2.5))
                : List.of(center.add(2.0, 0.0, 1.0), center.add(-2.2, 0.0, -0.8));
        TowerVfxService.showAreaEffect(
                source,
                id("debug_" + kind.name().toLowerCase(java.util.Locale.ROOT)),
                style,
                center,
                radius,
                hits,
                hits.size(),
                hits.size(),
                0
        );
    }

    /**
     * Current jumping off the struck monster.
     *
     * <p>The rim is drawn non-essential and dim: it explains the reach once, but the bolts are the
     * part that has to survive particle budget trimming.
     */
    private static void arc(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 origin = context.center().add(0.0, 0.45, 0.0);
        double radius = context.radius();
        long seed = context.gameTime();

        output.sphere(CORE, origin, Math.max(0.2, Math.min(0.45, radius * 0.16)), 12, true);
        output.circle(RIM, context.center().add(0.0, 0.06, 0.0), radius, outlinePoints(radius), false);

        int index = 0;
        for (Vec3 hit : context.sampledAppliedPositions()) {
            Vec3 target = hit.add(0.0, 0.55, 0.0);
            bolt(output, BOLT, origin, target, radius * 0.18, seed + index * 31L, true);
            output.sphere(CORE, target, 0.2, 8, false);
            index++;
        }
    }

    /**
     * The stored charge leaving a destroyed shell.
     *
     * <p>Arms climb from the core out to the rim and then straight up, so the burst reads as
     * grounded — it belongs to the tile the tank died on, not to the monsters it caught.
     */
    private static void discharge(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 ground = context.center().add(0.0, 0.06, 0.0);
        Vec3 core = context.center().add(0.0, 0.5, 0.0);
        double radius = context.radius();
        int outline = outlinePoints(radius);
        long seed = context.gameTime();

        output.circle(DISCHARGE_RIM, ground, radius, outline, true);
        output.circle(BOLT, ground.add(0.0, 0.12, 0.0), radius * 0.55, Math.max(12, outline / 2), false);
        output.sphere(CORE, core, Math.max(0.3, Math.min(0.7, radius * 0.18)), 16, true);

        // Rotate the arm fan with game time so repeated discharges do not stamp the same shape.
        double spin = (seed % 24L) * (Math.PI / 12.0);
        for (int arm = 0; arm < DISCHARGE_ARMS; arm++) {
            double angle = Math.PI * 2.0 * arm / DISCHARGE_ARMS + spin;
            Vec3 edge = ground.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            bolt(output, DISCHARGE_RIM, core, edge, radius * 0.12, seed + arm * 17L, false);
            output.line(BOLT, edge, edge.add(0.0, 0.9, 0.0), 4, false);
        }

        int index = 0;
        for (Vec3 hit : context.sampledAppliedPositions()) {
            bolt(output, BOLT, core, hit.add(0.0, 0.6, 0.0), radius * 0.14, seed + index * 53L, true);
            index++;
        }
    }

    /**
     * Draws a jagged polyline from {@code start} to {@code end}.
     *
     * <p>Both endpoints are exact so the bolt visibly connects the two things it is about. The
     * intermediate kinks are tapered by {@code sin(pi*t)}, which pins the deflection to zero at the
     * ends and puts the widest swing in the middle — a straight line with noise added uniformly
     * detaches from its endpoints and stops reading as a connection.
     *
     * <p>The offsets are derived from {@code seed} rather than a {@link java.util.Random}: VFX runs
     * on the server tick and every bolt in one trigger has to be reproducible for the whole lane.
     */
    private static void bolt(
            AreaVfxOutput output,
            AreaVfxParticle particle,
            Vec3 start,
            Vec3 end,
            double jag,
            long seed,
            boolean essential
    ) {
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 1.0e-4) {
            output.sphere(particle, end, 0.15, 6, essential);
            return;
        }

        Vec3 direction = axis.scale(1.0 / length);
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 lift = direction.cross(side).normalize();

        Vec3 previous = start;
        for (int segment = 1; segment <= BOLT_SEGMENTS; segment++) {
            double progress = (double) segment / BOLT_SEGMENTS;
            Vec3 point;
            if (segment == BOLT_SEGMENTS) {
                point = end;
            } else {
                double taper = Math.sin(Math.PI * progress);
                double phase = seed * 1.37 + segment * 2.399963;
                point = start.add(axis.scale(progress))
                        .add(side.scale(Math.sin(phase) * jag * taper))
                        .add(lift.scale(Math.cos(phase * 1.7) * jag * taper * 0.6));
            }
            output.line(particle, previous, point, BOLT_SEGMENT_POINTS, essential);
            previous = point;
        }
    }

    private static int outlinePoints(double radius) {
        return Math.max(18, Math.min(96, (int) Math.ceil(radius * 18.0)));
    }

    private static AreaVfxParticle particle(int color, float scale, String vanillaId) {
        return new AreaVfxParticle(
                new DustParticleOptions(color, scale),
                ResourceLocation.fromNamespaceAndPath("minecraft", vanillaId)
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
