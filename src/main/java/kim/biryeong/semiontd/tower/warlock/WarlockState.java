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

    void absorbBasePermanently(
            double sacrificedHealth,
            double sacrificedDamage,
            double healthRatio,
            double damageRatio
    ) {
        permanentHealthBonus += Math.max(0.0, sacrificedHealth) * Math.max(0.0, healthRatio);
        permanentDamageBonus += Math.max(0.0, sacrificedDamage) * Math.max(0.0, damageRatio);
    }

    void absorbForRound(double sacrificedHealth, double sacrificedDamage, double ratio) {
        totalSacrificeCount++;
        roundSacrificeCount++;
        double resolvedRatio = Math.max(0.0, ratio);
        double gainedHealth = Math.max(0.0, sacrificedHealth) * resolvedRatio;
        roundHealthBonus += gainedHealth;
        roundDamageBonus += Math.max(0.0, sacrificedDamage) * resolvedRatio;
    }

    void absorbPermanently(
            double sacrificedHealth,
            double sacrificedDamage,
            double healthRatio,
            double damageRatio
    ) {
        permanentHealthBonus += Math.max(0.0, sacrificedHealth) * Math.max(0.0, healthRatio);
        permanentDamageBonus += Math.max(0.0, sacrificedDamage) * Math.max(0.0, damageRatio);
    }

    void absorbAttackInterval(int baseIntervalTicks, int sacrificedIntervalTicks, double cap) {
        if (sacrificedIntervalTicks >= baseIntervalTicks) {
            return;
        }
        double reduction = baseIntervalTicks - sacrificedIntervalTicks;
        roundIntervalReduction = Math.min(Math.max(0.0, cap), roundIntervalReduction + reduction);
    }

    void resetRound() {
        roundHealthBonus = 0.0;
        roundDamageBonus = 0.0;
        roundIntervalReduction = 0.0;
        roundSacrificeCount = 0;
        awakenedThisRound = false;
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
