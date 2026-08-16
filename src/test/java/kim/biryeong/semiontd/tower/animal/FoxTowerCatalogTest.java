package kim.biryeong.semiontd.tower.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FoxTowerCatalogTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetTowerBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void defaultBalanceConfigIncludesFoxTowerStatsAndAbilities() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertTrue(config.towers().containsKey(AnimalTowers.T1_FOX_TOWER.id()));
        assertTrue(config.towers().containsKey(AnimalTowers.T2_FOX_TOWER.id()));
        assertTrue(config.towers().containsKey(AnimalTowers.T3_FOX_TOWER.id()));
        assertEquals(0.30, config.ability(AnimalTowers.T1_FOX_TOWER.id(), "executeHealthThreshold", -1.0), 0.0001);
        assertEquals(0.35, config.ability(AnimalTowers.T2_FOX_TOWER.id(), "executeHealthThreshold", -1.0), 0.0001);
        assertEquals(0.40, config.ability(AnimalTowers.T3_FOX_TOWER.id(), "executeHealthThreshold", -1.0), 0.0001);
        assertEquals(0.1, config.ability(AnimalTowers.T1_FOX_TOWER.id(), "killBonusDamage", -1.0), 0.0001);
        assertEquals(10.0, config.ability(AnimalTowers.T1_FOX_TOWER.id(), "killBonusDamageCap", -1.0), 0.0001);
        assertEquals(0.2, config.ability(AnimalTowers.T2_FOX_TOWER.id(), "killBonusDamage", -1.0), 0.0001);
        assertEquals(20.0, config.ability(AnimalTowers.T2_FOX_TOWER.id(), "killBonusDamageCap", -1.0), 0.0001);
        assertEquals(0.4, config.ability(AnimalTowers.T3_FOX_TOWER.id(), "killBonusDamage", -1.0), 0.0001);
        assertEquals(40.0, config.ability(AnimalTowers.T3_FOX_TOWER.id(), "killBonusDamageCap", -1.0), 0.0001);
    }

    @Test
    void defaultBalanceConfigIncludesFoxUpgradeCosts() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertEquals(150, config.upgradeCost(AnimalTowers.T1_FOX_TOWER.id(), AnimalTowers.T2_FOX_TOWER.id(), -1));
        assertEquals(225, config.upgradeCost(AnimalTowers.T2_FOX_TOWER.id(), AnimalTowers.T3_FOX_TOWER.id(), -1));
    }

    @Test
    void animalTowerJobAllowsFoxTowersForConstructionAndUpgrades() {
        AnimalTowerJob job = new AnimalTowerJob();

        assertTrue(job.canUseTower(null, AnimalTowers.T1_FOX_TOWER));
        assertTrue(job.canUseTower(null, AnimalTowers.T2_FOX_TOWER));
        assertTrue(job.canUseTower(null, AnimalTowers.T3_FOX_TOWER));
    }

}
