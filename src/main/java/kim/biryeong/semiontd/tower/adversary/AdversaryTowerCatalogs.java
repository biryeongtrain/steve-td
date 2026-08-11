package kim.biryeong.semiontd.tower.adversary;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class AdversaryTowerCatalogs {
    private AdversaryTowerCatalogs() {
    }

    public static void register() {
        synchronized (ProductionTowerCatalog.class) {
            registerAll();
        }
    }

    private static void registerAll() {
        registerFox();
        for (RivalKind kind : RivalKind.values()) {
            registerRival(AdversaryTowers.baseRival(kind), 1);
            registerRival(AdversaryTowers.enhancedRival(kind), 2);
        }
        for (RivalKind kind : RivalKind.values()) {
            linkEnhancement(kind);
        }
    }

    private static void registerFox() {
        TowerType type = AdversaryTowers.FOX;
        if (ProductionTowerCatalog.find(type.id()).isEmpty()) {
            ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(type), AdversaryFoxTower::new);
        }
    }

    private static void registerRival(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolved, AdversaryRivalTower::new);
            return;
        }
        ProductionTowerCatalog.register(resolved, AdversaryRivalTower::new, tier);
    }

    private static void linkEnhancement(RivalKind kind) {
        TowerType from = AdversaryTowers.baseRival(kind);
        TowerType to = AdversaryTowers.enhancedRival(kind);
        if (ProductionTowerCatalog.upgrade(from, to.id()).isPresent()) {
            return;
        }
        TowerType registeredTarget = ProductionTowerCatalog.find(to.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(to);
        ProductionTowerCatalog.linkUpgrade(
                from,
                to.id(),
                "강화된 " + kind.displayName() + " 숙적",
                registeredTarget,
                TowerBalanceRuntime.upgradeCost(from, to.id())
        );
    }
}
