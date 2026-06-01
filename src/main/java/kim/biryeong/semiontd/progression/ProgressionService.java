package kim.biryeong.semiontd.progression;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.persistence.AppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileAppliedMatchRepository;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class ProgressionService {
    private static final String PROGRESSION_SUBSYSTEM = "progression";

    private final ProgressionConfig progressionConfig;
    private final SemionProgressionStore store;
    private final AppliedMatchRepository appliedMatchRepository;

    public ProgressionService(ProgressionConfig progressionConfig, Path storePath) {
        this(
                progressionConfig,
                storePath,
                new FileAppliedMatchRepository(storePath == null
                        ? null
                        : storePath.resolveSibling("progression-applied-matches.json"))
        );
    }

    public ProgressionService(
            ProgressionConfig progressionConfig,
            Path storePath,
            AppliedMatchRepository appliedMatchRepository
    ) {
        this.progressionConfig = progressionConfig;
        this.store = new SemionProgressionStore(storePath);
        this.appliedMatchRepository = appliedMatchRepository;
    }

    public ProgressionConfig progressionConfig() {
        return progressionConfig;
    }

    public SemionPlayerProfile profile(MinecraftServer server, UUID playerId, String playerName) {
        return store.getOrCreateProfile(playerId, playerName);
    }

    public SemionPlayerProfile saveSelectedJob(MinecraftServer server, UUID playerId, String playerName, ResourceLocation jobId) {
        SemionPlayerProfile updated = store.getOrCreateProfile(playerId, playerName)
                .updateSelectedJob(playerName, jobId);
        return store.putProfile(playerId, updated);
    }

    public SemionPlayerProfile rememberRecentBuildCode(MinecraftServer server, UUID playerId, String playerName, String code) {
        SemionPlayerProfile updated = store.getOrCreateProfile(playerId, playerName)
                .rememberRecentBuildCode(playerName, code);
        return store.putProfile(playerId, updated);
    }

    public synchronized Map<UUID, MatchProgressionReward> applyMatchResult(MinecraftServer server, MatchResult matchResult) {
        if (!appliedMatchRepository.markApplied(matchResult.matchId(), PROGRESSION_SUBSYSTEM, System.currentTimeMillis())) {
            return Map.of();
        }

        Map<UUID, MatchProgressionReward> rewards = new LinkedHashMap<>();
        for (MatchParticipantResult participant : matchResult.participants()) {
            long reward = participant.winner()
                    ? progressionConfig.rewardForWin()
                    : progressionConfig.rewardForLoss();
            SemionPlayerProfile updated = store.getOrCreateProfile(participant.playerId(), participant.playerName())
                    .recordMatch(participant.playerName(), participant.winner(), reward);
            store.putProfile(participant.playerId(), updated);
            rewards.put(participant.playerId(), new MatchProgressionReward(participant.winner(), reward, updated));
        }
        return Map.copyOf(rewards);
    }
}
