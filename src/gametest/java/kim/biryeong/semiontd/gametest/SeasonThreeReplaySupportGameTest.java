package kim.biryeong.semiontd.gametest;

import static kim.biryeong.semiontd.gametest.SeasonThreeBuilderBalanceGameTest.require;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.animal.AnimalTowerCatalogs;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Fast, always-on regressions for the opt-in recorded-history runners. */
public final class SeasonThreeReplaySupportGameTest {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final GridPosition RELATIVE = new GridPosition(3, 0, 2);

    @GameTest(maxTicks = 1)
    public void configurationHashesIgnoreObjectOrderButPreserveValues(GameTestHelper context) {
        var first = JSON.fromJson("{\"z\":[{\"b\":2,\"a\":1}],\"a\":null}", JsonObject.class);
        var second = JSON.fromJson("{\"a\":null,\"z\":[{\"a\":1,\"b\":2}]}", JsonObject.class);
        require(!SeasonThreeReplaySupport.hash(first).equals(SeasonThreeReplaySupport.hash(second)),
                "The regression needs distinct raw JSON ordering.");
        require(SeasonThreeReplaySupport.canonicalHash(first).equals(SeasonThreeReplaySupport.canonicalHash(second)),
                "Object key ordering, including nested objects, must not split identical configurations.");
        second.getAsJsonArray("z").get(0).getAsJsonObject().addProperty("a", 3);
        require(!SeasonThreeReplaySupport.canonicalHash(first).equals(SeasonThreeReplaySupport.canonicalHash(second)),
                "A changed parameter must change the configuration hash.");
        require(!SeasonThreeReplaySupport.canonicalHash(List.of(1, 2))
                        .equals(SeasonThreeReplaySupport.canonicalHash(List.of(2, 1))),
                "Array order is meaningful and must remain unchanged.");
        context.succeed();
    }

    @GameTest
    public void parserPreservesOrderAndRejectsInvalidHistories(GameTestHelper context) {
        JsonObject valid = manifest();
        var sample = SeasonThreeReplaySupport.parseSamples(valid.toString()).getFirst();
        require(sample.actions().stream().map(BuildAction::subjectId).toList()
                .equals(List.of("t1_pig_tower", "t2_pig_tower")), "Same-round purchase order must be preserved.");
        require(sample.attemptedRounds().isEmpty() && sample.sourceFinalRound() == 40,
                "Missing participant history must not be filled with the match final round.");
        require(sample.sourceMap().isJsonNull(), "Unknown source map must remain unknown.");

        JsonObject invalid = manifest();
        invalid.addProperty("schemaVersion", 2);
        reject(invalid, "Unsupported schema version");
        invalid = manifest();
        invalid.getAsJsonArray("builders").add(invalid.getAsJsonArray("builders").get(0).deepCopy());
        reject(invalid, "Duplicate builders");
        invalid = manifest();
        records(invalid).add(records(invalid).get(0).deepCopy());
        reject(invalid, "Duplicate players in one builder");
        invalid = manifest();
        for (int index = 1; index < 4; index++) {
            JsonObject record = records(invalid).get(0).getAsJsonObject().deepCopy();
            record.addProperty("playerRef", "player-" + index);
            records(invalid).add(record);
        }
        reject(invalid, "More than three players");
        invalid = manifest();
        actions(invalid).get(0).getAsJsonObject().addProperty("round", 2);
        reject(invalid, "Reversed action rounds");
        invalid = manifest();
        actions(invalid).get(0).getAsJsonObject().addProperty("cost", -1);
        reject(invalid, "Negative cost must not be silently clamped by BuildAction");
        invalid = manifest();
        actions(invalid).get(0).getAsJsonObject().addProperty("subjectId", "");
        reject(invalid, "Missing purchase subject");
        context.succeed();
    }

