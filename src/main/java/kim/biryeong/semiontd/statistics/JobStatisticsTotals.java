package kim.biryeong.semiontd.statistics;

public record JobStatisticsTotals(
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
    private static final JobStatisticsTotals EMPTY = new JobStatisticsTotals(
            0L, 0L, 0L, 0L,
            0.0, 0.0, 0.0, 0.0,
            0L, 0L, 0L, 0.0, 0.0
    );

    public static JobStatisticsTotals empty() {
        return EMPTY;
    }
}
