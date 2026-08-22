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
        lines.add(formatPermanentHealth(defense.additionalHealth(), ""));
        if (ranged || melee) {
            lines.add(formatLifeSteal(defense.lifeSteal(), stackProgress(ranged ? stats.totalSacrifices() : stats.roundSacrifices(), lifeStealEvery, defense.lifeSteal(), defense.maximumLifeSteal())));
            lines.add(formatDamageReduction(defense.damageReduction(), stackProgress(ranged ? stats.roundSacrifices() : stats.totalSacrifices(), damageReductionEvery, defense.damageReduction(), defense.maximumDamageReduction())));
        }
        lines.add(formatPermanentDamage(combat.effectiveAttackDamage(), ""));
        if (melee) {
            lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), stackProgress(stats.roundSacrifices(), 1, combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        } else if (ranged) {
            lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), maxOnlyProgress(combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        } if (combat.showAttackRange()) {
            lines.add(formatSplashRange(combat.splashRadius(), stackProgress(ranged ? stats.totalSacrifices() : stats.roundSacrifices(), splashEvery, combat.splashRadius(), combat.maximumSplashRadius())));
        }
        if (ranged || melee) {
            lines.add(formatIncomeDebuffResistance(defense.incomeDebuffResistance(), ""));
        }
        if (stats.awakened() && ranged && stats.awakening().regenerationPerSecond() > 0.0) {
            lines.add(formatRegeneration(stats.awakening().regenerationPerSecond(), ""));
        }
        if (stats.awakened() && melee) {
            if (stats.awakening().attackDamageBonus() > 0.0) {
                lines.add(attackDamageText("🪓 추가 피해") + "<white>: </white>"
                        + attackDamageText(formatNumber(stats.awakening().attackDamageBonus())));
            }
            if (stats.awakening().movementSpeedBonus() > 0.0) {
                lines.add(formatMovementSpeed(stats.awakening().movementSpeedBonus(), ""));
            }
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
            boolean onlyCoreAlive,
            double regenerationPerSecond,
            double attackDamageBonus,
            double movementSpeedBonus
    ) {
    }

    record CombatStats(
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
            double lifeSteal,
            double maximumLifeSteal,
            double damageReduction,
            double maximumDamageReduction,
            double incomeDebuffResistance
    ) {
    }
}
