package kim.biryeong.semiontd.tower.developer;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * Typed access to the {@code developer_global} family configuration and per-tower abilities.
 *
 * <p>Every value here is read through {@link TowerBalanceRuntime} at call time rather than cached.
 * Patches are per-player and per-tower state, so nothing in this family may ever be written back
 * into the shared balance runtime — that map is global and one player's patch would leak to the
 * whole server.
 *
 * <p>Fallbacks mirror the {@code TowerBalanceConfig} source defaults so a config file that predates
 * a key still resolves to the shipped numbers instead of zero.
 */
public final class DeveloperBalance {
    public static final String CONFIG_ID = "developer_global";

    // ------------------------------------------------------------------ 패치 수치

    /** Attack gained by one attack patch, before diminishing and the tower scale. */
    public static final double PATCH_ATTACK = 0.16;
    public static final double PATCH_RANGE = 0.09;
    public static final double PATCH_INTERVAL = 0.12;
    public static final double PATCH_HEALTH = 0.13;

    /**
     * Aggro is the one stat on an integer scale ({@code -100..100} per {@code TowerBalanceConfig}
     * validation), so it is added flat instead of multiplied.
     */
    public static final int PATCH_AGGRO = 8;

    /**
     * Applied as {@code DIMINISHING^(n-1)} to the nth patch of the same kind on the same tower.
     *
     * <p>This is the knob that decides whether stacking one stat or spreading across four is
     * correct. At 0.88 the fifth patch of a kind is worth 60% of the first, which leaves stacking
     * ahead but not overwhelmingly so.
     */
    public static final double PATCH_DIMINISHING = 0.88;

    // ------------------------------------------------------------------ 타워별 배수

    public static final double ALPHA_PATCH_SCALE = 0.85;
    public static final double BETA_PATCH_SCALE = 1.0;
    public static final double RELEASE_PATCH_SCALE = 1.4;
    public static final double LTS_PATCH_SCALE = 1.0;

    /** Release is meant to change through review, so pushing straight to it lands at half. */
    public static final double RELEASE_HOTFIX_SCALE = 0.5;

    /** LTS exists to be hotfixed, so the same push lands harder. */
    public static final double LTS_HOTFIX_SCALE = 1.5;

    /** Aura from 테스트 빌드: how much better nearby allies absorb patches. */
    public static final double TEST_BUILD_AURA_BONUS = 0.25;
    public static final double TEST_BUILD_AURA_RADIUS = 5.0;

    // ------------------------------------------------------------------ 버그 발생

    public static final double ALPHA_BUG_CHANCE = 0.35;
    public static final double BETA_BUG_CHANCE = 0.20;

    /** Hotfixes skip review, so they leave a defect no matter how stable the target is. */
    public static final double HOTFIX_BUG_CHANCE = 1.0;

    /** A tower can only carry so many defects before the dialog stops being readable. */
    public static final int MAX_BUGS_PER_TOWER = 4;

    // ------------------------------------------------------------------ 불안정

    public static final int MAX_INSTABILITY = 5;
    public static final double INSTABILITY_STALL_CHANCE = 0.09;
    public static final int INSTABILITY_STALL_TICKS = 60;

    // ------------------------------------------------------------------ 긴급 점검

    /** Damage bonus the round after a maintenance, paying for the round that was given up. */
    public static final double MAINTENANCE_DAMAGE_BONUS = 0.30;

    // ------------------------------------------------------------------ 능력 한도

    /**
     * Patch slots the builder has before buying anything.
     *
     * <p>Without this the opening round is spent on a 작업대 that cannot fight, which is a tax no
     * other builder pays. 람쥐썬더 solves the same problem with {@code BASE_POWER}: the support
     * tower is an investment for the expensive tiers, not a prerequisite for playing at all.
     */
    public static final int BASE_PATCH_SLOTS = 1;

