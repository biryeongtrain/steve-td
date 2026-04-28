package kim.biryeong.semiontd.summon;

public final class SummonBalancePolicy {
    public static final double MAX_TOWER_ATTACK_SPEED_REDUCTION = 0.40;
    public static final double MAX_TOWER_RANGE_REDUCTION = 0.30;
    public static final double MAX_MONSTER_DAMAGE_REDUCTION = 0.35;
    public static final double MAX_MONSTER_MOVE_SPEED_BONUS = 0.30;
    public static final double SIEGE_NEAR_BOSS_PROGRESS = 0.80;
    public static final double SIEGE_NEAR_BOSS_TARGET_BONUS = 30.0;
    public static final double SUPPORT_SINGLE_HEAL_RADIUS = 8.0;
    public static final double SUPPORT_SINGLE_HEAL_AMOUNT = 12.0;
    public static final int SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS = 80;
    public static final double SUPPORT_AREA_HEAL_RADIUS = 5.0;
    public static final double SUPPORT_AREA_HEAL_AMOUNT = 5.0;
    public static final int SUPPORT_AREA_HEAL_COOLDOWN_TICKS = 200;
    public static final int SUPPORT_AREA_HEAL_MAX_TARGETS = 4;
    public static final int SUPPORT_HEAL_RETRY_TICKS = 10;

    private SummonBalancePolicy() {
    }
}
