package kim.biryeong.semiontd.tower.warlock;

import java.util.List;

final class WarlockStats {
    private final WarlockCombat combat;

    WarlockStats(WarlockCombat combat) {
        this.combat = combat;
    }

    List<String> create(WarlockTower tower) {
        WarlockAwakeningProgress.Snapshot awakeningProgress = WarlockAwakeningProgress.snapshot(tower.ownerPlayer());
        boolean showAwakening = WarlockTowers.isWarlockCore(tower.type());

        return WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        tower.totalSacrificeCount(),
                        tower.roundSacrificeCount(),
                        showAwakening,
                        tower.awakenedThisRound(),
                        new WarlockStatsView.AwakeningStats(
                                awakeningProgress.kills(),
                                awakeningProgress.requiredKills(),
                                awakeningProgress.unlocked(),
                                tower.currentHealthRatio(),
                                Math.max(0.0, WarlockConfig.RUNTIME.value(WarlockConfig.Ability.AWAKENING_THRESHOLD)),
                                tower.onlyCoreTowerAlive(),
                                tower.regenerationPerSecond(),
                                tower.awakeningDamageBonus(),
                                tower.awakeningMovementSpeedBonus()
                        ),
                        tower.is(WarlockTowers.RANGED_WARLOCK_TOWER),
                        tower.is(WarlockTowers.MELEE_WARLOCK_TOWER),
                        new WarlockStatsView.CombatStats(
                                tower.effectiveDamageBonus(),
                                tower.attackIntervalReduction(),
                                tower.maximumAttackIntervalReduction(),
                                tower.splashRadius(),
                                combat.maximumSplashRadius(tower),
                                combat.maximumSplashRadius(tower) > 0.0
                        ),
                        new WarlockStatsView.DefenseStats(
                                tower.additionalHealth(),
                                combat.lifeStealRatio(tower),
                                combat.maximumLifeSteal(tower),
                                tower.damageReduction(),
                                tower.maximumDamageReduction(),
                                tower.incomeDebuffResistance()
                        )
                )
        );
    }
}
