package kim.biryeong.semiontd.tower.adversary;

import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.augment.AugmentCombatFixture;
import kim.biryeong.semiontd.augment.AugmentChoice;

public final class AdversaryTowerAugmentCombatTest extends AugmentCombatFixture {
    @GameTest
    public void jobRivalMatchAddsHealingAndRefreshesWithoutDuplicateCredit(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var fox = new kim.biryeong.semiontd.tower.adversary.AdversaryFoxTower(
                kim.biryeong.semiontd.tower.adversary.AdversaryTowers.FOX, lane.ownerPlayer(), TeamId.RED, 1, position);
        var rival = new kim.biryeong.semiontd.tower.adversary.AdversaryRivalTower(
                kim.biryeong.semiontd.tower.adversary.AdversaryTowers.BREEZE_RIVAL,
                lane.ownerPlayer(), TeamId.RED, 1, new GridPosition(position.x() + 1, position.y(), position.z()));
        try {
            lane.addTower(fox);
            lane.addTower(rival);
            lane.assignAugmentSnapshot(snapshot("job_adversary_towers_g1", AugmentChoice.none()));
            lane.markWaveStarted(5);
            var source = entity(context, fox);
            source.setNoAi(true);
            source.setNoGravity(true);
            source.setPermanentlyInvulnerable(true);
            var proxy = (SemionMonsterEntity) context.getLevel().getEntity(lane.activeMonsters().getFirst().minecraftEntityId());
            proxy.setNoAi(true);
            fox.syncHealth(100);
            source.setHealth(100);
            fox.onKill(source, proxy, 1);
            close(4, rival.contributedScore(), "Rival contributes double score.");
            close(250, fox.health(), "Existing 20% plus augment 30% heals a 300-health fox by 150.");
            close(1, source.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Rival buff is +100%.");
            fox.onIgniteKill(proxy);
            close(250, fox.health(), "Duplicate kill cannot heal twice.");
            close(4, rival.contributedScore(), "Duplicate kill cannot score twice.");
            for (int tick = 0; tick < 60; tick++) {source.aiStep();}
            close(60, source.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Buff lasts exactly 120 ticks.");
            var second = new kim.biryeong.semiontd.tower.adversary.AdversaryRivalTower(
                    kim.biryeong.semiontd.tower.adversary.AdversaryTowers.BREEZE_RIVAL,
                    lane.ownerPlayer(), TeamId.RED, 1, new GridPosition(position.x() + 2, position.y(), position.z()));
            lane.addTower(second);
            second.onWaveStarted(lane, 5);
            var secondProxy = (SemionMonsterEntity) context.getLevel().getEntity(lane.activeMonsters().getLast().minecraftEntityId());
            fox.onIgniteKill(secondProxy);
            close(1, source.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Repeated proc refreshes instead of stacking.");
            close(120, source.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "A new kill refreshes all six seconds.");
            close(300, fox.health(), "Healing cannot exceed maximum health.");
            for (int tick = 0; tick < 120; tick++) {source.aiStep();}
            close(0, source.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Buff expires after refreshed duration.");
            source.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS,
                    net.minecraft.resources.Identifier.parse("semiontd:job_adversary_towers_g1"), 1, 120);
            lane.resetForRound();
            close(0, entity(context, fox).activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Round reset removes temporary buff.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
        } finally {
            cleanup(lane);
            kim.biryeong.semiontd.tower.adversary.AdversaryProgressStates.clear(lane.ownerPlayer());
        }
    }
}
