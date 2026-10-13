package kim.biryeong.semiontd.tower.end;

import eu.pb4.polymer.core.mixin.block.packet.ServerMapAccessor;
import eu.pb4.polymer.core.mixin.entity.TrackedEntityAccessor;
import eu.pb4.polymer.virtualentity.api.data.EntityData;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class EndDragonVisibilityPacketTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 160, structure = "semion-td-gametest:dragon_lane")
    public void vanishAndReturnReplaceOnlyTheClientVisualAndSpawnAtTheCurrentHeight(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            int id = f.source.getId();
            UUID uuid = f.source.getUUID();
            double health = f.core.health();
            f.drain(f.viewer);
            f.advance(EndDragonAssault.Phase.VANISHED);
            f.assertVisual(f.viewer, EntityTypes.ARMOR_STAND, true, true);
            f.advance(EndDragonAssault.Phase.BREATHING);
            f.assertVisual(f.viewer, EntityTypes.ENDER_DRAGON, false, true);
            close(f.floorY + 10, f.source.getY(), "The new dragon starts directly at breath height");
            f.advance(EndDragonAssault.Phase.RETURNING);
            f.assertVisual(f.viewer, EntityTypes.ARMOR_STAND, true, true);
            f.advance(EndDragonAssault.Phase.SPENT);
            f.assertVisual(f.viewer, EntityTypes.ENDER_DRAGON, false, true);
            require(f.source.getId() == id && f.source.getUUID().equals(uuid), "Server identity must not change");
            require(context.getLevel().getEntity(id) == f.source, "The original server entity remains registered");
            require(f.lane.towers().size() == 1 && f.lane.towers().getFirst() == f.core, "No duplicate tower is created");
            close(health, f.core.health(), "Visual refresh must not change health");
            for (int tick = 0; tick < 10; tick++) f.core.tick(f.lane);
            require(f.core.assaultPhase() == EndDragonAssault.Phase.SPENT, "Visual reappearance cannot restart the assault");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 160, structure = "semion-td-gametest:dragon_lane")
    public void lateWatcherSeesAnInvisibleProxyAndCancellationRestoresEveryWatcher(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            f.advance(EndDragonAssault.Phase.VANISHED);
            f.drain(f.viewer);
            try (var late = RuntimePlayerFixture.connect(context, context.getLevel(), f.viewer.player().position(),
                    GameType.CREATIVE, UUID.randomUUID(), "EndLateViewer")) {
                f.track(late);
                f.assertVisual(late, EntityTypes.ARMOR_STAND, true, false);
                f.lane.assignAugmentSnapshot(AugmentSnapshot.none());
                f.core.tick(f.lane);
                require(!f.source.isInvisible() && f.core.assaultPhase() == EndDragonAssault.Phase.SPENT,
                        "Removing the augment cancels the flight and restores the temporary hidden flag");
                for (var viewer : List.of(f.viewer, late)) {
                    f.assertVisual(viewer, EntityTypes.ENDER_DRAGON, false, true);
                }
                f.core.resetForRound(f.lane);
                require(f.core.state() == EndTowerState.EGG && !f.source.isInvisible()
                                && f.core.assaultPhase() == EndDragonAssault.Phase.READY,
                        "Round reset returns to the egg presentation and resets the use without keeping the hidden flag");
                f.untrack(late);
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 160, structure = "semion-td-gametest:dragon_lane")
    public void deathDuringTheHiddenPassCannotSpawnAVisibleDeadDragon(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            f.advance(EndDragonAssault.Phase.VANISHED);
            f.drain(f.viewer);
            f.lane.killTower(f.core);
            require(!f.source.isInvisible(), "Death restores the saved visibility state");
            require(f.core.assaultPhase() == EndDragonAssault.Phase.SPENT, "Death consumes the use");
            require(f.drain(f.viewer).stream().noneMatch(packet -> packet instanceof ClientboundAddEntityPacket add
                    && add.getId() == f.source.getId()), "Dead entities must not receive another visual spawn");
        }
        context.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final PlayerLane lane;
        final EndTower core;
        final SemionTowerEntity source;
        final RuntimePlayerFixture viewer;
        final TrackedEntityAccessor tracking;
        final double floorY;

        Fixture(GameTestHelper context) throws Exception {
            for (int x = 10; x < 60; x++) for (int z = 30; z < 37; z++) {
                context.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
            BlockPos min = context.absolutePos(new BlockPos(10, 3, 30));
            BlockPos max = context.absolutePos(new BlockPos(59, 3, 36));
            floorY = min.getY();
            var spawn = BlockBounds.of(new BlockPos(max.getX() - 6, min.getY(), min.getZ()), max);
            var path = BlockBounds.of(min, new BlockPos(max.getX() - 7, max.getY(), max.getZ()));
            Vec3 rear = new Vec3(min.getX(), floorY + 1, min.getZ() + 3.5);
            Vec3 front = rear.add(46.5, 0, 0);
            var layout = new LaneRegionLayout(1, front, spawn, List.of(rear), rear, path, List.of());
            viewer = RuntimePlayerFixture.connect(context, context.getLevel(), rear.add(25, 12, -10),
                    GameType.CREATIVE, UUID.randomUUID(), "EndProxyViewer");
            lane = new PlayerLane(TeamId.RED, 1, viewer.player().getUUID(), context.getLevel(), layout);
            lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(
                    new PlayerAugmentState.Selection(5, AugmentRarity.PRISMATIC, EndAugments.ASSAULT,
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
            AreaEffectLaneIndex.register(lane);
            TowerType base = EndTowers.BASE_END_TOWER;
            TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                    1_000_000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                    base.description(), base.visual(), base.upgradeOptions());
            core = new EndTower(giant, viewer.player().getUUID(), TeamId.RED, 1,
                    GridPosition.from(BlockPos.containing(rear.add(25, 0, 0))));
            lane.addTower(core);
            core.onWaveStarted(lane, 5);
            core.tick(lane);
            source = core.runtimeEntity(lane).orElseThrow();
            require(core.state() == EndTowerState.DRAGON, "Fixture must use a real evolved dragon");
            tracking = (TrackedEntityAccessor) ((ServerMapAccessor) context.getLevel().getChunkSource().chunkMap)
                    .polymer$getEntityTrackers().get(source.getId());
            require(tracking != null, "The real server entity tracker must exist");
            track(viewer);
        }

        void track(RuntimePlayerFixture player) {
            tracking.getSeenBy().add(player.player().connection);
            tracking.getServerEntity().addPairing(player.player());
        }

        void untrack(RuntimePlayerFixture player) {
            tracking.getSeenBy().remove(player.player().connection);
            tracking.getServerEntity().removePairing(player.player());
        }

        void advance(EndDragonAssault.Phase phase) {
            for (int tick = 0; tick < 400 && core.assaultPhase() != phase; tick++) core.tick(lane);
            require(core.assaultPhase() == phase, "Expected phase " + phase + ", got " + core.assaultPhase());
        }

        List<Packet<?>> drain(RuntimePlayerFixture player) throws Exception {
            var field = RuntimePlayerFixture.class.getDeclaredField("channel");
            field.setAccessible(true);
            var channel = (EmbeddedChannel) field.get(player);
            channel.runPendingTasks();
            List<Packet<?>> packets = new ArrayList<>();
            Object outbound;
            while ((outbound = channel.readOutbound()) != null) {
                if (outbound instanceof Packet<?> packet) flatten(packet, packets);
                else io.netty.util.ReferenceCountUtil.release(outbound);
            }
            return packets;
        }

        void assertVisual(RuntimePlayerFixture player, EntityType<?> type, boolean invisible, boolean replacement) throws Exception {
            var packets = drain(player);
            int removed = -1;
            int added = -1;
            ClientboundAddEntityPacket decoded = null;
            ClientboundSetEntityDataPacket metadata = null;
            for (int index = 0; index < packets.size(); index++) {
                var packet = packets.get(index);
                if (packet instanceof ClientboundRemoveEntitiesPacket remove && remove.entityIds().contains(source.getId())) removed = index;
                if (packet instanceof ClientboundAddEntityPacket add && add.getId() == source.getId()) {
                    added = index;
                    decoded = encode(player.player(), add, ClientboundAddEntityPacket.STREAM_CODEC);
                }
                if (packet instanceof ClientboundSetEntityDataPacket data && data.id() == source.getId()) {
                    var candidate = encode(player.player(), data, ClientboundSetEntityDataPacket.STREAM_CODEC);
                    if (candidate.packedItems().stream().anyMatch(value -> value.id() == EntityData.FLAGS.id())) metadata = candidate;
                }
            }
            require(decoded != null && decoded.getType() == type, "Actual client spawn type must be " + type);
            if (replacement) require(removed >= 0 && removed < added, "Old client visual is removed before replacement");
            require(decoded.getId() == source.getId() && decoded.getUUID().equals(source.getUUID()), "Wire identity is preserved");
            EndDragonVisibilityPacketTest.close(source.getY(), decoded.getY(), "Fresh client flight history begins at current server height");
            EndDragonVisibilityPacketTest.close(source.getX(), decoded.getX(), "Replacement uses current X instead of the stale tracker base");
            EndDragonVisibilityPacketTest.close(source.getZ(), decoded.getZ(), "Replacement uses current Z instead of the stale tracker base");
            require(metadata != null, "Pairing includes actual encoded metadata");
            var flags = metadata.packedItems().stream().filter(value -> value.id() == EntityData.FLAGS.id()).findFirst().orElseThrow();
            require(((((Byte) flags.value()) & 0x20) != 0) == invisible, "Client invisibility matches the visual state");
            if (invisible) {
                require((((Byte) flags.value()) & 0x40) == 0, "The hidden proxy cannot render a glow outline");
                var name = metadata.packedItems().stream().filter(value -> value.id() == EntityData.CUSTOM_NAME.id()).findFirst().orElseThrow();
                require(((java.util.Optional<?>) name.value()).isEmpty(), "The hidden proxy has no floating name");
            }
        }

        @Override public void close() {
            untrack(viewer);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            viewer.close();
        }
    }

    private static void flatten(Packet<?> packet, List<Packet<?>> result) {
        if (packet instanceof BundlePacket<?> bundle) {
            for (var child : bundle.subPackets()) flatten(child, result);
        } else result.add(packet);
    }

    private static <T> T encode(ServerPlayer viewer, T packet, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), viewer.level().registryAccess());
        try {
            return PacketContext.supplyWithContext(viewer.connection, () -> {
                codec.encode(buffer, packet);
                return codec.decode(buffer);
            });
        } finally {
            buffer.release();
        }
    }

    private static void close(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1e-6, message + ": " + expected + " / " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
