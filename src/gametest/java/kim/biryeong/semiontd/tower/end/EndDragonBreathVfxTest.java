package kim.biryeong.semiontd.tower.end;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class EndDragonBreathVfxTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void actualParticlePacketsProjectDiagonallyFromTheMouthAndPreserveGroundWave(GameTestHelper context) throws Exception {
        Vec3 body = Vec3.atCenterOf(context.absolutePos(new BlockPos(32, 20, 32)));
        try (var viewer = RuntimePlayerFixture.connect(context, context.getLevel(), body.add(0, 0, -12),
                GameType.CREATIVE, UUID.randomUUID(), "EndBreathViewer")) {
            var field = RuntimePlayerFixture.class.getDeclaredField("channel");
            field.setAccessible(true);
            EmbeddedChannel channel = (EmbeddedChannel) field.get(viewer);
            for (Vec3 direction : List.of(new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                    new Vec3(0, 0, 1), new Vec3(0, 0, -1))) {
                channel.runPendingTasks();
                Object previous;
                while ((previous = channel.readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(previous);
                Vec3 ground = body.add(0, -10, 0);
                Vec3 mouth = EndDragonBreathGeometry.mouth(body, direction);
                EndVfx.assaultBreath(context.getLevel(), body, ground, direction, 7);
                channel.runPendingTasks();
                var particles = channel.outboundMessages().stream()
                        .filter(ClientboundLevelParticlesPacket.class::isInstance)
                        .map(ClientboundLevelParticlesPacket.class::cast)
                        .filter(packet -> packet.particle().getType() == ParticleTypes.DRAGON_BREATH).toList();
                var rays = particles.stream().filter(packet -> packet.count() == 1).toList();
                require(!rays.isEmpty(), "The nearby viewer must receive actual breath particle packets");
                close(mouth, position(rays.getFirst()), "The first particle starts at the mouth, not the body");
                require(rays.stream().filter(packet -> position(packet).distanceTo(mouth) <= .250001).count() == 5,
                        "All five rays begin inside the narrow half-block mouth opening");
                require(rays.size() == 35, "The widening fan uses a fixed thirty-five particle samples per tick");
                Vec3 firstStep = position(rays.get(1)).subtract(mouth);
                require(firstStep.dot(direction) > 0 && firstStep.y < 0,
                        "The first visible segment leaves the face forward and downward");
                for (var packet : rays) {
                    Vec3 offset = position(packet).subtract(mouth);
                    require(offset.dot(direction) >= -1e-6 && offset.y <= 1e-6,
                            "Every visible particle stays ahead of the mouth and below it");
                    require(Math.abs(offset.dot(direction) + offset.y) < 1e-6,
                            "The jet follows a straight forty-five-degree downward diagonal");
                }
                Vec3 side = new Vec3(-direction.z, 0, direction.x);
                for (int ray = -1; ray <= 1; ray++) {
                    Vec3 expected = EndDragonBreathGeometry.impact(mouth, ground.add(side.scale(3.5 * ray)), direction);
                    require(rays.stream().anyMatch(packet -> position(packet).distanceTo(expected) < 1e-6),
                            "The forward diagonal fan reaches ground height at its center and both edges");
                }
                require(particles.stream().anyMatch(packet -> packet.count() == 2
                                && Math.abs(packet.y() - ground.y - .2) < 1e-6),
                        "The existing ground wave remains visible");
            }
        }
        context.succeed();
    }

    private static Vec3 position(ClientboundLevelParticlesPacket packet) {
        return new Vec3(packet.x(), packet.y(), packet.z());
    }

    private static void close(Vec3 expected, Vec3 actual, String message) {
        require(expected.distanceTo(actual) < 1e-6, message + ": " + expected + " / " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
