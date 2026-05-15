package kim.biryeong.semiontd.summon;

public enum SummonTier {
    T1(10, 35, 0.10, 0.13),
    T2(40, 80, 0.08, 0.11),
    T3(90, 160, 0.06, 0.09),
    T4(180, 320, 0.04, 0.07),
    T5(350, Long.MAX_VALUE, 0.03, 0.05);

    private final long minGasCost;
    private final long maxGasCost;
    private final double minIncomeRatio;
    private final double maxIncomeRatio;

    SummonTier(long minGasCost, long maxGasCost, double minIncomeRatio, double maxIncomeRatio) {
        this.minGasCost = minGasCost;
        this.maxGasCost = maxGasCost;
        this.minIncomeRatio = minIncomeRatio;
        this.maxIncomeRatio = maxIncomeRatio;
    }

    public long minGasCost() {
        return minGasCost;
    }

    public long maxGasCost() {
        return maxGasCost;
    }

    public double minIncomeRatio() {
        return minIncomeRatio;
    }

    public double maxIncomeRatio() {
        return maxIncomeRatio;
    }

    public boolean containsGasCost(long gasCost) {
        return gasCost >= minGasCost && gasCost <= maxGasCost;
    }
}
