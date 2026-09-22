package kim.biryeong.semiontd.augment;

import java.util.Set;

public record AugmentDefinition(
        String id, String displayName, AugmentRarity rarity, AugmentCategory category,
        String familyKey, boolean safe, boolean risky, boolean towerAugment, boolean reserve,
        Set<Integer> milestoneRounds, Set<String> conflicts, String description, String requiredJobId
) {
    public AugmentDefinition(String id, String displayName, AugmentRarity rarity, AugmentCategory category,
                             String familyKey, boolean safe, boolean risky, boolean towerAugment, boolean reserve,
                             Set<Integer> milestoneRounds, Set<String> conflicts, String description) {
        this(id, displayName, rarity, category, familyKey, safe, risky, towerAugment, reserve,
                milestoneRounds, conflicts, description, null);
    }

    public AugmentDefinition {
        id = AugmentCatalog.normalizeId(id);
        milestoneRounds = Set.copyOf(milestoneRounds);
        conflicts = conflicts.stream().map(AugmentCatalog::normalizeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
