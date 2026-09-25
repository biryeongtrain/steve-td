package kim.biryeong.semiontd.tower.illager;

public final class IllagerRaidState {
    private int gauge;
    private boolean active;
    private int roundStartTowerCount;
    private boolean pendingActivationEffects;
    private int grandRaidThreshold;
    private int extraGauge;
    private int pendingVolleys;

    public int gauge() {
        return gauge;
    }

    public boolean active() {
        return active;
    }

    public int roundStartTowerCount() {
        return roundStartTowerCount;
    }

    public boolean pendingActivationEffects() {
        return pendingActivationEffects;
    }

    public void resetForRound(int roundStartTowerCount) {
        this.gauge = 0;
        this.active = false;
        this.roundStartTowerCount = Math.max(0, roundStartTowerCount);
        this.pendingActivationEffects = false;
        this.grandRaidThreshold = 0;
        this.extraGauge = 0;
        this.pendingVolleys = 0;
    }

    public void enableGrandRaid(int threshold) {
        grandRaidThreshold = Math.max(1, threshold);
    }

    public int extraGauge() {return extraGauge;}

    public int grandRaidThreshold() {return grandRaidThreshold;}

    public void addExtraGauge(int amount) {
        if (!active || grandRaidThreshold <= 0 || amount <= 0) {return;}
        extraGauge += amount;
        pendingVolleys += extraGauge / grandRaidThreshold;
        extraGauge %= grandRaidThreshold;
    }

    public int consumePendingVolleys() {
        int volleys = pendingVolleys;
        pendingVolleys = 0;
        return volleys;
    }

    public boolean addGauge(int amount, int gaugeMax) {
        if (active || amount <= 0) {
            return false;
        }
        gauge = Math.min(Math.max(1, gaugeMax), gauge + amount);
        if (gauge >= Math.max(1, gaugeMax)) {
            active = true;
            pendingActivationEffects = true;
            return true;
        }
        return false;
    }

    public boolean consumePendingActivationEffects() {
        if (!pendingActivationEffects) {
            return false;
        }
        pendingActivationEffects = false;
        return true;
    }
}
