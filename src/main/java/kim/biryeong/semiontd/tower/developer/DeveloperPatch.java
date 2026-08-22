package kim.biryeong.semiontd.tower.developer;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The five stats a patch can raise.
 *
 * <p>Stored per tower and per kind so the diminishing curve can count how many of the same kind
 * already landed. Aggro is the odd one out: it is an integer on a {@code -100..100} scale, so it is
 * added flat while everything else multiplies.
 */
public enum DeveloperPatch {
    ATTACK("attack", "공격력", Items.IRON_SWORD),
    RANGE("range", "사거리", Items.SPYGLASS),
    FIRE_RATE("fire_rate", "연사", Items.SUGAR),
    HEALTH("health", "체력", Items.GOLDEN_APPLE),
    AGGRO("aggro", "어그로", Items.SHIELD);

    private final String key;
    private final String displayName;
    private final Item item;

    DeveloperPatch(String key, String displayName, Item item) {
        this.key = key;
        this.displayName = displayName;
        this.item = item;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Item item() {
        return item;
    }

    /** True when this patch adds a flat integer rather than a multiplier. */
    public boolean isFlat() {
        return this == AGGRO;
    }

    /**
     * Effect of the next patch of this kind, before the tower's own scale.
     *
     * <p>The nth patch contributes {@code base * diminishing^(n-1)}, so a tower that already holds
     * four attack patches gets noticeably less from the fifth. Aggro is exempt: it is a flat
     * integer on a bounded scale, and decaying it would make the last few points meaningless.
     *
     * @param existingCount patches of this kind already applied, including ones still pending
     */
    public double stepAmount(int existingCount) {
        if (isFlat()) {
            return DeveloperBalance.patchAggro();
        }
        int count = Math.max(0, existingCount);
        return baseAmount() * Math.pow(DeveloperBalance.patchDiminishing(), count);
    }

    private double baseAmount() {
        return switch (this) {
            case ATTACK -> DeveloperBalance.patchAttack();
            case RANGE -> DeveloperBalance.patchRange();
            case FIRE_RATE -> DeveloperBalance.patchInterval();
            case HEALTH -> DeveloperBalance.patchHealth();
            case AGGRO -> DeveloperBalance.patchAggro();
        };
    }

    public static Optional<DeveloperPatch> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (DeveloperPatch patch : values()) {
            if (patch.key.equals(normalized)) {
                return Optional.of(patch);
            }
        }
        return Optional.empty();
    }
}
