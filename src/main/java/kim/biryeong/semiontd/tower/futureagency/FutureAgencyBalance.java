package kim.biryeong.semiontd.tower.futureagency;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class FutureAgencyBalance {
    public static final String GLOBAL_ID = "future_agency_global";
    public static final double REBUILDER_DAMAGE = 0.05;
    public static final double REBUILDER_HEALTH = 0.05;
    public static final double COMMANDER_DAMAGE = 0.12;
    public static final double COMMANDER_HEALTH = 0.12;
    public static final double COMMANDER_ATTACK_SPEED = 0.08;
    public static final double DAMAGE_REDUCTION_CAP = 0.65;
    public static final double SLOW_CAP = 0.60;
    public static final double SUPPRESSION_DENSE_CAP = 0.18;
    public static final double SUPPRESSION_DENSE_RADIUS = 2.5;
    public static final double ESCORT_RADIUS = 2.5;

    private FutureAgencyBalance() {}

    public static double value(String key, double fallback) {
        return TowerBalanceRuntime.ability(GLOBAL_ID, key, fallback);
    }

    public static double policy(FutureAgencyPolicy policy) {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "policy." + policy.id(), policy.defaultValue());
    }

    public static double damageReductionCap() {
        return value("damageReductionCap", DAMAGE_REDUCTION_CAP);
    }

    public static double slowCap() {
        return value("slowCap", SLOW_CAP);
    }

    public static double suppressionDenseCap() {
        return value("suppressionDenseCap", SUPPRESSION_DENSE_CAP);
    }

    public static double suppressionDenseRadius() {
        return value("suppressionDenseRadius", SUPPRESSION_DENSE_RADIUS);
    }

    public static double escortRadius() {
        return value("escortRadius", ESCORT_RADIUS);
    }

    public static double agentAbility(kim.biryeong.semiontd.tower.TowerType type,
                                      String key, double fallback) {
        return TowerBalanceRuntime.ability(type.id(), key, fallback);
    }

    public static double leaderDamage(FutureAgencyStates.PlayerState state) {
        return state.commander() ? value("commanderDamageBonus", COMMANDER_DAMAGE)
                : state.reconstructed() ? value("rebuilderDamageBonus", REBUILDER_DAMAGE) : 0.0;
    }

    public static double leaderHealth(FutureAgencyStates.PlayerState state) {
        return state.commander() ? value("commanderMaxHealthBonus", COMMANDER_HEALTH)
                : state.reconstructed() ? value("rebuilderMaxHealthBonus", REBUILDER_HEALTH) : 0.0;
    }

    public static double leaderAttackSpeed(FutureAgencyStates.PlayerState state) {
        return state.commander() ? value("commanderAttackSpeedBonus", COMMANDER_ATTACK_SPEED) : 0.0;
    }

    public static double stacked(FutureAgencyStates.PlayerState state, FutureAgencyPolicy policy) {
        return state.stacks(policy) * policy(policy);
    }
}
