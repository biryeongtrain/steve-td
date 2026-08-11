package kim.biryeong.semiontd.tower.warlock;

import java.util.ArrayList;
import java.util.List;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.tower.warlock.WarlockConfig.Ability.*;

final class WarlockStatsView {
    private WarlockStatsView() {
    }

    static List<String> core(CoreStats stats) {
        CombatStats combat = stats.combat();
        DefenseStats defense = stats.defense();
        ArrayList<String> lines = new ArrayList<>();
        boolean ranged = stats.ranged();
        boolean melee = stats.melee();
        int lifeStealEvery = ranged ? Math.max(1, WarlockConfig.RUNTIME.integer(RANGED_LIFE_EVERY)) : 1;
        int damageReductionEvery = ranged ? Math.max(1, WarlockConfig.RUNTIME.integer(RANGED_DEFENSE_THRESHOLD) + 1) : Math.max(1, WarlockConfig.RUNTIME.integer(MELEE_DEFENSE_EVERY));
        lines.add("<white>흡수한 타워: " + stats.totalSacrifices() + "기</white>");
        lines.add("<white>이번 라운드에 흡수한 타워: " + stats.roundSacrifices() + "기</white>");
        if (stats.showAwakening()) {
            lines.add(stats.awakened()
                    ? "<gray>각성 상태</gray><white>: </white><dark_purple>각성</dark_purple>"
                    : "<gray>각성 상태</gray><white>: </white><gray>미각성</gray>");
        }
        lines.add(formatPermanentHealth(defense.additionalHealth(), ""));
        if (defense.maximumRegenerationPerSecond() > 0.0) {
            lines.add(formatRegeneration(defense.regenerationPerSecond(), ""));
        }
        if (ranged || melee) {
            lines.add(formatLifeSteal(defense.lifeSteal(), stackProgress(ranged ? stats.totalSacrifices() : stats.roundSacrifices(), lifeStealEvery, defense.lifeSteal(), defense.maximumLifeSteal())));
            lines.add(formatDamageReduction(defense.damageReduction(), stackProgress(ranged ? stats.roundSacrifices() : stats.totalSacrifices(), damageReductionEvery, defense.damageReduction(), defense.maximumDamageReduction())));
        }
        lines.add(formatPermanentDamage(
                combat.effectiveAttackDamage(),
                damageProgress(combat.rawAttackDamage(), combat.effectiveAttackDamage())
        ));
        if (melee) {
            lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), stackProgress(stats.roundSacrifices(), 1, combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        } else {
            lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), maxOnlyProgress(combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        }
        if (combat.showAttackRange()) {
            lines.add(formatSplashRange(combat.splashRadius(), stackProgress(ranged ? stats.totalSacrifices() : stats.roundSacrifices(), 1, combat.splashRadius(), combat.maximumSplashRadius())));
        }
        return lines;
    }

    private static String maxOnlyProgress(double currentValue, double maximumValue) {
        return maximumValue > 0.0 && currentValue >= maximumValue - 0.0001 ? "(MAX)" : "";
    }

    private static String damageProgress(double rawDamage, double effectiveDamage) {
        return rawDamage > effectiveDamage + 0.0001
                ? "(누적 " + formatNumber(rawDamage) + ")"
                : "";
    }

    record CoreStats(
            int totalSacrifices,
            int roundSacrifices,
            boolean showAwakening,
            boolean awakened,
            boolean ranged,
            boolean melee,
            CombatStats combat,
            DefenseStats defense
    ) {
    }

    record CombatStats(
            double rawAttackDamage,
            double effectiveAttackDamage,
            int attackIntervalReductionTicks,
            int maximumAttackIntervalReductionTicks,
            double splashRadius,
            double maximumSplashRadius,
            boolean showAttackRange
    ) {
    }

    record DefenseStats(
            double additionalHealth,
            double regenerationPerSecond,
            double maximumRegenerationPerSecond,
            double lifeSteal,
            double maximumLifeSteal,
            double damageReduction,
            double maximumDamageReduction
    ) {
    }
}
