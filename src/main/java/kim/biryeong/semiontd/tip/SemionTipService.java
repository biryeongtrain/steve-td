package kim.biryeong.semiontd.tip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TipConfig;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SemionTipService {
    private final SemionGameManager gameManager;
    private final Map<UUID, PlayerTipState> states = new HashMap<>();
    private TipConfig renderedConfig;
    private Component renderedJoinMessage;
    private List<Component> renderedMessages = List.of();

    public SemionTipService(SemionGameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void tick(MinecraftServer server) {
        TipConfig config = gameManager.tipConfig();
        refreshRenderedMessages(config);
        states.keySet().removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
        if (!config.enabled()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTipState state = states.computeIfAbsent(
                    player.getUUID(),
                    ignored -> loadState(player, config.intervalTicks(), false)
            );
            state.syncInterval(config.intervalTicks());
            if (!state.enabled) {
                continue;
            }
            if (state.joinPending && renderedJoinMessage != null) {
                player.sendSystemMessage(renderedJoinMessage);
            }
            state.joinPending = false;
            if (renderedMessages.isEmpty()) {
                continue;
            }
            SemionGame game = gameManager.playableGame(player.getUUID()).orElse(null);
            if (game == null || game.phase() == RoundPhase.WAITING || game.phase() == RoundPhase.ENDED) {
                continue;
            }
            state.remainingTicks--;
            if (state.remainingTicks <= 0) {
                sendNext(player, state);
                state.remainingTicks = config.intervalTicks();
            }
        }
    }

    public void handlePlayerJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        TipConfig config = gameManager.tipConfig();
        refreshRenderedMessages(config);
        boolean joinPending = config.enabled() && config.joinEnabled() && renderedJoinMessage != null;
        states.put(player.getUUID(), loadState(player, config.intervalTicks(), joinPending));
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (player != null) {
            states.remove(player.getUUID());
        }
    }

    public boolean tipsEnabled(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        PlayerTipState state = states.get(player.getUUID());
        return state == null ? profile(player).tipsEnabled() : state.enabled;
    }

    public void setTipsEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return;
        }
        gameManager.saveTipsEnabled(
                player.getServer(),
                player.getUUID(),
                player.getGameProfile().getName(),
                enabled
        );
        TipConfig config = gameManager.tipConfig();
        PlayerTipState state = states.computeIfAbsent(
                player.getUUID(),
                ignored -> loadState(player, config.intervalTicks(), false)
        );
        state.enabled = enabled;
        state.joinPending = false;
        state.remainingTicks = config.intervalTicks();
        state.intervalTicks = config.intervalTicks();
    }

    public void shutdown() {
        states.clear();
        renderedJoinMessage = null;
        renderedMessages = List.of();
        renderedConfig = null;
    }

    private PlayerTipState loadState(ServerPlayer player, int intervalTicks, boolean joinPending) {
        return new PlayerTipState(profile(player).tipsEnabled(), joinPending, intervalTicks);
    }

    private SemionPlayerProfile profile(ServerPlayer player) {
        return gameManager.profile(
                player.getServer(),
                player.getUUID(),
                player.getGameProfile().getName()
        );
    }

    private void refreshRenderedMessages(TipConfig config) {
        if (config.equals(renderedConfig)) {
            return;
        }
        renderedConfig = config;
        renderedJoinMessage = parseJoinMessage(config);
        ArrayList<Component> parsed = new ArrayList<>();
        for (int index = 0; index < config.messages().size(); index++) {
            String markup = config.messages().get(index);
            try {
                parsed.add(SemionText.mini(markup));
            } catch (RuntimeException exception) {
                SemionTd.LOGGER.warn("Skipping invalid MiniMessage tip at index {}: {}", index, markup, exception);
            }
        }
        renderedMessages = List.copyOf(parsed);
    }

    private Component parseJoinMessage(TipConfig config) {
        if (!config.joinEnabled() || config.joinMessage().isBlank()) {
            return null;
        }
        try {
            return SemionText.mini(config.joinMessage());
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.warn("Skipping invalid MiniMessage join tip: {}", config.joinMessage(), exception);
            return null;
        }
    }

    private void sendNext(ServerPlayer player, PlayerTipState state) {
        int index = Math.floorMod(state.nextMessageIndex++, renderedMessages.size());
        player.sendSystemMessage(renderedMessages.get(index));
    }

    private static final class PlayerTipState {
        private boolean enabled;
        private boolean joinPending;
        private int intervalTicks;
        private int remainingTicks;
        private int nextMessageIndex;

        private PlayerTipState(boolean enabled, boolean joinPending, int intervalTicks) {
            this.enabled = enabled;
            this.joinPending = joinPending;
            this.intervalTicks = intervalTicks;
            this.remainingTicks = intervalTicks;
        }

        private void syncInterval(int requestedIntervalTicks) {
            if (intervalTicks == requestedIntervalTicks) {
                return;
            }
            intervalTicks = requestedIntervalTicks;
            remainingTicks = requestedIntervalTicks;
        }
    }
}
