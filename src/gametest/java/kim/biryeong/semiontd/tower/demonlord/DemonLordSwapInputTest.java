package kim.biryeong.semiontd.tower.demonlord;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class DemonLordSwapInputTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void swapActionPacketCastsOwnedSkillWithoutSwappingItems(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            f.buy();
            f.startWave();
            f.hands();
            f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            require(f.state.shield() > 0, "The actual swap action packet must cast the owned barrier.");
            require(!f.state.isSkillReady(DemonLordSkill.DEMON_BARRIER, f.now()), "A successful swap cast must start its cooldown.");
            f.unchangedHands();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void stabActionMustNotTriggerTheSwapBinding(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            f.buy();
            f.startWave();
            f.hands();
            f.send(ServerboundPlayerActionPacket.Action.STAB);
            require(f.state.shield() == 0 && f.state.isSkillReady(DemonLordSkill.DEMON_BARRIER, f.now()),
                    "The stab action must never dispatch the swap-bound skill.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void repeatedSwapRespectsCooldownAcrossTickRatesAndFinalDefense(GameTestHelper context) throws Exception {
        float originalRate = context.getLevel().getServer().tickRateManager().tickrate();
        try (var f = new Fixture(context)) {
            f.buy();
            for (float rate : new float[]{20, 40, 20}) {
                context.getLevel().getServer().tickRateManager().setTickRate(rate);
                f.startWave();
                f.state.enterCentralDefense();
                f.hands();
                f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
                require(f.state.shield() > 0, "Swap must cast during final defense at " + rate + " TPS.");
                int cooldown = f.state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, f.now());
                require(cooldown == 400, "Server cooldown must remain 400 ticks at both tick rates.");
                require(f.cooldownDisplayTicks() == Math.round(400 * 20 / rate), "The delivered cooldown must use client tick units.");
                f.state.clearShield();
                for (int i = 0; i < 5; i++) f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
                require(f.state.shield() == 0 && f.state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, f.now()) == cooldown,
                        "Repeated input must neither recast nor extend the cooldown.");
                f.unchangedHands();
                f.state.refundCooldown(DemonLordSkill.DEMON_BARRIER, cooldown);
                f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
                require(f.state.shield() > 0, "An input at the ready deadline must cast again.");
            }
        } finally {
            context.getLevel().getServer().tickRateManager().setTickRate(originalRate);
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void preparationKnockoutSpectatorAndUnloadedClientCannotCast(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            f.buy();
            f.hands();
            f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            require(f.state.shield() == 0 && f.player.getMainHandItem().is(Items.APPLE),
                    "Preparation must preserve ordinary swap behavior without casting.");
            f.startWave();
            f.hands();
            f.player.setGameMode(GameType.SPECTATOR);
            f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            require(f.state.shield() == 0, "Spectator packets cannot cast a combat skill.");
            f.player.setGameMode(GameType.ADVENTURE);
            f.state.leaveCombat();
            f.hands();
            f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            require(f.state.shield() == 0 && f.player.getMainHandItem().is(Items.APPLE),
                    "Knockout must stop skills without capturing ordinary swap.");
            f.startWave();
            f.player.connection.markClientUnloadedAfterDeath();
            f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            require(f.state.shield() == 0, "An unloaded client cannot cast a combat skill.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void unpurchasedSlotAndForeignPlayerCannotUseAnotherPlayersSkill(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            var economy = f.game.players().get(f.player.getUUID()).economy();
            economy.spendDiamond(economy.diamond());
            require(DemonLordSkillShop.buy(f.state, economy, DemonLordBinding.OFFHAND, DemonLordSkill.DEMON_BARRIER)
                    == DemonLordSkillShop.Result.NOT_ENOUGH_DIAMOND, "A missing purchase cannot bypass its resource cost.");
            f.lane.markWaveStarted(1);
            DemonLordService.tick(f.lane, f.game.players());
            f.hands();
            f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            require(f.state.shield() == 0 && f.state.isSkillReady(DemonLordSkill.DEMON_BARRIER, f.now()),
                    "An empty binding must not cast or gain a cooldown.");
            f.unchangedHands();
            f.state.standDown();
            f.buy();
            f.startWave();
            try (var other = RuntimePlayerFixture.connect(context, context.getLevel(), f.player.position(),
                    GameType.ADVENTURE, f.otherId, "other-builder")) {
                receive(other.player(), new ServerboundPlayerLoadedPacket());
                other.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS));
                other.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
                receive(other.player(), new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO, Direction.DOWN));
                require(other.player().getMainHandItem().is(Items.APPLE) && other.player().getOffhandItem().is(Items.COMPASS),
                        "Other players must retain normal swap behavior.");
                require(f.state.shield() == 0, "Another UUID cannot invoke the owner's binding.");
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void reconnectPreservesCooldownAndRejectsTheReplacedConnection(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            f.buy();
            f.startWave();
            f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            require(f.state.shield() > 0, "The initial connection must cast through the packet path.");
            int remaining = f.state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, f.now());
            f.state.clearShield();
            f.manager.handlePlayerDisconnect(f.player);
            kim.biryeong.semiontd.ui.GambleRevealService.clear(f.player.getUUID());
            kim.biryeong.semiontd.tower.demonlord.DemonLordService.disconnectPlayer(f.player);
            kim.biryeong.semiontd.tower.frost.FrostFullOperationService.cleanupPlayer(f.player);
            f.connected.close();
            try (var replacement = RuntimePlayerFixture.connect(context, context.getLevel(), f.player.position(),
                    GameType.ADVENTURE, f.player.getUUID(), "demon-return")) {
                require(f.game.restorePlayerPlacement(context.getLevel().getServer(), replacement.player()), "The combat participant must reconnect.");
                receive(replacement.player(), new ServerboundPlayerLoadedPacket());
                DemonLordService.tick(f.lane, f.game.players());
                require(f.state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, f.now()) == remaining,
                        "Reconnect must preserve the current cooldown.");
                receive(replacement.player(), new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO, Direction.DOWN));
                require(f.state.shield() == 0, "Reconnect must not bypass cooldown.");
                f.state.refundCooldown(DemonLordSkill.DEMON_BARRIER, remaining);
                f.send(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
                require(f.state.shield() == 0, "A superseded connection cannot cast using the replacement's state.");
                receive(replacement.player(), new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO, Direction.DOWN));
                require(f.state.shield() > 0, "The new loaded connection must cast when ready.");
                DemonLordService.cleanupPlayer(replacement.player());
            }
        }
        context.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final RuntimePlayerFixture connected;
        final UUID otherId = UUID.randomUUID();
        final ServerPlayer player;
        final SemionGameManager manager;
        final Field activeField;
        final SemionGame previous;
        final SemionGame game;
        final PlayerLane lane;
        final DemonLordState state;

        Fixture(GameTestHelper context) throws Exception {
            ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
            var initializer = FabricLoader.getInstance().getEntrypoints("main", ModInitializer.class).stream()
                    .filter(SemionTd.class::isInstance).findFirst().orElseThrow();
            var managerField = SemionTd.class.getDeclaredField("gameManager");
            managerField.setAccessible(true);
            manager = (SemionGameManager) managerField.get(initializer);
            activeField = SemionGameManager.class.getDeclaredField("activeGame");
            activeField.setAccessible(true);
            previous = (SemionGame) activeField.get(manager);
            require(previous == null, "The packet fixture requires an idle global match manager.");
            connected = RuntimePlayerFixture.connect(context, context.getLevel(),
                    Vec3.atCenterOf(context.absolutePos(new BlockPos(3, 2, 3))), GameType.ADVENTURE,
                    UUID.randomUUID(), "demon-swap");
            player = connected.player();
            game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
            require(game.selectJob(player.getUUID(), DemonLordTowerJob.ID), "Demon Lord must be selectable.");
            require(game.selectJob(otherId, kim.biryeong.semiontd.job.MagicSchoolTowerJob.ID), "The other participant must use another builder.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL,
                    List.of(new AssignedParticipant(player.getUUID(), "demon-swap", TeamId.RED, 1),
                            new AssignedParticipant(otherId, "other-builder", TeamId.RED, 2)), Set.of(), 2)),
                    "The packet fixture match must start.");
            activeField.set(manager, game);
            lane = game.playerLane(player.getUUID()).orElseThrow();
            connected.enterWorld(context.getLevel(), lane.laneLayout().spawn());
            receive(player, new ServerboundPlayerLoadedPacket());
            require(player.connection.hasClientLoaded(), "The packet handler must accept a fully loaded adventure client.");
            state = DemonLordStates.getOrCreate(player.getUUID());
        }

        void buy() {
            var economy = game.players().get(player.getUUID()).economy();
            economy.addDiamond(100000);
            require(DemonLordSkillShop.buy(state, economy, DemonLordBinding.OFFHAND, DemonLordSkill.DEMON_BARRIER)
                    == DemonLordSkillShop.Result.SUCCESS, "Purchase must assign the real swap binding.");
        }

        void startWave() {
            lane.markWaveStarted(1);
            DemonLordService.tick(lane, game.players());
            require(state.inCombat() && DemonLordService.carrierFor(lane, player.getUUID(), DemonLordBinding.OFFHAND) != null,
                    "The owned combat skill carrier must be present.");
        }

        void hands() {
            player.getInventory().setSelectedSlot(DemonLordSkill.BLADE_SLOT);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
        }

        void unchangedHands() {
            require(player.getMainHandItem().is(Items.NETHERITE_SWORD) && player.getOffhandItem().is(Items.APPLE),
                    "A consumed swap skill input must preserve both hands.");
        }

        void send(ServerboundPlayerActionPacket.Action action) throws Exception {
            receive(player, new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN));
        }

        long now() { return lane.arenaWorld().getGameTime(); }

        int cooldownDisplayTicks() throws Exception {
            var channelField = RuntimePlayerFixture.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            var channel = (io.netty.channel.embedded.EmbeddedChannel) channelField.get(connected);
            channel.runPendingTasks();
            var group = player.getCooldowns().getCooldownGroup(new ItemStack(DemonLordSkill.DEMON_BARRIER.item()));
            return channel.outboundMessages().stream()
                    .filter(net.minecraft.network.protocol.game.ClientboundCooldownPacket.class::isInstance)
                    .map(net.minecraft.network.protocol.game.ClientboundCooldownPacket.class::cast)
                    .filter(packet -> packet.cooldownGroup().equals(group)).reduce((first, last) -> last).orElseThrow().duration();
        }

        @Override public void close() throws Exception {
            activeField.set(manager, previous);
            game.close();
            DemonLordService.cleanupPlayer(player);
            connected.close();
        }
    }

    private static void receive(ServerPlayer player, Packet<?> packet) throws Exception {
        var dispatch = Connection.class.getDeclaredMethod("genericsFtw", Packet.class, PacketListener.class);
        dispatch.setAccessible(true);
        dispatch.invoke(null, packet, player.connection);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
