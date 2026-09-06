package kim.biryeong.semiontd.entity.monster;

/** Support actually performed by one logical monster, retained across entity recreation. */
public final class MonsterSupportMetrics {
    private long healingAttempts;
    private double requestedHealing;
    private double effectiveHealing;
    private double overhealing;
    private final double[] granted = new double[2];
    private final double[] absorbed = new double[2];
    private final double[] expired = new double[2];

    public void recordHealing(double requested, double effective) {
        if (!Double.isFinite(requested) || requested <= 0.0) {
            return;
        }
        double actual = Math.max(0.0, Math.min(requested, effective));
        healingAttempts++;
        requestedHealing += requested;
        effectiveHealing += actual;
        overhealing += requested - actual;
    }

    void recordShieldGranted(DamageType type, double amount) {
        granted[index(type)] += amount;
    }

    void recordShieldAbsorbed(DamageType type, double amount) {
        absorbed[index(type)] += amount;
    }

    void recordShieldExpired(DamageType type, double amount) {
        expired[index(type)] += amount;
    }

    private static int index(DamageType type) {
        return switch (type) {
            case PHYSICAL -> 0;
            case MAGIC -> 1;
            case TRUE -> throw new IllegalArgumentException("True damage has no shield.");
        };
    }

    public Snapshot snapshot() {
        return new Snapshot(healingAttempts, requestedHealing, effectiveHealing, overhealing,
                granted[0], absorbed[0], expired[0], granted[1], absorbed[1], expired[1]);
    }

    public record Snapshot(long healingAttempts, double requestedHealing, double effectiveHealing,
            double overhealing, double physicalShieldGranted, double physicalShieldAbsorbed,
            double physicalShieldExpired, double magicShieldGranted, double magicShieldAbsorbed,
            double magicShieldExpired) {
        public static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public Snapshot plus(Snapshot other) {
            return new Snapshot(healingAttempts + other.healingAttempts,
                    requestedHealing + other.requestedHealing, effectiveHealing + other.effectiveHealing,
                    overhealing + other.overhealing, physicalShieldGranted + other.physicalShieldGranted,
                    physicalShieldAbsorbed + other.physicalShieldAbsorbed, physicalShieldExpired + other.physicalShieldExpired,
                    magicShieldGranted + other.magicShieldGranted, magicShieldAbsorbed + other.magicShieldAbsorbed,
                    magicShieldExpired + other.magicShieldExpired);
        }
    }
}
