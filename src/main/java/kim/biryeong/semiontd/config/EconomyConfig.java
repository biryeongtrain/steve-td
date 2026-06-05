package kim.biryeong.semiontd.config;

import com.google.gson.annotations.SerializedName;

public record EconomyConfig(
        @SerializedName(value = "startingDiamond", alternate = "startingMineral")
        long startingDiamond,
        @SerializedName(value = "startingEmerald", alternate = "startingGas")
        long startingEmerald,
        long startingIncome,
        @SerializedName(value = "emeraldCap", alternate = "gasCap")
        GasCapConfig emeraldCap,
        @SerializedName(value = "emeraldProduction", alternate = "gasProduction")
        GasProductionConfig emeraldProduction,
        TowerLimitConfig towerLimit,
        KillRewardConfig killReward,
        TeamTransferConfig teamTransfer
) {
    public EconomyConfig(
            long startingDiamond,
            long startingEmerald,
            long startingIncome,
            GasCapConfig emeraldCap,
            GasProductionConfig emeraldProduction
    ) {
        this(
                startingDiamond,
                startingEmerald,
                startingIncome,
                emeraldCap,
                emeraldProduction,
                TowerLimitConfig.defaultConfig(),
                KillRewardConfig.defaultConfig(),
                TeamTransferConfig.defaultConfig()
        );
    }

    public EconomyConfig(
            long startingDiamond,
            long startingEmerald,
            long startingIncome,
            GasCapConfig emeraldCap,
            GasProductionConfig emeraldProduction,
            TowerLimitConfig towerLimit
    ) {
        this(
                startingDiamond,
                startingEmerald,
                startingIncome,
                emeraldCap,
                emeraldProduction,
                towerLimit,
                KillRewardConfig.defaultConfig(),
                TeamTransferConfig.defaultConfig()
        );
    }

    public EconomyConfig(
            long startingDiamond,
            long startingEmerald,
            long startingIncome,
            GasCapConfig emeraldCap,
            GasProductionConfig emeraldProduction,
            TowerLimitConfig towerLimit,
            KillRewardConfig killReward
    ) {
        this(
                startingDiamond,
                startingEmerald,
                startingIncome,
                emeraldCap,
                emeraldProduction,
                towerLimit,
                killReward,
                TeamTransferConfig.defaultConfig()
        );
    }

    public EconomyConfig {
        if (startingDiamond < 0 || startingEmerald < 0 || startingIncome < 0) {
            throw new IllegalArgumentException("Starting economy values cannot be negative.");
        }
        if (emeraldCap == null) {
            emeraldCap = GasCapConfig.defaultConfig();
        }
        if (emeraldProduction == null) {
            emeraldProduction = GasProductionConfig.defaultConfig();
        }
        if (towerLimit == null) {
            towerLimit = TowerLimitConfig.defaultConfig();
        }
        if (killReward == null) {
            killReward = KillRewardConfig.defaultConfig();
        }
        if (teamTransfer == null) {
            teamTransfer = TeamTransferConfig.defaultConfig();
        }
    }

    public static EconomyConfig defaultConfig() {
        return new EconomyConfig(
                200,
                50,
                0,
                GasCapConfig.defaultConfig(),
                GasProductionConfig.defaultConfig(),
                TowerLimitConfig.defaultConfig(),
                KillRewardConfig.defaultConfig(),
                TeamTransferConfig.defaultConfig()
        );
    }

    public long startingMineral() {
        return startingDiamond;
    }

    public long startingGas() {
        return startingEmerald;
    }

    public GasCapConfig gasCap() {
        return emeraldCap;
    }

    public GasProductionConfig gasProduction() {
        return emeraldProduction;
    }

    public long emeraldCapForRound(int round) {
        return emeraldCap.capForRound(round);
    }

    public long gasCapForRound(int round) {
        return emeraldCapForRound(round);
    }

    public int towerLimitForRound(int round) {
        return towerLimit.limitForRound(round);
    }

    public record GasCapConfig(long base, long roundOffsetMultiplier, long roundOffsetStep, long flatBonus) {
        public GasCapConfig {
            if (base < 0 || roundOffsetMultiplier < 0 || roundOffsetStep < 0 || flatBonus < 0) {
                throw new IllegalArgumentException("Gas cap config values cannot be negative.");
            }
        }

        public static GasCapConfig defaultConfig() {
            return new GasCapConfig(1500, 6, 20, 30);
        }

        public long capForRound(int round) {
            int safeRound = Math.max(1, round);
            return base + roundOffsetMultiplier * ((long) (safeRound - 1) * roundOffsetStep) + flatBonus;
        }
    }

    public record GasProductionConfig(
            @SerializedName(value = "initialEmeraldPerSec", alternate = "initialGasPerSec")
            long initialEmeraldPerSec,
            int maxUpgradeCount,
            long initialUpgradeCost,
            long upgradeCostIncrease,
            @SerializedName(value = "emeraldPerSecIncrease", alternate = "gasPerSecIncrease")
            long emeraldPerSecIncrease,
            CurrencyType upgradeCurrency
    ) {
        public GasProductionConfig {
            if (initialEmeraldPerSec < 0 || maxUpgradeCount < 0 || initialUpgradeCost < 0
                    || upgradeCostIncrease < 0 || emeraldPerSecIncrease < 0) {
                throw new IllegalArgumentException("Emerald production config values cannot be negative.");
            }
            if (upgradeCurrency == null) {
                upgradeCurrency = CurrencyType.DIAMOND;
            }
        }

        public static GasProductionConfig defaultConfig() {
            return new GasProductionConfig(1, 20, 50, 25, 1, CurrencyType.DIAMOND);
        }

        public long upgradeCost(int currentUpgradeCount) {
            return initialUpgradeCost + upgradeCostIncrease * Math.max(0, currentUpgradeCount);
        }

        public long initialGasPerSec() {
            return initialEmeraldPerSec;
        }

        public long gasPerSecIncrease() {
            return emeraldPerSecIncrease;
        }
    }

    public record KillRewardConfig(
            boolean crossLaneWaveReductionEnabled,
            double crossLaneFinalDefenseWaveMultiplier,
            double finalDefenseProgressThreshold,
            boolean applyToIncomeUnits
    ) {
        public KillRewardConfig {
            if (crossLaneFinalDefenseWaveMultiplier < 0.0 || crossLaneFinalDefenseWaveMultiplier > 1.0
                    || finalDefenseProgressThreshold < 0.0 || finalDefenseProgressThreshold > 1.0) {
                throw new IllegalArgumentException("Kill reward config values are invalid.");
            }
        }

        public static KillRewardConfig defaultConfig() {
            return new KillRewardConfig(true, 0.40, 0.90, false);
        }
    }

    public record TeamTransferConfig(
            boolean enabled,
            int receiveCooldownRounds,
            long maxDiamondPerRound
    ) {
        public TeamTransferConfig {
            if (receiveCooldownRounds < 0 || maxDiamondPerRound < 0) {
                throw new IllegalArgumentException("Team transfer config values are invalid.");
            }
        }

        public static TeamTransferConfig defaultConfig() {
            return new TeamTransferConfig(true, 3, 30);
        }

        public long maxRequestDiamond(int round) {
            return maxDiamondPerRound * Math.max(1, round);
        }
    }

    public record TowerLimitConfig(
            int initialLimit,
            int increaseStartRound,
            int increaseEveryRounds,
            int increaseAmount,
            int maxLimit,
            int purchaseIncreaseAmount,
            int maxPurchaseCount,
            long initialPurchaseDiamondCost,
            long purchaseDiamondCostIncrease,
            long initialPurchaseEmeraldCost,
            long purchaseEmeraldCostIncrease
    ) {
        public TowerLimitConfig(
                int initialLimit,
                int increaseStartRound,
                int increaseEveryRounds,
                int increaseAmount,
                int maxLimit
        ) {
            this(
                    initialLimit,
                    increaseStartRound,
                    increaseEveryRounds,
                    increaseAmount,
                    maxLimit,
                    defaultConfig().purchaseIncreaseAmount,
                    defaultConfig().maxPurchaseCount,
                    defaultConfig().initialPurchaseDiamondCost,
                    defaultConfig().purchaseDiamondCostIncrease,
                    defaultConfig().initialPurchaseEmeraldCost,
                    defaultConfig().purchaseEmeraldCostIncrease
            );
        }

        public TowerLimitConfig {
            if (initialLimit < 0 || increaseStartRound < 1 || increaseEveryRounds < 1
                    || increaseAmount < 0 || maxLimit < 0 || purchaseIncreaseAmount < 0
                    || maxPurchaseCount < 0 || initialPurchaseDiamondCost < 0 || purchaseDiamondCostIncrease < 0
                    || initialPurchaseEmeraldCost < 0 || purchaseEmeraldCostIncrease < 0) {
                throw new IllegalArgumentException("Tower limit config values are invalid.");
            }
            if (maxLimit < initialLimit) {
                maxLimit = initialLimit;
            }
        }

        public static TowerLimitConfig defaultConfig() {
            return new TowerLimitConfig(5, 5, 5, 3, 11, 1, 20, 100, 50, 25, 10);
        }

        public TowerLimitConfig withDefaultPurchaseSettings() {
            TowerLimitConfig defaults = defaultConfig();
            return new TowerLimitConfig(
                    initialLimit,
                    increaseStartRound,
                    increaseEveryRounds,
                    increaseAmount,
                    maxLimit,
                    defaults.purchaseIncreaseAmount,
                    defaults.maxPurchaseCount,
                    defaults.initialPurchaseDiamondCost,
                    defaults.purchaseDiamondCostIncrease,
                    defaults.initialPurchaseEmeraldCost,
                    defaults.purchaseEmeraldCostIncrease
            );
        }

        public int limitForRound(int round) {
            int safeRound = Math.max(1, round);
            if (safeRound < increaseStartRound || increaseAmount == 0) {
                return Math.min(initialLimit, maxLimit);
            }
            int increases = ((safeRound - increaseStartRound) / increaseEveryRounds) + 1;
            long limit = initialLimit + (long) increases * increaseAmount;
            return (int) Math.min(maxLimit, Math.max(0, limit));
        }

        public int purchasedBonus(int purchaseCount) {
            return Math.max(0, purchaseCount) * purchaseIncreaseAmount;
        }

        public long purchaseDiamondCost(int currentPurchaseCount) {
            int safePurchaseCount = Math.max(0, currentPurchaseCount);
            if (safePurchaseCount >= maxPurchaseCount || purchaseIncreaseAmount == 0) {
                return -1;
            }
            return initialPurchaseDiamondCost + purchaseDiamondCostIncrease * (long) safePurchaseCount;
        }

        public long purchaseEmeraldCost(int currentPurchaseCount) {
            int safePurchaseCount = Math.max(0, currentPurchaseCount);
            if (safePurchaseCount >= maxPurchaseCount || purchaseIncreaseAmount == 0) {
                return -1;
            }
            return initialPurchaseEmeraldCost + purchaseEmeraldCostIncrease * (long) safePurchaseCount;
        }
    }
}
