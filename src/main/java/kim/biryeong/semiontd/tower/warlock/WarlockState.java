package kim.biryeong.semiontd.tower.warlock;

final class WarlockState {
    private double permanentHealthBonus;
    private double permanentDamageBonus;
    private double roundHealthBonus;
    private double roundDamageBonus;
    private double roundIntervalReduction;
    private int totalSacrificeCount;
    private int roundSacrificeCount;
    private boolean awakenedThisRound;

    void recordSacrifice(WarlockSacrifice.Gain gain) {
        if (gain == null) {
            return;
        }
        totalSacrificeCount++;
        roundSacrificeCount++;
        permanentHealthBonus += gain.permanentHealth();
        permanentDamageBonus += gain.permanentDamage();
        roundHealthBonus += gain.roundHealth();
        roundDamageBonus += gain.roundDamage();
        roundIntervalReduction = Math.min(
                gain.maximumIntervalReduction(),
                roundIntervalReduction + gain.intervalReduction()
        );
    }

    void resetRound() {
        roundHealthBonus = 0.0;
        roundDamageBonus = 0.0;
        roundIntervalReduction = 0.0;
        roundSacrificeCount = 0;
        awakenedThisRound = false;
    }

    void shareGrowth(WarlockSacrifice.Gain gain) {
        permanentHealthBonus += gain.permanentHealth();
        permanentDamageBonus += gain.permanentDamage();
        roundHealthBonus += gain.roundHealth();
        roundDamageBonus += gain.roundDamage();
    }

    void copyFrom(WarlockState source) {
        if (source == null) {
            return;
        }
        permanentHealthBonus = source.permanentHealthBonus;
        permanentDamageBonus = source.permanentDamageBonus;
        roundHealthBonus = source.roundHealthBonus;
        roundDamageBonus = source.roundDamageBonus;
        roundIntervalReduction = source.roundIntervalReduction;
        totalSacrificeCount = source.totalSacrificeCount;
        roundSacrificeCount = source.roundSacrificeCount;
        awakenedThisRound = source.awakenedThisRound;
    }

    boolean awaken() {
        if (awakenedThisRound) {
            return false;
        }
        awakenedThisRound = true;
        return true;
    }

    boolean awakenedThisRound() {
        return awakenedThisRound;
    }

    double permanentHealthBonus() {
        return permanentHealthBonus;
    }

    double permanentDamageBonus() {
        return permanentDamageBonus;
    }

    double roundHealthBonus() {
        return roundHealthBonus;
    }

    double roundDamageBonus() {
        return roundDamageBonus;
    }

    double roundIntervalReduction() {
        return roundIntervalReduction;
    }

    int totalSacrificeCount() {
        return totalSacrificeCount;
    }

    int roundSacrificeCount() {
        return roundSacrificeCount;
    }
}
