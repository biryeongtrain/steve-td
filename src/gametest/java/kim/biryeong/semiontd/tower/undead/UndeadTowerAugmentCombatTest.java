package kim.biryeong.semiontd.tower.undead;

import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.augment.AugmentCombatFixture;
import kim.biryeong.semiontd.augment.AugmentChoice;

public final class UndeadTowerAugmentCombatTest extends AugmentCombatFixture {
    @GameTest
    public void finalUndeadDefenderRevivesAfterTwentyTicksBeforeLaneBreak(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            Tower tower = ProductionTowerCatalog.find(
                    kim.biryeong.semiontd.tower.undead.UndeadTowers.T1_SKELETON_TOWER.id()).orElseThrow()
                    .create(lane.ownerPlayer(), TeamId.RED, 1, GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3))));
            lane.addTower(tower);
            lane.assignAugmentSnapshot(snapshot("job_undead_towers_g2", AugmentChoice.none()));
            lane.markWaveStarted(5);
            var source = entity(context, (EntityBackedTower) tower);
            source.setNoAi(true);
            source.setHealth(0);
            lane.tick(context.getLevel().getServer());
            if (lane.laneDefenseBroken()) {throw new AssertionError("A pending revival is still a defender.");}
            for (int tick = 0; tick < 19; tick++) {lane.tick(context.getLevel().getServer());}
            close(0, tower.health(), "Revival must wait all twenty ticks after death.");
            lane.tick(context.getLevel().getServer());
            close(tower.currentMaxHealth() * .6, tower.health(), "Revival restores sixty percent health.");
            if (lane.laneDefenseBroken()) {throw new AssertionError("Successful revival must preserve lane defense.");}
            entity(context, (EntityBackedTower) tower).setHealth(0);
            lane.tick(context.getLevel().getServer());
            if (!lane.laneDefenseBroken()) {throw new AssertionError("A spent revival cannot postpone a real lane break.");}
            context.succeed();
        } catch (Throwable failure) {context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));}
        finally {cleanup(lane);}
    }
}
