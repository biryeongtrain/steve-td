package kim.biryeong.semiontd.config;

public record EconomyConfig(
        long startingMineral,
        long startingGas,
        long startingIncome,
        GasCapConfig gasCap,
        GasProductionConfig gasProduction
) {
    public EconomyConfig {
        if (startingMineral < 0 || startingGas < 0 || startingIncome < 0) {
            throw new IllegalArgumentException("Starting economy values cannot be negative.");
        }
        if (gasCap == null) {
            gasCap = GasCapConfig.defaultConfig();
        }
        if (gasProduction == null) {
            gasProduction = GasProductionConfig.defaultConfig();
        }
    }

    public static EconomyConfig defaultConfig() {
        return new EconomyConfig(200, 50, 0, GasCapConfig.defaultConfig(), GasProductionConfig.defaultConfig());
    }

    public long gasCapForRound(int round) {
        return gasCap.capForRound(round);
    }

    public record GasCapConfig(long base, long roundOffsetMultiplier, long roundOffsetStep, long flatBonus) {
        public GasCapConfig {
            if (base < 0 || roundOffsetMultiplier < 0 || roundOffsetStep < 0 || flatBonus < 0) {
                throw new IllegalArgumentException("Gas cap config values cannot be negative.");
            }
        }

        public static GasCapConfig defaultConfig() {
            return new GasCapConfig(500, 2, 20, 10);
        }

        public long capForRound(int round) {
            int safeRound = Math.max(1, round);
            return base + roundOffsetMultiplier * ((long) (safeRound - 1) * roundOffsetStep) + flatBonus;
        }
    }

    public record GasProductionConfig(
            long initialGasPerSec,
            int maxUpgradeCount,
            long initialUpgradeCost,
            long upgradeCostIncrease,
            long gasPerSecIncrease,
            CurrencyType upgradeCurrency
    ) {
        public GasProductionConfig {
            if (initialGasPerSec < 0 || maxUpgradeCount < 0 || initialUpgradeCost < 0
                    || upgradeCostIncrease < 0 || gasPerSecIncrease < 0) {
                throw new IllegalArgumentException("Gas production config values cannot be negative.");
            }
            if (upgradeCurrency == null) {
                upgradeCurrency = CurrencyType.MINERAL;
            }
        }

        public static GasProductionConfig defaultConfig() {
            return new GasProductionConfig(1, 20, 50, 25, 1, CurrencyType.MINERAL);
        }

        public long upgradeCost(int currentUpgradeCount) {
            return initialUpgradeCost + upgradeCostIncrease * Math.max(0, currentUpgradeCount);
        }
    }
}