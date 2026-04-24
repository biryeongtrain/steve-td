package kim.biryeong.semionTd.game;

import kim.biryeong.semionTd.config.EconomyConfig;
import kim.biryeong.semionTd.config.MapConfig;
import kim.biryeong.semionTd.config.WaveConfig;
import kim.biryeong.semionTd.map.ArenaLoadException;
import kim.biryeong.semionTd.map.GameArena;
import kim.biryeong.semionTd.map.GameArenaLoader;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

public final class SemionGameManager {
    private EconomyConfig economyConfig = EconomyConfig.defaultConfig();
    private WaveConfig waveConfig = WaveConfig.defaultConfig();
    private MapConfig mapConfig = MapConfig.defaultConfig();
    private MatchMode matchMode = MatchMode.NORMAL;
    private SemionGame activeGame;

    public void configure(EconomyConfig economyConfig, WaveConfig waveConfig, MapConfig mapConfig) {
        this.economyConfig = economyConfig;
        this.waveConfig = waveConfig;
        this.mapConfig = mapConfig;
    }

    public SemionGame createGame(MinecraftServer server) throws ArenaLoadException {
        if (activeGame != null) {
            activeGame.close();
            activeGame = null;
        }

        GameArena arena = GameArenaLoader.load(server, mapConfig);
        activeGame = new SemionGame(economyConfig, waveConfig, arena);
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
        if (activeGame != null) {
            activeGame.tick(server);
        }
    }
}
