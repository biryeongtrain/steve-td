package kim.biryeong.semiontd.tower.warlock;

import java.util.ArrayList;
import java.util.List;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.tower.warlock.WarlockConfig.Ability.*;
import static kim.biryeong.semiontd.tower.warlock.WarlockFormatting.warlockText;

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
        int splashEvery = ranged ? Math.max(1, WarlockConfig.RUNTIME.integer(RANGED_SPLASH_EVERY)) : 1;
        int damageReductionEvery = ranged ? Math.max(1, WarlockConfig.RUNTIME.integer(RANGED_DEFENSE_THRESHOLD) + 1) : Math.max(1, WarlockConfig.RUNTIME.integer(MELEE_DEFENSE_EVERY));
        lines.add(sacrificeLine("영구 흡수", stats.totalSacrifices()));
        lines.add(sacrificeLine("라운드 흡수", stats.roundSacrifices()));
        if (stats.showAwakening()) {
            lines.add(awakeningLine(stats.awakened(), ranged || melee, stats.awakening()));
            if (!stats.awakened() && stats.awakening().unlocked() && (ranged || melee)) {
                lines.add(awakeningConditionLine(stats.awakening()));
            }
        }
        lines.add(formatPermanentHealth(defense.additionalHealth(), scalingProgress(defense.rawAbsorbedHealth(), defense.effectiveAbsorbedHealth())));
        if (defense.maximumRegenerationPerSecond() > 0.0) {
            lines.add(formatRegeneration(defense.regenerationPerSecond(), ""));
        } if (ranged || melee) {
            lines.add(formatLifeSteal(defense.lifeSteal(), stackProgress(ranged ? stats.totalSacrifices() : stats.roundSacrifices(), lifeStealEvery, defense.lifeSteal(), defense.maximumLifeSteal())));
            lines.add(formatDamageReduction(defense.damageReduction(), stackProgress(ranged ? stats.roundSacrifices() : stats.totalSacrifices(), damageReductionEvery, defense.damageReduction(), defense.maximumDamageReduction())));
        } lines.add(formatPermanentDamage(
                combat.effectiveAttackDamage(),
                damageProgress(combat.rawAttackDamage(), combat.effectiveAttackDamage())
        ));
        if (melee) {
            lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), stackProgress(stats.roundSacrifices(), 1, combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        } else if (ranged) {
            lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), maxOnlyProgress(combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        } if (combat.showAttackRange()) {
            lines.add(formatSplashRange(combat.splashRadius(), stackProgress(ranged ? stats.totalSacrifices() : stats.roundSacrifices(), splashEvery, combat.splashRadius(), combat.maximumSplashRadius())));
        }
        return lines;
    }

    private static String sacrificeLine(String label, int sacrifices) {
        return "<white>" + label + ": " + warlockText(sacrifices + "기") + "</white>";
    }

    private static String awakeningLine(boolean awakened, boolean branchSelected, AwakeningStats awakening) {
        if (!awakening.unlocked()) {
            return "<white>각성 해금: " + warlockText(
                    awakening.kills() + "/" + awakening.requiredKills() + "킬"
            ) + "</white>";
        }
        if (!branchSelected) {
            return "<white>각성 해금: " + warlockText("완료 · 분기 선택 필요") + "</white>";
        }
        if (awakened) {
            return "<white>각성 상태: " + warlockText("각성 완료") + "</white>";
        }
        return "<white>각성 해금: " + warlockText("완료") + "</white>";
    }

    private static String awakeningConditionLine(AwakeningStats awakening) {
        String survival = awakening.onlyCoreAlive() ? warlockText("충족") : "<gray>미충족</gray>";
        String health = format(awakening.currentHealthRatio(), "percent")
                + " / " + format(awakening.healthThreshold(), "percent");
        return "<white>각성 조건: 최후 생존 " + survival + " · 체력 " + health + "</white>";
    }

    private static String maxOnlyProgress(double currentValue, double maximumValue) {
        return maximumValue > 0.0 && currentValue >= maximumValue - 0.0001 ? "(MAX)" : "";
    }

    private static String damageProgress(double rawDamage, double effectiveDamage) {
        return scalingProgress(rawDamage, effectiveDamage);
    }

    private static String scalingProgress(double rawValue, double effectiveValue) {
        return rawValue > effectiveValue + 0.0001
                ? "(누적 " + formatNumber(rawValue) + ")"
                : "";
    }

    record CoreStats(
            int totalSacrifices,
            int roundSacrifices,
            boolean showAwakening,
            boolean awakened,
            AwakeningStats awakening,
            boolean ranged,
            boolean melee,
            CombatStats combat,
            DefenseStats defense
    ) {
    }

    record AwakeningStats(
            long kills,
            long requiredKills,
            boolean unlocked,
            double currentHealthRatio,
            double healthThreshold,
            boolean onlyCoreAlive
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
            double rawAbsorbedHealth,
            double effectiveAbsorbedHealth,
            double regenerationPerSecond,
            double maximumRegenerationPerSecond,
            double lifeSteal,
            double maximumLifeSteal,
            double damageReduction,
            double maximumDamageReduction
    ) {
    }
}
