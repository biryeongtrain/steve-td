package kim.biryeong.semiontd.tower.illager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.IllagerTowerJob;
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
        assertEquals(8.0, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "incomeKillGauge", -1), 0.0001);
        assertEquals(0.02, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "attackSpeedPercentPerTower", -1), 0.0001);
        assertEquals(0.05, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "damagePercentPerTower", -1), 0.0001);
        assertEquals(40.0, config.ability(IllagerRaidStates.RAID_CONFIG_ID, "timedEffectDurationTicks", -1), 0.0001);
        assertEquals(0.15, config.ability(IllagerTowers.T1_PILLAGER.id(), "raidMarkedDamageBonus", -1), 0.0001);
        assertEquals(0.25, config.ability(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE.id(), "raidIncomeDamageBonus", -1), 0.0001);
        assertEquals(0.10, config.ability(IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id(), "raidSplashDamageRatioBonus", -1), 0.0001);
        assertEquals(20.0, config.ability(IllagerTowers.T1_VEX.id(), "raidMarkDurationBonusTicks", -1), 0.0001);
        assertEquals(0.08, config.ability(IllagerTowers.T2_WITCH_LOW.id(), "raidLowHealthMarkDamageTakenBonus", -1), 0.0001);
        assertEquals(0.12, config.ability(IllagerTowers.T3_ILLUSIONER_HIGH.id(), "raidHighHealthMarkDamageTakenBonus", -1), 0.0001);
        assertTrue(config.towers().containsKey(IllagerTowers.T1_VINDICATOR.id()));
        assertTrue(config.towers().containsKey(IllagerTowers.T1_PILLAGER.id()));
        assertTrue(config.towers().containsKey(IllagerTowers.T1_VEX.id()));
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
        assertEquals(8.0, custom.ability(IllagerRaidStates.RAID_CONFIG_ID, "incomeKillGauge", -1), 0.0001);
    }

    @Test
    void illagerDescriptionsRenderConfiguredRaidAndTowerValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> raid = new LinkedHashMap<>(abilities.get(IllagerRaidStates.RAID_CONFIG_ID));
        raid.put("waveKillGauge", 4.0);
        raid.put("attackSpeedPercentPerTower", 0.03);
        raid.put("damagePercentPerTower", 0.06);
        abilities.put(IllagerRaidStates.RAID_CONFIG_ID, raid);
        Map<String, Double> splash = new LinkedHashMap<>(abilities.get(IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id()));
        splash.put("raidSplashRadiusBonus", 0.75);
        splash.put("raidSplashDamageRatioBonus", 0.2);
        abilities.put(IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id(), splash);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));

        var towerDescription = TowerBalanceRuntime.resolve(IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH).description();
        var jobDescription = new IllagerTowerJob().description().stream().map(net.minecraft.network.chat.Component::getString).toList();

        assertTrue(towerDescription.stream().anyMatch(line -> line.contains("공격속도 3%") && line.contains("공격력 6%")));
        assertTrue(towerDescription.stream().anyMatch(line -> line.contains("0.75블록") && line.contains("20% 증가")));
        assertTrue(jobDescription.stream().anyMatch(line -> line.contains("웨이브 적 처치 시 +4")));
        assertTrue(jobDescription.stream().anyMatch(line -> line.contains("공격속도 3%") && line.contains("공격력 6%")));
    }
}
