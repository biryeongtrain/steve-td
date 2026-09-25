package kim.biryeong.semiontd.game;

import java.util.Set;

/** The final decision at one milestone; absence in historical data means not recorded. */
public record AugmentSelectionSnapshot(
        int milestoneRound,
        String rarity,
        String augmentId,
        String outcome,
        String skipReason
) {
    public AugmentSelectionSnapshot {
        if (milestoneRound != 5 && milestoneRound != 15 && milestoneRound != 25) {
            throw new IllegalArgumentException("Invalid augment milestone: " + milestoneRound);
        }
        if (rarity == null || !Set.of("SILVER", "GOLD", "PRISMATIC").contains(rarity)) {
            throw new IllegalArgumentException("Invalid augment rarity: " + rarity);
        }
        if ("SELECTED".equals(outcome)) {
            if (augmentId == null || augmentId.isBlank() || skipReason != null) {
                throw new IllegalArgumentException("Selected augment requires an ID and no skip reason");
            }
        } else if ("SKIPPED".equals(outcome)) {
            if (augmentId != null || !("EXPLICIT".equals(skipReason) || "TIMEOUT".equals(skipReason))) {
                throw new IllegalArgumentException("Skipped augment requires only a skip reason");
            }
        } else {
            throw new IllegalArgumentException("Invalid augment outcome: " + outcome);
        }
    }
}
