package kim.biryeong.semiontd.tower.end;

import java.util.List;

final class EndStatsAssembler {
    private final EndConfig config;
    private final EndCombat combat;
    private final EndEvolutionController evolution;

    EndStatsAssembler(EndConfig config, EndCombat combat, EndEvolutionController evolution) {
        this.config = config;
        this.combat = combat;
        this.evolution = evolution;
    }

    List<String> create(EndTower tower, boolean waveActive, EndTransferSnapshot progression) {
        if (!tower.isCoreTower()) {
            double reduction = EndTowers.isShulkerLine(tower.type()) ? combat.shulkerDamageReduction(tower.type()) : 0.0;
            return EndStatsView.feeder(waveActive, EndTransferController.progress(tower), reduction);
        }
        EndTowerState state = tower.state();
        EndTransferStacks stacks = progression.stacks();
        EndTransferStats transfer = progression.resolve(config.healthScaling(), config.damageScaling());
        double projectedMaxHealth = tower.previewEvolutionMaxHealth(progression);
        int intervalReduction = Math.max(
                0,
                tower.type().attackIntervalTicks() - combat.attackInterval(tower.type(), stacks)
        );
        EndConfig.StackRule regeneration = config.regeneration();
        EndConfig.StackRule lifeSteal = config.lifeSteal();
        EndConfig.StackRule damageReduction = config.damageReduction();
        EndConfig.AttackSpeedRule attackSpeed = config.attackSpeed();
        EndConfig.SplashRule splash = config.splash();
        EndConfig.StackRule attackRange = config.attackRange();
        return EndStatsView.core(new EndStatsView.CoreStats(
                state,
                transfer.shulkerCount(),
                transfer.endCrystalCount(),
                new EndStatsView.DefenseStats(
                        transfer.permanentHealthBonus(),
                        combat.lifeStealRatio(stacks),
                        combat.maximumLifeSteal(),
                        tower.lifeStealDisplayDamage(),
                        combat.damageReduction(stacks),
                        combat.maximumDamageReduction(),
                        combat.regenerationPerSecond(stacks),
                        combat.maximumRegeneration()
                ),
                new EndStatsView.CombatStats(
                        transfer.permanentDamageBonus(),
                        combat.splashRadius(state == EndTowerState.EGG ? EndTowerState.PHANTOM : state, stacks),
                        combat.maximumSplashRadius(),
                        intervalReduction,
                        combat.maximumAttackIntervalReduction(tower.type()),
                        combat.attackRange(tower.type(), state, stacks),
                        combat.maximumAttackRange(tower.type(), state)
                ),
                new EndStatsView.EvolutionStats(
                        state != EndTowerState.PHANTOM && evolution.qualifiesForDragon(projectedMaxHealth),
                        combat.finalDamageBonus(EndTowerState.DRAGON),
                        combat.dragonRangeBonus(EndTowerState.DRAGON)
                ),
                new EndStatsView.ProgressionStats(
                        regeneration.stacksPerStep(),
                        lifeSteal.stacksPerStep(),
                        damageReduction.stacksPerStep(),
                        attackSpeed.stacksPerStep(),
                        splash.thresholds(),
                        attackRange.stacksPerStep()
                )
        ));
    }
}
