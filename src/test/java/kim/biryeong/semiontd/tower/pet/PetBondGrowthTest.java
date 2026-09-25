package kim.biryeong.semiontd.tower.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PetBondGrowthTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("pet-growth".getBytes(StandardCharsets.UTF_8));
    private static final int Y = 80;
    private static final double EPSILON = 1e-6;

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
    void butlerPoursItsWholeGrantIntoASingleCompanion() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);
        List<Tower> board = board(butler, cat);
        PetBondService.refresh(board);

        startRound(board, 1);

        // 1 (round) + 20 / 1^1.0 (grant) + 1 + 0.5 * 7 empty tiles (walk)
        assertEquals(25.5, cat.bond(), EPSILON);
    }

    @Test
    void keeperSpreadsAThinnerGrantAcrossAFullYard() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        List<Tower> board = board(keeper);
        List<PetTower> dogs = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    PetTower dog = tower(PetTowers.DOG_T1, x, z);
                    dogs.add(dog);
                    board.add(dog);
                }
            }
        }
        PetBondService.refresh(board);

        startRound(board, 1);

        // 1 (round) + 8 / 8^0.3 (grant) + 3.0 (keeper walks a flat amount)
        double expected = 1.0 + 8.0 / Math.pow(8, 0.3) + 3.0;
        for (PetTower dog : dogs) {
            assertEquals(expected, dog.bond(), EPSILON);
        }
        assertTrue(dogs.get(0).bond() < 9.0, "a full yard should grow far slower per head than a butler yard");
    }

    @Test
    void trainerRaisesTheCeilingRatherThanTheRate() {
        PetTower trainer = tower(PetTowers.TRAINER_T1, 0, 0);
        PetTower trainedDog = tower(PetTowers.DOG_T1, 1, 0);
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 5);
        PetTower butlerDog = tower(PetTowers.DOG_T1, 1, 5);
        List<Tower> board = board(trainer, trainedDog, butler, butlerDog);
        PetBondService.refresh(board);

        assertEquals(200.0, trainedDog.bondCap());
        assertEquals(100.0, butlerDog.bondCap());

        for (int round = 1; round <= 30; round++) {
            startRound(board, round);
        }

        assertEquals(100.0, butlerDog.bond(), EPSILON, "butler pet stalls at the base ceiling");
        assertTrue(trainedDog.bond() > 100.0, "trainer pet keeps growing past it");
        assertTrue(trainedDog.bond() <= 200.0);
    }

    @Test
    void bondLiftsAttackAndHealthByItsConfiguredRates() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower dog = tower(PetTowers.DOG_T1, 1, 0);
        List<Tower> board = board(keeper, dog);
        PetBondService.refresh(board);
        double baseMaxHealth = dog.currentMaxHealth();

        dog.addBond(100.0);

        assertEquals(100.0, dog.bond(), EPSILON);
        assertEquals(1.8, PetBalance.attackMultiplier(dog.bond()), EPSILON);
        assertEquals(baseMaxHealth * 1.4, dog.currentMaxHealth(), EPSILON);
        assertTrue(dog.health() > baseMaxHealth, "gaining max health should not leave the pet below its new cap");
    }

    @Test
    void packAndSoloBonusesStackOnTopOfBond() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower centre = tower(PetTowers.DOG_T1, 1, 0);
        PetTower mateA = tower(PetTowers.DOG_T1, 1, -1);
        PetTower mateB = tower(PetTowers.DOG_T1, 1, 1);
        PetTower cat = tower(PetTowers.CAT_T1, -1, 0);
        List<Tower> board = board(keeper, centre, mateA, mateB, cat);
        PetBondService.refresh(board);

        // A pack of three pays two mates.
        assertEquals(1.0 * (1.0 + 2 * 0.08), centre.companionDamageMultiplier(), EPSILON);
        assertEquals(centre.maxHealth() * (1.0 + 2 * 0.08), centre.currentMaxHealth(), EPSILON);
        assertEquals(1.0 * (1.0 + 2.0), cat.companionDamageMultiplier(), EPSILON);

        centre.addBond(50.0);
        // bond 50 -> x1.4, two pack mates -> x1.16
        assertEquals(1.4 * 1.16, centre.companionDamageMultiplier(), EPSILON);
        assertEquals(centre.maxHealth() * 1.2 * 1.16, centre.currentMaxHealth(), EPSILON);

        centre.syncHealth(centre.currentMaxHealth() / 2.0);
        board.remove(mateB);
        PetBondService.refresh(board);
        assertEquals(centre.maxHealth() * 1.2 * 1.08, centre.currentMaxHealth(), EPSILON);
        assertEquals(centre.currentMaxHealth() / 2.0, centre.health(), EPSILON,
                "pack changes must preserve current health ratio");
    }

    @Test
    void aSecondCatInTheYardCancelsTheSoloBonus() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);
        List<Tower> board = board(keeper, cat);
        PetBondService.refresh(board);
        assertEquals(3.0, cat.companionDamageMultiplier(), EPSILON);

        board.add(tower(PetTowers.CAT_T1, -1, 0));
        PetBondService.refresh(board);

        assertEquals(1.0, cat.companionDamageMultiplier(), EPSILON);
    }

    @Test
    void lostCompanionsKeepOnlyTheConfiguredFraction() {
        PetTower dog = tower(PetTowers.DOG_T1, 0, 0);
        List<Tower> board = board(dog);
        PetBondService.refresh(board);

        assertTrue(dog.isLost());
        assertEquals(0.4, dog.companionDamageMultiplier(), EPSILON);

        startRound(board, 1);
        assertEquals(0.0, dog.bond(), EPSILON, "a lost companion earns no bond");
    }

    @Test
    void upgradesUnlockOnlyAfterTheCompanionGrowsUp() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);
        List<Tower> board = board(butler, cat);
        PetBondService.refresh(board);
        var upgrade = ProductionTowerCatalog.upgrade(PetTowers.CAT_T1, PetTowers.CAT_T2.id()).orElseThrow();

        assertFalse(cat.isAdult());
        assertFalse(cat.meetsUpgradeRequirements(null, upgrade));

        cat.addBond(70.0);

        assertTrue(cat.isAdult());
        assertTrue(cat.meetsUpgradeRequirements(null, upgrade));
    }

    @Test
    void adultDogsReduceIncomingDamageByTier() {
        PetTower tierOne = tower(PetTowers.DOG_T1, 0, 0);
        PetTower tierTwo = tower(PetTowers.DOG_T2, 0, 1);
        PetTower tierThree = tower(PetTowers.DOG_T3, 0, 2);

        assertEquals(100.0, tierOne.modifyIncomingDamage(null, null, 100.0), EPSILON);
        assertEquals(100.0, tierTwo.modifyIncomingDamage(null, null, 100.0), EPSILON);

        tierOne.addBond(PetBalance.bondToUpgrade(PetTowers.DOG_T1));
        tierTwo.addBond(PetBalance.bondToUpgrade(PetTowers.DOG_T2));

        assertEquals(90.0, tierOne.modifyIncomingDamage(null, null, 100.0), EPSILON);
        assertEquals(85.0, tierTwo.modifyIncomingDamage(null, null, 100.0), EPSILON);
        assertTrue(tierThree.isAdult(), "The final tier must always receive adult effects.");
        assertEquals(80.0, tierThree.modifyIncomingDamage(null, null, 100.0), EPSILON);
    }

    @Test
    void ownersHaveNoBondRequirementOfTheirOwn() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        var upgrade = ProductionTowerCatalog.upgrade(PetTowers.BUTLER_T1, PetTowers.BUTLER_T2.id()).orElseThrow();

        assertTrue(butler.meetsUpgradeRequirements(null, upgrade));
    }

    @Test
    void bondSurvivesTheUpgradeToTheNextTier() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);
        PetBondService.refresh(board(butler, cat));
        cat.addBond(90.0);

        PetTower upgraded = (PetTower) ProductionTowerCatalog.find(PetTowers.CAT_T2.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, cat.position());
        upgraded.copyFrom(cat, 0L);

        assertEquals(90.0, upgraded.bond(), EPSILON);
        assertEquals(butler.position(), upgraded.loyalOwnerPosition());
        assertEquals(200.0, upgraded.bondCap(), "the next tier raises the ceiling");
        assertFalse(upgraded.isAdult(), "and the next threshold has to be earned again");
    }

    @Test
    void praiseIsCappedPerRoundAndDoesNotBankIntoTheNextOne() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);
        List<Tower> board = board(butler, cat);
        PetBondService.refresh(board);
        startRound(board, 1);
        double afterGrant = cat.bond();

        // 200 kills is 20 praise worth of kills, but the round only pays out five.
        for (int kill = 0; kill < 200; kill++) {
            cat.onKill(null, null, 1.0);
        }
        assertEquals(afterGrant + 5.0, cat.bond(), EPSILON, "the round cap holds");

        double beforeNextRound = cat.bond();
        startRound(board, 2);
        double grantOnly = cat.bond() - beforeNextRound;
        cat.onKill(null, null, 1.0);

        assertEquals(beforeNextRound + grantOnly, cat.bond(), EPSILON,
                "banked kills must not dump into the next round");
    }

    @Test
    void aDeadCompanionStopsTakingUpRoomInTheYard() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower first = tower(PetTowers.DOG_T1, 1, 0);
        PetTower second = tower(PetTowers.DOG_T1, -1, 0);
        List<Tower> board = board(keeper, first, second);
        PetBondService.refresh(board);
        assertEquals(2, first.yardCompanions());

        second.syncHealth(0.0);
        PetBondService.refresh(board);

        assertEquals(1, first.yardCompanions(),
                "yard headcount must match how packs and solo cats count the living");
    }

    /**
     * Loyalty points at the owner's grid position, not the owner instance, precisely so that an
     * owner upgrading does not orphan everything around it.
     */
    @Test
    void upgradingTheOwnerKeepsTheYardBonded() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);
        List<Tower> board = board(butler, cat);
        PetBondService.refresh(board);
        cat.addBond(40.0);
        assertEquals(PetTowers.BUTLER_T1, cat.loyalOwnerType());

        PetTower upgradedOwner = (PetTower) ProductionTowerCatalog.find(PetTowers.BUTLER_T2.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, butler.position());
        upgradedOwner.copyFrom(butler, 0L);
        board.remove(butler);
        board.add(upgradedOwner);
        PetBondService.refresh(board);

        assertFalse(cat.isLost(), "the upgrade must not orphan the companion");
        assertEquals(upgradedOwner.position(), cat.loyalOwnerPosition());
        assertEquals(PetTowers.BUTLER_T2, cat.loyalOwnerType(), "the yard now runs on the upgraded owner");
        assertEquals(40.0, cat.bond(), EPSILON, "and the bond it already earned is untouched");

        startRound(board, 2);
        // The tier-two butler grants 26 instead of 20.
        assertEquals(40.0 + 1.0 + 26.0 + 4.5, cat.bond(), EPSILON);
    }

    private static void startRound(List<Tower> board, int round) {
        for (Tower tower : board) {
            tower.onWaveStarted(null, round);
        }
    }

    private static PetTower tower(TowerType type, int x, int z) {
        return new PetTower(type, OWNER, TeamId.RED, 1, new GridPosition(x, Y, z));
    }

    private static List<Tower> board(Tower... values) {
        return new ArrayList<>(List.of(values));
    }
}
