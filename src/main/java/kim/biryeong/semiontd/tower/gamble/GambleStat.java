package kim.biryeong.semiontd.tower.gamble;

public enum GambleStat {
    MAX_HEALTH("최대 체력"),
    DAMAGE("공격력"),
    MAGIC_DAMAGE("마법 공격력"),
    RANGE("사거리"),
    SPLASH_RADIUS("공격 범위");

    private final String displayName;

    GambleStat(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
