package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import kim.biryeong.semiontd.balance.manage.BalanceBundle;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ManagedCatalogStagingTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void candidateStatsDescriptionsAndUpgradeEdgesAreResolvedWithoutGlobalMutation() {
        var originalCatalog = ProductionTowerCatalog.snapshot();
        TowerBalanceConfig originalBalance = TowerBalanceRuntime.current();
        try {
            BalanceBundle defaults = BalanceBundle.defaults();
            ProductionTowerCatalogs.reloadBuiltIns(defaults.tower());
            var before = ProductionTowerCatalog.snapshot();
            JsonObject json = defaults.toJson();
            JsonObject tower = json.getAsJsonObject("tower");
            tower.getAsJsonObject("towers").getAsJsonObject("t2_fox_tower").addProperty("damage", 123);
            tower.getAsJsonObject("abilities").getAsJsonObject("t1_fox_tower").addProperty("killBonusDamage", 7.25);
            tower.getAsJsonObject("upgradeCosts").addProperty("t1_fox_tower->t2_fox_tower", 177);
            TowerBalanceConfig candidate = BalanceBundle.fromJson(json).tower();
            var staged = ProductionTowerCatalog.stageBalance(candidate);
            assertEquals(before, ProductionTowerCatalog.snapshot());
            assertSame(defaults.tower(), TowerBalanceRuntime.current());
            assertEquals(123, staged.entries().get("t2_fox_tower").type().damage());
            assertTrue(staged.entries().get("t1_fox_tower").type().description().stream().anyMatch(line -> line.contains("7.25")));
            assertEquals(177, staged.upgrades().get("t1_fox_tower").getFirst().mineralCost());
            assertEquals(123, staged.upgrades().get("t1_fox_tower").getFirst().targetType().damage());
            assertEquals(.1, TowerBalanceRuntime.ability(AnimalTowers.T1_FOX_TOWER.id(), "killBonusDamage"));
            assertThrows(UnsupportedOperationException.class, () -> staged.entries().clear());
        } finally {
            TowerBalanceRuntime.apply(originalBalance);
            ProductionTowerCatalog.install(originalCatalog);
        }
    }
}
