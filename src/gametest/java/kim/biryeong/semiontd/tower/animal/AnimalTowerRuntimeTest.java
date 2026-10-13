package kim.biryeong.semiontd.tower.animal;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowerCatalogs;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.animal.FoxTower;
import kim.biryeong.semiontd.tower.animal.PigTower;
import kim.biryeong.semiontd.tower.animal.RabbitTower;
import kim.biryeong.semiontd.tower.animal.WolfTower;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class AnimalTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest(maxTicks = 80)
    public void foxTowerPrioritizesLowHealthTargetInRuntimeCombat(GameTestHelper context) {
        UUID playerId = stableUuid("red-fox-tower-owner");
        int testLaneId = 102;
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType foxType = TowerBalanceRuntime.resolve(AnimalTowers.T1_FOX_TOWER);
        lane.addTower(new FoxTower(foxType, playerId, TeamId.RED, testLaneId, GridPosition.from(towerPos)));
        FoxTower foxTower = (FoxTower) lane.towers().getFirst();
        if (!assertTrue(context, foxTower.entityId().isPresent(), "Fox tower entity should exist.")) {
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(foxTower.entityId().getAsInt());
        Vec3 towerPosition = towerEntity.position();

        SemionMonsterEntity healthyClose = spawnRoleMonsterEntity(
                context,
                "fox-healthy-close",
                Optional.empty(),
                TeamId.RED,
                testLaneId,
                towerPosition.add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        SemionMonsterEntity lowHealthFar = spawnRoleMonsterEntity(
                context,
                "fox-low-health-far",
                Optional.empty(),
                TeamId.RED,
                testLaneId,
                towerPosition.add(2.0, 0.0, 0.0),
                200.0,
                List.of(SummonRole.RUSH)
        );
        healthyClose.runtimeMonster().syncLaneProgress(0.95);
        lowHealthFar.runtimeMonster().syncLaneProgress(0.10);
        healthyClose.setNoAi(true);
        lowHealthFar.setNoAi(true);
        lowHealthFar.setHealth(50.0F);
        if (!assertTrue(
                context,
                towerEntity.selectAttackTarget(List.of(healthyClose, lowHealthFar)) == lowHealthFar,
                "Fox tower policy should prefer the low-health target before combat ticks."
        )) {
            return;
        }

        context.runAfterDelay(18, () -> {
            if (!assertTrue(
                    context,
                    towerEntity.currentAttackTarget() == lowHealthFar,
                    "Fox tower should select the low-health in-range target before the healthier progress target."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lowHealthFar.getHealth() < 50.0F,
                    "Fox tower should damage the low-health execute target."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lowHealthFar.getHealth() < healthyClose.getHealth(),
                    "Low-health target should take more damage than the healthier progress target."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void foxTowerGainsKillBonusDamageAfterNearbyMonsterDeath(GameTestHelper context) {
        UUID playerId = stableUuid("red-fox-kill-bonus-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        Vec3 deathPosition = lane.laneLayout().positionAt(0.0);
        BlockPos towerPos = BlockPos.containing(deathPosition.x, deathPosition.y - 1.0, deathPosition.z);
        TowerType foxType = TowerBalanceRuntime.resolve(AnimalTowers.T1_FOX_TOWER);
        lane.addTower(new FoxTower(foxType, playerId, TeamId.RED, 1, GridPosition.from(towerPos)));
        FoxTower foxTower = (FoxTower) lane.towers().getFirst();
        if (!assertTrue(context, foxTower.entityId().isPresent(), "Fox tower entity should exist.")) {
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(foxTower.entityId().getAsInt());
        SemionMonsterEntity damageProbe = spawnRoleMonsterEntity(
                context,
                "fox-nearby-death-damage-probe",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        damageProbe.setNoAi(true);
        damageProbe.setHealth(20.0F);

        double beforeKillDamage = towerEntity.attackDamageAmount(damageProbe);
        Monster nearbyMonster = deathStackTestMonster("fox-nearby-death-target", Optional.empty(), TeamId.RED, 1);
        nearbyMonster.syncLaneProgress(0.0);
        nearbyMonster.syncHealth(0.0);
        lane.activeMonsters().add(nearbyMonster);
        lane.tick(context.getLevel().getServer());
        double afterKillDamage = towerEntity.attackDamageAmount(damageProbe);

        if (!assertTrue(
                context,
                afterKillDamage > beforeKillDamage,
                "Fox tower should gain attack damage after a monster dies nearby."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void pigTowerStacksHealthDamageReductionAndSplash(GameTestHelper context) {
        UUID playerId = stableUuid("pig-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        PigTower tower = new PigTower(
                AnimalTowers.T3_PIG_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        lane.addTower(tower);
        lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 1, base.getY(), base.getZ())));
        lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 2, base.getY(), base.getZ())));

        if (!assertClose(context, 530.0, tower.currentMaxHealth(), "T3 pig should gain max health from two same-owner pig stacks.")) {
            return;
        }
        if (!assertClose(context, 530.0, tower.health(), "T3 pig should gain current health when stacks increase.")) {
            return;
        }
        if (!assertClose(context, 45.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "T3 pig should gain damage from two pig stacks.")) {
            return;
        }
        if (!assertClose(context, 70.0, tower.modifyIncomingDamage(null, null, 100.0), "T3 pig should reduce incoming damage at max stacks.")) {
            return;
        }

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        Vec3 origin = towerEntity.position().add(1.0, 0.0, 0.0);
        SemionMonsterEntity primary = spawnRoleMonsterEntity(context, "pig-primary", Optional.empty(), TeamId.RED, 1, origin, 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity nearby = spawnRoleMonsterEntity(context, "pig-nearby", Optional.empty(), TeamId.RED, 1, origin.add(0.75, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity far = spawnRoleMonsterEntity(context, "pig-far", Optional.empty(), TeamId.RED, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));

        tower.onAttack(towerEntity, primary, 20.0, false);
        if (!assertClose(context, 90.0, nearby.runtimeMonster().health(), "T3 pig should splash half damage at max stacks.")) {
            return;
        }
        if (!assertClose(context, 100.0, far.runtimeMonster().health(), "T3 pig splash should ignore enemies outside radius.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void wolfTowerStacksDamageIntervalAndSplash(GameTestHelper context) {
        UUID playerId = stableUuid("wolf-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        WolfTower tower = new WolfTower(
                AnimalTowers.T2_WOLF_DPS_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        WolfTower t1Tower = new WolfTower(
                AnimalTowers.T1_WOLF_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ() + 1)
        );
        WolfTower t3Tower = new WolfTower(
                AnimalTowers.T3_WOLF_DPS_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 2, base.getY(), base.getZ() + 1)
        );
        lane.addTower(tower);
        lane.addTower(t1Tower);
        lane.addTower(t3Tower);
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 3, base.getY(), base.getZ() + 1)));
        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 4, base.getY(), base.getZ() + 2)));
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, stableUuid("other-wolf-owner"), TeamId.RED, 1, new GridPosition(base.getX() + 5, base.getY(), base.getZ() + 2)));

        if (!assertClose(context, 25.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "Four total same-owner wolves should stay below max stacks.")) {
            return;
        }
        if (!assertEquals(context, 16, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "Different-family and different-owner towers should not grant wolf stacks.")) {
            return;
        }
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 6, base.getY(), base.getZ() + 1)));

        if (!assertClose(context, 13.0, t1Tower.modifyAttackDamage(null, null, t1Tower.type().damage()), "T1 wolf should reach 13 damage with five total wolves.")) {
            return;
        }
        if (!assertEquals(context, 15, t1Tower.adjustAttackInterval(t1Tower.type().attackIntervalTicks()), "T1 wolf should reach a 15-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 30.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "T2 wolf should reach 30 damage with five total wolves.")) {
            return;
        }
        if (!assertEquals(context, 12, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "T2 wolf should reach a 12-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 65.0, t3Tower.modifyAttackDamage(null, null, t3Tower.type().damage()), "T3 wolf should reach 65 damage with five total wolves.")) {
            return;
        }
        if (!assertEquals(context, 10, t3Tower.adjustAttackInterval(t3Tower.type().attackIntervalTicks()), "T3 wolf should reach a 10-tick interval at max stacks.")) {
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        Vec3 origin = towerEntity.position().add(1.0, 0.0, 0.0);
        SemionMonsterEntity primary = spawnRoleMonsterEntity(context, "wolf-primary", Optional.empty(), TeamId.RED, 1, origin, 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity nearby = spawnRoleMonsterEntity(context, "wolf-nearby", Optional.empty(), TeamId.RED, 1, origin.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity far = spawnRoleMonsterEntity(context, "wolf-far", Optional.empty(), TeamId.RED, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));

        tower.onAttack(towerEntity, primary, 20.0, false);
        if (!assertClose(context, 90.0, nearby.runtimeMonster().health(), "T2 wolf should splash half damage.")) {
            return;
        }
        if (!assertClose(context, 100.0, far.runtimeMonster().health(), "T2 wolf splash should ignore enemies outside radius.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rabbitTowerStacksDamageIntervalAndExtraAttack(GameTestHelper context) {
        UUID playerId = stableUuid("rabbit-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        RabbitTower tower = new RabbitTower(
                AnimalTowers.T3_RABBIT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        RabbitTower t1Tower = new RabbitTower(
                AnimalTowers.T1_RABBIT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ() + 2)
        );
        RabbitTower t2Tower = new RabbitTower(
                AnimalTowers.T2_RABBIT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 2, base.getY(), base.getZ() + 2)
        );
        lane.addTower(tower);
        lane.addTower(t1Tower);
        lane.addTower(t2Tower);
        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 3, base.getY(), base.getZ() + 2)));
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 4, base.getY(), base.getZ() + 1)));
        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, stableUuid("other-rabbit-owner"), TeamId.RED, 1, new GridPosition(base.getX() + 5, base.getY(), base.getZ() + 1)));

        if (!assertClose(context, 47.5, tower.modifyAttackDamage(null, null, tower.type().damage()), "Four total same-owner rabbits should stay below max stacks.")) {
            return;
        }
        if (!assertEquals(context, 13, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "Different-family and different-owner towers should not grant rabbit stacks.")) {
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "rabbit-extra-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        tower.onAttack(towerEntity, target, 20.0, false);
        if (!assertClose(context, 100.0, target.runtimeMonster().health(), "Four total rabbits should not activate the max-stack extra attack.")) {
            return;
        }

        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 6, base.getY(), base.getZ() + 2)));

        if (!assertClose(context, 15.0, t1Tower.modifyAttackDamage(null, null, t1Tower.type().damage()), "T1 rabbit should reach 15 damage with five total rabbits.")) {
            return;
        }
        if (!assertEquals(context, 15, t1Tower.adjustAttackInterval(t1Tower.type().attackIntervalTicks()), "T1 rabbit should keep its 15-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 33.0, t2Tower.modifyAttackDamage(null, null, t2Tower.type().damage()), "T2 rabbit should reach 33 damage with five total rabbits.")) {
            return;
        }
        if (!assertEquals(context, 10, t2Tower.adjustAttackInterval(t2Tower.type().attackIntervalTicks()), "T2 rabbit should reach a 10-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 60.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "T3 rabbit should reach 60 damage with five total rabbits.")) {
            return;
        }
        if (!assertEquals(context, 8, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "T3 rabbit should reach an 8-tick interval at max stacks.")) {
            return;
        }
        tower.onAttack(towerEntity, target, 20.0, false);
        if (!assertClose(context, 60.0, target.runtimeMonster().health(), "T3 rabbit should deal 200% extra damage at max stacks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void animalLeaderUpgradeRequiresMaxStacksAndOneLeaderPerFamily(GameTestHelper context) {
        UUID playerId = stableUuid("animal-leader-upgrade-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AnimalTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        game.players().get(playerId).economy().addMineral(3_000);

        GridPosition pigPosition = new GridPosition(base.getX(), base.getY(), base.getZ());
        PigTower pig = new PigTower(AnimalTowers.T3_PIG_TOWER, playerId, TeamId.RED, 1, pigPosition);
        lane.addTower(pig);
        long beforeRejectedUpgrade = game.players().get(playerId).economy().mineral();
        if (!assertTrue(context, ProductionTowerService.availableUpgrades(game, playerId, pigPosition).isEmpty(), "Leader upgrade should stay hidden below max stacks.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET,
                ProductionTowerService.upgradeTower(game, playerId, pigPosition, AnimalTowers.T4_PIG_LEADER_TOWER.id()),
                "Pig leader upgrade should reject below-max stacks.")) {
            return;
        }
        if (!assertEquals(context, beforeRejectedUpgrade, game.players().get(playerId).economy().mineral(), "Rejected leader upgrade should not spend mineral.")) {
            return;
        }

        lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ())));
        lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 2, base.getY(), base.getZ())));
        if (!assertEquals(context, Set.of(AnimalTowers.T4_PIG_LEADER_TOWER.id()),
                ProductionTowerService.availableUpgrades(game, playerId, pigPosition).stream()
                        .map(option -> option.targetType().id()).collect(Collectors.toSet()),
                "Pig leader upgrade should appear at max stacks.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, playerId, pigPosition, AnimalTowers.T4_PIG_LEADER_TOWER.id()),
                "Max-stack pig should upgrade into its leader.")) {
            return;
        }

        GridPosition secondPigPosition = new GridPosition(base.getX() + 3, base.getY(), base.getZ());
        lane.addTower(new PigTower(AnimalTowers.T3_PIG_TOWER, playerId, TeamId.RED, 1, secondPigPosition));
        long beforeDuplicateLeader = game.players().get(playerId).economy().mineral();
        if (!assertEquals(context, TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET,
                ProductionTowerService.upgradeTower(game, playerId, secondPigPosition, AnimalTowers.T4_PIG_LEADER_TOWER.id()),
                "A second living pig leader should be rejected.")) {
            return;
        }
        if (!assertEquals(context, beforeDuplicateLeader, game.players().get(playerId).economy().mineral(), "Duplicate leader rejection should not spend mineral.")) {
            return;
        }

        GridPosition wolfPosition = new GridPosition(base.getX(), base.getY(), base.getZ() + 4);
        lane.addTower(new WolfTower(AnimalTowers.T3_WOLF_DPS_TOWER, playerId, TeamId.RED, 1, wolfPosition));
        for (int index = 0; index < 4; index++) {
            lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1,
                    new GridPosition(base.getX() + index + 1, base.getY(), base.getZ() + 4)));
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, playerId, wolfPosition, AnimalTowers.T4_WOLF_LEADER_TOWER.id()),
                "A wolf leader should coexist with a pig leader.")) {
            return;
        }

        GridPosition foxPosition = new GridPosition(base.getX(), base.getY(), base.getZ() + 8);
        FoxTower fox = new FoxTower(AnimalTowers.T3_FOX_TOWER, playerId, TeamId.RED, 1, foxPosition);
        lane.addTower(fox);
        for (int index = 0; index < 4; index++) {
            lane.addTower(new FoxTower(AnimalTowers.T1_FOX_TOWER, playerId, TeamId.RED, 1,
                    new GridPosition(base.getX() + index + 1, base.getY(), base.getZ() + 8)));
        }
        fox.onNearbyMonsterDeath(lane, deathStackTestMonster("leader-fox-kill", Optional.empty(), TeamId.RED, 1),
                new Vec3(foxPosition.x() + 0.5, foxPosition.y() + 1.0, foxPosition.z() + 0.5));
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, playerId, foxPosition, AnimalTowers.T4_FOX_LEADER_TOWER.id()),
                "Max-stack fox should upgrade into its leader.")) {
            return;
        }
        Tower upgradedFox = lane.towerAt(foxPosition);
        if (!assertTrue(context, upgradedFox instanceof FoxTower foxLeader
                        && foxLeader.modifyAttackDamage(null, null, foxLeader.type().damage())
                        > foxLeader.type().damage(),
                "Fox kill bonus should survive the T3-to-leader upgrade.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void pigLeaderAuraTracksRangeOwnerStacksDeathAndSale(GameTestHelper context) {
        UUID playerId = stableUuid("pig-leader-aura-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);

        PigTower leader = new PigTower(AnimalTowers.T4_PIG_LEADER_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX(), base.getY(), base.getZ()));
        PigTower recipient = new PigTower(AnimalTowers.T3_PIG_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ()));
        PigTower support = new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 2, base.getY(), base.getZ()));
        lane.addTower(leader);
        lane.addTower(recipient);
        lane.addTower(support);

        if (!assertClose(context, 609.5, recipient.currentMaxHealth(), "Pig leader should multiply the T3 max-stack health from 530 to 609.5.")) {
            return;
        }
        if (!assertClose(context, 609.5, recipient.health(), "Pig leader activation should heal by the gained max-health amount.")) {
            return;
        }
        if (!assertClose(context, 65.0, recipient.modifyIncomingDamage(null, null, 100.0), "Pig leader should raise max-stack damage reduction from 30% to 35%.")) {
            return;
        }
        lane.removeTower(support);
        if (!assertClose(context, 440.0, recipient.currentMaxHealth(), "Leader aura should deactivate when the leader loses max stacks.")) {
            return;
        }
        if (!assertClose(context, 100.0, recipient.modifyIncomingDamage(null, null, 100.0), "Max-stack and leader reductions should both deactivate after stack loss.")) {
            return;
        }
        PigTower restoredSupport = new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 2, base.getY(), base.getZ()));
        lane.addTower(restoredSupport);
        if (!assertClose(context, 609.5, recipient.currentMaxHealth(), "Leader aura should reactivate when max stacks return.")) {
            return;
        }

        PigTower otherOwner = new PigTower(AnimalTowers.T3_PIG_TOWER, stableUuid("other-pig-owner"), TeamId.RED, 1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ() + 1));
        RabbitTower otherFamily = new RabbitTower(AnimalTowers.T3_RABBIT_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ() + 2));
        PigTower outOfRange = new PigTower(AnimalTowers.T3_PIG_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 20, base.getY(), base.getZ()));
        lane.addTower(otherOwner);
        lane.addTower(otherFamily);
        lane.addTower(outOfRange);
        if (!assertTrue(context, otherOwner.currentMaxHealth() < recipient.currentMaxHealth(),
                "Leader aura should exclude other owners.")) {
            return;
        }
        if (!assertClose(context, otherFamily.type().maxHealth(), otherFamily.currentMaxHealth(),
                "Leader aura should exclude other animal families.")) {
            return;
        }
        if (!assertTrue(context, outOfRange.currentMaxHealth() < recipient.currentMaxHealth(),
                "Leader aura should exclude towers outside its radius.")) {
            return;
        }

        double activeAuraHealth = recipient.currentMaxHealth();
        lane.killTower(leader);
        recipient.tick(lane);
        if (!assertTrue(context, recipient.currentMaxHealth() < activeAuraHealth,
                "A dead leader should stop its aura on the next state refresh.")) {
            return;
        }
        PigTower replacementLeader = new PigTower(AnimalTowers.T4_PIG_LEADER_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX(), base.getY(), base.getZ() + 1));
        lane.addTower(replacementLeader);
        if (!assertClose(context, activeAuraHealth, recipient.currentMaxHealth(),
                "A new living max-stack leader should reactivate the aura.")) {
            return;
        }
        lane.removeTower(replacementLeader);
        if (!assertTrue(context, recipient.currentMaxHealth() < activeAuraHealth,
                "Selling the leader should remove its aura immediately.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void animalLeaderAurasApplyExactWolfRabbitAndFoxBonuses(GameTestHelper context) {
        UUID playerId = stableUuid("animal-leader-aura-values-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);

        WolfTower wolfLeader = new WolfTower(AnimalTowers.T4_WOLF_LEADER_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX(), base.getY(), base.getZ()));
        WolfTower wolf = new WolfTower(AnimalTowers.T3_WOLF_DPS_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ()));
        lane.addTower(wolfLeader);
        lane.addTower(wolf);
        for (int index = 0; index < 3; index++) {
            lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1,
                    new GridPosition(base.getX() + index + 2, base.getY(), base.getZ())));
        }
        if (!assertEquals(context, 9, wolf.adjustAttackInterval(wolf.type().attackIntervalTicks()), "Wolf leader should reduce the T3 max-stack interval from 10 to 9 ticks.")) {
            return;
        }
        SemionTowerEntity wolfEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(wolf.entityId().orElseThrow());
        Vec3 wolfTargetPosition = wolfEntity.position().add(1.0, 0.0, 0.0);
        SemionMonsterEntity wolfPrimary = spawnRoleMonsterEntity(context, "leader-wolf-primary", Optional.empty(), TeamId.RED, 1, wolfTargetPosition, 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity wolfNearby = spawnRoleMonsterEntity(context, "leader-wolf-nearby", Optional.empty(), TeamId.RED, 1, wolfTargetPosition.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        wolf.onAttack(wolfEntity, wolfPrimary, 20.0, false);
        if (!assertClose(context, 83.0, wolfNearby.runtimeMonster().health(), "Wolf leader should raise existing T3 splash from 75% to 85%.")) {
            return;
        }

        int rabbitZ = base.getZ() + 10;
        RabbitTower rabbitLeader = new RabbitTower(AnimalTowers.T4_RABBIT_LEADER_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX(), base.getY(), rabbitZ));
        RabbitTower rabbit = new RabbitTower(AnimalTowers.T3_RABBIT_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 1, base.getY(), rabbitZ));
        lane.addTower(rabbitLeader);
        lane.addTower(rabbit);
        for (int index = 0; index < 3; index++) {
            lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, playerId, TeamId.RED, 1,
                    new GridPosition(base.getX() + index + 2, base.getY(), rabbitZ)));
        }
        if (!assertClose(context, 64.8, rabbit.modifyAttackDamage(null, null, rabbit.type().damage()), "Rabbit leader should multiply max-stack T3 damage by 8%.")) {
            return;
        }
        if (!assertClose(context, 8.0, rabbit.adjustAttackRange(rabbit.type().range()), "Rabbit leader should raise T3 range from 7 to 8.")) {
            return;
        }

        int foxZ = base.getZ() + 20;
        FoxTower foxLeader = new FoxTower(AnimalTowers.T4_FOX_LEADER_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX(), base.getY(), foxZ));
        FoxTower fox = new FoxTower(AnimalTowers.T3_FOX_TOWER, playerId, TeamId.RED, 1,
                new GridPosition(base.getX() + 1, base.getY(), foxZ));
        lane.addTower(foxLeader);
        lane.addTower(fox);
        for (int index = 0; index < 3; index++) {
            lane.addTower(new FoxTower(AnimalTowers.T1_FOX_TOWER, playerId, TeamId.RED, 1,
                    new GridPosition(base.getX() + index + 2, base.getY(), foxZ)));
        }
        SemionMonsterEntity belowAuraThreshold = spawnSummonEntity(
                context, "leader-fox-60-percent", TeamId.BLUE, TeamId.RED, 1,
                new Vec3(base.getX() + 2.0, base.getY() + 1.0, foxZ + 1.0), 100.0, 40.0
        );
        SemionMonsterEntity aboveAuraThreshold = spawnSummonEntity(
                context, "leader-fox-62-percent", TeamId.BLUE, TeamId.RED, 1,
                new Vec3(base.getX() + 3.0, base.getY() + 1.0, foxZ + 1.0), 100.0, 38.0
        );
        if (!assertClose(context, 320.0, fox.modifyAttackDamage(null, belowAuraThreshold, 100.0), "Fox leader should raise the max-stack execute threshold from 56% to 61% and multiplier from 2.95 to 3.20.")) {
            return;
        }
        if (!assertClose(context, 100.0, fox.modifyAttackDamage(null, aboveAuraThreshold, 100.0), "Fox leader execute threshold should stop above 61%.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void animalTowerCatalogRegistersAndLinksAnimalFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        AnimalTowerCatalogs.register();

        if (!assertEquals(context, 4L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Animal catalog should expose pig, wolf, rabbit, and fox starter families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T1_PIG_TOWER).size(), "Pig starter should link to T2 pig tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T2_PIG_TOWER).size(), "T2 pig should link to T3 pig tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T1_WOLF_TOWER).size(), "Wolf starter should link to T2 wolf tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T2_WOLF_DPS_TOWER).size(), "T2 wolf should link to T3 wolf tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T1_RABBIT_TOWER).size(), "Rabbit starter should link to T2 rabbit tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T2_RABBIT_TOWER).size(), "T2 rabbit should link to T3 rabbit tower.")) {
            return;
        }
        if (!assertEquals(context, AnimalTowers.T4_PIG_LEADER_TOWER.id(), ProductionTowerCatalog.upgrade(AnimalTowers.T3_PIG_TOWER, AnimalTowers.T4_PIG_LEADER_TOWER.id()).orElseThrow().targetType().id(), "T3 pig should link only to its leader.")) {
            return;
        }
        if (!assertEquals(context, AnimalTowers.T4_WOLF_LEADER_TOWER.id(), ProductionTowerCatalog.upgrade(AnimalTowers.T3_WOLF_DPS_TOWER, AnimalTowers.T4_WOLF_LEADER_TOWER.id()).orElseThrow().targetType().id(), "T3 wolf should link only to its leader.")) {
            return;
        }
        if (!assertEquals(context, AnimalTowers.T4_RABBIT_LEADER_TOWER.id(), ProductionTowerCatalog.upgrade(AnimalTowers.T3_RABBIT_TOWER, AnimalTowers.T4_RABBIT_LEADER_TOWER.id()).orElseThrow().targetType().id(), "T3 rabbit should link only to its leader.")) {
            return;
        }
        if (!assertEquals(context, AnimalTowers.T4_FOX_LEADER_TOWER.id(), ProductionTowerCatalog.upgrade(AnimalTowers.T3_FOX_TOWER, AnimalTowers.T4_FOX_LEADER_TOWER.id()).orElseThrow().targetType().id(), "T3 fox should link only to its leader.")) {
            return;
        }
        if (!assertEquals(context, 4, ProductionTowerCatalog.entry(AnimalTowers.T4_PIG_LEADER_TOWER).orElseThrow().tier(), "Pig leader should be registered as tier 4.")) {
            return;
        }
        if (!assertEquals(context, 4, ProductionTowerCatalog.entry(AnimalTowers.T4_WOLF_LEADER_TOWER).orElseThrow().tier(), "Wolf leader should be registered as tier 4.")) {
            return;
        }
        if (!assertEquals(context, 4, ProductionTowerCatalog.entry(AnimalTowers.T4_RABBIT_LEADER_TOWER).orElseThrow().tier(), "Rabbit leader should be registered as tier 4.")) {
            return;
        }
        if (!assertEquals(context, 4, ProductionTowerCatalog.entry(AnimalTowers.T4_FOX_LEADER_TOWER).orElseThrow().tier(), "Fox leader should be registered as tier 4.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_PIG_TOWER).orElseThrow().create(stableUuid("pig-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof PigTower, "Pig catalog entry should create PigTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_WOLF_TOWER).orElseThrow().create(stableUuid("wolf-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof WolfTower, "Wolf catalog entry should create WolfTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_RABBIT_TOWER).orElseThrow().create(stableUuid("rabbit-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof RabbitTower, "Rabbit catalog entry should create RabbitTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_FOX_TOWER).orElseThrow().create(stableUuid("fox-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof FoxTower, "Fox catalog entry should create FoxTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void animalTowerJobUsesAnimalStarterAndUpgradeTree(GameTestHelper context) {
        UUID playerId = stableUuid("animal-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AnimalTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        AnimalTowers.T1_PIG_TOWER.id(),
                        AnimalTowers.T1_WOLF_TOWER.id(),
                        AnimalTowers.T1_RABBIT_TOWER.id(),
                        AnimalTowers.T1_FOX_TOWER.id()
                ),
                starterIds,
                "Animal job should expose only animal starter towers."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, towerPos, UndeadTowers.T1_ZOMBIE_TOWER.id()),
                "Animal job should reject undead starter placement."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, towerPos, VillagerTowers.T1_SPLASH_TOWER.id()),
                "Animal job should reject villager starter placement."
        )) {
            return;
        }
        TowerPlacementResult placement = ProductionTowerService.placeTower(game, playerId, towerPos, AnimalTowers.T1_RABBIT_TOWER.id());
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, placement, "Animal job should be allowed to place rabbit tower.")) {
            return;
        }
        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(AnimalTowers.T2_RABBIT_TOWER.id()),
                upgradeIds,
                "Animal rabbit starter should connect only to the rabbit upgrade."
        )) {
            return;
        }
        context.succeed();
    }
}
