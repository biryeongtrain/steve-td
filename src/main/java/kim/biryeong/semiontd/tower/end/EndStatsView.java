package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.List;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.*;
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
        int regenerationStacks = Math.max(1, EndConfig.RUNTIME.integer(REGENERATION_STACKS));
        int lifeStealStacks = Math.max(1, EndConfig.RUNTIME.integer(LIFE_STEAL_STACKS));
        int damageReductionStacks = Math.max(1, EndConfig.RUNTIME.integer(DAMAGE_REDUCTION_STACKS));
        int attackSpeedStacks = Math.max(1, EndConfig.RUNTIME.integer(ATTACK_SPEED_STACKS));
        int splash1 = EndConfig.RUNTIME.integer(SPLASH_1);
        int splash2 = EndConfig.RUNTIME.integer(SPLASH_2);
        int splash3 = EndConfig.RUNTIME.integer(SPLASH_3);
        int splash4 = EndConfig.RUNTIME.integer(SPLASH_4);
        int splash5 = EndConfig.RUNTIME.integer(SPLASH_5);
        int attackRangeStacks = Math.max(1, EndConfig.RUNTIME.integer(ATTACK_RANGE_STACKS));
        ArrayList<String> lines = new ArrayList<>();
        lines.add(stateLine(stats.state()));
        lines.add(stackLine(stats.shulkerStacks(), stats.endCrystalStacks()));
        lines.add(formatPermanentHealth(defense.additionalHealth(), ""));
        lines.add(formatRegeneration(defense.currentRegeneration(), stackProgress(stats.shulkerStacks(), regenerationStacks, defense.currentRegeneration(), defense.maximumRegeneration())));
        lines.add(formatLifeSteal(defense.currentLifeSteal(), stackProgress(stats.shulkerStacks(), lifeStealStacks, defense.currentLifeSteal(), defense.maximumLifeSteal())));
        lines.add(formatDamageReduction(defense.currentDamageReduction(), stackProgress(stats.shulkerStacks(), damageReductionStacks, defense.currentDamageReduction(), defense.maximumDamageReduction())));
        lines.add(formatPermanentDamage(combat.additionalAttackDamage(), ""));
        lines.add(formatAttackSpeedReduction(combat.attackIntervalReductionTicks(), stackProgress(stats.endCrystalStacks(), attackSpeedStacks, combat.attackIntervalReductionTicks(), combat.maximumAttackIntervalReductionTicks())));
        lines.add(formatSplashRange(combat.currentSplashRadius(), splashProgress(stats.endCrystalStacks(), splash1, splash2, splash3, splash4, splash5)));
        lines.add(formatAttackRange(combat.currentAttackRange(), stackProgress(stats.endCrystalStacks(), attackRangeStacks, combat.currentAttackRange(), combat.maximumAttackRange())));
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

    record CoreStats(EndTowerState state, int shulkerStacks, int endCrystalStacks, DefenseStats defense, CombatStats combat, EvolutionStats evolution) {
    }

    record DefenseStats(double additionalHealth, double currentLifeSteal, double maximumLifeSteal, double currentDamageReduction, double maximumDamageReduction, double currentRegeneration, double maximumRegeneration) {
    }

    record CombatStats(double additionalAttackDamage, double currentSplashRadius, double maximumSplashRadius, int attackIntervalReductionTicks, int maximumAttackIntervalReductionTicks, double currentAttackRange, double maximumAttackRange) {
    }

    record EvolutionStats(boolean showBonuses, double finalDamageBonus, double dragonRangeBonus) {
    }
}
