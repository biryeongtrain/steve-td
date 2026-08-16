package kim.biryeong.semiontd.tower.warlock;

import static kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.assertFamilyClosed;
import static kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.assertResolvedDescriptions;
import static kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.upgrade;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.FamilyContract;
import kim.biryeong.semiontd.tower.TowerIntegrationSliceAssertions.UpgradeExpectation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class WarlockIntegrationSliceTest {
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
                WarlockTowerJob.ID,
                WarlockTowers.CONFIG_ID,
                WarlockTowers.all(),
                Map.of(
                        WarlockTowers.BASE_WARLOCK_TOWER.id(), 1,
                        WarlockTowers.RANGED_WARLOCK_TOWER.id(), 2,
                        WarlockTowers.MELEE_WARLOCK_TOWER.id(), 2,
                        WarlockTowers.T1_SLAVE.id(), 1,
                        WarlockTowers.T2_SLAVE.id(), 2,
                        WarlockTowers.T3_SLAVE.id(), 3,
                        WarlockTowers.T1_RANGED_SLAVE.id(), 1,
                        WarlockTowers.T2_RANGED_SLAVE.id(), 2,
                        WarlockTowers.T3_RANGED_SLAVE.id(), 3
                ),
                upgrades(),
                type -> WarlockTowers.isWarlockCore(type) ? WarlockTower.class : WarlockSacrificeTower.class
        ), defaults);
    }

    @Test
    void partialConfigMergeBackfillsFamilyAndReloadsConfiguredOverride() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(),
                Map.of(),
                Map.of(WarlockTowers.CONFIG_ID, Map.of("awakeningKills", 42.0))
        );

        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        WarlockTowers.all().forEach(type -> assertTrue(merged.towers().containsKey(type.id()),
                "Partial config did not backfill Warlock tower " + type.id()));
        assertEquals(42.0, merged.ability(WarlockTowers.CONFIG_ID, "awakeningKills", -1.0));
        assertTrue(merged.abilities().get(WarlockTowers.CONFIG_ID).containsKey("sacrificeRadius"));
        upgrades().forEach(upgrade -> assertTrue(merged.upgradeCosts().containsKey(upgrade.configKey())));

        ProductionTowerCatalogs.reloadBuiltIns(merged);
        assertEquals(42, TowerBalanceRuntime.abilityInt(WarlockTowers.CONFIG_ID, "awakeningKills", -1));
        assertResolvedDescriptions(WarlockTowers.all());
    }

    private static List<UpgradeExpectation> upgrades() {
        return List.of(
                upgrade(WarlockTowers.BASE_WARLOCK_TOWER, "ranged_warlock_tower", WarlockTowers.RANGED_WARLOCK_TOWER),
                upgrade(WarlockTowers.BASE_WARLOCK_TOWER, "melee_warlock_tower", WarlockTowers.MELEE_WARLOCK_TOWER),
                upgrade(WarlockTowers.T1_SLAVE, "t2_slave", WarlockTowers.T2_SLAVE),
                upgrade(WarlockTowers.T2_SLAVE, "t3_slave", WarlockTowers.T3_SLAVE),
                upgrade(WarlockTowers.T1_RANGED_SLAVE, "t2_ranged_slave", WarlockTowers.T2_RANGED_SLAVE),
                upgrade(WarlockTowers.T2_RANGED_SLAVE, "t3_ranged_slave", WarlockTowers.T3_RANGED_SLAVE)
        );
    }
}
