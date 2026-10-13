package kim.biryeong.semiontd.tower.warlock;

import kim.biryeong.semiontd.tower.Tower;

final class WarlockSacrifice {
    private WarlockSacrifice() {
    }

    static Snapshot snapshot(Tower tower) {
        return new Snapshot(
                tower.currentMaxHealth(),
                tower.sacrificeAttackDamage(),
                tower.type().attackIntervalTicks()
        );
    }

    static Gain calculate(
            WarlockPath path,
            Snapshot snapshot,
            WarlockRules.PathRule pathRule,
            WarlockRules.CombatRule combat,
            int baseAttackIntervalTicks
    ) {
        WarlockRules.AbsorptionRule absorption = pathRule.absorption();
        double permanentHealth = snapshot.maxHealth() * absorption.permanentHealthRatio();
        double permanentDamage = snapshot.attackDamage() * absorption.permanentDamageRatio();
        double roundHealth = snapshot.maxHealth() * absorption.roundStatRatio();
        double roundDamage = snapshot.attackDamage() * absorption.roundStatRatio();
        double intervalReduction = path == WarlockPath.RANGED
                ? Math.max(0, baseAttackIntervalTicks - snapshot.attackIntervalTicks())
                : 0.0;
        return new Gain(
                permanentHealth,
                permanentDamage,
                roundHealth,
                roundDamage,
                intervalReduction,
                combat.maximumIntervalReductionTicks()
        );
    }

    static boolean commit(boolean killSucceeded, WarlockState state, Gain gain) {
        if (!killSucceeded || state == null || gain == null) {
            return false;
        }
        state.recordSacrifice(gain);
        return true;
    }

    record Snapshot(double maxHealth, double attackDamage, int attackIntervalTicks) {
        Snapshot {
            maxHealth = finiteNonNegative(maxHealth);
            attackDamage = finiteNonNegative(attackDamage);
            attackIntervalTicks = Math.max(0, attackIntervalTicks);
        }
    }

    record Gain(
            double permanentHealth,
            double permanentDamage,
            double roundHealth,
            double roundDamage,
            double intervalReduction,
            double maximumIntervalReduction
    ) {
        Gain {
            permanentHealth = finiteNonNegative(permanentHealth);
            permanentDamage = finiteNonNegative(permanentDamage);
            roundHealth = finiteNonNegative(roundHealth);
            roundDamage = finiteNonNegative(roundDamage);
            intervalReduction = finiteNonNegative(intervalReduction);
            maximumIntervalReduction = finiteNonNegative(maximumIntervalReduction);
        }
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
