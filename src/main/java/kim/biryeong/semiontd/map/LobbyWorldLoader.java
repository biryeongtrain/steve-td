package kim.biryeong.semiontd.map;

import java.io.IOException;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.map.gen.TemplateChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplatePlacer;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.map_templates.TemplateRegion;

public final class LobbyWorldLoader {
    private static final Identifier LOBBY_TEMPLATE_ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "lobby");
    private static final String LOBBY_WORLD_ID_PREFIX = "lobby_world_";

    private LobbyWorldLoader() {
    }

    public static LobbyWorld load(MinecraftServer server) throws ArenaLoadException {
        MapTemplate template = loadTemplate(server);
        RuntimeLevelHandle worldHandle = Fantasy.get(server).openTemporaryLevel(runtimeWorldId(), runtimeWorldConfig(server, template));
        worldHandle.setTickWhenEmpty(true);

        try {
            ServerLevel world = worldHandle.asLevel();
//            new MapTemplatePlacer(template).placeAt(world, origin);
            Vec3 spawn = requiredSpawn(template);
//            RuntimeWorldWarmup.warmChunksAround(world, BlockPos.containing(spawn), LOBBY_SPAWN_CHUNK_RADIUS);
            return new LobbyWorld(worldHandle::unload, world, spawn);
        } catch (RuntimeException exception) {
            worldHandle.unload();
            throw exception;
        }
    }

    private static Identifier runtimeWorldId() {
        return Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, LOBBY_WORLD_ID_PREFIX + Long.toUnsignedString(System.nanoTime()));
    }

    private static Vec3 requiredSpawn(MapTemplate template) throws ArenaLoadException {
        TemplateRegion region = template.getMetadata().getFirstRegion("spawn");
        if (region == null) {
            throw new ArenaLoadException("Missing map region spawn in lobby template.");
        }
        return region.getBounds().centerTop();
    }

    private static RuntimeLevelConfig runtimeWorldConfig(MinecraftServer server, MapTemplate template) {
        return RuntimeWorldDaylight.configure(new RuntimeLevelConfig())
                .setGenerator(new TemplateChunkGenerator(server, template))
                .setDifficulty(Difficulty.PEACEFUL)
                .setGameRule(GameRules.ADVANCE_WEATHER, false)
                .setGameRule(GameRules.SPAWN_MOBS, false)
                .setGameRule(GameRules.MOB_GRIEFING, false)
                .setGameRule(GameRules.RANDOM_TICK_SPEED, 0)
                .setGameRule(GameRules.FALL_DAMAGE, false);
    }

    private static MapTemplate loadTemplate(MinecraftServer server) throws ArenaLoadException {
        try {
            return MapTemplateSerializer.loadFromResource(server, LOBBY_TEMPLATE_ID);
        } catch (IOException exception) {
            throw new ArenaLoadException("Failed to load lobby map template " + LOBBY_TEMPLATE_ID + ".", exception);
        }
    }
}