    @GameTest
    public void replayPurchasesDoNotSpendOnFailuresAndCleanupCanRepeat(GameTestHelper context) {
        for (int iteration = 0; iteration < 2; iteration++) {
            try (Fixture fixture = new Fixture(context)) {
                var game = fixture.game;
                var player = fixture.player;
                var lane = fixture.lane;
                BuildAction place = BuildAction.towerPlaceRelative(1, "t1_pig_tower", RELATIVE, 1);
                long currentCost = ProductionTowerCatalog.find(place.subjectId()).orElseThrow().type().mineralCost();
                require(currentCost > 0, "This regression needs a paid tower.");
                player.economy().overrideStartingValues(currentCost - 1, 11, 7, 0);
                for (int retry = 0; retry < 2; retry++) {
                    unchanged(SeasonThreeReplaySupport.execute(game, player, place, 6), "NOT_ENOUGH_MINERAL");
                }
                require(lane.towers().isEmpty(), "Failed retries must not create a tower.");
                unchanged(SeasonThreeReplaySupport.execute(game, player,
                        BuildAction.towerPlaceRelative(1, "removed_legacy_tower_id", RELATIVE, 1), 6), "UNKNOWN_TOWER");

                player.economy().overrideStartingValues(currentCost + 100, 11, 7, 0);
                var bought = SeasonThreeReplaySupport.execute(game, player, place, 6);
                require(bought.success() && bought.diamondsDelta() == -currentCost
                                && bought.emeraldsDelta() == 0 && bought.incomeDelta() == 0,
                        "Replay must spend the current purchase price, not the historical cost field.");
                var tower = lane.towerAt(fixture.position);
                require(tower != null, "Lane-relative action must create the tower at the translated position.");
                unchanged(SeasonThreeReplaySupport.execute(game, player,
                        BuildAction.towerUpgradeRelative(1, "removed_legacy_upgrade_id", RELATIVE, 1), 6), "UNKNOWN_UPGRADE");
                unchanged(SeasonThreeReplaySupport.execute(game, player,
                        BuildAction.towerSellRelative(1, "t1_wolf_tower", RELATIVE, 1), 6), "SALE_TARGET_TYPE_MISMATCH");
                require(lane.removeTower(tower), "The target must actually disappear before retrying its action.");
                unchanged(SeasonThreeReplaySupport.execute(game, player,
                        BuildAction.towerUpgradeRelative(1, "t2_pig_tower", RELATIVE, 1), 6), "NO_TOWER_AT_POSITION");
                unchanged(SeasonThreeReplaySupport.execute(game, player,
                        BuildAction.towerSellRelative(1, "t1_pig_tower", RELATIVE, 1), 6), "NO_TOWER_AT_POSITION");

                player.economy().overrideStartingValues(currentCost, 11, 7, 0);
                require(SeasonThreeReplaySupport.execute(game, player, place, 6).success(), "Re-placement must be legal.");
                var entity = ((EntityBackedTower) lane.towerAt(fixture.position)).runtimeEntity(lane).orElseThrow();
                game.close();
                game.close();
                require(entity.isRemoved() && lane.towers().isEmpty() && lane.activeMonsters().isEmpty(),
                        "Repeated cleanup must remove runtime entities and lane membership.");
            }
        }
        context.succeed();
    }

