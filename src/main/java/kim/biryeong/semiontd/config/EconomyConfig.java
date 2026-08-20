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
        TeamTransferConfig teamTransfer,
        EmeraldIncomeBoostConfig emeraldIncomeBoost
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
                TeamTransferConfig.defaultConfig(),
                EmeraldIncomeBoostConfig.defaultConfig()
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
                TeamTransferConfig.defaultConfig(),
                EmeraldIncomeBoostConfig.defaultConfig()
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
                TeamTransferConfig.defaultConfig(),
                EmeraldIncomeBoostConfig.defaultConfig()
        );
    }

    public EconomyConfig(
            long startingDiamond,
            long startingEmerald,
            long startingIncome,
            GasCapConfig emeraldCap,
            GasProductionConfig emeraldProduction,
            TowerLimitConfig towerLimit,
            KillRewardConfig killReward,
            TeamTransferConfig teamTransfer
    ) {
        this(
                startingDiamond,
                startingEmerald,
                startingIncome,
                emeraldCap,
                emeraldProduction,
                towerLimit,
                killReward,
                teamTransfer,
                EmeraldIncomeBoostConfig.defaultConfig()
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
        if (emeraldIncomeBoost == null) {
            emeraldIncomeBoost = EmeraldIncomeBoostConfig.defaultConfig();
        }
    }

    public static EconomyConfig defaultConfig() {
        EconomyConfig fallback = new EconomyConfig(
                200,
                50,
                0,
                GasCapConfig.defaultConfig(),
                GasProductionConfig.defaultConfig(),
                TowerLimitConfig.defaultConfig(),
                KillRewardConfig.defaultConfig(),
                TeamTransferConfig.defaultConfig(),
                EmeraldIncomeBoostConfig.defaultConfig()
        );
        return BundledBalanceDefaults.load("economy.json", EconomyConfig.class, fallback);
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

    public long emeraldIncomeMultiplierForRound(int round) {
        return emeraldIncomeBoost.multiplierForRound(round);
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

    /**
     * @param crossLaneOwnerShare 남의 레인에서 잡은 몫 중 레인 주인에게 돌아가는 비율입니다. 잡은
     *     사람은 나머지를 가집니다. 기본은 전액으로, 남의 레인 청소는 도와주는 행위이지 파밍이
     *     아니라는 뜻입니다 - 경험치는 그대로 들어가므로 도울 이유는 남습니다. 최종 방어 구간은
     *     원래 모두가 같이 막는 자리라 여기서 빼고 기존
     *     {@code crossLaneFinalDefenseWaveMultiplier} 가 계속 담당합니다.
     */
    public record KillRewardConfig(
            boolean crossLaneWaveReductionEnabled,
            double crossLaneFinalDefenseWaveMultiplier,
            double finalDefenseProgressThreshold,
            boolean applyToIncomeUnits,
            double crossLaneOwnerShare
    ) {
        public KillRewardConfig(
                boolean crossLaneWaveReductionEnabled,
                double crossLaneFinalDefenseWaveMultiplier,
                double finalDefenseProgressThreshold,
                boolean applyToIncomeUnits
        ) {
            this(crossLaneWaveReductionEnabled, crossLaneFinalDefenseWaveMultiplier,
                    finalDefenseProgressThreshold, applyToIncomeUnits, 1.00);
        }

        public KillRewardConfig {
            if (crossLaneFinalDefenseWaveMultiplier < 0.0 || crossLaneFinalDefenseWaveMultiplier > 1.0
                    || finalDefenseProgressThreshold < 0.0 || finalDefenseProgressThreshold > 1.0
                    || crossLaneOwnerShare < 0.0 || crossLaneOwnerShare > 1.0) {
                throw new IllegalArgumentException("Kill reward config values are invalid.");
            }
        }

        public static KillRewardConfig defaultConfig() {
            return new KillRewardConfig(true, 0.40, 0.90, false, 1.00);
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

    public record EmeraldIncomeBoostConfig(boolean enabled, int startRound) {
        private static final long BOOST_MULTIPLIER = 2L;

        public EmeraldIncomeBoostConfig {
            if (startRound < 1) {
                startRound = 1;
            }
        }

        public static EmeraldIncomeBoostConfig defaultConfig() {
            return new EmeraldIncomeBoostConfig(true, 25);
        }

        public boolean activeForRound(int round) {
            return enabled && Math.max(1, round) >= startRound;
        }

        public long multiplierForRound(int round) {
            return activeForRound(round) ? BOOST_MULTIPLIER : 1L;
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
