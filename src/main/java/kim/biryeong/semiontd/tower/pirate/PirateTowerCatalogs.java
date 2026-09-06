package kim.biryeong.semiontd.tower.pirate;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class PirateTowerCatalogs {
    private PirateTowerCatalogs() { }
    public static void register() {
        starter(PirateTowers.ADMIRAL); register(PirateTowers.GOLDEN_ADMIRAL, 2);
        starter(PirateTowers.DECKHAND); register(PirateTowers.LOOKOUT_DECKHAND, 2); register(PirateTowers.SOUL_REAVER_DECKHAND, 3);
        starter(PirateTowers.FERRYMAN); register(PirateTowers.VETERAN_FERRYMAN, 2); register(PirateTowers.LEGENDARY_FERRYMAN, 3); register(PirateTowers.BOAT_SINGER, 2); register(PirateTowers.SWEET_BOAT_SINGER, 3);
        starter(PirateTowers.SWORDSMAN); register(PirateTowers.IRON_SWORDSMAN, 2);
        starter(PirateTowers.PARROT); register(PirateTowers.ONE_EYED_PARROT, 2); register(PirateTowers.CAPABLANCA, 3);
        starter(PirateTowers.HELMSMAN); register(PirateTowers.GUIDE, 2); register(PirateTowers.NAVIGATOR, 2); register(PirateTowers.FIRST_NAVIGATOR, 3);
        starter(PirateTowers.SHABBY_CHEST); register(PirateTowers.EMPIRE_CHEST, 2); register(PirateTowers.DEEP_CHEST, 2); register(PirateTowers.FANTASY_CHEST, 3);
        starter(PirateTowers.DROPPED_ANCHOR); register(PirateTowers.DEEP_ANCHOR, 2); register(PirateTowers.ANCIENT_ANCHOR, 3);
        link(PirateTowers.ADMIRAL, PirateTowers.GOLDEN_ADMIRAL, 2000);
        link(PirateTowers.DECKHAND, PirateTowers.LOOKOUT_DECKHAND, 175); link(PirateTowers.LOOKOUT_DECKHAND, PirateTowers.SOUL_REAVER_DECKHAND, 575);
        link(PirateTowers.FERRYMAN, PirateTowers.VETERAN_FERRYMAN, 500); link(PirateTowers.VETERAN_FERRYMAN, PirateTowers.LEGENDARY_FERRYMAN, 800); link(PirateTowers.FERRYMAN, PirateTowers.BOAT_SINGER, 400); link(PirateTowers.BOAT_SINGER, PirateTowers.SWEET_BOAT_SINGER, 1600);
        link(PirateTowers.SWORDSMAN, PirateTowers.IRON_SWORDSMAN, 1200);
        link(PirateTowers.PARROT, PirateTowers.ONE_EYED_PARROT, 1500); link(PirateTowers.ONE_EYED_PARROT, PirateTowers.CAPABLANCA, 3000);
        link(PirateTowers.HELMSMAN, PirateTowers.GUIDE, 1300); link(PirateTowers.HELMSMAN, PirateTowers.NAVIGATOR, 1500); link(PirateTowers.NAVIGATOR, PirateTowers.FIRST_NAVIGATOR, 2200);
        link(PirateTowers.SHABBY_CHEST, PirateTowers.EMPIRE_CHEST, 150); link(PirateTowers.SHABBY_CHEST, PirateTowers.DEEP_CHEST, 100); link(PirateTowers.DEEP_CHEST, PirateTowers.FANTASY_CHEST, 350);
        link(PirateTowers.DROPPED_ANCHOR, PirateTowers.DEEP_ANCHOR, 600); link(PirateTowers.DEEP_ANCHOR, PirateTowers.ANCIENT_ANCHOR, 1200);
    }
    private static void starter(TowerType type) { ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(type), PirateTower::new); }
    private static void register(TowerType type, int tier) { ProductionTowerCatalog.register(TowerBalanceRuntime.resolve(type), PirateTower::new, tier); }
    private static void link(TowerType source, TowerType target, long fallback) { TowerType resolvedSource = ProductionTowerCatalog.find(source.id()).orElseThrow().type(); TowerType resolvedTarget = ProductionTowerCatalog.find(target.id()).orElseThrow().type(); ProductionTowerCatalog.linkUpgrade(resolvedSource, target.id(), target.displayName(), resolvedTarget, TowerBalanceRuntime.upgradeCost(source, target.id(), fallback)); }
}
