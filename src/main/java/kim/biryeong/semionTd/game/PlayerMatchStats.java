package kim.biryeong.semionTd.game;

public final class PlayerMatchStats {
    private long monsterKills;
    private long killMinerals;
    private long summonedMonsters;

    public long monsterKills() {
        return monsterKills;
    }

    public long killMinerals() {
        return killMinerals;
    }

    public long summonedMonsters() {
        return summonedMonsters;
    }

    public void recordMonsterKill(long mineralReward) {
        monsterKills++;
        killMinerals += Math.max(0, mineralReward);
    }

    public void recordSummonedMonster() {
        summonedMonsters++;
    }

    public PlayerMatchStatsSnapshot snapshot(long finalIncome) {
        return new PlayerMatchStatsSnapshot(monsterKills, killMinerals, summonedMonsters, finalIncome);
    }
}
