package kim.biryeong.semiontd.tower.warlock;

import java.util.List;

final class WarlockStatsAssembler {
    private final WarlockConfig config;
    private final WarlockCombat combat;

    WarlockStatsAssembler(WarlockConfig config, WarlockCombat combat) {
        this.config = config;
        this.combat = combat;
    }

    List<String> create(WarlockTower tower) {
        WarlockPath path = tower.path();
        WarlockRules.PathRule rule = config.path(path);
        var progression = tower.progressionSnapshot();
        var awakeningProgress = progression.awakening();
        boolean specialized = path.specialized();

        return WarlockStatsView.core(new WarlockStatsView.CoreStats(
                progression.totalSacrificeCount(),
                progression.roundSacrificeCount(),
                true,
                tower.awakenedThisRound(),
                new WarlockStatsView.AwakeningStats(
                        awakeningProgress.kills(),
                        awakeningProgress.requiredKills(),
                        awakeningProgress.unlocked() || tower.augmentSnapshot().has(WarlockAugments.AWAKENING),
                        specialized,
                        tower.currentHealthRatio(),
                        tower.awakeningHealthThreshold(),
                        tower.augmentSnapshot().has(WarlockAugments.AWAKENING) || tower.isLastSurvivingTower(),
                        tower.regenerationPerSecond(),
                        tower.awakeningDamageBonus(),
                        tower.awakeningMovementSpeedBonus()
                ),
                new WarlockStatsView.CombatStats(
                        tower.effectiveDamageBonus(),
                        tower.attackIntervalReduction(),
                        tower.maximumAttackIntervalReduction(),
                        tower.splashRadius(),
                        combat.maximumSplashRadius(path),
                        rule.splash().maximumRadius() > 0.0
                ),
                new WarlockStatsView.DefenseStats(
                        tower.additionalHealth(),
                        combat.lifeStealRatio(tower),
                        combat.maximumLifeSteal(path),
                        tower.damageReduction(),
                        tower.maximumDamageReduction(),
                        tower.incomeDebuffResistance()
                ),
                new WarlockStatsView.ProgressionStats(
                        specialized,
                        progression.lifeStealSacrificeCount(path),
                        rule.lifeSteal().sacrificesPerStep(),
                        progression.defenseSacrificeCount(path),
                        rule.defense().sacrificesPerStep(),
                        specialized,
                        path == WarlockPath.RANGED,
                        progression.roundSacrificeCount(),
                        1,
                        progression.splashSacrificeCount(path),
                        rule.splash().sacrificesPerStep(),
                        specialized
                )
        ));
    }
}
