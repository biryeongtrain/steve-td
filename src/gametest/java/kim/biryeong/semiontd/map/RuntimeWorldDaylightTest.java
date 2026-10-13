package kim.biryeong.semiontd.map;

import kim.biryeong.semiontd.config.MapConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import xyz.nucleoid.fantasy.Fantasy;

public final class RuntimeWorldDaylightTest {
    @GameTest(maxTicks = 120)
    public void lobbyAndEveryArenaStayAtNoonIncludingNightConfiguredReload(GameTestHelper context) throws Exception {
        var server = context.getLevel().getServer();
        var original = server.clockManager().packState();
        var defaults = MapConfig.defaultConfig();
        var night = new MapConfig(defaults.templateId(), defaults.originX(), defaults.originY(), defaults.originZ(),
                18000, defaults.regions());
        for (int load = 0; load < 2; load++) {
            var lobby = LobbyWorldLoader.load(server);
            var arena = GameArenaLoader.load(server, night);
            try {
                verify(context, lobby.world());
                for (var team : kim.biryeong.semiontd.game.TeamId.values()) {
                    verify(context, arena.teamArena(team).orElseThrow().world());
                }
                context.assertTrue(server.clockManager().packState().equals(original), "Other server clocks remain untouched");
            } finally {
                lobby.unload();
                arena.unload();
            }
        }
        context.succeed();
    }

    private static void verify(GameTestHelper context, ServerLevel world) {
        var clock = world.clockManager().getInstance(world.registryAccess().getOrThrow(Fantasy.DEFAULT_CLOCK));
        context.assertTrue(clock.totalTicks() == RuntimeWorldDaylight.NOON && clock.isPaused(), "Runtime world initializes to paused noon");
        context.assertTrue(!world.getGameRules().get(GameRules.ADVANCE_TIME), "Daylight cycle stays disabled");
        for (int tick = 0; tick < 24000; tick++) world.clockManager().tick();
        context.assertTrue(clock.totalTicks() == RuntimeWorldDaylight.NOON, "An entire simulated day cannot advance the runtime clock");
    }
}
