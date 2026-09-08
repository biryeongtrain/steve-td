package kim.biryeong.semiontd.tower.gamble;

import kim.biryeong.semiontd.effect.TimedEffectType;

public enum GambleSupportStat {
    RANGE("사거리", TimedEffectType.TOWER_FLAT_RANGE_BONUS,
            TimedEffectType.TOWER_FLAT_RANGE_REDUCTION),
    REGENERATION("초당 체력", TimedEffectType.TOWER_HEALTH_REGEN_PER_SECOND,
            TimedEffectType.TOWER_HEALTH_LOSS_PER_SECOND),
    DAMAGE("공격력", TimedEffectType.TOWER_FLAT_DAMAGE_BONUS,
            TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION),
    MAX_HEALTH("최대 체력", TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS,
            TimedEffectType.TOWER_FLAT_MAX_HEALTH_REDUCTION),
    MAGIC_DAMAGE("마법 공격력", TimedEffectType.TOWER_FLAT_MAGIC_DAMAGE_BONUS, null);

    private final String displayName;
    private final TimedEffectType positiveType;
    private final TimedEffectType negativeType;

    GambleSupportStat(String displayName, TimedEffectType positiveType, TimedEffectType negativeType) {
        this.displayName = displayName;
        this.positiveType = positiveType;
        this.negativeType = negativeType;
    }

    public String displayName() {
        return displayName;
    }

    public String displayName(boolean positive) {
        if (this == REGENERATION) {
            return positive ? "초당 체력 회복" : "초당 체력 감소";
        }
        return displayName;
    }

    public TimedEffectType effectType(boolean positive) {
        if (!positive && negativeType == null) throw new IllegalArgumentException("This support stat has no negative effect.");
        return positive ? positiveType : negativeType;
    }
}
