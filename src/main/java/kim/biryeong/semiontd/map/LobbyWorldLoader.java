package kim.biryeong.semiontd.map;

import java.io.IOException;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplatePlacer;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.map_templates.TemplateRegion;

public final class LobbyWorldLoader {
    private static final ResourceLocation LOBBY_TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "lobby");
    private static final ResourceLocation LOBBY_WORLD_ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "lobby_world");
    private static final BlockPos LOBBY_ORIGIN = new BlockPos(0, 64, 0);

    private LobbyWorldLoader() {
    }

    public static LobbyWorld load(MinecraftServer server) throws ArenaLoadException {
        MapTemplate template = loadTemplate(server);
        RuntimeWorldHandle worldHandle = Fantasy.get(server).openTemporaryWorld(LOBBY_WORLD_ID, runtimeWorldConfig(server));
        worldHandle.setTickWhenEmpty(true);

        try {
            ServerLevel world = worldHandle.asWorld();
            new MapTemplatePlacer(template).placeAt(world, LOBBY_ORIGIN);
            Vec3 spawn = requiredSpawn(template);
            return new LobbyWorld(worldHandle::unload, world, spawn);
        } catch (RuntimeException exception) {
            worldHandle.unload();
            throw exception;
        }
    }

    private static Vec3 requiredSpawn(MapTemplate template) throws ArenaLoadException {
        TemplateRegion region = template.getMetadata().getFirstRegion("spawn");
        if (region == null) {
            throw new ArenaLoadException("Missing map region spawn in lobby template.");
        }
        return region.getBounds().offset(LOBBY_ORIGIN).centerBottom();
    }

    private static RuntimeWorldConfig runtimeWorldConfig(MinecraftServer server) {
        return new RuntimeWorldConfig()
                .setGenerator(new VoidChunkGenerator(server))
                .setShouldTickTime(false)
                .setTimeOfDay(6000)
                .setDifficulty(Difficulty.PEACEFUL)
                .setGameRule(GameRules.RULE_DAYLIGHT, false)
                .setGameRule(GameRules.RULE_WEATHER_CYCLE, false)
                .setGameRule(GameRules.RULE_DOMOBSPAWNING, false)
                .setGameRule(GameRules.RULE_MOBGRIEFING, false)
                .setGameRule(GameRules.RULE_FALL_DAMAGE, false);
    }

    private static MapTemplate loadTemplate(MinecraftServer server) throws ArenaLoadException {
        try {
            return MapTemplateSerializer.loadFromResource(server, LOBBY_TEMPLATE_ID);
        } catch (IOException exception) {
            throw new ArenaLoadException("Failed to load lobby map template " + LOBBY_TEMPLATE_ID + ".", exception);
        }
    }
}