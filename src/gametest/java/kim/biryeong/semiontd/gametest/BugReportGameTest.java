package kim.biryeong.semiontd.gametest;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.game.*;
import kim.biryeong.semiontd.report.*;
import kim.biryeong.semiontd.persistence.SQLiteBugReportRepository;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.animal.PigTower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

public final class BugReportGameTest {
    @GameTest
    public void snapshotIncludesBothTeamsCopiesAndBuffsWithoutMutatingCombat(GameTestHelper context) {
        UUID red = UUID.randomUUID(), blue = UUID.randomUUID();
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        try {
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL,
                    List.of(new AssignedParticipant(red, "red", TeamId.RED, 1), new AssignedParticipant(blue, "blue", TeamId.BLUE, 1)), Set.of(), 2)), "game starts");
            var lane = game.playerLane(red).orElseThrow();
            BlockPos pos = context.absolutePos(new BlockPos(2, 2, 2));
            PigTower tower = new PigTower(AnimalTowers.T1_PIG_TOWER, red, TeamId.RED, 1, new GridPosition(pos.getX(), pos.getY(), pos.getZ()));
            lane.addTower(tower);
            var other = game.playerLane(blue).orElseThrow();
            PigTower copy = new PigTower(AnimalTowers.T1_PIG_TOWER, blue, TeamId.BLUE, 1, tower.position());
            copy.markTemporaryCopy(tower.logicalId());
            other.addTower(copy);
            var entity = tower.runtimeEntity(lane).orElseThrow();
            entity.setNoAi(true);
            tower.addPermanentFlatDamageBonus(7, lane);
            tower.setData(TowerDataKey.of(ResourceLocation.fromNamespaceAndPath("test", "stacks"), Integer.class), 5);
            tower.setData(TowerDataKey.of(ResourceLocation.fromNamespaceAndPath("test", "complex"), Map.class), Map.of("private", entity));
            entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, 0.5, 80);
            tower.syncHealth(23); tower.onStateChanged(lane);
            var beforeEffects = entity.effectSnapshot();
            var snapshot = BugReportSnapshot.capture(game);
            require(snapshot.getAsJsonArray("players").size() == 2, "both players captured");
            require(snapshot.getAsJsonArray("towers").size() == 2, "both teams captured including temporary copy");
            var captured = java.util.stream.StreamSupport.stream(snapshot.getAsJsonArray("towers").spliterator(), false)
                    .map(value -> value.getAsJsonObject()).filter(value -> value.get("logicalId").getAsString().equals(tower.logicalId().toString())).findFirst().orElseThrow();
            require(captured.get("health").getAsDouble() == 23, "entity health captured");
            require(captured.get("attack").getAsDouble() > tower.type().damage(), "buffed attack captured");
            require(captured.getAsJsonArray("effects").size() > 0, "buff contributions captured");
            require(captured.getAsJsonObject("state").getAsJsonObject("values").get("test:stacks").getAsInt() == 5, "scalar growth captured");
            require(captured.getAsJsonObject("state").getAsJsonArray("omittedKeys").size() > 0, "complex runtime values explicitly omitted");
            require(captured.getAsJsonArray("captureErrors").isEmpty(), "capture complete for supported fields");
            require(beforeEffects.equals(entity.effectSnapshot()) && entity.getHealth() == 23, "capture does not consume buffs or heal");
            entity.setHealth(1);
            require(captured.get("health").getAsDouble() == 23, "snapshot detached from live entity");
            require(!BugReportSnapshot.capture(null).get("hasGame").getAsBoolean(), "lobby is explicit");
            context.succeed();
        } finally { game.close(); }
    }

    @GameTest
    public void commandStoresReportOnceAndRestrictsListsToOperators(GameTestHelper context) throws Exception {
        var path = Files.createTempDirectory("semion-bug-report-test-").resolve("reports.db");
        var manager = new SemionGameManager();
        var service = new BugReportCommands(path, manager);
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        service.register(dispatcher);
        var player = context.makeMockServerPlayerInLevel();
        var source = player.createCommandSourceStack().withPermission(0);
        require(dispatcher.getRoot().getChild("버그신고").canUse(source), "players can report");
        require(!dispatcher.getRoot().getChild("버그신고목록").canUse(source), "players cannot read private reports");
        require(!dispatcher.getRoot().getChild("버그신고조회").canUse(source), "players cannot read report details");
        require(dispatcher.getRoot().getChild("버그신고목록").canUse(source.withPermission(2)), "operators can list");
        require(dispatcher.execute("버그신고 공격 버프가 이상합니다", source) == 1, "report queued");
        require(dispatcher.execute("버그신고 중복 신고", source) == 0, "cooldown prevents duplicate capture");
        // Closing drains pending writes. The acknowledgement callback is processed by the server afterwards.
        service.close();
        var repository = new SQLiteBugReportRepository(path);
        var reports = repository.list(1);
        require(reports.size() == 1, "one persisted report");
        var report = repository.find(reports.getFirst().id()).orElseThrow();
        require(report.playerId().equals(player.getUUID()) && report.playerName().equals(player.getGameProfile().getName()), "identity is server-derived");
        require(report.content().equals("공격 버프가 이상합니다"), "report text preserved");
        require(report.snapshot().has("reporterDimension"), "reporter context captured outside a game");
        require(dispatcher.execute("버그신고목록", context.getLevel().getServer().createCommandSourceStack()) == 0, "closed storage rejects new work");
        context.succeed();
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
