package kim.biryeong.semiontd.game;

public final class PlayerMatchStats {
    private long monsterKills;
    private long killMinerals;
    private long summonedMonsters;
    private double ownLaneIncomingThreat;
    private double ownLaneLeakedThreat;
    private double sentIncomeThreat;
    private double incomeAttackSuccessThreat;
    private long ownLaneDiamondGain;
    private long assistClearDiamondGain;
    private long incomeGenerated;
    private double assistClearThreat;
    private double incomingIncomeThreat;

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

    public void recordOwnLaneMonsterKill(long mineralReward, double threat) {
        recordMonsterKill(mineralReward);
        ownLaneDiamondGain += Math.max(0, mineralReward);
    }

    public void recordAssistMonsterKill(long mineralReward, double threat) {
        recordMonsterKill(mineralReward);
        assistClearDiamondGain += Math.max(0, mineralReward);
        assistClearThreat += Math.max(0.0, threat);
    }

    public void recordSummonedMonster() {
        summonedMonsters++;
    }

    public void recordOwnLaneIncomingThreat(double threat, boolean income) {
        double boundedThreat = Math.max(0.0, threat);
        ownLaneIncomingThreat += boundedThreat;
        if (income) {
            incomingIncomeThreat += boundedThreat;
        }
    }

    public void recordOwnLaneLeakedThreat(double threat) {
        ownLaneLeakedThreat += Math.max(0.0, threat);
    }

    public void recordSentIncomeThreat(double threat) {
        sentIncomeThreat += Math.max(0.0, threat);
    }

    public void recordIncomeAttackSuccessThreat(double threat) {
        incomeAttackSuccessThreat += Math.max(0.0, threat);
    }

    public void recordIncomeGenerated(long incomeGain) {
        incomeGenerated += Math.max(0, incomeGain);
    }

    public PlayerMatchStatsSnapshot snapshot(long finalIncome) {
        return new PlayerMatchStatsSnapshot(
                monsterKills,
                killMinerals,
                summonedMonsters,
                finalIncome,
                ownLaneIncomingThreat,
                ownLaneLeakedThreat,
                sentIncomeThreat,
                incomeAttackSuccessThreat,
                ownLaneDiamondGain,
                assistClearDiamondGain,
                incomeGenerated,
                assistClearThreat,
                incomingIncomeThreat
        );
    }
}
