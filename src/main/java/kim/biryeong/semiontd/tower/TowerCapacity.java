package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowers;

public final class TowerCapacity {
    public static final String CONFIG_KEY = "towerSlotCost";

    private TowerCapacity() {
    }

    public static int slotCost(TowerType type) {
        if (type == null) {
            return 1;
        }
        if (MagicSchoolTowers.isHogwarts(type)) {
            return 0;
        }
        if (AugmentTowers.isAugment(type)) {
            return AugmentTowers.slots(type);
        }
        if (kim.biryeong.semiontd.tower.blueprint.BlueprintTowers.isBlueprintTower(type)) {
            // 설계도 타워는 설정에 항목이 없고, 가격대가 타워 수를 정합니다.
            return kim.biryeong.semiontd.tower.blueprint.BlueprintPricing.slotCost(type.mineralCost());
        }
        return Math.max(0, TowerBalanceRuntime.abilityInt(type.id(), CONFIG_KEY, MagicSchoolTowers.isWizard(type) ? 2 : 1));
    }

    public static int slotCost(Tower tower) {
        return tower == null ? 1 : Math.max(0, tower.slotWeight()) * slotCost(tower.type());
    }
}
