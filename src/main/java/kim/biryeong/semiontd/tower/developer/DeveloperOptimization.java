package kim.biryeong.semiontd.tower.developer;

import java.util.Locale;
import java.util.Optional;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Permanent trades: give up one capability, get a large gain elsewhere.
 *
 * <p>Only {@link DeveloperBalance#optimizationsPerMatch()} of these exist for a whole match, so
 * every one is a decision the player cannot walk back. Several can stack on one tower, which is
 * where the family's sharpest builds — and its worst mistakes — come from.
 *
 * <p>Values resolve through {@link TowerBalanceRuntime} under the {@code developer_global} config
 * id so they can be retuned without a rebuild.
 */
public enum DeveloperOptimization {
    /** Half the reach for a large attack-speed gain. Turns a tower into a tile-guard. */
    RANGE("range", "사거리 포기", Items.SPYGLASS, 0.50, false, 0.60),

    /** Paper-thin but hits harder. Fatal on anything holding aggro. */
    DURABILITY("durability", "내구 포기", Items.LEATHER_CHESTPLATE, 0.60, false, 0.35),

    /** Always takes the nearest monster instead of the best one, in exchange for reach. */
    JUDGEMENT("judgement", "판단 포기", Items.COMPASS, 0.0, false, 0.50),

    /**
     * Much slower, much harder.
     *
     * <p>Net DPS only rises about 43%, but each hit lands two and a half times heavier. Excellent
     * against single high-health targets, wasteful against crowds, and outright suicidal next to
     * the 정수 오버플로 bug.
     */
    FIRE_RATE("fire_rate", "공속 포기", Items.CLOCK, 0.75, true, 1.50),

    /** Misses often, but a hit nearly doubles. Expected value up, variance way up. */
    ACCURACY("accuracy", "정확도 포기", Items.ARROW, 0.30, true, 0.90),

    /**
     * The only route to a real wall.
     *
     * <p>Attack falls by half and the tower becomes the thing every monster picks. Stacking this
     * with {@link #DURABILITY} is close to a no-op and the dialog should warn about it.
     */
    ATTACK("attack", "공격 포기", Items.NETHERITE_CHESTPLATE, 0.50, false, 1.20),

    /** Costs a second lane slot. Cheap in the late game, brutal in the opening. */
    SLOT("slot", "슬롯 포기", Items.CHEST, 0.0, true, 0.30);

    private final String key;
    private final String displayName;
    private final Item item;

    /**
     * Magnitude of the thing being given up, always stored positive.
     *
     * <p>The balance file rejects negative ability values outright, so the direction lives in
     * {@link #costIsIncrease} instead of in the sign. {@link #costMultiplier()} recombines them.
     */
    private final double defaultCost;

    /** True when the cost raises a number (attack interval) rather than lowering one. */
    private final boolean costIsIncrease;

    /** Size of the thing being gained. */
    private final double defaultGain;

    DeveloperOptimization(
            String key,
            String displayName,
            Item item,
            double defaultCost,
            boolean costIsIncrease,
            double defaultGain
    ) {
        this.key = key;
        this.displayName = displayName;
        this.item = item;
        this.defaultCost = defaultCost;
        this.costIsIncrease = costIsIncrease;
        this.defaultGain = defaultGain;
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

    /** Magnitude of the sacrifice, always non-negative. See {@link #costMultiplier()}. */
    public double cost() {
        return Math.max(0.0, TowerBalanceRuntime.ability(
                DeveloperBalance.CONFIG_ID, key + "OptimizationCost", defaultCost));
    }

    /**
     * The sacrifice as a multiplier: {@code 1 - cost} for a reduction, {@code 1 + cost} for an
     * increase.
     *
     * <p>{@link #ACCURACY} is the exception — its cost is a miss probability, not a multiplier, so
     * callers read {@link #cost()} directly there.
     */
    public double costMultiplier() {
        return costIsIncrease ? 1.0 + cost() : Math.max(0.0, 1.0 - cost());
    }

    public boolean costIsIncrease() {
        return costIsIncrease;
    }

    public double gain() {
        return Math.max(0.0, TowerBalanceRuntime.ability(
                DeveloperBalance.CONFIG_ID, key + "OptimizationGain", defaultGain));
    }

    /**
     * Slot weight this option adds.
     *
     * <p>{@link #SLOT} is the only one that touches lane capacity, and it does so through
     * {@code Tower.slotWeight()} rather than any bespoke accounting.
     */
    public int extraSlotWeight() {
        return this == SLOT ? 1 : 0;
    }

    /**
     * Whether stacking {@code other} on top of this one is close to pointless.
     *
     * <p>Only one pair qualifies today: cutting health by 60% and then raising it by 120% nets out
     * to almost nothing while burning two of three match-wide charges.
     */
    public boolean conflictsWith(DeveloperOptimization other) {
        if (other == null || other == this) {
            return false;
        }
        return (this == DURABILITY && other == ATTACK) || (this == ATTACK && other == DURABILITY);
    }

    /** Shipped default, used to seed the balance file. Reading {@link #cost()} there would recurse. */
    public double defaultCost() {
        return defaultCost;
    }

    public double defaultGain() {
        return defaultGain;
    }

    /** Config key for the cost half of this trade. */
    public String costKey() {
        return key + "OptimizationCost";
    }

    /** Config key for the gain half of this trade. */
    public String gainKey() {
        return key + "OptimizationGain";
    }

    public static Optional<DeveloperOptimization> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (DeveloperOptimization optimization : values()) {
            if (optimization.key.equals(normalized)) {
                return Optional.of(optimization);
            }
        }
        return Optional.empty();
    }
}
