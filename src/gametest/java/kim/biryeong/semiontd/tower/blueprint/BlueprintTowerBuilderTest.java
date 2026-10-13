package kim.biryeong.semiontd.tower.blueprint;

import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.BlueprintTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.blueprint.Blueprint;
import kim.biryeong.semiontd.tower.blueprint.BlueprintDesign;
import kim.biryeong.semiontd.tower.blueprint.BlueprintLibrary;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStates;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStats;
import kim.biryeong.semiontd.tower.blueprint.BlueprintTower;
import kim.biryeong.semiontd.tower.blueprint.BlueprintVisuals;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class BlueprintTowerBuilderTest {
    /** 빌더 빌더: 저장된 설계도는 경기 시작 때 타워가 되고, 경기 중에 설계도를 만들면 주인만 세울 수 있는 1단계 타워가 되고, 설계한 능력치·가격 그대로 서며, 경기가 끝나면 사라집니다. */
    @GameTest
    public void designedBlueprintBuildsWithItsStatsAndIsClearedWhenTheMatchCloses(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-builder-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        String towerId = null;
        String visual = BlueprintVisuals.options().getFirst().sourceTowerId();
        // 계정에 저장된 설계도는 경기가 시작하면 그 사람의 타워가 됩니다.
        BlueprintLibrary.load(owner, List.of(BlueprintDesign.of("저장된 궁수",
                new BlueprintStats(120.0, 10.0, 20, 6.0, 25, DamageType.PHYSICAL), visual)));
        try {
            require(game.selectJob(owner, BlueprintTowerJob.ID), "Blueprint job selection must succeed.");
            require(game.start(
                    context.getLevel().getServer(),
                    new ParticipantSelectionPlan(
                            MatchMode.NORMAL,
                            List.of(new AssignedParticipant(owner, "blueprint-tester", TeamId.RED, 1)),
                            java.util.Set.of(),
                            1
                    )
            ), "Blueprint test game must start.");
            require(ProductionTowerService.availableTowers(game, owner).size() == 1
                            && ProductionTowerService.availableTowers(game, owner).getFirst().type().displayName().equals("저장된 궁수"),
                    "The saved library must be installed as this match's towers.");

            BlueprintStates.Creation creation = BlueprintStates.create(owner, "시험 궁수",
                    new BlueprintStats(150.0, 15.0, 20, 7.0, 25, DamageType.MAGIC), visual);
            require(creation.success(), "Blueprint creation must succeed: " + creation.message());
            Blueprint blueprint = creation.blueprint();
            towerId = blueprint.towerId();
            require(ProductionTowerService.availableTowers(game, owner).stream()
                            .anyMatch(entry -> entry.type().id().equals(blueprint.towerId())),
                    "The owner's shop must list the new blueprint.");

            game.players().get(owner).economy().addMineral(5_000);
            long before = game.players().get(owner).economy().mineral();
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            BlockPos spot = BlockPos.containing(lane.laneLayout().positionAt(0.4));
            require(ProductionTowerService.placeTower(game, owner, spot, blueprint.towerId()) == TowerPlacementResult.SUCCESS,
                    "The owner must be able to build the blueprint.");
            Tower tower = lane.towerAt(GridPosition.from(spot));
            require(tower instanceof BlueprintTower, "A blueprint must build a BlueprintTower.");
            require(game.players().get(owner).economy().mineral() == before - blueprint.price(),
                    "Building must charge the blueprint's price.");
            require(tower.type().maxHealth() == 150.0 && tower.type().damage() == 15.0
                            && tower.type().range() == 7.0 && tower.type().primaryDamageType() == DamageType.MAGIC,
                    "The tower must carry the designed stats.");
        } finally {
            game.close();
            BlueprintLibrary.forget(owner);
        }
        require(towerId == null || ProductionTowerCatalog.find(towerId).isEmpty(),
                "Closing the match must drop the blueprint from the catalog.");
        require(BlueprintStates.of(owner).isEmpty(), "Closing the match must clear the owner's blueprints.");
        context.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
