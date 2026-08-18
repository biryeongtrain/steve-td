package kim.biryeong.semiontd.tower.end;

import static kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.assertFamilyClosed;
import static kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.assertResolvedDescriptions;
import static kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.upgrade;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.FamilyContract;
import kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.UpgradeExpectation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EndIntegrationSliceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaults() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void registryCatalogUpgradeFactoriesOwnershipAndDescriptionsAreClosed() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        assertFamilyClosed(new FamilyContract(
                EndTowerJob.ID,
                EndTowers.CONFIG_ID,
                EndTowers.all(),
                Map.of(
                        EndTowers.BASE_END_TOWER.id(), 1,
                        EndTowers.T1_SHULKER_TOWER.id(), 1,
                        EndTowers.T2_SHULKER_TOWER.id(), 2,
                        EndTowers.T3_SHULKER_TOWER.id(), 3,
                        EndTowers.T1_ENDERMITE_TOWER.id(), 1,
                        EndTowers.T2_ENDERMAN_TOWER.id(), 2,
                        EndTowers.T3_END_CRYSTAL_TOWER.id(), 3
                ),
                upgrades(),
                ignored -> EndTower.class
        ), defaults);
    }

    @Test
    void partialConfigMergeBackfillsFamilyAndReloadsConfiguredOverride() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig.TowerStats endDefaults = defaults.towers().get(EndTowers.BASE_END_TOWER.id());
        TowerBalanceConfig.TowerStats configuredEnd = new TowerBalanceConfig.TowerStats(
                endDefaults.mineralCost(),
                endDefaults.maxHealth() + 123.0,
                endDefaults.range(),
                endDefaults.damage(),
                endDefaults.attackIntervalTicks(),
                endDefaults.aggroPriority()
        );
        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(EndTowers.BASE_END_TOWER.id(), configuredEnd),
                Map.of(),
                Map.of()
        );

        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(configuredEnd, merged.towers().get(EndTowers.BASE_END_TOWER.id()));
        EndTowers.all().forEach(type -> assertTrue(merged.towers().containsKey(type.id()),
                "Partial config did not backfill End tower " + type.id()));
        assertTrue(merged.abilities().containsKey(EndTowers.CONFIG_ID));
        upgrades().forEach(upgrade -> assertTrue(merged.upgradeCosts().containsKey(upgrade.configKey())));

        ProductionTowerCatalogs.reloadBuiltIns(merged);
        assertEquals(configuredEnd.maxHealth(),
                ProductionTowerCatalog.find(EndTowers.BASE_END_TOWER.id()).orElseThrow().type().maxHealth(),
                0.0001);
        assertResolvedDescriptions(EndTowers.all());
    }

    private static List<UpgradeExpectation> upgrades() {
        return List.of(
                upgrade(EndTowers.T1_SHULKER_TOWER, EndTowers.T2_SHULKER_TOWER.id(), EndTowers.T2_SHULKER_TOWER),
                upgrade(EndTowers.T2_SHULKER_TOWER, EndTowers.T3_SHULKER_TOWER.id(), EndTowers.T3_SHULKER_TOWER),
                upgrade(EndTowers.T1_ENDERMITE_TOWER, EndTowers.T2_ENDERMAN_TOWER.id(), EndTowers.T2_ENDERMAN_TOWER),
                upgrade(EndTowers.T2_ENDERMAN_TOWER, EndTowers.T3_END_CRYSTAL_TOWER.id(), EndTowers.T3_END_CRYSTAL_TOWER)
        );
    }
}
