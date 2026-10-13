package kim.biryeong.semiontd.tower.demonlord;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.ClientTickScale;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class DemonLordTickScaleSyncTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void cooldownSyncReducesPacketsWithoutChangingDeliveredDurations(GameTestHelper context) throws Exception {
        var server = context.getLevel().getServer();
        float originalRate = server.tickRateManager().tickrate();
        try (var fixture = RuntimePlayerFixture.connect(context, context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5))), GameType.ADVENTURE,
                UUID.randomUUID(), "demon-sync-test")) {
            var player = fixture.player();
            var channel = channel(fixture);
            var skills = DemonLordSkill.values();
            for (float rate : new float[]{20, 40, 20}) {
                server.tickRateManager().setTickRate(rate);
                for (int active : new int[]{0, 1, skills.length}) {
                    var state = new DemonLordState(player.getUUID());
                    long now = context.getLevel().getGameTime();
                    for (int index = 0; index < active; index++) {
                        state.startCooldown(skills[index], now, index == 0 ? 1 : 400);
                    }
                    drain(channel);
                    legacySync(player, state, now);
                    var before = drain(channel);
                    DemonLordService.syncSkillCooldowns(player, state, now);
                    var after = drain(channel);
                    require(before.size() == skills.length * 2, "The reference path must measure two packets per skill.");
                    require(after.size() == skills.length + active, "Only ready-skill duplicate clears may be removed.");
                    require(finalDurations(before).equals(finalDurations(after)),
                            "Every client's final cooldown duration must match the reference at " + rate + " TPS.");
                    for (int index = 0; index < active; index++) {
                        require(state.remainingCooldownTicks(skills[index], now) == (index == 0 ? 1 : 400),
                                "Synchronization must not change server-tick deadlines.");
                    }
                    SemionTd.LOGGER.info("Demon lord cooldown packet measurement: rate={}, active={}, before={}, after={}",
                            rate, active, before.size(), after.size());
                }
            }
        } finally {
            server.tickRateManager().setTickRate(originalRate);
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void syncClearsActiveAndExpiredVanillaEntriesWithoutLateDisplayReset(GameTestHelper context) throws Exception {
        try (var fixture = RuntimePlayerFixture.connect(context, context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5))), GameType.ADVENTURE,
                UUID.randomUUID(), "demon-sync-clear")) {
            var player = fixture.player();
            var channel = channel(fixture);
            var state = new DemonLordState(player.getUUID());
            long now = context.getLevel().getGameTime();
            var barrier = new ItemStack(DemonLordSkill.DEMON_BARRIER.item());
            var wings = new ItemStack(DemonLordSkill.DEMON_WINGS.item());
            player.getCooldowns().addCooldown(barrier, 0);
            player.getCooldowns().addCooldown(wings, 5);
            state.startCooldown(DemonLordSkill.DEMON_BARRIER, now, 400);
            drain(channel);
            DemonLordService.syncSkillCooldowns(player, state, now);
            var packets = drain(channel);
            require(finalDurations(packets).get(player.getCooldowns().getCooldownGroup(barrier))
                            == ClientTickScale.toClientTicks(serverRatio(player), 400),
                    "An expired vanilla entry must not prevent the active server cooldown display.");
            require(!player.getCooldowns().isOnCooldown(wings), "Vanilla cooldown state must still be removed.");
            player.getCooldowns().tick();
            require(drain(channel).isEmpty(), "Expired vanilla entries must not send a late clear after synchronization.");
            require(state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, now) == 400,
                    "The game cooldown must remain authoritative.");
        }
        context.succeed();
    }

    private static void legacySync(ServerPlayer player, DemonLordState state, long now) {
        for (var skill : DemonLordSkill.values()) {
            Identifier group = player.getCooldowns().getCooldownGroup(new ItemStack(skill.item()));
            player.getCooldowns().removeCooldown(group);
            player.connection.send(new ClientboundCooldownPacket(group,
                    ClientTickScale.toClientTicks(serverRatio(player), state.remainingCooldownTicks(skill, now))));
        }
    }

    private static float serverRatio(ServerPlayer player) {
        return ClientTickScale.ratio(player.level().getServer());
    }

    private static EmbeddedChannel channel(RuntimePlayerFixture fixture) throws Exception {
        var field = RuntimePlayerFixture.class.getDeclaredField("channel");
        field.setAccessible(true);
        return (EmbeddedChannel) field.get(fixture);
    }

    private static List<ClientboundCooldownPacket> drain(EmbeddedChannel channel) {
        channel.runPendingTasks();
        var packets = new ArrayList<ClientboundCooldownPacket>();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof ClientboundCooldownPacket packet) packets.add(packet);
            else io.netty.util.ReferenceCountUtil.release(message);
        }
        return packets;
    }

    private static Map<Identifier, Integer> finalDurations(List<ClientboundCooldownPacket> packets) {
        var durations = new HashMap<Identifier, Integer>();
        for (var packet : packets) durations.put(packet.cooldownGroup(), packet.duration());
        return durations;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
