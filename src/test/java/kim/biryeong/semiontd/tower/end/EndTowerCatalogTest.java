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
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        assertEquals(150.0, config.ability(EndTower.CONFIG_ID, "damageThreshold", -1.0), 0.0001);
        assertEquals(25.0, config.ability(EndTower.CONFIG_ID, "damageScale", -1.0), 0.0001);
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
    void shulkerDescriptionsShowTierDamageReduction() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EndTowers.T1_SHULKER_TOWER).description()).contains("10%"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EndTowers.T2_SHULKER_TOWER).description()).contains("30%"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(EndTowers.T3_SHULKER_TOWER).description()).contains("50%"));
    }

    @Test
    void dragonDescriptionUsesCurrentCompactEndDescription() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        assertEquals(10.0, EndTowers.BASE_END_TOWER.damage(), 0.0001);
        String description = String.join(
                "\n",
                TowerBalanceRuntime.resolve(EndTowers.BASE_END_TOWER).description()
        );
        String plainDescription = description.replaceAll("<[^>]+>", "");
        assertTrue(plainDescription.contains("알로 소환되며, 라운드 시작 시 아기 드래곤으로 변합니다."));
        assertTrue(plainDescription.contains("아기 드래곤 크기는 최대 체력 100당 0.2씩 증가합니다."));
        assertTrue(plainDescription.contains("최대 체력 2000 이상이면 엔더 드래곤으로 진화합니다."));
        assertTrue(plainDescription.contains("엔더 드래곤으로 진화하면 추가 능력을 획득합니다."));
        assertTrue(plainDescription.contains("힘 전달 10초 후 타워 사망, 체력 30을 회복합니다."));
        assertTrue(plainDescription.contains("전달 중인 셜커 타워의 최대 체력의 5%를 초당 회복합니다."));
        assertTrue(plainDescription.contains("타워 체력의 50%를 임시 획득, 4% 영구 누적"));
        assertTrue(plainDescription.contains("타워 피해의 66%를 임시 획득, 4% 영구 누적"));
        assertFalse(description.contains("{ability."));
        assertTrue(description.contains("<#cc00fa>아기 드래곤</#cc00fa>"));
        assertTrue(description.contains("<#cc00fa>엔더 드래곤</#cc00fa>"));
        assertTrue(description.contains("전달 중인 셜커 타워의 <#fc5454>최대 체력</#fc5454>의 <#fc5454>5%</#fc5454>를 초당 회복합니다."));
        assertTrue(description.contains("<#fc5454>체력"));
        assertTrue(description.contains("<#ec8d34>피해"));
    }

    @Test
    void endJobDescriptionUsesCurrentEndDescription() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        List<Component> lines = new EndTowerJob().description();
        String description = lines.stream()
                .map(Component::getString)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(description.contains("아군 타워의 체력과 피해를"));
        assertTrue(description.contains("체력 50%, 피해 66%를"));
        assertTrue(description.contains("체력 4%, 피해 4%를 영구 누적합니다."));
        assertTrue(description.contains("엔더 드래곤으로 진화하면"));
        assertTrue(description.contains("추가 고유 능력을 획득합니다."));
        assertEquals(
                Component.literal("").withStyle(ChatFormatting.DARK_RED).getStyle().getColor(),
                lines.getLast().getStyle().getColor()
        );
    }

    @Test
    void everyEndFeederRegistersItsDescriptionTemplate() {
        assertDescription(EndTowers.T1_ENDERMITE_TOWER, "피해가 낮은 엔더마이트", "엔더 드래곤의 피해");
        assertDescription(EndTowers.T2_ENDERMAN_TOWER, "피해가 보통인 엔더맨", "엔더 드래곤의 피해");
        assertDescription(EndTowers.T3_END_CRYSTAL_TOWER, "피해가 높은 엔드 수정", "엔더 드래곤의 피해");
        assertDescription(EndTowers.T1_SHULKER_TOWER, "체력이 낮은 셜커", "엔더 드래곤의 체력");
        assertDescription(EndTowers.T2_SHULKER_TOWER, "체력이 보통인 견고한 셜커", "엔더 드래곤의 체력");
        assertDescription(EndTowers.T3_SHULKER_TOWER, "체력이 높은 완강한 셜커", "엔더 드래곤의 체력");
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

    private static void assertDescription(
            kim.biryeong.semiontd.tower.TowerType towerType,
            String summary,
            String effect
    ) {
        String description = String.join(
                "\n",
                TowerDescriptionRegistry.describe(towerType).orElseThrow()
        ).replaceAll("<[^>]+>", "");
        assertTrue(description.contains(summary));
        assertTrue(description.contains(effect));
    }
}
