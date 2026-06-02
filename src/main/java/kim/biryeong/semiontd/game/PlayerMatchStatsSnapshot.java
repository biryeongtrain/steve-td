package kim.biryeong.semiontd.game;

public record PlayerMatchStatsSnapshot(
        long monsterKills,
        long killMinerals,
        long summonedMonsters,
        long finalIncome,
        double ownLaneIncomingThreat,
        double ownLaneLeakedThreat,
        double sentIncomeThreat,
        double incomeAttackSuccessThreat,
        long ownLaneDiamondGain,
        long assistClearDiamondGain,
        long incomeGenerated,
        double assistClearThreat,
        double incomingIncomeThreat
) {
    public PlayerMatchStatsSnapshot(long monsterKills, long killMinerals, long summonedMonsters, long finalIncome) {
        this(
                monsterKills,
                killMinerals,
                summonedMonsters,
                finalIncome,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0,
                0,
                0.0,
                0.0
        );
    }

    public PlayerMatchStatsSnapshot {
        ownLaneIncomingThreat = Math.max(0.0, ownLaneIncomingThreat);
        ownLaneLeakedThreat = Math.max(0.0, ownLaneLeakedThreat);
        sentIncomeThreat = Math.max(0.0, sentIncomeThreat);
        incomeAttackSuccessThreat = Math.max(0.0, incomeAttackSuccessThreat);
        ownLaneDiamondGain = Math.max(0, ownLaneDiamondGain);
        assistClearDiamondGain = Math.max(0, assistClearDiamondGain);
        incomeGenerated = Math.max(0, incomeGenerated);
        assistClearThreat = Math.max(0.0, assistClearThreat);
        incomingIncomeThreat = Math.max(0.0, incomingIncomeThreat);
    }

    public static PlayerMatchStatsSnapshot empty() {
        return new PlayerMatchStatsSnapshot(0, 0, 0, 0);
    }

    public boolean hasAttributionStats() {
        return ownLaneIncomingThreat > 0.0
                || ownLaneLeakedThreat > 0.0
                || sentIncomeThreat > 0.0
                || incomeAttackSuccessThreat > 0.0
                || ownLaneDiamondGain > 0
                || assistClearDiamondGain > 0
                || incomeGenerated > 0
                || assistClearThreat > 0.0
                || incomingIncomeThreat > 0.0;
    }
}
