package kim.biryeong.semiontd.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record SemionPlayerProfile(
        String lastKnownName,
        int gamesPlayed,
        int wins,
        int losses,
        long cosmeticCurrency,
        List<String> ownedCosmeticIds,
        String selectedCosmeticId,
        List<String> selectedCosmeticIds,
        String selectedJobId,
        String selectedSkyboxId,
        Boolean tipsEnabled,
        List<String> recentBuildCodes,
        Map<String, HeroCompanionSkinPreference> heroCompanionSkins
) {
    public static final Codec<SemionPlayerProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("lastKnownName", "").forGetter(SemionPlayerProfile::lastKnownName),
            Codec.INT.optionalFieldOf("gamesPlayed", 0).forGetter(SemionPlayerProfile::gamesPlayed),
            Codec.INT.optionalFieldOf("wins", 0).forGetter(SemionPlayerProfile::wins),
            Codec.INT.optionalFieldOf("losses", 0).forGetter(SemionPlayerProfile::losses),
            Codec.LONG.optionalFieldOf("cosmeticCurrency", 0L).forGetter(SemionPlayerProfile::cosmeticCurrency),
            Codec.STRING.listOf().optionalFieldOf("ownedCosmeticIds", List.of()).forGetter(SemionPlayerProfile::ownedCosmeticIds),
            Codec.STRING.optionalFieldOf("selectedCosmeticId", "").forGetter(SemionPlayerProfile::selectedCosmeticId),
            Codec.STRING.listOf().optionalFieldOf("selectedCosmeticIds", List.of()).forGetter(SemionPlayerProfile::selectedCosmeticIds),
            Codec.STRING.optionalFieldOf("selectedJobId", "").forGetter(SemionPlayerProfile::selectedJobId),
            Codec.STRING.optionalFieldOf("selectedSkyboxId", "").forGetter(SemionPlayerProfile::selectedSkyboxId),
            Codec.BOOL.optionalFieldOf("tipsEnabled", true).forGetter(SemionPlayerProfile::tipsEnabled),
            Codec.STRING.listOf().optionalFieldOf("recentBuildCodes", List.of()).forGetter(SemionPlayerProfile::recentBuildCodes),
            Codec.unboundedMap(Codec.STRING, HeroCompanionSkinPreference.CODEC)
                    .optionalFieldOf("heroCompanionSkins", Map.of())
                    .forGetter(SemionPlayerProfile::heroCompanionSkins)
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
        LinkedHashMap<String, HeroCompanionSkinPreference> normalizedSkins = new LinkedHashMap<>();
        if (heroCompanionSkins != null) {
            heroCompanionSkins.forEach((roleId, skin) -> {
                if (roleId != null && !roleId.isBlank() && skin != null && skin.valid()) {
                    normalizedSkins.put(roleId, skin);
                }
            });
        }
        heroCompanionSkins = Map.copyOf(normalizedSkins);
        LinkedHashSet<String> normalizedCosmetics = new LinkedHashSet<>();
        if (ownedCosmeticIds != null) {
            for (String id : ownedCosmeticIds) {
                if (id != null && !id.isBlank()) {
                    normalizedCosmetics.add(id);
                }
            }
        }
        ownedCosmeticIds = List.copyOf(normalizedCosmetics);
        LinkedHashSet<String> normalizedSelections = new LinkedHashSet<>();
        if (selectedCosmeticIds != null) {
            for (String id : selectedCosmeticIds) {
                if (id != null && !id.isBlank()) {
                    normalizedSelections.add(id);
                }
            }
        }
        if (normalizedSelections.isEmpty() && selectedCosmeticId != null && !selectedCosmeticId.isBlank()) {
            normalizedSelections.add(selectedCosmeticId);
        }
        selectedCosmeticIds = List.copyOf(normalizedSelections);
        selectedCosmeticId = selectedCosmeticIds.isEmpty() ? "" : selectedCosmeticIds.getFirst();
        if (gamesPlayed < 0 || wins < 0 || losses < 0 || cosmeticCurrency < 0) {
            throw new IllegalArgumentException("Profile values cannot be negative.");
        }
    }

    public static SemionPlayerProfile fresh(String playerName) {
        return new SemionPlayerProfile(playerName == null ? "" : playerName, 0, 0, 0, 0,
                List.of(), "", List.of(), "", "", true, List.of(), Map.of());
    }

    public SemionPlayerProfile updateName(String playerName) {
        String normalized = playerName == null ? "" : playerName;
        if (normalized.equals(lastKnownName)) {
            return this;
        }
        return copy(normalized, cosmeticCurrency, ownedCosmeticIds, selectedCosmeticIds, selectedJobId, selectedSkyboxId, tipsEnabled, recentBuildCodes);
    }

    public SemionPlayerProfile updateSelectedJob(String playerName, ResourceLocation jobId) {
        String normalized = playerName == null ? "" : playerName;
        String jobIdText = jobId == null ? "" : jobId.toString();
        if (normalized.equals(lastKnownName) && jobIdText.equals(selectedJobId)) {
            return this;
        }
        return copy(normalized, cosmeticCurrency, ownedCosmeticIds, selectedCosmeticIds, jobIdText, selectedSkyboxId, tipsEnabled, recentBuildCodes);
    }

    public SemionPlayerProfile updateSelectedSkybox(String playerName, String skyboxId) {
        String normalized = playerName == null ? "" : playerName;
        String skyboxIdText = skyboxId == null ? "" : skyboxId;
        if (normalized.equals(lastKnownName) && skyboxIdText.equals(selectedSkyboxId)) {
            return this;
        }
        return copy(normalized, cosmeticCurrency, ownedCosmeticIds, selectedCosmeticIds, selectedJobId, skyboxIdText, tipsEnabled, recentBuildCodes);
    }

    public SemionPlayerProfile updateTipsEnabled(String playerName, boolean enabled) {
        String normalized = playerName == null ? "" : playerName;
        if (normalized.equals(lastKnownName) && tipsEnabled == enabled) {
            return this;
        }
        return copy(normalized, cosmeticCurrency, ownedCosmeticIds, selectedCosmeticIds, selectedJobId, selectedSkyboxId, enabled, recentBuildCodes);
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
                ownedCosmeticIds,
                selectedCosmeticId,
                selectedCosmeticIds,
                selectedJobId,
                selectedSkyboxId,
                tipsEnabled,
                recentBuildCodes,
                heroCompanionSkins
        );
    }

    public SemionPlayerProfile grantCosmeticCurrency(String playerName, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Cosmetic currency grant must be positive.");
        }
        return copy(
                playerName == null ? "" : playerName,
                Math.addExact(cosmeticCurrency, amount),
                ownedCosmeticIds,
                selectedCosmeticIds,
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
        return copy(normalized, cosmeticCurrency, ownedCosmeticIds, selectedCosmeticIds, selectedJobId, selectedSkyboxId, tipsEnabled, updated);
    }

    public boolean ownsCosmetic(String cosmeticId) {
        return cosmeticId != null && ownedCosmeticIds.contains(cosmeticId);
    }

    public SemionPlayerProfile purchaseCosmetic(String playerName, String cosmeticId, long price) {
        if (cosmeticId == null || cosmeticId.isBlank() || price < 0 || ownsCosmetic(cosmeticId) || cosmeticCurrency < price) {
            return this;
        }
        ArrayList<String> updated = new ArrayList<>(ownedCosmeticIds);
        updated.add(cosmeticId);
        return copy(playerName == null ? "" : playerName, cosmeticCurrency - price, updated, selectedCosmeticIds, selectedJobId, selectedSkyboxId, tipsEnabled, recentBuildCodes);
    }

    public SemionPlayerProfile updateSelectedCosmetic(String playerName, String cosmeticId) {
        return updateSelectedCosmetics(playerName,
                cosmeticId == null || cosmeticId.isBlank() ? List.of() : List.of(cosmeticId));
    }

    public SemionPlayerProfile updateSelectedCosmetics(String playerName, List<String> cosmeticIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (cosmeticIds != null) {
            cosmeticIds.stream().filter(id -> id != null && !id.isBlank()).forEach(normalized::add);
        }
        if (!ownedCosmeticIds.containsAll(normalized)) {
            return this;
        }
        return copy(playerName == null ? "" : playerName, cosmeticCurrency, ownedCosmeticIds,
                List.copyOf(normalized), selectedJobId, selectedSkyboxId, tipsEnabled, recentBuildCodes);
    }

    public boolean isCosmeticSelected(String cosmeticId) {
        return cosmeticId != null && selectedCosmeticIds.contains(cosmeticId);
    }

    public SemionPlayerProfile updateHeroCompanionSkin(
            String playerName,
            String roleId,
            HeroCompanionSkinPreference skin
    ) {
        if (roleId == null || roleId.isBlank() || skin != null && !skin.valid()) {
            return this;
        }
        LinkedHashMap<String, HeroCompanionSkinPreference> updated = new LinkedHashMap<>(heroCompanionSkins);
        if (skin == null) {
            updated.remove(roleId);
        } else {
            updated.put(roleId, skin);
        }
        String normalizedName = playerName == null ? "" : playerName;
        if (normalizedName.equals(lastKnownName) && updated.equals(heroCompanionSkins)) {
            return this;
        }
        String legacySelection = selectedCosmeticIds.isEmpty() ? "" : selectedCosmeticIds.getFirst();
        return new SemionPlayerProfile(normalizedName, gamesPlayed, wins, losses, cosmeticCurrency,
                ownedCosmeticIds, legacySelection, selectedCosmeticIds, selectedJobId, selectedSkyboxId,
                tipsEnabled, recentBuildCodes, updated);
    }

    private SemionPlayerProfile copy(
            String playerName,
            long currency,
            List<String> cosmetics,
            List<String> selectedCosmetics,
            String job,
            String skybox,
            boolean tips,
            List<String> builds
    ) {
        String legacySelection = selectedCosmetics.isEmpty() ? "" : selectedCosmetics.getFirst();
        return new SemionPlayerProfile(playerName, gamesPlayed, wins, losses, currency, cosmetics,
                legacySelection, selectedCosmetics, job, skybox, tips, builds, heroCompanionSkins);
    }
}
