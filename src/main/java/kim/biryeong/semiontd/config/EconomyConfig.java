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
        TowerLimitConfig towerLimit
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
                TowerLimitConfig.defaultConfig()
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
    }

    public static EconomyConfig defaultConfig() {
        return new EconomyConfig(
                200,
                50,
                0,
                GasCapConfig.defaultConfig(),
                GasProductionConfig.defaultConfig(),
                TowerLimitConfig.defaultConfig()
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

    public record TowerLimitConfig(
            int initialLimit,
            int increaseStartRound,
            int increaseEveryRounds,
            int increaseAmount,
            int maxLimit
    ) {
        public TowerLimitConfig {
            if (initialLimit < 0 || increaseStartRound < 1 || increaseEveryRounds < 1
                    || increaseAmount < 0 || maxLimit < 0) {
                throw new IllegalArgumentException("Tower limit config values are invalid.");
            }
            if (maxLimit < initialLimit) {
                maxLimit = initialLimit;
            }
        }

        public static TowerLimitConfig defaultConfig() {
            return new TowerLimitConfig(5, 5, 5, 3, 11);
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
    }
}
