package kim.biryeong.semiontd.tower.legion;

import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;

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
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(tower, "splash"), towerEntity, target, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.apply(tower, towerEntity, request, monster -> damageAmount * ratio, true);
    }

    private static double ability(Tower tower, String key) {
        return kim.biryeong.semiontd.config.TowerBalanceRuntime.ability(tower.type().id(), key);
    }
}
