package kim.biryeong.semionTd.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kim.biryeong.semionTd.SemionTd;
import kim.biryeong.semionTd.config.MapConfig;
import kim.biryeong.semionTd.game.TeamId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplatePlacer;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

public final class GameArenaLoader {
    private GameArenaLoader() {
    }

    public static GameArena load(MinecraftServer server, MapConfig config) throws ArenaLoadException {
        ResourceLocation templateId = parseTemplateId(config.templateId());
        MapTemplate template = loadTemplate(server, templateId);
        BlockPos origin = new BlockPos(config.originX(), config.originY(), config.originZ());

        List<RuntimeWorldHandle> createdWorlds = new ArrayList<>();
        Map<TeamId, TeamArena> teamArenas = new EnumMap<>(TeamId.class);
        try {
            for (TeamId teamId : TeamId.values()) {
                RuntimeWorldHandle worldHandle = Fantasy.get(server).openTemporaryWorld(
                        ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, runtimeWorldPath(teamId)),
                        runtimeWorldConfig(server, config)
                );
                createdWorlds.add(worldHandle);
                worldHandle.setTickWhenEmpty(true);

                ServerLevel world = worldHandle.asWorld();
                new MapTemplatePlacer(template).placeAt(world, origin);
                ArenaLayout layout = ArenaLayout.fromTemplate(template, origin, config.regions());
                teamArenas.put(teamId, new TeamArena(teamId, worldHandle::unload, world, layout));
            }

            return new GameArena(teamArenas);
        } catch (ArenaLoadException | RuntimeException exception) {
            for (RuntimeWorldHandle worldHandle : createdWorlds) {
                worldHandle.unload();
            }
            throw exception;
        }
    }

    private static String runtimeWorldPath(TeamId teamId) {
        return "arena_" + teamId.name().toLowerCase(Locale.ROOT) + "_" + Long.toUnsignedString(System.nanoTime());
    }

    private static RuntimeWorldConfig runtimeWorldConfig(MinecraftServer server, MapConfig config) {
        return new RuntimeWorldConfig()
                .setGenerator(new VoidChunkGenerator(server))
                .setShouldTickTime(false)
                .setTimeOfDay(config.timeOfDay())
                .setDifficulty(Difficulty.NORMAL)
                .setGameRule(GameRules.RULE_DAYLIGHT, false)
                .setGameRule(GameRules.RULE_WEATHER_CYCLE, false)
                .setGameRule(GameRules.RULE_DOMOBSPAWNING, false)
                .setGameRule(GameRules.RULE_MOBGRIEFING, false);
    }

    private static ResourceLocation parseTemplateId(String templateId) throws ArenaLoadException {
        ResourceLocation parsed = ResourceLocation.tryParse(templateId);
        if (parsed == null) {
            throw new ArenaLoadException("Invalid map template id: " + templateId);
        }
        return parsed;
    }

    private static MapTemplate loadTemplate(MinecraftServer server, ResourceLocation templateId)
            throws ArenaLoadException {
        try {
            return MapTemplateSerializer.loadFromResource(server, templateId);
        } catch (IOException exception) {
            throw new ArenaLoadException("Failed to load map template " + templateId + ".", exception);
        }
    }
}
