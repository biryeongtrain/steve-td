package kim.biryeong.semiontd.tower.illager;

public final class IllagerRaidState {
    private int gauge;
    private boolean active;
    private int roundStartTowerCount;
    private boolean pendingActivationSound;

    public int gauge() {
        return gauge;
    }

    public boolean active() {
        return active;
    }

    public int roundStartTowerCount() {
        return roundStartTowerCount;
    }

    public boolean pendingActivationSound() {
        return pendingActivationSound;
    }

    public void resetForRound(int roundStartTowerCount) {
        this.gauge = 0;
        this.active = false;
        this.roundStartTowerCount = Math.max(0, roundStartTowerCount);
        this.pendingActivationSound = false;
    }

    public boolean addGauge(int amount, int gaugeMax) {
        if (active || amount <= 0) {
            return false;
        }
        gauge = Math.min(Math.max(1, gaugeMax), gauge + amount);
        if (gauge >= Math.max(1, gaugeMax)) {
            active = true;
            pendingActivationSound = true;
            return true;
        }
        return false;
    }

    public boolean consumePendingActivationSound() {
        if (!pendingActivationSound) {
            return false;
        }
        pendingActivationSound = false;
        return true;
    }
}
