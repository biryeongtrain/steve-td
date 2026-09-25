package kim.biryeong.semiontd.augment;

import java.util.List;

/** Immutable match configuration and committed selections; never a UI draft. */
public record AugmentSnapshot(AugmentConfig config, List<PlayerAugmentState.Selection> selections) {
    private static final AugmentSnapshot NONE = new AugmentSnapshot(AugmentConfig.defaults(), List.of());
    public AugmentSnapshot {
        config = config == null ? AugmentConfig.defaults() : config;
        selections = List.copyOf(selections);
    }

    public static AugmentSnapshot none() {
        return NONE;
    }

    public boolean has(String cardId) {
        String id = AugmentCatalog.normalizeId(cardId);
        return selections.stream().anyMatch(selection -> selection.outcome() == PlayerAugmentState.Outcome.SELECTED
                && AugmentCatalog.matchesSelection(id, selection.augmentId()));
    }

    public AugmentChoice choice(String cardId) {
        String id = AugmentCatalog.normalizeId(cardId);
        return selections.stream().filter(selection -> AugmentCatalog.matchesSelection(id, selection.augmentId()))
                .map(selection -> {
                    AugmentChoice choice = selection.choice();
                    String fixed = AugmentCatalog.fixedMode(selection.augmentId());
                    return fixed.isEmpty() ? choice : new AugmentChoice(choice.primaryTargetId(), choice.secondaryTargetId(), fixed);
                }).findFirst().orElseGet(AugmentChoice::none);
    }

    public double parameter(String cardId, String key, double fallback) {
        return config.parameter(cardId, key, fallback);
    }
}
