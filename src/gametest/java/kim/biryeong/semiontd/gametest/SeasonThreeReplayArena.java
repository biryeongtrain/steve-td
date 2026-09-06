package kim.biryeong.semiontd.gametest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.ArenaLoadException;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.map.TeamArena;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplatePlacer;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

/** Real map blocks and metadata, reused by sequential trials without creating runtime worlds. */
public final class SeasonThreeReplayArena {
    private SeasonThreeReplayArena() {
    }

    /** Validates the participant lane count while retaining all five lanes and the shared defense terrain. */
    public static ArenaFixture createArena(GameTestHelper context, BlockPos relativeOffset, int lanesPerTeam) {
        if (lanesPerTeam < 1 || lanesPerTeam > SemionTeam.MAX_PLAYERS) {
            throw new IllegalArgumentException("Lane count must be between 1 and " + SemionTeam.MAX_PLAYERS);
        }
        try {
            MapConfig config = MapConfig.defaultConfig();
            var server = context.getLevel().getServer();
            var resourcePath = MapTemplateSerializer.getResourcePathFor(ResourceLocation.parse(config.templateId()));
            byte[] bytes;
            try (var input = server.getResourceManager().getResourceOrThrow(resourcePath).open()) {
                bytes = input.readAllBytes();
            }
            MapTemplate template = MapTemplateSerializer.loadFrom(new ByteArrayInputStream(bytes), server.registryAccess());
            String templateSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            ArenaLayout layout = ArenaLayout.fromTemplate(template, config.regions());
            return new ArenaFixture(context.getLevel(), context.absolutePos(relativeOffset), template, layout,
                    config.templateId(), templateSha256);
        } catch (IOException | ArenaLoadException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not load the production arena for replay", exception);
        }
    }

    public static final class ArenaFixture implements AutoCloseable {
        private final ServerLevel world;
        private final Map<TeamId, BlockBounds> bounds = new EnumMap<>(TeamId.class);
        private final Set<Long> forcedChunks = new HashSet<>();
        private final GameArena gameArena;
        private final String templateId;
        private final String templateSha256;
        private boolean closed;

        private ArenaFixture(ServerLevel world, BlockPos origin, MapTemplate template, ArenaLayout layout,
                             String templateId, String templateSha256) {
            this.world = world;
            this.templateId = templateId;
            this.templateSha256 = templateSha256;
            Map<TeamId, TeamArena> teams = new EnumMap<>(TeamId.class);
            Map<TeamId, BlockPos> offsets = new EnumMap<>(TeamId.class);
            int separation = ((template.getBounds().max().getX() - template.getBounds().min().getX()
                    + 1 + 127) / 128) * 128 + 128;
            offsets.put(TeamId.RED, origin);
            offsets.put(TeamId.BLUE, origin.offset(separation, 0, 0));
            for (Map.Entry<TeamId, BlockPos> entry : offsets.entrySet()) {
                BlockBounds target = template.getBounds().offset(entry.getValue());
                requireEmpty(world, target);
                bounds.put(entry.getKey(), target);
                teams.put(entry.getKey(), new TeamArena(entry.getKey(), () -> {}, world,
                        translate(layout, entry.getValue())));
            }
            gameArena = new GameArena(teams);
            try {
                for (Map.Entry<TeamId, BlockPos> entry : offsets.entrySet()) {
                    BlockBounds target = bounds.get(entry.getKey());
                    for (int x = target.min().getX() >> 4; x <= target.max().getX() >> 4; x++) {
                        for (int z = target.min().getZ() >> 4; z <= target.max().getZ() >> 4; z++) {
                            world.getChunk(x, z);
                            if (world.setChunkForced(x, z, true)) {
                                forcedChunks.add(ChunkPos.asLong(x, z));
                            }
                        }
                    }
                    new MapTemplatePlacer(template).placeAt(world, entry.getValue());
                }
            } catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }

        public GameArena gameArena() {
            return gameArena;
        }

        public String templateId() {
            return templateId;
        }

        public String templateSha256() {
            return templateSha256;
        }

        public boolean entitiesReady() {
            if (closed) {
                return false;
            }
            for (BlockBounds target : bounds.values()) {
                var chunks = target.asChunks().iterator();
                while (chunks.hasNext()) {
                    if (!world.areEntitiesActuallyLoadedAndTicking(new ChunkPos(chunks.nextLong()))) {
                        return false;
                    }
                }
            }
            return true;
        }

        public BlockBounds bounds(TeamId teamId) {
            BlockBounds result = bounds.get(teamId);
            if (result == null) {
                throw new IllegalArgumentException("Replay arena contains only RED and BLUE");
            }
            return result;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (BlockBounds target : bounds.values()) {
                world.getEntitiesOfClass(Entity.class, box(target), entity -> !(entity instanceof ServerPlayer))
                        .forEach(Entity::discard);
                for (BlockPos position : target) {
                    if (!world.getBlockState(position).isAir()) {
                        world.setBlock(position, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
            for (long packed : forcedChunks) {
                ChunkPos chunk = new ChunkPos(packed);
                world.setChunkForced(chunk.x, chunk.z, false);
            }
            forcedChunks.clear();
        }
    }

    private static void requireEmpty(ServerLevel world, BlockBounds bounds) {
        for (BlockPos position : bounds) {
            if (!world.getBlockState(position).isAir()) {
                throw new IllegalStateException("Replay arena would overwrite a block at " + position);
            }
        }
        if (!world.getEntitiesOfClass(Entity.class, box(bounds)).isEmpty()) {
            throw new IllegalStateException("Replay arena would overlap an existing entity");
        }
    }

    private static AABB box(BlockBounds bounds) {
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    private static ArenaLayout translate(ArenaLayout layout, BlockPos offset) {
        Map<Integer, LaneRegionLayout> lanes = new HashMap<>();
        layout.lanes().forEach((id, lane) -> lanes.put(id, translate(lane, offset)));
        return new ArenaLayout(translate(layout.teamSpawn(), offset), translate(layout.bossSpawn(), offset), lanes);
    }

    private static LaneRegionLayout translate(LaneRegionLayout lane, BlockPos offset) {
        List<GridPosition> slots = lane.finalDefenseTowerSlots().stream()
                .map(slot -> new GridPosition(slot.x() + offset.getX(), slot.y() + offset.getY(),
                        slot.z() + offset.getZ()))
                .toList();
        return new LaneRegionLayout(lane.laneId(), translate(lane.spawn(), offset),
                lane.spawnArea().offset(offset), lane.waypoints().stream().map(point -> translate(point, offset)).toList(),
                translate(lane.bossPosition(), offset), lane.laneArea().offset(offset), slots, lane.personalWaypointCount());
    }

    private static Vec3 translate(Vec3 point, BlockPos offset) {
        return point.add(offset.getX(), offset.getY(), offset.getZ());
    }
}
