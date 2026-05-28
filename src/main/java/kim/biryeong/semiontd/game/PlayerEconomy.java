package kim.biryeong.semiontd.game;

import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;

public final class PlayerEconomy {
    private long diamond;
    private long emerald;
    private long income;
    private long emeraldPerSec;
    private int emeraldProductionUpgradeCount;

    public PlayerEconomy(EconomyConfig config) {
        this.diamond = config.startingDiamond();
        this.emerald = config.startingEmerald();
        this.income = config.startingIncome();
        this.emeraldPerSec = config.gasProduction().initialEmeraldPerSec();
    }

    public void overrideStartingValues(long mineral, long gas, long income, long gasPerSec) {
        this.diamond = Math.max(0, mineral);
        this.emerald = Math.max(0, gas);
        this.income = Math.max(0, income);
        this.emeraldPerSec = Math.max(0, gasPerSec);
    }

    public long diamond() {
        return diamond;
    }

    public long emerald() {
        return emerald;
    }

    public long mineral() {
        return diamond;
    }

    public long gas() {
        return emerald;
    }

    public long income() {
        return income;
    }

    public long emeraldPerSec() {
        return emeraldPerSec;
    }

    public long gasPerSec() {
        return emeraldPerSec;
    }

    public int emeraldProductionUpgradeCount() {
        return emeraldProductionUpgradeCount;
    }

    public int gasProductionUpgradeCount() {
        return emeraldProductionUpgradeCount;
    }

    public void addDiamond(long amount) {
        if (amount > 0) {
            diamond += amount;
        }
    }

    public void addMineral(long amount) {
        addDiamond(amount);
    }

    public void addEmerald(long amount, long cap) {
        if (amount > 0) {
            emerald = Math.min(cap, emerald + amount);
        }
    }

    public void addEmerald(long amount) {
        if (amount > 0) {
            emerald += amount;
        }
    }

    public void addGas(long amount, long cap) {
        addEmerald(amount, cap);
    }

    public void addIncome(long amount) {
        if (amount > 0) {
            income += amount;
        }
    }

    public boolean spendDiamond(long amount) {
        if (amount < 0 || diamond < amount) {
            return false;
        }
        diamond -= amount;
        return true;
    }

    public boolean spendMineral(long amount) {
        return spendDiamond(amount);
    }

    public boolean spendEmerald(long amount) {
        if (amount < 0 || emerald < amount) {
            return false;
        }
        emerald -= amount;
        return true;
    }

    public boolean spendGas(long amount) {
        return spendEmerald(amount);
    }

    public void payIncome() {
        addDiamond(income);
    }

    public boolean upgradeGasProduction(EconomyConfig.GasProductionConfig config) {
        if (emeraldProductionUpgradeCount >= config.maxUpgradeCount()) {
            return false;
        }

        long cost = config.upgradeCost(emeraldProductionUpgradeCount);
        boolean spent = config.upgradeCurrency().spendsDiamond() ? spendDiamond(cost) : spendEmerald(cost);
        if (!spent) {
            return false;
        }

        emeraldPerSec += config.emeraldPerSecIncrease();
        emeraldProductionUpgradeCount++;
        return true;
    }
}
