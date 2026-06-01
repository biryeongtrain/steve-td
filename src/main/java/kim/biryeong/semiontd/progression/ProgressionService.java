package kim.biryeong.semiontd.progression;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.ProgressionConfig;
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
        if (appliedMatchRepository.hasApplied(matchResult.matchId(), PROGRESSION_SUBSYSTEM)) {
            return Map.of();
        }

        Optional<Map<UUID, MatchProgressionReward>> rewards = store.recordMatch(matchResult, progressionConfig);
        if (rewards.isEmpty()) {
            return Map.of();
        }
        if (!appliedMatchRepository.markApplied(matchResult.matchId(), PROGRESSION_SUBSYSTEM, System.currentTimeMillis())) {
            SemionTd.LOGGER.warn("Progression was persisted but applied marker already existed for match {}.", matchResult.matchId());
            return Map.of();
        }
        return rewards.get();
    }
}
