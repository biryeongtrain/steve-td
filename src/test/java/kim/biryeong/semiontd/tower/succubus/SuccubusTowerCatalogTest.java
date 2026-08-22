package kim.biryeong.semiontd.tower.succubus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SuccubusTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuccubusTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("succubus-owner".getBytes(StandardCharsets.UTF_8));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reload() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogContainsFourPathsAndTheSuccubus() {
        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> SuccubusTowers.isSuccubusTower(entry.type())).toList();

        assertEquals(13, entries.size());
        assertEquals(5, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertEquals("succubus", SuccubusTowers.SUCCUBUS.id());
        assertEquals("서큐버스 타워", SuccubusTowers.SUCCUBUS.displayName());
        assertEquals(120, SuccubusTowers.SUCCUBUS.mineralCost());
    }

    @Test
    void everyTowerHasOneOwningBuilderAndUsesTheSharedRuntime() {
        var job = JobRegistry.find(SuccubusTowerJob.ID).orElseThrow();
        for (var type : SuccubusTowers.all()) {
            assertTrue(job.includesTowerInCatalog(type));
            assertEquals(1, JobRegistry.all().stream().filter(owner -> owner.includesTowerInCatalog(type)).count());
            assertInstanceOf(SuccubusTower.class, ProductionTowerCatalog.find(type.id()).orElseThrow()
                    .create(OWNER, TeamId.RED, 1, new GridPosition(0, 80, 0)));
        }
    }

    @Test
    void upgradeCostsAndDreamBalanceMatchTheShippedConfig() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        config.validateForRuntime();

        assertUpgrade(SuccubusTowers.DREAM_DUST_T1, SuccubusTowers.DREAM_DUST_T2, 100);
        assertUpgrade(SuccubusTowers.SLEEPWALKER_T2, SuccubusTowers.SLEEPWALKER_T3, 230);
        assertUpgrade(SuccubusTowers.LULLABY_T1, SuccubusTowers.LULLABY_T2, 120);
        assertUpgrade(SuccubusTowers.NIGHTMARE_T2, SuccubusTowers.NIGHTMARE_T3, 270);
        assertEquals(10, config.abilityInt(SuccubusBalance.CONFIG_ID, "maxStacks", -1));
        assertEquals(100, config.abilityInt(SuccubusBalance.CONFIG_ID, "sleepDurationTicks", -1));
        assertEquals(40, config.abilityInt(SuccubusBalance.CONFIG_ID, "towerSleepDurationTicks", -1));
        assertEquals(0.50, config.ability(SuccubusBalance.CONFIG_ID, "succubusAmplification", -1));
        assertEquals(0.07, config.ability(SuccubusBalance.CONFIG_ID, "allyDamagePerStack", -1));
        assertEquals(0.035, config.ability(SuccubusBalance.CONFIG_ID, "allyAttackSpeedPerStack", -1));
        assertEquals(0.20, config.ability(SuccubusBalance.CONFIG_ID, "monsterWakeBonusDamage", -1));
        assertEquals(0.10, config.ability(SuccubusTowers.SLEEPWALKER_T1.id(), "dreamDamageReduction", -1));
        assertEquals(0.15, config.ability(SuccubusTowers.SLEEPWALKER_T2.id(), "dreamDamageReduction", -1));
        assertEquals(0.20, config.ability(SuccubusTowers.SLEEPWALKER_T3.id(), "dreamDamageReduction", -1));
        assertEquals(0.03, config.ability(SuccubusBalance.CONFIG_ID, "absorbAttackRatio", -1));
        assertEquals(0.01, config.ability(SuccubusBalance.CONFIG_ID, "absorbMaxHealthRatio", -1));
        assertEquals(1, config.abilityInt(SuccubusTowers.SLEEPWALKER_T1.id(), "counterStacks", -1));
        assertEquals(2, config.abilityInt(SuccubusTowers.SLEEPWALKER_T2.id(), "counterStacks", -1));
        assertEquals(3, config.abilityInt(SuccubusTowers.SLEEPWALKER_T3.id(), "counterStacks", -1));
        assertEquals(2, config.abilityInt(SuccubusTowers.LULLABY_T1.id(), "allyMaxTargets", -1));
        assertEquals(3, config.abilityInt(SuccubusTowers.LULLABY_T1.id(), "enemyMaxTargets", -1));
        assertEquals(3, config.abilityInt(SuccubusTowers.LULLABY_T2.id(), "allyMaxTargets", -1));
        assertEquals(5, config.abilityInt(SuccubusTowers.LULLABY_T2.id(), "enemyMaxTargets", -1));
        assertEquals(4, config.abilityInt(SuccubusTowers.LULLABY_T3.id(), "allyMaxTargets", -1));
        assertEquals(7, config.abilityInt(SuccubusTowers.LULLABY_T3.id(), "enemyMaxTargets", -1));
        assertEquals(14, SuccubusTowers.DREAM_DUST_T1.attackIntervalTicks());
        assertEquals(12, SuccubusTowers.DREAM_DUST_T2.attackIntervalTicks());
        assertEquals(10, SuccubusTowers.DREAM_DUST_T3.attackIntervalTicks());
        assertEquals(16, SuccubusTowers.NIGHTMARE_T1.attackIntervalTicks());
        assertEquals(14, SuccubusTowers.NIGHTMARE_T2.attackIntervalTicks());
        assertEquals(12, SuccubusTowers.NIGHTMARE_T3.attackIntervalTicks());
    }

    @Test
    void sleepwalkersArePurpleSheepAndLullabiesAreWhiteRabbits() {
        for (var type : List.of(SuccubusTowers.SLEEPWALKER_T1, SuccubusTowers.SLEEPWALKER_T2,
                SuccubusTowers.SLEEPWALKER_T3)) {
            assertEquals("minecraft:sheep", type.visual().entityTypeId());
            assertEquals(DyeColor.PURPLE, type.visual().properties().get("sheep_color"));
        }
        for (var type : List.of(SuccubusTowers.LULLABY_T1, SuccubusTowers.LULLABY_T2,
                SuccubusTowers.LULLABY_T3)) {
            assertEquals("minecraft:rabbit", type.visual().entityTypeId());
            assertEquals(Rabbit.Variant.WHITE, type.visual().properties().get("rabbit_variant"));
        }
    }

    private static void assertUpgrade(kim.biryeong.semiontd.tower.TowerType from,
                                      kim.biryeong.semiontd.tower.TowerType to, long cost) {
        assertEquals(cost, ProductionTowerCatalog.upgrade(from, to.id()).orElseThrow().mineralCost());
    }
}
