package kim.biryeong.semiontd.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

final class RuntimeWorldWarmup {
    private static final int MAX_LIGHT_UPDATE_PASSES = 16;
    private static final int SPAWN_LIGHT_UPDATE_PASSES = 4;

    private RuntimeWorldWarmup() {
    }

    static void warmTemplate(ServerLevel world, MapTemplate template, BlockPos origin) {
        if (!world.getServer().isSameThread()) {
            return;
        }
        BlockBounds worldBounds = template.getBounds().offset(origin);
        loadChunks(world, worldBounds);
        enableLighting(world, worldBounds, MAX_LIGHT_UPDATE_PASSES);
    }

    static void loadTemplateChunks(ServerLevel world, MapTemplate template, BlockPos origin) {
        if (!world.getServer().isSameThread()) {
            return;
        }
        loadChunks(world, template.getBounds().offset(origin));
    }

    static void loadChunks(ServerLevel world, BlockBounds bounds) {
        if (!world.getServer().isSameThread()) {
            return;
        }
        var chunkIterator = bounds.asChunks().iterator();
        while (chunkIterator.hasNext()) {
            long packedChunk = chunkIterator.nextLong();
            int chunkX = ChunkPos.getX(packedChunk);
            int chunkZ = ChunkPos.getZ(packedChunk);
            world.getChunk(chunkX, chunkZ);
        }
    }

    static void warmChunksAround(ServerLevel world, BlockPos center, int chunkRadius) {
        if (!world.getServer().isSameThread()) {
            return;
        }
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        LevelLightEngine lightEngine = world.getChunkSource().getLightEngine();
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                lightEngine.setLightEnabled(chunkPos, true);
                lightEngine.propagateLightSources(chunkPos);
            }
        }

        runLightUpdates(lightEngine, SPAWN_LIGHT_UPDATE_PASSES);
    }

    private static void enableLighting(ServerLevel world, BlockBounds worldBounds, int maxPasses) {
        LevelLightEngine lightEngine = world.getChunkSource().getLightEngine();
        var chunkIterator = worldBounds.asChunks().iterator();
        while (chunkIterator.hasNext()) {
            long packedChunk = chunkIterator.nextLong();
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(packedChunk), ChunkPos.getZ(packedChunk));
            lightEngine.setLightEnabled(chunkPos, true);
            lightEngine.propagateLightSources(chunkPos);
        }

        runLightUpdates(lightEngine, maxPasses);
    }

    private static void runLightUpdates(LevelLightEngine lightEngine, int maxPasses) {
        for (int pass = 0; pass < maxPasses && lightEngine.hasLightWork(); pass++) {
            lightEngine.runLightUpdates();
        }
    }
}
