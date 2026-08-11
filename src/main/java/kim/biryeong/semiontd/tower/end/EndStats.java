package kim.biryeong.semiontd.tower.end;

import java.util.List;

final class EndStats {
    private final EndCombat combat;

    EndStats(EndCombat combat) {
        this.combat = combat;
    }

    List<String> create(EndTower tower, boolean waveActive) {
        if (!tower.isCoreTower()) {
            double reduction = EndTowers.isShulkerLine(tower.type()) ? combat.shulkerDamageReduction(tower.type()) : 0.0;
            return EndStatsView.feeder(waveActive, tower.transferProgress(), reduction);
        }
        double maxHealth = tower.isEgg() ? tower.previewHatchedMaxHealth() : tower.currentMaxHealth();
        int intervalReduction = Math.max(0, tower.type().attackIntervalTicks() - tower.previewHatchedAttackIntervalTicks());
        return EndStatsView.core(new EndStatsView.CoreStats(
                tower.state(),
                tower.shulkerCount(),
                tower.endCrystalCount(),
                new EndStatsView.DefenseStats(
                        tower.permanentHealthBonus(),
                        combat.lifeStealRatio(),
                        combat.maximumLifeSteal(),
                        combat.damageReduction(),
                        combat.maximumDamageReduction(),
                        combat.regenerationPerSecond(),
                        combat.maximumRegeneration()
                ),
                new EndStatsView.CombatStats(
                        tower.permanentDamageBonus(),
                        combat.splashRadius(true),
                        combat.maximumSplashRadius(),
                        intervalReduction,
                        combat.maximumAttackIntervalReduction(tower.type()),
                        tower.previewHatchedAttackRange(),
                        combat.maximumAttackRange(tower.type(), tower.isDragon())
                ),
                new EndStatsView.EvolutionStats(
                        (tower.isEgg() || tower.isDragon()) && maxHealth >= combat.dragonEvolutionHealth(),
                        combat.finalDamageBonus(true),
                        combat.dragonRangeBonus(true)
                )
        ));
    }
}
