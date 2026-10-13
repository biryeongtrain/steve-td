package kim.biryeong.semiontd.tower.futureagency;

import java.util.List;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCoreAugmentFixture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

public final class FutureAgencyCrossfireTest extends TowerCoreAugmentFixture {
    private static final String CROSSFIRE = "job_future_agency_towers_s";
    private static final String RESCUE = "job_future_agency_towers_g2";

    @GameTest
    public void primaryAndSuppressionSplashShareCrossfireWithoutRepeatingDamage(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, CROSSFIRE)) {
            try {
                List<FutureAgencyAgentTower> agents = agents(fixture, FutureAgencyRole.SUPPRESSION, 1);
                Vec3 position = fixture.entity(agents.getFirst()).position().add(0, 0, 1);
                SemionMonsterEntity first = target(fixture, position);
                SemionMonsterEntity second = target(fixture, position.add(.5, 0, 0));
                SemionMonsterEntity splash = target(fixture, position.add(1, 0, 0));
                SemionMonsterEntity outside = target(fixture, position.add(4, 0, 0));
                SemionMonsterEntity removed = target(fixture, position.add(.75, 0, 0));
                removed.discard();
                hit(fixture, agents.getFirst(), first);
                requireClose(496, splash.runtimeMonster().health(), "Original splash must deal damage once.");
                requireClose(0, splash.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "One side cannot root.");
                hit(fixture, agents.getLast(), second);
                requireClose(492, splash.runtimeMonster().health(), "Survivor splash must not recursively repeat damage.");
                requireClose(486, first.runtimeMonster().health(), "A primary followed by splash must deal each hit once.");
                requireClose(486, second.runtimeMonster().health(), "Splash followed by a primary must deal each hit once.");
                for (SemionMonsterEntity enemy : List.of(first, second, splash)) {
                    requireClose(1, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Both hit paths must root.");
                    require(enemy.activeTimedEffectTicks(TimedEffectType.MONSTER_ROOT) == 40, "Root must last forty ticks.");
                }
                requireClose(500, outside.runtimeMonster().health(), "Out-of-range target must not receive splash.");
                requireClose(0, outside.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Out-of-range target must not root.");
                requireClose(500, removed.runtimeMonster().health(), "Removed target must not receive splash.");
                requireClose(0, removed.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Removed target must not root.");
                context.succeed();
            } finally {FutureAgencyStates.clear(fixture.owner);}
        }
    }

    @GameTest
    public void splashRequiresDamageEnabledTriggersAndTheAugment(GameTestHelper context) {
        for (int scenario = 0; scenario < 3; scenario++) {
            try (Fixture fixture = new Fixture(context, scenario == 0 ? new String[0] : new String[]{CROSSFIRE})) {
                try {
                    List<FutureAgencyAgentTower> agents = agents(fixture, FutureAgencyRole.SUPPRESSION, 1);
                    Vec3 position = fixture.entity(agents.getFirst()).position().add(0, 0, 1);
                    SemionMonsterEntity first = target(fixture, position);
                    SemionMonsterEntity second = target(fixture, position.add(.5, 0, 0));
                    SemionMonsterEntity splash = target(fixture, position.add(1, 0, 0));
                    hit(fixture, agents.getFirst(), first);
                    if (scenario == 1) {
                        AugmentCombat.runWithoutTriggers(() -> hit(fixture, agents.getLast(), second));
                    } else {
                        if (scenario == 2) splash.applyTimedEffect(TimedEffectType.MONSTER_DAMAGE_REDUCTION, 1, 40);
                        hit(fixture, agents.getLast(), second);
                    }
                    requireClose(scenario == 2 ? 496 : 492, splash.runtimeMonster().health(), "Fixture must exercise actual splash damage or immunity.");
                    requireClose(0, splash.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Ineligible splash must not root, scenario " + scenario);
                } finally {FutureAgencyStates.clear(fixture.owner);}
            }
        }
        context.succeed();
    }

    @GameTest
    public void differentOriginalsAndZeroDamageCannotCompleteThePair(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, CROSSFIRE)) {
            try {
                List<FutureAgencyAgentTower> agents = agents(fixture, FutureAgencyRole.COMBAT, 1);
                FutureAgencyAgentTower unrelated = new FutureAgencyAgentTower(FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 5),
                        fixture.owner, TeamId.RED, 1, fixture.position(4), fixture.position(4));
                fixture.add(unrelated);
                unrelated.onWaveStarted(fixture.lane, 2);
                SemionMonsterEntity enemy = target(fixture, fixture.entity(agents.getFirst()).position().add(0, 0, 1));
                hit(fixture, agents.getFirst(), enemy);
                hit(fixture, unrelated, enemy);
                agents.getLast().onAttackResolved(fixture.entity(agents.getLast()), enemy, 10, 10, 0, false);
                agents.getLast().onAttackResolved(fixture.entity(agents.getLast()), null, 10, 10, 10, false);
                requireClose(0, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Unlinked originals and zero-damage hits must not complete a pair.");
                hit(fixture, agents.getLast(), enemy);
                requireClose(1, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "The linked survivor's valid hit must still root.");
                context.succeed();
            } finally {FutureAgencyStates.clear(fixture.owner);}
        }
    }

    @GameTest(maxTicks = 240)
    public void timeWindowAndSharedCooldownKeepTheirInclusiveBoundaries(GameTestHelper context) {
        Fixture fixture = new Fixture(context, CROSSFIRE, RESCUE);
        List<FutureAgencyAgentTower> agents = agents(fixture, FutureAgencyRole.SUPPRESSION, 2);
        Vec3 position = fixture.entity(agents.getFirst()).position().add(0, 0, 1);
        SemionMonsterEntity first = target(fixture, position);
        SemionMonsterEntity second = target(fixture, position.add(.5, 0, 0));
        SemionMonsterEntity splash = target(fixture, position.add(1, 0, 0));
        SemionMonsterEntity expired = target(fixture, position.add(4, 0, 0));
        hit(fixture, agents.getFirst(), first);
        hit(fixture, agents.getFirst(), expired);
        long started = context.getLevel().getGameTime();
        context.startSequence()
                .thenIdle(40)
                .thenExecute(() -> check(context, fixture, () -> {
                    require(context.getLevel().getGameTime() - started == 40, "Check the exact forty-tick boundary.");
                    hit(fixture, agents.get(1), second);
                    requireClose(1, splash.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Splash hits exactly forty ticks apart must root.");
                }))
                .thenIdle(1)
                .thenExecute(() -> check(context, fixture, () -> {
                    hit(fixture, agents.get(2), expired);
                    requireClose(0, expired.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Forty-one ticks exceeds the hit window.");
                    int remaining = splash.activeTimedEffectTicks(TimedEffectType.MONSTER_ROOT);
                    hit(fixture, agents.get(2), second);
                    requireClose(remaining, splash.activeTimedEffectTicks(TimedEffectType.MONSTER_ROOT), "Another survivor must not refresh the shared root.");
                }))
                .thenIdle(158)
                .thenExecute(() -> check(context, fixture, () -> {
                    require(context.getLevel().getGameTime() - started == 199, "Check one tick before cooldown expiry.");
                    requireClose(0, splash.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "The original forty-tick root must have expired.");
                    hit(fixture, agents.getFirst(), first);
                    hit(fixture, agents.get(2), second);
                    requireClose(0, splash.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "All linked survivors share the full 160-tick cooldown.");
                }))
                .thenIdle(1)
                .thenExecute(() -> check(context, fixture, () -> {
                    hit(fixture, agents.getFirst(), first);
                    requireClose(1, splash.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ROOT), "Crossfire must become available at exactly 160 ticks.");
                    requireClose(40, splash.activeTimedEffectTicks(TimedEffectType.MONSTER_ROOT), "The next root starts its own forty ticks.");
                    fixture.close();
                    FutureAgencyStates.clear(fixture.owner);
                }))
                .thenSucceed();
    }

    private static List<FutureAgencyAgentTower> agents(Fixture fixture, FutureAgencyRole role, int survivors) {
        FutureAgencyAgentTower original = new FutureAgencyAgentTower(FutureAgencyTowers.agent(role, 5),
                fixture.owner, TeamId.RED, 1, fixture.position(0), fixture.position(0));
        fixture.add(original);
        for (int round = 1; round <= survivors; round++) {
            fixture.lane.markWaveStarted(round);
            for (Tower tower : List.copyOf(fixture.lane.towers())) tower.onLaneCleared(fixture.lane);
            fixture.lane.resetForRound();
        }
        fixture.lane.markWaveStarted(survivors + 1);
        List<FutureAgencyAgentTower> agents = fixture.lane.towers().stream()
                .filter(FutureAgencyAgentTower.class::isInstance).map(FutureAgencyAgentTower.class::cast).toList();
        require(agents.size() == survivors + 1, "Fixture must include its original and linked survivors.");
        for (FutureAgencyAgentTower agent : agents) {
            fixture.entity(agent).setNoAi(true);
            fixture.entity(agent).setNoGravity(true);
        }
        return agents;
    }

    private static SemionMonsterEntity target(Fixture fixture, Vec3 position) {
        SemionMonsterEntity target = fixture.target(position, 500);
        target.setNoGravity(true);
        return target;
    }

    private static void hit(Fixture fixture, FutureAgencyAgentTower agent, SemionMonsterEntity target) {
        var entity = fixture.entity(agent);
        var damage = agent.damagePrimaryAttackTargetResult(entity, target, 10);
        entity.recordAttack(target, 10, damage.secondaryOutgoingDamage(), damage.dealtDamage(), damage.killed());
    }

    private static void check(GameTestHelper context, Fixture fixture, Runnable assertion) {
        try {assertion.run();}
        catch (RuntimeException | Error failure) {
            fixture.close();
            FutureAgencyStates.clear(fixture.owner);
            context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
        }
    }
}
