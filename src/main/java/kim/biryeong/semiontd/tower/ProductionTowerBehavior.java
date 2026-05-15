package kim.biryeong.semiontd.tower;

public record ProductionTowerBehavior(
        TowerFaction faction,
        String mechanicName,
        double splashRadius,
        double splashDamageMultiplier,
        int maxStacks,
        double damagePerStack,
        double attackSpeedPerStack,
        boolean stackOnHit,
        boolean stackOnKill,
        double killSplashRadius,
        double killSplashDamageMultiplier
) {
    public ProductionTowerBehavior {
        if (faction == null) {
            throw new IllegalArgumentException("Tower faction cannot be null.");
        }
        mechanicName = mechanicName == null || mechanicName.isBlank() ? faction.name() : mechanicName;
        splashRadius = Math.max(0.0, splashRadius);
        splashDamageMultiplier = Math.max(0.0, splashDamageMultiplier);
        maxStacks = Math.max(0, maxStacks);
        damagePerStack = Math.max(0.0, damagePerStack);
        attackSpeedPerStack = Math.max(0.0, attackSpeedPerStack);
        killSplashRadius = Math.max(0.0, killSplashRadius);
        killSplashDamageMultiplier = Math.max(0.0, killSplashDamageMultiplier);
    }
}
