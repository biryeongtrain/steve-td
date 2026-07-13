package kim.biryeong.semiontd.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record SemionPlayerProfile(
        String lastKnownName,
        int gamesPlayed,
        int wins,
        int losses,
        long cosmeticCurrency,
        String selectedJobId,
        String selectedSkyboxId,
        Boolean tipsEnabled,
        List<String> recentBuildCodes
) {
    public static final Codec<SemionPlayerProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("lastKnownName", "").forGetter(SemionPlayerProfile::lastKnownName),
            Codec.INT.optionalFieldOf("gamesPlayed", 0).forGetter(SemionPlayerProfile::gamesPlayed),
            Codec.INT.optionalFieldOf("wins", 0).forGetter(SemionPlayerProfile::wins),
            Codec.INT.optionalFieldOf("losses", 0).forGetter(SemionPlayerProfile::losses),
            Codec.LONG.optionalFieldOf("cosmeticCurrency", 0L).forGetter(SemionPlayerProfile::cosmeticCurrency),
            Codec.STRING.optionalFieldOf("selectedJobId", "").forGetter(SemionPlayerProfile::selectedJobId),
            Codec.STRING.optionalFieldOf("selectedSkyboxId", "").forGetter(SemionPlayerProfile::selectedSkyboxId),
            Codec.BOOL.optionalFieldOf("tipsEnabled", true).forGetter(SemionPlayerProfile::tipsEnabled),
            Codec.STRING.listOf().optionalFieldOf("recentBuildCodes", List.of()).forGetter(SemionPlayerProfile::recentBuildCodes)
    ).apply(instance, SemionPlayerProfile::new));

    public SemionPlayerProfile {
        if (lastKnownName == null) {
            lastKnownName = "";
        }
        if (selectedJobId == null) {
            selectedJobId = "";
        }
        if (selectedSkyboxId == null) {
            selectedSkyboxId = "";
        }
        if (tipsEnabled == null) {
            tipsEnabled = true;
        }
        recentBuildCodes = recentBuildCodes == null ? List.of() : List.copyOf(recentBuildCodes);
        if (gamesPlayed < 0 || wins < 0 || losses < 0 || cosmeticCurrency < 0) {
            throw new IllegalArgumentException("Profile values cannot be negative.");
        }
    }

    public static SemionPlayerProfile fresh(String playerName) {
        return new SemionPlayerProfile(playerName == null ? "" : playerName, 0, 0, 0, 0, "", "", true, List.of());
    }

    public SemionPlayerProfile updateName(String playerName) {
        String normalized = playerName == null ? "" : playerName;
        if (normalized.equals(lastKnownName)) {
            return this;
        }
        return new SemionPlayerProfile(normalized, gamesPlayed, wins, losses, cosmeticCurrency, selectedJobId, selectedSkyboxId, tipsEnabled, recentBuildCodes);
    }

    public SemionPlayerProfile updateSelectedJob(String playerName, ResourceLocation jobId) {
        String normalized = playerName == null ? "" : playerName;
        String jobIdText = jobId == null ? "" : jobId.toString();
        if (normalized.equals(lastKnownName) && jobIdText.equals(selectedJobId)) {
            return this;
        }
        return new SemionPlayerProfile(normalized, gamesPlayed, wins, losses, cosmeticCurrency, jobIdText, selectedSkyboxId, tipsEnabled, recentBuildCodes);
    }

    public SemionPlayerProfile updateSelectedSkybox(String playerName, String skyboxId) {
        String normalized = playerName == null ? "" : playerName;
        String skyboxIdText = skyboxId == null ? "" : skyboxId;
        if (normalized.equals(lastKnownName) && skyboxIdText.equals(selectedSkyboxId)) {
            return this;
        }
        return new SemionPlayerProfile(normalized, gamesPlayed, wins, losses, cosmeticCurrency, selectedJobId, skyboxIdText, tipsEnabled, recentBuildCodes);
    }

    public SemionPlayerProfile updateTipsEnabled(String playerName, boolean enabled) {
        String normalized = playerName == null ? "" : playerName;
        if (normalized.equals(lastKnownName) && tipsEnabled == enabled) {
            return this;
        }
        return new SemionPlayerProfile(normalized, gamesPlayed, wins, losses, cosmeticCurrency, selectedJobId, selectedSkyboxId, enabled, recentBuildCodes);
    }

    public Optional<ResourceLocation> selectedJobResource() {
        if (selectedJobId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(selectedJobId));
    }

    public SemionPlayerProfile recordMatch(String playerName, boolean winner, long reward) {
        String normalized = playerName == null ? "" : playerName;
        return new SemionPlayerProfile(
                normalized,
                gamesPlayed + 1,
                wins + (winner ? 1 : 0),
                losses + (winner ? 0 : 1),
                cosmeticCurrency + Math.max(0L, reward),
                selectedJobId,
                selectedSkyboxId,
                tipsEnabled,
                recentBuildCodes
        );
    }

    public SemionPlayerProfile rememberRecentBuildCode(String playerName, String code) {
        String normalized = playerName == null ? "" : playerName;
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedCode.isBlank()) {
            return updateName(normalized);
        }
        ArrayList<String> updated = new ArrayList<>();
        updated.add(normalizedCode);
        for (String existing : recentBuildCodes) {
            String existingCode = existing == null ? "" : existing.trim().toUpperCase(java.util.Locale.ROOT);
            if (!existingCode.isBlank() && !existingCode.equals(normalizedCode) && updated.size() < 8) {
                updated.add(existingCode);
            }
        }
        return new SemionPlayerProfile(normalized, gamesPlayed, wins, losses, cosmeticCurrency, selectedJobId, selectedSkyboxId, tipsEnabled, updated);
    }
}
