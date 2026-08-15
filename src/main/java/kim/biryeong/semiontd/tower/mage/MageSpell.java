package kim.biryeong.semiontd.tower.mage;

public enum MageSpell {
    MANA_MISSILE("mana_missile", "마나 미사일", 10, 80, 16.0),
    WIND_CUTTER("wind_cutter", "윈드 커터", 20, 100, 14.0),
    MANA_BOMB("mana_bomb", "마나 폭탄", 25, 120, 16.0),
    CHAIN_LIGHTNING("chain_lightning", "연쇄 번개", 35, 140, 14.0),
    FROST_WAVE("frost_wave", "빙결 파동", 45, 160, 12.0),
    DIMENSIONAL_COLLAPSE("dimensional_collapse", "차원 붕괴", 400, 400, 256.0),
    MAGIC_AMPLIFICATION("magic_amplification", "마력 증폭", 20, 20, 1.5),
    PROJECTILE_BARRIER("projectile_barrier", "투사체 결계", 25, 20, 1.5);

    private final String id;
    private final String displayName;
    private final int defaultManaCost;
    private final int defaultCooldownTicks;
    private final double defaultRange;

    MageSpell(String id, String displayName, int defaultManaCost, int defaultCooldownTicks, double defaultRange) {
        this.id = id;
        this.displayName = displayName;
        this.defaultManaCost = defaultManaCost;
        this.defaultCooldownTicks = defaultCooldownTicks;
        this.defaultRange = defaultRange;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultManaCost() {
        return defaultManaCost;
    }

    public int defaultCooldownTicks() {
        return defaultCooldownTicks;
    }

    public double defaultRange() {
        return defaultRange;
    }
}
