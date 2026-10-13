package kim.biryeong.semiontd.command;

import java.nio.file.Files;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.VillagerTowerJob;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

public final class CommandJobAvailabilityTest {
    @GameTest
    public void shortJobCommandsPersistBothStatesAndSuggestShortIds(GameTestHelper context) throws Exception {
        var server = context.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var op = server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(2)));
        var job = JobRegistry.find(VillagerTowerJob.ID).orElseThrow();
        boolean original = JobRegistry.isEnabled(job);
        var file = FabricLoader.getInstance().getConfigDir().resolve("semion-td/jobs.json");
        try {
            context.assertValueEqual(1, dispatcher.execute("semiontd job disable villager_towers", op), "Short disable command must execute");
            context.assertTrue(!JobRegistry.isEnabled(job), "Disable command must change availability");
            var saved = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("disabledJobs");
            context.assertTrue(saved.asList().stream().anyMatch(value -> value.getAsString().equals(job.id().toString())), "Persistent IDs must retain namespace");
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("semiontd job enable ", op)).join().getList();
            context.assertTrue(suggestions.stream().anyMatch(value -> value.getText().equals("villager_towers")), "Completion must offer short ID");
            context.assertTrue(suggestions.stream().noneMatch(value -> value.getText().contains(":")), "Completion must omit namespace");
            context.assertValueEqual(1, dispatcher.execute("semiontd job enable villager_towers", op), "Short enable command must execute");
            context.assertTrue(JobRegistry.isEnabled(job), "Enable command must change availability");
            saved = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("disabledJobs");
            context.assertTrue(saved.asList().stream().noneMatch(value -> value.getAsString().equals(job.id().toString())), "Enable command must persist");
        } finally {
            dispatcher.execute("semiontd job " + (original ? "enable " : "disable ") + "villager_towers", op);
        }
        context.succeed();
    }

    @GameTest
    public void enableAndDisableKeepPermissionLevelTwo(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var job = dispatcher.getRoot().getChild("semiontd").getChild("job");
        for (String action : java.util.List.of("enable", "disable")) {
            for (int level : java.util.List.of(0, 1, 2)) {
                var source = server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
                context.assertValueEqual(level >= 2, job.getChild(action).canUse(source), "Job switch must require permission level two");
                var parsed = dispatcher.parse("semiontd job " + action + " villager_towers", source);
                context.assertValueEqual(level < 2, parsed.getReader().canRead(), "Only operators may parse full switch command");
            }
        }
        context.succeed();
    }
}
