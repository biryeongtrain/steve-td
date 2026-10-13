package kim.biryeong.semiontd.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.gen.TemplateChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplatePlacer;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

public final class GameArenaLoader {
    private GameArenaLoader() {
    }

    public static GameArena load(MinecraftServer server, MapConfig config) throws ArenaLoadException {
        Identifier templateId = parseTemplateId(config.templateId());
        MapTemplate template = loadTemplate(server, templateId);
        BlockPos origin = new BlockPos(config.originX(), config.originY(), config.originZ());

        List<RuntimeLevelHandle> createdWorlds = new ArrayList<>();
        Map<TeamId, TeamArena> teamArenas = new EnumMap<>(TeamId.class);
        try {
            for (TeamId teamId : TeamId.values()) {
                RuntimeLevelHandle worldHandle = Fantasy.get(server).openTemporaryLevel(
                        Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, runtimeWorldPath(teamId)),
                        runtimeWorldConfig(server, config, template)
                );
                createdWorlds.add(worldHandle);
                worldHandle.setTickWhenEmpty(true);

                ServerLevel world = worldHandle.asLevel();
//                new MapTemplatePlacer(template).placeAt(world, origin);
//                RuntimeWorldWarmup.loadTemplateChunks(world, template, origin);
                ArenaLayout layout = ArenaLayout.fromTemplate(template, config.regions());
                teamArenas.put(teamId, new TeamArena(teamId, worldHandle::unload, world, layout));
            }

            return new GameArena(teamArenas);
        } catch (ArenaLoadException | RuntimeException exception) {
            for (RuntimeLevelHandle worldHandle : createdWorlds) {
                worldHandle.unload();
            }
            throw exception;
        }
    }

    private static String runtimeWorldPath(TeamId teamId) {
        return "arena_" + teamId.name().toLowerCase(Locale.ROOT) + "_" + Long.toUnsignedString(System.nanoTime());
    }

    private static RuntimeLevelConfig runtimeWorldConfig(MinecraftServer server, MapConfig config, MapTemplate template) {
        return RuntimeWorldDaylight.configure(new RuntimeLevelConfig())
                .setGenerator(new TemplateChunkGenerator(server, template))
                .setDifficulty(Difficulty.NORMAL)
                .setGameRule(GameRules.ADVANCE_WEATHER, false)
                .setGameRule(GameRules.SPAWN_MOBS, false)
                .setGameRule(GameRules.MOB_GRIEFING, false);
    }

    private static Identifier parseTemplateId(String templateId) throws ArenaLoadException {
        Identifier parsed = Identifier.tryParse(templateId);
        if (parsed == null) {
            throw new ArenaLoadException("Invalid map template id: " + templateId);
        }
        return parsed;
    }

    private static MapTemplate loadTemplate(MinecraftServer server, Identifier templateId)
            throws ArenaLoadException {
        try {
            return MapTemplateSerializer.loadFromResource(server, templateId);
        } catch (IOException exception) {
            throw new ArenaLoadException("Failed to load map template " + templateId + ".", exception);
        }
    }
}
