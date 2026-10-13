package kim.biryeong.semiontd.entity.goal;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

public final class EntityGoalSupportTargetsTest {
    @GameTest
    public void monsterBuffKeepsNearestLimitAndSelfFallback(GameTestHelper context) {
        int lane = 19031;
        var caster = monster(context, lane, 1);
        var farther = monster(context, lane, 4);
        var nearest = monster(context, lane, 2);
        var otherLane = monster(context, lane + 1, 1.2);
        var effect = TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS;
        if (!new ApplyMonsterTimedEffectGoal(caster, effect, 0.3, 8, 100, 20, 20, 1).castAbility()) {
            throw new AssertionError("Nearest valid target must receive the buff.");
        }
        equal(0.3, nearest.activeTimedEffectMagnitude(effect));
        equal(0, farther.activeTimedEffectMagnitude(effect));
        equal(0, otherLane.activeTimedEffectMagnitude(effect));
        equal(0, caster.activeTimedEffectMagnitude(effect));
        new ApplyMonsterTimedEffectGoal(caster, effect, 0.4, 8, 100, 20, 20, 3).castAbility();
        equal(0.4, caster.activeTimedEffectMagnitude(effect));
        equal(0.4, nearest.activeTimedEffectMagnitude(effect));
        equal(0.4, farther.activeTimedEffectMagnitude(effect));
        equal(0, otherLane.activeTimedEffectMagnitude(effect));
        context.succeed();
    }

    @GameTest
    public void towerDebuffKeepsTeamLaneAndDistanceFilters(GameTestHelper context) {
        int lane = 19032;
        var caster = monster(context, lane, 1);
        var farther = tower(context, TeamId.RED, lane, 5);
        var nearest = tower(context, TeamId.RED, lane, 2);
        var otherLane = tower(context, TeamId.RED, lane + 1, 1);
        var otherTeam = tower(context, TeamId.BLUE, lane, 1);
        var effect = TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION;
        if (!new ApplyTowerTimedEffectGoal(caster, effect, 0.3, 8, 100, 20, 20, 1).castAbility()) {
            throw new AssertionError("Nearest valid tower must receive the debuff.");
        }
        equal(0.3, nearest.activeTimedEffectMagnitude(effect));
        equal(0, farther.activeTimedEffectMagnitude(effect));
        equal(0, otherLane.activeTimedEffectMagnitude(effect));
        equal(0, otherTeam.activeTimedEffectMagnitude(effect));
        context.succeed();
    }

    private static SemionMonsterEntity monster(GameTestHelper context, int lane, double x) {
        Monster logical = new Monster("selection-target", TeamId.RED, lane,
                Optional.of(UUID.randomUUID()), Optional.of(TeamId.BLUE),
                100, 0, 10, AttackKind.MELEE, "minecraft:zombie", 0);
        var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(logical, null);
        entity.setPos(context.absoluteVec(new Vec3(x, 1, 1)));
        entity.setNoAi(true);
        entity.setNoGravity(true);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static SemionTowerEntity tower(GameTestHelper context, TeamId team, int lane, int x) {
        BlockPos position = context.absolutePos(new BlockPos(x, 1, 1));
        var runtime = new TestTower(TestTowerTypes.TEST_DIRECT, UUID.randomUUID(), team, lane,
                GridPosition.from(position));
        var entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(runtime, null);
        entity.setPos(Vec3.atBottomCenterOf(position));
        entity.setNoAi(true);
        entity.setNoGravity(true);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void equal(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
