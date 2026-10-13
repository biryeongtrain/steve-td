package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.tower.DamageLifeSteal;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.tower.end.EndFormatting.endText;

final class EndStatsView {
    private EndStatsView() {
    }

    static List<String> feeder(boolean waveActive, double transferProgress, double damageReduction) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add(waveActive ? "<white>힘 전달 진행률: " + endText(format(transferProgress, "percent")) + "</white>" : "<gray>" + endText("엔더 드래곤") + "에게 힘 전달 대기 중</gray>");
        if (damageReduction > 0.0) {lines.add(formatDamageReduction(damageReduction, ""));}
        return lines;
    }

    static List<String> core(CoreStats stats) {
        CombatStats combat = stats.combat();
        DefenseStats defense = stats.defense();
        EvolutionStats evolution = stats.evolution();
        ProgressionStats progression = stats.progression();
        List<Integer> splashThresholds = progression.splashThresholds();
        ArrayList<String> lines = new ArrayList<>();
        lines.add(stateLine(stats.state()));
        lines.add(stackLine(stats.shulkerStacks(), stats.endCrystalStacks()));
        lines.add(formatPermanentHealth(defense.additionalHealth(), ""));
        lines.add(formatRegeneration(defense.currentRegeneration(), stackProgress(stats.shulkerStacks(), progression.regenerationStacks(), defense.currentRegeneration(), defense.maximumRegeneration())));
        double lifeStealEfficiency = DamageLifeSteal.rate(defense.lifeStealDisplayDamage(),
                EndCombat.lifeStealProgress(defense.currentLifeSteal(), defense.maximumLifeSteal()), 30.0);
        lines.add(formatLifeStealEfficiency(lifeStealEfficiency, stackProgress(stats.shulkerStacks(), progression.lifeStealStacks(), defense.currentLifeSteal(), defense.maximumLifeSteal())));
        lines.add(formatDamageReduction(defense.currentDamageReduction(), stackProgress(stats.shulkerStacks(), progression.damageReductionStacks(), defense.currentDamageReduction(), defense.maximumDamageReduction())));
        lines.add(formatPermanentDamage(combat.additionalAttackDamage(), ""));
        lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), stackProgress(stats.endCrystalStacks(), progression.attackSpeedStacks(), combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        lines.add(formatSplashRange(combat.currentSplashRadius(), splashProgress(stats.endCrystalStacks(), splashThresholds.get(0), splashThresholds.get(1), splashThresholds.get(2), splashThresholds.get(3), splashThresholds.get(4))));
        lines.add(formatAttackRange(combat.currentAttackRange(), stackProgress(stats.endCrystalStacks(), progression.attackRangeStacks(), combat.currentAttackRange(), combat.maximumAttackRange())));
        if (evolution.showBonuses()) {
            lines.add(formatFinalDamage(evolution.finalDamageBonus(), ""));
            lines.add(formatBonusRange(evolution.dragonRangeBonus(), ""));
        }
        return lines;
    }

    private static String stateLine(EndTowerState state) {
        String stateName = switch (state) {
            case EGG -> "드래곤 알";
            case PHANTOM -> "아기 드래곤";
            case DRAGON -> "엔더 드래곤";
        };
        return "<white>상태: " + endText(stateName) + "</white>";
    }

    private static String stackLine(int shulkerStacks, int endCrystalStacks) {
        return "<white>" + healthText("셜커") + " 계열, " + attackDamageText("엔드 수정") + " 계열 스택: " + healthText(Integer.toString(shulkerStacks)) + " <dark_gray>|</dark_gray> " + attackDamageText(Integer.toString(endCrystalStacks)) + "</white>";
    }

    record CoreStats(EndTowerState state, int shulkerStacks, int endCrystalStacks, DefenseStats defense, CombatStats combat, EvolutionStats evolution, ProgressionStats progression) {
    }

    record DefenseStats(double additionalHealth, double currentLifeSteal, double maximumLifeSteal, double lifeStealDisplayDamage, double currentDamageReduction, double maximumDamageReduction, double currentRegeneration, double maximumRegeneration) {
    }

    record CombatStats(double additionalAttackDamage, double currentSplashRadius, double maximumSplashRadius, int attackIntervalReductionTicks, int maximumAttackIntervalReductionTicks, double currentAttackRange, double maximumAttackRange) {
    }

    record EvolutionStats(boolean showBonuses, double finalDamageBonus, double dragonRangeBonus) {
    }

    record ProgressionStats(int regenerationStacks, int lifeStealStacks, int damageReductionStacks, int attackSpeedStacks, List<Integer> splashThresholds, int attackRangeStacks) {
        ProgressionStats {
            splashThresholds = List.copyOf(splashThresholds);
            if (splashThresholds.size() != 5) {
                throw new IllegalArgumentException("End splash progression requires exactly five thresholds");
            }
        }
    }
}
