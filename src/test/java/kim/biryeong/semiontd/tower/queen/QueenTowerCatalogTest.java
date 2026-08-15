package kim.biryeong.semiontd.tower.queen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.QueenTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class QueenTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("queen-test".getBytes());

    @BeforeAll static void bootstrapMinecraft() {SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();}

    @AfterEach void cleanup() {
        QueenStates.clear(OWNER);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogRegistersOnlyQueenAndRandomCardAsStarters() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> QueenTowers.isQueenTower(entry.type())).toList();
        assertEquals(2, entries.size());
        assertEquals(2, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertTrue(JobRegistry.find(QueenTowerJob.ID).isPresent());
        assertInstanceOf(QueenTower.class, ProductionTowerCatalog.find(QueenTowers.QUEEN.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0)));
        assertInstanceOf(QueenCardTower.class, ProductionTowerCatalog.find(QueenTowers.RANDOM_CARD_SOLDIER.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(1, 64, 0)));
        assertTrue(ProductionTowerCatalog.upgrades(QueenTowers.QUEEN).isEmpty());
        assertTrue(ProductionTowerCatalog.upgrades(QueenTowers.RANDOM_CARD_SOLDIER).isEmpty());
        assertEquals("붉은 여왕", ProductionTowerCatalog.find(QueenTowers.QUEEN.id()).orElseThrow().type().displayName());
        assertEquals(55, QueenBalance.cardAggro(QueenCard.Suit.HEART));
        assertEquals(45, QueenBalance.cardAggro(QueenCard.Suit.DIAMOND));
        assertEquals(110, QueenBalance.cardAggro(QueenCard.Suit.CLUB));
        assertEquals(80, QueenBalance.cardAggro(QueenCard.Suit.SPADE));
        assertEquals("붉은 여왕 빌더", new QueenTowerJob().displayName().getString());
    }

    @Test
    void nextCardPreviewMatchesTheNextPlacedCard() {
        QueenStates.PlayerState state = QueenStates.state(OWNER);
        QueenCard preview = state.peekNextCard();

        assertEquals(preview, state.peekNextCard());
        assertEquals(preview, state.drawNextCard());
        assertTrue(state.peekNextCard() != null);
    }

    @Test
    void pokerRecognizesStandardHandsAceLowBroadwayAndFiveOfAKind() {
        assertEquals(PokerHand.HIGH_CARD, hand("H2", "D4", "C6", "S8", "H10"));
        assertEquals(PokerHand.ONE_PAIR, hand("H2", "D2", "C6", "S8", "H10"));
        assertEquals(PokerHand.TWO_PAIR, hand("H2", "D2", "C6", "S6", "H10"));
        assertEquals(PokerHand.THREE_OF_A_KIND, hand("H2", "D2", "C2", "S8", "H10"));
        assertEquals(PokerHand.STRAIGHT, hand("HA", "D2", "C3", "S4", "H5"));
        assertEquals(PokerHand.STRAIGHT, hand("HA", "D10", "CJ", "SQ", "HK"));
        assertEquals(PokerHand.FLUSH, hand("H2", "H4", "H6", "H8", "H10"));
        assertEquals(PokerHand.FULL_HOUSE, hand("H2", "D2", "C2", "S8", "H8"));
        assertEquals(PokerHand.FOUR_OF_A_KIND, hand("H2", "D2", "C2", "S2", "H8"));
        assertEquals(PokerHand.STRAIGHT_FLUSH, hand("H2", "H3", "H4", "H5", "H6"));
        assertEquals(PokerHand.ROYAL_FLUSH, hand("HA", "H10", "HJ", "HQ", "HK"));
        assertEquals(PokerHand.FIVE_OF_A_KIND, hand("H2", "H2", "H2", "H2", "H2"));
        assertEquals(List.of(0.0, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.65, 0.80, 1.00),
                java.util.Arrays.stream(PokerHand.values()).map(PokerHand::defaultBonus).toList());
    }

    @Test
    void pokerSnapshotAppliesTheConfiguredHealthAndAttackSpeedBonus() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        QueenCardTower card = (QueenCardTower) ProductionTowerCatalog.find(QueenTowers.RANDOM_CARD_SOLDIER.id())
                .orElseThrow().create(OWNER, TeamId.RED, 1, new GridPosition(1, 64, 0));
        card.assignCard(new QueenCard(QueenCard.Suit.HEART, 2));

        card.applyPokerSnapshot(PokerHand.FIVE_OF_A_KIND);

        assertEquals(QueenBalance.cardMaxHealth(QueenCard.Suit.HEART) * 2.0, card.currentMaxHealth(), 0.0001);
        assertEquals(QueenBalance.cardInterval(QueenCard.Suit.HEART) / 2, card.adjustAttackInterval(999));
    }

    @Test
    void executionGrowthUsesTheTargetAndCurrentThresholdCaps() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        QueenStates.PlayerState state = QueenStates.state(OWNER);
        state.growExecutionHealth(10_000.0);
        assertEquals(54.0, state.executionHealth(), 0.0001);
        state.growExecutionHealth(100.0);
        assertEquals(56.0, state.executionHealth(), 0.0001);
    }

    @Test
    void playerStateIsIsolatedAndClearRestoresDefaults() {
        UUID other = UUID.nameUUIDFromBytes("queen-other".getBytes());
        try {
            QueenStates.state(OWNER).addCharge(123.0);
            assertEquals(0.0, QueenStates.state(other).charge(), 0.0001);
            QueenStates.clear(OWNER);
            assertEquals(0.0, QueenStates.state(OWNER).charge(), 0.0001);
            assertEquals(QueenBalance.giantInitialExecutionHealth(),
                    QueenStates.state(OWNER).executionHealth(), 0.0001);
        } finally {
            QueenStates.clear(other);
        }
    }

    @Test
    void permanentMonsterScalePreservesHealthRatioAndStacksWithoutKilling() {
        Monster monster = new Monster("queen-scale", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                1000, 0, 100, AttackKind.MELEE, "minecraft:zombie", 0);
        monster.syncHealth(500);
        monster.applyPermanentStatScale(0.8, 0.10);
        monster.applyPermanentStatScale(0.5, 0.10);
        assertEquals(400, monster.maxHealth(), 0.0001);
        assertEquals(200, monster.health(), 0.0001);
        assertEquals(40, monster.attackDamage(), 0.0001);
        assertEquals(0.4, monster.permanentStatScale(), 0.0001);
        assertEquals(0.4, monster.visualScale(), 0.0001);
        for (int i = 0; i < 200; i++) monster.applyPermanentStatScale(0.5, 0.10);
        assertTrue(monster.health() > 0.0);
        assertEquals(0.10, monster.visualScale(), 0.0001);
    }

    @Test
    void defaultsMergeAndRejectInvalidQueenValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        QueenTowers.all().forEach(type -> assertTrue(defaults.towers().containsKey(type.id())));
        assertEquals(0.98, defaults.ability(QueenBalance.GLOBAL_ID, "shrinkFactorPerPoint", -1), 0.0001);
        assertEquals(0.20, defaults.ability(QueenBalance.GLOBAL_ID, "minimumStatScale", -1), 0.0001);
        TowerBalanceConfig merged = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(
                QueenBalance.GLOBAL_ID, Map.of("queenShrinkPoints", 4.0))).withMissingDefaults(defaults);
        assertEquals(70, merged.towers().get(QueenTowers.QUEEN.id()).mineralCost());
        assertEquals(4.0, merged.ability(QueenBalance.GLOBAL_ID, "queenShrinkPoints", -1), 0.0001);
        assertEquals(0.20, merged.ability(QueenBalance.GLOBAL_ID, "minimumStatScale", -1), 0.0001);
        assertEquals(400, merged.abilityInt(QueenBalance.GLOBAL_ID, "giantChargeTicks", -1));
        assertEquals(50.0, merged.ability(QueenBalance.GLOBAL_ID,
                "giantInitialExecutionHealth", -1), 0.0001);
        assertEquals(4.0, merged.ability(QueenBalance.GLOBAL_ID,
                "giantGrowthTargetCapMultiplier", -1), 0.0001);
        assertEquals(80, merged.abilityInt(QueenBalance.GLOBAL_ID,
                "rangeVfxIntervalTicks", -1));
        assertEquals(4.0, merged.ability(QueenBalance.GLOBAL_ID,
                "giantContactRadius", -1), 0.0001);
        assertEquals(1.25, merged.ability(QueenBalance.GLOBAL_ID,
                "cardSplashRadius", -1), 0.0001);
        assertEquals(1, merged.abilityInt(QueenBalance.GLOBAL_ID,
                "cardSplashExtraTargets", -1));

        assertInvalidAbility(defaults, "shrinkFactorPerPoint", 1.0);
        assertInvalidAbility(defaults, "minimumStatScale", 0.0);
        assertInvalidAbility(defaults, "minimumVisualScale", 1.1);
        assertInvalidAbility(defaults, "rangeVfxIntervalTicks", 20.5);
        assertInvalidAbility(defaults, "card.heart.aggro", 55.5);
        assertInvalidAbility(defaults, "spadeRadius", 1.0);
        assertInvalidAbility(defaults, "hand.full_house", 0.10);
    }

    @Test
    void bundledQueenDefaultsMatchJavaDefaults() throws Exception {
        try (var input = QueenTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var bundled = JsonParser.parseReader(new InputStreamReader(java.util.Objects.requireNonNull(input),
                    StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("abilities")
                    .getAsJsonObject(QueenBalance.GLOBAL_ID);
            Map<String, Double> defaults = TowerBalanceConfig.defaultConfig().abilities().get(QueenBalance.GLOBAL_ID);
            assertEquals(defaults.keySet(), bundled.keySet());
            defaults.forEach((key, value) -> assertEquals(value, bundled.get(key).getAsDouble(), 0.0001, key));
        }
    }

    private static void assertInvalidAbility(TowerBalanceConfig defaults, String key, double value) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> invalid = new LinkedHashMap<>(abilities.get(QueenBalance.GLOBAL_ID));
        invalid.put(key, value);
        abilities.put(QueenBalance.GLOBAL_ID, invalid);
        TowerBalanceConfig broken = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion());
        assertThrows(IllegalArgumentException.class, broken::validateForRuntime);
    }

    private static PokerHand hand(String... specs) {
        return PokerHand.evaluate(java.util.Arrays.stream(specs).map(QueenTowerCatalogTest::card).toList());
    }

    private static QueenCard card(String spec) {
        QueenCard.Suit suit = switch (spec.charAt(0)) {
            case 'H' -> QueenCard.Suit.HEART;
            case 'D' -> QueenCard.Suit.DIAMOND;
            case 'C' -> QueenCard.Suit.CLUB;
            case 'S' -> QueenCard.Suit.SPADE;
            default -> throw new IllegalArgumentException(spec);
        };
        String rank = spec.substring(1);
        int value = switch (rank) {case "A" -> 1; case "J" -> 11; case "Q" -> 12; case "K" -> 13; default -> Integer.parseInt(rank);};
        return new QueenCard(suit, value);
    }
}
