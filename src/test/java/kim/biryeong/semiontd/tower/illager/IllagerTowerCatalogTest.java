package kim.biryeong.semiontd.tower.illager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IllagerTowerCatalogTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetTowerBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        IllagerRaidStates.clearAllForTesting();
    }

    @Test
    void defaultBalanceConfigIncludesIllagerRaidAndTowerValues() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertEquals(100.0, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "gaugeMax", -1), 0.0001);
        assertEquals(3.0, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "waveKillGauge", -1), 0.0001);
        assertEquals(6.0, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "incomeKillGauge", -1), 0.0001);
        assertEquals(0.03, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "attackSpeedPercentPerTower", -1), 0.0001);
        assertEquals(0.08, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "damagePercentPerTower", -1), 0.0001);
        assertEquals(40.0, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "timedEffectDurationTicks", -1), 0.0001);
        assertEquals(0.40, config.ability(IllagerTowers.T1_PILLAGER.id(), "raidMarkedDamageBonus", -1), 0.0001);
        assertEquals(1.25, config.ability(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE.id(), "raidIncomeDamageBonus", -1), 0.0001);
        assertEquals(0.25, config.ability(IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id(), "raidSplashDamageRatioBonus", -1), 0.0001);
        assertEquals(20.0, config.ability(IllagerTowers.T1_VEX.id(), "raidMarkDurationBonusTicks", -1), 0.0001);
        assertEquals(0.25, config.ability(IllagerTowers.T2_WITCH_LOW.id(), "raidLowHealthMarkDamageTakenBonus", -1), 0.0001);
        assertEquals(0.40, config.ability(IllagerTowers.T3_ILLUSIONER_HIGH.id(), "raidHighHealthMarkDamageTakenBonus", -1), 0.0001);
        assertTrue(config.towers().containsKey(IllagerTowers.T1_VINDICATOR.id()));
        assertTrue(config.towers().containsKey(IllagerTowers.T1_PILLAGER.id()));
        assertTrue(config.towers().containsKey(IllagerTowers.T1_VEX.id()));
        assertEquals(0.70, IllagerTowers.T3_RAVAGER.visual().scale(), 0.0001);
    }

    @Test
    void customTowerBalanceMergesMissingIllagerDefaults() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig custom = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                java.util.Map.of(IllagerRaidStates.RAID_CONFIG_ID, java.util.Map.of("gaugeMax", 80.0))
        ).withMissingDefaults(defaults);

        assertEquals(80.0, custom.ability(IllagerRaidStates.RAID_CONFIG_ID, "gaugeMax", -1), 0.0001);
        assertEquals(6.0, custom.ability(IllagerRaidStates.RAID_CONFIG_ID, "incomeKillGauge", -1), 0.0001);
    }

}
