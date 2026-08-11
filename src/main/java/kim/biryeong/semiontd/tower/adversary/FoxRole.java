package kim.biryeong.semiontd.tower.adversary;

public enum FoxRole {
    BASE("기본"),
    RAPID("빠른 저비용 성장"),
    TEAM_CONTROL("팀 강화 및 적 약화"),
    TARGET_SPECIALIST("웨이브 및 인컴 특화"),
    HIGH_CEILING("느리고 위험한 초고점");

    private final String displayName;

    FoxRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
