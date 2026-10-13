package kim.biryeong.semiontd.tower.magicschool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MagicSchoolProficiencyTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    @AfterEach
    void defaults() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void waveGainIsPerStudentOncePerRoundAndLinearUntilTheCap() {
        var first = wizard(MagicSchoolTowers.FRESHMAN);
        var second = wizard(MagicSchoolTowers.FRESHMAN);
        first.onWaveStarted(null, 1);
        assertEquals(11, first.proficiency());
        assertEquals(203.3, first.currentMaxHealth(), 1e-8);
        assertEquals(30.495, first.modifyAttackDamage(null, null, 30), 1e-8);
        assertEquals(0, second.proficiency());
        first.onWaveStarted(null, 1);
        assertEquals(11, first.proficiency());
        first.onWaveStarted(null, 2);
        assertEquals(23, first.proficiency());
        first.gainProficiency(1000, null);
        assertEquals(100, first.proficiency());
        assertEquals(230, first.currentMaxHealth(), 1e-8);
        assertEquals(34.5, first.modifyAttackDamage(null, null, 30), 1e-8);
        assertEquals(0, first.gainProficiency(Double.NaN, null));
        assertEquals(0, first.gainProficiency(-1, null));
        assertEquals(0, first.gainProficiency(Double.POSITIVE_INFINITY, null));
        assertEquals(100, first.proficiency());
    }

    @Test
    void houseGraduationResetsProficiencyButRetainsSpellAndWaveGuard() {
        var student = wizard(MagicSchoolTowers.FRESHMAN);
        student.onWaveStarted(null, 1);
        student.gainProficiency(1000, null);
        for (TowerType type : MagicSchoolTowers.houseWizards()) {
            var house = wizard(type);
            house.copyFrom(student, 200);
            house.onStateChanged(null);
            assertEquals(0, house.proficiency());
            assertEquals(300, house.maxProficiency());
            assertEquals(MagicSchoolSpell.EXPELLIARMUS, house.selectedSpell());
            assertEquals(type == MagicSchoolTowers.GRYFFINDOR ? 17 : 18, house.adjustAttackInterval(house.type().attackIntervalTicks()));
            assertEquals(type == MagicSchoolTowers.HUFFLEPUFF ? 374 : 340, house.currentMaxHealth(), 1e-8);
            assertEquals(type == MagicSchoolTowers.SLYTHERIN ? 55 : 50, house.modifyAttackDamage(null, null, house.type().damage()), 1e-8);
            assertEquals(type == MagicSchoolTowers.RAVENCLAW ? 0 : 123, house.spellChangeCost(123));
            house.onWaveStarted(null, 1);
            assertEquals(0, house.proficiency(), "Upgrade must copy the last rewarded round.");
        }
    }

    @Test
    void ravenclawKeepsFractionalGainsAndCapsAtThreeHundred() {
        var ravenclaw = wizard(MagicSchoolTowers.RAVENCLAW);
        ravenclaw.onWaveStarted(null, 1);
        assertEquals(12.65, ravenclaw.proficiency(), 1e-8);
        ravenclaw.onWaveStarted(null, 2);
        assertEquals(26.45, ravenclaw.proficiency(), 1e-8);
        ravenclaw.gainProficiency(1000, null);
        assertEquals(300, ravenclaw.proficiency());
        assertEquals(421.6, ravenclaw.currentMaxHealth(), 1e-8);
        assertEquals(62, ravenclaw.modifyAttackDamage(null, null, 50), 1e-8);
    }

    @Test
    void archwizardGraduationResetsGrowthWhileSameTypeCopyKeepsItAndDoesNotRepeatWaveRewards() {
        for (var type : MagicSchoolTowers.houseWizards()) {
            var house = wizard(type);
            house.onWaveStarted(null, 1);
            house.gainProficiency(10000, null);
            var arch = wizard(MagicSchoolTowers.archWizardFor(type));
            arch.copyFrom(house, 450);
            arch.onStateChanged(null);
            assertEquals(0, arch.proficiency());
            assertEquals(1000, arch.maxProficiency());
            assertEquals(arch.type().maxHealth(), arch.currentMaxHealth());
            assertEquals(arch.type().damage(), arch.modifyAttackDamage(null, null, arch.type().damage()));
            assertEquals(type == MagicSchoolTowers.RAVENCLAW ? 0 : 123, arch.spellChangeCost(123));
            arch.onWaveStarted(null, 1);
            assertEquals(0, arch.proficiency());
            arch.onWaveStarted(null, 2);
            assertEquals(type == MagicSchoolTowers.RAVENCLAW ? 13.8 : 12, arch.proficiency(), 1e-8);
            var copy = wizard(arch.type());
            copy.copyFrom(arch, 0);
            assertEquals(arch.proficiency(), copy.proficiency(), "Same-type runtime copies are not graduation.");
            arch.gainProficiency(10000, null);
            assertEquals(1000, arch.proficiency());
            assertEquals(arch.type().maxHealth() * 1.5, arch.currentMaxHealth(), 1e-8);
        }
    }

    @Test
    void wizardTiersUseTheirOwnGrowthRatesAndCaps() {
        for (TowerType type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
            var wizard = wizard(type);
            double cap = MagicSchoolTowers.isFreshman(type) ? 100 : MagicSchoolTowers.isHouseWizard(type) ? 300 : 1000;
            double rate = MagicSchoolTowers.isFreshman(type) ? .0015 : MagicSchoolTowers.isHouseWizard(type) ? .0008 : .0005;
            String percentage = MagicSchoolTowers.isFreshman(type) ? "0.15%" : MagicSchoolTowers.isHouseWizard(type) ? "0.08%" : "0.05%";
            wizard.gainProficiency(10000, null);
            assertEquals(cap, wizard.proficiency(), type.id());
            assertTrue(wizard.type().description().stream().anyMatch(line -> line.contains(percentage)),
                    "Catalog descriptions must preserve the fractional percentage: " + type.id());
            assertEquals(type.maxHealth() * (1 + cap * rate), wizard.currentMaxHealth(), 1e-8, type.id());
            assertEquals(type.damage() * (1 + cap * rate),
                    wizard.modifyAttackDamage(null, null, type.damage()), 1e-8, type.id());
        }
    }

    @Test
    void proficiencyConfigRejectsInvalidCapsRatiosAndFractionalCosts() {
        var defaults = TowerBalanceConfig.defaultConfig();
        assertThrows(IllegalArgumentException.class, () -> new TowerBalanceConfig(Map.of(), Map.of(),
                Map.of(MagicSchoolTowers.FRESHMAN.id(), Map.of("maxProficiency", 0.0)))
                .withMissingDefaults(defaults).validateForRuntime());
        assertThrows(IllegalArgumentException.class, () -> new TowerBalanceConfig(Map.of(), Map.of(),
                Map.of(MagicSchoolTowers.CONFIG_ID, Map.of("sortingHatCost", 150.5)))
                .withMissingDefaults(defaults).validateForRuntime());
        assertThrows(IllegalArgumentException.class, () -> new TowerBalanceConfig(Map.of(), Map.of(),
                Map.of(MagicSchoolTowers.RAVENCLAW.id(), Map.of("proficiencyGainBonus", 1.5)))
                .withMissingDefaults(defaults).validateForRuntime());
    }

    private static MagicSchoolWizardTower wizard(TowerType type) {
        return (MagicSchoolWizardTower) ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(UUID.randomUUID(), TeamId.RED, 1, new GridPosition(1, 64, 1));
    }
}
