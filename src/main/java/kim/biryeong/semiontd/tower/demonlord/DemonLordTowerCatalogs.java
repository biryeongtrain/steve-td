package kim.biryeong.semiontd.tower.demonlord;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class DemonLordTowerCatalogs {
    private DemonLordTowerCatalogs() {
    }

    public static void register() {
        // 스킬 10종 × 4티어. T1만 상점에 뜨고 나머지는 업그레이드로만 닿습니다.
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                registerTower(DemonLordTowers.tower(skill, tier), tier);
            }
            for (int tier = 1; tier < DemonLordSkill.MAX_TIER; tier++) {
                link(DemonLordTowers.tower(skill, tier), DemonLordTowers.tower(skill, tier + 1));
            }
        }

        JobRegistry.registerIfAbsent(new DemonLordTowerJob());
    }

    private static void registerTower(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolvedType = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolvedType, DemonLordSkillTower::new);
            return;
        }
        ProductionTowerCatalog.register(resolvedType, DemonLordSkillTower::new, tier);
    }

    private static void link(TowerType from, TowerType to) {
        if (ProductionTowerCatalog.upgrade(from, to.id()).isPresent()) {
            return;
        }
        TowerType targetType = ProductionTowerCatalog.find(to.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(to);
        ProductionTowerCatalog.linkUpgrade(
                from,
                to.id(),
                to.displayName(),
                targetType,
                TowerBalanceRuntime.upgradeCost(from, to.id())
        );
    }
}
