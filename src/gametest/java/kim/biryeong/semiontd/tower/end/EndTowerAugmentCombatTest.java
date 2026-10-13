package kim.biryeong.semiontd.tower.end;

import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.tower.TowerCoreAugmentFixture;

public final class EndTowerAugmentCombatTest extends TowerCoreAugmentFixture {
    @GameTest
    public void voidMineSurvivesCoreDeathAndUsesEightTargetCap(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.MINE)) {
            EndTower core = fixture.end(EndTowers.BASE_END_TOWER);
            core.onWaveStarted(fixture.lane, 5);
            core.tick(fixture.lane);
            EndAugments effects = new EndAugments();
            effects.onTransferCompleted(core, core, 100);
            effects.tickMines(core, fixture.lane);
            Vec3 center = new Vec3(core.position().x() + .5, core.position().y() + 1, core.position().z() + .5);
            for (int i = 0; i < 9; i++) {fixture.target(center.add(.1 * i, 0, .5), 1000);}
            fixture.lane.killTower(core);
            effects.tickMines(core, fixture.lane);
            requireClose(800, core.roundMagicDamageDealt(), "A stored mine survives the core's combat death and hits eight targets.");
            require(effects.mineCount() == 0, "The mine is consumed once.");
            effects.tickMines(core, fixture.lane);
            requireClose(800, core.roundMagicDamageDealt(), "A consumed mine cannot explode twice.");
            context.succeed();
        }
    }

    @GameTest
    public void growthHitsNativeSecondaryTargetsOnceAndExtraAttacksCannotConsumeItsCharge(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.GROWTH)) {
            EndTower core = fixture.end(EndTowers.BASE_END_TOWER);
            for (int offset = 1; offset <= 4; offset++) {
                fixture.add(new EndTower(EndTowers.T3_END_CRYSTAL_TOWER, fixture.owner, TeamId.RED, 1,
                        fixture.position(offset)));
            }
            core.onWaveStarted(fixture.lane, 5);
            int ticks = (int) Math.ceil(EndConfig.RUNTIME.transfer().durationTicks() * core.transferDurationMultiplier());
            for (int tick = 0; tick < ticks; tick++) {core.tick(fixture.lane);}
            require(core.transferStats().endCrystalCount() == 12, "Four native crystal transfers must unlock splash.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("전달 4기 / 추가 피해 충전")),
                    "Three completed transfers must leave one charge for the next ordinary attack.");
            SemionTowerEntity source = fixture.entity(core);
            SemionMonsterEntity primary = fixture.target(source.position().add(1, 0, 0), 1000);
            SemionMonsterEntity secondary = fixture.target(primary.position().add(0, 0, core.splashRadius() * .5), 1000);
            SemionMonsterEntity outside = fixture.target(primary.position().add(0, 0, core.splashRadius() + .25), 1000);
            double attackDamage = source.attackDamageAmount(primary);
            double nativeDamage = core.resolveBasicAttackOutgoingDamage(source, primary, attackDamage);
            double splashDamage = nativeDamage * EndConfig.RUNTIME.splash().damageRatio();
            double growthDamage = core.resolveBasicAttackOutgoingDamage(source, primary, attackDamage * 1.5);

            AugmentCombat.additionalAttack(source, primary, 1);
            requireClose(nativeDamage, 1000 - primary.runtimeMonster().health(), "An extra attack retains its native primary damage.");
            requireClose(splashDamage, 1000 - secondary.runtimeMonster().health(), "An extra attack retains native splash targeting.");
            requireClose(0, core.roundMagicDamageDealt(), "An augment extra attack must not trigger growth magic damage.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("추가 피해 충전")),
                    "An augment extra attack must preserve the pending growth charge.");

            attack(source, primary);
            requireClose(nativeDamage * 2 + growthDamage, 1000 - primary.runtimeMonster().health(),
                    "The ordinary primary target must take exactly 150% extra magic damage.");
            requireClose(splashDamage * 2 + growthDamage, 1000 - secondary.runtimeMonster().health(),
                    "The native secondary target must receive the same 150% extra magic damage.");
            requireClose(growthDamage * 2, core.roundMagicDamageDealt(), "Growth damage must be attributed as magic for both native targets.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("추가 피해 대기")),
                    "The ordinary attack must consume the stored charge.");

            attack(source, primary);
            requireClose(nativeDamage * 3 + growthDamage, 1000 - primary.runtimeMonster().health(), "The consumed charge cannot hit the primary again.");
            requireClose(splashDamage * 3 + growthDamage, 1000 - secondary.runtimeMonster().health(), "The consumed charge cannot hit the secondary again.");
            requireClose(growthDamage * 2, core.roundMagicDamageDealt(), "A consumed charge cannot produce more magic damage.");
            requireClose(1000, outside.runtimeMonster().health(), "Growth cannot expand the native secondary target set.");
            context.succeed();
        }
    }

    @GameTest
    public void breathFiresTwiceFourTicksApartAndReselectsInsideRange(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.BREATH)) {
            TowerType base = EndTowers.BASE_END_TOWER;
            TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                    1_000_000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                    base.description(), base.visual(), base.upgradeOptions());
            EndTower core = fixture.end(giant);
            core.onWaveStarted(fixture.lane, 5);
            core.tick(fixture.lane);
            require(core.state() == EndTowerState.DRAGON, "The large core evolves before the breath timer.");
            SemionTowerEntity source = fixture.entity(core);
            SemionMonsterEntity first = fixture.target(source.position().add(2, 0, 0), 1000);
            source.recordCurrentAttackTarget(first);
            for (int i = 1; i < 120; i++) {core.tick(fixture.lane);}
            double firstShot = core.roundMagicDamageDealt();
            require(firstShot > 0, "The first breath fires at six seconds.");
            first.setPos(source.position().add(100, 0, 0));
            SemionMonsterEntity replacement = fixture.target(source.position().add(0, 0, 2), 1000);
            for (int i = 0; i < 3; i++) {core.tick(fixture.lane);}
            requireClose(firstShot, core.roundMagicDamageDealt(), "The second breath waits four ticks.");
            core.tick(fixture.lane);
            require(replacement.runtimeMonster().health() < 1000, "The second shot reselects a target inside native range.");
            requireClose(firstShot * 2, core.roundMagicDamageDealt(), "Exactly two equal breaths fired.");
            core.tick(fixture.lane);
            requireClose(firstShot * 2, core.roundMagicDamageDealt(), "The card name does not create a third breath.");
            context.succeed();
        }
    }

    private static void attack(SemionTowerEntity source, SemionMonsterEntity target) {
        double damage = source.attackDamageAmount(target);
        var result = source.damageTargetResult(target, damage);
        source.recordAttack(target, damage, result.outgoingDamage(), result.dealtDamage(), result.killed());
    }

    @GameTest
    public void dragonLifeStealUsesActualOverkillAndSeparateSplashDamage(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context)) {
            TowerType base = EndTowers.BASE_END_TOWER;
            TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                    2000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                    base.description(), base.visual(), base.upgradeOptions());
            EndTower core = fixture.end(giant);
            for (int i = 0; i < 10; i++) {
                fixture.add(new EndTower(EndTowers.T3_SHULKER_TOWER, fixture.owner, TeamId.RED, 1, fixture.position(1)));
            }
            for (int i = 0; i < 4; i++) {
                fixture.add(new EndTower(EndTowers.T3_END_CRYSTAL_TOWER, fixture.owner, TeamId.RED, 1, fixture.position(2)));
            }
            core.onWaveStarted(fixture.lane, 5);
            for (int i = 0; i < EndConfig.RUNTIME.transfer().durationTicks(); i++) {core.tick(fixture.lane);}
            require(core.state() == EndTowerState.DRAGON, "The fixture starts with enough health to evolve into a dragon.");
            require(core.transferStats().shulkerCount() == 30, "Thirty shulker stacks still grant the first life-steal step.");
            SemionTowerEntity source = fixture.entity(core);
            SemionMonsterEntity primary = fixture.target(source.position().add(1, 0, 0), 30);
            SemionMonsterEntity secondary = fixture.target(primary.position().add(0, 0, .1), 5000);
            source.setHealth(10);
            core.syncHealth(10);
            var result = core.damageResolvedTargetResult(source, primary, 300,
                    kim.biryeong.semiontd.entity.monster.DamageType.PHYSICAL);
            source.recordAttack(primary, 300, result.outgoingDamage(), result.dealtDamage(), result.killed());
            requireClose(30, result.dealtDamage(), "Overkill is limited to actual health lost.");
            requireClose(198, 5000 - secondary.runtimeMonster().health(), "Splash keeps its resolved outgoing damage.");
            requireClose(14.98, core.health(), "The primary heals 3 from actual 30 damage; splash heals 1.98 using the 300 outgoing snapshot.");
            for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                core.onAttackResolved(source, secondary, 0, 0, invalid, false);
            }
            requireClose(14.98, core.health(), "Invalid hits cannot heal the dragon.");
            source.setHealth((float) (core.currentMaxHealth() - .05));
            core.syncHealth(source.getHealth());
            core.onAttackResolved(source, secondary, 30, 0, 30, false);
            requireClose(core.currentMaxHealth(), core.health(), "The dragon cannot overheal.");
            require(core.transferStats().shulkerCount() == 30, "Healing never consumes progression stacks.");
            requireClose(kim.biryeong.semiontd.ui.SemionDialogService.currentTowerPrimaryDamage(core, source),
                    core.lifeStealDisplayDamage(), "The efficiency preview uses the same target-free damage as the detail dialog.");
            context.succeed();
        }
    }


    @GameTest
    public void sixStageDragonReportsPrimaryAndEachSplashHealingSeparately(GameTestHelper context) {
        for (int scenario = 0; scenario <= 6; scenario++) {
            int splashTargets = scenario <= 2 ? scenario : 1;
            try (Fixture fixture = new Fixture(context)) {
                EndTower core = fixture.end(EndTowers.BASE_END_TOWER);
                for (int i = 0; i < 60; i++) {
                    fixture.add(new EndTower(EndTowers.T3_SHULKER_TOWER, fixture.owner, TeamId.RED, 1, fixture.position(1)));
                }
                for (int i = 0; i < 4; i++) {
                    fixture.add(new EndTower(EndTowers.T3_END_CRYSTAL_TOWER, fixture.owner, TeamId.RED, 1, fixture.position(2)));
                }
                core.onWaveStarted(fixture.lane, 5);
                for (int i = 0; i < EndConfig.RUNTIME.transfer().durationTicks(); i++) {core.tick(fixture.lane);}
                require(core.transferStats().shulkerCount() == 180, "Six existing stages require 180 shulker stacks.");
                require(core.state() == EndTowerState.DRAGON, "The transferred core is a dragon.");
                SemionTowerEntity source = fixture.entity(core);
                SemionMonsterEntity primary = fixture.target(source.position().add(1, 0, 0), scenario == 3 ? 10 : 10000);
                if (scenario == 4 || scenario == 5) {
                    primary.applyTimedEffect(kim.biryeong.semiontd.effect.TimedEffectType.MONSTER_DAMAGE_REDUCTION,
                            scenario == 4 ? 1 : .5, 1000);
                }
                java.util.List<SemionMonsterEntity> secondaries = new java.util.ArrayList<>();
                for (int i = 0; i < splashTargets; i++) {
                    secondaries.add(fixture.target(primary.position().add(0, 0, .1 * (i + 1)), 10000));
                }
                if (scenario == 6) {
                    secondaries.getFirst().applyTimedEffect(
                            kim.biryeong.semiontd.effect.TimedEffectType.MONSTER_DAMAGE_REDUCTION, .5, 1000);
                }
                source.setHealth(100);
                core.syncHealth(100);
                require(core.currentMaxHealth() > 1000, "The heal must not be hidden by the health cap.");
                var result = core.damageResolvedTargetResult(source, primary, 250,
                        kim.biryeong.semiontd.entity.monster.DamageType.PHYSICAL);
                source.recordAttack(primary, 250, result.outgoingDamage(), result.dealtDamage(), result.killed());
                double expectedPrimaryDamage = scenario == 3 ? 10 : scenario == 4 ? 0 : scenario == 5 ? 125 : 250;
                double expectedPrimaryHealing = scenario == 3 ? 6 : scenario == 4 ? 0 : 18;
                double expectedSplashDamage = scenario == 4 ? 0 : scenario == 6 ? 82.5 : 165;
                double expectedSplashHealing = scenario == 4 ? 0 : scenario == 6 ? 5.94 : 11.88;
                requireClose(expectedPrimaryDamage, result.dealtDamage(), "Primary damage honors health and reductions.");
                for (SemionMonsterEntity secondary : secondaries) {
                    requireClose(expectedSplashDamage, 10000 - secondary.runtimeMonster().health(), "Each splash deals 66% actual damage.");
                }
                double actualHealing = core.health() - 100;
                requireClose(expectedPrimaryHealing + expectedSplashHealing * splashTargets, actualHealing,
                        "Splash healing uses its actual damage and the original outgoing attack snapshot.");
                System.out.println("END_LIFESTEAL_SPLASH_VERIFY scenario=" + scenario
                        + " primaryDamage=" + result.dealtDamage()
                        + " primaryHealing=" + expectedPrimaryHealing + " splashTargets=" + splashTargets
                        + " splashDamageEach=" + expectedSplashDamage + " actualHealing=" + actualHealing
                        + " measuredSplashHealingEach=" + (splashTargets == 0 ? 0 : (actualHealing - expectedPrimaryHealing) / splashTargets));
            }
        }
        context.succeed();
    }

}
