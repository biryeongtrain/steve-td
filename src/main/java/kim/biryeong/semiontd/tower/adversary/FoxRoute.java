package kim.biryeong.semiontd.tower.adversary;

public enum FoxRoute {
    RAPID(FoxRole.RAPID),
    TEAM_CONTROL(FoxRole.TEAM_CONTROL),
    TARGET_SPECIALIST(FoxRole.TARGET_SPECIALIST),
    HIGH_CEILING(FoxRole.HIGH_CEILING);

    private final FoxRole role;

    FoxRoute(FoxRole role) {
        this.role = role;
    }

    public FoxRole role() {
        return role;
    }
}
