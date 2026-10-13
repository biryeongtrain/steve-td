package kim.biryeong.semiontd.tower.magicschool;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.*;

class MagicSchoolSpellTest {
    private final UUID owner = UUID.randomUUID();
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach @AfterEach void reset() {
        MagicSchoolCurriculum.clear(owner);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test void protegoDefaultsFallbackAndDescriptionUseThirtyPercentWithoutChangingMaxima() {
        var spell = MagicSchoolSpell.PROTEGO;
        assertEquals(.3, TowerBalanceConfig.codeDefaults().ability(spell.configId(), "damageReduction", -1));
        assertEquals(.3, spell.value("damageReduction"));
        assertTrue(spell.effectDescription().contains("30%"));
        kim.biryeong.semiontd.config.TowerBalanceRuntime.apply(new TowerBalanceConfig(Map.of(), Map.of(), Map.of()));
        assertEquals(.3, spell.value("damageReduction"));
        assertEquals(.3, MagicSchoolSpell.PROTEGO_MAXIMA.value("damageReduction"));
        assertEquals(.05, MagicSchoolSpell.PROTEGO_MAXIMA.value("auraReduction"));
        assertEquals(3, MagicSchoolSpell.PROTEGO_MAXIMA.value("maxAuraStacks"));
        kim.biryeong.semiontd.config.TowerBalanceRuntime.apply(new TowerBalanceConfig(Map.of(), Map.of(),
                Map.of(spell.configId(), Map.of("damageReduction", .37))));
        assertEquals(.37, spell.value("damageReduction"));
        assertTrue(spell.effectDescription().contains("37%"));
    }

    @Test void seventeenKoreanSpellsFitStableTierRowsAndSharePackagedConfig() {
        assertEquals(17, MagicSchoolSpell.values().length);
        int[] counts = new int[6];
        var defaults = TowerBalanceConfig.defaultConfig();
        defaults.validateForRuntime();
        for (var spell : MagicSchoolSpell.values()) {
            counts[spell.tier() - 1]++;
            assertFalse(spell.displayName().matches(".*[a-zA-Z].*"));
            assertFalse(spell.effectDescription().contains("{"));
            assertEquals(spell, MagicSchoolSpell.find(spell.id()).orElseThrow());
            assertEquals(spell.defaultAbilities(), defaults.abilities().get(spell.configId()));
        }
        assertArrayEquals(new int[]{2, 3, 3, 3, 3, 3}, counts);
    }

    @Test void lumosDescriptionsExplainSharedTargetPriorityAndCasterException() {
        for (var spell : java.util.List.of(MagicSchoolSpell.LUMOS, MagicSchoolSpell.LUMOS_MAXIMA)) {
            var description = spell.effectDescription();
            assertTrue(description.contains("마법사는 루모스 디버프가 부여된 대상을 우선 공격합니다."));
            assertTrue(description.contains("단, 루모스·루모스 맥시마를 장착한 마법사는 루모스가 없는 적을 우선 공격합니다."));
            assertTrue(description.contains("받는 마법 피해 +12%"));
        }
    }

    @Test void bombardaUsesUpdatedDamageAndRadiusInDescriptions() {
        assertEquals(.9, MagicSchoolSpell.BOMBARDA.damageMultiplier());
        assertEquals(.70, MagicSchoolSpell.BOMBARDA.value("secondaryMultiplier"));
        assertEquals(2.4, MagicSchoolSpell.BOMBARDA.value("radius"));
        assertEquals("기본 공격이 공격력 90%의 마법 피해를 입힙니다.", MagicSchoolSpell.BOMBARDA.effectLines().getFirst());
        assertTrue(MagicSchoolSpell.BOMBARDA.effectDescription().contains("주 대상 주변 2.4칸의 다른 적에게 공격력 70%"));
        assertEquals(.8, MagicSchoolSpell.EXPULSO.damageMultiplier());
        assertEquals(1.2, MagicSchoolSpell.EXPULSO.value("radius"));
    }

    @Test void spellPercentagesUseExactDecimalScalingAndRetainConfiguredFractions() {
        assertEquals(.75, MagicSchoolSpell.EPISKEY.value("healingMultiplier"));
        assertTrue(MagicSchoolSpell.EPISKEY.effectDescription().contains("공격력 75%만큼 회복"));
        assertEquals("기본 공격이 공격력 110%의 마법 피해를 입힙니다.", MagicSchoolSpell.RENNERVATE.effectLines().getFirst());
        var changed = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolSpell.RENNERVATE.configId(),
                Map.of("damageMultiplier", 1.125, "damageBonus", .333, "buffTicks", 25.0)))
                .withMissingDefaults(TowerBalanceConfig.defaultConfig());
        ProductionTowerCatalogs.reloadBuiltIns(changed);
        assertEquals("기본 공격이 공격력 112.5%의 마법 피해를 입힙니다.", MagicSchoolSpell.RENNERVATE.effectLines().getFirst());
        assertTrue(MagicSchoolSpell.RENNERVATE.effectDescription().contains("1.25초간 공격력 +33.3%"));
        assertFalse(MagicSchoolSpell.RENNERVATE.effectDescription().contains("<"), "Plain descriptions must remain markup-free.");
    }

    @Test void damageHealingAndBuffCoefficientsShareLightOrangeEmphasis() {
        for (String coefficient : java.util.List.of("공격력 110%", "공격력 112.5%", "공격력 +20%",
                "공격력 200%", "대상 최대 체력의 200%")) {
            assertEquals("앞 <color:#ffb86c>" + coefficient + "</color> 뒤",
                    MagicSchoolSpell.highlightAttackCoefficients("앞 " + coefficient + " 뒤"));
        }
        assertEquals("공격 속도 +100% · 5초 · 6칸",
                MagicSchoolSpell.highlightAttackCoefficients("공격 속도 +100% · 5초 · 6칸"));
    }

    @Test void tiersGateSelectionButMuggleRemainsLockedAfterEveryTierUnlock() {
        var pos = new GridPosition(0, 64, 0);
        var wizard = new FreshmanTower(MagicSchoolTowers.FRESHMAN, owner, TeamId.RED, 1, pos, pos);
        assertFalse(wizard.selectSpell(MagicSchoolSpell.STUPEFY));
        var economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.addMineral(100000);
        for (int tier = 2; tier <= 5; tier++) MagicSchoolCurriculum.unlockSpellTier(owner, tier, economy);
        for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
            var caster = (MagicSchoolWizardTower) kim.biryeong.semiontd.tower.ProductionTowerCatalog.find(type.id()).orElseThrow()
                    .create(owner, TeamId.RED, 1, pos);
            int cap = MagicSchoolTowers.belongsToHouse(type, MagicSchoolTowers.RAVENCLAW) ? 5
                    : MagicSchoolTowers.isFreshman(type) ? 2 : MagicSchoolTowers.isArchWizard(type) ? 4 : 3;
            assertEquals(cap, caster.maxSpellTier());
            caster.syncAugments(MagicSchoolAugmentsTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES), null);
            for (var spell : MagicSchoolSpell.values()) {
                var before = caster.selectedSpell();
                boolean allowed = spell != MagicSchoolSpell.MUGGLE_WAND && Math.min(5, spell.tier()) <= cap;
                assertEquals(allowed, caster.selectSpell(spell), type.id() + ": " + spell);
                assertEquals(allowed ? spell : before, caster.selectedSpell());
            }
        }
        wizard.setData(kim.biryeong.semiontd.tower.TowerDataKey.of(
                Identifier.fromNamespaceAndPath("semion-td", "magic_school_selected_spell"), String.class), "imperio");
        assertEquals(MagicSchoolSpell.EXPELLIARMUS, wizard.selectedSpell(), "Old unsupported selections must not bypass the tier restriction.");
    }

    @Test void advancedClassRaisesCasterLimitOnceWithoutUnlockingSpellsOrAffectingAnotherOwner() {
        var funds = new PlayerEconomy(EconomyConfig.defaultConfig());
        funds.overrideStartingValues(350, 0, 0, 0);
        var pos = new GridPosition(0, 64, 0);
        var student = new FreshmanTower(MagicSchoolTowers.FRESHMAN, owner, TeamId.RED, 1, pos, pos);
        assertEquals(2, student.maxSpellTier());
        assertEquals(MagicSchoolCurriculum.PurchaseResult.PURCHASED,
                MagicSchoolCurriculum.purchase(owner, MagicSchoolCurriculum.Upgrade.ADVANCED_SPELLS, funds));
        assertEquals(0, funds.diamond());
        assertEquals(3, student.maxSpellTier());
        assertFalse(student.selectSpell(MagicSchoolSpell.LUMOS));
        assertEquals(MagicSchoolCurriculum.PurchaseResult.ALREADY_PURCHASED,
                MagicSchoolCurriculum.purchase(owner, MagicSchoolCurriculum.Upgrade.ADVANCED_SPELLS, funds));
        for (var type : MagicSchoolTowers.houseWizards()) {
            var house = new HouseWizardTower(type, owner, TeamId.RED, 1, pos, pos);
            assertEquals(type == MagicSchoolTowers.RAVENCLAW ? 5 : 4, house.maxSpellTier());
            var arch = new HouseWizardTower(MagicSchoolTowers.archWizardFor(type), owner, TeamId.RED, 1, pos, pos);
            assertEquals(5, arch.maxSpellTier());
        }
        var other = new FreshmanTower(MagicSchoolTowers.FRESHMAN, UUID.randomUUID(), TeamId.RED, 2, pos, pos);
        assertEquals(2, other.maxSpellTier());
        MagicSchoolCurriculum.clear(owner);
        assertEquals(2, student.maxSpellTier());
    }

    @Test void lowProficiencyCursePenaltyIncludesFiveHundredAndMultipliesOtherSpeedEffects() {
        var pos = new GridPosition(0, 64, 0);
        for (var spell : java.util.List.of(MagicSchoolSpell.AVADA_KEDAVRA, MagicSchoolSpell.CRUCIO, MagicSchoolSpell.IMPERIO)) {
            var wizard = new HouseWizardTower(MagicSchoolTowers.WISE_ARCHWIZARD, owner, TeamId.RED, 1, pos, pos);
            wizard.syncAugments(MagicSchoolAugmentsTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES), null);
            assertTrue(wizard.selectSpell(spell));
            assertEquals(120, wizard.resolveFinalAttackInterval(12));
            wizard.gainProficiency(500 / 1.15, null);
            assertEquals(500, wizard.proficiency(), 1e-8);
            assertEquals(120, wizard.resolveFinalAttackInterval(12));
            assertEquals(300, wizard.resolveFinalAttackInterval(30));
            wizard.gainProficiency(1, null);
            assertEquals(12, wizard.resolveFinalAttackInterval(12));
            assertEquals(spell == MagicSchoolSpell.AVADA_KEDAVRA ? -.6 : 0, wizard.spellAttackSpeedBonus());
            assertTrue(spell.effectDescription().contains("500 이하"));
            assertTrue(spell.effectDescription().contains("90% 감소"));
        }
    }

    @Test void protectionAggroIsAWaveSnapshotThatDoesNotStackAndResetsForTheNextRound() {
        var pos = new GridPosition(0, 64, 0);
        var economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.addMineral(10000);
        MagicSchoolCurriculum.unlockSpellTier(owner, 2, economy);
        MagicSchoolCurriculum.unlockSpellTier(owner, 3, economy);
        MagicSchoolCurriculum.unlockSpellTier(owner, 4, economy);
        for (var spell : java.util.List.of(MagicSchoolSpell.PROTEGO, MagicSchoolSpell.PROTEGO_MAXIMA)) {
            var wizard = new HouseWizardTower(MagicSchoolTowers.BRAVE_ARCHWIZARD, owner, TeamId.RED, 1, pos, pos);
            assertTrue(wizard.selectSpell(spell));
            assertEquals(0, wizard.aggroPriority());
            wizard.onWaveStarted(null, 1);
            assertEquals(60, wizard.aggroPriority());
            wizard.onWaveStarted(null, 1);
            assertEquals(60, wizard.aggroPriority());
            wizard.selectSpell(MagicSchoolSpell.STUPEFY);
            assertEquals(60, wizard.aggroPriority());
            var copy = new HouseWizardTower(MagicSchoolTowers.BRAVE_ARCHWIZARD, owner, TeamId.RED, 1, pos, pos);
            copy.copyFrom(wizard, 0);
            assertEquals(60, copy.aggroPriority());
            wizard.resetForRound(null);
            assertEquals(0, wizard.aggroPriority());
            wizard.onWaveStarted(null, 2);
            wizard.selectSpell(spell);
            assertEquals(0, wizard.aggroPriority(), "Equipping after wave start must wait for the next wave's aggro bonus.");
            wizard.onWaveStarted(null, 3);
            assertEquals(60, wizard.aggroPriority());
            assertTrue(spell.effectDescription().contains("어그로") && spell.effectDescription().contains("60"));
        }
    }

    @Test void configuredSpellDamageAndDescriptionsReloadTogetherAndInvalidTicksAreRejected() {
        var defaults = TowerBalanceConfig.defaultConfig();
        var changed = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolSpell.EXPELLIARMUS.configId(),
                Map.of("damageMultiplier", .9, "disarmTicks", 60.0),
                MagicSchoolSpell.PROTEGO.configId(), Map.of("waveAggroBonus", 75.0),
                MagicSchoolTowers.CONFIG_ID, Map.of("curseLowProficiencyAttackSpeedMultiplier", .8))).withMissingDefaults(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(changed);
        assertEquals(.9, MagicSchoolSpell.EXPELLIARMUS.damageMultiplier());
        assertTrue(MagicSchoolSpell.EXPELLIARMUS.effectDescription().contains("90%"));
        assertTrue(MagicSchoolSpell.EXPELLIARMUS.effectDescription().contains("3초"));
        assertTrue(MagicSchoolSpell.PROTEGO.effectDescription().contains("75"));
        assertTrue(MagicSchoolSpell.CRUCIO.effectDescription().contains("추가로 20% 감소"));
        var economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.addDiamond(200);
        MagicSchoolCurriculum.unlockSpellTier(owner, 2, economy);
        var pos = new GridPosition(0, 64, 0);
        var wizard = new FreshmanTower(MagicSchoolTowers.FRESHMAN, owner, TeamId.RED, 1, pos, pos);
        assertTrue(wizard.selectSpell(MagicSchoolSpell.PROTEGO));
        wizard.onWaveStarted(null, 1);
        assertEquals(75, wizard.aggroPriority(), "Configured aggro must drive actual wave behavior.");
        var bad = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolSpell.CRUCIO.configId(),
                Map.of("dotIntervalTicks", 0.5))).withMissingDefaults(defaults);
        assertThrows(IllegalArgumentException.class, bad::validateForRuntime);
        var fractionalAggro = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolSpell.PROTEGO.configId(),
                Map.of("waveAggroBonus", 60.5))).withMissingDefaults(defaults);
        assertThrows(IllegalArgumentException.class, fractionalAggro::validateForRuntime);
    }

    @Test void leviosaBackfillsWithoutOverwritingCustomValuesAndRejectsInvalidTiming() {
        var spell = MagicSchoolSpell.WINGARDIUM_LEVIOSA;
        var defaults = TowerBalanceConfig.defaultConfig();
        var changed = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(spell.configId(),
                Map.of("damageMultiplier", .7, "radius", 4.0, "intervalTicks", 80.0, "stunTicks", 30.0)))
                .withMissingDefaults(defaults);
        changed.validateForRuntime();
        ProductionTowerCatalogs.reloadBuiltIns(changed);
        assertEquals(.7, spell.damageMultiplier());
        assertEquals(.8, spell.value("liftPower"));
        assertTrue(spell.effectDescription().contains("70%"));
        assertTrue(spell.effectDescription().contains("4초"));
        assertTrue(spell.effectDescription().contains("4칸"));
        assertTrue(spell.effectDescription().contains("1.5초"));
        for (var invalid : Map.of("intervalTicks", 0.0, "stunTicks", 1.5, "radius", 0.0, "liftPower", -1.0).entrySet()) {
            var bad = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(spell.configId(),
                    Map.of(invalid.getKey(), invalid.getValue()))).withMissingDefaults(defaults);
            assertThrows(IllegalArgumentException.class, bad::validateForRuntime, invalid.getKey());
        }
    }

    @Test void changingCostsUseDestinationTierWhileBothRavenclawRanksStayFree() {
        long[] prices = {0, 20, 35, 50, 65, 80};
        var pos = new GridPosition(0, 64, 0);
        for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
            var wizard = (MagicSchoolWizardTower) kim.biryeong.semiontd.tower.ProductionTowerCatalog.find(type.id())
                    .orElseThrow().create(owner, TeamId.RED, 1, pos);
            boolean free = MagicSchoolTowers.belongsToHouse(type, MagicSchoolTowers.RAVENCLAW);
            for (var spell : MagicSchoolSpell.values()) {
                assertEquals(prices[spell.tier() - 1], spell.changeCost());
                assertEquals(free ? 0 : prices[spell.tier() - 1], wizard.spellChangeCost(spell));
            }
        }
    }

    @Test void changePricesBackfillAndReloadWithoutReplacingCustomSettings() {
        var defaults = TowerBalanceConfig.defaultConfig();
        var changed = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolTowers.CONFIG_ID,
                Map.of("spellChangeTier2Cost", 27.0))).withMissingDefaults(defaults);
        changed.validateForRuntime();
        ProductionTowerCatalogs.reloadBuiltIns(changed);
        assertEquals(27, MagicSchoolSpell.STUPEFY.changeCost());
        assertEquals(27, MagicSchoolSpell.PROTEGO.changeCost());
        assertEquals(35, MagicSchoolSpell.EXPULSO.changeCost());
        assertEquals(80, MagicSchoolSpell.CRUCIO.changeCost());
        for (var invalid : Map.of("spellChangeTier2Cost", 20.5, "spellChangeTier6Cost", -1.0).entrySet()) {
            var bad = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(MagicSchoolTowers.CONFIG_ID,
                    Map.of(invalid.getKey(), invalid.getValue()))).withMissingDefaults(defaults);
            assertThrows(IllegalArgumentException.class, bad::validateForRuntime, invalid.getKey());
        }
    }

    @Test void cleansingRemovesAllDebuffSourcesWithoutRemovingBuffs() {
        var effects = new TimedEffectSet();
        var source = Identifier.fromNamespaceAndPath("semion-td", "spell_test");
        effects.apply(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, .2, 20);
        effects.apply(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, source, .3, 30);
        effects.setPersistent(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, source, .1);
        effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, .2, 60);
        for (var type : TimedEffectType.values()) if (type.isTowerDebuff()) effects.remove(type);
        assertEquals(0, effects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION));
        assertEquals(.2, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS));
    }
}
