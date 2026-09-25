package kim.biryeong.semiontd.tower.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
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
import org.junit.jupiter.api.Test;

class PetAugmentsTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("pet-augments".getBytes());

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void largerYardBindsDistanceTwoWithoutLengtheningDogAdjacency() {
        PetTower owner = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower close = tower(PetTowers.DOG_T1, 0, 1);
        PetTower far = tower(PetTowers.DOG_T1, 2, 0);
        PetTower outside = tower(PetTowers.CAT_T1, 3, 0);
        owner.syncAugments(snapshot(PetTower.LARGE_YARD), null);
        PetBondService.refresh(List.of(owner, close, far, outside));
        assertEquals(owner.position(), far.loyalOwnerPosition());
        assertNull(outside.loyalOwnerPosition());
        assertEquals(1, close.packSize());
        assertEquals(1, far.packSize());
    }

    @Test
    void earlyCombatUnlockDoesNotChangeBondAppearanceOrUpgradeEligibility() {
        PetTower dog = tower(PetTowers.DOG_T1, 1, 0);
        dog.syncAugments(snapshot(PetTower.GROWN_UP), null);
        assertFalse(dog.isAdult());
        assertTrue(dog.hasAdultCombatAbilities());
        assertEquals(0.0, dog.bond());
        assertEquals(PetBalance.PUP_SCALE, dog.renderScale(), .0001);
        assertEquals(100 * (1 - PetBalance.adultDamageReduction(dog.type())),
                dog.modifyIncomingDamage(null, null, 100), .0001);
        assertFalse(dog.meetsUpgradeRequirements(null,
                ProductionTowerCatalog.upgrade(PetTowers.DOG_T1, PetTowers.DOG_T2.id()).orElseThrow()));
    }

    @Test
    void familyNeedsActualAdultsAndSharesPackHealthWithoutHealing() {
        PetTower owner = tower(PetTowers.KEEPER_T1, 0, 0);
        PetTower dog = tower(PetTowers.DOG_T1, 1, 0);
        PetTower cat = tower(PetTowers.CAT_T1, 1, 1);
        PetTower cat2 = tower(PetTowers.CAT_T1, 0, 1);
        PetTower bird = tower(PetTowers.BIRD_T1, -1, 1);
        List<PetTower> pets = List.of(owner, dog, cat, cat2, bird);
        pets.forEach(pet -> pet.syncAugments(snapshot(PetTower.FAMILY, PetTower.GROWN_UP), null));
        PetBondService.refresh(List.copyOf(pets));
        assertFalse(cat.hasFamilyYard());
        assertFalse(cat.isSoloCat());
        dog.addBond(PetBalance.bondToUpgrade(dog.type()));
        cat.addBond(PetBalance.bondToUpgrade(cat.type()));
        bird.addBond(PetBalance.bondToUpgrade(bird.type()));
        cat.syncHealth(cat.currentMaxHealth() * .4);
        double previousHealth = cat.currentMaxHealth();
        PetBondService.refresh(List.copyOf(pets));
        assertTrue(cat.hasFamilyYard());
        assertTrue(cat2.isSoloCat());
        assertEquals(4, cat.packSize());
        assertEquals(previousHealth * (1 + PetBalance.packHealthBonus(PetTowers.DOG_T1, 4)), cat.currentMaxHealth(), .0001);
        assertEquals(cat.currentMaxHealth() * .4, cat.health(), .0001);
        bird.syncHealth(0);
        PetBondService.refresh(List.copyOf(pets));
        assertFalse(cat.hasFamilyYard());
        assertFalse(cat.isSoloCat());
        assertEquals(previousHealth, cat.currentMaxHealth(), .0001);
    }

    private static PetTower tower(TowerType type, int x, int z) {
        return new PetTower(type, OWNER, TeamId.RED, 0, new GridPosition(x, 64, z));
    }

    private static AugmentSnapshot snapshot(String... cards) {
        return new AugmentSnapshot(AugmentConfig.defaults(), java.util.Arrays.stream(cards)
                .map(card -> new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, card,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }
}
