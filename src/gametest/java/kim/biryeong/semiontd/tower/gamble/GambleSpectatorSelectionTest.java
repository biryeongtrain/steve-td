package kim.biryeong.semiontd.tower.gamble;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class GambleSpectatorSelectionTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void spectatorOrderingRetainsScoreDistanceCoordinatesAndFirstIdentity(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        UUID owner = owner("ordering");
        PlayerLane lane = lane(context, owner);
        GambleSupportTower spectator = spectator(context, owner);
        GamblerTower xLater = gambler(context, owner, 4, 2, 3);
        GamblerTower yLater = gambler(context, owner, 3, 3, 3);
        GamblerTower zLater = gambler(context, owner, 3, 2, 4);
        GamblerTower first = gambler(context, owner, 3, 2, 3);
        GamblerTower tied = gambler(context, owner, 3, 2, 3);
        GamblerTower nearer = gambler(context, owner, 9, 2, 3);
        GamblerTower strongest = gambler(context, owner, 10, 2, 3);
        try {
            lane.addTower(spectator);
            SemionTowerEntity source = entity(lane, spectator);
            List<GamblerTower> candidates = List.of(xLater, yLater, zLater, first, tied, nearer, strongest);
            for (GamblerTower candidate : candidates) {
                lane.addTower(candidate);
                entity(lane, candidate).setPos(source.position().add(2.0, 0.0, 0.0));
            }
            entity(lane, nearer).setPos(source.position().add(1.0, 0.0, 0.0));
            entity(lane, strongest).setPos(source.position().add(3.0, 0.0, 0.0));
            strongest.setData(GamblerTower.STATE,
                    GambleState.EMPTY.recordStat(GambleStat.DAMAGE, 1, 10, 10, "selection"));
            requireSelected(context, lane, owner, source, strongest, "Score must take precedence over distance.");
            strongest.setData(GamblerTower.STATE, GambleState.EMPTY);
            requireSelected(context, lane, owner, source, nearer, "Current distance must break equal scores.");
            entity(lane, nearer).setPos(source.position().add(2.0, 0.0, 0.0));
            requireSelected(context, lane, owner, source, first,
                    "Equal scores and distances must use original x/y/z, then the first identity.");
            entity(lane, first).discard();
            requireSelected(context, lane, owner, source, tied, "A removed entity must lose its assignment.");
            lane.removeTower(tied);
            requireSelected(context, lane, owner, source, zLater, "Removal must expose the next z coordinate.");
            lane.removeTower(zLater);
            requireSelected(context, lane, owner, source, yLater, "The y coordinate must precede x-later towers.");
            lane.removeTower(yLater);
            requireSelected(context, lane, owner, source, xLater, "The x coordinate must break remaining ties.");
            context.succeed();
        } finally {
            close(lane, owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void spectatorSelectionKeepsBoundaryFiltersAndFreshLookups(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        UUID owner = owner("filters");
        PlayerLane lane = lane(context, owner);
        GambleSupportTower spectator = spectator(context, owner);
        Identifier sourceId = sourceId();
        try {
            lane.addTower(spectator);
            SemionTowerEntity source = entity(lane, spectator);
            context.assertTrue(GambleRoundEffects.assignSpectator(lane, owner, sourceId, source, 2.0).isEmpty(),
                    "A lane containing only the support tower must have no candidate.");
            context.assertTrue(GambleRoundEffects.assignSpectator(null, owner, sourceId, source, 2.0).isEmpty()
                            && GambleRoundEffects.assignSpectator(lane, null, sourceId, source, 2.0).isEmpty()
                            && GambleRoundEffects.assignSpectator(lane, owner, null, source, 2.0).isEmpty()
                            && GambleRoundEffects.assignSpectator(lane, owner, sourceId, null, 2.0).isEmpty()
                            && GambleRoundEffects.assignSpectator(lane, owner, sourceId, source, -1.0).isEmpty(),
                    "Invalid arguments must retain their empty results.");
            GamblerTower boundary = gambler(context, owner, 3, 2, 3);
            GamblerTower foreign = gambler(context, owner("foreign"), 4, 2, 3);
            GamblerTower outside = gambler(context, owner, 5, 2, 3);
            GamblerTower dead = gambler(context, owner, 6, 2, 3);
            for (GamblerTower candidate : List.of(boundary, foreign, outside, dead)) {
                lane.addTower(candidate);
                entity(lane, candidate).setPos(source.position().add(1.0, 0.0, 0.0));
            }
            entity(lane, boundary).setPos(source.position().add(2.0, 0.0, 0.0));
            entity(lane, outside).setPos(source.position().add(3.0, 0.0, 0.0));
            entity(lane, dead).setHealth(0.0f);
            context.assertTrue(GambleRoundEffects.assignSpectator(lane, owner, sourceId, source, 2.0)
                            .orElse(null) == boundary,
                    "The exact radius is inclusive; foreign, outside and dead towers are excluded.");
            context.assertTrue(GambleRoundEffects.assignSpectator(lane, owner, sourceId, source, Math.nextDown(2.0))
                            .isEmpty(),
                    "A smaller radius must exclude the previous target and release its link.");
            context.assertTrue(GambleRoundEffects.spectatorLinkCount(lane, owner, boundary.originalPosition()) == 0,
                    "An empty reassignment must release the previous link.");
            entity(lane, outside).setPos(source.position());
            context.assertTrue(GambleRoundEffects.assignSpectator(lane, owner, sourceId, source, 0.0)
                            .orElse(null) == outside,
                    "Each assignment must read moved entities, including the zero-radius boundary.");
            lane.removeTower(outside);
            context.assertTrue(GambleRoundEffects.assignSpectator(lane, owner, sourceId, source, 0.0).isEmpty(),
                    "A removed tower must not survive in a cached selection.");
            context.succeed();
        } finally {
            close(lane, owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    private static void requireSelected(GameTestHelper context, PlayerLane lane, UUID owner,
            SemionTowerEntity source, GamblerTower expected, String message) {
        context.assertTrue(GambleRoundEffects.assignSpectator(lane, owner, sourceId(), source, 20.0)
                .orElse(null) == expected, message);
    }

    private static GamblerTower gambler(GameTestHelper context, UUID owner, int x, int y, int z) {
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
        return new GamblerTower(TowerBalanceRuntime.resolve(GambleTowers.GAMBLER),
                owner, TeamId.RED, 1, position, position);
    }

    private static GambleSupportTower spectator(GameTestHelper context, UUID owner) {
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(7, 2, 7)));
        return new GambleSupportTower(TowerBalanceRuntime.resolve(GambleTowers.SPECTATOR_T3),
                owner, TeamId.RED, 1, position, position);
    }

    private static SemionTowerEntity entity(PlayerLane lane, Tower tower) {
        return GambleRoundEffects.towerEntity(tower, lane).orElseThrow();
    }

    private static PlayerLane lane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.atCenterOf(min),
                List.of(Vec3.atCenterOf(min)), Vec3.atCenterOf(max), BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))));
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static UUID owner(String name) {
        return UUID.nameUUIDFromBytes(("gamble-spectator-selection-" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static Identifier sourceId() {
        return Identifier.fromNamespaceAndPath("semion-td", "gamble/test/selection");
    }

    private static void close(PlayerLane lane, UUID owner) {
        GambleRoundEffects.clearAll(lane, owner);
        for (Tower tower : List.copyOf(lane.towers())) {
            lane.removeTower(tower);
        }
    }
}
