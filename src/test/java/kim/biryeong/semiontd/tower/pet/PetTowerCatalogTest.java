package kim.biryeong.semiontd.tower.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.PetTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.animal.CatVariants;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PetTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("pet-owner".getBytes(StandardCharsets.UTF_8));

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
    void catalogHoldsThreeOwnersAndThreeCompanionLines() {
        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> PetTowers.isPetTower(entry.type()))
                .toList();

        assertEquals(15, entries.size());
        assertEquals(6, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertEquals(6, PetTowers.all().stream().filter(PetTowers::isOwner).count());
        assertEquals(9, PetTowers.all().stream().filter(PetTowers::isCompanion).count());
    }

    @Test
    void everyTowerBelongsToExactlyOneBuilderAndUsesTheSharedRuntime() {
        var job = JobRegistry.find(PetTowerJob.ID).orElseThrow();
        for (TowerType type : PetTowers.all()) {
            assertTrue(job.includesTowerInCatalog(type), type.id());
            assertEquals(1, JobRegistry.all().stream().filter(owner -> owner.includesTowerInCatalog(type)).count(),
                    type.id());
            assertInstanceOf(PetTower.class, ProductionTowerCatalog.find(type.id()).orElseThrow()
                    .create(OWNER, TeamId.RED, 1, new GridPosition(0, 80, 0)));
        }
    }

    @Test
    void ownersNeverAttackAndSitAtTheBottomOfTheAggroOrder() {
        for (TowerType type : PetTowers.all()) {
            if (!PetTowers.isOwner(type)) {
                continue;
            }
            assertEquals(0.0, type.range(), type.id());
            assertEquals(0.0, type.damage(), type.id());
            assertTrue(type.aggroPriority() <= -40, type.id());
        }
    }

    @Test
    void upgradeCostsMatchTheShippedConfig() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        config.validateForRuntime();

        assertUpgrade(PetTowers.BUTLER_T1, PetTowers.BUTLER_T2, 220);
        assertUpgrade(PetTowers.TRAINER_T1, PetTowers.TRAINER_T2, 200);
        assertUpgrade(PetTowers.KEEPER_T1, PetTowers.KEEPER_T2, 190);
        assertUpgrade(PetTowers.DOG_T1, PetTowers.DOG_T2, 110);
        assertUpgrade(PetTowers.DOG_T2, PetTowers.DOG_T3, 240);
        assertUpgrade(PetTowers.CAT_T1, PetTowers.CAT_T2, 120);
        assertUpgrade(PetTowers.CAT_T2, PetTowers.CAT_T3, 260);
        assertUpgrade(PetTowers.BIRD_T1, PetTowers.BIRD_T2, 125);
        assertUpgrade(PetTowers.BIRD_T2, PetTowers.BIRD_T3, 270);
    }

    @Test
    void bondAndSpeciesAbilitiesComeFromConfigWithoutNegativeValues() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertEquals(1.0, config.ability(PetBalance.CONFIG_ID, "bondPerRound", -1));
        assertEquals(10, config.abilityInt(PetBalance.CONFIG_ID, "praiseKillsPerBond", -1));
        assertEquals(0.008, config.ability(PetBalance.CONFIG_ID, "bondAttackPerPoint", -1));
        assertEquals(0.004, config.ability(PetBalance.CONFIG_ID, "bondHealthPerPoint", -1));
        assertEquals(0.4, config.ability(PetBalance.CONFIG_ID, "lostPetMultiplier", -1));

        assertEquals(20.0, config.ability(PetTowers.BUTLER_T1.id(), PetBalance.KEY_BOND_GRANT_BASE, -1));
        assertEquals(1.0, config.ability(PetTowers.BUTLER_T1.id(), PetBalance.KEY_BOND_GRANT_EXPONENT, -1));
        assertEquals(2.0, config.ability(PetTowers.TRAINER_T1.id(), PetBalance.KEY_BOND_CAP_MULTIPLIER, -1));
        assertEquals(0.6, config.ability(PetTowers.TRAINER_T1.id(), PetBalance.KEY_BOND_GRANT_EXPONENT, -1));
        assertEquals(0.3, config.ability(PetTowers.KEEPER_T1.id(), PetBalance.KEY_BOND_GRANT_EXPONENT, -1));
        assertEquals(3.0, config.ability(PetTowers.KEEPER_T1.id(), PetBalance.KEY_WALK_BOND_FLAT, -1));

        assertEquals(0.08, config.ability(PetTowers.DOG_T3.id(), PetBalance.KEY_PACK_DAMAGE_PER_MATE, -1));
        assertEquals(0.08, config.ability(PetTowers.DOG_T3.id(), PetBalance.KEY_PACK_HEALTH_PER_MATE, -1));
        assertEquals(0.10, PetBalance.adultDamageReduction(PetTowers.DOG_T1));
        assertEquals(0.15, PetBalance.adultDamageReduction(PetTowers.DOG_T2));
        assertEquals(0.20, PetBalance.adultDamageReduction(PetTowers.DOG_T3));
        assertEquals(2.0, config.ability(PetTowers.CAT_T3.id(), PetBalance.KEY_SOLO_DAMAGE_BONUS, -1));
        assertEquals(1.5, PetBalance.adultSplashRadius(PetTowers.CAT_T1));
        assertEquals(2.0, PetBalance.adultSplashRadius(PetTowers.CAT_T2));
        assertEquals(2.5, PetBalance.adultSplashRadius(PetTowers.CAT_T3));
        assertEquals(2, PetBalance.adultSplashMaxTargets(PetTowers.CAT_T1));
        assertEquals(4, PetBalance.adultSplashMaxTargets(PetTowers.CAT_T2));
        assertEquals(6, PetBalance.adultSplashMaxTargets(PetTowers.CAT_T3));
        assertEquals(0.30, PetBalance.adultSplashDamageRatio(PetTowers.CAT_T3));
        assertEquals(1.0, config.ability(PetTowers.BIRD_T3.id(), PetBalance.KEY_HEAL_RATIO, -1));

        // A negative ability value breaks config application for every family, so keep them positive.
        for (TowerType type : PetTowers.all()) {
            config.abilities().getOrDefault(type.id(), java.util.Map.of())
                    .forEach((key, value) -> assertTrue(value >= 0.0, type.id() + "." + key));
        }
        config.abilities().getOrDefault(PetBalance.CONFIG_ID, java.util.Map.of())
                .forEach((key, value) -> assertTrue(value >= 0.0, PetBalance.CONFIG_ID + "." + key));
    }

    @Test
    void bondCapsAndUpgradeThresholdsRisePerTier() {
        for (List<TowerType> line : List.of(
                List.of(PetTowers.DOG_T1, PetTowers.DOG_T2, PetTowers.DOG_T3),
                List.of(PetTowers.CAT_T1, PetTowers.CAT_T2, PetTowers.CAT_T3),
                List.of(PetTowers.BIRD_T1, PetTowers.BIRD_T2, PetTowers.BIRD_T3))) {
            assertEquals(100.0, PetBalance.bondCap(line.get(0), null));
            assertEquals(200.0, PetBalance.bondCap(line.get(1), null));
            assertEquals(320.0, PetBalance.bondCap(line.get(2), null));
            assertEquals(70.0, PetBalance.bondToUpgrade(line.get(0)));
            assertEquals(160.0, PetBalance.bondToUpgrade(line.get(1)));
            assertEquals(0.0, PetBalance.bondToUpgrade(line.get(2)));
        }
        // The trainer raises the ceiling rather than the rate.
        assertEquals(640.0, PetBalance.bondCap(PetTowers.DOG_T3, PetTowers.TRAINER_T1));
        assertEquals(320.0, PetBalance.bondCap(PetTowers.DOG_T3, PetTowers.BUTLER_T1));
    }

    @Test
    void companionVisualsDoNotCollideWithOtherBuilders() {
        assertEquals(WolfVariants.SPOTTED, PetTowers.DOG_T1.visual().properties().get("wolf_variant"));
        assertEquals(WolfVariants.CHESTNUT, PetTowers.DOG_T2.visual().properties().get("wolf_variant"));
        assertEquals(WolfVariants.BLACK, PetTowers.DOG_T3.visual().properties().get("wolf_variant"));
        assertEquals(CatVariants.TABBY, PetTowers.CAT_T1.visual().properties().get("cat_variant"));
        assertEquals(CatVariants.CALICO, PetTowers.CAT_T2.visual().properties().get("cat_variant"));
        assertEquals(CatVariants.RAGDOLL, PetTowers.CAT_T3.visual().properties().get("cat_variant"));
        assertEquals(Parrot.Variant.BLUE, PetTowers.BIRD_T1.visual().properties().get("parrot_variant"));
        assertEquals(Parrot.Variant.YELLOW_BLUE, PetTowers.BIRD_T2.visual().properties().get("parrot_variant"));
        assertEquals(Parrot.Variant.GRAY, PetTowers.BIRD_T3.visual().properties().get("parrot_variant"));
    }

    @Test
    void descriptionsResolveEveryPlaceholder() {
        for (TowerType type : PetTowers.all()) {
            List<String> lines = kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry.describe(type)
                    .orElseThrow(() -> new AssertionError("no description registered for " + type.id()));
            assertFalse(lines.isEmpty(), type.id());
            for (String line : lines) {
                assertFalse(line.contains("{ability."), type.id() + " -> " + line);
            }
        }
    }

    /**
     * {@code defaultConfig()} hands back the bundled resource verbatim and never merges the Java
     * side, so a value that lands in only one of the two silently ships wrong. Diff them directly.
     */
    @Test
    void codeDefaultsAndBundledResourceAgreeOnEveryPetValue() {
        TowerBalanceConfig code = TowerBalanceConfig.codeDefaults();
        TowerBalanceConfig bundled = TowerBalanceConfig.defaultConfig();

        for (TowerType type : PetTowers.all()) {
            assertEquals(code.towers().get(type.id()), bundled.towers().get(type.id()), type.id());
        }
        assertEquals(petKeys(code.towers().keySet()), petKeys(bundled.towers().keySet()), "tower ids");
        assertEquals(petKeys(code.upgradeCosts().keySet()), petKeys(bundled.upgradeCosts().keySet()),
                "upgrade ids");
        for (String key : petKeys(code.upgradeCosts().keySet())) {
            assertEquals(code.upgradeCosts().get(key), bundled.upgradeCosts().get(key), key);
        }
        assertEquals(petKeys(code.abilities().keySet()), petKeys(bundled.abilities().keySet()), "ability ids");
        for (String key : petKeys(code.abilities().keySet())) {
            assertEquals(code.abilities().get(key), bundled.abilities().get(key), key);
        }
    }

    private static java.util.Set<String> petKeys(java.util.Set<String> keys) {
        return keys.stream()
                .filter(key -> key.contains("_pet_") || key.equals(PetBalance.CONFIG_ID))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    @Test
    void eachOwnerWearsItsOwnHatAndCompanionsWearNone() {
        assertEquals("hats/uniques/black_beret_hat", PetTowers.hatPath(PetTowers.BUTLER_T1));
        assertEquals("hats/uniques/black_beret_hat", PetTowers.hatPath(PetTowers.BUTLER_T2));
        assertEquals("hats/uniques/red_baseball_hat", PetTowers.hatPath(PetTowers.TRAINER_T1));
        assertEquals("hats/uniques/red_baseball_hat", PetTowers.hatPath(PetTowers.TRAINER_T2));
        assertEquals("hats/villagers/shepherd_hat", PetTowers.hatPath(PetTowers.KEEPER_T1));
        assertEquals("hats/villagers/shepherd_hat", PetTowers.hatPath(PetTowers.KEEPER_T2));
        for (TowerType type : PetTowers.all()) {
            if (PetTowers.isCompanion(type)) {
                assertNull(PetTowers.hatPath(type), type.id());
            }
        }
    }

    @Test
    void companionsArePlacedAsPupsAndGrowToFullSizeWhenReadyToUpgrade() {
        PetTower butler = new PetTower(PetTowers.BUTLER_T1, OWNER, TeamId.RED, 1, new GridPosition(0, 80, 0));
        PetTower cat = new PetTower(PetTowers.CAT_T1, OWNER, TeamId.RED, 1, new GridPosition(1, 80, 0));
        PetBondService.refresh(List.of(butler, cat));

        assertEquals(0.7, cat.renderScale(), 1e-9);

        cat.addBond(PetBalance.bondToUpgrade(PetTowers.CAT_T1));

        assertEquals(1.0, cat.renderScale(), 1e-9);

        // The final tier has nothing left to unlock, so it is always full size.
        PetTower finalTier = new PetTower(PetTowers.CAT_T3, OWNER, TeamId.RED, 1, new GridPosition(1, 80, 0));
        assertTrue(finalTier.isAdult());
        assertEquals(1.0, finalTier.renderScale(), 1e-9);
        // Owners keep whatever their tower type declares.
        assertEquals(butler.type().visual().scale(), butler.renderScale(), 1e-9);
    }

    private static void assertUpgrade(TowerType from, TowerType to, long cost) {
        assertEquals(cost, ProductionTowerCatalog.upgrade(from, to.id()).orElseThrow().mineralCost());
    }
}
