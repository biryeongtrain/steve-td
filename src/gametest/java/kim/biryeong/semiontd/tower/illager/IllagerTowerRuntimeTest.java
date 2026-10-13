package kim.biryeong.semiontd.tower.illager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.IllagerTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.illager.IllagerTower;
import kim.biryeong.semiontd.tower.illager.IllagerMarks;
import kim.biryeong.semiontd.tower.illager.IllagerRaidState;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowerCatalogs;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class IllagerTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void illagerMarkOverridesCachedTargetForNearbyIllagerTower(GameTestHelper context) {
        UUID playerId = stableUuid("illager-forced-target-owner");
        int testLaneId = 99;
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        GridPosition towerPosition = GridPosition.from(BlockPos.containing(origin));
        TowerType towerType = new TowerType("illager_forced_target", "Illager Forced Target", TowerCategory.DIRECT, 0, 50.0, 8.0, 0.0, 100, 0);
        SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        tower.configure(new IllagerTower(towerType, playerId, TeamId.RED, testLaneId, towerPosition, towerPosition), null);
        tower.setPos(origin);
        context.getLevel().addFreshEntity(tower);

        SemionMonsterEntity cachedTarget = spawnRoleMonsterEntity(
                context,
                "illager-forced-cached",
                Optional.empty(),
                TeamId.RED,
                testLaneId,
                origin.add(0.0, 0.0, 4.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        cachedTarget.setNoAi(true);
        cachedTarget.runtimeMonster().syncLaneProgress(0.9);
        context.runAfterDelay(1, () -> {
            TowerAttackMonsterGoal targetingGoal = new TowerAttackMonsterGoal(tower);
            targetingGoal.tick();
            if (!assertTrue(context, tower.currentAttackTarget() == cachedTarget, "Illager tower should cache its original target before a mark appears.")) {
                return;
            }

            SemionMonsterEntity markedTarget = spawnRoleMonsterEntity(
                    context,
                    "illager-forced-marked",
                    Optional.empty(),
                    TeamId.RED,
                    testLaneId,
                    origin.add(1.0, 0.0, 4.0),
                    100.0,
                    List.of(SummonRole.RUSH)
            );
            markedTarget.setNoAi(true);
            markedTarget.runtimeMonster().syncLaneProgress(0.1);
            IllagerMarks.apply(markedTarget.runtimeMonster(), playerId, 0.2, 100, towerPosition, 2.0);

            targetingGoal.tick();
            if (!assertTrue(context, tower.currentAttackTarget() == markedTarget, "Nearby illager towers should replace cached targets with an active forced mark.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void illagerTowerCatalogRegistersAndLinksAllFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        IllagerTowerCatalogs.register();

        if (!assertEquals(context, 3L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Illager catalog should expose three starter tower families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(IllagerTowers.T1_VINDICATOR).size(), "Vindicator starter should link to captain tank tower.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(IllagerTowers.T1_PILLAGER).size(), "Pillager starter should branch to single and splash captain towers.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(IllagerTowers.T1_VEX).size(), "Vex starter should branch to low-health and high-health witch towers.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE).orElseThrow().create(stableUuid("illager-single-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof IllagerTower, "Single captain catalog entry should create IllagerTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(IllagerTowers.T2_WITCH_LOW).orElseThrow().create(stableUuid("illager-witch-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof IllagerTower, "Witch catalog entry should create IllagerTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illagerTowerJobUsesIllagerStartersAndBranchUpgrades(GameTestHelper context) {
        UUID playerId = stableUuid("illager-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, IllagerTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos pillagerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        IllagerTowers.T1_VINDICATOR.id(),
                        IllagerTowers.T1_PILLAGER.id(),
                        IllagerTowers.T1_VEX.id()
                ),
                starterIds,
                "Illager job should expose exactly vindicator, pillager, and vex starters."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, pillagerPos, AnimalTowers.T1_PIG_TOWER.id()),
                "Illager job should reject non-illager starter placement."
        )) {
            return;
        }
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(game, playerId, pillagerPos, IllagerTowers.T1_PILLAGER.id()), "Illager job should place pillager tower.")) {
            return;
        }
        Set<String> pillagerUpgradeIds = ProductionTowerService.availableUpgrades(game, playerId, pillagerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE.id(), IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id()),
                pillagerUpgradeIds,
                "Pillager tower should branch to single and splash captain upgrades."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illagerRaidBonusesAreExposedAsTowerTimedEffects(GameTestHelper context) {
        UUID playerId = stableUuid("illager-raid-effect-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, IllagerTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        GridPosition towerPosition = new GridPosition(base.getX(), base.getY(), base.getZ());
        IllagerTower tower = new IllagerTower(
                IllagerTowers.T3_RAVAGER,
                playerId,
                TeamId.RED,
                1,
                towerPosition,
                towerPosition
        );
        lane.addTower(tower);
        if (!assertTrue(context, tower.entityId().isPresent(), "Illager tower should spawn a runtime tower entity.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Illager tower entity should exist."));
            return;
        }

        SemionPlayer player = game.players().get(playerId);
        if (player == null) {
            context.fail(Component.literal("Illager raid test player should exist."));
            return;
        }
        IllagerRaidStates.onRoundStarted(new JobContext(game, player));
        IllagerRaidState state = IllagerRaidStates.get(playerId).orElseThrow();
        state.resetForRound(4);
        state.addGauge(100, 100);

        int activatedTowers = IllagerRaidStates.playPendingActivationEffects(context.getLevel().getServer(), lane);
        if (!assertEquals(context, 1, activatedTowers, "Illager raid activation should emit VFX for each live illager tower.")) {
            return;
        }
        if (!assertTrue(context, !state.pendingActivationEffects(), "Illager raid activation effects should be consumed once.")) {
            return;
        }

        tower.tick(lane);

        if (!assertClose(context, 0.24, towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Illager raid damage bonus should be exposed as a tower timed effect.")) {
            return;
        }
        if (!assertClose(context, 0.08, towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), "Illager raid attack speed bonus should be exposed as a tower timed effect.")) {
            return;
        }
        if (!assertClose(context, 0.35, towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Illager raid damage reduction should be exposed as a tower timed effect.")) {
            return;
        }
        if (!assertEquals(context, 40, towerEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Illager raid timed effect duration should come from towerbalance.")) {
            return;
        }
        context.succeed();
    }
}
