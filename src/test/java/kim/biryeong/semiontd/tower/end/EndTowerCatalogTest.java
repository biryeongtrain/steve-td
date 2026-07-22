package kim.biryeong.semiontd.tower.end;

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
import kim.biryeong.semiontd.job.EndTowerJob;
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
        assertEquals(-1.0, config.ability(EndTower.CONFIG_ID, "hatchDelayTicks", -1.0), 0.0001);
        assertEquals(2000.0, config.ability(EndTower.CONFIG_ID, "dragonEvolutionMaxHealth", -1.0), 0.0001);
        assertEquals(200.0, config.ability(EndTower.CONFIG_ID, "absorptionDurationTicks", -1.0), 0.0001);
        assertEquals(50.0, config.ability(EndTower.CONFIG_ID, "absorptionHealAmount", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "transferHealingPerTower", -1.0), 0.0001);
        assertEquals(20.0, config.ability(EndTower.CONFIG_ID, "transferHealingIntervalTicks", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "roundAbsorptionAttackIntervalEvery", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "roundAbsorptionAttackIntervalReductionTicks", -1.0), 0.0001);
        assertEquals(0.50, config.ability(EndTower.CONFIG_ID, "roundHealthRatio", -1.0), 0.0001);
        assertEquals(0.50, config.ability(EndTower.CONFIG_ID, "roundDamageRatio", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(EndTower.CONFIG_ID, "roundStatBonusCapRatio", -1.0), 0.0001);
        assertEquals(0.05, config.ability(EndTower.CONFIG_ID, "permanentHealthRatio", -1.0), 0.0001);
        assertEquals(0.05, config.ability(EndTower.CONFIG_ID, "permanentDamageRatio", -1.0), 0.0001);
        assertEquals(30.0, config.ability(EndTower.CONFIG_ID, "endCrystalAttackIntervalEvery", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "attackIntervalReductionPerStep", -1.0), 0.0001);
        assertEquals(50.0, config.ability(EndTower.CONFIG_ID, "endCrystalAttackRangeEvery", -1.0), 0.0001);
        assertEquals(0.5, config.ability(EndTower.CONFIG_ID, "attackRangePerStep", -1.0), 0.0001);
        assertEquals(3.0, config.ability(EndTower.CONFIG_ID, "attackRangeCap", -1.0), 0.0001);
        assertEquals(15.0, config.ability(EndTower.CONFIG_ID, "shulkerLifeStealEvery", -1.0), 0.0001);
        assertEquals(15.0, config.ability(EndTower.CONFIG_ID, "endCrystalSplashThreshold1", -1.0), 0.0001);
        assertEquals(60.0, config.ability(EndTower.CONFIG_ID, "endCrystalSplashThreshold2", -1.0), 0.0001);
        assertEquals(150.0, config.ability(EndTower.CONFIG_ID, "endCrystalSplashThreshold3", -1.0), 0.0001);
        assertEquals(300.0, config.ability(EndTower.CONFIG_ID, "endCrystalSplashThreshold4", -1.0), 0.0001);
        assertEquals(4.0, config.ability(EndTower.CONFIG_ID, "splashRadiusCap", -1.0), 0.0001);
        assertEquals(0.60, config.ability(EndTower.CONFIG_ID, "splashDamageRatio", -1.0), 0.0001);
        assertEquals(60.0, config.ability(EndTower.CONFIG_ID, "shulkerReductionEvery", -1.0), 0.0001);
        assertEquals(0.04, config.ability(EndTower.CONFIG_ID, "damageReductionPerStep", -1.0), 0.0001);
        assertEquals(0.20, config.ability(EndTower.CONFIG_ID, "lifeStealCap", -1.0), 0.0001);
        assertEquals(30.0, config.ability(EndTower.CONFIG_ID, "shulkerRegenerationEvery", -1.0), 0.0001);
        assertEquals(1.0, config.ability(EndTower.CONFIG_ID, "regenerationPerStep", -1.0), 0.0001);
        assertEquals(10.0, config.ability(EndTower.CONFIG_ID, "regenerationCap", -1.0), 0.0001);
        assertEquals(20.0, config.ability(EndTower.CONFIG_ID, "regenerationIntervalTicks", -1.0), 0.0001);
        assertEquals(0.20, config.ability(EndTower.CONFIG_ID, "damageReductionCap", -1.0), 0.0001);
        assertEquals(10.0, config.ability(EndTower.CONFIG_ID, "maxAttackIntervalReductionTicks", -1.0), 0.0001);
        assertEquals(5.0, config.ability(EndTower.CONFIG_ID, "minimumAttackIntervalTicks", -1.0), 0.0001);
        assertEquals(0.30, config.ability(EndTower.CONFIG_ID, "dragonFinalDamageBonus", -1.0), 0.0001);
        assertEquals(0.10, config.ability(EndTower.CONFIG_ID, "dragonIncomeDebuffResistance", -1.0), 0.0001);
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
        assertEquals(80L, EndTowers.T2_ENDERMAN_TOWER.mineralCost());
        assertEquals(130L, EndTowers.T3_END_CRYSTAL_TOWER.mineralCost());
        assertEquals(50L, EndTowers.T1_SHULKER_TOWER.mineralCost());
        assertEquals(80L, EndTowers.T2_SHULKER_TOWER.mineralCost());
        assertEquals(130L, EndTowers.T3_SHULKER_TOWER.mineralCost());

        assertStarter(EndTowers.BASE_END_TOWER.id(), "엔더 드래곤");
        assertStarter(EndTowers.T1_ENDERMITE_TOWER.id(), "엔더 마이트");
        assertStarter(EndTowers.T1_SHULKER_TOWER.id(), "셜커");
        assertUpgrade(EndTowers.T1_ENDERMITE_TOWER.id(), EndTowers.T2_ENDERMAN_TOWER.id(), "엔더맨", 80);
        assertUpgrade(EndTowers.T2_ENDERMAN_TOWER.id(), EndTowers.T3_END_CRYSTAL_TOWER.id(), "엔드 수정", 130);
        assertUpgrade(EndTowers.T1_SHULKER_TOWER.id(), EndTowers.T2_SHULKER_TOWER.id(), "견고한 셜커", 80);
        assertUpgrade(EndTowers.T2_SHULKER_TOWER.id(), EndTowers.T3_SHULKER_TOWER.id(), "완강한 셜커", 130);
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
    void shulkerDescriptionsShowTierDamageReduction() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());

        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EndTowers.T1_SHULKER_TOWER).description()).contains("10%"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EndTowers.T2_SHULKER_TOWER).description()).contains("30%"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EndTowers.T3_SHULKER_TOWER).description()).contains("50%"));
    }

    @Test
    void dragonDescriptionUsesConfiguredAbilityValues() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());

        assertEquals(10.0, EndTowers.BASE_END_TOWER.damage(), 0.0001);
        String description = String.join("\n", TowerBalanceRuntime.resolve(EndTowers.BASE_END_TOWER).description());
        String plainDescription = description.replaceAll("<[^>]+>", "");

        assertTrue(plainDescription.contains("알로 소환되며"));
        assertTrue(plainDescription.contains("라운드 시작 시 아기 드래곤"));
        assertTrue(plainDescription.contains("이상이면"));
        assertTrue(plainDescription.contains("아기 드래곤 크기는 최대 체력 100당 0.2씩 증가합니다."));
        assertTrue(plainDescription.contains("10초"));
        assertTrue(plainDescription.contains("전달 중 타워 당 체력을 초당 +1 재생합니다."));
        assertTrue(description.contains("<red>체력</red>을 초당 <green>+1 재생</green>"));
        assertTrue(plainDescription.contains("타워 공격력의 50%를 임시 획득"));
        assertTrue(plainDescription.contains("공격 범위: 엔드 수정 15 / 60 / 150 / 300스택에서 +1"));
        assertTrue(plainDescription.contains("엔드 수정 30스택마다 -1틱"));
        assertTrue(plainDescription.contains("사거리: 엔드 수정 50스택마다 +0.5블록"));
        assertTrue(plainDescription.contains("타워 체력의 50%를 임시 획득"));
        assertTrue(plainDescription.contains("셜커 15스택마다 +1%"));
        assertTrue(plainDescription.contains("받는 피해 감소: 셜커 60스택마다 +4%"));
        assertTrue(plainDescription.contains("셜커 30스택마다 초당 +1"));
        assertFalse(plainDescription.contains("(최대"));
        assertTrue(plainDescription.contains("엔더 드래곤 진화 시 최종 피해: +30% / 저항: +10%"));
        assertTrue(description.contains("<dark_purple>엔더 드래곤</dark_purple> 진화 시"));
        assertTrue(description.contains("<dark_red>공격력</dark_red>"));
        assertTrue(description.contains("<red>체력</red>"));
        assertTrue(description.contains("<blue>받는 피해 감소</blue>"));
    }

    @Test
    void everyEndFeederRegistersItsDescriptionTemplate() {
        assertDescription(EndTowers.T1_ENDERMITE_TOWER, "공격력이 높은 엔더마이트", "엔더 드래곤의 공격 능력");
        assertDescription(EndTowers.T2_ENDERMAN_TOWER, "공격력이 높은 엔더맨", "엔더 드래곤의 공격 능력");
        assertDescription(EndTowers.T3_END_CRYSTAL_TOWER, "공격력이 매우 높은 엔드 수정", "엔더 드래곤의 공격 능력");
        assertDescription(EndTowers.T1_SHULKER_TOWER, "체력이 높은 셜커", "엔더 드래곤의 내구력");
        assertDescription(EndTowers.T2_SHULKER_TOWER, "체력이 높은 견고한 셜커", "엔더 드래곤의 내구력");
        assertDescription(EndTowers.T3_SHULKER_TOWER, "체력이 매우 높은 완강한 셜커", "엔더 드래곤의 내구력");
    }

    @Test
    void upgradePricesComeFromBalanceConfig() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Long> upgradeCosts = new LinkedHashMap<>(defaults.upgradeCosts());
        upgradeCosts.put(
                TowerBalanceConfig.upgradeKey(EndTowers.T1_ENDERMITE_TOWER.id(), EndTowers.T2_ENDERMAN_TOWER.id()),
                1L
        );
        TowerBalanceConfig custom = new TowerBalanceConfig(defaults.towers(), upgradeCosts, defaults.abilities());

        ProductionTowerCatalogs.reloadBuiltIns(custom);

        assertEquals(1L, ProductionTowerCatalog.upgrade(
                EndTowers.T1_ENDERMITE_TOWER,
                EndTowers.T2_ENDERMAN_TOWER.id()
        ).orElseThrow().mineralCost());
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
