package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndTowerCatalogTest {
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
    void defaultBalanceConfigIncludesEndTowersAndAbilities() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        assertTrue(config.towers().containsKey(EndTowers.BASE_END_TOWER.id()));
        assertTrue(config.towers().containsKey(EndTowers.T3_END_CRYSTAL_TOWER.id()));
        assertTrue(config.towers().containsKey(EndTowers.T3_SHULKER_TOWER.id()));
        assertTrue(config.abilities().containsKey(EndTower.CONFIG_ID));
        List<String> expectedAbilityKeys = Arrays.stream(EndConfig.Ability.values())
                .map(EndConfig.Ability::key)
                .toList();
        List<String> actualAbilityKeys = List.copyOf(
                config.abilities().get(EndTower.CONFIG_ID).keySet()
        );
        assertEquals(expectedAbilityKeys, actualAbilityKeys);
        assertEquals(-1.0, config.ability(EndTower.CONFIG_ID, "hatchDelayTicks", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(EndTower.CONFIG_ID, "regenerationTicks", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(EndTower.CONFIG_ID, "attackDamageCap", -1.0), 0.0001);
        assertEquals(2000.0, config.ability(EndTower.CONFIG_ID, "dragonEvolution", -1.0), 0.0001);
        assertEquals(100.0, config.ability(EndTower.CONFIG_ID, "phantomScaleHealth", -1.0), 0.0001);
        assertEquals(0.2, config.ability(EndTower.CONFIG_ID, "phantomScaleStep", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "phantomScaleBase", -1.0), 0.0001);
        assertEquals(5.0, config.ability(EndTower.CONFIG_ID, "phantomScaleCap", -1.0), 0.0001);
        assertEquals(200.0, config.ability(EndTower.CONFIG_ID, "transferTicks", -1.0), 0.0001);
        assertEquals(30.0, config.ability(EndTower.CONFIG_ID, "transferHeal", -1.0), 0.0001);
        assertEquals(0.05, config.ability(EndTower.CONFIG_ID, "transferHealRatio", -1.0), 0.0001);
        assertEquals(0.50, config.ability(EndTower.CONFIG_ID, "roundHealthRatio", -1.0), 0.0001);
        assertEquals(0.04, config.ability(EndTower.CONFIG_ID, "permanentHealthRatio", -1.0), 0.0001);
        assertEquals(3000.0, config.ability(EndTower.CONFIG_ID, "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, config.ability(EndTower.CONFIG_ID, "healthScale", -1.0), 0.0001);
        assertEquals(0.66, config.ability(EndTower.CONFIG_ID, "roundDamageRatio", -1.0), 0.0001);
        assertEquals(0.04, config.ability(EndTower.CONFIG_ID, "permanentDamageRatio", -1.0), 0.0001);
        assertEquals(140.0, config.ability(EndTower.CONFIG_ID, "damageThreshold", -1.0), 0.0001);
        assertEquals(20.0, config.ability(EndTower.CONFIG_ID, "damageScale", -1.0), 0.0001);
        assertEquals(30.0, config.ability(EndTower.CONFIG_ID, "lifeStealStacks", -1.0), 0.0001);
        assertEquals(0.01, config.ability(EndTower.CONFIG_ID, "lifeStealStep", -1.0), 0.0001);
        assertEquals(0.10, config.ability(EndTower.CONFIG_ID, "lifeStealCap", -1.0), 0.0001);
        assertEquals(15.0, config.ability(EndTower.CONFIG_ID, "damageReductionStacks", -1.0), 0.0001);
        assertEquals(0.01, config.ability(EndTower.CONFIG_ID, "damageReductionStep", -1.0), 0.0001);
        assertEquals(0.20, config.ability(EndTower.CONFIG_ID, "damageReductionCap", -1.0), 0.0001);
        assertEquals(10.0, config.ability(EndTower.CONFIG_ID, "regenerationStacks", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "regenerationStep", -1.0), 0.0001);
        assertEquals(30.0, config.ability(EndTower.CONFIG_ID, "regenerationCap", -1.0), 0.0001);
        assertEquals(10.0, config.ability(EndTower.CONFIG_ID, "splash1", -1.0), 0.0001);
        assertEquals(35.0, config.ability(EndTower.CONFIG_ID, "splash2", -1.0), 0.0001);
        assertEquals(75.0, config.ability(EndTower.CONFIG_ID, "splash3", -1.0), 0.0001);
        assertEquals(150.0, config.ability(EndTower.CONFIG_ID, "splash4", -1.0), 0.0001);
        assertEquals(300.0, config.ability(EndTower.CONFIG_ID, "splash5", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "splashStep", -1.0), 0.0001);
        assertEquals(5.0, config.ability(EndTower.CONFIG_ID, "splashCap", -1.0), 0.0001);
        assertEquals(0.66, config.ability(EndTower.CONFIG_ID, "splashDamageRatio", -1.0), 0.0001);
        assertEquals(30.0, config.ability(EndTower.CONFIG_ID, "attackSpeedStacks", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "attackSpeedStep", -1.0), 0.0001);
        assertEquals(10.0, config.ability(EndTower.CONFIG_ID, "attackSpeedCap", -1.0), 0.0001);
        assertEquals(5.0, config.ability(EndTower.CONFIG_ID, "attackSpeedMinimumTicks", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "transferAttackSpeedStacks", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "transferAttackSpeedStep", -1.0), 0.0001);
        assertEquals(50.0, config.ability(EndTower.CONFIG_ID, "attackRangeStacks", -1.0), 0.0001);
        assertEquals(0.5, config.ability(EndTower.CONFIG_ID, "attackRangeStep", -1.0), 0.0001);
        assertEquals(3.0, config.ability(EndTower.CONFIG_ID, "attackRangeCap", -1.0), 0.0001);
        assertEquals(0.10, config.ability(EndTower.CONFIG_ID, "dragonFinalDamage", -1.0), 0.0001);
        assertEquals(2.0, config.ability(EndTower.CONFIG_ID, "dragonRangeBonus", -1.0), 0.0001);
        assertEquals(0.10, config.ability(EndTowers.T1_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
        assertEquals(0.30, config.ability(EndTowers.T2_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
        assertEquals(0.50, config.ability(EndTowers.T3_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
    }

    @Test
    void endJobAllowsEveryEndTowerOnly() {
        EndTowerJob job = new EndTowerJob();
        assertTrue(job.canUseTower(null, EndTowers.BASE_END_TOWER));
        assertTrue(job.canUseTower(null, EndTowers.T1_ENDERMITE_TOWER));
        assertTrue(job.canUseTower(null, EndTowers.T3_END_CRYSTAL_TOWER));
        assertTrue(job.canUseTower(null, EndTowers.T1_SHULKER_TOWER));
        assertTrue(job.canUseTower(null, EndTowers.T3_SHULKER_TOWER));
        assertFalse(job.canUseTower(null, AnimalTowers.T1_PIG_TOWER));
    }

    @Test
    void catalogRegistersDragonAndTwoUpgradePaths() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        assertEquals(50L, EndTowers.T1_ENDERMITE_TOWER.mineralCost());
        assertEquals(100L, EndTowers.T2_ENDERMAN_TOWER.mineralCost());
        assertEquals(150L, EndTowers.T3_END_CRYSTAL_TOWER.mineralCost());
        assertEquals(50L, EndTowers.T1_SHULKER_TOWER.mineralCost());
        assertEquals(100L, EndTowers.T2_SHULKER_TOWER.mineralCost());
        assertEquals(150L, EndTowers.T3_SHULKER_TOWER.mineralCost());
        assertStarter(EndTowers.BASE_END_TOWER.id(), "엔더 드래곤");
        assertStarter(EndTowers.T1_SHULKER_TOWER.id(), "셜커");
        assertUpgrade(EndTowers.T1_SHULKER_TOWER.id(), EndTowers.T2_SHULKER_TOWER.id(), "견고한 셜커", 100);
        assertUpgrade(EndTowers.T2_SHULKER_TOWER.id(), EndTowers.T3_SHULKER_TOWER.id(), "완강한 셜커", 150);
        assertStarter(EndTowers.T1_ENDERMITE_TOWER.id(), "엔더마이트");
        assertUpgrade(EndTowers.T1_ENDERMITE_TOWER.id(), EndTowers.T2_ENDERMAN_TOWER.id(), "엔더맨", 100);
        assertUpgrade(EndTowers.T2_ENDERMAN_TOWER.id(), EndTowers.T3_END_CRYSTAL_TOWER.id(), "엔드 수정", 150);
    }

    @Test
    void shulkerLineUsesShulkerVisuals() {
        assertEquals("minecraft:shulker", EndTowers.T1_SHULKER_TOWER.visual().entityTypeId());
        assertEquals("minecraft:shulker", EndTowers.T2_SHULKER_TOWER.visual().entityTypeId());
        assertEquals("minecraft:shulker", EndTowers.T3_SHULKER_TOWER.visual().entityTypeId());
        assertFalse(EndTowers.T1_SHULKER_TOWER.visual().properties().containsKey("shulker_color"));
        assertEquals(DyeColor.PURPLE, EndTowers.T2_SHULKER_TOWER.visual().properties().get("shulker_color"));
        assertEquals(DyeColor.BLACK, EndTowers.T3_SHULKER_TOWER.visual().properties().get("shulker_color"));
    }

    @Test
    void upgradePricesComeFromBalanceConfig() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Long> upgradeCosts = new LinkedHashMap<>(defaults.upgradeCosts());
        upgradeCosts.put(
                TowerBalanceConfig.upgradeKey(
                        EndTowers.T1_ENDERMITE_TOWER.id(),
                        EndTowers.T2_ENDERMAN_TOWER.id()
                ),
                1L
        );
        TowerBalanceConfig custom = new TowerBalanceConfig(
                defaults.towers(),
                upgradeCosts,
                defaults.abilities()
        );
        ProductionTowerCatalogs.reloadBuiltIns(custom);
        assertEquals(
                1L,
                ProductionTowerCatalog.upgrade(
                        EndTowers.T1_ENDERMITE_TOWER,
                        EndTowers.T2_ENDERMAN_TOWER.id()
                ).orElseThrow().mineralCost()
        );
    }

    @Test
    void catalogCreatesEndRuntime() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        var entry = ProductionTowerCatalog.find(EndTowers.BASE_END_TOWER.id()).orElseThrow();
        var tower = entry.create(
                UUID.nameUUIDFromBytes("end-runtime".getBytes()),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        assertInstanceOf(EndTower.class, tower);
        assertEquals(0.0, tower.adjustAttackRange(tower.type().range()), 0.0001);
    }

    private static void assertStarter(String towerId, String displayName) {
        var entry = ProductionTowerCatalog.find(towerId).orElseThrow();
        assertTrue(entry.starter());
        assertEquals(displayName, entry.type().displayName());
    }

    private static void assertUpgrade(
            String fromTowerId,
            String upgradeId,
            String displayName,
            long cost
    ) {
        var from = ProductionTowerCatalog.find(fromTowerId).orElseThrow().type();
        var upgrade = ProductionTowerCatalog.upgrade(from, upgradeId).orElseThrow();
        assertEquals(displayName, upgrade.displayName());
        assertEquals(cost, upgrade.mineralCost());
    }

}
