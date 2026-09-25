package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;

final class EndCombat {
    private static final int REGENERATION_TICKS = 20;
    private final EndConfig config;

    EndCombat(EndConfig config) {
        this.config = config;
    }

    int attackInterval(TowerType type, EndTransferStacks stacks) {
        return reducedAttackInterval(type.attackIntervalTicks(), config.attackSpeed().minimumIntervalTicks(), stacks);
    }

    int adjustAttackInterval(int baseIntervalTicks, EndTransferStacks stacks) {
        return reducedAttackInterval(baseIntervalTicks, config.attackSpeed().minimumIntervalTicks(), stacks);
    }

    double attackRange(TowerType type, EndTowerState state, EndTransferStacks stacks) {
        return type.range() + attackRangeBonus(stacks) + dragonRangeBonus(state);
    }

    double modifyAttackDamage(TowerType type, double transferredDamageBonus, double damageAmount) {
        if (type.damage() <= 0.0) {return damageAmount;}
        return damageAmount * (1.0 + transferredDamageBonus / type.damage());
    }

    double lifeStealRatio(EndTransferStacks stacks) {
        return stacks.shulkerBonus(config.lifeSteal());
    }

    double maximumLifeSteal() {
        return config.lifeSteal().maximum();
    }

    double damageReduction(EndTransferStacks stacks) {
        return stacks.shulkerBonus(config.damageReduction());
    }

    double maximumDamageReduction() {
        return config.damageReduction().maximum();
    }

    double shulkerDamageReduction(TowerType type) {
        return config.towerDamageReduction(type);
    }

    double regenerationPerSecond(EndTransferStacks stacks) {
        return stacks.shulkerBonus(config.regeneration());
    }

    double maximumRegeneration() {
        return config.regeneration().maximum();
    }

    int regenerationTicks() {return REGENERATION_TICKS;}

    double splashRadius(EndTowerState state, EndTransferStacks stacks) {
        if (!state.hatched()) {return 0.0;}
        return splashRadiusForSteps(config.splash().unlockedSteps(stacks.endCrystalCount()));
    }

    double maximumSplashRadius() {return splashRadiusForSteps(config.splash().thresholds().size());}

    double resolvedSplashDamage(double resolvedOutgoingDamage) {
        if (!Double.isFinite(resolvedOutgoingDamage) || resolvedOutgoingDamage <= 0.0) {return 0.0;}
        return resolvedOutgoingDamage * config.splash().damageRatio();
    }

    int maximumAttackIntervalReduction(TowerType type) {
        EndConfig.AttackSpeedRule attackSpeed = config.attackSpeed();
        int availableReduction = Math.max(0, type.attackIntervalTicks() - attackSpeed.minimumIntervalTicks());
        if (config.roundAttackSpeed().ticksPerStep() > 0) {return availableReduction;}
        return Math.min(availableReduction, attackSpeed.maximumReductionTicks());
    }

    double attackRangeBonus(EndTransferStacks stacks) {
        return stacks.endCrystalBonus(config.attackRange());
    }

    double maximumAttackRange(TowerType type, EndTowerState state) {
        return type.range() + config.attackRange().maximum() + dragonRangeBonus(state);
    }

    double finalDamageBonus(EndTowerState state) {
        return state == EndTowerState.DRAGON ? config.dragon().finalDamageBonus() : 0.0;
    }

    double dragonRangeBonus(EndTowerState state) {
        return state == EndTowerState.DRAGON ? config.dragon().rangeBonus() : 0.0;
    }

    List<SemionMonsterEntity> resolveAttack(
            EndTower tower,
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            EndTransferStacks stacks
    ) {
        List<SemionMonsterEntity> secondaries = applySplashDamage(tower, towerEntity, target, attemptedDamage, resolvedOutgoingDamage, stacks);
        heal(towerEntity, dealtDamage * lifeStealRatio(stacks));
        return secondaries;
    }

    private double splashRadiusForSteps(int unlockedSteps) {
        EndConfig.SplashRule splash = config.splash();
        return Math.min(splash.maximumRadius(), unlockedSteps * splash.radiusPerStep());
    }

    private List<SemionMonsterEntity> applySplashDamage(
            EndTower tower,
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            EndTransferStacks stacks
    ) {
        double radius = splashRadius(tower.state(), stacks);
        double splashDamage = resolvedSplashDamage(resolvedOutgoingDamage);
        double igniteAttackDamage = resolvedSplashDamage(attemptedDamage);
        if (radius <= 0.0 || splashDamage <= 0.0) {return List.of();}
        List<SemionMonsterEntity> secondaries = new ArrayList<>();
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(tower, "splash"),
                towerEntity,
                target,
                radius,
                EndVfx.attack(tower.state(), true)
        );
        TowerAreaDamage.applyResolved(
                tower,
                towerEntity,
                request,
                ignored -> splashDamage,
                true,
                (splashTarget, dealtSplashDamage, killed) -> {
                    secondaries.add(splashTarget);
                    heal(towerEntity, dealtSplashDamage * lifeStealRatio(stacks));
                    towerEntity.applyIgniteFromBasicAttack(
                            splashTarget,
                            igniteAttackDamage,
                            killed
                    );
                }
        );
        return secondaries;
    }

    private int reducedAttackInterval(
            int baseIntervalTicks,
            int minimumInterval,
            EndTransferStacks stacks
    ) {
        long reduction = stacks.attackIntervalReduction(
                config.attackSpeed(),
                config.roundAttackSpeed()
        );
        return (int) Math.max(minimumInterval, (long) baseIntervalTicks - reduction);
    }

    private static void heal(SemionTowerEntity towerEntity, double amount) {
        if (amount > 0.0) {towerEntity.healTarget(towerEntity, amount);}
    }

}
