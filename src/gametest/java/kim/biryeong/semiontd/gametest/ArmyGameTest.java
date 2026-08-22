package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.ArmyTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.army.ArmyBalance;
import kim.biryeong.semiontd.tower.army.ArmyStates;
import kim.biryeong.semiontd.tower.army.ArmyTower;
import kim.biryeong.semiontd.tower.army.ArmyTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class ArmyGameTest {
    @GameTest
    public void armyCatalogPlacementUpgradeAndServiceSnapshotUseTheRealLane(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("army-placement-owner");
        SemionGame game = null;
        try {
            TowerBalanceRuntime.apply(defaults);
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
            game = startedArmyGame(context, owner);
            game.players().get(owner).economy().addMineral(10_000);
            PlayerLane lane = game.playerLane(owner).orElseThrow();

            require(ProductionTowerCatalog.all().stream()
                    .filter(ProductionTowerCatalog.CatalogEntry::starter).count() == 127,
                    "Built-ins must include all 127 starter entries after Developer Builder registration.");
            require(ProductionTowerService.availableTowers(game, owner).stream()
                    .filter(entry -> ArmyTowers.isArmyTower(entry.type())).count() == 3,
                    "Army must expose headquarters, guard, and combat starters.");

            BlockPos position = emptyPosition(lane, 0);
            require(ProductionTowerService.placeTower(game, owner, position, ArmyTowers.RECRUIT.id())
                            == TowerPlacementResult.SUCCESS,
                    "Army recruit placement must succeed.");
            ArmyTower recruit = (ArmyTower) lane.towerAt(GridPosition.from(position));
            lane.markWaveStarted(1);
            require(recruit.service() == 0, "Wave start must snapshot service without applying it before combat.");
            new ArmyTowerJob().onRoundEnded(new JobContext(game, game.players().get(owner)), 1);
            require(recruit.service() == 1, "A participating recruit must gain service after the round.");

            require(ProductionTowerService.upgradeTower(
                    game, owner, GridPosition.from(position), ArmyTowers.SPECIALIST.id())
                    == TowerUpgradeResult.SUCCESS, "Army recruit upgrade must succeed.");
            ArmyTower upgraded = (ArmyTower) lane.towerAt(GridPosition.from(position));
            require(upgraded.service() == 1, "Army upgrades must preserve accumulated service.");
            require(towerEntity(lane, upgraded).getItemBySlot(EquipmentSlot.MAINHAND).is(Items.CROSSBOW),
                    "Infantry must carry the shared crossbow overlay.");

            BlockPos headquartersPosition = emptyPosition(lane, 0);
            require(ProductionTowerService.placeTower(game, owner, headquartersPosition, ArmyTowers.CLERK.id())
                    == TowerPlacementResult.SUCCESS, "Army headquarters placement must succeed.");
            require(ProductionTowerService.upgradeTower(
                    game, owner, GridPosition.from(headquartersPosition), ArmyTowers.QUARTERMASTER.id())
                    == TowerUpgradeResult.SUCCESS, "Army headquarters upgrade must succeed.");
            ArmyTower headquarters = (ArmyTower) lane.towerAt(GridPosition.from(headquartersPosition));
            require(towerEntity(lane, headquarters).getItemBySlot(EquipmentSlot.MAINHAND).is(Items.COMPASS),
                    "Headquarters must carry the compass overlay.");

            BlockPos guardPosition = emptyPosition(lane, 0);
            require(ProductionTowerService.placeTower(game, owner, guardPosition, ArmyTowers.GUARD.id())
                    == TowerPlacementResult.SUCCESS, "Army guard placement must succeed.");
            require(ProductionTowerService.upgradeTower(
                    game, owner, GridPosition.from(guardPosition), ArmyTowers.MILITARY_POLICE.id())
                    == TowerUpgradeResult.SUCCESS, "Army guard upgrade must succeed.");
            ArmyTower guard = (ArmyTower) lane.towerAt(GridPosition.from(guardPosition));
            SemionTowerEntity guardEntity = towerEntity(lane, guard);
            require(guardEntity.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                            && guardEntity.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                    "Guards must carry the iron helmet and shield overlays.");

            BlockPos artilleryPosition = emptyPosition(lane, 0);
            require(ProductionTowerService.placeTower(game, owner, artilleryPosition, ArmyTowers.RECRUIT.id())
                    == TowerPlacementResult.SUCCESS, "Army artillery starter placement must succeed.");
            require(ProductionTowerService.upgradeTower(
                    game, owner, GridPosition.from(artilleryPosition), ArmyTowers.GUNNER.id())
                    == TowerUpgradeResult.SUCCESS, "Army artillery upgrade must succeed.");
            ArmyTower artillery = (ArmyTower) lane.towerAt(GridPosition.from(artilleryPosition));
            require(towerEntity(lane, artillery).getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TNT),
                    "Artillery must carry the TNT overlay.");

            upgraded.onWaveStarted(lane, 2);
            upgraded.completeServiceWave(lane);
            require(towerEntity(lane, upgraded).getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET),
                    "Corporal rank must use a chainmail helmet.");
            upgraded.onWaveStarted(lane, 3);
            upgraded.completeServiceWave(lane);
            require(towerEntity(lane, upgraded).getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                    "Sergeant rank must use an iron helmet.");
            upgraded.onWaveStarted(lane, 4);
            upgraded.completeServiceWave(lane);
            upgraded.onWaveStarted(lane, 5);
            upgraded.completeServiceWave(lane);
            require(towerEntity(lane, upgraded).getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                    "Staff sergeant rank must use a diamond helmet.");
            context.succeed();
        } finally {
            if (game != null) game.close();
            ArmyStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void thirteenthWaveDischargesOnceEvenAfterLaneFailureAndCloseClearsState(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("army-discharge-owner");
        TowerBalanceRuntime.apply(defaults);
        SemionGame game = startedArmyGame(context, owner);
        PlayerLane lane = game.playerLane(owner).orElseThrow();
        ArmyTowerJob job = new ArmyTowerJob();
        try {
            ArmyTower recruit = tower(ArmyTowers.RECRUIT, owner, emptyPosition(lane, 0));
            lane.addTower(recruit);
            for (int round = 1; round < ArmyBalance.dischargeService(); round++) {
                lane.markWaveStarted(round);
                job.onRoundEnded(new JobContext(game, game.players().get(owner)), round);
            }
            require(recruit.service() == 12 && lane.towers().contains(recruit),
                    "The recruit must fight at service 0 through 12 and remain for thirteen waves.");

            ArmyTower quartermaster = tower(ArmyTowers.QUARTERMASTER, owner, emptyPosition(lane, 1));
            lane.addTower(quartermaster);
            long beforeRefund = game.players().get(owner).economy().mineral();
            lane.markWaveStarted(ArmyBalance.dischargeService());
            job.onRoundEnded(new JobContext(game, game.players().get(owner)), ArmyBalance.dischargeService());
            require(!lane.towers().contains(recruit), "Service 13 must remove the recruit after combat.");
            long refund = game.players().get(owner).economy().mineral() - beforeRefund;
            require(refund == 47,
                    "One quartermaster must refund 47 diamonds, got " + refund + ".");
            double earnedBonus = ArmyStates.medalBonus(owner);
            require(close(earnedBonus, 0.075), "One supported discharge must award exactly 1.5 medals.");
            require(lane.removeTower(quartermaster), "Quartermaster cleanup must succeed before casualty coverage.");

            ArmyTower casualty = tower(ArmyTowers.RECRUIT, owner, emptyPosition(lane, 2));
            lane.addTower(casualty);
            for (int round = 1; round < ArmyBalance.dischargeService(); round++) {
                lane.markWaveStarted(round);
                job.onRoundEnded(new JobContext(game, game.players().get(owner)), round);
            }
            lane.markWaveStarted(ArmyBalance.dischargeService());
            require(lane.killTower(casualty), "The casualty must die before service is applied.");
            long beforeFailedLaneRefund = game.players().get(owner).economy().mineral();
            job.onRoundEnded(new JobContext(game, game.players().get(owner)), ArmyBalance.dischargeService());
            require(!lane.towers().contains(casualty),
                    "A tower that participated in the failed lane must still discharge at service 13.");
            require(game.players().get(owner).economy().mineral() - beforeFailedLaneRefund == 45,
                    "A failed-lane discharge without a quartermaster must refund 45 diamonds.");
            double failedLaneBonus = earnedBonus + ArmyBalance.medalDamageBonus();
            require(close(ArmyStates.medalBonus(owner), failedLaneBonus),
                    "A failed-lane discharge must still award exactly one medal.");
            require(!casualty.completeDischarge(lane)
                            && close(ArmyStates.medalBonus(owner), failedLaneBonus),
                    "A failed-lane discharge must remain idempotent.");

            game.close();
            game = null;
            require(ArmyStates.medalCount(owner) == 0, "Match close must clear Army runtime state after tower cleanup.");
            context.succeed();
        } finally {
            if (game != null) game.close();
            ArmyStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void medalsReachGuardsArtilleryUsesResolvedDamageOnceAndTopRankKeepsAttacking(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("army-combat-owner");
        TowerBalanceRuntime.apply(defaults);
        SemionGame game = startedArmyGame(context, owner);
        PlayerLane lane = game.playerLane(owner).orElseThrow();
        SemionMonsterEntity primary = null;
        SemionMonsterEntity secondary = null;
        try {
            ArmyStates.awardMedal(owner, 1.0);
            ArmyTower guard = tower(ArmyTowers.GUARD, owner, emptyPosition(lane, 0));
            require(close(guard.modifyAttackDamage(null, null, 100.0), 105.0),
                    "The medal bonus must reach non-ranking Army guards.");

            ArmyTower artillery = tower(ArmyTowers.GUNNER, owner, emptyPosition(lane, 1));
            lane.addTower(artillery);
            SemionTowerEntity source = towerEntity(lane, artillery);
            Vec3 impact = source.position().add(0.0, 0.0, 2.0);
            primary = spawnTarget(context, lane, impact, "army-primary");
            secondary = spawnTarget(context, lane, impact.add(0.5, 0.0, 0.0), "army-secondary");
            artillery.onAttackResolved(source, primary, 100.0, 100.0, 100.0, false);
            int secondaryEntityId = secondary.getId();
            Monster secondaryRuntime = lane.activeMonsters().stream()
                    .filter(monster -> monster.minecraftEntityId() == secondaryEntityId)
                    .findFirst().orElseThrow();
            require(close(secondaryRuntime.health(), 50.0),
                    "Artillery splash must use the already-resolved 100 damage exactly once at 50%.");

            ArmyTower senior = tower(ArmyTowers.RECRUIT, owner, emptyPosition(lane, 2));
            for (int round = 1; round <= ArmyBalance.staffSergeantService(); round++) {
                senior.onWaveStarted(lane, round);
                senior.completeServiceWave(lane);
            }
            require(close(senior.adjustAttackRange(senior.type().range()), senior.type().range()),
                    "Top-rank combat towers must retain their attack range.");
            context.succeed();
        } finally {
            if (primary != null) primary.discard();
            if (secondary != null) secondary.discard();
            game.close();
            ArmyStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    private static SemionGame startedArmyGame(GameTestHelper context, UUID owner) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        require(game.selectJob(owner, ArmyTowerJob.ID), "Army job selection must succeed.");
        require(game.start(
                context.getLevel().getServer(),
                new ParticipantSelectionPlan(
                        MatchMode.NORMAL,
                        List.of(new AssignedParticipant(owner, "army-tester", TeamId.RED, 1)),
                        java.util.Set.of(),
                        1
                )
        ), "Army test game must start.");
        return game;
    }

    private static ArmyTower tower(kim.biryeong.semiontd.tower.TowerType type, UUID owner, BlockPos position) {
        GridPosition grid = GridPosition.from(position);
        ArmyTower tower = new ArmyTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, grid);
        tower.recordPlacementEconomy(type.mineralCost(), 1);
        return tower;
    }

    private static SemionTowerEntity towerEntity(PlayerLane lane, ArmyTower tower) {
        return (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
    }

    private static BlockPos emptyPosition(PlayerLane lane, int skip) {
        var bounds = lane.laneLayout().laneArea();
        int found = 0;
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, bounds.min().getY(), z);
                if (lane.canPlaceTowerAt(candidate) && !lane.hasTowerAt(GridPosition.from(candidate))) {
                    if (found++ == skip) return candidate;
                }
            }
        }
        throw new AssertionError("No empty Army tower position was found.");
    }

    private static SemionMonsterEntity spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            Vec3 position,
            String id
    ) {
        Monster runtime = new Monster(
                id, TeamId.RED, 1, Optional.empty(), Optional.empty(), 100.0, 0.0, 1.0,
                AttackKind.MELEE, "minecraft:zombie", 0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x, position.y, position.z);
        require(context.getLevel().addFreshEntity(entity), "Army target must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return entity;
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 1.0E-6;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
