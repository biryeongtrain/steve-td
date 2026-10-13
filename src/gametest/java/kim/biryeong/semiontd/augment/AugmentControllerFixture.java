package kim.biryeong.semiontd.augment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public abstract class AugmentControllerFixture {
    protected static boolean selfTargeted(kim.biryeong.semiontd.game.SemionPlayer player, String id) {
        return AugmentService.selfTargeted(player, id);
    }

    protected static void warmPlayerSpawn(GameTestHelper context) {
        // Login waits for entity storage too; let it tick before creating a mock player in isolation.
        net.minecraft.world.level.ChunkPos.rangeClosed(net.minecraft.world.level.ChunkPos.containing(context.getLevel().getRespawnData().pos()), 2)
                .forEach(pos -> context.getLevel().getChunk(pos.x(), pos.z()));
    }

    protected static SemionGame prepare(GameTestHelper context, ServerPlayer online) {
        return prepare(context, online, "SSS");
    }

    protected static SemionGame prepare(GameTestHelper context, ServerPlayer online, String raritySchedule) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        Map<String, Integer> weights = new LinkedHashMap<>();
        AugmentConfig.defaults().rarityWeights().keySet().forEach(key -> weights.put(key, key.equals(raritySchedule) ? 100 : 0));
        game.configureAugments(new AugmentConfig(true, false, weights, Map.of(), Set.of()));
        require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                        new AssignedParticipant(online.getUUID(), "controller-red", TeamId.RED, 1),
                        new AssignedParticipant(UUID.randomUUID(), "controller-blue", TeamId.BLUE, 1)), Set.of(), 2)),
                "The synthetic NORMAL game must start.");
        for (var team : game.teams().values()) {team.laneGroup().disableMonsters();}
        setField(game, "currentRound", 4);
        setField(game, "phase", RoundPhase.ROUND_PAYOUT);
        game.tick(context.getLevel().getServer());
        require(game.currentRound() == 5 && game.phase() == RoundPhase.PREPARE_AND_SUMMON, "The real payout hook must enter R5 preparation.");
        return game;
    }

    protected static void holdTargetTool(ServerPlayer online) {
        for (int index = 0; index < online.getInventory().getContainerSize(); index++) {
            var stack = online.getInventory().getItem(index);
            if (AugmentTargetTool.isTool(stack)) {
                online.getInventory().setItem(index, net.minecraft.world.item.ItemStack.EMPTY);
                online.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                return;
            }
        }
        throw new AssertionError("The targeted augment must grant a tool.");
    }

    protected static int toolCount(ServerPlayer online) {
        int count = 0;
        for (int index = 0; index < online.getInventory().getContainerSize(); index++) {
            if (AugmentTargetTool.isTool(online.getInventory().getItem(index))) {count++;}
        }
        return count;
    }

    protected static Tower addTarget(SemionGame game, ServerPlayer online) {
        return addTarget(game, online, UndeadTowers.T1_ZOMBIE_TOWER);
    }

    protected static Tower addTarget(SemionGame game, ServerPlayer online, kim.biryeong.semiontd.tower.TowerType type) {
        var lane = game.playerLane(online.getUUID()).orElseThrow();
        var position = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        Tower tower = ProductionTowerCatalog.entry(type).orElseThrow()
                .create(online.getUUID(), TeamId.RED, 1, position);
        lane.addTower(tower);
        require(AugmentCombat.isNormalPermanent(tower), "Target fixture must be a registered ordinary owned tower.");
        return tower;
    }

    protected static void force(SemionGame game, ServerPlayer online, String cards) {
        require(game.augmentService().handle(game, online, "force 5 " + cards, true) == 1, "An authorized internal force-offer must succeed.");
    }

    protected static int handle(SemionGame game, ServerPlayer online, String input) {
        try {
            var token = AugmentService.class.getDeclaredField("sessionToken");
            token.setAccessible(true);
            return game.augmentService().handle(game, online, "session " + token.get(game.augmentService()) + " " + input, false);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot obtain the actual native-button match token.", exception);
        }
    }

    protected static void advance(SemionGame game, ServerPlayer online, int ticks) {
        for (int i = 0; i < ticks; i++) {game.tick(online.level().getServer());}
    }

    protected static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot prepare controller fixture: " + name, exception);
        }
    }

    protected static void require(boolean condition, String message) {
        if (!condition) {throw new AssertionError(message);}
    }

    protected static java.util.ArrayList<net.minecraft.network.protocol.Packet<?>> collectPackets(net.minecraft.server.level.ServerPlayer online) {
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        online.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(online.level().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), online,
                net.minecraft.server.network.CommonListenerCookie.createInitial(online.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                packets.add(packet);
            }
        };
        eu.pb4.polymer.common.api.PolymerCommonUtils.setHasResourcePack(online,
                eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.getMainUuid(), true);
        require(eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(online),
                "The packet fixture represents a client that loaded the card resource pack.");
        return packets;
    }

    protected static long clearPackets(java.util.List<net.minecraft.network.protocol.Packet<?>> packets) {
        return packets.stream().filter(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.class::isInstance).count();
    }

    protected static long showPackets(java.util.List<net.minecraft.network.protocol.Packet<?>> packets) {
        return packets.stream().filter(net.minecraft.network.protocol.common.ClientboundShowDialogPacket.class::isInstance).count();
    }
}
