package kim.biryeong.semiontd.tower.atlantis;

import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStyleRegistry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Area VFX styles for the Atlantis family.
 *
 * <p>Two styles, matching the two things a player has to be able to read at a glance:
 *
 * <ul>
 *   <li>{@link #PRESSURE_ZONE} draws the standing field a turtle deployed. It is rendered with
 *       {@code ON_TRIGGER} so the boundary stays visible while the zone is empty — a zone the
 *       player cannot see is a zone they cannot build around, and the zone sits on the path rather
 *       than under the tower that made it.
 *   <li>{@link #WATER_PRESSURE} draws the burst. It has to read as an outward shock so the chain
 *       through a pack is legible as one event per monster.
 * </ul>
 */
public final class AtlantisVfx {
    public static final ResourceLocation PRESSURE_ZONE = id("atlantis_pressure_zone");
    public static final ResourceLocation WATER_PRESSURE = id("atlantis_water_pressure");

    private static final AreaVfxParticle DEEP_WATER = particle(0x1B6FA8, 1.15F, "bubble");
    private static final AreaVfxParticle SHALLOW_FOAM = particle(0x7FE3FF, 0.9F, "bubble_column_up");
    private static final AreaVfxParticle BURST_FOAM = particle(0xE8FBFF, 1.3F, "splash");
    private static final AreaVfxParticle BURST_CORE = particle(0x2CE0C8, 1.1F, "bubble_pop");

    private static final int ZONE_WALL_COLUMNS = 8;
    private static final int BURST_RAYS = 10;

    private AtlantisVfx() {
    }

    public static void register(AreaVfxStyleRegistry registry) {
        registry.register(PRESSURE_ZONE, AtlantisVfx::pressureZone);
        registry.register(WATER_PRESSURE, AtlantisVfx::waterPressure);
    }

    /**
     * A standing column of water: a bright rim on the ground, a dimmer ring above it, and short
     * rising columns around the edge so the field has height instead of reading as a flat decal.
     */
    private static void pressureZone(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 center = context.center();
        double radius = context.radius();
        int outline = outlinePoints(radius);

        output.circle(SHALLOW_FOAM, center.add(0.0, 0.06, 0.0), radius, outline, true);
        output.circle(DEEP_WATER, center.add(0.0, 0.55, 0.0), radius * 0.94, Math.max(12, outline / 2), false);

        for (int column = 0; column < ZONE_WALL_COLUMNS; column++) {
            double angle = Math.PI * 2.0 * column / ZONE_WALL_COLUMNS;
            Vec3 base = center.add(Math.cos(angle) * radius, 0.06, Math.sin(angle) * radius);
            output.line(DEEP_WATER, base, base.add(0.0, 1.1, 0.0), 5, false);
        }

        // Monsters caught this scan get pulled toward the floor: the zone is pressure, not a mark.
        for (Vec3 hit : context.sampledAppliedPositions()) {
            output.line(SHALLOW_FOAM, hit.add(0.0, 1.25, 0.0), hit.add(0.0, 0.1, 0.0), 8, false);
        }
    }

    /**
     * An outward shock: a tight core, an expanding rim, and rays that carry the eye from the
     * detonating monster to whatever the burst reached.
     */
    private static void waterPressure(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 center = context.center().add(0.0, 0.1, 0.0);
        double radius = context.radius();
        int outline = outlinePoints(radius);

        output.sphere(BURST_CORE, center.add(0.0, 0.35, 0.0),
                Math.max(0.25, Math.min(0.6, radius * 0.2)), 16, true);
        output.circle(BURST_FOAM, center, radius, outline, true);
        output.circle(BURST_CORE, center.add(0.0, 0.22, 0.0), radius * 0.6, Math.max(12, outline / 2), false);

        for (int ray = 0; ray < BURST_RAYS; ray++) {
            double angle = Math.PI * 2.0 * ray / BURST_RAYS;
            Vec3 edge = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            Vec3 control = center.lerp(edge, 0.5).add(0.0, 0.6, 0.0);
            output.trail(BURST_FOAM, center.add(0.0, 0.3, 0.0), control, edge, 8, false);
        }

        for (Vec3 hit : context.sampledAppliedPositions()) {
            output.line(BURST_CORE, center.add(0.0, 0.3, 0.0), hit.add(0.0, 0.5, 0.0), 10, false);
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
