package kim.biryeong.semiontd.tower.pet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class PetGameTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("pet-gametest".getBytes(StandardCharsets.UTF_8));

    @GameTest
    public void designatedLeaderRalliesOneOfEachOtherSpeciesAndAdditionalAttacksDoNotCharge(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        PetTower owner = tower(PetTowers.KEEPER_T1, position(context, 3, 2, 4));
        PetTower dog = tower(PetTowers.DOG_T1, position(context, 4, 2, 4));
        PetTower cat = tower(PetTowers.CAT_T1, position(context, 4, 2, 5));
        PetTower secondCat = tower(PetTowers.CAT_T1, position(context, 2, 2, 4));
        PetTower bird = tower(PetTowers.BIRD_T1, position(context, 3, 2, 5));
        for (PetTower pet : List.of(owner, dog, cat, secondCat, bird)) {
            lane.addTower(pet);
        }
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, PetTower.LEADER,
                        PlayerAugmentState.Outcome.SELECTED, null, new AugmentChoice(dog.logicalId(), null, "")))));
        SemionMonsterEntity target = spawnMonster(context, lane, "pet_leader_target",
                Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 6))));
        SemionTowerEntity source = dog.runtimeEntity(lane).orElseThrow();
        double expected = cat.runtimeEntity(lane).orElseThrow().attackDamageAmount(target)
                + bird.runtimeEntity(lane).orElseThrow().attackDamageAmount(target);
        dog.onAttackResolved(source, target, 1, 1, 0, false);
        dog.onAttackResolved(source, target, 1, 1, 1, false);
        dog.onAttackResolved(source, target, 1, 1, 1, false);
        requireClose(1_000, target.runtimeMonster().health(), "Two valid hits must not rally");
        double dogDamage = source.attackDamageAmount(target);
        AugmentCombat.additionalAttack(source, target, 1);
        requireClose(1_000 - dogDamage, target.runtimeMonster().health(), "Augment attack must not charge rally");
        dog.onAttackResolved(source, target, 1, 1, 1, false);
        requireClose(1_000 - dogDamage - expected, target.runtimeMonster().health(),
                "Third valid hit adds exactly one cat and one bird native attack");
        context.succeed();
    }

    @GameTest
    public void grownUpBirdHealsSelfAndOneAllyThenFamilyHealsThreeAllies(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        PetTower owner = tower(PetTowers.KEEPER_T1, position(context, 3, 2, 4));
        PetTower bird = tower(PetTowers.BIRD_T1, position(context, 4, 2, 4));
        PetTower dog = tower(PetTowers.DOG_T1, position(context, 3, 2, 5));
        PetTower cat = tower(PetTowers.CAT_T1, position(context, 4, 2, 5));
        PetTower cat2 = tower(PetTowers.CAT_T1, position(context, 2, 2, 4));
        List<PetTower> pets = List.of(bird, dog, cat, cat2);
        lane.addTower(owner);
        pets.forEach(lane::addTower);
        lane.assignAugmentSnapshot(petAugments(PetTower.GROWN_UP));
        pets.forEach(pet -> pet.syncHealth(pet.currentMaxHealth() * .5));
        double birdBefore = bird.health();
        double totalBefore = dog.health() + cat.health() + cat2.health();
        double amount = 10 * PetBalance.healRatio(bird.type());
        bird.onAttackResolved(bird.runtimeEntity(lane).orElseThrow(), null, 10, 10, 10, false);
        requireHealthClose(birdBefore + amount, bird.health(), "Bird heals itself");
        requireHealthClose(totalBefore + amount, dog.health() + cat.health() + cat2.health(), "Exactly one ally heals");
        lane.assignAugmentSnapshot(petAugments(PetTower.GROWN_UP, PetTower.FAMILY));
        pets.forEach(pet -> pet.addBond(PetBalance.bondToUpgrade(pet.type())));
        PetBondService.refresh(lane);
        require(bird.hasFamilyYard(), "Three actual adult species must activate the family");
        pets.forEach(pet -> pet.syncHealth(pet.currentMaxHealth() * .5));
        List<Double> before = pets.stream().map(PetTower::health).toList();
        bird.onAttackResolved(bird.runtimeEntity(lane).orElseThrow(), null, 10, 10, 10, false);
        for (int i = 0; i < pets.size(); i++) {
            requireHealthClose(before.get(i) + amount, pets.get(i).health(), "Family heals self plus three allies");
        }
        context.succeed();
    }

    @GameTest
    public void earlyCatUsesNativeSplashBeforeUpgradeGrowth(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        PetTower cat = tower(PetTowers.CAT_T1, position(context, 4, 2, 4));
        lane.addTower(cat);
        lane.assignAugmentSnapshot(petAugments(PetTower.GROWN_UP));
        SemionMonsterEntity primary = spawnMonster(context, lane, "early_cat_primary",
                Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 6))));
        SemionMonsterEntity secondary = spawnMonster(context, lane, "early_cat_secondary", primary.position().add(.5, 0, 0));
        AreaEffectLaneIndex.register(lane);
        try {
            cat.onAttackResolved(cat.runtimeEntity(lane).orElseThrow(), primary, 100, 100, 100, false);
            requireClose(1_000 - 100 * PetBalance.adultSplashDamageRatio(cat.type()), secondary.runtimeMonster().health(),
                    "Young cat uses native adult splash");
            require(!cat.isAdult() && cat.bond() == 0, "Combat unlock must not grant growth");
            context.succeed();
        } finally {
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    private static AugmentSnapshot petAugments(String... cards) {
        return new AugmentSnapshot(AugmentConfig.defaults(), java.util.Arrays.stream(cards)
                .map(card -> new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, card,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }

    @GameTest
    public void companionsBondOverRoundsAndGrowUpIntoAnUpgrade(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        PetTower butler = tower(PetTowers.BUTLER_T1, position(context, 3, 2, 4));
        PetTower cat = tower(PetTowers.CAT_T1, position(context, 4, 2, 4));
        lane.addTower(butler);
        lane.addTower(cat);

        require(butler.entityId().isPresent(), "The owner must spawn a tower entity.");
        require(cat.entityId().isPresent(), "The companion must spawn a tower entity.");
        SemionTowerEntity catEntity = cat.runtimeEntity(lane).orElseThrow();
        require(butler.position().equals(cat.loyalOwnerPosition()), "The companion must imprint on the adjacent owner.");
        require(cat.hasActiveOwner() && !cat.isLost(), "A companion beside a living owner is not lost.");
        requireClose(0.7, cat.renderScale(), "A freshly placed companion is a pup");

        var upgrade = ProductionTowerCatalog.upgrade(PetTowers.CAT_T1, PetTowers.CAT_T2.id()).orElseThrow();
        require(!cat.meetsUpgradeRequirements(lane, upgrade), "A pup must not be upgradeable yet.");

        double previousBond = cat.bond();
        for (int round = 1; round <= 3; round++) {
            lane.towers().forEach(tower -> tower.onWaveStarted(lane, 1));
            require(cat.bond() > previousBond, "Each round must grant bond.");
            requireClose(cat.currentMaxHealth(), catEntity.getAttributeValue(Attributes.MAX_HEALTH),
                    "Bonded companion entity max health");
            requireHealthClose(cat.health(), catEntity.getHealth(), "Bonded companion entity health");
            double synchronizedHealth = cat.health();
            require(!cat.isDestroyed(lane), "A bonded living companion must remain alive.");
            requireHealthClose(synchronizedHealth, cat.health(), "Entity health must not erase the bond health gain");
            previousBond = cat.bond();
        }

        require(cat.isAdult(), "Three butler rounds must raise the cat to adult.");
        requireClose(1.0, cat.renderScale(), "An adult companion renders at full size");
        require(cat.meetsUpgradeRequirements(lane, upgrade), "Growing up must unlock the upgrade.");
        context.succeed();
    }

    @GameTest
    public void parrotHealsTheMostHurtCompanionInItsOwnYard(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        PetTower keeper = tower(PetTowers.KEEPER_T1, position(context, 3, 2, 4));
        PetTower bird = tower(PetTowers.BIRD_T1, position(context, 4, 2, 4));
        PetTower dog = tower(PetTowers.DOG_T1, position(context, 3, 2, 5));
        lane.addTower(keeper);
        lane.addTower(bird);
        lane.addTower(dog);
        PetBondService.refresh(lane);

        double wounded = 40.0;
        dog.syncHealth(wounded);
        require(dog.health() < dog.currentMaxHealth(), "The dog must start wounded.");

        // 100 damage dealt at a 50% heal ratio should return 50 health to the yard's weakest pet.
        bird.onAttackResolved(null, null, 100.0, 100.0, 100.0, false);

        require(dog.health() > wounded, "The parrot must heal its wounded yardmate, got " + dog.health());
        requireClose(wounded + 100.0 * PetBalance.healRatio(PetTowers.BIRD_T1), dog.health(), "Healed amount");
        context.succeed();
    }

    @GameTest
    public void losingTheOwnerCutsOutputUntilItRevives(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        PetTower butler = tower(PetTowers.BUTLER_T1, position(context, 3, 2, 4));
        PetTower cat = tower(PetTowers.CAT_T1, position(context, 4, 2, 4));
        lane.addTower(butler);
        lane.addTower(cat);
        cat.addBond(50.0);
        double bondedMultiplier = cat.companionDamageMultiplier();

        butler.syncHealth(0.0);
        PetBondService.refresh(lane);

        require(cat.isLost(), "A companion whose owner is down must be lost.");
        requireClose(bondedMultiplier * PetBalance.lostPetMultiplier(), cat.companionDamageMultiplier(),
                "Lost output");
        require(butler.position().equals(cat.loyalOwnerPosition()),
                "Loyalty must survive the owner going down, since owners revive next round.");

        double bondBeforeLostRound = cat.bond();
        lane.towers().forEach(tower -> tower.onWaveStarted(lane, 2));
        requireClose(bondBeforeLostRound, cat.bond(), "A lost companion earns no bond");

        // Owners come back next round; the bond that was already earned is untouched.
        butler.syncHealth(butler.currentMaxHealth());
        PetBondService.refresh(lane);

        require(!cat.isLost(), "Reviving the owner must restore the bond.");
        requireClose(bondedMultiplier, cat.companionDamageMultiplier(), "Restored output");
        context.succeed();
    }

    @GameTest
    public void yardSurvivesTheTripToFinalDefenseAndComesBackOnReset(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        PetTower keeper = tower(PetTowers.KEEPER_T1, position(context, 3, 2, 4));
        PetTower dogA = tower(PetTowers.DOG_T1, position(context, 4, 2, 4));
        PetTower dogB = tower(PetTowers.DOG_T1, position(context, 4, 2, 5));
        lane.addTower(keeper);
        lane.addTower(dogA);
        lane.addTower(dogB);
        PetBondService.refresh(lane);
        SemionTowerEntity dogEntity = dogA.runtimeEntity(lane).orElseThrow();
        requireClose(dogA.maxHealth() * 1.08, dogA.currentMaxHealth(), "Pack maximum health");
        requireClose(dogA.currentMaxHealth(), dogEntity.getAttributeValue(Attributes.MAX_HEALTH),
                "Pack entity maximum health");
        requireHealthClose(dogA.health(), dogEntity.getHealth(), "Pack entity current health");
        dogA.addBond(60.0);

        require(!dogA.isLost(), "The dog starts bonded.");
        require(dogA.packSize() == 2, "The two dogs start as one pack.");
        double bondedMultiplier = dogA.companionDamageMultiplier();

        // Final defense drops every tower onto shared slots, so grid adjacency no longer holds.
        lane.moveTowersToFinalDefense();
        PetBondService.refresh(lane);

        require(dogA.deployedAtFinalDefense(), "The dog must have been relocated.");
        require(!dogA.isLost(), "Relocation must not strand the companion as lost.");
        require(dogA.packSize() == 2, "The pack it earned must hold through final defense.");
        requireClose(bondedMultiplier, dogA.companionDamageMultiplier(), "Output at final defense");
        requireClose(60.0, dogA.bond(), "Bond at final defense");

        // resetForRound puts everyone back on their own tile and the yard is derived again.
        lane.towers().forEach(tower -> tower.resetForRound(lane));
        PetBondService.refresh(lane);

        require(!dogA.deployedAtFinalDefense(), "The reset must bring the tower home.");
        require(!dogA.isLost(), "And the yard must resolve normally again.");
        require(keeper.position().equals(dogA.loyalOwnerPosition()), "Loyalty points at the owner again.");
        require(dogA.packSize() == 2, "The pack re-forms on the original tiles.");
        requireClose(60.0, dogA.bond(), "Bond survives the whole round trip");
        context.succeed();
    }

    @GameTest
    public void adultCatsSplashAtTheirTierRadiusAndTargetCap(GameTestHelper context) {
        reloadBalance();
        PlayerLane lane = testLane(context);
        AreaEffectLaneIndex.register(lane);
        try {
            assertAdultCatSplash(context, lane, PetTowers.CAT_T1, 1.5, 2, 3);
            assertAdultCatSplash(context, lane, PetTowers.CAT_T2, 2.0, 4, 7);
            assertAdultCatSplash(context, lane, PetTowers.CAT_T3, 2.5, 6, 11);
            context.succeed();
        } finally {
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    private static void reloadBalance() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(config);
        ProductionTowerCatalogs.reloadBuiltIns(config);
    }

    private static PetTower tower(TowerType type, GridPosition position) {
        return new PetTower(TowerBalanceRuntime.resolve(type), OWNER, TeamId.RED, 1, position, position);
    }

    private static void assertAdultCatSplash(
            GameTestHelper context,
            PlayerLane lane,
            TowerType type,
            double radius,
            int maxTargets,
            int z
    ) {
        PetTower cat = tower(type, position(context, 1, 2, z));
        lane.addTower(cat);
        SemionTowerEntity source = cat.runtimeEntity(lane).orElseThrow();
        Vec3 center = Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, z)));
        SemionMonsterEntity primary = spawnMonster(context, lane, type.id() + "-primary", center);
        List<SemionMonsterEntity> nearby = new ArrayList<>();
        for (int index = 0; index <= maxTargets; index++) {
            double offset = radius * (index + 1) / (maxTargets + 2);
            nearby.add(spawnMonster(context, lane, type.id() + "-nearby-" + index,
                    center.add(offset, 0.0, 0.0)));
        }
        SemionMonsterEntity outside = spawnMonster(
                context, lane, type.id() + "-outside", center.add(radius + 0.5, 0.0, 0.0));

        if (!cat.isAdult()) {
            cat.onAttackResolved(source, primary, 100.0, 100.0, 100.0, false);
            require(nearby.stream().allMatch(target -> Math.abs(target.runtimeMonster().health() - 1_000.0) < 1.0E-6),
                    type.id() + " must not splash before adulthood.");
            cat.addBond(PetBalance.bondToUpgrade(type));
        }

        require(cat.isAdult(), type.id() + " must receive its adult splash.");
        cat.onAttackResolved(source, primary, 100.0, 100.0, 100.0, false);

        List<SemionMonsterEntity> damaged = nearby.stream()
                .filter(target -> target.runtimeMonster().health() < 1_000.0)
                .toList();
        require(damaged.size() == maxTargets,
                type.id() + " must splash exactly " + maxTargets + " additional targets, got " + damaged.size());
        for (SemionMonsterEntity target : damaged) {
            requireClose(970.0, target.runtimeMonster().health(), type.id() + " splash damage");
        }
        requireClose(1_000.0, primary.runtimeMonster().health(), type.id() + " primary exclusion");
        requireClose(1_000.0, outside.runtimeMonster().health(), type.id() + " splash radius");
    }

    private static SemionMonsterEntity spawnMonster(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            Vec3 position
    ) {
        Monster monster = new Monster(id, TeamId.RED, 1, Optional.empty(), Optional.empty(),
                1_000.0, 0.0, 10.0, AttackKind.MELEE, "minecraft:zombie", 0L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "Monster must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static PlayerLane testLane(GameTestHelper context) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(10, 5, 14));
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 13)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn,
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 7)))), boss,
                BlockBounds.of(min, max), List.of(position(context, 8, 2, 11)));
        return new PlayerLane(TeamId.RED, 1, OWNER, context.getLevel(), layout);
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1.0E-6, message + ": expected " + expected + ", got " + actual);
    }

    private static void requireHealthClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 0.01, message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
