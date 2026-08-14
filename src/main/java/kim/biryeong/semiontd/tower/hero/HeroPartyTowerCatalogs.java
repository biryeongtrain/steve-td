package kim.biryeong.semiontd.tower.hero;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class HeroPartyTowerCatalogs {
    private HeroPartyTowerCatalogs() {
    }

    public static void register() {
        TowerType hero = TowerBalanceRuntime.resolve(HeroPartyTowers.HERO);
        ProductionTowerCatalog.registerStarter(hero, HeroTower::new);

        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            for (int tier = 1; tier <= 4; tier++) {
                TowerType resolved = TowerBalanceRuntime.resolve(HeroPartyTowers.companion(role, tier));
                if (tier == 1) {
                    ProductionTowerCatalog.registerStarter(resolved, HeroCompanionTower::new);
                } else {
                    ProductionTowerCatalog.register(resolved, HeroCompanionTower::new, tier);
                }
            }
        }

        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            for (int tier = 1; tier < 4; tier++) {
                TowerType from = ProductionTowerCatalog.find(HeroPartyTowers.companion(role, tier).id()).orElseThrow().type();
                TowerType to = ProductionTowerCatalog.find(HeroPartyTowers.companion(role, tier + 1).id()).orElseThrow().type();
                String upgradeId = to.id();
                ProductionTowerCatalog.linkUpgrade(
                        from,
                        upgradeId,
                        role.displayName() + " T" + (tier + 1),
                        to,
                        TowerBalanceRuntime.upgradeCost(from, upgradeId)
                );
            }
        }
    }
}
