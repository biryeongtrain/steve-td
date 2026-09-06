package kim.biryeong.semiontd.tower.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PetBondServiceTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("pet-bond".getBytes(StandardCharsets.UTF_8));
    private static final UUID OTHER_PLAYER = UUID.nameUUIDFromBytes("pet-bond-2".getBytes(StandardCharsets.UTF_8));
    private static final int Y = 80;

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
    void companionImprintsOnAnAdjacentOwner() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);

        PetBondService.refresh(towers(butler, cat));

        assertEquals(butler.position(), cat.loyalOwnerPosition());
        assertTrue(cat.hasActiveOwner());
        assertFalse(cat.isLost());
        assertEquals(1, cat.yardCompanions());
    }

    @Test
    void diagonalTilesCountAsYardButDistanceTwoDoesNot() {
        PetTower butler = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower diagonal = tower(PetTowers.CAT_T1, 1, 1);
        PetTower faraway = tower(PetTowers.DOG_T1, 2, 0);

        PetBondService.refresh(towers(butler, diagonal, faraway));

        assertEquals(butler.position(), diagonal.loyalOwnerPosition());
        assertNull(faraway.loyalOwnerPosition());
        assertTrue(faraway.isLost());
    }

    @Test
    void companionWithNoOwnerIsLost() {
        PetTower dog = tower(PetTowers.DOG_T1, 0, 0);

        PetBondService.refresh(towers(dog));

        assertNull(dog.loyalOwnerPosition());
        assertTrue(dog.isLost());
        assertEquals(0, dog.yardCompanions());
    }

    @Test
    void aCompanionBetweenTwoOwnersJoinsOnlyOneYard() {
        PetTower first = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower second = tower(PetTowers.KEEPER_T1, 2, 0);
        PetTower dog = tower(PetTowers.DOG_T1, 1, 0);

        PetBondService.refresh(towers(first, second, dog));

        assertEquals(first.position(), dog.loyalOwnerPosition());
        assertEquals(1, first.position().equals(dog.loyalOwnerPosition()) ? 1 : 0);
        assertEquals(1, dog.yardCompanions());
    }

    @Test
    void loyaltySurvivesAnotherOwnerArrivingLater() {
        PetTower first = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower dog = tower(PetTowers.DOG_T1, 1, 0);
        List<Tower> board = towers(first, dog);
        PetBondService.refresh(board);
        assertEquals(first.position(), dog.loyalOwnerPosition());

        PetTower latecomer = tower(PetTowers.BUTLER_T1, 2, 0);
        board.add(latecomer);
        PetBondService.refresh(board);

        assertEquals(first.position(), dog.loyalOwnerPosition());
    }

    @Test
    void removingTheOwnerReleasesLoyaltySoTheCompanionCanImprintAgain() {
        PetTower first = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower dog = tower(PetTowers.DOG_T1, 1, 0);
        List<Tower> board = towers(first, dog);
        PetBondService.refresh(board);

        board.remove(first);
        PetTower replacement = tower(PetTowers.BUTLER_T1, 2, 0);
        board.add(replacement);
        PetBondService.refresh(board);

        assertEquals(replacement.position(), dog.loyalOwnerPosition());
        assertFalse(dog.isLost());
    }

    @Test
    void yardCountsOnlyTheOwnersOwnCompanions() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        List<Tower> board = towers(keeper);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    board.add(tower(PetTowers.DOG_T1, x, z));
                }
            }
        }

        PetBondService.refresh(board);

        for (Tower tower : board) {
            if (tower instanceof PetTower pet && pet.isCompanion()) {
                assertEquals(8, pet.yardCompanions());
            }
        }
    }

    @Test
    void touchingDogsFormOnePackAndEveryMemberSeesItsSize() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower centre = tower(PetTowers.DOG_T1, 1, 0);
        PetTower above = tower(PetTowers.DOG_T1, 1, -1);
        PetTower below = tower(PetTowers.DOG_T1, 1, 1);
        PetTower cat = tower(PetTowers.CAT_T1, 0, 1);

        PetBondService.refresh(towers(keeper, centre, above, below, cat));

        // above and below never touch each other, but the centre dog chains them into one pack.
        assertEquals(3, centre.packSize());
        assertEquals(3, above.packSize());
        assertEquals(3, below.packSize());
        assertEquals(0, cat.packSize(), "only dogs form packs");
        assertEquals(0.16, PetBalance.packBonus(PetTowers.DOG_T1, centre.packSize()), 1e-9);
        assertEquals(0.16, PetBalance.packHealthBonus(PetTowers.DOG_T1, centre.packSize()), 1e-9);
    }

    @Test
    void aPackChainsThroughTheYardAndSpansBothSidesOfTheOwner() {
        // Three dogs in front of the owner, plus one on each flank, all linked into a pack of five.
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        List<Tower> board = towers(keeper,
                tower(PetTowers.DOG_T1, -1, -1),
                tower(PetTowers.DOG_T1, 0, -1),
                tower(PetTowers.DOG_T1, 1, -1),
                tower(PetTowers.DOG_T1, -1, 0),
                tower(PetTowers.DOG_T1, 1, 0));

        PetBondService.refresh(board);

        for (Tower tower : board) {
            if (tower instanceof PetTower pet && pet.role() == PetRole.DOG) {
                assertEquals(5, pet.packSize());
                assertEquals(0.32, PetBalance.packBonus(pet.type(), pet.packSize()), 1e-9);
                assertEquals(0.32, PetBalance.packHealthBonus(pet.type(), pet.packSize()), 1e-9);
            }
        }
    }

    @Test
    void separatedDogsAreSeparatePacks() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower nearA = tower(PetTowers.DOG_T1, -1, 0);
        PetTower nearB = tower(PetTowers.DOG_T1, -1, 1);
        PetTower alone = tower(PetTowers.DOG_T1, 1, 0);

        PetBondService.refresh(towers(keeper, nearA, nearB, alone));

        assertEquals(2, nearA.packSize());
        assertEquals(2, nearB.packSize());
        assertEquals(1, alone.packSize());
        assertEquals(0.0, PetBalance.packBonus(PetTowers.DOG_T1, alone.packSize()), 1e-9,
                "a lone dog gets nothing from the pack rule");
    }

    @Test
    void packsIgnoreYardBoundariesAndHaveNoCeiling() {
        // Two owners with their own yards; the dogs between them still count as one pack.
        List<Tower> board = towers(
                tower(PetTowers.KEEPER_T1, 0, 0),
                tower(PetTowers.KEEPER_T1, 4, 0));
        for (int x = -1; x <= 5; x++) {
            board.add(tower(PetTowers.DOG_T1, x, 1));
        }

        PetBondService.refresh(board);

        for (Tower tower : board) {
            if (tower instanceof PetTower pet && pet.role() == PetRole.DOG) {
                assertEquals(7, pet.packSize(), "a chain of seven dogs is one pack across two yards");
            }
        }
        // No cap: seven dogs pay six mates.
        assertEquals(0.48, PetBalance.packBonus(PetTowers.DOG_T1, 7), 1e-9);
        assertEquals(0.80, PetBalance.packBonus(PetTowers.DOG_T1, 11), 1e-9);
    }

    @Test
    void aCatIsSoloOnlyWhileItIsTheOnlyCatInItsYard() {
        PetTower keeper = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 0);
        PetTower dog = tower(PetTowers.DOG_T1, -1, 0);
        List<Tower> board = towers(keeper, cat, dog);

        PetBondService.refresh(board);
        assertTrue(cat.isSoloCat(), "a cat sharing the yard with a dog is still solo");

        PetTower secondCat = tower(PetTowers.CAT_T1, 0, 1);
        board.add(secondCat);
        PetBondService.refresh(board);

        assertFalse(cat.isSoloCat());
        assertFalse(secondCat.isSoloCat());
    }

    @Test
    void catsInSeparateYardsBothStaySolo() {
        PetTower firstOwner = tower(PetTowers.BUTLER_T1, 0, 0);
        PetTower firstCat = tower(PetTowers.CAT_T1, 1, 0);
        PetTower secondOwner = tower(PetTowers.BUTLER_T1, 0, 4);
        PetTower secondCat = tower(PetTowers.CAT_T1, 1, 4);

        PetBondService.refresh(towers(firstOwner, firstCat, secondOwner, secondCat));

        assertTrue(firstCat.isSoloCat());
        assertTrue(secondCat.isSoloCat());
        assertEquals(1, firstCat.yardCompanions());
        assertEquals(1, secondCat.yardCompanions());
    }

    @Test
    void anotherPlayersOwnerNeverAdoptsThisPlayersCompanion() {
        PetTower foreignOwner = new PetTower(PetTowers.BUTLER_T1, OTHER_PLAYER, TeamId.RED, 1,
                new GridPosition(0, Y, 0));
        PetTower dog = tower(PetTowers.DOG_T1, 1, 0);

        PetBondService.refresh(towers(foreignOwner, dog));

        assertNull(dog.loyalOwnerPosition());
        assertTrue(dog.isLost());
    }

    private static PetTower tower(TowerType type, int x, int z) {
        return new PetTower(type, OWNER, TeamId.RED, 1, new GridPosition(x, Y, z));
    }

    private static List<Tower> towers(Tower... values) {
        return new ArrayList<>(List.of(values));
    }
}
