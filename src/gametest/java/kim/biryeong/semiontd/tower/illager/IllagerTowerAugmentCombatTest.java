package kim.biryeong.semiontd.tower.illager;

import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.augment.AugmentCombatFixture;
import kim.biryeong.semiontd.augment.AugmentChoice;

public final class IllagerTowerAugmentCombatTest extends AugmentCombatFixture {
    @GameTest
    public void jobOmenStartsAfterFirstHitAndKeepsCaptainMark(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var tower = new kim.biryeong.semiontd.tower.illager.IllagerTower(
                kim.biryeong.semiontd.tower.illager.IllagerTowers.T1_PILLAGER,
                lane.ownerPlayer(), TeamId.RED, 1, position, position);
        try {
            lane.addTower(tower);
            lane.assignAugmentSnapshot(snapshot("job_illager_towers_s", AugmentChoice.none()));
            lane.markWaveStarted(5);
            var source = entity(context, tower);
            source.setNoAi(true);
            var target = monster(context, lane, source.position().add(1, 0, 0), 1000);
            var other = monster(context, lane, source.position().add(2, 0, 0), 1000);
            tower.onAttackResolved(source, target, 10, 10, 0, false);
            double base = source.attackDamageAmount(target);
            var hit = tower.damagePrimaryAttackTargetResult(source, target, base);
            close(base, hit.dealtDamage(), "First hit must not receive Omen retroactively.");
            source.recordAttack(target, base, hit.outgoingDamage(), hit.dealtDamage(), false);
            close(base * 1.2, tower.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target)), "Next hit receives Omen once.");
            kim.biryeong.semiontd.tower.illager.IllagerMarks.apply(target.runtimeMonster(), lane.ownerPlayer(), .3, 200, position, 4);
            close(base * 1.5, tower.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target)), "Captain and Omen bonuses add rather than multiply.");
            close(15, tower.damageTargetResult(source, target, 10).dealtDamage(), "Secondary damage uses the same target mark once.");
            close(10, tower.damageTargetResult(source, other, 10).dealtDamage(), "Unmarked secondary victim receives no Omen bonus.");
            source.recordAttack(other, 10, 10, 10, false);
            close(0, kim.biryeong.semiontd.tower.illager.IllagerMarks.omenBonus(other.runtimeMonster(), lane.ownerPlayer()), "Only the first hit marks.");
            for (int i = 0; i < 80; i++) {target.runtimeMonster().tickSurvivalScaling(null, 0);}
            close(base * 1.3, tower.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target)), "Omen expires at 80 ticks while captain mark remains.");
            lane.markWaveStarted(6);
            source.recordAttack(other, 10, 10, 10, false);
            close(.2, kim.biryeong.semiontd.tower.illager.IllagerMarks.omenBonus(other.runtimeMonster(), lane.ownerPlayer()), "Next wave resets first-hit eligibility.");
            context.succeed();
        } finally {cleanup(lane);}
    }
}