    /**
     * One extra patch slot per this many combat towers.
     *
     * <p>A fixed budget spread over a lane that grows to 23 towers means the builder gets
     * <em>weaker</em> as it fills out, which is backwards. Linking supply to lane size keeps the
     * per-tower patch count roughly flat however wide the player builds.
     */
    public static final int PATCH_SLOTS_PER_TOWERS = 4;

    public static final int WORKBENCH_PATCH_SLOTS = 2;
    public static final int DEPLOY_SERVER_PATCH_SLOTS = 3;
    public static final int OPS_CENTER_PATCH_SLOTS = 6;

    public static final int DEPLOY_SERVER_HOTFIXES = 1;
    public static final int OPS_CENTER_HOTFIXES = 2;

    public static final int MAINTENANCE_PER_ROUND = 1;
    public static final int DEBUG_REMOVALS_PER_ROUND = 1;
    public static final int REPRODUCE_PER_ROUND = 1;
    public static final int OPTIMIZATIONS_PER_MATCH = 3;
    public static final int VERSION_PIN_SLOTS = 2;

    private DeveloperBalance() {
    }

    // ------------------------------------------------------------------ 조회

    public static double patchAttack() {
        return clamp(global("patchAttack", PATCH_ATTACK), 0.0, 1.0);
    }

    public static double patchRange() {
        return clamp(global("patchRange", PATCH_RANGE), 0.0, 1.0);
    }

    public static double patchInterval() {
        return clamp(global("patchInterval", PATCH_INTERVAL), 0.0, 0.5);
    }

    public static double patchHealth() {
        return clamp(global("patchHealth", PATCH_HEALTH), 0.0, 1.0);
    }

    public static int patchAggro() {
        return Math.max(0, globalInt("patchAggro", PATCH_AGGRO));
    }

    public static double patchDiminishing() {
        return clamp(global("patchDiminishing", PATCH_DIMINISHING), 0.5, 1.0);
    }

    public static double testBuildAuraBonus() {
        return clamp(global("testBuildAuraBonus", TEST_BUILD_AURA_BONUS), 0.0, 2.0);
    }

    public static double testBuildAuraRadius() {
        return Math.max(0.0, global("testBuildAuraRadius", TEST_BUILD_AURA_RADIUS));
    }

    public static int maxBugsPerTower() {
        return Math.max(1, globalInt("maxBugsPerTower", MAX_BUGS_PER_TOWER));
    }

    public static int maxInstability() {
        return Math.max(1, globalInt("maxInstability", MAX_INSTABILITY));
    }

    public static double instabilityStallChance() {
        return clamp(global("instabilityStallChance", INSTABILITY_STALL_CHANCE), 0.0, 0.5);
    }

    public static int instabilityStallTicks() {
        return Math.max(1, globalInt("instabilityStallTicks", INSTABILITY_STALL_TICKS));
    }

    public static double maintenanceDamageBonus() {
        return clamp(global("maintenanceDamageBonus", MAINTENANCE_DAMAGE_BONUS), 0.0, 2.0);
    }

    public static int optimizationsPerMatch() {
        return Math.max(0, globalInt("optimizationsPerMatch", OPTIMIZATIONS_PER_MATCH));
    }

    public static int basePatchSlots() {
        return Math.max(0, globalInt("basePatchSlots", BASE_PATCH_SLOTS));
    }

    public static int patchSlotsPerTowers() {
        return Math.max(1, globalInt("patchSlotsPerTowers", PATCH_SLOTS_PER_TOWERS));
    }

    public static int versionPinSlots() {
        return Math.max(0, globalInt("versionPinSlots", VERSION_PIN_SLOTS));
    }

    public static int debugRemovalsPerRound() {
        return Math.max(0, globalInt("debugRemovalsPerRound", DEBUG_REMOVALS_PER_ROUND));
    }

    public static int reproducePerRound() {
        return Math.max(0, globalInt("reproducePerRound", REPRODUCE_PER_ROUND));
    }

    public static int maintenancePerRound() {
        return Math.max(0, globalInt("maintenancePerRound", MAINTENANCE_PER_ROUND));
    }

