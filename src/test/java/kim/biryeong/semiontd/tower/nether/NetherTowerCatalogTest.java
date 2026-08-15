package kim.biryeong.semiontd.tower.nether;

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
import kim.biryeong.semiontd.job.NetherTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NetherTowerCatalogTest {
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
    void defaultBalanceConfigIncludesNetherValues() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertTrue(config.towers().containsKey(NetherTowers.T1_STRIDER.id()));
        assertTrue(config.towers().containsKey(NetherTowers.T3_WITHER.id()));
        assertEquals(0.0667, config.ability(NetherTower.CONFIG_ID, "netherDecayMaxHealthRatioPerSecond", -1), 0.0001);
        assertEquals(0.20, config.ability(NetherTower.CONFIG_ID, "zombieDecayMaxHealthRatioPerSecond", -1), 0.0001);
        assertEquals(1.0, config.ability(NetherTower.CONFIG_ID, "zombieReviveHealthRatio", -1), 0.0001);
        assertEquals(0.70, config.ability(NetherTower.CONFIG_ID, "lowHealthThreshold", -1), 0.0001);
        assertEquals(0.35, config.ability(NetherTower.CONFIG_ID, "criticalHealthThreshold", -1), 0.0001);
        assertEquals(0.12, config.ability(NetherTowers.T2_PIGLIN.id(), "lifeStealBonus", -1), 0.0001);
        assertEquals(0.16, config.ability(NetherTowers.T3_PIGLIN_BRUTE.id(), "lifeStealBonus", -1), 0.0001);
        assertEquals(0.75, config.ability(NetherTowers.T3_PIGLIN_BRUTE.id(), "tankDamageBonus", -1), 0.0001);
        assertEquals(0.50, config.ability(NetherTowers.T1_HOGLIN.id(), "splashDamageRatio", -1), 0.0001);
        assertEquals(0.75, config.ability(NetherTowers.T2_ZOGLIN.id(), "splashDamageRatio", -1), 0.0001);
        assertEquals(1.00, config.ability(NetherTowers.T3_ZOMBIFIED_PIGLIN.id(), "splashDamageRatio", -1), 0.0001);
        assertEquals(0.30, config.ability(NetherTowers.T1_MAGMA_CUBE.id(), "missingHealthAttackSpeedBonusCap", -1), 0.0001);
        assertEquals(0.50, config.ability(NetherTowers.T2_BLAZE.id(), "missingHealthAttackSpeedBonusCap", -1), 0.0001);
        assertEquals(0.75, config.ability(NetherTowers.T3_GHAST.id(), "missingHealthAttackSpeedBonusCap", -1), 0.0001);
        assertEquals(1.50, config.ability(NetherTowers.T1_MAGMA_CUBE.id(), "pulseDamageRatio", -1), 0.0001);
        assertEquals(2.50, config.ability(NetherTowers.T1_MAGMA_CUBE.id(), "zombieTransitionPulseDamageRatio", -1), 0.0001);
        assertEquals(0.40, config.ability(NetherTowers.T3_GHAST.id(), "criticalMarkDamageTakenBonus", -1), 0.0001);
        assertEquals(0.50, config.ability(NetherTowers.T2_WITHER_SKELETON.id(), "lowTargetDamageBonus", -1), 0.0001);
        assertEquals(0.75, config.ability(NetherTowers.T3_WITHER.id(), "lowTargetDamageBonus", -1), 0.0001);
        assertEquals(0.0, config.ability(NetherTowers.T3_WITHER.id(), "zombieLifeStealRatio", -1), 0.0001);
    }

    @Test
    void netherJobOnlyAllowsNetherTowers() {
        NetherTowerJob job = new NetherTowerJob();

        assertTrue(job.canUseTower(null, NetherTowers.T1_STRIDER));
        assertTrue(job.canUseTower(null, NetherTowers.T3_WITHER));
        assertFalse(job.canUseTower(null, AnimalTowers.T1_PIG_TOWER));
    }

    @Test
    void catalogRegistersFourStarterPathsWithMobOnlyNames() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        assertStarter(NetherTowers.T1_STRIDER.id(), "스트라이더");
        assertStarter(NetherTowers.T1_HOGLIN.id(), "호글린");
        assertStarter(NetherTowers.T1_MAGMA_CUBE.id(), "마그마 큐브");
        assertStarter(NetherTowers.T1_SKELETON.id(), "스켈레톤");
        assertUpgrade(NetherTowers.T1_STRIDER.id(), NetherTowers.T2_PIGLIN.id(), "피글린", 100);
        assertUpgrade(NetherTowers.T2_PIGLIN.id(), NetherTowers.T3_PIGLIN_BRUTE.id(), "피글린 야수", 200);
        assertUpgrade(NetherTowers.T2_WITHER_SKELETON.id(), NetherTowers.T3_WITHER.id(), "위더", 200);
    }

    @Test
    void upgradePricesComeFromUpgradeCostsNotTargetMineralCost() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Long> upgradeCosts = new LinkedHashMap<>(defaults.upgradeCosts());
        upgradeCosts.put(TowerBalanceConfig.upgradeKey(NetherTowers.T1_STRIDER.id(), NetherTowers.T2_PIGLIN.id()), 1L);
        TowerBalanceConfig custom = new TowerBalanceConfig(defaults.towers(), upgradeCosts, defaults.abilities());

        ProductionTowerCatalogs.reloadBuiltIns(custom);

        assertEquals(1L, ProductionTowerCatalog.upgrade(NetherTowers.T1_STRIDER, NetherTowers.T2_PIGLIN.id()).orElseThrow().mineralCost());
        assertEquals(110L, NetherTowers.T2_PIGLIN.mineralCost());
    }

    @Test
    void catalogCreatesNetherTowerRuntime() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var entry = ProductionTowerCatalog.find(NetherTowers.T1_STRIDER.id()).orElseThrow();
        var tower = entry.create(UUID.nameUUIDFromBytes("nether-runtime".getBytes()), TeamId.RED, 1, new GridPosition(0, 64, 0));

        assertInstanceOf(NetherTower.class, tower);
        assertEquals(NetherTowerState.NETHER, ((NetherTower) tower).state());
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
}
