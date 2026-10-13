package kim.biryeong.semiontd.tower;

public final class DamageLifeSteal {
    private DamageLifeSteal() {
    }

    public static double efficiency(double referenceDamage, double fullEfficiencyDamage) {
        if (!Double.isFinite(fullEfficiencyDamage) || fullEfficiencyDamage <= 0.0) {return 0.0;}
        if (!Double.isFinite(referenceDamage)) {return 0.01;}
        if (referenceDamage <= fullEfficiencyDamage) {return 1.0;}
        return Math.clamp(fullEfficiencyDamage / referenceDamage, 0.01, 1.0);
    }

    public static double rate(double referenceDamage, double baseRate, double fullEfficiencyDamage) {
        if (!Double.isFinite(baseRate) || baseRate <= 0.0) {return 0.0;}
        return baseRate * efficiency(referenceDamage, fullEfficiencyDamage);
    }

    public static double healing(double dealtDamage, double referenceDamage, double baseRate, double fullEfficiencyDamage) {
        if (!Double.isFinite(dealtDamage) || dealtDamage <= 0.0
                || !Double.isFinite(referenceDamage) || referenceDamage <= 0.0) {return 0.0;}
        double amount = dealtDamage * rate(referenceDamage, baseRate, fullEfficiencyDamage);
        return Double.isFinite(amount) ? amount : 0.0;
    }
}
