package kim.biryeong.semionTd.game;

public record PlayerMatchStatsSnapshot(
        long monsterKills,
        long killMinerals,
        long summonedMonsters,
        long finalIncome
) {
    public static PlayerMatchStatsSnapshot empty() {
        return new PlayerMatchStatsSnapshot(0, 0, 0, 0);
    }
}
