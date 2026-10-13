package kim.biryeong.semiontd.tower.ocean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.job.OceanTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.ocean.OceanTower;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.ocean.OceanWaterTower;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class OceanTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void oceanWaterTowerPlacesSuppliesAndRestoresWaterloggedLight(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-water-runtime-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        List<ProductionTowerCatalog.CatalogEntry> starters = ProductionTowerService.availableTowers(game, playerId);
        long oceanStarterCount = starters.stream().filter(entry -> OceanTowers.isOceanTower(entry.type())).count();
        if (!assertEquals(context, 6L, oceanStarterCount, "Ocean job should expose all six tier-one paths.")) {
            return;
        }

        BlockPos towerPos = towerPlacementPos(lane);
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, towerPos, OceanTowers.T1_WATER.id()),
                "Ocean water tower should place on an open lane floor."
        )) {
            return;
        }
        if (!(lane.towers().getFirst() instanceof OceanWaterTower waterTower)) {
            context.fail(Component.literal("Placed ocean supply tower should use the water runtime."));
            return;
        }
        if (!(lane.arenaWorld().getEntity(waterTower.entityId().orElseThrow()) instanceof SemionTowerEntity waterTowerEntity)) {
            context.fail(Component.literal("Ocean water tower should spawn a clickable tower entity."));
            return;
        }
        if (!assertTrue(context, waterTowerEntity.isNoAi(), "Water tower entity should disable floating AI.")) {
            return;
        }
        if (!assertTrue(context, waterTowerEntity.canBreatheUnderwater(), "Water tower entity must not drown in its water block.")) {
            return;
        }
        Vec3 entityPosition = waterTowerEntity.position();

        BlockPos waterPos = OceanWaterTower.waterBlockPos(waterTower.position());
        if (!assertEquals(
                context,
                OceanWaterTower.waterMarker(),
                lane.arenaWorld().getBlockState(waterPos),
                "Water tower should occupy its tower cell with a waterlogged light block."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getFluidState(waterPos).isSource(),
                "Water tower marker should expose a real source-water fluid state."
        )) {
            return;
        }
        long mineralBeforeStackAttempt = game.players().get(playerId).economy().mineral();
        if (!assertEquals(
                context,
                TowerPlacementResult.OCCUPIED,
                ProductionTowerService.placeTower(game, playerId, waterPos, OceanTowers.T1_WATER.id()),
                "Water tower markers must not become a new floor for stacked tower placement."
        )) {
            return;
        }
        if (!assertEquals(context, 1, lane.towers().size(), "Rejected water tower stacking must not add another tower.")) {
            return;
        }
        if (!assertEquals(
                context,
                mineralBeforeStackAttempt,
                game.players().get(playerId).economy().mineral(),
                "Rejected water tower stacking must not spend diamonds."
        )) {
            return;
        }

        GridPosition combatPosition = new GridPosition(
                waterTower.position().x() + 1,
                waterTower.position().y(),
                waterTower.position().z()
        );
        OceanTower codTower = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                combatPosition
        );
        lane.addTower(codTower);
        waterTower.syncPosition(new GridPosition(
                waterTower.originalPosition().x(),
                waterTower.originalPosition().y() + 3,
                waterTower.originalPosition().z()
        ));
        lane.markWaveStarted(1);
        if (!assertEquals(
                context,
                107.0,
                codTower.water(),
                "Water supply should use the fixed water block even if its proxy entity position drifted."
        )) {
            return;
        }
        waterTower.syncPosition(waterTower.originalPosition());

        codTower.syncPosition(new GridPosition(
                waterTower.position().x() + 20,
                waterTower.position().y(),
                waterTower.position().z()
        ));
        OceanTower lateNearbyTower = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_SALMON),
                playerId,
                TeamId.RED,
                1,
                combatPosition
        );
        lane.addTower(lateNearbyTower);
        lane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 108.5, codTower.water(), "Captured towers should keep receiving water after moving out of range.")) {
            return;
        }
        if (!assertEquals(context, 100.0, lateNearbyTower.water(), "Towers entering range after the first wave starts must not receive water.")) {
            return;
        }

        lane.resetForRound();
        lane.markWaveStarted(2);
        if (!assertEquals(context, 107.0, lateNearbyTower.water(), "The next wave should capture newly placed nearby towers.")) {
            return;
        }

        BlockPos freeWaterPos = waterPos.offset(4, 0, 0);
        lane.arenaWorld().setBlock(freeWaterPos.below(), Blocks.STONE.defaultBlockState(), 3);
        lane.arenaWorld().setBlock(freeWaterPos.east().below(), Blocks.STONE.defaultBlockState(), 3);
        lane.arenaWorld().setBlock(freeWaterPos, Blocks.WATER.defaultBlockState(), 3);
        lane.arenaWorld().setBlock(freeWaterPos.east(), Blocks.AIR.defaultBlockState(), 3);
        lane.arenaWorld().scheduleTick(freeWaterPos, Fluids.WATER, 1);

        context.runAfterDelay(10, () -> {
            if (!assertEquals(context, entityPosition, waterTowerEntity.position(), "Water tower hitbox should stay fixed inside water.")) {
                return;
            }
            boolean contained = List.of(waterPos.north(), waterPos.south(), waterPos.east(), waterPos.west()).stream()
                    .allMatch(neighbor -> lane.arenaWorld().getBlockState(neighbor).isAir());
            if (!assertTrue(context, contained, "Water tower should remain contained to one block after fluid ticks.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getBlockState(freeWaterPos.east()).isAir(),
                    "Water fluid ticks should not spread inside Fantasy runtime worlds."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    TowerSellResult.SUCCESS,
                    ProductionTowerService.sellTower(game, playerId, waterTower.position()).result(),
                    "Water tower should remain a normal sellable tower."
            )) {
                return;
            }
            if (!assertTrue(context, lane.arenaWorld().getBlockState(waterPos).isAir(), "Selling should restore the original air block.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void oceanWaterTowerKeepsSupplyingAcrossGameRounds(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-water-round-transition-owner");
        SemionGame game = startedTwoPlayerGame(
                context,
                playerId,
                stableUuid("ocean-water-round-transition-opponent")
        );
        game.disableWaveSpawnsForTeam(TeamId.RED);
        game.disableWaveSpawnsForTeam(TeamId.BLUE);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        OceanWaterTower waterTower = new OceanWaterTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_WATER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPos)
        );
        OceanTower codTower = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                new GridPosition(
                        waterTower.originalPosition().x() + 1,
                        waterTower.originalPosition().y(),
                        waterTower.originalPosition().z()
                )
        );
        lane.addTower(waterTower);
        lane.addTower(codTower);

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, 107.0, codTower.water(), "Round one should capture and supply the nearby cod tower.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), 3);
        if (!assertEquals(context, 2, game.currentRound(), "The empty first wave should advance to round two.")) {
            return;
        }
        double beforeRoundTwoWave = codTower.water();
        tickGame(
                game,
                context.getLevel().getServer(),
                SemionGame.DEFAULT_PREPARE_TICKS - game.phaseTicks()
        );
        if (!assertEquals(
                context,
                beforeRoundTwoWave + 7.0,
                codTower.water(),
                "The same water tower should recapture and supply the same cod tower in round two."
        )) {
            return;
        }
        lane.moveTowersToFinalDefense();
        double waterAtFinalDefense = codTower.water();
        for (int tick = 0; tick < 25; tick++) {
            lane.tick(context.getLevel().getServer());
        }
        if (!assertEquals(
                context,
                waterAtFinalDefense,
                codTower.water(),
                "Water towers should stop supplying after moving to final defense."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanWaterTowerStackingSoftCapAndSourceRemoval(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-water-inflation-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        GridPosition targetPosition = GridPosition.from(towerPlacementPos(lane));
        OceanTower target = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                targetPosition
        );
        lane.addTower(target);

        int[][] offsets = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-2, 0}, {2, 0}
        };
        ArrayList<OceanWaterTower> sources = new ArrayList<>();
        for (int[] offset : offsets) {
            OceanWaterTower source = new OceanWaterTower(
                    TowerBalanceRuntime.resolve(OceanTowers.T1_WATER),
                    playerId,
                    TeamId.RED,
                    1,
                    new GridPosition(
                            targetPosition.x() + offset[0],
                            targetPosition.y(),
                            targetPosition.z() + offset[1]
                    )
            );
            sources.add(source);
            lane.addTower(source);
        }

        sources.forEach(source -> source.onWaveStarted(lane, 1));
        double decay = TowerBalanceRuntime.ability(OceanTower.CONFIG_ID, "waterSupplyStackDecay");
        double equivalentSources = (1.0 - Math.pow(decay, sources.size())) / (1.0 - decay);
        double expectedAfterSixSources = TowerBalanceRuntime.ability(OceanTower.CONFIG_ID, "initialWater")
                + TowerBalanceRuntime.ability(OceanTowers.T1_WATER.id(), "waveStartWater") * equivalentSources;
        if (!assertClose(
                context,
                expectedAfterSixSources,
                target.water(),
                "Six water towers should supply only 2.38336 equivalent sources."
        )) {
            return;
        }

        double stopThreshold = TowerBalanceRuntime.ability(OceanTower.CONFIG_ID, "waterSupplyStopThreshold");
        target.addWater(stopThreshold - target.water());
        sources.forEach(source -> source.onWaveStarted(lane, 2));
        if (!assertClose(context, stopThreshold, target.water(), "Water towers should stop supplying at the configured threshold.")) {
            return;
        }

        if (!assertTrue(context, target.spendWater(500.0), "Target should spend stored water below the supply threshold.")) {
            return;
        }
        for (int index = 1; index < sources.size(); index++) {
            lane.removeTower(sources.get(index));
        }
        double waterBeforeRemainingSupply = target.water();
        sources.getFirst().onWaveStarted(lane, 3);
        double softCap = TowerBalanceRuntime.ability(OceanTower.CONFIG_ID, "waterSoftCap");
        double efficiency = (stopThreshold - waterBeforeRemainingSupply) / (stopThreshold - softCap);
        double expectedAfterRemoval = waterBeforeRemainingSupply
                + TowerBalanceRuntime.ability(OceanTowers.T1_WATER.id(), "waveStartWater") * efficiency;
        if (!assertClose(
                context,
                expectedAfterRemoval,
                target.water(),
                "Removing five sources should restore the remaining source to full stacking weight."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanTankWaterTransferRespectsCooldown(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-tank-transfer-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        GridPosition tankPosition = GridPosition.from(base);
        GridPosition targetPosition = GridPosition.from(base.east());
        OceanTower tank = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_PUFFERFISH),
                playerId,
                TeamId.RED,
                1,
                tankPosition
        );
        OceanTower target = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                targetPosition
        );
        OceanTower guardian = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T2_GUARDIAN),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.west())
        );
        OceanTower elderGuardian = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T3_ELDER_GUARDIAN),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.north())
        );
        lane.addTower(tank);
        lane.addTower(target);
        lane.addTower(guardian);
        lane.addTower(elderGuardian);
        if (!(lane.arenaWorld().getEntity(tank.entityId().orElseThrow()) instanceof SemionTowerEntity tankEntity)) {
            context.fail(Component.literal("Ocean tank should spawn a tower entity."));
            return;
        }

        tank.onDamaged(tankEntity, null, 30.0, 100.0, 70.0);
        if (!assertEquals(context, 124.0, target.water(), "The first hit should transfer the tier-one cap.")) {
            return;
        }
        if (!assertEquals(context, 96.0, tank.water(), "A successful transfer should spend water once.")) {
            return;
        }
        if (!assertEquals(context, 100.0, guardian.water(), "Pufferfish water transfer should exclude guardian tanks.")) {
            return;
        }
        if (!assertEquals(context, 100.0, elderGuardian.water(), "Pufferfish water transfer should exclude elder guardian tanks.")) {
            return;
        }

        tank.onDamaged(tankEntity, null, 30.0, 70.0, 40.0);
        if (!assertEquals(context, 124.0, target.water(), "Hits during the transfer cooldown should not create water.")) {
            return;
        }
        if (!assertEquals(context, 96.0, tank.water(), "Hits during the transfer cooldown should not spend water.")) {
            return;
        }

        for (int tick = 0; tick < 99; tick++) {
            tank.tick(lane);
        }
        tank.onDamaged(tankEntity, null, 30.0, 40.0, 10.0);
        if (!assertEquals(context, 124.0, target.water(), "The ability should remain blocked before one hundred ticks pass.")) {
            return;
        }

        tank.tick(lane);
        tank.onDamaged(tankEntity, null, 10.0, 10.0, 0.0);
        if (!assertEquals(context, 124.0, target.water(), "A fatal hit should not create water.")) {
            return;
        }
        if (!assertEquals(context, 96.0, tank.water(), "A fatal hit should not spend transfer water.")) {
            return;
        }

        tank.onDamaged(tankEntity, null, 30.0, 40.0, 10.0);
        if (!assertEquals(context, 148.0, target.water(), "The ability should become ready after one hundred ticks.")) {
            return;
        }
        if (!assertEquals(context, 92.0, tank.water(), "The next ready transfer should spend water exactly once.")) {
            return;
        }

        lane.moveTowersToFinalDefense();
        for (int tick = 0; tick < 100; tick++) {
            tank.tick(lane);
        }
        tank.onDamaged(tankEntity, null, 30.0, 40.0, 10.0);
        if (!assertEquals(context, 148.0, target.water(), "Ocean tanks should stop supplying water at final defense.")) {
            return;
        }
        if (!assertEquals(context, 92.0, tank.water(), "Stopped final-defense transfers should not spend water.")) {
            return;
        }

        target.syncHealth(0.0);
        for (int tick = 0; tick < 100; tick++) {
            tank.tick(lane);
        }
        tank.onDamaged(tankEntity, null, 30.0, 40.0, 10.0);
        if (!assertEquals(context, 148.0, target.water(), "A dead tower should not receive transferred water.")) {
            return;
        }
        if (!assertEquals(context, 92.0, tank.water(), "No water should be spent when every nearby target is dead.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanSupportSpendsStoredWaterForEmpoweredBuff(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-support-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        OceanTower support = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_TROPICAL_FISH),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base)
        );
        OceanTower target = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.east())
        );
        lane.addTower(support);
        lane.addTower(target);
        if (!(lane.arenaWorld().getEntity(target.entityId().orElseThrow()) instanceof SemionTowerEntity targetEntity)) {
            context.fail(Component.literal("Ocean support target should spawn a tower entity."));
            return;
        }

        support.addWater(50.0);
        lane.markWaveStarted(1);
        support.tick(lane);
        if (!assertEquals(context, 126.0, support.water(), "Empowered support should spend three times its normal water cost.")) {
            return;
        }
        if (!assertClose(context, 0.12, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                "Empowered support should multiply its damage buff by one and a half.")) {
            return;
        }
        if (!assertClose(context, 0.15, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "Empowered support should multiply its attack-speed buff by one and a half.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanHealerConsumesWaterAndHealsNearbyLivingTowers(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-healer-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        OceanTower healer = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_SQUID),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base)
        );
        OceanTower target = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.east())
        );
        lane.addTower(healer);
        lane.addTower(target);
        if (!(lane.arenaWorld().getEntity(target.entityId().orElseThrow()) instanceof SemionTowerEntity targetEntity)) {
            context.fail(Component.literal("Ocean heal target should spawn a tower entity."));
            return;
        }

        target.syncHealth(10.0);
        targetEntity.setHealth(10.0F);
        if (!assertTrue(context, healer.spendWater(1.0), "Normal healing setup should stay below the empowered threshold.")) {
            return;
        }
        lane.markWaveStarted(1);
        healer.tick(lane);
        if (!assertEquals(context, 25.0, target.health(), "Squid should heal a nearby damaged tower by fifteen.")) {
            return;
        }
        if (!assertEquals(context, 89.0, healer.water(), "A successful squid heal should spend ten water.")) {
            return;
        }

        healer.resetForRound(lane);
        healer.addWater(11.0);
        healer.onWaveStarted(lane, 2);
        target.syncHealth(target.currentMaxHealth());
        targetEntity.setHealth((float) target.currentMaxHealth());
        healer.tick(lane);
        if (!assertEquals(context, 100.0, healer.water(), "No stored water should be spent when no nearby tower needs healing.")) {
            return;
        }

        target.syncHealth(10.0);
        targetEntity.setHealth(10.0F);
        healer.tick(lane);
        if (!assertEquals(context, 32.5, target.health(), "Empowered squid should heal by one and a half times its normal amount.")) {
            return;
        }
        if (!assertEquals(context, 70.0, healer.water(), "Empowered squid should spend three times its normal water cost.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanTierThreeTowerHitboxesDoNotOverlapAdjacentCells(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-tier-three-hitbox-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        List<TowerType> tierThreeTypes = List.of(
                OceanTowers.T3_CURRENT,
                OceanTowers.T3_ELDER_GUARDIAN,
                OceanTowers.T3_GIANT_TROPICAL_FISH,
                OceanTowers.T3_DOLPHIN,
                OceanTowers.T3_GIANT_SALMON,
                OceanTowers.T3_GIANT_COD
        );
        SemionTowerEntity previousEntity = null;

        for (int index = 0; index < tierThreeTypes.size(); index++) {
            TowerType type = tierThreeTypes.get(index);
            Tower tower = ProductionTowerCatalog.find(type.id()).orElseThrow().create(
                    playerId,
                    TeamId.RED,
                    1,
                    GridPosition.from(base.offset(index, 0, 0))
            );
            lane.addTower(tower);
            if (!(tower instanceof EntityBackedTower entityBackedTower)
                    || !(lane.arenaWorld().getEntity(entityBackedTower.entityId().orElseThrow())
                    instanceof SemionTowerEntity towerEntity)) {
                context.fail(Component.literal(type.id() + " should spawn a clickable tower entity."));
                return;
            }
            if (!assertTrue(context, towerEntity.getBbWidth() <= 1.0F, type.id() + " hitbox should fit one cell.")) {
                return;
            }
            if (previousEntity != null && !assertTrue(
                    context,
                    !previousEntity.getBoundingBox().intersects(towerEntity.getBoundingBox()),
                    type.id() + " hitbox should not cover the adjacent tower."
            )) {
                return;
            }
            previousEntity = towerEntity;
        }
        context.succeed();
    }

    @GameTest
    public void oceanWaterTowerRejectsBlockedCellBeforeChargingDiamond(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-water-blocked-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        BlockPos occupiedWaterCell = towerPos.above();
        lane.arenaWorld().setBlock(occupiedWaterCell, Blocks.STONE.defaultBlockState(), 3);
        long diamondBefore = game.players().get(playerId).economy().diamond();

        if (!assertEquals(
                context,
                TowerPlacementResult.OCCUPIED,
                ProductionTowerService.placeTower(game, playerId, towerPos, OceanTowers.T1_WATER.id()),
                "Water tower should reject a non-air tower cell."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                diamondBefore,
                game.players().get(playerId).economy().diamond(),
                "Rejected water placement must not spend diamonds."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().isEmpty(), "Rejected water placement should not add a runtime tower.")) {
            return;
        }
        context.succeed();
    }
}
