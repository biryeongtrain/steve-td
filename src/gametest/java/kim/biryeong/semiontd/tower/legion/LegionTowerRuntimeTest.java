package kim.biryeong.semiontd.tower.legion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.LegionTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.legion.BeeTower;
import kim.biryeong.semiontd.tower.legion.IllusionCloneSpawnQueue;
import kim.biryeong.semiontd.tower.legion.IllusionProfile;
import kim.biryeong.semiontd.tower.legion.IllusionRuntimeTower;
import kim.biryeong.semiontd.tower.legion.IllusionSummonerTower;
import kim.biryeong.semiontd.tower.legion.LegionGlobalIllusionTower;
import kim.biryeong.semiontd.tower.legion.LegionGoatTower;
import kim.biryeong.semiontd.tower.legion.LegionParrotTower;
import kim.biryeong.semiontd.tower.legion.LegionSlimeTower;
import kim.biryeong.semiontd.tower.legion.LegionTowerCatalogs;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.ui.SemionTowerInteractionService;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class LegionTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest(maxTicks = 100)
    public void beeTowerPoisonDealsConfigDrivenRuntimeDamage(GameTestHelper context) {
        UUID playerId = stableUuid("red-bee-tower-owner");
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, TowerBalanceConfig.TowerStats> towers = new LinkedHashMap<>(defaults.towers());
        TowerType baseBee = LegionTowers.T1_BEE_TOWER;
        towers.put(baseBee.id(), new TowerBalanceConfig.TowerStats(
                baseBee.mineralCost(),
                baseBee.maxHealth(),
                baseBee.range(),
                0.0,
                10,
                baseBee.aggroPriority()
        ));
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        abilities.put(baseBee.id(), Map.of(
                "maxSwarmStacks", 1.0,
                "poisonDamagePerStack", 5.0,
                "poisonDamagePerSwarmStack", 0.0,
                "maxPoisonStacks", 2.0,
                "poisonStacksPerSwarmStack", 0.0,
                "poisonDurationTicks", 40.0,
                "poisonTickIntervalTicks", 5.0
        ));
        TowerBalanceRuntime.apply(new TowerBalanceConfig(towers, defaults.upgradeCosts(), abilities));

        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType beeType = TowerBalanceRuntime.resolve(baseBee);
        lane.addTower(new BeeTower(beeType, playerId, TeamId.RED, 1, GridPosition.from(towerPos)));
        BeeTower beeTower = (BeeTower) lane.towers().getFirst();
        if (!assertTrue(context, beeTower.entityId().isPresent(), "Bee tower entity should exist.")) {
            TowerBalanceRuntime.apply(defaults);
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(beeTower.entityId().getAsInt());
        Vec3 towerPosition = towerEntity.position();
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "bee-poison-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerPosition.add(3.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        target.setNoAi(true);

        for (int sting = 0; sting < 3; sting++) {
            beeTower.onAttack(towerEntity, target, 0.0, false);
            for (int tick = 0; tick < 5; tick++) {
                target.aiStep();
            }
        }
        double healthBeforeSale = target.runtimeMonster().health();
        if (!assertTrue(context, lane.removeTower(beeTower), "Bee tower should be removable while poison remains active.")) {
            TowerBalanceRuntime.apply(defaults);
            return;
        }
        for (int tick = 0; tick < 5; tick++) {
            target.aiStep();
        }
        TowerBalanceRuntime.apply(defaults);
        if (!assertTrue(context, target.runtimeMonster().health() < healthBeforeSale, "Bee poison should keep ticking after its source tower is sold.")) {
            return;
        }
        if (!assertTrue(
                context,
                target.getHealth() <= 80.0F,
                "Bee tower has zero direct damage in this test, so health loss should come from config-driven poison. Actual health=" + target.getHealth()
        )) {
            return;
        }
        if (!assertClose(context, target.runtimeMonster().health(), target.getHealth(), "Bee poison should synchronize runtime and entity health.")) {
            return;
        }
        if (!assertTrue(context, target.runtimeMonster().lastHitSourceKind() == KillSourceKind.TOWER, "Bee poison should preserve tower kill attribution.")) {
            return;
        }
        if (!assertClose(context, 0.0, beeTower.roundPhysicalDamageDealt(), "Bee poison should not count as physical damage.")) {
            return;
        }
        if (!assertClose(context, 100.0 - target.runtimeMonster().health(), beeTower.roundMagicDamageDealt(), "Bee poison should count as source-tower magic damage.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 80, structure = "semion-td-gametest:combat_arena", environment = "semion-td-gametest:clone_targeting")
    public void illusionCloneAttacksSharedSourceTargetInsteadOfScanningOwnTarget(GameTestHelper context) {
        UUID playerId = stableUuid("red-clone-shared-target-owner");
        BlockPos anchor = new BlockPos(8, 0, 8);
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(anchor));
        TowerType sourceType = new TowerType("shared_target_source", "Shared Target Source", TowerCategory.DIRECT, 0, 50.0, 6.0, 0.0, 100, 0);
        TowerType cloneType = new TowerType("shared_target_clone", "Shared Target Clone", TowerCategory.DIRECT, 0, 50.0, 6.0, 10.0, 10, 0);
        SemionTowerEntity sourceEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        sourceEntity.configure(new TestTower(sourceType, playerId, TeamId.RED, 1, GridPosition.from(context.absolutePos(anchor))), null);
        sourceEntity.setNoAi(true);
        sourceEntity.setPos(origin);
        context.getLevel().addFreshEntity(sourceEntity);

        SemionTowerEntity cloneEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        cloneEntity.configure(new TestTower(cloneType, playerId, TeamId.RED, 1, GridPosition.from(context.absolutePos(anchor.east(4)))), null);
        cloneEntity.useAttackTargetFrom(sourceEntity);
        cloneEntity.setPos(origin.add(4.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(cloneEntity);

        SemionMonsterEntity sharedTarget = spawnRoleMonsterEntity(
                context,
                "shared-source-target",
                Optional.empty(),
                TeamId.RED,
                1,
                origin.add(6.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity closerOwnTarget = spawnRoleMonsterEntity(
                context,
                "closer-clone-target",
                Optional.empty(),
                TeamId.RED,
                1,
                origin.add(5.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        sharedTarget.setNoAi(true);
        closerOwnTarget.setNoAi(true);
        sourceEntity.recordCurrentAttackTarget(sharedTarget);

        context.runAfterDelay(30, () -> {
            if (!assertTrue(context, sharedTarget.getHealth() < 100.0F, "Clone should damage the source tower's shared target.")) {
                return;
            }
            if (!assertEquals(context, 100.0F, closerOwnTarget.getHealth(), "Clone should not run its own target scan while a source target is shared.")) {
                return;
            }
            if (!assertTrue(context, cloneEntity.currentAttackTarget() == sharedTarget, "Clone should expose the source-selected monster as its current target.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void illusionSummonerSpawnsConfiguredTowerEntityClonesOnWaveStarted(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_fixture", "Illusion Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(2, 0, 0.25, 0.5, 1.5, 2.0, 1.0, 99)
        );
        lane.addTower(tower);

        if (!assertEquals(context, 1, lane.towers().size(), "Illusion summoner body should be the only lane catalog tower.")) {
            return;
        }
        if (!assertTrue(context, tower.entityId().isPresent(), "Illusion summoner body entity should spawn on placement.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity,
                "Illusion summoner body should be backed by a tower entity."
        )) {
            return;
        }

        lane.markWaveStarted(1);

        if (!assertTrue(context, tower.spawnedCloneEntities().size() < 2, "Wave start should queue clones instead of spawning all clones immediately.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertTrue(context, tower.spawnedCloneEntities().size() > 0, "The first queued clone should spawn on the next global queue tick.")) {
            return;
        }
        if (!assertTrue(context, tower.spawnedCloneEntities().size() < 2, "Clone spawning should stay distributed after one tick.")) {
            return;
        }
        for (int tick = 1; tick <= TowerBalanceRuntime.illusionCloneSpawnSpreadTicks(); tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 2, tower.spawnedCloneEntities().size(), "Wave start should spawn the configured clone count within the configured spread.")) {
            return;
        }
        if (!assertEquals(context, 1, lane.towers().size(), "Illusion clones should not be inserted into the lane tower list.")) {
            return;
        }

        for (SemionTowerEntity cloneEntity : tower.spawnedCloneEntities()) {
            if (!assertTrue(context, cloneEntity.isAlive(), "Spawned clone tower entity should be alive.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    cloneEntity.runtimeTower() instanceof IllusionRuntimeTower,
                    "Spawned clone should be backed by an illusion runtime tower."
            )) {
                return;
            }
            IllusionRuntimeTower cloneTower = (IllusionRuntimeTower) cloneEntity.runtimeTower();
            if (!assertEquals(context, "illusion_fixture#illusion", cloneTower.type().id(), "Clone type should use an internal illusion id.")) {
                return;
            }
            if (!assertEquals(context, playerId, cloneTower.ownerPlayer(), "Clone should keep the source owner.")) {
                return;
            }
            if (!assertEquals(context, TeamId.RED, cloneTower.teamId(), "Clone should keep the source team.")) {
                return;
            }
            if (!assertEquals(context, 1, cloneTower.laneId(), "Clone should keep the source lane.")) {
                return;
            }
            if (!assertClose(context, 25.0, cloneTower.currentMaxHealth(), "Clone health should use the configured ratio.")) {
                return;
            }
            if (!assertClose(context, 10.0, cloneTower.type().damage(), "Clone damage should use the configured ratio.")) {
                return;
            }
            if (!assertClose(context, 15.0, cloneTower.type().range(), "Clone range should use the configured ratio.")) {
                return;
            }
            if (!assertEquals(context, 24, cloneTower.type().attackIntervalTicks(), "Clone attack interval should use the configured multiplier.")) {
                return;
            }
            if (!assertEquals(context, 106, cloneTower.aggroPriority(), "Clone aggro should use the configured priority bonus.")) {
                return;
            }
        }

        List<SemionTowerEntity> firstRoundClones = List.copyOf(tower.spawnedCloneEntities());
        lane.moveTowersToFinalDefense();
        Set<GridPosition> finalDefensePositions = new java.util.HashSet<>();
        finalDefensePositions.add(tower.position());
        for (SemionTowerEntity cloneEntity : firstRoundClones) {
            if (!assertTrue(
                    context,
                    cloneEntity.runtimeTower() instanceof IllusionRuntimeTower,
                    "Final-defense clone should still be backed by an illusion runtime tower."
            )) {
                return;
            }
            IllusionRuntimeTower cloneTower = (IllusionRuntimeTower) cloneEntity.runtimeTower();
            finalDefensePositions.add(cloneTower.position());
            if (!assertTrue(context, cloneTower.deployedAtFinalDefense(), "Wave-cleared clone should move to final defense.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.laneLayout().isInsideFinalDefenseTowerArea(cloneEntity.position()),
                    "Wave-cleared clone entity should be positioned inside the final defense tower area."
            )) {
                return;
            }
        }
        if (!assertEquals(
                context,
                firstRoundClones.size() + 1,
                finalDefensePositions.size(),
                "Source tower and final-defense clones should use distinct shared slots while capacity remains."
        )) {
            return;
        }

        lane.resetForRound();
        for (SemionTowerEntity cloneEntity : firstRoundClones) {
            if (!assertTrue(context, cloneEntity.isRemoved(), "Round reset should discard existing illusion clones.")) {
                return;
            }
        }

        int previousCloneCount = tower.spawnedCloneEntities().size();
        lane.markWaveStarted(2);
        for (int tick = 0; tick <= TowerBalanceRuntime.illusionCloneSpawnSpreadTicks(); tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        List<SemionTowerEntity> secondRoundClones = tower.spawnedCloneEntities().subList(previousCloneCount, tower.spawnedCloneEntities().size());
        if (!assertEquals(context, 2, secondRoundClones.size(), "A later wave should spawn a fresh clone set within the configured spread.")) {
            return;
        }
        if (!assertTrue(context, lane.removeTower(tower), "Removing the source tower should succeed.")) {
            return;
        }
        for (SemionTowerEntity cloneEntity : secondRoundClones) {
            if (!assertTrue(context, cloneEntity.isRemoved(), "Source removal should discard active illusion clones.")) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void illusionPendingCloneSpawnsCancelOnResetAndRemoval(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-cancel-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_cancel_fixture", "Illusion Cancel Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(10, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);

        lane.markWaveStarted(1);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "One clone should spawn before reset cancellation.")) {
            return;
        }
        SemionTowerEntity resetRoundClone = tower.spawnedCloneEntities().getFirst();
        lane.resetForRound();
        if (!assertTrue(context, resetRoundClone.isRemoved(), "Round reset should discard the already spawned clone.")) {
            return;
        }
        for (int tick = 0; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "Round reset should cancel pending illusion clones.")) {
            return;
        }

        int cloneCountAfterReset = tower.spawnedCloneEntities().size();
        lane.markWaveStarted(2);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, cloneCountAfterReset + 1, tower.spawnedCloneEntities().size(), "One clone should spawn before removal cancellation.")) {
            return;
        }
        SemionTowerEntity removalRoundClone = tower.spawnedCloneEntities().get(cloneCountAfterReset);
        if (!assertTrue(context, lane.removeTower(tower), "Removing the illusion source should succeed.")) {
            return;
        }
        if (!assertTrue(context, removalRoundClone.isRemoved(), "Source removal should discard already spawned illusion clones.")) {
            return;
        }
        for (int tick = 0; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, cloneCountAfterReset + 1, tower.spawnedCloneEntities().size(), "Source removal should cancel pending illusion clones.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gameCloseDiscardsRuntimeEntitiesAndCancelsPendingIllusionClones(GameTestHelper context) {
        IllusionCloneSpawnQueue.clear();
        UUID playerId = stableUuid("red-illusion-close-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_close_fixture", "Illusion Close Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(10, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);
        int bodyEntityId = tower.entityId().orElse(-1);
        if (!assertTrue(context, bodyEntityId >= 0, "Illusion source tower entity should spawn before close.")) {
            return;
        }

        lane.markWaveStarted(1);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "One clone should spawn before close.")) {
            return;
        }
        SemionTowerEntity spawnedClone = tower.spawnedCloneEntities().getFirst();

        game.close();
        if (!assertTrue(context, lane.towers().isEmpty(), "Closing the game should clear lane tower runtime state.")) {
            return;
        }
        if (!assertTrue(context, spawnedClone.isRemoved(), "Closing the game should discard already spawned illusion clones.")) {
            return;
        }
        if (!assertTrue(context, lane.arenaWorld().getEntity(bodyEntityId) == null || lane.arenaWorld().getEntity(bodyEntityId).isRemoved(), "Closing the game should discard the source tower entity.")) {
            return;
        }
        for (int tick = 0; tick < 40; tick++) {
            IllusionCloneSpawnQueue.tick();
        }
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "Closing the game should cancel pending illusion clone spawns.")) {
            return;
        }
        IllusionCloneSpawnQueue.clear();
        context.succeed();
    }

    @GameTest
    public void illusionCloneSpawnsAboveTenCompleteWithinConfiguredSpreadTicks(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-spread-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_spread_fixture", "Illusion Spread Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(12, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);

        lane.markWaveStarted(1);
        if (!assertEquals(context, 0, tower.spawnedCloneEntities().size(), "Clone spawning should be queued after wave start.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertTrue(context, tower.spawnedCloneEntities().size() > 0, "At least one queued clone should spawn on the first tick.")) {
            return;
        }
        if (!assertTrue(context, tower.spawnedCloneEntities().size() < 12, "Clone counts above 10 should not all spawn in one tick.")) {
            return;
        }
        for (int tick = 1; tick <= TowerBalanceRuntime.illusionCloneSpawnSpreadTicks(); tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 12, tower.spawnedCloneEntities().size(), "All queued clones should spawn within the configured spread.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illusionCloneInheritsSourceTraitEffects(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-trait-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        lane.assignTraitLoadout(new TraitLoadout(
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID,
                BuiltInTraits.FORTITUDE_ID
        ));
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_trait_fixture", "Illusion Trait Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(1, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);
        lane.markWaveStarted(1);

        SemionTowerEntity sourceEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        sourceEntity.refreshTimedEffect(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                BuiltInTraits.OPENING_SALVO_ID,
                0.15,
                100
        );
        sourceEntity.setPersistentEffect(
                TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS,
                BuiltInTraits.TRANSCENDENCE_ID,
                0.30
        );
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "One illusion clone should spawn.")) {
            return;
        }
        SemionTowerEntity cloneEntity = tower.spawnedCloneEntities().getFirst();
        for (TimedEffectType type : List.of(
                TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS,
                TimedEffectType.TOWER_TRAIT_MAX_HEALTH_BONUS,
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS
        )) {
            if (!assertEquals(
                    context,
                    sourceEntity.activeEffectMagnitude(type),
                    cloneEntity.activeEffectMagnitude(type),
                    "Illusion clone should inherit source trait effect: " + type
            )) {
                return;
            }
        }
        if (!assertEquals(
                context,
                sourceEntity.applyTraitOutgoingDamage(null, 100.0),
                cloneEntity.applyTraitOutgoingDamage(null, 100.0),
                "Illusion clone should apply inherited trait effects to actual damage."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illusionCloneQueueHonorsConfiguredMaxSpawnsPerTick(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                defaults.abilities(),
                new TowerBalanceConfig.IllusionCloneQueueConfig(1, 2)
        ));
        UUID playerId = stableUuid("red-illusion-queue-limit-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_queue_limit_fixture", "Illusion Queue Limit Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(5, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);

        lane.markWaveStarted(1);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 2, tower.spawnedCloneEntities().size(), "Global illusion queue should apply the configured per-tick spawn cap.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 4, tower.spawnedCloneEntities().size(), "Global illusion queue should continue draining capped ready spawns on later ticks.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 5, tower.spawnedCloneEntities().size(), "Global illusion queue should finish remaining capped ready spawns.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionTowerCatalogRegistersAndLinksLegionFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        LegionTowerCatalogs.register();

        if (!assertEquals(context, 6L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Legion catalog should expose chicken, slime, penguin, parrot, goat, and illusion starters.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(LegionTowers.T1_CHICKEN).size(), "Chicken starter should branch to social and DPS upgrades.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(LegionTowers.T1_SLIME_TOWER).size(), "Slime starter should link to T2 slime.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(LegionTowers.T1_PENGUIN).size(), "Penguin starter should link to T2 penguin.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(LegionTowers.T1_PARROT_TOWER).size(), "Parrot starter should link to T2 parrot.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(LegionTowers.T1_SLIME_TOWER).orElseThrow().create(stableUuid("legion-slime-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof LegionSlimeTower, "Slime catalog entry should create LegionSlimeTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(LegionTowers.T1_PARROT_TOWER).orElseThrow().create(stableUuid("legion-parrot-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof LegionParrotTower, "Parrot catalog entry should create LegionParrotTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(LegionTowers.ILLUSION_TOWER).orElseThrow().create(stableUuid("legion-illusion-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof LegionGlobalIllusionTower, "Illusion catalog entry should create LegionGlobalIllusionTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionTowerJobUsesLegionStarterAndUpgradeTree(GameTestHelper context) {
        UUID playerId = stableUuid("legion-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, LegionTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        LegionTowers.T1_CHICKEN.id(),
                        LegionTowers.T1_SLIME_TOWER.id(),
                        LegionTowers.T1_PENGUIN.id(),
                        LegionTowers.T1_PARROT_TOWER.id(),
                        LegionTowers.T1_GOAT_TOWER.id(),
                        LegionTowers.ILLUSION_TOWER.id()
                ),
                starterIds,
                "Legion job should expose all legion starters including goat and illusion tower."
        )) {
            return;
        }
        TowerPlacementResult placement = ProductionTowerService.placeTower(game, playerId, towerPos, LegionTowers.T1_CHICKEN.id());
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, placement, "Legion job should be allowed to place chicken tower.")) {
            return;
        }
        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(LegionTowers.T2_CHICKEN_TOWER.id(), LegionTowers.T2_DPS_CHICKEN_TOWER.id()),
                upgradeIds,
                "Legion chicken starter should branch to both chicken upgrades."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionSlimeCloneUsesCatalogRuntimeAndRegenerates(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID playerId = stableUuid("legion-slime-clone-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        TowerType slimeType = ProductionTowerCatalog.entry(LegionTowers.T2_SLIME_TOWER).orElseThrow().type();
        CapturingLegionSlimeTower slime = new CapturingLegionSlimeTower(
                slimeType,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(slime);
        lane.markWaveStarted(1);
        for (int tick = 0; tick <= TowerBalanceRuntime.illusionCloneSpawnSpreadTicks(); tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }

        if (!assertEquals(context, 2, slime.spawnedCloneEntities().size(), "T2 slime should spawn its configured clone count within the configured spread.")) {
            return;
        }
        SemionTowerEntity cloneEntity = slime.spawnedCloneEntities().getFirst();
        if (!assertTrue(context, cloneEntity.runtimeTower() instanceof LegionSlimeTower, "Slime clone should reuse the catalog LegionSlimeTower runtime.")) {
            return;
        }
        LegionSlimeTower cloneTower = (LegionSlimeTower) cloneEntity.runtimeTower();
        double damagedHealth = cloneTower.health() - 10.0;
        cloneTower.syncHealth(damagedHealth);
        cloneEntity.setHealth((float) damagedHealth);
        for (int tick = 0; tick < 20; tick++) {
            slime.tick(lane);
        }
        if (!assertClose(context, damagedHealth + 3.0, cloneTower.health(), "Slime clone should regenerate using configured runtime logic.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionIllusionDeathClonesParrotWithCatalogRuntimeAndStackLogic(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID playerId = stableUuid("legion-global-parrot-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos parrotPos = towerPlacementPos(lane);
        LegionParrotTower parrot = new LegionParrotTower(
                ProductionTowerCatalog.entry(LegionTowers.T2_PARROT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(parrotPos)
        );
        lane.addTower(parrot);
        CapturingGlobalIllusionTower illusion = new CapturingGlobalIllusionTower(
                ProductionTowerCatalog.entry(LegionTowers.ILLUSION_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(nearbyTowerPlacementPos(lane, parrotPos))
        );
        lane.addTower(illusion);

        illusion.syncHealth(0.0);
        illusion.notifyDeath(lane);

        if (!assertEquals(context, 0, illusion.spawnedCloneEntities().size(), "Illusion tower death should queue clone spawning.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, illusion.spawnedCloneEntities().size(), "Illusion tower death should clone the living same-owner parrot tower on global queue tick.")) {
            return;
        }
        SemionTowerEntity cloneEntity = illusion.spawnedCloneEntities().getFirst();
        if (!assertTrue(context, cloneEntity.runtimeTower() instanceof LegionParrotTower, "Illusion-created parrot clone should reuse LegionParrotTower runtime.")) {
            return;
        }
        LegionParrotTower cloneTower = (LegionParrotTower) cloneEntity.runtimeTower();
        double baseDamage = cloneTower.type().damage();
        int baseInterval = cloneTower.type().attackIntervalTicks();
        if (!assertClose(context, baseDamage, cloneTower.modifyAttackDamage(cloneEntity, null, baseDamage), "Parrot clone should start without attack stacks.")) {
            return;
        }
        cloneTower.onAttack(cloneEntity, null, baseDamage, false);
        if (!assertEquals(context, 1, cloneTower.attackStacks(), "Parrot clone should gain a stack after attacking.")) {
            return;
        }
        if (!assertClose(context, baseDamage * 1.45, cloneTower.modifyAttackDamage(cloneEntity, null, baseDamage), "T2 parrot clone should apply configured attack stack damage bonus.")) {
            return;
        }
        if (!assertEquals(context, (int) Math.ceil(baseInterval / 1.45), cloneTower.adjustAttackInterval(baseInterval), "T2 parrot clone should apply configured attack stack speed bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionGoatTowerCatalogRegistersStarterAndUpgradeTree(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        ProductionTowerCatalog.CatalogEntry t1Entry = ProductionTowerCatalog.entry(LegionTowers.T1_GOAT_TOWER).orElseThrow();
        ProductionTowerCatalog.CatalogEntry t2Entry = ProductionTowerCatalog.entry(LegionTowers.T2_STRONG_GOAT_TOWER).orElseThrow();
        ProductionTowerCatalog.CatalogEntry t3Entry = ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow();
        if (!assertTrue(context, t1Entry.starter(), "T1 goat should be a starter legion tower.")) {
            return;
        }
        if (!assertEquals(context, 2, t2Entry.tier(), "Strong goat should be registered as tier 2.")) {
            return;
        }
        if (!assertEquals(context, 3, t3Entry.tier(), "Extreme goat should be registered as tier 3.")) {
            return;
        }
        if (!assertEquals(context, 70L, t1Entry.type().mineralCost(), "T1 goat should cost 70 minerals.")) {
            return;
        }
        if (!assertEquals(context, 150L, t2Entry.type().mineralCost(), "T2 goat should cost 150 minerals.")) {
            return;
        }
        if (!assertEquals(context, 250L, t3Entry.type().mineralCost(), "T3 goat should cost 250 minerals.")) {
            return;
        }
        TowerUpgradeOption t2Upgrade = ProductionTowerCatalog.upgrade(LegionTowers.T1_GOAT_TOWER, LegionTowers.T2_STRONG_GOAT_TOWER.id()).orElseThrow();
        TowerUpgradeOption t3Upgrade = ProductionTowerCatalog.upgrade(LegionTowers.T2_STRONG_GOAT_TOWER, LegionTowers.T3_EXTREME_GOAT_TOWER.id()).orElseThrow();
        if (!assertEquals(context, 150L, t2Upgrade.mineralCost(), "T2 goat upgrade should cost 150 minerals.")) {
            return;
        }
        if (!assertEquals(context, 250L, t3Upgrade.mineralCost(), "T3 goat upgrade should cost 250 minerals.")) {
            return;
        }
        Tower created = t1Entry.create(stableUuid("legion-goat-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0));
        if (!assertTrue(context, created instanceof LegionGoatTower, "Goat catalog entry should create LegionGoatTower runtime.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionGoatBuffPulsesDuringPrepareAndAppearsOnRightClick(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, LegionTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos targetPos = towerPlacementPos(lane);
        BlockPos goatPos = nearbyTowerPlacementPos(lane, targetPos);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(game, playerId, targetPos, LegionTowers.T1_CHICKEN.id()), "Player should place a Legion target during prepare.")) {
            return;
        }
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(game, playerId, goatPos, LegionTowers.T1_GOAT_TOWER.id()), "Player should place a goat during prepare.")) {
            return;
        }

        Tower target = lane.towers().stream()
                .filter(tower -> tower.type().id().equals(LegionTowers.T1_CHICKEN.id()))
                .findFirst()
                .orElseThrow();
        LegionGoatTower goat = lane.towers().stream()
                .filter(LegionGoatTower.class::isInstance)
                .map(LegionGoatTower.class::cast)
                .findFirst()
                .orElseThrow();
        SemionTowerEntity targetEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(((EntityBackedTower) target).entityId().orElseThrow());

        game.tick(context.getLevel().getServer());
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Goat buff verification should run during the real prepare phase.")) {
            return;
        }
        if (!assertClose(context, 0.025, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat should buff a player's Legion tower during prepare.")) {
            return;
        }
        if (!assertClose(context, 0.02, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat should temporarily reduce incoming damage for a player's Legion tower during prepare.")) {
            return;
        }
        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);
        try {
            InteractionResult result = SemionTowerInteractionService.handleUse(
                    manager,
                    player,
                    context.getLevel(),
                    InteractionHand.MAIN_HAND,
                    targetEntity,
                    new EntityHitResult(targetEntity)
            );
            if (!assertEquals(context, InteractionResult.SUCCESS, result, "A real player right-click should open the buffed tower details.")) {
                return;
            }
        } finally {
            setField(manager, "activeGame", null);
            manager.shutdown();
        }

        for (int tick = 0; tick <= goat.type().attackIntervalTicks(); tick++) {
            targetEntity.aiStep();
            game.tick(context.getLevel().getServer());
        }
        int buffDurationTicks = TowerBalanceRuntime.abilityTicks(goat.type().id(), "buffDurationTicks");
        if (!assertEquals(context, buffDurationTicks, targetEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat should refresh its buff every configured pulse interval.")) {
            return;
        }
        if (!assertEquals(context, buffDurationTicks, targetEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat should refresh its damage reduction every configured pulse interval.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionGoatTowerBuffsLegionBodiesAndClonesUpToThreeStacks(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID playerId = stableUuid("legion-goat-buff-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos slimePos = towerPlacementPos(lane);
        CapturingLegionSlimeTower slime = new CapturingLegionSlimeTower(
                ProductionTowerCatalog.entry(LegionTowers.T2_SLIME_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(slimePos)
        );
        lane.addTower(slime);

        BlockPos goatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower goat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(goatPos)
        );
        lane.addTower(goat);
        BlockPos secondGoatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower secondGoat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(secondGoatPos)
        );
        lane.addTower(secondGoat);
        BlockPos thirdGoatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower thirdGoat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(thirdGoatPos)
        );
        lane.addTower(thirdGoat);
        BlockPos fourthGoatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower fourthGoat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(fourthGoatPos)
        );
        lane.addTower(fourthGoat);

        lane.markWaveStarted(1);
        for (int tick = 0; tick <= TowerBalanceRuntime.illusionCloneSpawnSpreadTicks(); tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 2, slime.spawnedCloneEntities().size(), "T2 slime should spawn clones for goat buff targeting.")) {
            return;
        }

        SemionTowerEntity bodyEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(slime.entityId().orElseThrow());
        SemionTowerEntity cloneEntity = slime.spawnedCloneEntities().getFirst();
        goat.tick(lane);
        secondGoat.tick(lane);
        thirdGoat.tick(lane);
        fourthGoat.tick(lane);
        if (!assertClose(context, 0.30, bodyEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat body damage buff should stack up to three times.")) {
            return;
        }
        if (!assertClose(context, 0.195, bodyEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat body damage reduction should stack up to three times.")) {
            return;
        }
        if (!assertClose(context, 0.30, cloneEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat clone damage buff should stack up to three times.")) {
            return;
        }
        if (!assertClose(context, 0.24, cloneEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat clone damage reduction should stack up to three times.")) {
            return;
        }

        if (!assertTrue(context, lane.killTower(goat), "Goat stack test should kill the first provider.")) {
            return;
        }
        int buffDurationTicks = TowerBalanceRuntime.abilityTicks(goat.type().id(), "buffDurationTicks");
        for (int tick = 0; tick < buffDurationTicks; tick++) {
            bodyEntity.aiStep();
        }
        for (LegionGoatTower provider : List.of(secondGoat, thirdGoat, fourthGoat)) {
            provider.resetForRound(lane);
            provider.tick(lane);
        }
        if (!assertClose(context, 0.30, bodyEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "A dead goat should not consume one of the three buff stacks.")) {
            return;
        }
        context.succeed();
    }

    private static void tickLaneWithGlobalCloneQueue(PlayerLane lane, MinecraftServer server) {
        IllusionCloneSpawnQueue.tick();
        lane.tick(server);
    }

    private static final class FixtureIllusionTower extends IllusionSummonerTower {
        private final IllusionProfile profile;
        private final List<SemionTowerEntity> spawnedCloneEntities = new ArrayList<>();

        private FixtureIllusionTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition position,
                IllusionProfile profile
        ) {
            super(type, ownerPlayer, teamId, laneId, position);
            this.profile = profile;
        }

        private List<SemionTowerEntity> spawnedCloneEntities() {
            return spawnedCloneEntities;
        }

        @Override
        protected IllusionProfile illusionProfile(PlayerLane lane) {
            return profile;
        }

        @Override
        protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, Tower cloneTower) {
            spawnedCloneEntities.add(cloneEntity);
        }
    }

    private static final class CapturingLegionSlimeTower extends LegionSlimeTower {
        private final List<SemionTowerEntity> spawnedCloneEntities = new ArrayList<>();

        private CapturingLegionSlimeTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition position
        ) {
            super(type, ownerPlayer, teamId, laneId, position);
        }

        private List<SemionTowerEntity> spawnedCloneEntities() {
            return spawnedCloneEntities;
        }

        @Override
        protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, Tower cloneTower) {
            spawnedCloneEntities.add(cloneEntity);
        }
    }

    private static final class CapturingGlobalIllusionTower extends LegionGlobalIllusionTower {
        private final List<SemionTowerEntity> spawnedCloneEntities = new ArrayList<>();

        private CapturingGlobalIllusionTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition position
        ) {
            super(type, ownerPlayer, teamId, laneId, position);
        }

        private List<SemionTowerEntity> spawnedCloneEntities() {
            return spawnedCloneEntities;
        }

        @Override
        protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, Tower cloneTower) {
            spawnedCloneEntities.add(cloneEntity);
        }
    }
}
