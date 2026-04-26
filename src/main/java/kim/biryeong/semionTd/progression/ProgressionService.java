package kim.biryeong.semionTd.progression;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semionTd.config.ProgressionConfig;
import kim.biryeong.semionTd.game.MatchParticipantResult;
import kim.biryeong.semionTd.game.MatchResult;
import net.minecraft.server.MinecraftServer;

public final class ProgressionService {
    private final ProgressionConfig progressionConfig;
    private final SemionProgressionStore store;

    public ProgressionService(ProgressionConfig progressionConfig, Path storePath) {
        this.progressionConfig = progressionConfig;
        this.store = new SemionProgressionStore(storePath);
    }

    public ProgressionConfig progressionConfig() {
        return progressionConfig;
    }

    public SemionPlayerProfile profile(MinecraftServer server, UUID playerId, String playerName) {
        return store.getOrCreateProfile(playerId, playerName);
    }

    public Map<UUID, MatchProgressionReward> applyMatchResult(MinecraftServer server, MatchResult matchResult) {
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