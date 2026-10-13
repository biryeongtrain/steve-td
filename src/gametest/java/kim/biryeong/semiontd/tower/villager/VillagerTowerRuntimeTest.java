package kim.biryeong.semiontd.tower.villager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.villager.AllayTower;
import kim.biryeong.semiontd.tower.villager.AntiTankerCatTower;
import kim.biryeong.semiontd.tower.villager.LaneClearCatTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowerCatalogs;
import kim.biryeong.semiontd.tower.villager.VillagerThornTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class VillagerTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void allayTowerHealsNearbyTowersAndBlocksDuplicateHealing(GameTestHelper context) {
        UUID playerId = stableUuid("allay-heal-support-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        AllayTower allayTower = new AllayTower(
                VillagerTowers.T1_ALLAY_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ())
        );
        TestTower nearbyTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX() + 1, base.getY(), base.getZ())
        );
        TestTower farTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX() + 5, base.getY(), base.getZ())
        );
        lane.addTower(allayTower);
        if (!assertTrue(context, allayTower.entityId().isPresent(), "Allay support tower should spawn a visible tower entity.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(allayTower.entityId().getAsInt()) instanceof SemionTowerEntity allayEntity
                        && allayEntity.getPolymerEntityType(null) == net.minecraft.world.entity.EntityTypes.ALLAY,
                "Allay support tower should render through an Allay polymer entity."
        )) {
            return;
        }
        lane.addTower(nearbyTower);
        lane.addTower(farTower);
        nearbyTower.syncHealth(30.0);
        farTower.syncHealth(30.0);
        ((SemionTowerEntity) lane.arenaWorld().getEntity(nearbyTower.entityId().orElseThrow())).syncTowerState(nearbyTower);
        ((SemionTowerEntity) lane.arenaWorld().getEntity(farTower.entityId().orElseThrow())).syncTowerState(farTower);

        allayTower.tick(lane);
        if (!assertEquals(context, 45.0, nearbyTower.health(), "Allay tower should heal nearby damaged towers.")) {
            return;
        }
        if (!assertEquals(context, 30.0, farTower.health(), "Allay tower should ignore towers outside its support radius.")) {
            return;
        }
        for (int i = 0; i < VillagerTowers.T1_ALLAY_TOWER.attackIntervalTicks() + 1; i++) {
            allayTower.tick(lane);
        }
        if (!assertEquals(context, 45.0, nearbyTower.health(), "Allay tower should not re-heal the same target inside the block window.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void golemTowerSurvivalBonusIncreasesCurrentMaxHealth(GameTestHelper context) {
        UUID playerId = stableUuid("golem-health-bonus-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        VillagerThornTower tower = new VillagerThornTower(
                VillagerTowers.T2_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        lane.addTower(tower);

        lane.resetForRound();

        double expectedMaxHealth = VillagerTowers.T2_GOLEM_TOWER.maxHealth() * 1.15;
        if (!assertEquals(context, expectedMaxHealth, tower.currentMaxHealth(), "Golem survival bonus should increase current max health.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth, tower.health(), "Golem round reset should refill to current max health.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Golem tower entity should exist after reset."));
            return;
        }
        if (!assertEquals(
                context,
                expectedMaxHealth,
                towerEntity.getAttributeValue(Attributes.MAX_HEALTH),
                "Golem tower entity max health attribute should match current max health."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void golemTowerFinalDefenseSurvivalBonusHealsCurrentHealthDelta(GameTestHelper context) {
        UUID playerId = stableUuid("golem-final-defense-health-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        VillagerThornTower tower = new VillagerThornTower(
                VillagerTowers.T3_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        lane.addTower(tower);
        double initialHealth = tower.health();

        tower.moveToFinalDefense(lane, new GridPosition(base.getX() + 1, base.getY(), base.getZ()));

        double expectedMaxHealth = VillagerTowers.T3_GOLEM_TOWER.maxHealth() * 1.20;
        if (!assertEquals(context, expectedMaxHealth, tower.currentMaxHealth(), "Iron golem final-defense bonus should increase max health immediately.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth, tower.health(), "Iron golem final-defense bonus should heal the newly gained max health.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth - initialHealth, tower.health() - initialHealth, "Iron golem should gain current health equal to the max-health delta.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void ironGolemUpgradeCopiesSurvivalBonusAndHealsAboveBaseHealth(GameTestHelper context) {
        UUID playerId = stableUuid("iron-golem-upgrade-health-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        VillagerThornTower previousTower = new VillagerThornTower(
                VillagerTowers.T2_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        for (int round = 0; round < 5; round++) {
            previousTower.moveToFinalDefense(lane, previousTower.position());
        }
        VillagerThornTower upgradedTower = new VillagerThornTower(
                VillagerTowers.T3_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                previousTower.originalPosition(),
                previousTower.position()
        );

        upgradedTower.copyFrom(previousTower, VillagerTowers.T3_GOLEM_TOWER.mineralCost());

        double expectedMaxHealth = VillagerTowers.T3_GOLEM_TOWER.maxHealth() * 2.0;
        if (!assertEquals(context, expectedMaxHealth, upgradedTower.currentMaxHealth(), "Iron golem upgrade should inherit capped survival stacks.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth, upgradedTower.health(), "Iron golem upgrade should heal to its inherited current max health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void weaponSmithTowerAppliesSourcedDamageAndSpeedBuffs(GameTestHelper context) {
        UUID playerId = stableUuid("weapon-smith-support-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        AllayTower weaponSmithTower = new AllayTower(
                VillagerTowers.T2_WEAPON_SMITH_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ())
        );
        TestTower targetTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX() + 1, base.getY(), base.getZ())
        );
        lane.addTower(weaponSmithTower);
        lane.addTower(targetTower);
        SemionTowerEntity targetEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(targetTower.entityId().orElseThrow());

        weaponSmithTower.tick(lane);
        if (!assertEquals(context, 0.075, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Weapon smith should apply a sourced damage buff.")) {
            return;
        }
        if (!assertEquals(context, 0.075, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), "Weapon smith should apply a sourced attack-speed buff.")) {
            return;
        }
        for (int i = 0; i < VillagerTowers.T2_WEAPON_SMITH_TOWER.attackIntervalTicks() + 1; i++) {
            weaponSmithTower.tick(lane);
        }
        if (!assertEquals(context, 0.075, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Weapon smith should not stack the same sourced buff inside the block window.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerAntiTankerCatBonusesNonWaveAndTankTargets(GameTestHelper context) {
        AntiTankerCatTower catTower = new AntiTankerCatTower(
                VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                stableUuid("anti-tanker-cat-owner"),
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        SemionMonsterEntity wave = spawnRoleMonsterEntity(
                context,
                "cat-wave",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO,
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity rushSummon = spawnRoleMonsterEntity(
                context,
                "cat-rush",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                1,
                Vec3.ZERO.add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity tankSummon = spawnRoleMonsterEntity(
                context,
                "cat-tank",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                1,
                Vec3.ZERO.add(2.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.TANK)
        );

        if (!assertClose(context, 20.0, catTower.modifyAttackDamage(null, wave, 20.0), "Anti-tanker cat should not bonus wave monsters.")) {
            return;
        }
        if (!assertClose(context, 50.0, catTower.modifyAttackDamage(null, rushSummon, 20.0), "T2 anti-tanker cat should deal 150% bonus damage to non-wave summons.")) {
            return;
        }
        if (!assertClose(context, 50.0, catTower.modifyAttackDamage(null, tankSummon, 20.0), "T2 anti-tanker cat should use its tank-target bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerLaneClearCatExplodesWithoutChainKills(GameTestHelper context) {
        UUID playerId = stableUuid("lane-clear-cat-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        LaneClearCatTower catTower = new LaneClearCatTower(
                VillagerTowers.T2_LANE_CLEAR_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ())
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(catTower, lane.laneLayout());
        Vec3 targetPosition = Vec3.atBottomCenterOf(base);
        towerEntity.setPos(targetPosition.add(0.0, 0.0, -1.0));
        context.getLevel().addFreshEntity(towerEntity);

        SemionMonsterEntity primary = spawnRoleMonsterEntity(context, "cat-primary", Optional.empty(), TeamId.RED, 1, targetPosition, 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity nearby = spawnRoleMonsterEntity(context, "cat-nearby", Optional.empty(), TeamId.RED, 1, targetPosition.add(0.8, 0.0, 0.0), 10.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity chainCandidate = spawnRoleMonsterEntity(context, "cat-chain-candidate", Optional.empty(), TeamId.RED, 1, targetPosition.add(1.6, 0.0, 0.0), 10.0, List.of(SummonRole.RUSH));

        catTower.onKill(towerEntity, primary, 15.0);

        if (!assertTrue(context, nearby.isRemoved() || !nearby.isAlive() || nearby.getHealth() <= 0.0F, "Lane-clear cat explosion should damage and kill monsters near the primary target.")) {
            return;
        }
        if (!assertTrue(context, chainCandidate.isAlive() && chainCandidate.getHealth() > 0.0F, "Lane-clear cat explosion should not chain from explosion-killed monsters.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void deathStackTowersGainStacksFromNearbyWaveIncomeAndTowerDeaths(GameTestHelper context) {
        UUID playerId = stableUuid("death-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        Vec3 deathPosition = lane.laneLayout().positionAt(0.0);
        BlockPos towerBlock = BlockPos.containing(deathPosition.x, deathPosition.y - 1.0, deathPosition.z);
        GridPosition stackTowerPosition = GridPosition.from(towerBlock);
        AntiTankerCatTower catTower = new AntiTankerCatTower(
                VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                stackTowerPosition
        );
        lane.addTower(catTower);

        Monster waveMonster = deathStackTestMonster("death-stack-wave", Optional.empty(), TeamId.RED, 1);
        waveMonster.syncLaneProgress(0.0);
        waveMonster.syncHealth(0.0);
        lane.activeMonsters().add(waveMonster);
        lane.tick(context.getLevel().getServer());

        Monster incomeMonster = deathStackTestMonster("death-stack-income", Optional.of(TeamId.BLUE), TeamId.RED, 1);
        incomeMonster.syncLaneProgress(0.0);
        incomeMonster.syncHealth(0.0);
        lane.activeMonsters().add(incomeMonster);
        lane.tick(context.getLevel().getServer());

        ProductionTower nearbyTower = new ProductionTower(
                VillagerTowers.T1_SPLASH_TOWER,
                stableUuid("death-stack-nearby-tower"),
                TeamId.RED,
                1,
                new GridPosition(stackTowerPosition.x() + 1, stackTowerPosition.y(), stackTowerPosition.z())
        );
        lane.addTower(nearbyTower);
        lane.killTower(nearbyTower);

        if (!assertClose(context, 22.4, catTower.modifyAttackDamage(null, null, 20.0), "Three default T2 anti-tanker cat death stacks should add 2.4 attack damage.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerTowerCatalogRegistersAndLinksAllFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        VillagerTowerCatalogs.register();

        long baseStarterCount = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .filter(entry -> VillagerTowers.isBaseVillagerTower(entry.type()))
                .count();
        if (!assertEquals(context, 4L, baseStarterCount, "Villager catalog should expose four base starter tower families.")) {
            return;
        }
        long advStarterCount = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .filter(entry -> VillagerTowers.isAdvVillagerTower(entry.type()))
                .count();
        if (!assertEquals(context, 4L, advStarterCount, "Villager ADV catalog should expose four separate starter tower families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(VillagerTowers.T1_SPLASH_TOWER).size(), "Splash starter should link to librarian tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(VillagerTowers.T1_GOLEM_TOWER).size(), "Golem starter should link to llama tower.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(VillagerTowers.T1_ALLAY_TOWER).size(), "Allay starter should branch to heal and weapon-smith support towers.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(VillagerTowers.T1_CAT_TOWER).size(), "Cat starter should branch to anti-tanker and lane-clear towers.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(VillagerTowers.T2_ANTI_TANKER_CAT_TOWER).orElseThrow().create(stableUuid("cat-catalog-owner"), TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)) instanceof AntiTankerCatTower, "Anti-tanker cat catalog entry should create AntiTankerCatTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(VillagerTowers.T2_LANE_CLEAR_CAT_TOWER).orElseThrow().create(stableUuid("lane-cat-catalog-owner"), TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)) instanceof LaneClearCatTower, "Lane-clear cat catalog entry should create LaneClearCatTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(VillagerTowers.T1_ALLAY_TOWER).orElseThrow().create(stableUuid("allay-catalog-owner"), TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)) instanceof AllayTower, "Allay catalog entry should create AllayTower through the widened production factory.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerCatUpgradesCopyKillStackDamage(GameTestHelper context) {
        UUID playerId = stableUuid("cat-stack-copy-owner");
        AntiTankerCatTower t2Anti = new AntiTankerCatTower(
                VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t2Anti.onNearbyMonsterDeath(null, null, new Vec3(0.5, 1.0, 0.5));
        AntiTankerCatTower t3Anti = new AntiTankerCatTower(
                VillagerTowers.T3_ANTI_TANKER_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t3Anti.copyFrom(t2Anti, 0);

        SemionMonsterEntity rushSummon = spawnRoleMonsterEntity(
                context,
                "cat-copy-rush",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                1,
                Vec3.ZERO,
                100.0,
                List.of(SummonRole.RUSH)
        );
        if (!assertClose(context, 62.4, t3Anti.modifyAttackDamage(null, rushSummon, 20.0), "Anti-tanker cat upgrade should keep death stack count before applying T3 summon bonus.")) {
            return;
        }

        LaneClearCatTower t2LaneClear = new LaneClearCatTower(
                VillagerTowers.T2_LANE_CLEAR_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t2LaneClear.onNearbyMonsterDeath(null, null, new Vec3(0.5, 1.0, 0.5));
        LaneClearCatTower t3LaneClear = new LaneClearCatTower(
                VillagerTowers.T3_LANE_CLEAR_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t3LaneClear.copyFrom(t2LaneClear, 0);

        SemionMonsterEntity wave = spawnRoleMonsterEntity(
                context,
                "cat-copy-wave",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO.add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        if (!assertClose(context, 52.39, t3LaneClear.modifyAttackDamage(null, wave, 20.0), "Lane-clear cat upgrade should keep death stack damage before applying T3 wave bonus.")) {
            return;
        }
        context.succeed();
    }
}
