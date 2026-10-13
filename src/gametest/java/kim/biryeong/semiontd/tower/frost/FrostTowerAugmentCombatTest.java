package kim.biryeong.semiontd.tower.frost;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.frost.FrostSplashTower;
import kim.biryeong.semiontd.tower.frost.FrostTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.augment.AugmentCombatFixture;
import kim.biryeong.semiontd.augment.AugmentChoice;

public final class FrostTowerAugmentCombatTest extends AugmentCombatFixture {
    @GameTest(maxTicks = 100)
    public void frostImmediateExtraAttackDoesNotReceivePrimaryFinishingBonus(GameTestHelper context) {
        PlayerLane lane = lane(context);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        FrostSplashTower tower = new FrostSplashTower(FrostTowers.ICE_BREAKER_T3, lane.ownerPlayer(), TeamId.RED, 1, position, position);
        lane.addTower(tower);
        lane.assignAugmentSnapshot(snapshot("finishing_fire_2", AugmentChoice.none()));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity target = monster(context, lane, source.position().add(1, 0, 0), 100_000);
        target.runtimeMonster().syncHealth(50_000);
        target.setHealth(50_000);
        try {
            new TowerAttackMonsterGoal(source).tick();
            close(50_000 - 54, target.runtimeMonster().health(), "Frost must deal primary 34 plus one unaugmented extra 20.");
            context.succeed();
        } finally { cleanup(lane); }
    }
}
