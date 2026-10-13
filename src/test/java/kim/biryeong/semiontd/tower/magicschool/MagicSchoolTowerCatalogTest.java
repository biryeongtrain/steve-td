package kim.biryeong.semiontd.tower.magicschool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.JobAvailabilityConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.tower.vfx.BuilderPalette;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.MagicSchoolTowerJob;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MagicSchoolTowerCatalogTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaults() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
    }

    @Test
    void creativeBuilderOwnsExactlyTwoTierOneStarters() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
        var job = JobRegistry.find(MagicSchoolTowerJob.ID).orElseThrow();
        assertEquals("마법학교 빌더", job.displayName().getString());
        assertTrue(JobRegistry.isEnabled(job));
        assertTrue(JobRegistry.creativeBuilders().contains(job));
        assertFalse(JobRegistry.officialBuilders().contains(job));
        assertEquals(10, ProductionTowerCatalog.all().stream()
                .filter(entry -> job.includesTowerInCatalog(entry.type())).count());
        for (var type : MagicSchoolTowers.all()) {
            var entry = ProductionTowerCatalog.find(type.id()).orElseThrow();
            int tier = MagicSchoolTowers.isArchWizard(type) ? 3 : MagicSchoolTowers.isHouseWizard(type) ? 2 : 1;
            assertEquals(tier == 1, entry.starter());
            assertEquals(tier, entry.tier());
            assertEquals(ProductionTowerCatalog.Availability.JOB, entry.availability());
            assertEquals(MagicSchoolTowers.isFreshman(type) ? 4 : MagicSchoolTowers.isHouseWizard(type) ? 1 : 0,
                    ProductionTowerCatalog.upgrades(entry.type()).size());
            assertEquals(MagicSchoolTowers.isHogwarts(type) ? 0 : 2, TowerCapacity.slotCost(type));
            if (MagicSchoolTowers.isHogwarts(type)) {
                assertEquals((double) tier, entry.type().maxHealth());
                assertFalse(MagicSchoolTowers.isWizard(type));
                assertEquals(Blocks.ENCHANTING_TABLE.defaultBlockState(), BlockDisplayVisual.blockState(type.visual()));
            }
            assertEquals(BuilderPalette.MAGIC_SCHOOL, TowerVfxService.paletteFor(entry.type()));
            assertEquals(1, JobRegistry.all().stream().filter(candidate -> candidate.includesTowerInCatalog(type)).count());
        }
        assertEquals(Blocks.ENCHANTING_TABLE.defaultBlockState(), BlockDisplayVisual.blockState(MagicSchoolTowers.HOGWARTS.visual()));
        assertEquals(2, ProductionTowerCatalog.all().stream()
                .filter(entry -> entry.starter() && job.includesTowerInCatalog(entry.type())).count());
        assertTrue(ProductionTowerCatalog.upgrades(MagicSchoolTowers.HOGWARTS).isEmpty());
        assertTrue(ProductionTowerCatalog.find("magic_school_hogwarts_t2").isEmpty());
        assertTrue(ProductionTowerCatalog.find("magic_school_hogwarts_t3").isEmpty());
        for (var type : MagicSchoolTowers.houseWizards()) {
            assertEquals(200, ProductionTowerCatalog.upgrade(MagicSchoolTowers.FRESHMAN, type.id()).orElseThrow().mineralCost());
        }
    }

    @Test
    void houseDifferencesAreBaseStatsOnEveryUpgradeTarget() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        for (var house : MagicSchoolTowers.houseWizards()) {
            var target = ProductionTowerCatalog.upgrade(MagicSchoolTowers.FRESHMAN, house.id()).orElseThrow().targetType();
            assertEquals(house == MagicSchoolTowers.HUFFLEPUFF ? 374 : 340, target.maxHealth());
            assertEquals(house == MagicSchoolTowers.SLYTHERIN ? 55 : 50, target.damage());
            assertEquals(house == MagicSchoolTowers.GRYFFINDOR ? 17 : 18, target.attackIntervalTicks());
            var wizard = (MagicSchoolWizardTower) ProductionTowerCatalog.find(house.id()).orElseThrow()
                    .create(UUID.randomUUID(), TeamId.RED, 1, new GridPosition(1, 64, 1));
            assertEquals(target.maxHealth(), wizard.effectBaseMaxHealth(), 1e-8);
            assertEquals(target.damage(), wizard.modifyAttackDamage(null, null, target.damage()), 1e-8);
            assertEquals(target.attackIntervalTicks(), wizard.adjustAttackInterval(target.attackIntervalTicks()));
            assertTrue(target.description().stream().noneMatch(line -> line.contains("{stat.") || line.contains("{ability.")));
        }
    }

    @Test
    void archwizardsUpgradeOnlyFromTheirOwnHouseWithVisibleBaseDifferences() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        for (var house : MagicSchoolTowers.houseWizards()) {
            var arch = MagicSchoolTowers.archWizardFor(house);
            var options = ProductionTowerCatalog.upgrades(house);
            assertEquals(1, options.size());
            var target = options.getFirst().targetType();
            assertEquals(arch.id(), target.id());
            assertEquals(450, options.getFirst().mineralCost());
            assertEquals(house == MagicSchoolTowers.HUFFLEPUFF ? 550 : 500, target.maxHealth());
            assertEquals(house == MagicSchoolTowers.SLYTHERIN ? 88 : 80, target.damage());
            assertEquals(house == MagicSchoolTowers.GRYFFINDOR ? 11 : 12, target.attackIntervalTicks());
            assertEquals(10, target.range());
            assertTrue(ProductionTowerCatalog.upgrade(MagicSchoolTowers.FRESHMAN, arch.id()).isEmpty());
            assertTrue(ProductionTowerCatalog.upgrades(target).isEmpty());
            for (var other : MagicSchoolTowers.houseWizards()) {
                assertEquals(house == other, ProductionTowerCatalog.upgrade(other, arch.id()).isPresent());
            }
            assertTrue(target.description().stream().noneMatch(line -> line.contains("{stat.") || line.contains("{ability.")));
        }
    }

    @Test
    void bundledDefaultsMatchCodeAndPreserveExistingServerOverrides() {
        var defaults = TowerBalanceConfig.defaultConfig();
        for (var type : MagicSchoolTowers.all()) {
            assertEquals(TowerBalanceConfig.TowerStats.from(type), defaults.towers().get(type.id()));
        }
        var override = new TowerBalanceConfig.TowerStats(null, null, null, 19.0, null, null);
        var oldConfig = new TowerBalanceConfig(Map.of(MagicSchoolTowers.FRESHMAN.id(), override), Map.of(), Map.of());
        var merged = oldConfig.withMissingDefaults(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(merged);
        var student = ProductionTowerCatalog.find(MagicSchoolTowers.FRESHMAN.id()).orElseThrow().type();
        assertEquals(19.0, student.damage());
        assertEquals(100, student.mineralCost());
        assertEquals(200.0, student.maxHealth());
        assertEquals(8.0, student.range());
        assertEquals(22, student.attackIntervalTicks());
        assertEquals(DamageType.MAGIC, student.primaryDamageType());
        assertEquals(0, ProductionTowerCatalog.find(MagicSchoolTowers.HOGWARTS.id()).orElseThrow().type().mineralCost());
    }

    @Test
    void wizardsStartWithTheTierOneSpellAndExposeMagicDamageAndSelection() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        var spell = MagicSchoolSpell.EXPELLIARMUS;
        assertEquals("expelliarmus", spell.id());
        assertEquals(1, spell.tier());
        assertEquals(.8, spell.damageMultiplier());
        assertEquals("기본 공격이 공격력 80%의 마법 피해를 입힙니다.", spell.effectLines().getFirst());
        assertFalse(MagicSchoolTowers.isWizard(MagicSchoolTowers.HOGWARTS));
        for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
            var entry = ProductionTowerCatalog.find(type.id()).orElseThrow();
            assertEquals(DamageType.MAGIC, entry.type().primaryDamageType());
            var wizard = assertInstanceOf(MagicSchoolWizardTower.class,
                    entry.create(UUID.randomUUID(), TeamId.RED, 1, new GridPosition(1, 64, 1)));
            assertEquals(DamageType.MAGIC, wizard.primaryDamageType());
            assertEquals(spell, wizard.selectedSpell());
            assertEquals("expelliarmus", wizard.diagnosticState().values().get("semion-td:magic_school_selected_spell"));
            assertTrue(wizard.runtimeDetailLines().contains("주문: 엑스펠리아르무스"));
            assertTrue(wizard.runtimeDetailLines().contains(spell.effectDescription()));
        }
    }

    @Test
    void webCatalogExportsCreativeOwnershipAndBothStarters() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        var document = WebCatalogExporter.snapshot(1L);
        var builder = document.builders().stream()
                .filter(entry -> entry.id().equals(MagicSchoolTowerJob.ID.toString())).findFirst().orElseThrow();
        assertEquals("CREATIVE", builder.builderOrigin());
        var ids = MagicSchoolTowers.all().stream().map(type -> type.id()).collect(java.util.stream.Collectors.toSet());
        assertEquals(ids, Set.copyOf(builder.towerIds()));
        var owned = document.towers().stream().filter(tower -> builder.id().equals(tower.builderId())).toList();
        assertEquals(10, owned.size());
        assertTrue(owned.stream().flatMap(tower -> tower.description().stream()).noneMatch(line -> line.contains("{ability.")));
        assertTrue(owned.stream().allMatch(tower -> ids.contains(tower.id()) && "JOB".equals(tower.availability())));
        var student = owned.stream().filter(tower -> tower.id().equals(MagicSchoolTowers.FRESHMAN.id())).findFirst().orElseThrow();
        assertTrue(student.description().stream().anyMatch(line -> line.contains(MagicSchoolSpell.EXPELLIARMUS.effectDescription())));
    }
}
