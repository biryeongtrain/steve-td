package kim.biryeong.semiontd.tower.warlock;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.tower.warlock.WarlockFormatting.warlockText;

import java.util.ArrayList;
import java.util.List;

final class WarlockStatsView {
    private WarlockStatsView() {
    }

    static List<String> core(CoreStats stats) {
        CombatStats combat = stats.combat();
        DefenseStats defense = stats.defense();
        ProgressionStats progression = stats.progression();
        ArrayList<String> lines = new ArrayList<>();
        lines.add(sacrificeLine("영구 흡수", stats.totalSacrifices()));
        lines.add(sacrificeLine("라운드 흡수", stats.roundSacrifices()));
        if (stats.showAwakening()) {
            lines.add(awakeningLine(stats.awakened(), stats.awakening()));
            if (!stats.awakened() && stats.awakening().unlocked() && stats.awakening().branchSelected()) {
                lines.add(awakeningConditionLine(stats.awakening()));
            }
        }
        lines.add(formatPermanentHealth(defense.additionalHealth(), ""));
        if (progression.showDefenseStats()) {
            lines.add(formatLifeStealEfficiency(
                    WarlockCombat.lifeStealRate(defense.lifeStealDisplayDamage(), defense.lifeSteal()),
                    stackProgress(
                            progression.lifeStealSacrifices(),
                            progression.lifeStealEvery(),
                            defense.lifeSteal(),
                            defense.maximumLifeSteal()
                    )
            ));
            lines.add(formatDamageReduction(
                    defense.damageReduction(),
                    stackProgress(
                            progression.damageReductionSacrifices(),
                            progression.damageReductionEvery(),
                            defense.damageReduction(),
                            defense.maximumDamageReduction()
                    )
            ));
        }
        lines.add(formatPermanentDamage(combat.effectiveAttackDamage(), ""));
        if (progression.showAttackSpeed()) {
            String progress = progression.attackSpeedMaximumOnly()
                    ? maxOnlyProgress(combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())
                    : stackProgress(
                            progression.attackSpeedSacrifices(),
                            progression.attackSpeedEvery(),
                            combat.attackIntervalReductionTicks(),
                            combat.maximumAttackIntervalReductionTicks()
                    );
            lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), progress));
        }
        if (combat.showSplashRadius()) {
            lines.add(formatSplashRange(
                    combat.splashRadius(),
                    stackProgress(
                            progression.splashSacrifices(),
                            progression.splashEvery(),
                            combat.splashRadius(),
                            combat.maximumSplashRadius()
                    )
            ));
        }
        if (progression.showIncomeDebuffResistance()) {
            lines.add(formatIncomeDebuffResistance(defense.incomeDebuffResistance(), ""));
        }
        if (stats.awakened()) {
            appendAwakeningBonuses(lines, stats.awakening());
        }
        return lines;
    }

    private static void appendAwakeningBonuses(List<String> lines, AwakeningStats awakening) {
        if (awakening.regenerationPerSecond() > 0.0) {
            lines.add(formatRegeneration(awakening.regenerationPerSecond(), ""));
        }
        if (awakening.attackDamageBonus() > 0.0) {
            lines.add(attackDamageText("🪓 추가 피해") + "<white>: </white>"
                    + attackDamageText(formatNumber(awakening.attackDamageBonus())));
        }
        if (awakening.movementSpeedBonus() > 0.0) {
            lines.add(formatMovementSpeed(awakening.movementSpeedBonus(), ""));
        }
    }

    private static String sacrificeLine(String label, int sacrifices) {
        return "<white>" + label + ": " + warlockText(sacrifices + "기") + "</white>";
    }

    private static String awakeningLine(boolean awakened, AwakeningStats awakening) {
        if (!awakening.unlocked()) {
            return "<white>각성 해금: " + warlockText(
                    awakening.kills() + "/" + awakening.requiredKills() + "킬"
            ) + "</white>";
        }
        if (!awakening.branchSelected()) {
            return "<white>각성 해금: " + warlockText("완료 · 분기 선택 필요") + "</white>";
        }
        if (awakened) {
            return "<white>각성 상태: " + warlockText("각성 완료") + "</white>";
        }
        return "<white>각성 해금: " + warlockText("완료") + "</white>";
    }

    private static String awakeningConditionLine(AwakeningStats awakening) {
        String survival = awakening.lastSurvivingTower() ? warlockText("충족") : "<gray>미충족</gray>";
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
            CombatStats combat,
            DefenseStats defense,
            ProgressionStats progression
    ) {
    }

    record AwakeningStats(
            long kills,
            long requiredKills,
            boolean unlocked,
            boolean branchSelected,
            double currentHealthRatio,
            double healthThreshold,
            boolean lastSurvivingTower,
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
            boolean showSplashRadius
    ) {
    }

    record DefenseStats(
            double additionalHealth,
            double lifeSteal,
            double maximumLifeSteal,
            double lifeStealDisplayDamage,
            double damageReduction,
            double maximumDamageReduction,
            double incomeDebuffResistance
    ) {
    }

    record ProgressionStats(
            boolean showDefenseStats,
            int lifeStealSacrifices,
            int lifeStealEvery,
            int damageReductionSacrifices,
            int damageReductionEvery,
            boolean showAttackSpeed,
            boolean attackSpeedMaximumOnly,
            int attackSpeedSacrifices,
            int attackSpeedEvery,
            int splashSacrifices,
            int splashEvery,
            boolean showIncomeDebuffResistance
    ) {
    }
}
