package kim.biryeong.semionTd.game;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semionTd.SemionTd;
import kim.biryeong.semionTd.config.EconomyConfig;
import kim.biryeong.semionTd.config.MapConfig;
import kim.biryeong.semionTd.config.ProgressionConfig;
import kim.biryeong.semionTd.config.SummonConfig;
import kim.biryeong.semionTd.config.WaveConfig;
import kim.biryeong.semionTd.map.ArenaLoadException;
import kim.biryeong.semionTd.map.GameArena;
import kim.biryeong.semionTd.map.GameArenaLoader;
import kim.biryeong.semionTd.map.LobbyWorld;
import kim.biryeong.semionTd.map.LobbyWorldLoader;
import kim.biryeong.semionTd.progression.MatchProgressionReward;
import kim.biryeong.semionTd.progression.ProgressionService;
import kim.biryeong.semionTd.progression.SemionPlayerProfile;
import kim.biryeong.semionTd.ui.SemionDialogService;
import kim.biryeong.semionTd.ui.SemionDisplayHudService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;

public final class SemionGameManager {
    private EconomyConfig economyConfig = EconomyConfig.defaultConfig();
    private WaveConfig waveConfig = WaveConfig.defaultConfig();
    private MapConfig mapConfig = MapConfig.defaultConfig();
    private SummonConfig summonConfig = SummonConfig.defaultConfig();
    private ProgressionConfig progressionConfig = ProgressionConfig.defaultConfig();
    private Path progressionStorePath;
    private ProgressionService progressionService;
    private final SemionDialogService dialogService = new SemionDialogService();
    private final SemionDisplayHudService displayHudService = new SemionDisplayHudService();
    private MatchMode matchMode = MatchMode.NORMAL;
    private SemionGame activeGame;
    private LobbyWorld lobbyWorld;
    private MatchResult lastMatchResult;

