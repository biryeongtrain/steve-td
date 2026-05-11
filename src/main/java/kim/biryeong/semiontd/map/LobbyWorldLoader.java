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
    private static final String LOBBY_WORLD_ID_PREFIX = "lobby_world_";
    private static final int LOBBY_MIN_Y = 16;

    private LobbyWorldLoader() {
    }

    public static LobbyWorld load(MinecraftServer server) throws ArenaLoadException {
        MapTemplate template = loadTemplate(server);
        BlockPos origin = originFor(template);
        RuntimeWorldHandle worldHandle = Fantasy.get(server).openTemporaryWorld(runtimeWorldId(), runtimeWorldConfig(server));
        worldHandle.setTickWhenEmpty(true);

        try {
            ServerLevel world = worldHandle.asWorld();
            new MapTemplatePlacer(template).placeAt(world, origin);
            Vec3 spawn = requiredSpawn(template, origin);
            return new LobbyWorld(worldHandle::unload, world, spawn);
        } catch (RuntimeException exception) {
            worldHandle.unload();
            throw exception;
        }
    }

    private static ResourceLocation runtimeWorldId() {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, LOBBY_WORLD_ID_PREFIX + Long.toUnsignedString(System.nanoTime()));
    }

    private static BlockPos originFor(MapTemplate template) {
        BlockPos min = template.getBounds().min();
        return new BlockPos(-min.getX(), LOBBY_MIN_Y - min.getY(), -min.getZ());
    }

    private static Vec3 requiredSpawn(MapTemplate template, BlockPos origin) throws ArenaLoadException {
        TemplateRegion region = template.getMetadata().getFirstRegion("spawn");
        if (region == null) {
            throw new ArenaLoadException("Missing map region spawn in lobby template.");
        }
        return region.getBounds().offset(origin).centerBottom();
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
