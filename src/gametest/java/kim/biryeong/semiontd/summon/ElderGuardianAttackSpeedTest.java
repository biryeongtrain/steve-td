package kim.biryeong.semiontd.summon;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

public final class ElderGuardianAttackSpeedTest implements RuntimeArenaFixture {
    private static final TimedEffectType ELDER_SLOW = TimedEffectType.TOWER_ATTACK_SPEED_MULTIPLICATIVE_REDUCTION;
    private static final TimedEffectType ELDER_RANGE = TimedEffectType.TOWER_RANGE_MULTIPLICATIVE_REDUCTION;

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void realElderAbilityMultipliesBuffedSpeedAndKeepsRangeAndTargetLimit(GameTestHelper context) throws Exception {
        int lane = 19121;
        var caster = monster(context, lane);
        var first = tower(context, TeamId.RED, lane, 2, 0);
        var second = tower(context, TeamId.RED, lane, 3, 0);
        var third = tower(context, TeamId.RED, lane, 4, 0);
        var fourth = tower(context, TeamId.RED, lane, 5, 0);
        var otherLane = tower(context, TeamId.RED, lane + 1, 2, 0);
        var otherTeam = tower(context, TeamId.BLUE, lane, 2, 0);
        double baseRange = first.attackRange();
        first.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 1.0, 300);
        first.applyTimedEffect(TimedEffectType.TOWER_RANGE_BONUS, 1.0, 300);
        cast(caster, definition());
        for (var entity : new SemionTowerEntity[]{first, second, third}) {
            equal(.25, entity.activeTimedEffectMagnitude(ELDER_SLOW));
            equal(.20, entity.activeTimedEffectMagnitude(ELDER_RANGE));
            equal(0, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION));
            equal(100, entity.activeTimedEffectTicks(ELDER_SLOW));
            equal(100, entity.activeTimedEffectTicks(ELDER_RANGE));
            equal(0, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION));
        }
        equal(9, first.attackIntervalTicks());
        equal(18, second.attackIntervalTicks());
        equal(baseRange * 1.6, first.attackRange());
        equal(baseRange * .8, second.attackRange());
        var detailMethod = kim.biryeong.semiontd.ui.SemionDialogService.class.getDeclaredMethod(
                "appendTowerTimedEffects", StringBuilder.class, SemionTowerEntity.class);
        detailMethod.setAccessible(true);
        var detail = new StringBuilder();
        detailMethod.invoke(null, detail, second);
        String displayed = kim.biryeong.semiontd.ui.SemionText.mini(detail.toString()).getString();
        if (displayed.contains("곱연산") || !displayed.contains("공격 속도 -25.0%")
                || !displayed.contains("사거리 -20.0%") || !displayed.contains("5.0초")) {
            throw new AssertionError("Tower detail must keep simple labels, percentages and duration: " + displayed);
        }
        for (var entity : new SemionTowerEntity[]{fourth, otherLane, otherTeam}) {
            equal(0, entity.activeTimedEffectMagnitude(ELDER_SLOW));
            equal(0, entity.activeTimedEffectMagnitude(ELDER_RANGE));
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void ordinarySlowStaysAdditiveAndElderStacksByStrongestThenCleanses(GameTestHelper context) {
        int lane = 19122;
        var caster = monster(context, lane);
        var target = tower(context, TeamId.RED, lane, 2, 0);
        double baseRange = target.attackRange();
        target.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 1.0, 300);
        target.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, .5, 300);
        target.applyTimedEffect(TimedEffectType.TOWER_RANGE_BONUS, 1.0, 300);
        target.applyTimedEffect(TimedEffectType.TOWER_RANGE_REDUCTION, .3, 300);
        target.applyTimedEffect(TimedEffectType.TOWER_FLAT_RANGE_BONUS, 2, 300);
        target.applyTimedEffect(TimedEffectType.TOWER_FLAT_RANGE_REDUCTION, .5, 300);
        double beforeElder = baseRange * 1.7 + 1.5;
        equal(beforeElder, target.attackRange());
        equal(9, target.attackIntervalTicks());
        cast(caster, definition());
        equal(12, target.attackIntervalTicks());
        equal(beforeElder * .8, target.attackRange());
        cast(caster, definition());
        equal(.25, target.activeTimedEffectMagnitude(ELDER_SLOW));
        equal(.2, target.activeTimedEffectMagnitude(ELDER_RANGE));
        equal(beforeElder * .8, target.attackRange());
        equal(12, target.attackIntervalTicks());
        cast(caster, custom(.20));
        equal(.25, target.activeTimedEffectMagnitude(ELDER_SLOW));
        cast(caster, custom(.40));
        equal(.40, target.activeTimedEffectMagnitude(ELDER_SLOW));
        equal(15, target.attackIntervalTicks());
        target.cleanseDebuffs();
        equal(0, target.activeTimedEffectMagnitude(ELDER_SLOW));
        equal(0, target.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION));
        equal(0, target.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION));
        equal(0, target.activeTimedEffectMagnitude(ELDER_RANGE));
        equal(baseRange * 2 + 2, target.attackRange());
        equal(1, target.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS));
        equal(7, target.attackIntervalTicks());
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void configuredTwentyPercentAndIncomeResistanceRemainEffective(GameTestHelper context) {
        int lane = 19123;
        var caster = monster(context, lane);
        var target = tower(context, TeamId.RED, lane, 2, 0);
        var resistant = tower(context, TeamId.RED, lane, 3, .5);
        double baseRange = target.attackRange();
        target.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 1.0, 300);
        resistant.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 1.0, 300);
        cast(caster, custom(.20));
        equal(.20, target.activeTimedEffectMagnitude(ELDER_SLOW));
        equal(.10, resistant.activeTimedEffectMagnitude(ELDER_SLOW));
        equal(.20, target.activeTimedEffectMagnitude(ELDER_RANGE));
        equal(.10, resistant.activeTimedEffectMagnitude(ELDER_RANGE));
        equal(baseRange * .8, target.attackRange());
        equal(baseRange * .9, resistant.attackRange());
        equal(9, target.attackIntervalTicks());
        equal(8, resistant.attackIntervalTicks());
        for (int tick = 0; tick < 100; tick++) {
            target.aiStep();
            resistant.aiStep();
        }
        equal(0, target.activeTimedEffectMagnitude(ELDER_SLOW));
        equal(0, resistant.activeTimedEffectMagnitude(ELDER_SLOW));
        equal(0, target.activeTimedEffectMagnitude(ELDER_RANGE));
        equal(0, resistant.activeTimedEffectMagnitude(ELDER_RANGE));
        equal(baseRange, target.attackRange());
        equal(7, target.attackIntervalTicks());
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void bothDebuffsRefreshAndExpireInGameTicksAcrossCombatSpeeds(GameTestHelper context) {
        var manager = context.getLevel().getServer().tickRateManager();
        float originalRate = manager.tickrate();
        int lane = 19124;
        try {
            for (float rate : new float[]{20, 40, 20}) {
                manager.setTickRate(rate);
                var caster = monster(context, lane);
                var target = tower(context, TeamId.RED, lane++, 2, 0);
                double baseRange = target.attackRange();
                cast(caster, definition());
                for (int tick = 0; tick < 40; tick++) target.aiStep();
                equal(60, target.activeTimedEffectTicks(ELDER_SLOW));
                equal(60, target.activeTimedEffectTicks(ELDER_RANGE));
                cast(caster, definition());
                equal(100, target.activeTimedEffectTicks(ELDER_SLOW));
                equal(100, target.activeTimedEffectTicks(ELDER_RANGE));
                for (int tick = 0; tick < 20; tick++) target.aiStep();
                var weak = new LinkedHashMap<>(definition().abilityValues());
                weak.put("attackSpeedMagnitude", .10);
                weak.put("rangeMagnitude", .10);
                cast(caster, definition().withAbilityValues(weak));
                equal(.25, target.activeTimedEffectMagnitude(ELDER_SLOW));
                equal(.20, target.activeTimedEffectMagnitude(ELDER_RANGE));
                equal(80, target.activeTimedEffectTicks(ELDER_SLOW));
                equal(80, target.activeTimedEffectTicks(ELDER_RANGE));
                var strong = new LinkedHashMap<>(definition().abilityValues());
                strong.put("attackSpeedMagnitude", .40);
                strong.put("rangeMagnitude", .40);
                cast(caster, definition().withAbilityValues(strong));
                equal(.40, target.activeTimedEffectMagnitude(ELDER_SLOW));
                equal(baseRange * .60, target.attackRange());
                for (int tick = 0; tick < 99; tick++) target.aiStep();
                equal(1, target.activeTimedEffectTicks(ELDER_SLOW));
                equal(1, target.activeTimedEffectTicks(ELDER_RANGE));
                target.aiStep();
                equal(0, target.activeTimedEffectMagnitude(ELDER_SLOW));
                equal(0, target.activeTimedEffectMagnitude(ELDER_RANGE));
                equal(baseRange, target.attackRange());
                equal(rate, manager.tickrate());
                caster.discard();
                target.discard();
            }
        } finally {
            manager.setTickRate(originalRate);
        }
        context.succeed();
    }

    private static SummonConfig.SummonDefinition definition() {
        return SummonConfig.defaultConfig().summons().get("elder_guardian");
    }

    private static SummonConfig.SummonDefinition custom(double magnitude) {
        var definition = definition();
        var abilities = new LinkedHashMap<>(definition.abilityValues());
        abilities.put("attackSpeedMagnitude", magnitude);
        return definition.withAbilityValues(abilities);
    }

    private static void cast(SemionMonsterEntity caster, SummonConfig.SummonDefinition definition) {
        new ElderGuardianSummon(definition).createAbilityGoals(caster).forEach(goal -> goal.tick());
    }

    private static SemionMonsterEntity monster(GameTestHelper context, int lane) {
        var logical = new Monster("elder_guardian", TeamId.RED, lane,
                Optional.of(UUID.randomUUID()), Optional.of(TeamId.BLUE),
                100, 0, 10, AttackKind.MELEE, "minecraft:elder_guardian", 0);
        var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(logical, null);
        entity.setPos(context.absoluteVec(new Vec3(1, 1, 1)));
        entity.setNoAi(true);
        entity.setNoGravity(true);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static SemionTowerEntity tower(GameTestHelper context, TeamId team, int lane, int x, double resistance) {
        BlockPos position = context.absolutePos(new BlockPos(x, 1, 1));
        var runtime = new TestTower(TestTowerTypes.TEST_DIRECT, UUID.randomUUID(), team, lane,
                GridPosition.from(position)) {
            @Override
            public double incomeDebuffResistance() {
                return resistance;
            }
        };
        var entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(runtime, null);
        entity.setPos(Vec3.atBottomCenterOf(position));
        entity.setNoAi(true);
        entity.setNoGravity(true);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void equal(double expected, double actual) {
        if (Math.abs(expected - actual) > .000001) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
