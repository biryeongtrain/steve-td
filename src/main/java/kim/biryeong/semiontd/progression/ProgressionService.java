package kim.biryeong.semiontd.progression;

import java.nio.file.Path;
import java.util.List;
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

    public enum CosmeticUpdateResult {
        SUCCESS,
        ALREADY_OWNED,
        INSUFFICIENT_FUNDS,
        NOT_OWNED,
        PERSISTENCE_FAILED
    }

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

    public SemionPlayerProfile saveSelectedSkybox(MinecraftServer server, UUID playerId, String playerName, String skyboxId) {
        SemionPlayerProfile updated = store.getOrCreateProfile(playerId, playerName)
                .updateSelectedSkybox(playerName, skyboxId);
        return store.putProfile(playerId, updated);
    }

    public SemionPlayerProfile saveTipsEnabled(MinecraftServer server, UUID playerId, String playerName, boolean enabled) {
        SemionPlayerProfile updated = store.getOrCreateProfile(playerId, playerName)
                .updateTipsEnabled(playerName, enabled);
        return store.putProfile(playerId, updated);
    }

    public SemionPlayerProfile rememberRecentBuildCode(MinecraftServer server, UUID playerId, String playerName, String code) {
        SemionPlayerProfile updated = store.getOrCreateProfile(playerId, playerName)
                .rememberRecentBuildCode(playerName, code);
        return store.putProfile(playerId, updated);
    }

    public synchronized Optional<SemionPlayerProfile> grantCosmeticCurrency(
            UUID playerId,
            String playerName,
            long amount
    ) {
        if (playerId == null || amount <= 0) {
            return Optional.empty();
        }
        SemionPlayerProfile updated;
        try {
            updated = store.getOrCreateProfile(playerId, playerName).grantCosmeticCurrency(playerName, amount);
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
        return store.putProfilePersisted(playerId, updated) ? Optional.of(updated) : Optional.empty();
    }

    public synchronized CosmeticUpdateResult purchaseCosmetic(UUID playerId, String playerName, String cosmeticId, long price) {
        SemionPlayerProfile current = store.getOrCreateProfile(playerId, playerName);
        if (current.ownsCosmetic(cosmeticId)) {
            return CosmeticUpdateResult.ALREADY_OWNED;
        }
        if (current.cosmeticCurrency() < price) {
            return CosmeticUpdateResult.INSUFFICIENT_FUNDS;
        }
        SemionPlayerProfile updated = current.purchaseCosmetic(playerName, cosmeticId, price);
        return store.putProfilePersisted(playerId, updated)
                ? CosmeticUpdateResult.SUCCESS
                : CosmeticUpdateResult.PERSISTENCE_FAILED;
    }

    public synchronized CosmeticUpdateResult selectCosmetics(UUID playerId, String playerName, List<String> cosmeticIds) {
        SemionPlayerProfile current = store.getOrCreateProfile(playerId, playerName);
        List<String> normalized = cosmeticIds == null
                ? List.of()
                : cosmeticIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (normalized.stream().anyMatch(id -> !current.ownsCosmetic(id))) {
            return CosmeticUpdateResult.NOT_OWNED;
        }
        SemionPlayerProfile updated = current.updateSelectedCosmetics(playerName, normalized);
        if (updated.equals(current)) {
            return CosmeticUpdateResult.SUCCESS;
        }
        return store.putProfilePersisted(playerId, updated)
                ? CosmeticUpdateResult.SUCCESS
                : CosmeticUpdateResult.PERSISTENCE_FAILED;
    }

    public synchronized boolean clearSelectedCosmetic(String cosmeticId) {
        return store.clearSelectedCosmetic(cosmeticId);
    }

    public synchronized boolean saveHeroCompanionSkin(
            UUID playerId,
            String playerName,
            String roleId,
            HeroCompanionSkinPreference skin
    ) {
        if (playerId == null || roleId == null || roleId.isBlank() || skin != null && !skin.valid()) {
            return false;
        }
        SemionPlayerProfile current = store.getOrCreateProfile(playerId, playerName);
        SemionPlayerProfile updated = current.updateHeroCompanionSkin(playerName, roleId, skin);
        return updated.equals(current) || store.putProfilePersisted(playerId, updated);
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
