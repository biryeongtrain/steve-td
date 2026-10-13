package kim.biryeong.semiontd.ui;

import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class AugmentClientCaptureTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        String address = "127.0.0.1:" + Integer.getInteger("semiontd.capture.port", 25643);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            var server = new ServerData("Semion isolated capture", address, ServerData.Type.OTHER);
            server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
            ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(address), server, false, null);
        });
        context.waitFor(client -> {
            if (client.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
                throw new AssertionError("The isolated capture client disconnected before world entry; inspect its protocol report");
            }
            return client.player != null && client.level != null && client.getConnection() != null;
        }, 2400);
        context.waitFor(client -> client.getResourceManager().getResource(
                net.minecraft.resources.Identifier.parse("semion-td:font/ui.json")).isPresent(), 2400);
        if ("demon".equals(System.getProperty("semiontd.capture.mode"))) {
            captureDemon(context);
        } else if ("sky".equals(System.getProperty("semiontd.capture.mode"))) {
            captureSky(context);
        } else if ("dragon".equals(System.getProperty("semiontd.capture.mode"))) {
            EndDragonClientCapture.capture(context);
        } else if ("card-dialog".equals(System.getProperty("semiontd.capture.mode"))) {
            CardDialogClientCapture.capture(context);
        } else {
            AugmentCardClientCapture.capture(context);
        }
        context.runOnClient(client -> {
            if (client.level == null) throw new AssertionError("The capture connection ended before cleanup");
            client.level.disconnect(net.minecraft.network.chat.Component.literal("Capture complete"));
            client.disconnectWithSavingScreen();
        });
        context.waitFor(client -> client.level == null);
        context.waitTicks(2);
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }

    private static void captureDemon(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600, 1000);
        for (int scale : new int[]{2, 3}) {
            context.runOnClient(client -> {
                client.options.guiScale().set(scale);
                client.resizeGui();
            });
            for (var skill : List.of(kim.biryeong.semiontd.tower.demonlord.DemonLordSkill.SOUL_DRAIN,
                    kim.biryeong.semiontd.tower.demonlord.DemonLordSkill.SKY_BREAKER,
                    kim.biryeong.semiontd.tower.demonlord.DemonLordSkill.GRIP_OF_DOOM,
                    kim.biryeong.semiontd.tower.demonlord.DemonLordSkill.HELLFIRE_BRAND)) {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    client.getConnection().sendCommand("semioncapture demon " + skill.name());
                });
                context.waitFor(client -> client.player.experienceLevel == 2300 + skill.ordinal()
                        && client.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>, 1200);
                context.waitTicks(10);
                context.runOnClient(client -> {
                    try {
                        var dialog = client.gui.screen();
                        var body = net.minecraft.client.gui.screens.dialog.DialogScreen.class.getDeclaredField("bodyScroll");
                        body.setAccessible(true);
                        var content = net.minecraft.client.gui.components.ScrollableLayout.class.getDeclaredField("content");
                        content.setAccessible(true);
                        var widgets = new java.util.ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
                        ((net.minecraft.client.gui.layouts.Layout) content.get(body.get(dialog))).visitWidgets(widgets::add);
                        StringBuilder text = new StringBuilder();
                        for (var widget : widgets) {
                            if (!(widget instanceof net.minecraft.client.gui.components.FocusableTextWidget field)) continue;
                            text.append(field.getMessage().getString());
                            int width = field.getWidth() - field.getPadding() * 2;
                            for (var line : client.font.split(field.getMessage(), width)) {
                                if (client.font.width(line) > width) throw new AssertionError("Demon detail line overflow");
                            }
                            if (field.getX() < 0 || field.getX() + field.getWidth() > dialog.width) {
                                throw new AssertionError("Demon detail widget exceeds the viewport");
                            }
                        }
                        if (!text.toString().contains("피해 포인트") || !text.toString().contains("100.0/50.0")
                                || !text.toString().contains("점감 후 기준 피해") || text.toString().contains("{ability.")) {
                            throw new AssertionError("Live Demon details are missing scaling values");
                        }
                        client.gui.toastManager().clear();
                        System.out.println("SEMION_DEMON_NATIVE_LAYOUT_OK skill=" + skill + " scale=" + scale);
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                });
                var shot = context.takeScreenshot(TestScreenshotOptions.of("demon-damage-" + skill.name().toLowerCase(java.util.Locale.ROOT)
                        + "-scale" + scale).withSize(1600, 1000).disableCounterPrefix());
                if (!Files.isRegularFile(shot)) throw new AssertionError("Demon GPU screenshot missing");
                System.out.println("SEMION_CAPTURE_SCREENSHOT=" + shot.toAbsolutePath());
            }
        }
    }

    private static void captureSky(ClientGameTestContext context) {
        context.waitFor(client -> client.getResourceManager().getResource(
                net.minecraft.resources.Identifier.parse("semion-td:items/skybox/blue_ember.json")).isPresent(), 2400);
        context.runOnClient(client -> {
            client.gui.setScreen(null);
            client.options.fov().set(70);
            client.options.fovEffectScale().set(0.0);
            client.options.bobView().set(false);
            client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            client.getConnection().sendCommand("semioncapture sky");
        });
        context.waitFor(client -> client.player.experienceLevel == 2026 && client.player.getY() > 230, 1200);
        context.waitTicks(60);
        for (int[] distances : List.of(new int[]{8, 32}, new int[]{8, 128}, new int[]{10, 32}, new int[]{16, 32})) {
            for (float yaw : new float[]{0, 22.5F, 45}) {
                for (float pitch : yaw == 45 ? new float[]{0, 45, 35.264F, -80, 80} : new float[]{0, 45}) {
                    context.runOnClient(client -> {
                        client.options.renderDistance().set(distances[0]);
                        client.options.cloudRange().set(distances[1]);
                        client.player.setYRot(yaw);
                        client.player.yRotO = yaw;
                        client.player.setXRot(pitch);
                        client.player.xRotO = pitch;
                        client.gui.toastManager().clear();
                    });
                    context.waitTicks(10);
                    String name = "sky-r" + distances[0] + "-c" + distances[1] + "-yaw" + yaw + "-pitch" + pitch;
                    var shot = context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1600, 900).disableCounterPrefix());
                    if (!Files.isRegularFile(shot)) throw new AssertionError("Sky GPU screenshot was not written");
                    System.out.println("SEMION_CAPTURE_SCREENSHOT=" + shot.toAbsolutePath());
                    context.runOnClient(client -> {
                        try {
                            var camera = client.gameRenderer.mainCamera();
                            var field = camera.getClass().getDeclaredField("depthFar");
                            field.setAccessible(true);
                            float actualFar = field.getFloat(camera);
                            var projectionField = camera.getClass().getDeclaredField("projection");
                            projectionField.setAccessible(true);
                            var projection = (net.minecraft.client.renderer.Projection) projectionField.get(camera);
                            var matrix = projection.getMatrix(new org.joml.Matrix4f());
                            float expectedFar = Math.max(distances[0] * 64, distances[1] * 16);
                            if (actualFar != expectedFar) throw new AssertionError("Unexpected real camera far distance " + actualFar
                                    + " renderDistance=" + client.options.renderDistance().get() + " effective=" + client.options.getEffectiveRenderDistance());
                            if (client.getWindow().getWidth() != 1600 || client.getWindow().getHeight() != 900) {
                                throw new AssertionError("The real camera viewport must be 1600x900");
                            }
                            System.out.println("SEMION_SKY_CAMERA=" + name + " actualFar=" + actualFar + " fov=" + camera.getFov()
                                    + " pos=" + client.player.position() + " guiScale=" + client.options.guiScale().get()
                                    + " effective=" + client.options.getEffectiveRenderDistance() + " viewport=1600x900"
                                    + " zZeroToOne=" + com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
                                    + " projectionM22=" + matrix.m22() + " projectionM32=" + matrix.m32());
                        } catch (ReflectiveOperationException exception) {
                            throw new AssertionError("Cannot inspect actual camera far plane", exception);
                        }
                    });
                }
            }
        }
        context.runOnClient(client -> {
            client.player.setYRot(0);
            client.player.yRotO = 0;
            client.player.setXRot(0);
            client.player.xRotO = 0;
            client.getConnection().sendCommand("semioncapture skyoff");
        });
        context.waitFor(client -> client.player.experienceLevel == 2025, 1200);
        context.waitTicks(10);
        var off = context.takeScreenshot(TestScreenshotOptions.of("sky-off-control").withSize(1600, 900).disableCounterPrefix());
        if (!Files.isRegularFile(off)) throw new AssertionError("Sky off screenshot was not written");
        System.out.println("SEMION_CAPTURE_SCREENSHOT=" + off.toAbsolutePath());
        try {
            var onImage = javax.imageio.ImageIO.read(off.resolveSibling("sky-r8-c32-yaw0.0-pitch0.0.png").toFile());
            var offImage = javax.imageio.ImageIO.read(off.toFile());
            int visibleOn = 0;
            int visibleOff = 0;
            for (int x = 980; x < 1080; x++) {
                for (int y = 200; y < 600; y++) {
                    if (isRedForeground(onImage.getRGB(x, y))) visibleOn++;
                    if (isRedForeground(offImage.getRGB(x, y))) visibleOff++;
                }
            }
            System.out.println("SEMION_SKY_FOREGROUND_RED_PIXELS on=" + visibleOn + " off=" + visibleOff + " total=40000");
            if (visibleOn < 38000 || visibleOff < 38000) {
                throw new AssertionError("The actual GPU sky must preserve the opaque foreground; red pixels on=" + visibleOn + " off=" + visibleOff);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Cannot inspect actual sky framebuffer foreground", exception);
        }
    }

    private static boolean isRedForeground(int color) {
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        return red > 40 && red > green * 2 && red > blue * 2;
    }
}
