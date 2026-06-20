package kim.biryeong.semiontd.tower.illager;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.IllagerTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class IllagerTowerCatalogs {
    private IllagerTowerCatalogs() {
    }

    public static void register() {
        registerTower(IllagerTowers.T1_VINDICATOR, IllagerTower::new, 1);
        registerTower(IllagerTowers.T2_VINDICATOR_CAPTAIN, IllagerTower::new, 2);
        registerTower(IllagerTowers.T3_RAVAGER, IllagerTower::new, 3);

        registerTower(IllagerTowers.T1_PILLAGER, IllagerTower::new, 1);
        registerTower(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE, singleTargetFactory(), 2);
        registerTower(IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH, IllagerTower::new, 2);
        registerTower(IllagerTowers.T3_EVOKER_SINGLE, singleTargetFactory(), 3);
        registerTower(IllagerTowers.T3_EVOKER_SPLASH, IllagerTower::new, 3);

        registerTower(IllagerTowers.T1_VEX, IllagerTower::new, 1);
        registerTower(IllagerTowers.T2_WITCH_LOW, lowHealthFactory(), 2);
        registerTower(IllagerTowers.T2_WITCH_HIGH, highHealthFactory(), 2);
        registerTower(IllagerTowers.T3_ILLUSIONER_LOW, lowHealthFactory(), 3);
        registerTower(IllagerTowers.T3_ILLUSIONER_HIGH, highHealthFactory(), 3);

        link(IllagerTowers.T1_VINDICATOR, IllagerTowers.T2_VINDICATOR_CAPTAIN.id(), "변명자 대장타워", IllagerTowers.T2_VINDICATOR_CAPTAIN);
        link(IllagerTowers.T2_VINDICATOR_CAPTAIN, IllagerTowers.T3_RAVAGER.id(), "파괴수타워", IllagerTowers.T3_RAVAGER);

        link(IllagerTowers.T1_PILLAGER, IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE.id(), "약탈자 대장타워(단일)", IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE);
        link(IllagerTowers.T1_PILLAGER, IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id(), "약탈자 대장타워(광역)", IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH);
        link(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE, IllagerTowers.T3_EVOKER_SINGLE.id(), "소환사타워(단일)", IllagerTowers.T3_EVOKER_SINGLE);
        link(IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH, IllagerTowers.T3_EVOKER_SPLASH.id(), "소환사타워(광역)", IllagerTowers.T3_EVOKER_SPLASH);

        link(IllagerTowers.T1_VEX, IllagerTowers.T2_WITCH_LOW.id(), "마녀타워(약자 표식)", IllagerTowers.T2_WITCH_LOW);
        link(IllagerTowers.T1_VEX, IllagerTowers.T2_WITCH_HIGH.id(), "마녀타워(강자 표식)", IllagerTowers.T2_WITCH_HIGH);
        link(IllagerTowers.T2_WITCH_LOW, IllagerTowers.T3_ILLUSIONER_LOW.id(), "환술사타워(약자 표식)", IllagerTowers.T3_ILLUSIONER_LOW);
        link(IllagerTowers.T2_WITCH_HIGH, IllagerTowers.T3_ILLUSIONER_HIGH.id(), "환술사타워(강자 표식)", IllagerTowers.T3_ILLUSIONER_HIGH);

        JobRegistry.registerIfAbsent(new IllagerTowerJob());
    }

    private static void registerTower(TowerType type, ProductionTowerCatalog.TowerFactory factory, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolvedType = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolvedType, factory);
            return;
        }
        ProductionTowerCatalog.register(resolvedType, factory, tier);
    }

    private static void link(TowerType from, String id, String displayName, TowerType to) {
        if (ProductionTowerCatalog.upgrade(from, id).isPresent()) {
            return;
        }
        TowerType targetType = ProductionTowerCatalog.find(to.id()).map(ProductionTowerCatalog.CatalogEntry::type).orElse(to);
        ProductionTowerCatalog.linkUpgrade(from, id, displayName, targetType, TowerBalanceRuntime.upgradeCost(from, id));
    }

    private static ProductionTowerCatalog.TowerFactory lowHealthFactory() {
        return (type, ownerPlayer, teamId, laneId, originalPosition, currentPosition) ->
                new IllagerTower(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition, IllagerTargetPolicy.LOW_HEALTH);
    }

    private static ProductionTowerCatalog.TowerFactory highHealthFactory() {
        return (type, ownerPlayer, teamId, laneId, originalPosition, currentPosition) ->
                new IllagerTower(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition, IllagerTargetPolicy.HIGH_HEALTH);
    }

    private static ProductionTowerCatalog.TowerFactory singleTargetFactory() {
        return (type, ownerPlayer, teamId, laneId, originalPosition, currentPosition) ->
                new IllagerTower(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition, IllagerTargetPolicy.INCOME);
    }
}
