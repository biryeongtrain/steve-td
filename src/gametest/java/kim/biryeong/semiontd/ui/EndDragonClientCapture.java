package kim.biryeong.semiontd.ui;

import java.nio.file.Files;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class EndDragonClientCapture {
    private EndDragonClientCapture() {}

    public static void capture(ClientGameTestContext context) {
        capture(context, "side", "dragon");
        context.waitTicks(40);
        capture(context, "front", "dragonfront");
    }

    private static void capture(ClientGameTestContext context, String view, String command) {
        context.runOnClient(client -> {
            client.gui.setScreen(null);
            client.options.fov().set(70);
            client.options.fovEffectScale().set(0.0);
            client.options.bobView().set(false);
            client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            client.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
            client.getConnection().sendCommand("semioncapture " + command);
        });
        context.waitFor(client -> client.player.experienceLevel == 2030 && client.player.getY() > 250, 1200);
        context.waitTicks(40);
        context.runOnClient(client -> client.getConnection().sendCommand("semioncapture dragonstart"));
        context.waitFor(client -> dragon(client) != null, 1200);
        int[] phase = {2030};
        int[] seen = new int[8];
        boolean[] hiddenReturn = {false};
        boolean[] hiddenBeforeBreath = {false};
        int[] entityId = {-1};
        UUID[] entityUuid = {null};
        EnderDragon[] previousDragon = {null};
        float[] previousFlap = {Float.NaN};
        boolean[] animated = {false};
        for (int frame = 0; frame < 200 && phase[0] != 2037; frame++) {
            int index = frame;
            context.runOnClient(client -> {
                phase[0] = client.player.experienceLevel;
                if (phase[0] >= 2030 && phase[0] <= 2037) seen[phase[0] - 2030]++;
                var dragon = dragon(client);
                int dragonCount = 0;
                for (var entity : client.level.entitiesForRendering()) {
                    if (entity instanceof EnderDragon) dragonCount++;
                }
                boolean hidden = phase[0] == 2034 || phase[0] == 2036;
                if (hidden) {
                    var proxy = client.level.getEntity(entityId[0]);
                    if (dragonCount != 0 || !(proxy instanceof ArmorStand) || !proxy.isInvisible()
                            || !proxy.getUUID().equals(entityUuid[0])) {
                        throw new AssertionError("Hidden flight phases must replace the visible dragon with the same invisible proxy identity");
                    }
                    if (phase[0] == 2034) hiddenBeforeBreath[0] = true;
                    if (phase[0] == 2036) hiddenReturn[0] = true;
                    client.gui.toastManager().clear();
                    System.out.println("SEMION_DRAGON_CLIENT view=" + view + " frame=" + index + " phaseAck=" + phase[0]
                            + " id=" + proxy.getId() + " uuid=" + proxy.getUUID() + " pos=" + proxy.position()
                            + " proxy=armor_stand invisible=" + proxy.isInvisible() + " dragonCount=" + dragonCount);
                    return;
                }
                if (dragon == null) throw new AssertionError("The actual client dragon must remain tracked");
                if (entityId[0] == -1) {
                    entityId[0] = dragon.getId();
                    entityUuid[0] = dragon.getUUID();
                }
                if (dragonCount != 1 || entityId[0] != dragon.getId() || !entityUuid[0].equals(dragon.getUUID())) {
                    throw new AssertionError("Exactly one dragon with the original identity must complete its return");
                }
                if (previousDragon[0] == dragon && dragon.flapTime > previousFlap[0]) animated[0] = true;
                previousDragon[0] = dragon;
                previousFlap[0] = dragon.flapTime;
                var renderer = (EnderDragonRenderer) client.getEntityRenderDispatcher().getRenderer(dragon);
                var state = renderer.createRenderState();
                renderer.extractRenderState(dragon, state, 1.0F);
                client.gui.toastManager().clear();
                System.out.println("SEMION_DRAGON_CLIENT view=" + view + " frame=" + index + " phaseAck=" + phase[0]
                        + " particles=" + client.particleEngine.countParticles()
                        + " viewerDistance=" + client.player.position().distanceTo(dragon.position())
                        + " id=" + dragon.getId() + " pos=" + dragon.position()
                        + " yaw=" + dragon.getYRot() + " pitch=" + dragon.getXRot()
                        + " noAi=" + dragon.isNoAi() + " invisible=" + dragon.isInvisible()
                        + " flap=" + dragon.flapTime + " oldFlap=" + dragon.oFlapTime
                        + " renderFlap=" + state.flapTime + " sitting=" + state.isSitting
                        + " landing=" + state.isLandingOrTakingOff + " vanillaPhase="
                        + dragon.getPhaseManager().getCurrentPhase().getPhase()
                        + " h0=" + state.getHistoricalPos(0) + " h5=" + state.getHistoricalPos(5)
                        + " h10=" + state.getHistoricalPos(10) + " h20=" + state.getHistoricalPos(20));
            });
            var shot = context.takeScreenshot(TestScreenshotOptions.of("dragon-" + view + "-frame-" + frame + "-phase-" + phase[0])
                    .withSize(1600, 900).disableCounterPrefix());
            if (!Files.isRegularFile(shot)) throw new AssertionError("The actual dragon framebuffer was not written");
            System.out.println("SEMION_CAPTURE_SCREENSHOT=" + shot.toAbsolutePath());
            context.waitTicks(phase[0] >= 2035 ? 1 : 3);
        }
        if (phase[0] != 2037 || seen[2] < 2 || seen[5] < 2 || !hiddenBeforeBreath[0] || !hiddenReturn[0]) {
            throw new AssertionError("The capture must include multiple real rush and breath frames and finish normally");
        }
        if (!animated[0]) throw new AssertionError("The real client dragon flapTime must advance between frames");
        context.waitTicks(10);
        context.runOnClient(client -> {
            var dragon = dragon(client);
            if (dragon == null || dragon.getId() != entityId[0] || !entityUuid[0].equals(dragon.getUUID()) || dragon.isInvisible()
                    || Math.abs(dragon.getX() - 21.5) > .1 || Math.abs(dragon.getZ() - .5) > .1) {
                throw new AssertionError("The original dragon must visibly reappear at the isolated lane's buildable center");
            }
            System.out.println("SEMION_DRAGON_RETURN_VISIBLE view=" + view + " id=" + dragon.getId() + " pos=" + dragon.position());
        });
        var returned = context.takeScreenshot(TestScreenshotOptions.of("dragon-" + view + "-return-center-visible")
                .withSize(1600, 900).disableCounterPrefix());
        if (!Files.isRegularFile(returned)) throw new AssertionError("Visible center-return framebuffer missing");
        System.out.println("SEMION_CAPTURE_SCREENSHOT=" + returned.toAbsolutePath());
        context.runOnClient(client -> client.getConnection().sendCommand("semioncapture dragonoff"));
    }

    private static EnderDragon dragon(Minecraft client) {
        if (client.level == null) return null;
        for (var entity : client.level.entitiesForRendering()) {
            if (entity instanceof EnderDragon dragon) return dragon;
        }
        return null;
    }
}
