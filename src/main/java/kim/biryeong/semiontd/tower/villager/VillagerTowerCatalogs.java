package kim.biryeong.semiontd.tower.villager;

import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.VillagerTowerJob;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class VillagerTowerCatalogs {
    private VillagerTowerCatalogs() {
    }

    public static void register() {
        registerTower(VillagerTowers.T1_SPLASH_TOWER, ProductionTower::new, 1);
        registerTower(VillagerTowers.T2_LIBRARIAN_TOWER, VillagerSplashTower::new, 2);
        registerTower(VillagerTowers.T3_CLERIC_TOWER, VillagerSplashTower::new, 3);

        registerTower(VillagerTowers.T1_GOLEM_TOWER, ProductionTower::new, 1);
        registerTower(VillagerTowers.T2_GOLEM_TOWER, VillagerThornTower::new, 2);
        registerTower(VillagerTowers.T3_GOLEM_TOWER, VillagerThornTower::new, 3);

        registerTower(VillagerTowers.T1_ALLAY_TOWER, AllayTower::new, 1);
        registerTower(VillagerTowers.T2_ALLAY_TOWER, AllayTower::new, 2);
        registerTower(VillagerTowers.T2_WEAPON_SMITH_TOWER, AllayTower::new, 2);
        registerTower(VillagerTowers.T3_ARMORER_TOWER, AllayTower::new, 3);
        registerTower(VillagerTowers.T3_WEAPON_SMITH_TOWER, AllayTower::new, 3);

        registerTower(VillagerTowers.T1_CAT_TOWER, ProductionTower::new, 1);
        registerTower(VillagerTowers.T2_ANTI_TANKER_CAT_TOWER, AntiTankerCatTower::new, 2);
        registerTower(VillagerTowers.T2_LANE_CLEAR_CAT_TOWER, LaneClearCatTower::new, 2);
        registerTower(VillagerTowers.T3_ANTI_TANKER_CAT_TOWER, AntiTankerCatTower::new, 3);
        registerTower(VillagerTowers.T3_LANE_CLEAR_CAT_TOWER, LaneClearCatTower::new, 3);

        link(VillagerTowers.T1_SPLASH_TOWER, "villager_splash_t2", "사서 타워", VillagerTowers.T2_LIBRARIAN_TOWER);
        link(VillagerTowers.T2_LIBRARIAN_TOWER, "villager_splash_t3", "성직자 타워", VillagerTowers.T3_CLERIC_TOWER);

        link(VillagerTowers.T1_GOLEM_TOWER, "t2_golem_tower", "라마 타워", VillagerTowers.T2_GOLEM_TOWER);
        link(VillagerTowers.T2_GOLEM_TOWER, "t3_golem_tower", "철 골렘 타워", VillagerTowers.T3_GOLEM_TOWER);

        link(VillagerTowers.T1_ALLAY_TOWER, "t2_allay_tower", "알레이 타워(강함)", VillagerTowers.T2_ALLAY_TOWER);
        link(VillagerTowers.T1_ALLAY_TOWER, "t2_weapon_smith_tower", "대장장이 타워", VillagerTowers.T2_WEAPON_SMITH_TOWER);
        link(VillagerTowers.T2_ALLAY_TOWER, "t3_armorer_tower", "갑옷 제조인 타워", VillagerTowers.T3_ARMORER_TOWER);
        link(VillagerTowers.T2_WEAPON_SMITH_TOWER, "t3_weapon_smith_tower", "강화 대장장이 타워", VillagerTowers.T3_WEAPON_SMITH_TOWER);

        link(VillagerTowers.T1_CAT_TOWER, "t2_anti_tanker_cat_tower", "저격 캣 타워", VillagerTowers.T2_ANTI_TANKER_CAT_TOWER);
        link(VillagerTowers.T1_CAT_TOWER, "t2_lane_clear_cat_tower", "라클 캣 타워", VillagerTowers.T2_LANE_CLEAR_CAT_TOWER);
        link(VillagerTowers.T2_ANTI_TANKER_CAT_TOWER, "t3_anti_tanker_cat_tower", "강화 저격 캣 타워", VillagerTowers.T3_ANTI_TANKER_CAT_TOWER);
        link(VillagerTowers.T2_LANE_CLEAR_CAT_TOWER, "t3_lane_clear_cat_tower", "강화 라클 캣 타워", VillagerTowers.T3_LANE_CLEAR_CAT_TOWER);

        registerTower(VillagerTowers.ADV_T1_SPLASH_TOWER, ProductionTower::new, 1);
        registerTower(VillagerTowers.ADV_T2_LIBRARIAN_TOWER, VillagerSplashTower::new, 2);
        registerTower(VillagerTowers.ADV_T3_CLERIC_TOWER, VillagerSplashTower::new, 3);

        registerTower(VillagerTowers.ADV_T1_GOLEM_TOWER, ProductionTower::new, 1);
        registerTower(VillagerTowers.ADV_T2_GOLEM_TOWER, VillagerThornTower::new, 2);
        registerTower(VillagerTowers.ADV_T3_GOLEM_TOWER, VillagerThornTower::new, 3);

        registerTower(VillagerTowers.ADV_T1_ALLAY_TOWER, AllayTower::new, 1);
        registerTower(VillagerTowers.ADV_T2_ALLAY_TOWER, AllayTower::new, 2);
        registerTower(VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, AllayTower::new, 2);
        registerTower(VillagerTowers.ADV_T3_ARMORER_TOWER, AllayTower::new, 3);
        registerTower(VillagerTowers.ADV_T3_WEAPON_SMITH_TOWER, AllayTower::new, 3);

        registerTower(VillagerTowers.ADV_T1_CAT_TOWER, ProductionTower::new, 1);
        registerTower(VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, AntiTankerCatTower::new, 2);
        registerTower(VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, LaneClearCatTower::new, 2);
        registerTower(VillagerTowers.ADV_T3_ANTI_TANKER_CAT_TOWER, AntiTankerCatTower::new, 3);
        registerTower(VillagerTowers.ADV_T3_LANE_CLEAR_CAT_TOWER, LaneClearCatTower::new, 3);

        link(VillagerTowers.ADV_T1_SPLASH_TOWER, "villager_splash_t2", "사서 타워", VillagerTowers.ADV_T2_LIBRARIAN_TOWER);
        link(VillagerTowers.ADV_T2_LIBRARIAN_TOWER, "villager_splash_t3", "성직자 타워", VillagerTowers.ADV_T3_CLERIC_TOWER);

        link(VillagerTowers.ADV_T1_GOLEM_TOWER, "t2_golem_tower", "라마 타워", VillagerTowers.ADV_T2_GOLEM_TOWER);
        link(VillagerTowers.ADV_T2_GOLEM_TOWER, "t3_golem_tower", "철 골렘 타워", VillagerTowers.ADV_T3_GOLEM_TOWER);

        link(VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_allay_tower", "알레이 타워(강함)", VillagerTowers.ADV_T2_ALLAY_TOWER);
        link(VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_weapon_smith_tower", "대장장이 타워", VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER);
        link(VillagerTowers.ADV_T2_ALLAY_TOWER, "t3_armorer_tower", "갑옷 제조인 타워", VillagerTowers.ADV_T3_ARMORER_TOWER);
        link(VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, "t3_weapon_smith_tower", "강화 대장장이 타워", VillagerTowers.ADV_T3_WEAPON_SMITH_TOWER);

        link(VillagerTowers.ADV_T1_CAT_TOWER, "t2_anti_tanker_cat_tower", "저격 캣 타워", VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER);
        link(VillagerTowers.ADV_T1_CAT_TOWER, "t2_lane_clear_cat_tower", "라클 캣 타워", VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER);
        link(VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, "t3_anti_tanker_cat_tower", "강화 저격 캣 타워", VillagerTowers.ADV_T3_ANTI_TANKER_CAT_TOWER);
        link(VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, "t3_lane_clear_cat_tower", "강화 라클 캣 타워", VillagerTowers.ADV_T3_LANE_CLEAR_CAT_TOWER);

        if (JobRegistry.find(VillagerTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new VillagerTowerJob());
        }
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
}
