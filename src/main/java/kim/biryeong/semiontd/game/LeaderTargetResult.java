package kim.biryeong.semiontd.game;

public enum LeaderTargetResult {
    SUCCESS("타깃을 지정했습니다."),
    PLAYER_NOT_IN_GAME("현재 게임 참가자가 아닙니다."),
    NOT_TEAM_LEADER("팀장만 타깃을 지정할 수 있습니다."),
    INVALID_TARGET_TEAM("알 수 없는 팀입니다."),
    TARGET_SELF_TEAM("자기 팀은 타깃으로 지정할 수 없습니다."),
    TARGET_TEAM_NOT_ALIVE("생존 중인 팀만 타깃으로 지정할 수 있습니다."),
    TARGET_TEAM_ALREADY_DESIGNATED("해당 라인을 지정할 수 없습니다."),
    COOLDOWN_ACTIVE("아직 팀장 능력 쿨타임입니다.");

    private final String message;

    LeaderTargetResult(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
