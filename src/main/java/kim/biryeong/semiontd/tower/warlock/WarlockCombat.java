package kim.biryeong.semiontd.tower.warlock;

import static kim.biryeong.semiontd.tower.warlock.WarlockConfig.Ability.*;

import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;

final class WarlockCombat {
    private final WarlockConfig config;

    WarlockCombat(WarlockConfig config) {
        this.config = config;
    }

    double splashRadius(WarlockTower tower) {
        if (tower.is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return splashRadiusForCount(tower.totalSacrificeCount());
        }
        if (tower.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return meleeSplashRadiusForCount(tower.roundSacrificeCount());
        }
        return 0.0;
    }

    double splashRadiusForCount(int sacrificeCount) {
        int every = config.integer(RANGED_SPLASH_EVERY);
        if (every <= 0) {
            return 0.0;
        }
        double step = Math.max(0.0, config.value(RANGED_SPLASH_STEP));
        return Math.min(configuredSplashCap(), (Math.max(0, sacrificeCount) / every) * step);
    }

    double maximumSplashRadius(WarlockTower tower) {
        if (tower.is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return configuredSplashCap();
        }
        if (tower.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return configuredMeleeSplashCap();
        }
        return 0.0;
    }

    private double configuredSplashCap() {
        return Math.max(0.0, config.value(RANGED_SPLASH_CAP));
    }

    double meleeSplashRadiusForCount(int roundSacrificeCount) {
        double step = Math.max(0.0, config.value(MELEE_SPLASH_STEP));
        return Math.min(configuredMeleeSplashCap(), Math.max(0, roundSacrificeCount) * step);
    }

    private double configuredMeleeSplashCap() {
        return Math.max(0.0, config.value(MELEE_SPLASH_CAP));
    }

    double lifeStealRatio(WarlockTower tower) {
        return lifeStealRatioForCounts(
                tower.type(),
                tower.totalSacrificeCount(),
                tower.roundSacrificeCount(),
                tower.onlyCoreTowerAlive()
        );
    }

    double lifeStealRatioForCounts(TowerType type, int totalSacrificeCount, int roundSacrificeCount) {
        int sacrificeCount = isMelee(type) ? roundSacrificeCount : totalSacrificeCount;
        return lifeStealRatioForCount(type, sacrificeCount);
    }

    double lifeStealRatioForCounts(
            TowerType type,
            int totalSacrificeCount,
            int roundSacrificeCount,
            boolean onlyCoreTowerAlive
    ) {
        if (isMelee(type) && !onlyCoreTowerAlive) {
            return 0.0;
        }
        return lifeStealRatioForCounts(type, totalSacrificeCount, roundSacrificeCount);
    }

    double lifeStealRatioForCount(TowerType type, int sacrificeCount) {
        double ratio = 0.0;
        if (isRanged(type)) {
            int every = config.integer(RANGED_LIFE_EVERY);
            if (every > 0) {
                ratio = (Math.max(0, sacrificeCount) / every) * config.value(RANGED_LIFE_STEP);
            }
        } else if (isMelee(type)) {
            ratio = Math.max(0, sacrificeCount) * config.value(MELEE_LIFE_STEP);
        }
        return Math.min(maximumLifeSteal(type), Math.max(0.0, ratio));
    }

    double maximumLifeSteal(WarlockTower tower) {
        return maximumLifeSteal(tower.type());
    }

    int meleeAttackIntervalReduction(WarlockTower tower) {
        return meleeAttackIntervalReductionForCount(tower.type(), tower.roundSacrificeCount());
    }

    int meleeAttackIntervalReductionForCount(TowerType type, int roundSacrificeCount) {
        if (!isMelee(type)) {
            return 0;
        }
        int reduction = (int) Math.floor(Math.max(0, roundSacrificeCount)
                * Math.max(0.0, config.value(MELEE_SPEED_STEP)));
        return Math.min(maximumAttackIntervalReduction(), reduction);
    }

    int minimumAttackIntervalTicks() {
        return Math.max(1, config.integer(MIN_INTERVAL));
    }

    int maximumAttackIntervalReduction() {
        return Math.max(0, config.integer(SPEED_CAP));
    }

    private double maximumLifeSteal(TowerType type) {
        if (isRanged(type)) {
            return Math.max(0.0, config.value(RANGED_LIFE_CAP));
        }
        if (isMelee(type)) {
            return Math.max(0.0, config.value(MELEE_LIFE_CAP));
        }
        return 0.0;
    }

    void resolveAttack(
            WarlockTower tower,
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage
    ) {
        if (towerEntity == null || target == null) {
            return;
        }
        double lifeSteal = lifeStealRatio(tower);
        applySplash(
                tower,
                towerEntity,
                target,
                attemptedDamage,
                resolvedOutgoingDamage,
                lifeSteal
        );
        tower.heal(towerEntity, Math.max(0.0, dealtDamage) * lifeSteal);
    }

    double resolvedSplashDamage(TowerType type, double resolvedOutgoingDamage) {
        if (type == null || !Double.isFinite(resolvedOutgoingDamage) || resolvedOutgoingDamage <= 0.0) {
            return 0.0;
        }
        double ratio = isRanged(type)
                ? config.value(RANGED_SPLASH_DAMAGE)
                : isMelee(type) ? config.value(MELEE_SPLASH_DAMAGE) : 0.0;
        return resolvedOutgoingDamage * Math.max(0.0, ratio);
    }

    private void applySplash(
            WarlockTower tower,
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double lifeSteal
    ) {
        double radius = tower.splashRadius();
        double splashDamage = resolvedSplashDamage(tower.type(), resolvedOutgoingDamage);
        double igniteAttackDamage = resolvedSplashDamage(tower.type(), attemptedDamage);
        if (radius <= 0.0 || splashDamage <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(tower, "splash"), towerEntity, target, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.applyResolved(
                tower,
                towerEntity,
                request,
                ignored -> splashDamage,
                true,
                (splashTarget, dealtSplashDamage, killed) -> {
                    tower.heal(towerEntity, dealtSplashDamage * lifeSteal);
                    towerEntity.applyIgniteFromBasicAttack(
                            splashTarget,
                            igniteAttackDamage,
                            killed
                    );
                }
        );
    }

    private static boolean isRanged(TowerType type) {
        return type != null && type.id().equals(WarlockTowers.RANGED_WARLOCK_TOWER.id());
    }

    private static boolean isMelee(TowerType type) {
        return type != null && type.id().equals(WarlockTowers.MELEE_WARLOCK_TOWER.id());
    }
}
