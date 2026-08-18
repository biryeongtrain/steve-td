package kim.biryeong.semiontd.tower.end;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
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
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class EndIntegrationGameTest {
    @GameTest
    public void placementUpgradeWaveResetAndCloseUseRealLifecycle(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("end-integration-lifecycle-owner");
        SemionGame game = null;
        boolean closed = false;
        try {
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
            game = startedGame(context, owner);
            game.players().get(owner).economy().addMineral(1_000);
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            BlockPos corePosition = BlockPos.containing(lane.laneLayout().positionAt(0.35));
            BlockPos feederPosition = nearbyTowerPlacementPosition(lane, corePosition);

            require(ProductionTowerService.placeTower(game, owner, corePosition, EndTowers.BASE_END_TOWER.id())
                    == TowerPlacementResult.SUCCESS, "End core placement must succeed.");
            require(ProductionTowerService.placeTower(game, owner, feederPosition, EndTowers.T1_SHULKER_TOWER.id())
                    == TowerPlacementResult.SUCCESS, "End feeder placement must succeed.");
            require(ProductionTowerService.upgradeTower(
                    game, owner, feederPosition, EndTowers.T2_SHULKER_TOWER.id()) == TowerUpgradeResult.SUCCESS,
                    "End feeder upgrade must use the production graph."
            );

            EndTower core = (EndTower) lane.towerAt(GridPosition.from(corePosition));
            EndTower feeder = (EndTower) lane.towerAt(GridPosition.from(feederPosition));
            require(feeder.type().id().equals(EndTowers.T2_SHULKER_TOWER.id()),
                    "End upgrade must replace the feeder with its T2 runtime.");
            SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld()
                    .getEntity(core.entityId().orElseThrow());

            lane.markWaveStarted(1);
            require(core.state() == EndTowerState.PHANTOM,
                    "End core must hatch through the real wave-start lifecycle.");
            game.teams().get(TeamId.RED).resetForRound();
            require(core.state() == EndTowerState.EGG,
                    "End core must return to its egg state during round reset.");
            require(Math.abs(feeder.health() - feeder.currentMaxHealth()) < 0.0001,
                    "End feeder must finish round reset at full health.");

            game.close();
            closed = true;
            require(lane.towers().isEmpty(), "Game close must detach every End tower from its lane.");
            require(coreEntity.isRemoved(), "Game close must remove the End core entity.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("End integration lifecycle failed: " + failure.getMessage()));
        } finally {
            if (game != null && !closed) {
                game.close();
            }
            TowerBalanceRuntime.apply(defaults);
        }
    }

    private static SemionGame startedGame(GameTestHelper context, UUID owner) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        require(game.selectJob(owner, EndTowerJob.ID), "End builder selection must succeed.");
        require(game.start(
                context.getLevel().getServer(),
                new ParticipantSelectionPlan(
                        MatchMode.NORMAL,
                        List.of(new AssignedParticipant(owner, "end-integration", TeamId.RED, 1)),
                        Set.of(),
                        1
                )
        ), "End game start must succeed.");
        return game;
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static BlockPos nearbyTowerPlacementPosition(PlayerLane lane, BlockPos origin) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos candidate = origin.offset(dx, 0, dz);
                if (!candidate.equals(origin)
                        && lane.canPlaceTowerAt(candidate)
                        && !lane.hasTowerAt(GridPosition.from(candidate))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Could not find a second End tower placement position.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
