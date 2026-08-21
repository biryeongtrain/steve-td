package kim.biryeong.semiontd.tower.futureagency;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerLane;

public final class FutureAgencyBalance {
    public static final String GLOBAL_ID = "future_agency_global";
    public static final double REBUILDER_DAMAGE = 0.05;
    public static final double REBUILDER_HEALTH = 0.05;
    public static final double COMMANDER_DAMAGE = 0.12;
    public static final double COMMANDER_HEALTH = 0.12;
    public static final double COMMANDER_ATTACK_SPEED = 0.08;
    public static final double SURVIVOR_DAMAGE_PER_COPY = 0.07;
    public static final double ESCAPEE_SURVIVOR_MULTIPLIER = 1.0;
    public static final double REBUILDER_SURVIVOR_MULTIPLIER = 2.0;
    public static final double COMMANDER_SURVIVOR_MULTIPLIER = 3.0;
    public static final double SURVIVOR_DAMAGE_CAP = 3.0;
    public static final double DAMAGE_REDUCTION_CAP = 0.65;
    public static final double SLOW_CAP = 0.60;
    public static final double SUPPRESSION_DENSE_CAP = 0.30;
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

    public static double survivorDamage(FutureAgencyStates.PlayerState state, long survivorCount) {
        double multiplier = switch (state.stage()) {
            case ESCAPEE -> value("escapeeSurvivorDamageMultiplier", ESCAPEE_SURVIVOR_MULTIPLIER);
            case REBUILDER -> value("rebuilderSurvivorDamageMultiplier", REBUILDER_SURVIVOR_MULTIPLIER);
            case COMMANDER -> value("commanderSurvivorDamageMultiplier", COMMANDER_SURVIVOR_MULTIPLIER);
        };
        return Math.min(value("survivorDamageCap", SURVIVOR_DAMAGE_CAP), Math.max(0, survivorCount)
                * value("survivorDamagePerCopy", SURVIVOR_DAMAGE_PER_COPY) * multiplier);
    }

    public static double survivorDamage(FutureAgencyStates.PlayerState state, PlayerLane lane, UUID owner) {
        if (lane == null || owner == null) return 0.0;
        long survivors = lane.towers().stream().filter(FutureAgencyAgentTower.class::isInstance)
                .map(FutureAgencyAgentTower.class::cast)
                .filter(FutureAgencyAgentTower::carriedCopy)
                .filter(tower -> owner.equals(tower.ownerPlayer()) && !tower.isDestroyed(lane))
                .count();
        return survivorDamage(state, survivors);
    }

    public static double stacked(FutureAgencyStates.PlayerState state, FutureAgencyPolicy policy) {
        return state.stacks(policy) * policy(policy);
    }
}