    public void configure(
            EconomyConfig economyConfig,
            WaveConfig waveConfig,
            MapConfig mapConfig,
            SummonConfig summonConfig,
            ProgressionConfig progressionConfig,
            Path progressionStorePath
    ) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.mapConfig = mapConfig;
        this.summonConfig = summonConfig;
        this.progressionConfig = progressionConfig;
        this.progressionStorePath = progressionStorePath;
        this.progressionService = new ProgressionService(progressionConfig, progressionStorePath);
    }

    public LobbyWorld ensureLobby(MinecraftServer server) throws ArenaLoadException {
        if (lobbyWorld != null) {
            return lobbyWorld;
        }
        lobbyWorld = LobbyWorldLoader.load(server);
        return lobbyWorld;
    }

    public Optional<LobbyWorld> lobbyWorld() {
        return Optional.ofNullable(lobbyWorld);
    }

    public ProgressionConfig progressionConfig() {
        return progressionConfig;
    }

    public SemionPlayerProfile profile(MinecraftServer server, UUID playerId, String playerName) {
        return progressionService.profile(server, playerId, playerName);
    }

    public Optional<MatchResult> lastMatchResult() {
        return Optional.ofNullable(lastMatchResult);
    }

    public SemionDialogService dialogService() {
        return dialogService;
    }

    public SemionDisplayHudService displayHudService() {
        return displayHudService;
    }

    public void sendAllPlayersToLobby(MinecraftServer server) throws ArenaLoadException {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendPlayerToLobby(server, player);
        }
    }

    public SemionGame createGame(MinecraftServer server) throws ArenaLoadException {
        ensureLobby(server);

        if (activeGame != null) {
            displayHudService.clear(server);
            activeGame.close();
            activeGame = null;
        }

        GameArena arena = GameArenaLoader.load(server, mapConfig);
        activeGame = new SemionGame(economyConfig, waveConfig, summonConfig, arena);
        lastMatchResult = null;
        VanillaTeamBridge.ensureTeams(server);
        return activeGame;
    }

    public Optional<SemionGame> activeGame() {
        return Optional.ofNullable(activeGame);
    }

    public MatchMode matchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode;
    }

    public void tick(MinecraftServer server) {
        if (activeGame == null) {
            return;
        }

        activeGame.tick(server);
        if (activeGame != null && activeGame.phase() == RoundPhase.ENDED) {
            finishActiveGame(server, activeGame);
            return;
        }
        if (activeGame != null) {
            displayHudService.tick(server, activeGame);
        }
    }

    public void handlePlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        VanillaTeamBridge.ensureTeams(server);

        if (activeGame != null && activeGame.rosterLocked()) {
            if (activeGame.restorePlayerPlacement(server, player)) {
                return;
            }

            VanillaTeamBridge.assignSpectator(server, player);
            try {
                sendPlayerToLobby(server, player);
            } catch (ArenaLoadException exception) {
                SemionTd.LOGGER.warn("Failed to send late-joining player {} to lobby.", player.getGameProfile().getName(), exception);
            }
            player.sendSystemMessage(Component.literal("A Semion TD match is already in progress. You are waiting in the lobby."));
            return;
        }

        if (lobbyWorld != null) {
            try {
                sendPlayerToLobby(server, player);
            } catch (ArenaLoadException exception) {
                SemionTd.LOGGER.warn("Failed to send player {} to lobby.", player.getGameProfile().getName(), exception);
            }
        }
    }

    public void shutdown() {
        if (activeGame != null) {
            activeGame.close();
            activeGame = null;
        }
        if (lobbyWorld != null) {
            lobbyWorld.unload();
            lobbyWorld = null;
        }
    }

    private void sendPlayerToLobby(MinecraftServer server, ServerPlayer player) throws ArenaLoadException {
        LobbyWorld lobby = ensureLobby(server);
        player.setGameMode(GameType.ADVENTURE);
        player.teleportTo(
                lobby.world(),
                lobby.spawn().x,
                lobby.spawn().y,
                lobby.spawn().z,
                java.util.Set.<Relative>of(),
                player.getYRot(),
                player.getXRot(),
                false
        );
    }

    private void finishActiveGame(MinecraftServer server, SemionGame finishedGame) {
        displayHudService.clear(server);
        Optional<MatchResult> result = finishedGame.matchResult();
        if (result.isPresent()) {
            lastMatchResult = result.get();
            Map<UUID, MatchProgressionReward> rewards = progressionService.applyMatchResult(server, result.get());
            announceMatchResult(server, result.get(), rewards);
            showMatchResultDialogs(server, result.get(), rewards);
        } else {
            lastMatchResult = null;
        }

        try {
            sendAllPlayersToLobby(server);
        } catch (ArenaLoadException exception) {
            SemionTd.LOGGER.warn("Failed to send players to the Semion TD lobby after match end.", exception);
        }

        finishedGame.close();
        if (activeGame == finishedGame) {
            activeGame = null;
        }
    }

    private void announceMatchResult(
            MinecraftServer server,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        String winners = matchResult.winningTeams().isEmpty()
                ? "none"
                : matchResult.winningTeams().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Semion TD match ended. Winners=" + winners + ", round=" + matchResult.finalRound() + "."),
                false
        );

        for (MatchParticipantResult participant : matchResult.participants()) {
            MatchProgressionReward reward = rewards.get(participant.playerId());
            if (reward == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player == null) {
                continue;
            }
            player.sendSystemMessage(Component.literal(
                    "Cosmetic currency +" + reward.currencyAwarded()
                            + " | games=" + reward.profile().gamesPlayed()
                            + ", wins=" + reward.profile().wins()
                            + ", losses=" + reward.profile().losses()
                            + ", total=" + reward.profile().cosmeticCurrency()
            ));
        }
    }

    private void showMatchResultDialogs(
            MinecraftServer server,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            dialogService.showMatchResult(player, matchResult, rewards);
        }
    }
}
