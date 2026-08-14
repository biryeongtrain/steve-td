package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class TowerCapacity {
    public static final String CONFIG_KEY = "towerSlotCost";

    private TowerCapacity() {
    }

    public static int slotCost(TowerType type) {
        if (type == null) {
            return 1;
        }
        return Math.max(1, TowerBalanceRuntime.abilityInt(type.id(), CONFIG_KEY, 1));
    }
}
