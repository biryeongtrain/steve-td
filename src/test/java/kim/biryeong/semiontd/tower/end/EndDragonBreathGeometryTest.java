package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EndDragonBreathGeometryTest {
    @Test
    void emitterStaysWithinPointThreeBlocksOfActualVanillaUpperLipThroughoutWingCycle() {
        var root = EnderDragonModel.createBodyLayer().bakeRoot();
        var model = new EnderDragonModel(root);
        var state = new EnderDragonRenderState();
        state.isSitting = true;
        state.partialTicks = 1;
        for (int sample = 0; sample < 64; sample++) state.flightHistory.record(250, 0);
        Vec3 emitter = EndDragonBreathGeometry.mouth(Vec3.ZERO, new Vec3(0, 0, -1));
        for (int sample = 0; sample < 256; sample++) {
            state.flapTime = sample / 256.0F;
            model.setupAnim(state);
            PoseStack pose = new PoseStack();
            pose.translate(0, 0, 1);
            pose.scale(-1, -1, 1);
            pose.translate(0, -1.501F, 0);
            root.translateAndRotate(pose);
            root.getChild("head").translateAndRotate(pose);
            Vector3f lip = pose.last().pose().transformPosition(new Vector3f(0, 4 / 16.0F, -24 / 16.0F));
            Vec3 actual = new Vec3(lip.x, lip.y, lip.z);
            assertTrue(emitter.distanceTo(actual) < .3,
                    "Actual vanilla lip at flap=" + state.flapTime + ": " + actual + ", emitter=" + emitter);
        }
    }

    @Test
    void everyLaneDirectionProjectsStraightForwardAndDownFromTheMouth() {
        Vec3 body = new Vec3(32, 250, 32);
        for (Vec3 forward : new Vec3[]{new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1)}) {
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            Vec3 mouth = EndDragonBreathGeometry.mouth(body, forward);
            assertTrue(mouth.subtract(body).dot(forward) > 6.4);
            assertEquals(0, mouth.subtract(body).dot(side), 1e-9);
            for (double lead : new double[]{0, 2.5, 5}) {
                for (double edge : new double[]{-3.5, 0, 3.5}) {
                    Vec3 ground = body.add(forward.scale(lead)).add(side.scale(edge)).add(0, -10, 0);
                    assertEquals(mouth, EndDragonBreathGeometry.point(mouth, ground, forward, 0));
                    Vec3 impact = EndDragonBreathGeometry.impact(mouth, ground, forward);
                    assertEquals(impact, EndDragonBreathGeometry.point(mouth, ground, forward, 1));
                    assertEquals(ground.y, impact.y, 1e-9);
                    assertEquals(edge, impact.subtract(mouth).dot(side), 1e-9);
                    assertEquals(mouth.y - ground.y, impact.subtract(mouth).dot(forward), 1e-9);
                    Vec3 initial = EndDragonBreathGeometry.point(mouth, ground, forward, .001).subtract(mouth);
                    assertTrue(initial.dot(forward) > 0, "The jet initially follows the rendered face");
                    assertTrue(initial.y < 0, "The jet initially points down");
                    Vec3 previous = mouth;
                    for (int step = 1; step <= 60; step++) {
                        Vec3 point = EndDragonBreathGeometry.point(mouth, ground, forward, step / 60.0);
                        assertTrue(point.y < previous.y, "Breath descends continuously");
                        assertTrue(point.subtract(previous).dot(forward) > 0, "No part of the jet bends behind the mouth");
                        assertEquals(mouth.lerp(impact, step / 60.0), point);
                        previous = point;
                    }
                }
            }
        }
    }
}
