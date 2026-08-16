package kim.biryeong.semiontd.tower.plant;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStyleRegistry;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class PlantVfx {
    public static final ResourceLocation LOBBED_SPLASH = id("plant_lobbed_splash");

    private static final AreaVfxParticle SHOT = particle(ParticleTypes.FALLING_WATER, "falling_water");
    private static final AreaVfxParticle RIM = particle(ParticleTypes.SPORE_BLOSSOM_AIR, "spore_blossom_air");
    private static final AreaVfxParticle IMPACT = particle(ParticleTypes.SPLASH, "splash");

    private PlantVfx() {
    }

    public static void register(AreaVfxStyleRegistry registry) {
        registry.register(LOBBED_SPLASH, PlantVfx::lobbedSplash);
    }

    public static void showDebug(SemionTowerEntity tower, ServerPlayer player) {
        Vec3 horizontal = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        horizontal = horizontal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
        Vec3 center = player.position().add(horizontal.scale(8.0));
        double radius = TowerBalanceRuntime.ability(
                PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), "splashRadius", 4.0);
        TowerVfxService.showAreaEffect(
                tower,
                id("debug/plant_lobbed_splash"),
                LOBBED_SPLASH,
                new Vec3(center.x, tower.getY(), center.z),
                radius,
                List.of(),
                1,
                1,
                0
        );
    }

    private static void lobbedSplash(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 start = context.source();
        Vec3 end = context.center();
        double height = TowerBalanceRuntime.ability(
                context.sourceTowerTypeId().getPath(), "lobArcHeight", 5.0);
        Vec3 control = start.lerp(end, 0.5).add(0.0, height, 0.0);
        output.trail(SHOT, start, control, end, Math.max(12, (int) Math.ceil(start.distanceTo(end))), true);
        output.circle(RIM, end, context.radius(), Math.max(18, (int) Math.ceil(context.radius() * 14.0)), true);
        output.sphere(IMPACT, end.add(0.0, 0.25, 0.0), Math.min(0.7, context.radius() * 0.2), 16, false);
    }

    private static AreaVfxParticle particle(ParticleOptions vanilla, String id) {
        return new AreaVfxParticle(vanilla, ResourceLocation.fromNamespaceAndPath("minecraft", id));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
