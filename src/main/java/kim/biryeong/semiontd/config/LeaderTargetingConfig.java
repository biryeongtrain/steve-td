package kim.biryeong.semiontd.config;

public record LeaderTargetingConfig(
        int maxTargetingTeamsPerTarget,
        int activeTargetRounds
) {
    public LeaderTargetingConfig {
        if (maxTargetingTeamsPerTarget < 1) {
            maxTargetingTeamsPerTarget = 2;
        }
        if (activeTargetRounds < 1) {
            activeTargetRounds = 2;
        }
    }

    public static LeaderTargetingConfig defaultConfig() {
        return new LeaderTargetingConfig(2, 2);
    }

    public LeaderTargetingConfig withMaxTargetingTeams(int maxTargetingTeamsPerTarget) {
        return new LeaderTargetingConfig(maxTargetingTeamsPerTarget, activeTargetRounds);
    }

    public LeaderTargetingConfig withActiveTargetRounds(int activeTargetRounds) {
        return new LeaderTargetingConfig(maxTargetingTeamsPerTarget, activeTargetRounds);
    }
}
