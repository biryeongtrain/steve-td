package kim.biryeong.semiontd.tower.warlock;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.warlock.WarlockSacrificeTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.tower.TowerCoreAugmentFixture;

public final class WarlockTowerAugmentCombatTest extends TowerCoreAugmentFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void explosiveSacrificeDoesNotBoostAnyPermanentGrowthButKeepsExplosion(GameTestHelper context) {
        var types = new kim.biryeong.semiontd.tower.TowerType[]{WarlockTowers.BASE_WARLOCK_TOWER,
                WarlockTowers.RANGED_WARLOCK_TOWER, WarlockTowers.MELEE_WARLOCK_TOWER};
        double[] healthRatios = {.02, .04, .07};
        double[] damageRatios = {.02, .07, .04};
        double[] roundRatios = {0, .50, .60};
        for (int path = 0; path < types.length; path++) {
            for (int variant = 0; variant < 3; variant++) {
                try (Fixture fixture = new Fixture(context, variant == 0 ? new String[]{} : new String[]{WarlockAugments.EXPLOSIVE})) {
                    if (variant == 2) {
                        var config = kim.biryeong.semiontd.augment.AugmentConfig.fromJson(
                                com.google.gson.JsonParser.parseString("""
                                        {"parameters":{"job_warlock_towers_g1":{"growthBonus":9}}}
                                        """).getAsJsonObject());
                        fixture.lane.assignAugmentSnapshot(new kim.biryeong.semiontd.augment.AugmentSnapshot(config,
                                java.util.List.of(new kim.biryeong.semiontd.augment.PlayerAugmentState.Selection(5,
                                        kim.biryeong.semiontd.augment.AugmentRarity.GOLD, WarlockAugments.EXPLOSIVE,
                                        kim.biryeong.semiontd.augment.PlayerAugmentState.Outcome.SELECTED, null,
                                        kim.biryeong.semiontd.augment.AugmentChoice.none()))));
                    }
                    WarlockTower core = new WarlockTower(types[path], fixture.owner, TeamId.RED, 1, fixture.position(0));
                    fixture.add(core);
                    WarlockSacrificeTower donor = new WarlockSacrificeTower(WarlockTowers.T1_SLAVE,
                            fixture.owner, TeamId.RED, 1, fixture.position(2));
                    fixture.add(donor);
                    double donatedHealth = donor.currentMaxHealth();
                    double donatedDamage = donor.sacrificeAttackDamage();
                    var center = fixture.entity(donor).position();
                    var victims = new java.util.ArrayList<SemionMonsterEntity>();
                    for (int index = 0; index < 13; index++) {
                        victims.add(fixture.target(center.add(0, 0, .1 + index * .1), 1000));
                    }
                    var outside = fixture.target(center.add(0, 0, 3.01), 1000);
                    SemionTowerEntity source = fixture.entity(core);
                    source.setHealth(2);
                    core.syncHealth(2);
                    source.applyTransferredDamage(path == 0 ? 2 : 1);
                    require(donor.health() == 0, "The real damage hook must commit one sacrifice for path " + path
                            + " and augment variant " + variant + ".");
                    require(source.isAlive() && core.health() > 0, "Absorption must heal the triggering damage.");
                    requireClose(donatedHealth * (healthRatios[path] + roundRatios[path]), core.rawHealthBonus(),
                            "Explosive sacrifice never changes permanent or round health growth.");
                    requireClose(donatedDamage * (damageRatios[path] + roundRatios[path]), core.rawDamageBonus(),
                            "Explosive sacrifice never changes permanent or round damage growth.");
                    requireClose(variant == 0 ? 0 : 12 * donatedHealth, core.roundMagicDamageDealt(),
                            "Explosion preserves donor-health damage and the twelve-target cap.");
                    requireClose(0, core.roundPhysicalDamageDealt(), "Explosion introduces no physical damage.");
                    for (int index = 0; index < 13; index++) {
                        requireClose(variant > 0 && index < 12 ? 1000 - donatedHealth : 1000,
                                victims.get(index).runtimeMonster().health(), "Only the nearest twelve targets receive one explosion.");
                    }
                    requireClose(1000, outside.runtimeMonster().health(), "The three-block explosion radius is preserved.");
                    core.resetForRound(fixture.lane);
                    requireClose(donatedHealth * healthRatios[path], core.rawHealthBonus(),
                            "Only unchanged base permanent health remains next round.");
                    requireClose(donatedDamage * damageRatios[path], core.rawDamageBonus(),
                            "Only unchanged base permanent damage remains next round.");
                }
            }
        }
        context.succeed();
    }

    @GameTest
    public void awakeningVfxChargesOnceThenBurstsAndStopsOnResetOrDeath(GameTestHelper context) {
        for (var type : new kim.biryeong.semiontd.tower.TowerType[] {
                WarlockTowers.RANGED_WARLOCK_TOWER, WarlockTowers.MELEE_WARLOCK_TOWER}) {
            try (Fixture fixture = new Fixture(context, "job_warlock_towers_g2")) {
                WarlockTower core = new WarlockTower(type, fixture.owner, TeamId.RED, 1, fixture.position(0));
                fixture.add(core);
                SemionTowerEntity source = fixture.entity(core);
                java.util.List<String> phases = new java.util.ArrayList<>();
                kim.biryeong.semiontd.entity.tower.vfx.WarlockAwakeningVfxTestHooks.setObserver((id, phase) -> {
                    if (id.equals(source.getUUID())) phases.add(phase);
                });
                try {
                    source.setHealth(1);
                    core.syncHealth(1);
                    core.onDamaged(source, context.getLevel().damageSources().generic(), 1, 2, 1);
                    core.onDamaged(source, context.getLevel().damageSources().generic(), 0, source.getHealth(), source.getHealth());
                    require(phases.equals(java.util.List.of("CHARGE")), "Repeated activation cannot duplicate the charge.");
                    for (int tick = 1; tick <= 15; tick++) core.tick(fixture.lane);
                    require(phases.equals(java.util.List.of("CHARGE", "CHARGE", "CHARGE", "CHARGE")),
                            "Absorption must condense before the skull burst.");
                    core.tick(fixture.lane);
                    require(phases.getLast().equals("BURST"), "The skull burst occurs at tick 16.");
                    for (int tick = 17; tick <= 40; tick++) core.tick(fixture.lane);
                    require(java.util.Collections.frequency(phases, "BURST") == 1, "There is only one burst per awakening.");
                    require(java.util.Collections.frequency(phases, "AURA") == 3
                            && java.util.Collections.frequency(phases, "SPARK") == 2, "Afterglow uses the reduced cadence.");
                    core.resetForRound(fixture.lane);
                    phases.clear();
                    for (int tick = 0; tick < 40; tick++) core.tick(fixture.lane);
                    require(phases.isEmpty() && !source.isCurrentlyGlowing(), "Round reset stops all new awakening VFX.");
                    source.setHealth(1);
                    core.syncHealth(1);
                    core.onDamaged(source, context.getLevel().damageSources().generic(), 1, 2, 1);
                    phases.clear();
                    source.setHealth(0);
                    core.syncHealth(0);
                    for (int tick = 0; tick < 40; tick++) core.tick(fixture.lane);
                    require(phases.isEmpty(), "A dead warlock cannot complete or repeat awakening VFX.");
                } finally {
                    kim.biryeong.semiontd.entity.tower.vfx.WarlockAwakeningVfxTestHooks.setObserver(null);
                }
            }
        }
        context.succeed();
    }

    @GameTest
    public void warlockAbsorptionFiresOnceAndSharesOnlyGrowth(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, "job_warlock_towers_s", "job_warlock_towers_g1", "job_warlock_towers_p")) {
            WarlockTower core = fixture.warlock(0);
            WarlockTower partner = fixture.warlock(1);
            WarlockSacrificeTower donor = new WarlockSacrificeTower(WarlockTowers.T1_RANGED_SLAVE,
                    fixture.owner, TeamId.RED, 1, fixture.position(2));
            fixture.add(donor);
            double donatedHealth = donor.currentMaxHealth();
            double donatedDamage = donor.sacrificeAttackDamage();
            SemionMonsterEntity target = fixture.target(fixture.entity(donor).position().add(0, 0, .5), 1000);
            SemionTowerEntity source = fixture.entity(core);
            source.setHealth(1);
            core.syncHealth(1);
            core.onDamaged(source, context.getLevel().damageSources().generic(), 1, 2, 1);
            require(donor.health() == 0, "The sacrifice is committed once.");
            requireClose(donatedDamage * 3, core.roundPhysicalDamageDealt(), "Testament uses the donor's physical damage type.");
            requireClose(donatedHealth, core.roundMagicDamageDealt(), "Only the absorbing core explodes the sacrifice.");
            requireClose(1000 - donatedDamage * 3 - donatedHealth, target.runtimeMonster().health(), "Both effects damage the target once.");
            requireClose(core.currentMaxHealth(), partner.currentMaxHealth(), "The partner receives the committed growth.");
            requireClose(0, partner.roundDamageDealt(), "Sharing never repeats testament or explosion.");
            context.succeed();
        }
    }

    @GameTest
    public void trueAwakeningUnlocksAtSixtyPercentWithAnotherCoreAlive(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, "job_warlock_towers_g2")) {
            WarlockTower core = fixture.warlock(0);
            fixture.warlock(1);
            SemionTowerEntity source = fixture.entity(core);
            source.setHealth((float) (core.currentMaxHealth() * .60));
            core.syncHealth(source.getHealth());
            core.onDamaged(source, context.getLevel().damageSources().generic(), 1,
                    core.currentMaxHealth(), source.getHealth());
            requireClose(.20, core.finalDamageBonus(), "Awakening is unlocked without kills and ignores the living ally.");
            core.resetForRound(fixture.lane);
            requireClose(0, core.finalDamageBonus(), "The awakening damage bonus ends with the round.");
            context.succeed();
        }
    }

    @GameTest
    public void lifeStealScalesEachActualHitWhileAlliesRemainAlive(GameTestHelper context) {
        for (var type : new kim.biryeong.semiontd.tower.TowerType[] {
                WarlockTowers.RANGED_WARLOCK_TOWER, WarlockTowers.MELEE_WARLOCK_TOWER}) {
            try (Fixture fixture = new Fixture(context)) {
                WarlockTower core = new WarlockTower(type, fixture.owner, TeamId.RED, 1, fixture.position(0));
                fixture.add(core);
                SemionTowerEntity source = fixture.entity(core);
                int sacrifices = core.path() == WarlockPath.RANGED ? 10 : 3;
                for (int i = 0; i < sacrifices; i++) {
                    WarlockSacrificeTower donor = new WarlockSacrificeTower(WarlockTowers.T1_SLAVE,
                            fixture.owner, TeamId.RED, 1, fixture.position(2));
                    fixture.add(donor);
                    source.setHealth(1);
                    core.syncHealth(1);
                    core.onDamaged(source, context.getLevel().damageSources().generic(), 1, 2, 1);
                    require(donor.health() == 0, "Each sacrifice completes through the existing damage hook.");
                }
                WarlockSacrificeTower remaining = new WarlockSacrificeTower(WarlockTowers.T1_SLAVE,
                        fixture.owner, TeamId.RED, 1, fixture.position(3));
                fixture.add(remaining);
                require(!core.isLastSurvivingTower(fixture.lane), "A living donor still awaits absorption.");
                SemionMonsterEntity primary = fixture.target(source.position().add(1, 0, 0), 5000);
                SemionMonsterEntity secondary = fixture.target(primary.position().add(0, 0, .1), 5000);
                source.setHealth(10);
                core.syncHealth(10);
                var result = core.damageResolvedTargetResult(source, primary, 300,
                        kim.biryeong.semiontd.entity.monster.DamageType.PHYSICAL);
                source.recordAttack(primary, 300, result.outgoingDamage(), result.dealtDamage(), result.killed());
                double expected = core.path() == WarlockPath.RANGED ? 3.0 : 21.0;
                requireClose(300, result.dealtDamage(), "The primary hit uses its resolved damage.");
                requireClose(core.path() == WarlockPath.RANGED ? 150 : 225,
                        5000 - secondary.runtimeMonster().health(), "The secondary retains its own splash ratio.");
                requireClose(10 + expected, core.health(), "Primary and splash share the outgoing reference for splash efficiency.");
                require(remaining.health() > 0, "Healing does not consume the remaining ally.");
                int count = core.progressionSnapshot().lifeStealSacrificeCount(core.path());
                require(count == sacrifices, "Efficiency does not alter sacrifice progression.");
                for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                    core.onAttackResolved(source, primary, 0, 0, invalid, false);
                }
                requireClose(10 + expected, core.health(), "Invalid or zero damage grants no healing.");
                source.setHealth((float) (core.currentMaxHealth() - .05));
                core.syncHealth(source.getHealth());
                core.onAttackResolved(source, primary, 300, 0, 300, false);
                requireClose(core.currentMaxHealth(), core.health(), "Life steal never overheals.");
            }
        }
        context.succeed();
    }


    @GameTest
    public void maximumLifeStealKeepsSplashAttenuationAndActualDamageLimits(GameTestHelper context) {
        for (var type : new kim.biryeong.semiontd.tower.TowerType[] {
                WarlockTowers.RANGED_WARLOCK_TOWER, WarlockTowers.MELEE_WARLOCK_TOWER}) {
            for (int scenario = 0; scenario <= 8; scenario++) {
                try (Fixture fixture = new Fixture(context)) {
                    WarlockTower core = new WarlockTower(type, fixture.owner, TeamId.RED, 1, fixture.position(0));
                    fixture.add(core);
                    SemionTowerEntity source = fixture.entity(core);
                    boolean ranged = core.path() == WarlockPath.RANGED;
                    int sacrifices = ranged ? 140 : 12;
                    for (int i = 0; i < sacrifices; i++) {
                        WarlockSacrificeTower donor = new WarlockSacrificeTower(WarlockTowers.T1_SLAVE,
                                fixture.owner, TeamId.RED, 1, fixture.position(2));
                        fixture.add(donor);
                        source.setHealth(1);
                        core.syncHealth(1);
                        core.onDamaged(source, context.getLevel().damageSources().generic(), 1, 2, 1);
                        require(donor.health() == 0, "The sacrifice is committed through the production hook.");
                    }
                    WarlockSacrificeTower remaining = new WarlockSacrificeTower(WarlockTowers.T1_SLAVE,
                            fixture.owner, TeamId.RED, 1, fixture.position(3));
                    fixture.add(remaining);
                    require(!core.isLastSurvivingTower(fixture.lane), "A living ally must not block life steal.");
                    require(core.progressionSnapshot().lifeStealSacrificeCount(core.path()) == sacrifices,
                            "The original stack counts grant maximum life steal.");
                    requireClose(kim.biryeong.semiontd.ui.SemionDialogService.currentTowerPrimaryDamage(core, source),
                            core.lifeStealDisplayDamage(), "The preview uses the detail dialog attack damage.");
                    int splashTargets = scenario <= 2 ? scenario : 1;
                    SemionMonsterEntity primary = fixture.target(source.position().add(1, 0, 0), scenario == 3 ? 10 : 10000);
                    if (scenario == 4 || scenario == 5) {
                        primary.applyTimedEffect(kim.biryeong.semiontd.effect.TimedEffectType.MONSTER_DAMAGE_REDUCTION,
                                scenario == 4 ? 1 : .5, 1000);
                    }
                    java.util.List<SemionMonsterEntity> secondaries = new java.util.ArrayList<>();
                    for (int i = 0; i < splashTargets; i++) {
                        secondaries.add(fixture.target(primary.position().add(0, 0, .1 * (i + 1)), scenario == 7 ? 10 : 10000));
                    }
                    if (scenario == 6 || scenario == 8) {
                        secondaries.getFirst().applyTimedEffect(kim.biryeong.semiontd.effect.TimedEffectType.MONSTER_DAMAGE_REDUCTION,
                                scenario == 8 ? 1 : .5, 1000);
                    }
                    source.setHealth(10);
                    core.syncHealth(10);
                    require(core.currentMaxHealth() > 150, "The test must not hide healing behind the health cap.");
                    var result = core.damageResolvedTargetResult(source, primary, 400,
                            kim.biryeong.semiontd.entity.monster.DamageType.PHYSICAL);
                    source.recordAttack(primary, 400, result.outgoingDamage(), result.dealtDamage(), result.killed());
                    double primaryDamage = scenario == 3 ? 10 : scenario == 4 ? 0 : scenario == 5 ? 200 : 400;
                    double primaryHealing = scenario == 3 ? (ranged ? 7 : 12) : scenario == 4 ? 0 : ranged ? 28 : 48;
                    double splashDamage = scenario == 4 || scenario == 8 ? 0 : scenario == 7 ? 10 : (ranged ? 200 : 300) * (scenario == 6 ? .5 : 1);
                    double splashHealing = scenario == 4 || scenario == 8 ? 0 : scenario == 7 ? (ranged ? .7 : 1.2) : (ranged ? 14 : 36) * (scenario == 6 ? .5 : 1);
                    requireClose(primaryDamage, result.dealtDamage(), "Primary damage respects remaining health and defense.");
                    for (SemionMonsterEntity secondary : secondaries) {
                        requireClose(splashDamage, (scenario == 7 ? 10 : 10000) - secondary.runtimeMonster().health(),
                                "The existing ranged 50% and melee 75% splash ratios remain intact.");
                    }
                    double actualHealing = core.health() - 10;
                    requireClose(primaryHealing + splashHealing * splashTargets, actualHealing,
                            "Primary and each splash use their requested efficiency reference.");
                    require(remaining.health() > 0, "Healing leaves the living ally intact.");
                    System.out.println("WARLOCK_LIFESTEAL_VERIFY path=" + core.path() + " scenario=" + scenario
                            + " primaryDamage=" + result.dealtDamage() + " primaryHealing=" + primaryHealing
                            + " splashTargets=" + splashTargets + " splashDamageEach=" + splashDamage
                            + " actualHealing=" + actualHealing + " splashHealingEach=" + splashHealing);
                    source.setHealth((float) (core.currentMaxHealth() - .05));
                    core.syncHealth(source.getHealth());
                    core.onAttackResolved(source, primary, 400, 0, 400, false);
                    requireClose(core.currentMaxHealth(), core.health(), "Actual healing cannot exceed maximum health.");
                }
            }
        }
        context.succeed();
    }
}
