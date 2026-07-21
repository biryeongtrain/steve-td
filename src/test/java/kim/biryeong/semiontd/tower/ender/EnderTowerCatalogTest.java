package kim.biryeong.semiontd.tower.ender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.EnderTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnderTowerCatalogTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetCatalogs() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void defaultBalanceConfigIncludesEnderTowersAndAbilities() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertTrue(config.towers().containsKey(EnderTowers.BASE_ENDER_TOWER.id()));
        assertTrue(config.towers().containsKey(EnderTowers.T3_END_CRYSTAL_TOWER.id()));
        assertTrue(config.towers().containsKey(EnderTowers.T3_SHULKER_TOWER.id()));
        assertEquals(-1.0, config.ability(EnderTower.CONFIG_ID, "hatchDelayTicks", -1.0), 0.0001);
        assertEquals(2000.0, config.ability(EnderTower.CONFIG_ID, "dragonEvolutionMaxHealth", -1.0), 0.0001);
        assertEquals(400.0, config.ability(EnderTower.CONFIG_ID, "absorptionDurationTicks", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EnderTower.CONFIG_ID, "roundHealthRatio", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EnderTower.CONFIG_ID, "roundDamageRatio", -1.0), 0.0001);
        assertEquals(0.50, config.ability(EnderTower.CONFIG_ID, "roundStatBonusCapRatio", -1.0), 0.0001);
        assertEquals(0.05, config.ability(EnderTower.CONFIG_ID, "permanentHealthRatio", -1.0), 0.0001);
        assertEquals(0.05, config.ability(EnderTower.CONFIG_ID, "permanentDamageRatio", -1.0), 0.0001);
        assertEquals(20.0, config.ability(EnderTower.CONFIG_ID, "endCrystalAttackIntervalEvery", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EnderTower.CONFIG_ID, "attackIntervalReductionPerStep", -1.0), 0.0001);
        assertEquals(10.0, config.ability(EnderTower.CONFIG_ID, "shulkerLifeStealEvery", -1.0), 0.0001);
        assertEquals(10.0, config.ability(EnderTower.CONFIG_ID, "endCrystalSplashEvery", -1.0), 0.0001);
        assertEquals(0.25, config.ability(EnderTower.CONFIG_ID, "splashRadiusPerStep", -1.0), 0.0001);
        assertEquals(5.0, config.ability(EnderTower.CONFIG_ID, "splashRadiusCap", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EnderTower.CONFIG_ID, "splashDamageRatio", -1.0), 0.0001);
        assertEquals(20.0, config.ability(EnderTower.CONFIG_ID, "shulkerReductionEvery", -1.0), 0.0001);
        assertEquals(0.025, config.ability(EnderTower.CONFIG_ID, "damageReductionPerStep", -1.0), 0.0001);
        assertEquals(0.30, config.ability(EnderTower.CONFIG_ID, "lifeStealCap", -1.0), 0.0001);
        assertEquals(0.25, config.ability(EnderTower.CONFIG_ID, "damageReductionCap", -1.0), 0.0001);
        assertEquals(15.0, config.ability(EnderTower.CONFIG_ID, "maxAttackIntervalReductionTicks", -1.0), 0.0001);
        assertEquals(5.0, config.ability(EnderTower.CONFIG_ID, "minimumAttackIntervalTicks", -1.0), 0.0001);
        assertEquals(0.10, config.ability(EnderTowers.T1_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
        assertEquals(0.30, config.ability(EnderTowers.T2_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
        assertEquals(0.50, config.ability(EnderTowers.T3_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
    }

    @Test
    void enderJobAllowsEveryEnderTowerOnly() {
        EnderTowerJob job = new EnderTowerJob();

        assertTrue(job.canUseTower(null, EnderTowers.BASE_ENDER_TOWER));
        assertTrue(job.canUseTower(null, EnderTowers.T1_ENDERMITE_TOWER));
        assertTrue(job.canUseTower(null, EnderTowers.T3_END_CRYSTAL_TOWER));
        assertTrue(job.canUseTower(null, EnderTowers.T1_SHULKER_TOWER));
        assertTrue(job.canUseTower(null, EnderTowers.T3_SHULKER_TOWER));
        assertFalse(job.canUseTower(null, AnimalTowers.T1_PIG_TOWER));
    }

    @Test
    void catalogRegistersDragonAndTwoUpgradePaths() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        assertStarter(EnderTowers.BASE_ENDER_TOWER.id(), "엔더 드래곤");
        assertStarter(EnderTowers.T1_ENDERMITE_TOWER.id(), "엔더 마이트");
        assertStarter(EnderTowers.T1_SHULKER_TOWER.id(), "셜커");
        assertUpgrade(EnderTowers.T1_ENDERMITE_TOWER.id(), EnderTowers.T2_ENDERMAN_TOWER.id(), "엔더맨", 125);
        assertUpgrade(EnderTowers.T2_ENDERMAN_TOWER.id(), EnderTowers.T3_END_CRYSTAL_TOWER.id(), "엔드 수정", 200);
        assertUpgrade(EnderTowers.T1_SHULKER_TOWER.id(), EnderTowers.T2_SHULKER_TOWER.id(), "견고한 셜커", 125);
        assertUpgrade(EnderTowers.T2_SHULKER_TOWER.id(), EnderTowers.T3_SHULKER_TOWER.id(), "완강한 셜커", 200);
    }

    @Test
    void shulkerLineUsesShulkerVisuals() {
        assertEquals("minecraft:shulker", EnderTowers.T1_SHULKER_TOWER.visual().entityTypeId());
        assertEquals("minecraft:shulker", EnderTowers.T2_SHULKER_TOWER.visual().entityTypeId());
        assertEquals("minecraft:shulker", EnderTowers.T3_SHULKER_TOWER.visual().entityTypeId());
        assertFalse(EnderTowers.T1_SHULKER_TOWER.visual().properties().containsKey("shulker_color"));
        assertEquals(DyeColor.PURPLE, EnderTowers.T2_SHULKER_TOWER.visual().properties().get("shulker_color"));
        assertEquals(DyeColor.BLACK, EnderTowers.T3_SHULKER_TOWER.visual().properties().get("shulker_color"));
    }

    @Test
    void shulkerDescriptionsShowTierDamageReduction() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());

        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EnderTowers.T1_SHULKER_TOWER).description()).contains("10%"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EnderTowers.T2_SHULKER_TOWER).description()).contains("30%"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EnderTowers.T3_SHULKER_TOWER).description()).contains("50%"));
    }

    @Test
    void dragonDescriptionUsesConfiguredAbilityValues() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());

        assertEquals(10.0, EnderTowers.BASE_ENDER_TOWER.damage(), 0.0001);
        String description = String.join("\n", TowerBalanceRuntime.resolve(EnderTowers.BASE_ENDER_TOWER).description());

        assertTrue(description.contains("알로 소환되며"));
        assertTrue(description.contains("라운드 시작 시 팬텀"));
        assertTrue(description.contains("이상이면"));
        assertTrue(description.contains("최대 5.0"));
        assertTrue(description.contains("20초"));
        assertTrue(description.contains("5%"));
        assertTrue(description.contains("엔드 수정 계열"));
        assertTrue(description.contains("전달하고 사망"));
        assertTrue(description.contains("20기마다 공격 주기 -1틱"));
        assertTrue(description.contains("최소 5틱"));
        assertTrue(description.contains("10기마다 광역 공격 반경"));
        assertTrue(description.contains("10기마다 생명력 흡수"));
        assertTrue(description.contains("20기마다 받는 피해 -2.5%"));
        assertTrue(description.contains("최대 30%"));
        assertTrue(description.contains("최대 25%"));
    }

    @Test
    void everyEnderFeederRegistersItsDescriptionTemplate() {
        assertDescription(EnderTowers.T1_ENDERMITE_TOWER, "공격력이 높은 엔더마이트", "공격력, 광역 공격, 공격 속도");
        assertDescription(EnderTowers.T2_ENDERMAN_TOWER, "공격력이 높은 엔더맨", "공격력, 광역 공격, 공격 속도");
        assertDescription(EnderTowers.T3_END_CRYSTAL_TOWER, "공격력이 매우 높은 엔드 수정", "공격력, 광역 공격, 공격 속도");
        assertDescription(EnderTowers.T1_SHULKER_TOWER, "체력이 높은 셜커", "체력, 생명력 흡수, 피해 감소");
        assertDescription(EnderTowers.T2_SHULKER_TOWER, "체력이 높은 견고한 셜커", "체력, 생명력 흡수, 피해 감소");
        assertDescription(EnderTowers.T3_SHULKER_TOWER, "체력이 매우 높은 완강한 셜커", "체력, 생명력 흡수, 피해 감소");
    }

    @Test
    void upgradePricesComeFromBalanceConfig() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Long> upgradeCosts = new LinkedHashMap<>(defaults.upgradeCosts());
        upgradeCosts.put(
                TowerBalanceConfig.upgradeKey(EnderTowers.T1_ENDERMITE_TOWER.id(), EnderTowers.T2_ENDERMAN_TOWER.id()),
                1L
        );
        TowerBalanceConfig custom = new TowerBalanceConfig(defaults.towers(), upgradeCosts, defaults.abilities());

        ProductionTowerCatalogs.reloadBuiltIns(custom);

        assertEquals(1L, ProductionTowerCatalog.upgrade(
                EnderTowers.T1_ENDERMITE_TOWER,
                EnderTowers.T2_ENDERMAN_TOWER.id()
        ).orElseThrow().mineralCost());
    }

    @Test
    void catalogCreatesEnderRuntime() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var entry = ProductionTowerCatalog.find(EnderTowers.BASE_ENDER_TOWER.id()).orElseThrow();
        var tower = entry.create(
                UUID.nameUUIDFromBytes("ender-runtime".getBytes()),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );

        assertInstanceOf(EnderTower.class, tower);
        assertEquals(0.0, tower.adjustAttackRange(tower.type().range()), 0.0001);
    }

    private static void assertStarter(String towerId, String displayName) {
        var entry = ProductionTowerCatalog.find(towerId).orElseThrow();
        assertTrue(entry.starter());
        assertEquals(displayName, entry.type().displayName());
    }

    private static void assertUpgrade(String fromTowerId, String upgradeId, String displayName, long cost) {
        var from = ProductionTowerCatalog.find(fromTowerId).orElseThrow().type();
        var upgrade = ProductionTowerCatalog.upgrade(from, upgradeId).orElseThrow();
        assertEquals(displayName, upgrade.displayName());
        assertEquals(cost, upgrade.mineralCost());
    }

    private static void assertDescription(
            kim.biryeong.semiontd.tower.TowerType towerType,
            String summary,
            String effect
    ) {
        String description = String.join("\n", TowerDescriptionRegistry.describe(towerType).orElseThrow());
        assertTrue(description.contains(summary));
        assertTrue(description.contains(effect));
    }
}
