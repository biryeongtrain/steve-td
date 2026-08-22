package kim.biryeong.semiontd.tower.succubus;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class SuccubusTowerCatalogs {
    private SuccubusTowerCatalogs() {
    }

    public static void register() {
        SuccubusTowers.all().forEach(type -> register(type, SuccubusTowers.tier(type)));
        link(SuccubusTowers.DREAM_DUST_T1, SuccubusTowers.DREAM_DUST_T2);
        link(SuccubusTowers.DREAM_DUST_T2, SuccubusTowers.DREAM_DUST_T3);
        link(SuccubusTowers.SLEEPWALKER_T1, SuccubusTowers.SLEEPWALKER_T2);
        link(SuccubusTowers.SLEEPWALKER_T2, SuccubusTowers.SLEEPWALKER_T3);
        link(SuccubusTowers.LULLABY_T1, SuccubusTowers.LULLABY_T2);
        link(SuccubusTowers.LULLABY_T2, SuccubusTowers.LULLABY_T3);
        link(SuccubusTowers.NIGHTMARE_T1, SuccubusTowers.NIGHTMARE_T2);
        link(SuccubusTowers.NIGHTMARE_T2, SuccubusTowers.NIGHTMARE_T3);
    }

    private static void register(TowerType type, int tier) {
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) ProductionTowerCatalog.registerStarter(resolved, SuccubusTower::new);
        else ProductionTowerCatalog.register(resolved, SuccubusTower::new, tier);
    }

    private static void link(TowerType from, TowerType to) {
        TowerType target = ProductionTowerCatalog.find(to.id()).orElseThrow().type();
        ProductionTowerCatalog.linkUpgrade(from, to.id(), to.displayName(), target,
                TowerBalanceRuntime.upgradeCost(from, to.id()));
    }
}