    @GameTest
    public void positionsStayRelativeAndEndedGamesCannotPayOrReplay(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context)) {
            var game = fixture.game;
            var player = fixture.player;
            BlockPos min = fixture.lane.laneLayout().laneArea().min();
            BuildAction negativeY = BuildAction.towerPlaceRelative(1, "t1_pig_tower", new GridPosition(42, -1, 6), 1);
            require(SeasonThreeReplaySupport.position(fixture.lane, negativeY)
                    .equals(new GridPosition(min.getX() + 42, min.getY() - 1, min.getZ() + 6)),
                    "Relative coordinates must preserve negative height and full recorded horizontal offsets.");
            require(SeasonThreeReplaySupport.position(fixture.lane,
                    BuildAction.towerPlace(1, "t1_pig_tower", RELATIVE, 1)) == null,
                    "An unknown historical map must not turn absolute coordinates into local coordinates.");
            player.economy().overrideStartingValues(333, 44, 55, 2);
            require(game.killBoss(context.getLevel().getServer(), TeamId.BLUE), "The opponent must really be eliminated.");
            require(game.phase() == RoundPhase.ENDED, "Normal victory must end the fixture.");
            long diamonds = player.economy().diamond();
            long emeralds = player.economy().emerald();
            long income = player.economy().income();
            int round = game.currentRound();
            for (int tick = 0; tick < 100; tick++) {game.tick(context.getLevel().getServer());}
            require(player.economy().diamond() == diamonds && player.economy().emerald() == emeralds
                            && player.economy().income() == income && game.currentRound() == round,
                    "Ended games must not invent future payouts, production, or rounds.");
            unchanged(SeasonThreeReplaySupport.execute(game, player,
                    BuildAction.towerPlaceRelative(1, "t1_pig_tower", RELATIVE, 1), 6), "INVALID_PHASE");
            unchanged(SeasonThreeReplaySupport.execute(game, player,
                    BuildAction.emeraldProductionUpgrade(1, 1, 1, 1), 6), "INVALID_PHASE");
        }
        context.succeed();
    }

    private static void unchanged(SeasonThreeReplaySupport.ActionResult result, String status) {
        require(result.status().equals(status), "Expected " + status + ", got " + result.status());
        require(result.diamondsDelta() == 0 && result.emeraldsDelta() == 0 && result.incomeDelta() == 0,
                "Failed replay must leave every recorded currency unchanged: " + status);
    }

    private static JsonObject manifest() {
        JsonObject source = new JsonObject();
        source.addProperty("matchId", "recorded-match");
        source.addProperty("playerRef", "player-0");
        source.addProperty("catalogVersion", "historical-catalog");
        source.addProperty("sourceFinalRound", 40);
        source.add("attemptedRounds", JsonNull.INSTANCE);
        source.add("sourceTraits", JsonNull.INSTANCE);
        source.add("sourceMap", JsonNull.INSTANCE);
        source.add("actions", JSON.toJsonTree(List.of(
                BuildAction.towerPlaceRelative(1, "t1_pig_tower", RELATIVE, 40),
                BuildAction.towerUpgradeRelative(1, "t2_pig_tower", RELATIVE, 100))));
        JsonObject builder = new JsonObject();
        builder.addProperty("builderId", AnimalTowerJob.ID.toString());
        JsonArray records = new JsonArray();
        records.add(source);
        builder.add("records", records);
        JsonArray builders = new JsonArray();
        builders.add(builder);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("builders", builders);
        return root;
    }

    private static JsonArray records(JsonObject manifest) {
        return manifest.getAsJsonArray("builders").get(0).getAsJsonObject().getAsJsonArray("records");
    }

    private static JsonArray actions(JsonObject manifest) {
        return records(manifest).get(0).getAsJsonObject().getAsJsonArray("actions");
    }

    private static void reject(JsonObject manifest, String reason) {
        try {SeasonThreeReplaySupport.parseSamples(manifest.toString());}
        catch (IllegalArgumentException expected) {return;}
        throw new AssertionError("Parser accepted: " + reason);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper context;
        final SemionGame game;
        final SemionPlayer player;
        final PlayerLane lane;
        final GridPosition position;
        final BlockPos floor;
        final BlockState previousBlock;

        Fixture(GameTestHelper context) {
            this.context = context;
            AnimalTowerCatalogs.register();
            BlockPos origin = context.absolutePos(BlockPos.ZERO);
            game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(context.getLevel(), origin));
            UUID red = UUID.nameUUIDFromBytes(("replay-support-red-" + origin).getBytes(StandardCharsets.UTF_8));
            UUID blue = UUID.nameUUIDFromBytes(("replay-support-blue-" + origin).getBytes(StandardCharsets.UTF_8));
            require(game.selectJob(red, AnimalTowerJob.ID) && game.selectJob(blue, AnimalTowerJob.ID), "Animal fixture must be selectable.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL,
                    List.of(new AssignedParticipant(red, "ReplayRed", TeamId.RED, 1),
                            new AssignedParticipant(blue, "ReplayBlue", TeamId.BLUE, 1)), Set.of(), 2)), "Replay fixture must start.");
            player = game.players().get(red);
            lane = game.playerLane(red).orElseThrow();
            require(lane.towers().isEmpty(), "A repeated fixture must not inherit a prior board.");
            position = SeasonThreeReplaySupport.position(lane, BuildAction.towerPlaceRelative(1, "t1_pig_tower", RELATIVE, 1));
            floor = new BlockPos(position.x(), position.y(), position.z());
            previousBlock = context.getLevel().getBlockState(floor);
            context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        }

        @Override
        public void close() {
            try {game.close();}
            finally {context.getLevel().setBlock(floor, previousBlock, 3);}
        }
    }
}
