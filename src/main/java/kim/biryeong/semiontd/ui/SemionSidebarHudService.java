package kim.biryeong.semiontd.ui;

import eu.pb4.sidebars.api.Sidebar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.SemionGame;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SemionSidebarHudService {
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private int updateTicker;

    public void tick(MinecraftServer server, SemionGame game, MatchMode matchMode) {
        updateTicker++;
        if (updateTicker % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        refreshNow(server, game, matchMode);
    }

    public void refreshNow(MinecraftServer server, SemionGame game, MatchMode matchMode) {
        Set<UUID> onlinePlayerIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlinePlayerIds.add(player.getUUID());
            List<Component> lines = SemionHudTextService.sidebarLinesFor(player, game, matchMode, server);
            if (lines.isEmpty()) {
                remove(player);
            } else {
                update(player, lines);
                updateActionbar(player, game);
            }
        }
        sidebars.keySet().removeIf(playerId -> !onlinePlayerIds.contains(playerId));
    }

    public void clear(MinecraftServer server) {
        updateTicker = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            remove(player);
        }
        sidebars.clear();
    }

    public void remove(ServerPlayer player) {
        Sidebar sidebar = sidebars.remove(player.getUUID());
        if (sidebar != null) {
            sidebar.removePlayer(player);
            sidebar.hide();
        }
    }

    public static void refreshPlayerHud(ServerPlayer player) {
    }

    private void update(ServerPlayer player, List<Component> lines) {
        Sidebar sidebar = sidebar(player);
        sidebar.setTitle(SemionHudTextService.title());
        sidebar.replaceLines(lines.toArray(Component[]::new));
        sidebar.show();
        sidebar.addPlayer(player);
    }

    private void updateActionbar(ServerPlayer player, SemionGame game) {
        SemionHudTextService.actionbarTextFor(player.getUUID(), game)
                .ifPresent(component -> player.displayClientMessage(component, true));
    }

    private Sidebar sidebar(ServerPlayer player) {
        return sidebars.computeIfAbsent(player.getUUID(), ignored -> {
            Sidebar sidebar = new Sidebar(Sidebar.Priority.LOW);
            sidebar.setDefaultNumberFormat(BlankFormat.INSTANCE);
            return sidebar;
        });
    }
}
