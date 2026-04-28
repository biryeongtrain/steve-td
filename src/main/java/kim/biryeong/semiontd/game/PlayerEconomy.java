package kim.biryeong.semiontd.game;

import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;

public final class PlayerEconomy {
    private long mineral;
    private long gas;
    private long income;
    private long gasPerSec;
    private int gasProductionUpgradeCount;

    public PlayerEconomy(EconomyConfig config) {
        this.mineral = config.startingMineral();
        this.gas = config.startingGas();
        this.income = config.startingIncome();
        this.gasPerSec = config.gasProduction().initialGasPerSec();
    }

    public void overrideStartingValues(long mineral, long gas, long income, long gasPerSec) {
        this.mineral = Math.max(0, mineral);
        this.gas = Math.max(0, gas);
        this.income = Math.max(0, income);
        this.gasPerSec = Math.max(0, gasPerSec);
    }

    public long mineral() {
        return mineral;
    }

    public long gas() {
        return gas;
    }

    public long income() {
        return income;
    }

    public long gasPerSec() {
        return gasPerSec;
    }

    public int gasProductionUpgradeCount() {
        return gasProductionUpgradeCount;
    }

    public void addMineral(long amount) {
        if (amount > 0) {
            mineral += amount;
        }
    }

    public void addGas(long amount, long cap) {
        if (amount > 0) {
            gas = Math.min(cap, gas + amount);
        }
    }

    public void addIncome(long amount) {
        if (amount > 0) {
            income += amount;
        }
    }

    public boolean spendMineral(long amount) {
        if (amount < 0 || mineral < amount) {
            return false;
        }
        mineral -= amount;
        return true;
    }

    public boolean spendGas(long amount) {
        if (amount < 0 || gas < amount) {
            return false;
        }
        gas -= amount;
        return true;
    }

    public void payIncome() {
        addMineral(income);
    }

    public boolean upgradeGasProduction(EconomyConfig.GasProductionConfig config) {
        if (gasProductionUpgradeCount >= config.maxUpgradeCount()) {
            return false;
        }

        long cost = config.upgradeCost(gasProductionUpgradeCount);
        boolean spent = config.upgradeCurrency() == CurrencyType.MINERAL ? spendMineral(cost) : spendGas(cost);
        if (!spent) {
            return false;
        }

        gasPerSec += config.gasPerSecIncrease();
        gasProductionUpgradeCount++;
        return true;
    }
}
