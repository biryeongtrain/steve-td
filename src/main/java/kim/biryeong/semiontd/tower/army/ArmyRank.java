package kim.biryeong.semiontd.tower.army;

/**
 * The one number the 군대 builder runs on.
 *
 * <p>Rank is earned by surviving waves, never by spending minerals, which makes it orthogonal to
 * tier: a T3 이등병 is a fresh heavy hitter and a T3 병장 is a commander that no longer fires at all.
 *
 * <p>The attack multipliers fall to zero on purpose. A tower that keeps some damage at the top rank
 * would let a player stack 고참 without paying for it, and the whole builder is the trade between
 * shooting now and making everyone else shoot harder.
 *
 * <p>Thresholds are deliberately not configurable. They define the shape of the family rather than
 * its power level — {@link ArmyBalance} tunes the multipliers instead.
 */
public enum ArmyRank {
    /** Fresh. Full damage, gives nothing. */
    PRIVATE("이등병", 0, 1.0, 0.0, 0.0),
    CORPORAL("일병", 2, 0.75, 0.12, 0.0),
    SERGEANT("상병", 5, 0.40, 0.28, 0.0),
    /** Stops firing entirely and carries the family's real output on its buff. */
    STAFF_SERGEANT("병장", 9, 0.0, 0.50, 0.15);

    /**
     * Service length at which a tower is discharged.
     *
     * <p>Set from the lifetime-average maths rather than picked: over a full service the damage
     * multiplier averages {@code (2*1.00 + 3*0.75 + 4*0.40 + 4*0.00) / 13 = 0.45}. Pushing discharge
     * later stretches the zero-damage tail and drags the family below every other builder's curve.
     */
    public static final int DISCHARGE_SERVICE = 13;

    /** Waves of warning before the automatic discharge. */
    public static final int DISCHARGE_NOTICE_WAVES = 2;

    private final String displayName;
    private final int requiredService;
    private final double attackMultiplier;
    private final double damageBuff;
    private final double attackSpeedBuff;

    ArmyRank(String displayName, int requiredService, double attackMultiplier, double damageBuff, double attackSpeedBuff) {
        this.displayName = displayName;
        this.requiredService = requiredService;
        this.attackMultiplier = attackMultiplier;
        this.damageBuff = damageBuff;
        this.attackSpeedBuff = attackSpeedBuff;
    }

    public String displayName() {
        return displayName;
    }

    public int requiredService() {
        return requiredService;
    }

    /** What fraction of its listed damage a tower of this rank still deals. */
    public double attackMultiplier() {
        return attackMultiplier;
    }

    /** Damage bonus this rank grants to each lower-ranked ally in range. */
    public double damageBuff() {
        return damageBuff;
    }

    public double attackSpeedBuff() {
        return attackSpeedBuff;
    }

    public boolean isSuperiorTo(ArmyRank other) {
        return other != null && ordinal() > other.ordinal();
    }

    /** Rank for a given service length, clamped to the top rank. */
    public static ArmyRank of(int service) {
        ArmyRank resolved = PRIVATE;
        for (ArmyRank rank : values()) {
            if (service >= rank.requiredService) {
                resolved = rank;
            }
        }
        return resolved;
    }

    /** Waves until the next promotion, or {@code -1} once the top rank is reached. */
    public static int wavesUntilPromotion(int service) {
        for (ArmyRank rank : values()) {
            if (service < rank.requiredService) {
                return rank.requiredService - service;
            }
        }
        return -1;
    }

    /** Waves until automatic discharge, floored at 0. */
    public static int wavesUntilDischarge(int service) {
        return Math.max(0, DISCHARGE_SERVICE - service);
    }
}