    // ------------------------------------------------------------------ 타워별

    /** How well this tower absorbs a 정식 패치. */
    public static double patchScale(TowerType type) {
        if (type == null) {
            return 0.0;
        }
        return Math.max(0.0, TowerBalanceRuntime.ability(type.id(), "patchScale", defaultPatchScale(type)));
    }

    /** How well this tower absorbs a 핫픽스. Multiplied on top of {@link #patchScale}. */
    public static double hotfixScale(TowerType type) {
        if (type == null) {
            return 0.0;
        }
        return Math.max(0.0, TowerBalanceRuntime.ability(type.id(), "hotfixScale", defaultHotfixScale(type)));
    }

    /** Probability that a 정식 패치 on this tower leaves a bug behind. */
    public static double bugChance(TowerType type) {
        if (type == null) {
            return 0.0;
        }
        return clamp(TowerBalanceRuntime.ability(type.id(), "bugChance", defaultBugChance(type)), 0.0, 1.0);
    }

    /** Patch slots this ability tower contributes. The highest owned tower wins, they do not add. */
    public static int patchSlots(TowerType type) {
        if (type == null) {
            return 0;
        }
        return Math.max(0, TowerBalanceRuntime.abilityInt(type.id(), "patchSlots", defaultPatchSlots(type)));
    }

    /** Hotfixes per round this ability tower contributes. Highest owned wins. */
    public static int hotfixesPerRound(TowerType type) {
        if (type == null) {
            return 0;
        }
        return Math.max(0, TowerBalanceRuntime.abilityInt(type.id(), "hotfixesPerRound", defaultHotfixes(type)));
    }

    private static double defaultPatchScale(TowerType type) {
        if (DeveloperTowers.ALPHA.id().equals(type.id())) {
            return ALPHA_PATCH_SCALE;
        }
        if (DeveloperTowers.isRelease(type)) {
            return RELEASE_PATCH_SCALE;
        }
        if (DeveloperTowers.isLts(type)) {
            return LTS_PATCH_SCALE;
        }
        return DeveloperTowers.isGrowthTower(type) ? BETA_PATCH_SCALE : 0.0;
    }

    private static double defaultHotfixScale(TowerType type) {
        if (DeveloperTowers.isRelease(type)) {
            return RELEASE_HOTFIX_SCALE;
        }
        if (DeveloperTowers.isLts(type)) {
            return LTS_HOTFIX_SCALE;
        }
        return DeveloperTowers.isGrowthTower(type) ? 1.0 : 0.0;
    }

    private static double defaultBugChance(TowerType type) {
        if (DeveloperTowers.hasIntegrity(type)) {
            return 0.0;
        }
        if (DeveloperTowers.ALPHA.id().equals(type.id())) {
            return ALPHA_BUG_CHANCE;
        }
        return DeveloperTowers.isGrowthTower(type) ? BETA_BUG_CHANCE : 0.0;
    }

    private static int defaultPatchSlots(TowerType type) {
        if (DeveloperTowers.WORKBENCH.id().equals(type.id())) {
            return WORKBENCH_PATCH_SLOTS;
        }
        if (DeveloperTowers.DEPLOY_SERVER.id().equals(type.id())) {
            return DEPLOY_SERVER_PATCH_SLOTS;
        }
        if (DeveloperTowers.OPS_CENTER.id().equals(type.id())) {
            return OPS_CENTER_PATCH_SLOTS;
        }
        return 0;
    }

    private static int defaultHotfixes(TowerType type) {
        if (DeveloperTowers.DEPLOY_SERVER.id().equals(type.id())) {
            return DEPLOY_SERVER_HOTFIXES;
        }
        if (DeveloperTowers.OPS_CENTER.id().equals(type.id())) {
            return OPS_CENTER_HOTFIXES;
        }
        return 0;
    }

    public static double global(String key, double fallback) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key, fallback);
    }

    public static int globalInt(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, key, fallback);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
