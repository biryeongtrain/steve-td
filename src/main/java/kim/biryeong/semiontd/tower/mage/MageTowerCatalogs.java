package kim.biryeong.semiontd.tower.mage;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class MageTowerCatalogs {
    private MageTowerCatalogs() {
    }

    public static void register() {
        registerStarter(MageTowers.WIZARD, MageWizardTower::new);
        registerStarter(MageTowers.PROPHET, MageProphetTower::new);
        registerStarter(MageTowers.MAGIC_CORE, MageCoreTower::new);
        for (MageSpell spell : MageSpell.values()) {
            registerTemporary(MageTowers.spellType(spell), MageWizardTower::new);
        }
        for (TowerType type : MageTowers.predictionTypes().values()) {
            registerTemporary(type, MageProphetTower::new);
        }

        for (MageSpell spell : MageSpell.values()) {
            TowerType target = resolved(MageTowers.spellType(spell));
            ProductionTowerCatalog.linkUpgrade(
                    MageTowers.WIZARD,
                    target.id(),
                    spell.displayName() + " (마나 " + manaCost(spell) + ")",
                    target,
                    TowerBalanceRuntime.upgradeCost(MageTowers.WIZARD, target.id())
            );
        }
        MageTowers.predictionTypes().forEach((summonId, defaults) -> {
            TowerType target = resolved(defaults);
            ProductionTowerCatalog.linkUpgrade(
                    MageTowers.PROPHET,
                    target.id(),
                    target.displayName(),
                    target,
                    TowerBalanceRuntime.upgradeCost(MageTowers.PROPHET, target.id())
            );
        });
    }

    private static void registerStarter(TowerType defaults, ProductionTowerCatalog.TowerFactory factory) {
        ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(defaults), factory);
    }

    private static void registerTemporary(TowerType defaults, ProductionTowerCatalog.TowerFactory factory) {
        ProductionTowerCatalog.register(TowerBalanceRuntime.resolve(defaults), factory, 2);
    }

    private static TowerType resolved(TowerType defaults) {
        return ProductionTowerCatalog.find(defaults.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(defaults);
    }

    private static int manaCost(MageSpell spell) {
        return TowerBalanceRuntime.abilityInt(
                MageBalance.GLOBAL_ID, spell.id() + "ManaCost", spell.defaultManaCost()
        );
    }
}
