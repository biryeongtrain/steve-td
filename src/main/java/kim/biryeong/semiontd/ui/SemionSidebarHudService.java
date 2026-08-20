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
import kim.biryeong.semiontd.tutorial.TutorialService.HighlightTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SemionSidebarHudService {
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private final Set<UUID> damageViewers = new HashSet<>();
    private int updateTicker;

    public void tick(MinecraftServer server, SemionGame game, MatchMode matchMode) {
        tick(server, game, matchMode, Set.of());
    }

    public void tick(MinecraftServer server, SemionGame game, MatchMode matchMode, Set<UUID> protectedPlayerIds) {
        updateTicker++;
        if (updateTicker % updateIntervalTicks(server.tickRateManager().tickrate()) != 0) {
            return;
        }

        refreshNow(server, game, matchMode, protectedPlayerIds);
    }

    static int updateIntervalTicks(float tickRate) {
        return !Float.isFinite(tickRate) || tickRate <= 20.0F
                ? UPDATE_INTERVAL_TICKS
                : Math.max(UPDATE_INTERVAL_TICKS, Math.round(UPDATE_INTERVAL_TICKS * tickRate / 20.0F));
    }

    public void refreshNow(MinecraftServer server, SemionGame game, MatchMode matchMode) {
        refreshNow(server, game, matchMode, Set.of());
    }

    public void refreshNow(MinecraftServer server, SemionGame game, MatchMode matchMode, Set<UUID> protectedPlayerIds) {
        Set<UUID> onlinePlayerIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlinePlayerIds.add(player.getUUID());
            if (protectedPlayerIds.contains(player.getUUID())) {
                continue;
            }
            List<Component> lines = sidebarLinesFor(player, game, matchMode, server);
            if (lines.isEmpty()) {
                remove(player);
            } else {
                update(player, lines);
                updateActionbar(player, game);
            }
        }
        sidebars.keySet().removeIf(playerId -> !onlinePlayerIds.contains(playerId));
        damageViewers.removeIf(playerId -> !onlinePlayerIds.contains(playerId));
    }

    public void refreshPlayersNow(MinecraftServer server, SemionGame game, MatchMode matchMode, Set<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return;
        }
        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            List<Component> lines = sidebarLinesFor(player, game, matchMode, server);
            if (lines.isEmpty()) {
                remove(player);
            } else {
                update(player, lines);
                updateActionbar(player, game);
            }
        }
    }

    public void refreshTutorialPlayerNow(
            MinecraftServer server,
            SemionGame game,
            MatchMode matchMode,
            UUID playerId,
            HighlightTarget highlightTarget
    ) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        boolean highlightOn = tutorialHighlightOn(server.getTickCount());
        List<Component> lines = SemionHudTextService.sidebarLinesFor(
                player,
                game,
                matchMode,
                server,
                false,
                highlightTarget,
                highlightOn
        );
        if (lines.isEmpty()) {
            remove(player);
            return;
        }
        update(player, lines);
        updateActionbar(player, game, highlightTarget, highlightOn);
    }

    static boolean tutorialHighlightOn(int serverTick) {
        return Math.floorDiv(serverTick, UPDATE_INTERVAL_TICKS) % 2 == 0;
    }

    public void clear(MinecraftServer server) {
        updateTicker = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            remove(player);
        }
        sidebars.clear();
        damageViewers.clear();
    }

    public void remove(ServerPlayer player) {
        damageViewers.remove(player.getUUID());
        Sidebar sidebar = sidebars.remove(player.getUUID());
        if (sidebar != null) {
            sidebar.removePlayer(player);
            sidebar.hide();
        }
    }

    public static void refreshPlayerHud(ServerPlayer player) {
    }

    public boolean toggleDamageView(UUID playerId) {
        if (damageViewers.remove(playerId)) {
            return false;
        }
        damageViewers.add(playerId);
        return true;
    }

    public boolean damageViewEnabled(UUID playerId) {
        return damageViewers.contains(playerId);
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

    private void updateActionbar(
            ServerPlayer player,
            SemionGame game,
            HighlightTarget highlightTarget,
            boolean highlightOn
    ) {
        SemionHudTextService.actionbarTextFor(player.getUUID(), game, highlightTarget, highlightOn)
                .ifPresent(component -> player.displayClientMessage(component, true));
    }

    private List<Component> sidebarLinesFor(
            ServerPlayer player,
            SemionGame game,
            MatchMode matchMode,
            MinecraftServer server
    ) {
        return SemionHudTextService.sidebarLinesFor(
                player,
                game,
                matchMode,
                server,
                damageViewEnabled(player.getUUID())
        );
    }

    private Sidebar sidebar(ServerPlayer player) {
        return sidebars.computeIfAbsent(player.getUUID(), ignored -> {
            Sidebar sidebar = new Sidebar(Sidebar.Priority.LOW);
            sidebar.setDefaultNumberFormat(BlankFormat.INSTANCE);
            return sidebar;
        });
    }
}
