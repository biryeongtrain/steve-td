package kim.biryeong.semiontd.tower.engineer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.EngineerTowerJob;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

public final class EngineerRedstoneBossBarService {
    private static final int CLIENT_RESYNC_INTERVAL_TICKS = 10;
    private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
    private final Map<UUID, Integer> clientResyncTicks = new HashMap<>();

    public void tick(MinecraftServer server, SemionGame game, Set<UUID> protectedPlayerIds) {
        if (server == null || game == null || game.phase() == RoundPhase.WAITING || game.phase() == RoundPhase.ENDED) {
            clearExcept(protectedPlayerIds);
            return;
        }
        Set<UUID> visible = new HashSet<>();
        if (protectedPlayerIds != null) {
            visible.addAll(protectedPlayerIds);
        }
        for (SemionPlayer semionPlayer : game.players().values()) {
            UUID playerId = semionPlayer.uuid();
            if (protectedPlayerIds != null && protectedPlayerIds.contains(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !isEngineer(semionPlayer)) {
                removePlayer(playerId);
                continue;
            }
            visible.add(playerId);
            update(player, redstoneCount(game, playerId), EngineerBalance.maxRedstone());
        }
        for (UUID playerId : Set.copyOf(bossBars.keySet())) {
            if (!visible.contains(playerId)) {
                removePlayer(playerId);
            }
        }
    }

    public void refreshPlayersNow(MinecraftServer server, SemionGame game, Set<UUID> playerIds) {
        if (server == null || game == null || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        Set<UUID> protectedIds = new HashSet<>(game.players().keySet());
        protectedIds.removeAll(playerIds);
        tick(server, game, protectedIds);
    }

    public void clear(MinecraftServer server) {
        bossBars.values().forEach(ServerBossEvent::removeAllPlayers);
        bossBars.clear();
        clientResyncTicks.clear();
    }

    public void clearExcept(Set<UUID> protectedPlayerIds) {
        if (protectedPlayerIds == null || protectedPlayerIds.isEmpty()) {
            clear(null);
            return;
        }
        for (UUID playerId : Set.copyOf(bossBars.keySet())) {
            if (!protectedPlayerIds.contains(playerId)) {
                removePlayer(playerId);
            }
        }
    }

    public void removePlayer(UUID playerId) {
        ServerBossEvent event = bossBars.remove(playerId);
        clientResyncTicks.remove(playerId);
        if (event != null) {
            event.removeAllPlayers();
        }
    }

    static int redstoneCount(SemionGame game, UUID owner) {
        return game.playerLane(owner)
                .map(lane -> (int) lane.towers().stream()
                        .filter(tower -> owner.equals(tower.ownerPlayer()))
                        .filter(tower -> EngineerTowers.isDust(tower.type())
                                || EngineerTowers.repeaterDirection(tower.type()).isPresent())
                        .count())
                .orElse(0);
    }

    static Component title(int count, int maximum) {
        int max = Math.max(1, maximum);
        int value = Math.max(0, Math.min(max, count));
        return Component.literal("레드스톤 - " + value + "/" + max);
    }

    static float progress(int count, int maximum) {
        int max = Math.max(1, maximum);
        return (float) Math.max(0, Math.min(max, count)) / max;
    }

    private void update(ServerPlayer player, int count, int maximum) {
        Component title = title(count, maximum);
        UUID playerId = player.getUUID();
        ServerBossEvent event = bossBars.computeIfAbsent(playerId, ignored -> new ServerBossEvent(
                title, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS
        ));
        event.setName(title);
        event.setProgress(progress(count, maximum));
        event.addPlayer(player);
        int ticks = clientResyncTicks.getOrDefault(playerId, 0) + 1;
        if (ticks >= CLIENT_RESYNC_INTERVAL_TICKS) {
            clientResyncTicks.put(playerId, 0);
            player.connection.send(ClientboundBossEventPacket.createAddPacket(event));
        } else {
            clientResyncTicks.put(playerId, ticks);
        }
    }

    private static boolean isEngineer(SemionPlayer player) {
        return player.job().filter(job -> EngineerTowerJob.ID.equals(job.id())).isPresent();
    }
}
