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
                && id.equals(selection.augmentId()));
    }

    public AugmentChoice choice(String cardId) {
        String id = AugmentCatalog.normalizeId(cardId);
        return selections.stream().filter(selection -> id.equals(selection.augmentId()))
                .map(PlayerAugmentState.Selection::choice).findFirst().orElseGet(AugmentChoice::none);
    }

    public double parameter(String cardId, String key, double fallback) {
        return config.parameter(cardId, key, fallback);
    }
}
