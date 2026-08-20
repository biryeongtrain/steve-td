package kim.biryeong.semiontd.tower.engineer;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class EngineerBalance {
    public static final String GLOBAL_ID = "engineer_global";

    public static final int ACTIVE_TICKS = 60;
    public static final int DOOR_ACTIVE_TICKS = 120;
    public static final int PLATE_COOLDOWN_TICKS = 100;
    public static final double GOLEM_MOVE_SPEED = 0.18;
    public static final int PISTON_IMMUNITY_TICKS = 300;
    public static final int DOOR_RETARGET_TICKS = 10;
    public static final int TNT_FUSE_TICKS = 60;
    public static final int MAX_REDSTONE = 35;
    public static final int MAX_PLATES = 4;
    public static final int MAX_PISTONS = 3;
    public static final double PLATE_DAMAGE_BONUS_PER_TIER = 0.10;
    public static final double DISPENSER_DAMAGE_PER_PLATE_BLOCK = 0.10;
    public static final int DISPENSER_MAX_PLATE_DISTANCE = 10;
    public static final double DISPENSER_DAMAGE_BONUS_PER_GOLEM_PRESS = 0.01;
    public static final double DISPENSER_DAMAGE_BONUS_CAP = 3.5;
    public static final double DOOR_DAMAGE_REDUCTION_PER_GOLEM_PRESS = 0.001;
    public static final double DOOR_DAMAGE_REDUCTION_CAP = 0.40;
    public static final int GOLEM_PRESSES_PER_EXTRA_TARGET = 10;
    public static final int TNT_EXTRA_TARGET_CAP = 20;
    public static final int PISTON_EXTRA_TARGET_CAP = 10;
    public static final double SLIME_SLOW_PER_GOLEM_PRESS = 0.001;
    public static final double SLIME_SLOW_CAP = 0.80;
    public static final int ACTIVE_VFX_INTERVAL_TICKS = 20;
    public static final int TNT_FUSE_VFX_INTERVAL_TICKS = 10;

    private EngineerBalance() {
    }

    public static int activeTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "activeTicks", ACTIVE_TICKS);
    }

    public static int doorActiveTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "doorActiveTicks", DOOR_ACTIVE_TICKS);
    }

    public static int plateCooldownTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "plateCooldownTicks", PLATE_COOLDOWN_TICKS);
    }

    public static double golemMoveSpeed() {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "golemMoveSpeed", GOLEM_MOVE_SPEED);
    }

    public static int pistonImmunityTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "pistonImmunityTicks", PISTON_IMMUNITY_TICKS);
    }

    public static int doorRetargetTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "doorRetargetTicks", DOOR_RETARGET_TICKS);
    }

    public static int tntFuseTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "tntFuseTicks", TNT_FUSE_TICKS);
    }

    public static int maxRedstone() {
        return TowerBalanceRuntime.abilityInt(GLOBAL_ID, "maxRedstone", MAX_REDSTONE);
    }

    public static int maxPlates() {
        return TowerBalanceRuntime.abilityInt(GLOBAL_ID, "maxPlates", MAX_PLATES);
    }

    public static int maxPistons() {
        return TowerBalanceRuntime.abilityInt(GLOBAL_ID, "maxPistons", MAX_PISTONS);
    }

    public static int dispenserMaxPlateDistance() {
        return TowerBalanceRuntime.abilityInt(
                GLOBAL_ID,
                "dispenserMaxPlateDistance",
                DISPENSER_MAX_PLATE_DISTANCE
        );
    }

    public static int activeVfxIntervalTicks() {
        return TowerBalanceRuntime.abilityTicks(
                GLOBAL_ID,
                "activeVfxIntervalTicks",
                ACTIVE_VFX_INTERVAL_TICKS
        );
    }

    public static int tntFuseVfxIntervalTicks() {
        return TowerBalanceRuntime.abilityTicks(
                GLOBAL_ID,
                "tntFuseVfxIntervalTicks",
                TNT_FUSE_VFX_INTERVAL_TICKS
        );
    }

    public static double dispenserDamageMultiplier(int plateDistance) {
        return 1.0 + Math.min(Math.max(0, plateDistance), dispenserMaxPlateDistance())
                * TowerBalanceRuntime.ability(
                        GLOBAL_ID,
                        "dispenserDamagePerPlateBlock",
                        DISPENSER_DAMAGE_PER_PLATE_BLOCK
                );
    }

    public static double plateDamageMultiplier(EngineerTowers.PlateKind kind) {
        return 1.0 + (kind == null ? 0 : kind.ordinal()) * TowerBalanceRuntime.ability(
                GLOBAL_ID,
                "plateDamageBonusPerTier",
                PLATE_DAMAGE_BONUS_PER_TIER
        );
    }

    public static double dispenserPressDamageMultiplier(int presses) {
        double bonus = Math.max(0, presses) * TowerBalanceRuntime.ability(
                GLOBAL_ID,
                "dispenserDamageBonusPerGolemPress",
                DISPENSER_DAMAGE_BONUS_PER_GOLEM_PRESS
        );
        return 1.0 + Math.min(bonus, dispenserDamageBonusCap());
    }

    public static double dispenserDamageBonusCap() {
        return TowerBalanceRuntime.ability(
                GLOBAL_ID, "dispenserDamageBonusCap", DISPENSER_DAMAGE_BONUS_CAP);
    }

    public static double doorDamageReduction(int presses) {
        double reduction = Math.max(0, presses) * TowerBalanceRuntime.ability(
                GLOBAL_ID,
                "doorDamageReductionPerGolemPress",
                DOOR_DAMAGE_REDUCTION_PER_GOLEM_PRESS
        );
        return Math.min(reduction, doorDamageReductionCap());
    }

    public static double doorDamageReductionCap() {
        return TowerBalanceRuntime.ability(
                GLOBAL_ID, "doorDamageReductionCap", DOOR_DAMAGE_REDUCTION_CAP);
    }

    public static int tntExtraTargets(int presses) {
        return extraTargets(presses, "tntExtraTargetCap", TNT_EXTRA_TARGET_CAP);
    }

    public static int pistonExtraTargets(int presses) {
        return extraTargets(presses, "pistonExtraTargetCap", PISTON_EXTRA_TARGET_CAP);
    }

    public static int tntExtraTargetCap() {
        return TowerBalanceRuntime.abilityInt(GLOBAL_ID, "tntExtraTargetCap", TNT_EXTRA_TARGET_CAP);
    }

    public static int pistonExtraTargetCap() {
        return TowerBalanceRuntime.abilityInt(GLOBAL_ID, "pistonExtraTargetCap", PISTON_EXTRA_TARGET_CAP);
    }

    public static double slimeSlow(double baseSlow, int presses) {
        double slow = Math.max(0.0, baseSlow) + Math.max(0, presses) * TowerBalanceRuntime.ability(
                GLOBAL_ID,
                "slimeSlowPerGolemPress",
                SLIME_SLOW_PER_GOLEM_PRESS
        );
        return Math.min(slow, slimeSlowCap());
    }

    public static double slimeSlowCap() {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "slimeSlowCap", SLIME_SLOW_CAP);
    }

    private static int extraTargets(int presses, String capKey, int fallbackCap) {
        int perTarget = Math.max(1, TowerBalanceRuntime.abilityInt(
                GLOBAL_ID,
                "golemPressesPerExtraTarget",
                GOLEM_PRESSES_PER_EXTRA_TARGET
        ));
        int cap = TowerBalanceRuntime.abilityInt(GLOBAL_ID, capKey, fallbackCap);
        return Math.min(Math.max(0, presses) / perTarget, cap);
    }
}
