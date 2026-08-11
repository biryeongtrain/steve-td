package kim.biryeong.semiontd.tower.adversary;

import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public enum RivalKind {
    BREEZE("breeze", "브리즈", AttackKind.RANGED, "minecraft:breeze"),
    CREEPER("creeper", "크리퍼", AttackKind.MELEE, "minecraft:creeper"),
    PHANTOM("phantom", "팬텀", AttackKind.RANGED, "minecraft:phantom"),
    POLAR_BEAR("polar_bear", "북극곰", AttackKind.MELEE, "minecraft:polar_bear");

    private final String id;
    private final String displayName;
    private final AttackKind attackKind;
    private final String entityTypeId;

    RivalKind(
            String id,
            String displayName,
            AttackKind attackKind,
            String entityTypeId
    ) {
        this.id = id;
        this.displayName = displayName;
        this.attackKind = attackKind;
        this.entityTypeId = entityTypeId;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public long baseCost() {
        return AdversaryBalance.rivalBaseCost(this);
    }

    public long enhancementCost() {
        String fromTowerId = "adversary_" + id + "_rival";
        String upgradeId = fromTowerId + "_enhanced";
        return TowerBalanceRuntime.current().upgradeCost(fromTowerId, upgradeId, baseCost());
    }

    public double baseMaxHealth() {
        return AdversaryBalance.rivalBaseHealth(this);
    }

    public double baseArmor() {
        return AdversaryBalance.rivalBaseArmor(this);
    }

    public double baseDamage() {
        return AdversaryBalance.rivalBaseDamage(this);
    }

    public int attackIntervalTicks() {
        return AdversaryBalance.rivalAttackIntervalTicks(this);
    }

    public int attackIntervalTicks(boolean enhanced) {
        return AdversaryBalance.rivalAttackIntervalTicks(this, enhanced);
    }

    public double range() {
        return AdversaryBalance.rivalRange(this);
    }

    public double range(boolean enhanced) {
        return AdversaryBalance.rivalRange(this, enhanced);
    }

    public AttackKind attackKind() {
        return attackKind;
    }

    public boolean ranged() {
        return attackKind == AttackKind.RANGED;
    }

    public String entityTypeId() {
        return entityTypeId;
    }

    public double maxHealth(int round, boolean enhanced) {
        return AdversaryBalance.rivalHealth(this, enhanced)
                * AdversaryBalance.rivalRoundHealthMultiplier(round);
    }

    public double armor(int round, boolean enhanced) {
        return AdversaryBalance.rivalArmor(this, enhanced)
                + AdversaryBalance.rivalRoundArmorBonus(round);
    }

    public double damage(int round, boolean enhanced) {
        return AdversaryBalance.rivalDamage(this, enhanced)
                * AdversaryBalance.rivalRoundDamageMultiplier(round);
    }

    public int scorePerKill(boolean enhanced) {
        return AdversaryBalance.rivalScorePerKill(this, enhanced);
    }
}
