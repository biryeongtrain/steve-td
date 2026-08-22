package kim.biryeong.semiontd.tower.succubus;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStyleRegistry;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.BuilderPalette;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class SuccubusVfx {
    public enum DebugKind {STACK, SLEEP, SMOKE, WAKE, ABSORB}

    public static final ResourceLocation STACK = id("succubus_stack");
    public static final ResourceLocation SLEEP = id("succubus_sleep");
    public static final ResourceLocation SLEEP_SMOKE = id("succubus_sleep_smoke");
    public static final ResourceLocation ABSORB = id("succubus_absorb");

    private static final AreaVfxParticle PURPLE = particle(0x8B5CF6, 0.9F, "witch");
    private static final AreaVfxParticle PINK = particle(0xF472B6, 0.85F, "portal");
    private static final AreaVfxParticle DARK = particle(0x581C87, 1.1F, "reverse_portal");
    private static final AreaVfxParticle RED = particle(0xDC2626, 0.95F, "damage_indicator");
    private static final AreaVfxParticle LARGE_SMOKE = new AreaVfxParticle(ParticleTypes.LARGE_SMOKE,
            ResourceLocation.fromNamespaceAndPath("minecraft", "large_smoke"));
    private static final AreaVfxParticle SMOKE = new AreaVfxParticle(ParticleTypes.SMOKE,
            ResourceLocation.fromNamespaceAndPath("minecraft", "smoke"));
    private static Consumer<Vec3> sleepSmokeTestObserver;

    private SuccubusVfx() {
    }

    public static void register(AreaVfxStyleRegistry registry) {
        registry.register(STACK, SuccubusVfx::stack);
        registry.register(SLEEP, SuccubusVfx::sleep);
        registry.register(SLEEP_SMOKE, SuccubusVfx::sleepSmoke);
        registry.register(ABSORB, SuccubusVfx::absorb);
    }

    public static void showDreamStack(SemionTowerEntity source, Vec3 target) {
        show(source, STACK, target, 0.8, List.of(target));
    }

    public static void showSleep(SemionTowerEntity source, Vec3 target) {
        show(source, SLEEP, target, 1.25, List.of(target));
    }

    public static void showSleepSmoke(SemionTowerEntity source, Entity target) {
        if (target == null) return;
        Vec3 center = target.position().add(0.0, target.getBbHeight() + 0.2, 0.0);
        if (sleepSmokeTestObserver != null) sleepSmokeTestObserver.accept(center);
        show(source, SLEEP_SMOKE, center, 0.75, List.of(center));
    }

    static void setSleepSmokeTestObserver(Consumer<Vec3> observer) {
        sleepSmokeTestObserver = observer;
    }

    public static void showAbsorption(SemionTowerEntity source, Vec3 deathPosition) {
        double radius = Math.max(1.0, source.position().distanceTo(deathPosition));
        show(source, ABSORB, deathPosition, radius, List.of(source.position()));
    }

    public static void showDebug(ServerPlayer player, DebugKind kind) {
        if (player == null || kind == null) return;
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        forward = forward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
        Vec3 source = player.position().add(forward.scale(3.0)).add(0.0, 1.0, 0.0);
        Vec3 target = player.position().add(forward.scale(6.0)).add(0.0, 1.0, 0.0);
        ResourceLocation style = switch (kind) {
            case STACK -> STACK;
            case SLEEP -> SLEEP;
            case SMOKE -> SLEEP_SMOKE;
            case WAKE -> AreaVfxStyles.DEBUFF;
            case ABSORB -> ABSORB;
        };
        if (kind == DebugKind.SMOKE) target = target.add(0.0, 1.5, 0.0);
        TowerVfxService.showAreaEffectDebug(player, new AreaVfxContext(
                id("debug_" + kind.name().toLowerCase(Locale.ROOT)), style, player.getUUID(), id("succubus"),
                BuilderPalette.SUCCUBUS.areaPalette(), source, target,
                kind == DebugKind.SLEEP ? 1.25 : kind == DebugKind.SMOKE ? 0.75
                        : kind == DebugKind.WAKE ? SuccubusBalance.spreadRadius() : 3.0,
                List.of(source), 1, 1, 0,
                player.level().getGameTime()
        ));
    }

    private static void show(SemionTowerEntity source, ResourceLocation style, Vec3 center,
                             double radius, List<Vec3> samples) {
        if (source == null || center == null) return;
        TowerVfxService.showAreaEffect(source,
                id("trigger_" + style.getPath().substring("succubus_".length())),
                style, center, radius, samples, 1, 1, 0);
    }

    private static void stack(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 base = context.center().add(0.0, 0.15, 0.0);
        for (int arm = 0; arm < 2; arm++) {
            double phase = arm * Math.PI;
            Vec3 start = base.add(Math.cos(phase) * 0.55, 0.0, Math.sin(phase) * 0.55);
            Vec3 control = base.add(Math.cos(phase + Math.PI * 0.7) * 0.75, 0.75,
                    Math.sin(phase + Math.PI * 0.7) * 0.75);
            Vec3 end = base.add(Math.cos(phase + Math.PI * 1.4) * 0.25, 1.5,
                    Math.sin(phase + Math.PI * 1.4) * 0.25);
            output.trail(PURPLE, start, control, end, 12, true);
        }
        output.sphere(PINK, base.add(0.0, 0.75, 0.0), 0.2, 10, true);
    }

    private static void sleep(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 center = context.center().add(0.0, 0.75, 0.0);
        output.circle(PURPLE, center.add(0.0, -0.5, 0.0), 1.2, 28, true);
        output.circle(PINK, center, 0.85, 24, true);
        output.circle(DARK, center.add(0.0, 0.5, 0.0), 0.5, 20, true);
        output.sphere(DARK, center, 0.7, 24, false);
    }

    private static void sleepSmoke(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 center = context.center();
        output.sphere(LARGE_SMOKE, center, 0.45, 8, false);
        output.trail(SMOKE, center.add(-0.25, 0.0, 0.0), center.add(0.2, 0.45, 0.1),
                center.add(-0.1, 1.0, 0.0), 6, false);
        output.trail(SMOKE, center.add(0.25, 0.0, 0.0), center.add(-0.2, 0.4, -0.1),
                center.add(0.1, 0.9, 0.0), 6, false);
    }

    private static void absorb(AreaVfxContext context, AreaVfxOutput output) {
        Vec3 start = context.center().add(0.0, 0.6, 0.0);
        Vec3 end = context.source().add(0.0, 0.9, 0.0);
        Vec3 middle = start.add(end).scale(0.5).add(0.0, 1.2, 0.0);
        output.trail(RED, start, middle.add(0.25, 0.0, 0.25), end, 18, true);
        output.trail(PURPLE, start, middle.add(-0.25, 0.25, -0.25), end, 18, true);
        output.sphere(PINK, end, 0.45, 18, true);
        output.circle(DARK, end, 0.65, 20, false);
    }

    private static AreaVfxParticle particle(int color, float scale, String vanillaId) {
        return new AreaVfxParticle(new DustParticleOptions(color, scale),
                ResourceLocation.fromNamespaceAndPath("minecraft", vanillaId));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path.toLowerCase(Locale.ROOT));
    }
}
