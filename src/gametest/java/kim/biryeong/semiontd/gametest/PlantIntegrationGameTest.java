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
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
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
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.PlantTowerJob;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.plant.PlantCombatTower;
import kim.biryeong.semiontd.tower.plant.PlantMineTower;
import kim.biryeong.semiontd.tower.plant.PlantSoil;
import kim.biryeong.semiontd.tower.plant.PlantSoilEnvironment;
import kim.biryeong.semiontd.tower.plant.PlantSoilStates;
import kim.biryeong.semiontd.tower.plant.PlantTerraformTower;
import kim.biryeong.semiontd.tower.plant.PlantTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class PlantIntegrationGameTest {
    @GameTest
    public void tallPlantVisualKeepsAOneBlockInteractionHitbox(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        PlantCombatTower tower = new PlantCombatTower(
                TowerBalanceRuntime.resolve(PlantTowers.T3_DESERT_TOWER),
                stableUuid("plant-tall-hitbox"),
                TeamId.RED,
                1,
                position(context, 3, 1, 3)
        );
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);

        require(entity.getBbWidth() <= 1.0F && entity.getBbHeight() <= 1.0F,
                "Tall block-display plants must not overlap adjacent cells or suffocate in final defense.");
        context.succeed();
    }

    @GameTest
    public void pitcherSnareIncludesTheDirectTargetAndUsesSharedLobVfx(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("plant-pitcher-snare");
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, owner);
        group.addLane(lane);
        try {
            TowerBalanceRuntime.apply(defaults);
            fillFloor(context);
            PlantTerraformTower terraformer = new PlantTerraformTower(
                    TowerBalanceRuntime.resolve(PlantTowers.T1_SPRUCE_SEED_TOWER),
                    owner, TeamId.RED, 1, position(context, 3, 1, 3));
            PlantCombatTower pitcher = new PlantCombatTower(
                    TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_PITCHER_TOWER),
                    owner, TeamId.RED, 1, position(context, 4, 1, 3));
            lane.addTower(terraformer);
            lane.addTower(pitcher);
            SemionTowerEntity source = (SemionTowerEntity) context.getLevel()
                    .getEntity(pitcher.entityId().orElseThrow());
            Monster direct = spawnMonster(context, lane, "plant-pitcher-direct", position(context, 6, 1, 3));
            Monster nearby = spawnMonster(context, lane, "plant-pitcher-nearby", position(context, 6, 1, 4));

            pitcher.onAttackResolved(source, entity(context, direct), 48.0, 48.0, 48.0, false);

            requireClose(0.7, entity(context, direct).activeTimedEffectMagnitude(
                    TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION), "The direct target must be snared.");
            requireClose(0.7, entity(context, nearby).activeTimedEffectMagnitude(
                    TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION), "Nearby splash targets must still be snared.");
            requireClose(1_000.0, direct.health(), "The direct target must not take duplicate splash damage.");
            require(nearby.health() < 1_000.0, "A nearby target must take splash damage.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Plant pitcher regression failed: " + failure.getMessage()));
        } finally {
            group.closeRuntime();
            PlantSoilStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 30)
    public void podzolGrowthShareBuffsTheWholeLane(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("plant-podzol-share");
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, owner);
        group.addLane(lane);
        try {
            TowerBalanceRuntime.apply(defaults);
            fillFloor(context);
            PlantTerraformTower terraformer = new PlantTerraformTower(
                    TowerBalanceRuntime.resolve(PlantTowers.T1_SPRUCE_SEED_TOWER),
                    owner, TeamId.RED, 1, position(context, 3, 1, 3));
            PlantCombatTower pitcher = new PlantCombatTower(
                    TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_PITCHER_TOWER),
                    owner, TeamId.RED, 1, position(context, 4, 1, 3));
            lane.addTower(terraformer);
            lane.addTower(pitcher);
            pitcher.resetForRound(lane);
            double expected = pitcher.sharedDamageGrowthBonus();
            require(expected > 0.0, "A surviving podzol tower must contribute shared damage growth.");
            int interval = TowerBalanceRuntime.abilityTicks(
                    PlantTowers.GLOBAL_CONFIG_ID, "environmentTickIntervalTicks", 20);
            int delay = (int) ((interval - context.getLevel().getGameTime() % interval) % interval);

            context.runAfterDelay(delay, () -> {
                try {
                    PlantSoilEnvironment.tick(lane);
                    SemionTowerEntity terraformerEntity = (SemionTowerEntity) context.getLevel()
                            .getEntity(terraformer.entityId().orElseThrow());
                    SemionTowerEntity pitcherEntity = (SemionTowerEntity) context.getLevel()
                            .getEntity(pitcher.entityId().orElseThrow());
                    requireClose(expected, terraformerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                            "Podzol growth must reach non-combat towers across the lane.");
                    requireClose(expected, pitcherEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                            "Podzol growth must also reach its source tower.");
                    context.succeed();
                } catch (RuntimeException | Error failure) {
                    failure.printStackTrace();
                    context.fail(Component.literal("Plant podzol share failed: " + failure.getMessage()));
                } finally {
                    group.closeRuntime();
                    PlantSoilStates.clear(owner);
                    TowerBalanceRuntime.apply(defaults);
                }
            });
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            group.closeRuntime();
            PlantSoilStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
            context.fail(Component.literal("Plant podzol share setup failed: " + failure.getMessage()));
        }
    }

    @GameTest
    public void plantSoilUpgradeSaleAndWaveSettlementUseTheRealLane(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("plant-lifecycle-owner");
        SemionGame game = null;
        try {
            TowerBalanceRuntime.apply(defaults);
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
            game = startedPlantGame(context, owner);
            game.players().get(owner).economy().addMineral(1_000);
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            BlockPos terraformerPos = BlockPos.containing(lane.laneLayout().positionAt(0.35));
            var originalFloor = context.getLevel().getBlockState(terraformerPos);

            require(ProductionTowerService.placeTower(
                    game, owner, terraformerPos, PlantTowers.T1_OAK_SEED_TOWER.id()) == TowerPlacementResult.SUCCESS,
                    "Meadow terraformer placement must succeed."
            );
            int initialTiles = PlantSoilStates.count(owner, PlantSoil.MEADOW);
            require(initialTiles > 0, "T1 terraformer must claim meadow tiles.");

            require(ProductionTowerService.upgradeTower(
                    game, owner, GridPosition.from(terraformerPos), PlantTowers.T2_OAK_SEED_TOWER.id())
                    == TowerUpgradeResult.SUCCESS, "Terraformer upgrade must succeed."
            );
            require(PlantSoilStates.count(owner, PlantSoil.MEADOW) > initialTiles,
                    "T2 terraformer must expand the claimed soil.");

            BlockPos meadowPos = claimedEmptyPosition(lane, owner, PlantSoil.MEADOW, terraformerPos);
            require(ProductionTowerService.placeTower(
                    game, owner, meadowPos, PlantTowers.T1_MYCELIUM_TOWER.id()) == TowerPlacementResult.OCCUPIED,
                    "A combat plant must reject another family's soil."
            );
            require(ProductionTowerService.placeTower(
                    game, owner, meadowPos, PlantTowers.T1_MEADOW_TOWER.id()) == TowerPlacementResult.SUCCESS,
                    "A meadow combat plant must accept meadow soil."
            );
            PlantCombatTower dandelion = (PlantCombatTower) lane.towerAt(GridPosition.from(meadowPos));
            long beforeSettlement = game.players().get(owner).economy().mineral();
            PlantSoilEnvironment.tick(lane);
            require(game.players().get(owner).economy().mineral() == beforeSettlement,
                    "Plant environment ticks must not pay continuous income.");

            lane.moveTowersToFinalDefense();
            require(PlantSoilStates.soilAt(owner, dandelion.position()) == null,
                    "The final-defense slot must be bare ground for this regression test.");
            require(dandelion.bloomBonus() > 0.0,
                    "A plant at final defense must keep its family terrain effect.");
            new PlantTowerJob().onRoundEnded(new JobContext(game, game.players().get(owner)), 1);
            require(game.players().get(owner).economy().mineral() == beforeSettlement + 4,
                    "A surviving T1 dandelion must pay four diamonds exactly once at settlement.");
            require(dandelion.diamondPerWave() == 4,
                    "Final-defense movement must preserve the original meadow settlement claim.");

            game.teams().get(TeamId.RED).resetForRound();
            require(ProductionTowerService.sellTower(game, owner, GridPosition.from(terraformerPos)).result()
                    == TowerSellResult.SUCCESS, "Selling the terraformer must succeed.");
            require(PlantSoilStates.count(owner, PlantSoil.MEADOW) == 0,
                    "Selling the terraformer must release every claimed tile.");
            require(context.getLevel().getBlockState(terraformerPos).equals(originalFloor),
                    "Selling the terraformer must restore the original floor block.");
            require(dandelion.diamondPerWave() == 0,
                    "A dandelion without meadow soil must not settle income.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Plant lifecycle failed: " + failure.getMessage()));
        } finally {
            if (game != null) {
                game.close();
            }
            PlantSoilStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void myceliumMineDamagesAndDisablesTargetsInItsRadius(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("plant-mine-owner");
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, owner);
        group.addLane(lane);
        try {
            TowerBalanceRuntime.apply(defaults);
            fillFloor(context);
            GridPosition sourcePos = position(context, 3, 1, 3);
            PlantTerraformTower terraformer = new PlantTerraformTower(
                    TowerBalanceRuntime.resolve(PlantTowers.T1_MUSHROOM_SPORE_TOWER), owner, TeamId.RED, 1, sourcePos
            );
            PlantMineTower mine = new PlantMineTower(
                    TowerBalanceRuntime.resolve(PlantTowers.T1_MYCELIUM_TOWER),
                    owner,
                    TeamId.RED,
                    1,
                    position(context, 4, 1, 3)
            );
            lane.addTower(terraformer);
            lane.addTower(mine);
            mine.markWaveStarted(1);

            Monster first = spawnMonster(context, lane, "plant-mine-first", position(context, 4, 1, 3));
            Monster second = spawnMonster(context, lane, "plant-mine-second", position(context, 6, 1, 3));
            Monster outside = spawnMonster(context, lane, "plant-mine-outside", position(context, 7, 1, 7));

            // 밟는 즉시 터지지 않습니다. 먼저 섬광이 뜨고 도화선이 탑니다.
            mine.tick(lane);
            requireClose(1_000.0, first.health(), "A mine must not damage anything while its fuse burns.");
            // 점화 틱이 쿨다운을 도화선 길이로 세우므로, 실제 폭발은 그다음 틱입니다.
            int fuseTicks = (int) defaults.ability(PlantTowers.T1_MYCELIUM_TOWER.id(), "fuseTicks", 8.0);
            for (int tick = 0; tick <= fuseTicks; tick++) {
                mine.tick(lane);
            }

            require(lane.towers().contains(mine), "A triggered mine must stay until the round ends.");
            requireClose(900.6875, first.health(), "The trigger target must take the tuned T1 explosion.");
            requireClose(900.6875, second.health(), "A second target inside the blast must take splash damage.");
            requireClose(1_000.0, outside.health(), "A target outside the blast must remain unharmed.");
            SemionMonsterEntity firstEntity = entity(context, first);
            requireClose(0.35, firstEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "The mine must apply its tuned slow.");
            requireClose(1.0, firstEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "The mine must disable attacks.");
            requireClose(198.625, mine.roundMagicDamageDealt(), "Mine splash damage must be attributed as magic damage.");

            // 라운드 안에서는 적이 계속 서 있어도 다시 터지지 않아야 합니다. 다시 터지게 두면
            // 지뢰 하나가 광역 기관총이 되고, 무력화가 끊기지 않아 그 길목의 적이 영영 공격하지
            // 못합니다.
            require(mine.spentThisRound(), "A detonated mine must be spent for the rest of the round.");
            double afterFirstBlast = first.health();
            for (int tick = 0; tick < 400; tick++) {
                mine.tick(lane);
            }
            requireClose(afterFirstBlast, first.health(), "A spent mine must not detonate again in the same round.");

            // 라운드가 새로 시작되면 다시 장전됩니다. 도화선은 그때도 그대로 탑니다.
            mine.resetForRound(lane);
            require(!mine.spentThisRound(), "A new round must rearm the mine.");
            mine.tick(lane);
            requireClose(afterFirstBlast, first.health(), "The next round's fuse must burn before it hurts anyone.");
            for (int tick = 0; tick <= fuseTicks; tick++) {
                mine.tick(lane);
            }
            require(first.health() < afterFirstBlast, "A rearmed mine must detonate again in the next round.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Plant mine failed: " + failure.getMessage()));
        } finally {
            group.closeRuntime();
            PlantSoilStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    /**
     * 지뢰는 라운드가 끝날 때마다 한 단계씩 삭고, 붉은 버섯은 사라집니다.
     *
     * <p>실제 레인에서 확인하는 이유는 삭는 과정이 타워를 갈아 끼우기 때문입니다. 판매가와 체력이
     * 새 티어 기준으로 잡히는지, 자리와 소유자가 그대로인지는 카탈로그 값만 봐서는 알 수 없습니다.
     */
    @GameTest
    public void myceliumMinesDecayOneTierEachRoundUntilTheyDisappear(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("plant-mine-decay-owner");
        SemionGame game = null;
        try {
            TowerBalanceRuntime.apply(defaults);
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
            game = startedPlantGame(context, owner);
            game.players().get(owner).economy().addMineral(1_000);
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            BlockPos terraformerPos = BlockPos.containing(lane.laneLayout().positionAt(0.35));
            require(ProductionTowerService.placeTower(
                    game, owner, terraformerPos, PlantTowers.T1_MUSHROOM_SPORE_TOWER.id())
                    == TowerPlacementResult.SUCCESS, "Mycelium terraformer placement must succeed.");

            BlockPos minePos = claimedEmptyPosition(lane, owner, PlantSoil.MYCELIUM, terraformerPos);
            require(ProductionTowerService.placeTower(
                    game, owner, minePos, PlantTowers.T1_MYCELIUM_TOWER.id()) == TowerPlacementResult.SUCCESS,
                    "Mine placement must succeed.");
            require(ProductionTowerService.upgradeTower(
                    game, owner, GridPosition.from(minePos), PlantTowers.T2_MYCELIUM_TOWER.id())
                    == TowerUpgradeResult.SUCCESS, "Mine upgrade must succeed.");

            GridPosition grid = GridPosition.from(minePos);
            JobContext jobContext = new JobContext(game, game.players().get(owner));

            new PlantTowerJob().onRoundEnded(jobContext, 1);
            var decayed = lane.towerAt(grid);
            require(decayed instanceof PlantMineTower, "A decayed mine must still be a mine.");
            require(PlantTowers.matches(decayed.type(), PlantTowers.T1_MYCELIUM_TOWER),
                    "진홍빛 버섯 must decay into 붉은 버섯, found " + decayed.type().id());
            require(owner.equals(decayed.ownerPlayer()), "Decay must keep the owner.");
            requireClose(TowerBalanceRuntime.resolve(PlantTowers.T1_MYCELIUM_TOWER).maxHealth(), decayed.health(),
                    "A decayed mine must carry the health of the tier it became.");
            require(decayed.paidMineralCost()
                            == TowerBalanceRuntime.resolve(PlantTowers.T1_MYCELIUM_TOWER).mineralCost(),
                    "A decayed mine must be worth its new tier, not the one it was bought at.");

            new PlantTowerJob().onRoundEnded(jobContext, 2);
            require(lane.towerAt(grid) == null, "붉은 버섯 must disappear at the end of the next round.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Plant mine decay failed: " + failure.getMessage()));
        } finally {
            if (game != null) {
                game.close();
            }
            PlantSoilStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 30)
    public void desertTerrainDamageBelongsToItsLivingTerraformer(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("plant-desert-owner");
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, owner);
        group.addLane(lane);
        try {
            TowerBalanceRuntime.apply(defaults);
            fillFloor(context);
            GridPosition sourcePos = position(context, 3, 1, 3);
            PlantTerraformTower source = new PlantTerraformTower(
                    TowerBalanceRuntime.resolve(PlantTowers.T1_DRY_GRASS_SEED_TOWER), owner, TeamId.RED, 1, sourcePos
            );
            lane.addTower(source);
            source.markWaveStarted(1);
            Monster target = spawnMonster(context, lane, "plant-desert-target", sourcePos);
            int delay = (int) ((20 - context.getLevel().getGameTime() % 20) % 20);

            context.runAfterDelay(delay, () -> {
                try {
                    PlantSoilEnvironment.tick(lane);
                    requireClose(992.5, target.health(), "Desert terrain must deal 0.75% max-health magic damage.");
                    requireClose(7.5, source.roundMagicDamageDealt(),
                            "Desert terrain damage must be credited to its terraformer.");
                    require(owner.equals(target.lastHitPlayerId().orElse(null))
                                    && target.lastHitSourceKind() == KillSourceKind.TOWER,
                            "Desert terrain damage must preserve tower last-hit attribution."
                    );

                    lane.removeTower(source);
                    fillFloor(context);
                    PlantSoilStates.terraform(lane, owner, sourcePos, 1, PlantSoil.DESERT);
                    double healthAfterAttributedHit = target.health();
                    PlantSoilEnvironment.tick(lane);
                    requireClose(healthAfterAttributedHit, target.health(),
                            "Orphaned soil must not fall back to unattributed raw damage.");
                    context.succeed();
                } catch (RuntimeException | Error failure) {
                    failure.printStackTrace();
                    context.fail(Component.literal("Plant desert attribution failed: " + failure.getMessage()));
                } finally {
                    group.closeRuntime();
                    PlantSoilStates.clear(owner);
                    TowerBalanceRuntime.apply(defaults);
                }
            });
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            group.closeRuntime();
            PlantSoilStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
            context.fail(Component.literal("Plant desert setup failed: " + failure.getMessage()));
        }
    }

    private static SemionGame startedPlantGame(GameTestHelper context, UUID owner) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        require(game.selectJob(owner, PlantTowerJob.ID), "Plant job selection must succeed.");
        require(game.start(
                context.getLevel().getServer(),
                new ParticipantSelectionPlan(
                        MatchMode.NORMAL,
                        List.of(new AssignedParticipant(owner, "plant-tester", TeamId.RED, 1)),
                        java.util.Set.of(),
                        1
                )
        ), "Plant test game must start.");
        return game;
    }

    private static BlockPos claimedEmptyPosition(PlayerLane lane, UUID owner, PlantSoil soil, BlockPos excluded) {
        BlockBounds bounds = lane.laneLayout().laneArea();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, excluded.getY(), z);
                if (!candidate.equals(excluded)
                        && PlantSoilStates.soilAt(owner, GridPosition.from(candidate)) == soil
                        && !lane.hasTowerAt(GridPosition.from(candidate))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("No empty claimed soil tile was found.");
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 4, 7));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7))),
                BlockBounds.of(min, max),
                List.of(position(context, 6, 1, 6))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static void fillFloor(GameTestHelper context) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                context.setBlock(x, 1, z, Blocks.STONE);
                context.setBlock(x, 2, z, Blocks.AIR);
            }
        }
    }

    private static Monster spawnMonster(GameTestHelper context, PlayerLane lane, String id, GridPosition position) {
        Monster monster = new Monster(
                id,
                lane.teamId(),
                lane.laneId(),
                Optional.empty(),
                Optional.empty(),
                1_000.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return monster;
    }

    private static SemionMonsterEntity entity(GameTestHelper context, Monster monster) {
        return (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual + '.');
        }
    }
}
