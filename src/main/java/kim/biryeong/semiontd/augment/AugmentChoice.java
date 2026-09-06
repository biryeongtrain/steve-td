package kim.biryeong.semiontd.augment;

import java.util.UUID;

/** Logical tower IDs survive normal upgrades and entity reconstruction. */
public record AugmentChoice(UUID primaryTargetId, UUID secondaryTargetId, String mode) {
    public AugmentChoice {
        mode = mode == null ? "" : mode;
    }

    public static AugmentChoice none() {
        return new AugmentChoice(null, null, "");
    }
}
