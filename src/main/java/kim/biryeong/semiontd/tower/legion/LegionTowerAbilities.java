package kim.biryeong.semiontd.tower.legion;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.world.phys.AABB;

final class LegionTowerAbilities {
    private LegionTowerAbilities() {
    }

    static void splash(Tower tower, SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || target == null) {
            return;
        }
        double radius = ability(tower, "splashRadius");
        double ratio = ability(tower, "splashDamageRatio");
        if (radius <= 0.0 || ratio <= 0.0) {
            return;
        }
        double radiusSqr = radius * radius;
        AABB splashBox = target.getBoundingBox().inflate(radius);
        towerEntity.level().getEntities(towerEntity, splashBox, entity ->
                        entity instanceof SemionMonsterEntity splashTarget
                                && splashTarget.isAlive()
                                && splashTarget != target
                                && splashTarget.runtimeMonster() != null
                                && towerEntity.defendsLane(splashTarget.runtimeMonster().targetLaneId())
                                && splashTarget.distanceToSqr(target) <= radiusSqr
                )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(monster -> {
                    double splashDamage = damageAmount * ratio;
                    if (tower.damageTarget(towerEntity, monster, splashDamage)) {
                        tower.onKill(towerEntity, monster, splashDamage);
                    }
                });
    }

    private static double ability(Tower tower, String key) {
        return kim.biryeong.semiontd.config.TowerBalanceRuntime.ability(tower.type().id(), key);
    }
}
