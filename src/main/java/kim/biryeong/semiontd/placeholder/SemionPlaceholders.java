package kim.biryeong.semiontd.placeholder;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SemionPlaceholders {
    public static final ResourceLocation SELECTED_JOB = id("selected_job");
    public static final ResourceLocation SELECTED_JOB_ID = id("selected_job_id");
    public static final ResourceLocation JOB = id("job");
    public static final ResourceLocation JOB_ID = id("job_id");

    private SemionPlaceholders() {
    }

    public static void register(SemionGameManager gameManager) {
        Placeholders.register(SELECTED_JOB, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).displayName());
        });
        Placeholders.register(SELECTED_JOB_ID, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).id().toString());
        });
        Placeholders.register(JOB, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(selectedJob(gameManager, context.player().getUUID()).displayName());
        });
        Placeholders.register(JOB_ID, (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(Component.literal(selectedJob(gameManager, context.player().getUUID()).id().toString()));
        });
    }

    private static SemionJob selectedJob(SemionGameManager gameManager, java.util.UUID playerId) {
        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null) {
            return JobRegistry.defaultJob();
        }
        SemionPlayer activePlayer = game.players().get(playerId);
        if (activePlayer != null) {
            return activePlayer.job().orElse(JobRegistry.defaultJob());
        }
        return game.selectedJobOrDefault(playerId);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
