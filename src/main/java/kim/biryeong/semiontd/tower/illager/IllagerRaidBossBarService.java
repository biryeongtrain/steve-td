package kim.biryeong.semiontd.tower.illager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.IllagerTowerJob;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

public final class IllagerRaidBossBarService {
    private static final int CLIENT_RESYNC_INTERVAL_TICKS = 10;

    private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
    private final Map<UUID, Integer> clientResyncTicks = new HashMap<>();

    public void tick(MinecraftServer server, SemionGame game) {
        tick(server, game, Set.of());
    }

    public void tick(MinecraftServer server, SemionGame game, Set<UUID> protectedPlayerIds) {
        if (server == null || game == null || game.phase() == RoundPhase.WAITING || game.phase() == RoundPhase.ENDED) {
            clearExcept(protectedPlayerIds);
            return;
        }

        Set<UUID> visiblePlayerIds = new HashSet<>();
        if (protectedPlayerIds != null) {
            visiblePlayerIds.addAll(protectedPlayerIds);
        }
        for (SemionPlayer semionPlayer : game.players().values()) {
            UUID playerId = semionPlayer.uuid();
            if (protectedPlayerIds != null && protectedPlayerIds.contains(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !isIllagerBuilder(semionPlayer)) {
                removePlayer(playerId);
                continue;
            }
            Optional<IllagerRaidState> state = IllagerRaidStates.get(playerId);
            if (state.isEmpty()) {
                removePlayer(playerId);
                continue;
            }
            visiblePlayerIds.add(playerId);
            update(player, state.get(), IllagerRaidStates.gaugeMax());
        }
        for (UUID playerId : Set.copyOf(bossBars.keySet())) {
            if (!visiblePlayerIds.contains(playerId)) {
                removePlayer(playerId);
            }
        }
    }

    public void refreshPlayersNow(MinecraftServer server, SemionGame game, Set<UUID> playerIds) {
        if (server == null || game == null || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        Set<UUID> protectedPlayerIds = new HashSet<>(game.players().keySet());
        protectedPlayerIds.removeAll(playerIds);
        tick(server, game, protectedPlayerIds);
    }

    public void clear(MinecraftServer server) {
        for (ServerBossEvent bossBar : bossBars.values()) {
            bossBar.removeAllPlayers();
        }
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

    public void remove(ServerPlayer player) {
        if (player != null) {
            removePlayer(player.getUUID());
        }
    }

    public void removePlayer(UUID playerId) {
        ServerBossEvent bossBar = bossBars.remove(playerId);
        clientResyncTicks.remove(playerId);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
    }

    public boolean hasPlayer(UUID playerId) {
        return bossBars.containsKey(playerId);
    }

    private void update(ServerPlayer player, IllagerRaidState state, int gaugeMax) {
        UUID playerId = player.getUUID();
        Component title = title(state, gaugeMax);
        float progress = progress(state, gaugeMax);
        ServerBossEvent bossBar = bossBars.computeIfAbsent(playerId, ignored -> new ServerBossEvent(
                title,
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        ));
        bossBar.setName(title);
        bossBar.setProgress(progress);
        bossBar.addPlayer(player);
        if (shouldResyncClient(playerId)) {
            player.connection.send(ClientboundBossEventPacket.createAddPacket(bossBar));
        }
    }

    private boolean shouldResyncClient(UUID playerId) {
        int ticks = clientResyncTicks.getOrDefault(playerId, 0) + 1;
        if (ticks < CLIENT_RESYNC_INTERVAL_TICKS) {
            clientResyncTicks.put(playerId, ticks);
            return false;
        }
        clientResyncTicks.put(playerId, 0);
        return true;
    }

    static Component title(IllagerRaidState state, int gaugeMax) {
        int max = Math.max(1, gaugeMax);
        int gauge = Math.min(max, Math.max(0, state == null ? 0 : state.gauge()));
        String status = state != null && state.active() ? "발동 중" : gauge + "/" + max;
        return Component.literal("습격 게이지 - " + status);
    }

    static float progress(IllagerRaidState state, int gaugeMax) {
        int max = Math.max(1, gaugeMax);
        int gauge = Math.min(max, Math.max(0, state == null ? 0 : state.gauge()));
        return (float) gauge / (float) max;
    }

    private static boolean isIllagerBuilder(SemionPlayer player) {
        return player.job().filter(job -> job instanceof IllagerTowerJob).isPresent();
    }
}
