package kim.biryeong.semiontd.tower.magicschool;

import static org.junit.jupiter.api.Assertions.*;
import static kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum.*;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MagicSchoolCurriculumTest {
    private final UUID owner = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    @AfterEach
    void reset() {
        clear(owner);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void lessonPricesIncludeTheFreeFirstLevelAndStopAtTheirCaps() {
        long[][] costs = {{0, 100, 160, 220, 280, 340}, {0, 100, 160, 220, 280, 340}, {120, 240, 360, 480, 600}};
        Upgrade[] lessons = {Upgrade.SPELL_POWER, Upgrade.DARK_ARTS_DEFENSE, Upgrade.MAGIC_HISTORY};
        var economy = economy(10000);
        for (int i = 0; i < lessons.length; i++) {
            for (long cost : costs[i]) {
                assertEquals(cost, lessons[i].cost(level(owner, lessons[i])));
                long before = economy.diamond();
                assertEquals(PurchaseResult.PURCHASED, purchase(owner, lessons[i], economy));
                assertEquals(before - cost, economy.diamond());
            }
            long before = economy.diamond();
            assertEquals(PurchaseResult.ALREADY_PURCHASED, purchase(owner, lessons[i], economy));
            assertEquals(before, economy.diamond());
        }
        assertEquals(1.24, lessonMultiplier(owner, Upgrade.SPELL_POWER), 1e-8);
        assertEquals(1.18, lessonMultiplier(owner, Upgrade.DARK_ARTS_DEFENSE), 1e-8);
        assertEquals(2.25, lessonMultiplier(owner, Upgrade.MAGIC_HISTORY));
    }

    @Test
    void freeLessonsWorkAtZeroFundsAndRejectedPurchasesCannotChangeState() {
        var economy = economy(0);
        assertEquals(PurchaseResult.PURCHASED, purchase(owner, Upgrade.SPELL_POWER, economy));
        assertEquals(PurchaseResult.PURCHASED, purchase(owner, Upgrade.DARK_ARTS_DEFENSE, economy));
        for (Upgrade upgrade : Upgrade.values()) {
            assertEquals(upgrade == Upgrade.EXPLOSIVE_BARRELS ? PurchaseResult.PREREQUISITE_REQUIRED
                    : PurchaseResult.NOT_ENOUGH_DIAMONDS, purchase(owner, upgrade, economy));
        }
        assertEquals(1, level(owner, Upgrade.SPELL_POWER));
        assertEquals(1, level(owner, Upgrade.DARK_ARTS_DEFENSE));
        assertFalse(deathEaterEnabled(owner));
        assertEquals(0, economy.diamond());
    }

    @Test
    void singlePurchasesChargeExactlyOnceAndAreOwnerScoped() {
        var economy = economy(2000);
        for (Upgrade upgrade : new Upgrade[]{Upgrade.SORTING_HAT, Upgrade.CUSTOM_WANDS, Upgrade.DEATH_EATER, Upgrade.MENTOR}) {
            long before = economy.diamond();
            assertEquals(PurchaseResult.PURCHASED, purchase(owner, upgrade, economy));
            assertEquals(before - upgrade.cost(0), economy.diamond());
            assertEquals(PurchaseResult.ALREADY_PURCHASED, purchase(owner, upgrade, economy));
            assertEquals(before - upgrade.cost(0), economy.diamond());
            assertFalse(purchased(UUID.randomUUID(), upgrade));
        }
        assertTrue(deathEaterEnabled(owner));
        assertTrue(beginDeathEaterWave(owner, 3));
        assertFalse(beginDeathEaterWave(owner, 3));
        assertFalse(beginDeathEaterWave(owner, 2));
        assertTrue(beginDeathEaterWave(owner, 4));
        clear(owner);
        assertFalse(deathEaterEnabled(owner));
        assertFalse(hasSortingHat(owner));
        assertFalse(beginDeathEaterWave(owner, 5));
    }

    @Test
    void tierUnlocksUseDiamondsOnceWithoutLockingTheDefaultSpell() {
        var economy = economy(1695);
        assertTrue(isSpellTierUnlocked(owner, MagicSchoolSpell.EXPELLIARMUS.tier()));
        long[] costs = {175, 320, 500, 700};
        for (int tier = 2; tier <= 5; tier++) {
            assertFalse(isSpellTierUnlocked(owner, tier));
            assertEquals(costs[tier - 2], spellTierCost(tier));
            long before = economy.diamond();
            assertEquals(PurchaseResult.PURCHASED, unlockSpellTier(owner, tier, economy));
            assertEquals(PurchaseResult.ALREADY_PURCHASED, unlockSpellTier(owner, tier, economy));
            assertEquals(before - costs[tier - 2], economy.diamond());
            assertTrue(isSpellTierUnlocked(owner, tier));
            assertFalse(isSpellTierUnlocked(UUID.randomUUID(), tier));
        }
        assertEquals(0, economy.diamond());
        assertEquals(10, economy.emerald());
        clear(owner);
        assertTrue(isSpellTierUnlocked(owner, 1));
        assertFalse(isSpellTierUnlocked(owner, 6));
        assertEquals(PurchaseResult.NOT_ENOUGH_DIAMONDS, unlockSpellTier(owner, 2, economy));
        assertThrows(IllegalArgumentException.class, () -> unlockSpellTier(owner, 7, economy));
    }

    @Test
    void skippedSpellTiersRejectWithoutSpendingAndEachPriorTierUnlocksTheNext() {
        var economy = economy(20000);
        for (int tier = 3; tier <= 5; tier++) {
            long before = economy.diamond();
            assertEquals(PurchaseResult.PREVIOUS_SPELL_TIER_REQUIRED, unlockSpellTier(owner, tier, economy));
            assertEquals(before, economy.diamond());
            assertFalse(isSpellTierUnlocked(owner, tier));
            assertEquals(PurchaseResult.PURCHASED, unlockSpellTier(owner, tier - 1, economy));
        }
        assertEquals(PurchaseResult.PURCHASED, unlockSpellTier(owner, 5, economy));
        long before = economy.diamond();
        assertEquals(PurchaseResult.AUGMENT_REQUIRED, unlockSpellTier(owner, 6, economy));
        assertEquals(before, economy.diamond());
        assertFalse(isSpellTierUnlocked(owner, 6));
    }

    @Test
    void lessonsWandsAndHouseBonusesMultiplyOnceForExistingAndFutureWizards() {
        var economy = economy(10000);
        var freshman = wizard(MagicSchoolTowers.FRESHMAN);
        for (Upgrade upgrade : new Upgrade[]{Upgrade.SPELL_POWER, Upgrade.DARK_ARTS_DEFENSE, Upgrade.CUSTOM_WANDS}) {
            purchase(owner, upgrade, economy);
        }
        assertEquals(31.2, freshman.modifyAttackDamage(null, null, 30), 1e-8);
        assertEquals(206, freshman.effectBaseMaxHealth(), 1e-8);
        for (TowerType type : MagicSchoolTowers.houseWizards()) {
            var wizard = wizard(type);
            wizard.gainProficiency(100, null);
            double wandDamage = type == MagicSchoolTowers.SLYTHERIN ? 1.1 : type == MagicSchoolTowers.RAVENCLAW ? 1.05 : 1;
            double wandHealth = type == MagicSchoolTowers.HUFFLEPUFF ? 1.1 : type == MagicSchoolTowers.RAVENCLAW ? 1.05 : 1;
            double proficiency = type == MagicSchoolTowers.RAVENCLAW ? 1.092 : 1.08;
            assertEquals(50 * proficiency * 1.04 * wandDamage * (type == MagicSchoolTowers.SLYTHERIN ? 1.1 : 1),
                    wizard.modifyAttackDamage(null, null, wizard.type().damage()), 1e-8);
            assertEquals(340 * proficiency * 1.03 * wandHealth * (type == MagicSchoolTowers.HUFFLEPUFF ? 1.1 : 1),
                    wizard.currentMaxHealth(), 1e-8);
            assertEquals(type == MagicSchoolTowers.GRYFFINDOR ? 16 : 18, wizard.adjustAttackInterval(wizard.type().attackIntervalTicks()));
        }
    }

    @Test
    void historyMultipliesAllGainSourcesAndRavenclawBonusBeforeCapping() {
        var economy = economy(1800);
        var ravenclaw = wizard(MagicSchoolTowers.RAVENCLAW);
        purchase(owner, Upgrade.MAGIC_HISTORY, economy);
        ravenclaw.onWaveStarted(null, 1);
        assertEquals(11 * 1.15 * 1.25, ravenclaw.proficiency(), 1e-8);
        ravenclaw.gainProficiency(8, null);
        assertEquals(19 * 1.15 * 1.25, ravenclaw.proficiency(), 1e-8);
        for (int i = 1; i < 5; i++) purchase(owner, Upgrade.MAGIC_HISTORY, economy);
        assertEquals(2 * 1.15 * 2.25, ravenclaw.gainProficiency(2, null), 1e-8);
        ravenclaw.gainProficiency(10000, null);
        assertEquals(300, ravenclaw.proficiency());
    }

    @Test
    void defaultsMergeNewKeysWithoutReplacingCustomValuesAndRejectInvalidValues() {
        var defaults = TowerBalanceConfig.defaultConfig();
        var merged = new TowerBalanceConfig(Map.of(), Map.of(),
                Map.of(MagicSchoolTowers.CONFIG_ID, Map.of("customWandsCost", 321.0))).withMissingDefaults(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(merged);
        assertEquals(321, Upgrade.CUSTOM_WANDS.cost(0));
        assertEquals(200, Upgrade.MENTOR.cost(0));
        for (var entry : defaultAbilities().entrySet()) {
            assertEquals(entry.getValue(), defaults.abilities().get(MagicSchoolTowers.CONFIG_ID).get(entry.getKey()));
        }
        for (var invalid : Map.of("spellPowerMaxLevel", 0.0, "customWandsCost", 1.5, "magicHistoryPerLevel", 1.1,
                "mentorRadius", 0.0, "deathEaterProficiencyPerRound", -1.0).entrySet()) {
            assertThrows(IllegalArgumentException.class, () -> new TowerBalanceConfig(Map.of(), Map.of(),
                    Map.of(MagicSchoolTowers.CONFIG_ID, Map.of(invalid.getKey(), invalid.getValue())))
                    .withMissingDefaults(defaults).validateForRuntime());
        }
    }

    @Test
    void newLessonsChargeOnceAndBombsRequireTransfigurationWithoutSpending() {
        var economy = economy(1500);
        assertEquals(PurchaseResult.PREREQUISITE_REQUIRED, purchase(owner, Upgrade.EXPLOSIVE_BARRELS, economy));
        assertEquals(1500, economy.diamond());
        Upgrade[] upgrades = {Upgrade.SPELL_TRANSFER, Upgrade.POTIONS, Upgrade.QUIDDITCH,
                Upgrade.TRANSFIGURATION, Upgrade.EXPLOSIVE_BARRELS};
        long[] prices = {300, 300, 200, 200, 500};
        for (int i = 0; i < upgrades.length; i++) {
            assertEquals(prices[i], upgrades[i].cost(0));
            long before = economy.diamond();
            assertEquals(PurchaseResult.PURCHASED, purchase(owner, upgrades[i], economy));
            assertEquals(PurchaseResult.ALREADY_PURCHASED, purchase(owner, upgrades[i], economy));
            assertEquals(before - prices[i], economy.diamond());
        }
        assertEquals(0, economy.diamond());
    }

    @Test
    void quidditchRewardsGrowFiveTimesAndAreOncePerRoundAndOwner() {
        purchase(owner, Upgrade.QUIDDITCH, economy(200));
        assertEquals(0, claimQuidditchReward(owner, 0));
        for (int round = 1; round <= 9; round++) {
            assertEquals(5 + 5 * Math.min(5, round - 1), claimQuidditchReward(owner, round));
            assertEquals(0, claimQuidditchReward(owner, round));
            assertEquals(0, claimQuidditchReward(owner, round - 1));
            assertEquals(0, claimQuidditchReward(UUID.randomUUID(), round));
        }
        clear(owner);
        purchase(owner, Upgrade.QUIDDITCH, economy(200));
        assertEquals(5, claimQuidditchReward(owner, 1));
    }

    @Test
    void transfigurationSharesItsCooldownAcrossStudentsAndScalesAtExactRounds() {
        assertFalse(beginBarrel(owner, 1, 0));
        purchase(owner, Upgrade.TRANSFIGURATION, economy(200));
        assertFalse(beginBarrel(owner, 0, 0));
        assertTrue(beginBarrel(owner, 1, 100));
        assertFalse(beginBarrel(owner, 1, 159));
        assertTrue(beginBarrel(owner, 1, 160));
        assertTrue(beginBarrel(owner, 2, 161));
        assertFalse(beginBarrel(UUID.randomUUID(), 1, 100));
        for (int round : new int[]{1, 4, 5, 14, 15, 24, 25, 50}) {
            assertEquals(round < 5 ? 1 : round < 15 ? 2 : round < 25 ? 3 : 4, barrelHealth(round));
        }
    }

    @Test
    void curriculumCombatValuesBackfillAndRejectInvalidSettings() {
        var defaults = TowerBalanceConfig.defaultConfig();
        var merged = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolTowers.CONFIG_ID,
                Map.of("potionsCost", 456.0, "spellTransferDamageRatio", .2))).withMissingDefaults(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(merged);
        assertEquals(456, Upgrade.POTIONS.cost(0));
        assertEquals(.2, value("spellTransferDamageRatio", .1));
        assertEquals(60, integer("transfigurationCooldownTicks", 0));
        for (var invalid : Map.of("spellTransferMaxTargets", 0.0, "spellTransferCooldownTicks", 1.5,
                "potionsHealRatio", 0.0, "explosiveBarrelDamageRatio", 1.1, "explosiveBarrelRadius", 0.0,
                "barrelHealth", 1.5, "quidditchMaxIncreases", -1.0).entrySet()) {
            assertThrows(IllegalArgumentException.class, () -> new TowerBalanceConfig(Map.of(), Map.of(),
                    Map.of(MagicSchoolTowers.CONFIG_ID, Map.of(invalid.getKey(), invalid.getValue())))
                    .withMissingDefaults(defaults).validateForRuntime(), invalid.getKey());
        }
    }

    @Test
    void duelingPracticeChargesOnceStartsEnabledAndClearsWithTheOwner() {
        var economy = economy(150);
        assertFalse(enabled(owner, Upgrade.DUELING_PRACTICE));
        assertEquals(PurchaseResult.PURCHASED, purchase(owner, Upgrade.DUELING_PRACTICE, economy));
        assertEquals(0, economy.diamond());
        assertTrue(enabled(owner, Upgrade.DUELING_PRACTICE));
        assertFalse(enabled(UUID.randomUUID(), Upgrade.DUELING_PRACTICE));
        assertEquals(PurchaseResult.ALREADY_PURCHASED, purchase(owner, Upgrade.DUELING_PRACTICE, economy));
        clear(owner);
        assertFalse(enabled(owner, Upgrade.DUELING_PRACTICE));
        assertFalse(purchased(owner, Upgrade.DUELING_PRACTICE));
    }

    @Test
    void duelingPracticeUsesPostGrowthMaximumHealthAndExistingGainBonusesOncePerWave() {
        purchase(owner, Upgrade.DUELING_PRACTICE, economy(150));
        var freshman = wizard(MagicSchoolTowers.FRESHMAN);
        freshman.onWaveStarted(null, 1);
        double lost = 203.3 * .15;
        assertEquals(11 + lost * .30, freshman.proficiency(), 1e-8);
        assertEquals(freshman.currentMaxHealth() - lost, freshman.health(), 1e-8);
        double before = freshman.health();
        freshman.onWaveStarted(null, 1);
        assertEquals(before, freshman.health(), 1e-8);
        assertEquals(11 + lost * .30, freshman.proficiency(), 1e-8);
        purchase(owner, Upgrade.MAGIC_HISTORY, economy(120));
        var ravenclaw = wizard(MagicSchoolTowers.RAVENCLAW);
        double normalGain = 11 * 1.15 * 1.25;
        double ravenclawLoss = 340 * (1 + normalGain * .0008) * .15;
        ravenclaw.onWaveStarted(null, 1);
        assertEquals(normalGain + ravenclawLoss * .30 * 1.15 * 1.25, ravenclaw.proficiency(), 1e-8);
        assertEquals(ravenclaw.currentMaxHealth() - ravenclawLoss, ravenclaw.health(), 1e-8);
        var capped = wizard(MagicSchoolTowers.FRESHMAN);
        capped.gainProficiency(10000, null);
        capped.onWaveStarted(null, 1);
        assertEquals(100, capped.proficiency());
        assertEquals(195.5, capped.health(), 1e-8);
        var copy = wizard(MagicSchoolTowers.FRESHMAN);
        copy.markTemporaryCopy(freshman.logicalId());
        copy.onWaveStarted(null, 1);
        assertEquals(0, copy.proficiency());
        assertEquals(200, copy.health());
    }

    @Test
    void duelingPracticeConfigurationBackfillsAndRejectsInvalidRatiosAndPrice() {
        var defaults = TowerBalanceConfig.defaultConfig();
        var merged = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolTowers.CONFIG_ID,
                Map.of("duelingPracticeCost", 333.0))).withMissingDefaults(defaults);
        merged.validateForRuntime();
        ProductionTowerCatalogs.reloadBuiltIns(merged);
        assertEquals(333, Upgrade.DUELING_PRACTICE.cost(0));
        assertEquals(.15, value("duelingPracticeHealthRatio", 0));
        assertEquals(.30, value("duelingPracticeProficiencyRatio", 0));
        for (var invalid : Map.of("duelingPracticeCost", 1.5, "duelingPracticeHealthRatio", 1.01,
                "duelingPracticeProficiencyRatio", -0.01).entrySet()) {
            var bad = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolTowers.CONFIG_ID,
                    Map.of(invalid.getKey(), invalid.getValue()))).withMissingDefaults(defaults);
            assertThrows(IllegalArgumentException.class, bad::validateForRuntime, invalid.getKey());
        }
    }

    @Test
    void spellPracticeAddsTierGainAndConsecutiveBonusWithCursesCountingAsFive() {
        var funds = economy(20000);
        purchase(owner, Upgrade.SPELL_PRACTICE, funds);
        purchase(owner, Upgrade.ADVANCED_SPELLS, funds);
        for (int tier = 2; tier <= 5; tier++) unlockSpellTier(owner, tier, funds);
        for (var spell : MagicSchoolSpell.values()) {
            if (spell == MagicSchoolSpell.MUGGLE_WAND) continue;
            var wizard = wizard(MagicSchoolTowers.BRAVE_ARCHWIZARD);
            wizard.syncAugments(MagicSchoolAugmentsTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES), null);
            assertTrue(wizard.selectSpell(spell));
            double base = Math.min(5, spell.tier()) * 2;
            wizard.onWaveStarted(null, 1);
            assertEquals(11 + base, wizard.proficiency(), 1e-8, spell.id());
            wizard.onWaveStarted(null, 1);
            assertEquals(11 + base, wizard.proficiency(), 1e-8, "Same-round callbacks cannot repeat the award.");
            wizard.resetForRound(null);
            wizard.onWaveStarted(null, 2);
            assertEquals(11 + base + 12 + base + 2, wizard.proficiency(), 1e-8, spell.id());
        }
    }

    @Test
    void spellPracticeRemembersWaveStartBeforePurchaseAndThroughPromotionWithoutCountingSkippedRounds() {
        var funds = economy(20000);
        unlockSpellTier(owner, 2, funds);
        var student = wizard(MagicSchoolTowers.FRESHMAN);
        student.selectSpell(MagicSchoolSpell.STUPEFY);
        student.onWaveStarted(null, 1);
        purchase(owner, Upgrade.SPELL_PRACTICE, funds);
        purchase(owner, Upgrade.ADVANCED_SPELLS, funds);
        student.onWaveStarted(null, 1);
        assertEquals(11, student.proficiency(), "Buying mid-wave cannot award proficiency immediately.");
        var promoted = wizard(MagicSchoolTowers.GRYFFINDOR);
        promoted.copyFrom(student, 200);
        promoted.onWaveStarted(null, 1);
        assertEquals(0, promoted.proficiency(), "Promotion must not rerun an already rewarded wave.");
        promoted.resetForRound(null);
        promoted.onWaveStarted(null, 2);
        assertEquals(18, promoted.proficiency(), "The previous wave's selected spell survives promotion: 12 + 4 + 2.");
        promoted.resetForRound(null);
        promoted.selectSpell(MagicSchoolSpell.PROTEGO);
        promoted.onWaveStarted(null, 3);
        assertEquals(35, promoted.proficiency(), "A different spell in the same tier is not a repetition: +13 + 4.");
        promoted.resetForRound(null);
        promoted.onWaveStarted(null, 5);
        assertEquals(54, promoted.proficiency(), "Skipping a round breaks consecutive use: +15 + 4.");
    }

    @Test
    void spellPracticeUsesHistoryAndRavenclawBonusesAndExcludesDeadStudentsAndCopies() {
        purchase(owner, Upgrade.SPELL_PRACTICE, economy(125));
        purchase(owner, Upgrade.MAGIC_HISTORY, economy(120));
        var raven = wizard(MagicSchoolTowers.RAVENCLAW);
        raven.onWaveStarted(null, 1);
        assertEquals((11 + 2) * 1.15 * 1.25, raven.proficiency(), 1e-8);
        raven.onWaveStarted(null, 2);
        assertEquals((11 + 2 + 12 + 2 + 2) * 1.15 * 1.25, raven.proficiency(), 1e-8);
        raven.gainProficiency(10000, null);
        raven.onWaveStarted(null, 3);
        assertEquals(300, raven.proficiency());
        var student = wizard(MagicSchoolTowers.FRESHMAN);
        student.markTemporaryCopy(raven.logicalId());
        student.onWaveStarted(null, 1);
        assertEquals(0, student.proficiency());
        var dead = wizard(MagicSchoolTowers.FRESHMAN);
        dead.syncHealth(0);
        dead.onWaveStarted(null, 1);
        assertEquals(0, dead.proficiency());
    }

    @Test
    void spellPracticePriceAndGainSettingsAreValidatedAndReloadable() {
        var defaults = TowerBalanceConfig.defaultConfig();
        var changed = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolTowers.CONFIG_ID,
                Map.of("spellPracticePerTier", 3.0, "spellPracticeRepeatBonus", 4.0)))
                .withMissingDefaults(defaults);
        changed.validateForRuntime();
        ProductionTowerCatalogs.reloadBuiltIns(changed);
        var funds = economy(125);
        assertEquals(PurchaseResult.PURCHASED, purchase(owner, Upgrade.SPELL_PRACTICE, funds));
        assertEquals(0, funds.diamond());
        assertEquals(PurchaseResult.ALREADY_PURCHASED, purchase(owner, Upgrade.SPELL_PRACTICE, funds));
        var student = wizard(MagicSchoolTowers.FRESHMAN);
        student.onWaveStarted(null, 1);
        student.onWaveStarted(null, 2);
        assertEquals(11 + 3 + 12 + 3 + 4, student.proficiency());
        for (var invalid : Map.of("spellPracticeCost", 1.5, "spellPracticePerTier", -1.0,
                "spellPracticeRepeatBonus", 0.5).entrySet()) {
            var bad = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolTowers.CONFIG_ID,
                    Map.of(invalid.getKey(), invalid.getValue()))).withMissingDefaults(defaults);
            assertThrows(IllegalArgumentException.class, bad::validateForRuntime, invalid.getKey());
        }
    }

    private MagicSchoolWizardTower wizard(TowerType type) {
        return (MagicSchoolWizardTower) ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(owner, TeamId.RED, 1, new GridPosition(1, 64, 1));
    }

    private static PlayerEconomy economy(long diamonds) {
        var economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(diamonds, 10, 0, 0);
        return economy;
    }
}
