package kim.biryeong.semiontd.effect;

import kim.biryeong.semiontd.summon.SummonBalancePolicy;

public enum TimedEffectType {
    TOWER_ATTACK_SPEED_REDUCTION(SummonBalancePolicy.MAX_TOWER_ATTACK_SPEED_REDUCTION),
    TOWER_RANGE_REDUCTION(SummonBalancePolicy.MAX_TOWER_RANGE_REDUCTION),
    TOWER_DAMAGE_BONUS(SummonBalancePolicy.MAX_TOWER_DAMAGE_BONUS),
    TOWER_ATTACK_SPEED_BONUS(SummonBalancePolicy.MAX_TOWER_ATTACK_SPEED_BONUS),
    TOWER_RANGE_BONUS(SummonBalancePolicy.MAX_TOWER_RANGE_BONUS),
    TOWER_DAMAGE_REDUCTION(SummonBalancePolicy.MAX_TOWER_DAMAGE_REDUCTION),
    MONSTER_DAMAGE_REDUCTION(SummonBalancePolicy.MAX_MONSTER_DAMAGE_REDUCTION),
    MONSTER_MOVE_SPEED_BONUS(SummonBalancePolicy.MAX_MONSTER_MOVE_SPEED_BONUS);

    private final double maximumMagnitude;

    TimedEffectType(double maximumMagnitude) {
        this.maximumMagnitude = Math.max(0.0, maximumMagnitude);
    }

    public double cappedMagnitude(double magnitude) {
        return Math.max(0.0, Math.min(maximumMagnitude, magnitude));
    }
}
