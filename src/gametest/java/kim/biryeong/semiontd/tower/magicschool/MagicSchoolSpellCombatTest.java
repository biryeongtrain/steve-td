package kim.biryeong.semiontd.tower.magicschool;

import static kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowerIntegrationTest.*;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.vfx.AreaEffectVfxTestHooks;
import kim.biryeong.semiontd.entity.tower.vfx.MagicSchoolSpellVfx;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MagicSchoolSpellCombatTest implements kim.biryeong.semiontd.gametest.RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void disarmAndControlCancelPendingAnimatedMonsterHits(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 0);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            var target = f.monster(2, 10000);
            int[] hits = {0};
            target.setAttackStyle(new kim.biryeong.semiontd.entity.monster.MonsterAttackStyle() {
                @Override public int hitDelayTicks() { return 2; }
                @Override public void hit(SemionMonsterEntity attacker, net.minecraft.world.entity.LivingEntity victim) {
                    hits[0]++;
                }
            });
            target.startAttack(source);
            check(target.hasPendingHit(), "A normal animated attack must wait for its hit tick.");
            hit(wizard, target);
            target.tickCount += 2;
            target.tick();
            check(hits[0] == 0 && !target.hasPendingHit(), "Disarm must cancel a hit already winding up.");
            target.schoolSpells().clearRound();
            target.startAttack(source);
            wizard.selectSpell(MagicSchoolSpell.IMPERIO);
            hit(wizard, target);
            target.tickCount += 2;
            target.tick();
            check(hits[0] == 0 && !target.hasPendingHit(), "Control must cancel the previous tower-directed hit.");
            target.schoolSpells().clearRound();
            target.startAttack(source);
            target.tickCount += 2;
            target.tick();
            check(hits[0] == 1, "Uncontrolled monsters retain their normal delayed attack path.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void disarmAndSharedThreeHitStunHaveSeparateDurations(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var first = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 0);
            var second = f.wizard(MagicSchoolSpell.STUPEFY, 1);
            var target = f.monster(2, 10000);
            hit(first, target);
            requireClose(9976, target.getHealth(), "The default spell must deal 80% magic damage.");
            check(target.isDisarmed() && !target.isStunned(), "Expelliarmus prevents attacks but does not stun movement.");
            var other = f.monster(3, 10000);
            hit(first, other);
            check(!other.isDisarmed(), "Disarm may trigger only once per wizard per round.");
            var entity = first.runtimeEntity(f.lane).orElseThrow();
            target.setPos(entity.getX() + 1, entity.getY(), entity.getZ());
            target.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
            target.setTarget(entity);
            var goal = new MonsterAttackTargetGoal(target, 1);
            double before = entity.getHealth();
            goal.tick();
            requireClose(before, entity.getHealth(), "Disarm must block the real monster attack goal.");
            for (int i = 0; i < 40; i++) target.aiStep();
            check(!target.isDisarmed(), "Disarm expires after forty ticks.");
            goal.tick();
            check(entity.getHealth() < before, "The monster must resume attacking after disarm.");
            first.selectSpell(MagicSchoolSpell.STUPEFY);
            hit(first, other);
            hit(second, other);
            check(!other.isStunned(), "Two combined hits must not stun.");
            hit(first, other);
            check(other.isStunned() && other.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN) == 10,
                    "The third hit from different casters must stun for ten ticks.");
            for (int i = 0; i < 10; i++) other.aiStep();
            check(!other.isStunned(), "Stupefy must expire after half a second.");
            first.resetForRound(f.lane);
            first.selectSpell(MagicSchoolSpell.EXPELLIARMUS);
            hit(first, other);
            check(other.isDisarmed(), "A new round must restore the first-attack disarm.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void splashAndWoundsUseExactSecondaryDamageAndRadius(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.EXPULSO, 0);
            var primary = f.monster(3, 10000);
            var near = f.monster(4, 10000);
            var far = f.monster(5, 10000);
            near.setPos(Math.nextDown(primary.getX() + 1.2), primary.getY(), primary.getZ());
            far.setPos(Math.nextUp(primary.getX() + 1.2), primary.getY(), primary.getZ());
            hit(wizard, primary);
            requireClose(9976, primary.getHealth(), "The primary hit must not receive splash twice.");
            requireClose(9985, near.getHealth(), "Expulso splash must use 50% attack, not the reduced primary hit.");
            requireClose(10000, far.getHealth(), "Expulso must retain its 1.2-block boundary.");
            wizard.selectSpell(MagicSchoolSpell.SECTUMSEMPRA);
            setMonsterHealth(primary, 9000); setMonsterHealth(near, 9000); setMonsterHealth(far, 9000);
            hit(wizard, primary);
            requireClose(8970, primary.getHealth(), "Sectumsempra primary must deal full attack damage.");
            requireClose(8985, near.getHealth(), "Exactly the nearest additional target must take half attack damage.");
            requireClose(9000, far.getHealth(), "The second nearby extra target must be excluded.");
            primary.receiveHealing(100); near.receiveHealing(100);
            requireClose(9005, primary.getHealth(), "The main wound must reduce healing by 65%.");
            requireClose(9020, near.getHealth(), "The secondary wound must reduce healing too.");
            for (int i = 0; i < 100; i++) primary.aiStep();
            primary.receiveHealing(100);
            requireClose(9105, primary.getHealth(), "Wound healing suppression must expire in five seconds.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void bombardaDealsNinetyPercentAndSeventyPercentSplashWithinTwoPointFourBlocks(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.BOMBARDA, 0);
            var primary = f.monster(3, 10000);
            var near = f.monster(4, 10000);
            var edge = f.monster(5, 10000);
            var outside = f.monster(6, 10000);
            near.setPos(primary.position().add(1.6, 0, 0));
            edge.setPos(Math.nextDown(primary.getX() + 2.4), primary.getY(), primary.getZ());
            outside.setPos(primary.position().add(2.41, 0, 0));
            hit(wizard, primary);
            requireClose(9973, primary.getHealth(), "Bombarda deals 90% attack to the primary without duplicate splash.");
            requireClose(9979, near.getHealth(), "The larger splash reaches beyond the former radius with 70% attack.");
            requireClose(9979, edge.getHealth(), "The nearest representable point inside the 2.4-block boundary is included.");
            requireClose(10000, outside.getHealth(), "Enemies beyond 2.4 blocks are excluded.");
            requireClose(69, wizard.roundMagicDamageDealt(), "All three hits must count as magic damage.");
            requireClose(0, wizard.roundPhysicalDamageDealt(), "Bombarda must not deal physical damage.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void lumosChangesMagicDamageAndOverridesHouseTargetOrder(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var caster = f.wizard(MagicSchoolSpell.LUMOS, 0);
            var entity = caster.runtimeEntity(f.lane).orElseThrow();
            check(entity.attackIntervalTicks() == 24, "Lumos must retain the ordinary attack interval.");
            var lit = f.monster(3, 10000);
            var unlit = f.monster(4, 20000);
            unlit.setPos(lit.position().add(2.1, 0, 0));
            var edge = f.monster(5, 10000);
            edge.setPos(lit.position().add(2, 0, 0));
            hit(caster, lit);
            check(edge.isCurrentlyGlowing() && !unlit.isCurrentlyGlowing(), "Lumos spreads to two blocks but not beyond.");
            requireClose(10000, edge.getHealth(), "Spreading Lumos must not deal splash damage.");
            requireClose(9976, lit.getHealth(), "The first Lumos hit applies vulnerability after its 80% damage.");
            check(lit.isCurrentlyGlowing(), "Lumos must glow visibly.");
            caster.damageResolvedTargetResult(entity, lit, 100, DamageType.MAGIC);
            requireClose(9864, lit.getHealth(), "Lumos must amplify incoming magic damage by 12%.");
            caster.damageResolvedTargetResult(entity, lit, 100, DamageType.PHYSICAL);
            requireClose(9764, lit.getHealth(), "Lumos must not amplify physical damage.");
            for (TowerType house : MagicSchoolTowers.houseWizards()) {
                var wizard = (MagicSchoolWizardTower) add(f.lane, house, f.pos(0));
                var source = wizard.runtimeEntity(f.lane).orElseThrow();
                check(wizard.selectForcedAttackTarget(source, List.of(lit, unlit)).orElseThrow() == lit,
                        "All houses must prioritize Lumos before their house-specific ordering.");
                wizard.selectSpell(MagicSchoolSpell.LUMOS);
                check(wizard.selectForcedAttackTarget(source, List.of(lit, unlit)).orElseThrow() == unlit,
                        "Lumos casters must spread the debuff to unmarked targets first.");
                f.lane.removeTower(wizard);
            }
            caster.selectSpell(MagicSchoolSpell.LUMOS_MAXIMA);
            unlit.setPos(lit.position().add(4, 0, 0));
            var outside = f.monster(7, 10000);
            outside.setPos(lit.position().add(4.01, 0, 0));
            hit(caster, lit);
            check(unlit.isCurrentlyGlowing() && !outside.isCurrentlyGlowing(), "Lumos Maxima spreads within four blocks.");
            f.lane.resetForRound();
            check(!lit.isCurrentlyGlowing() && !unlit.isCurrentlyGlowing(), "Lumos must be removed on round reset.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void protectionWaveAggroChangesMonsterTargetingAndClearsAtRoundEnd(GameTestHelper context) {
        for (var spell : List.of(MagicSchoolSpell.PROTEGO, MagicSchoolSpell.PROTEGO_MAXIMA)) {
            try (var f = new Fixture(context)) {
                var protector = f.wizard(spell, 0);
                var ally = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 2);
                var source = protector.runtimeEntity(f.lane).orElseThrow();
                var allyEntity = ally.runtimeEntity(f.lane).orElseThrow();
                var enemy = f.monster(3, 10000);
                var goal = new kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal(enemy);
                goal.start();
                check(enemy.getTarget() == allyEntity, "Equal base aggro must favor the closer wizard before battle.");
                protector.onWaveStarted(f.lane, 1);
                protector.onWaveStarted(f.lane, 1);
                check(source.aggroPriority() == 60 && allyEntity.aggroPriority() == 0,
                        "Protection grants only its caster sixty aggro once per wave.");
                goal.start();
                check(enemy.getTarget() == source, "The real monster targeting goal must prefer the protection caster.");
                protector.selectSpell(MagicSchoolSpell.EXPELLIARMUS);
                check(source.aggroPriority() == 60, "The wave-start bonus must remain until the round ends.");
                protector.resetForRound(f.lane);
                check(protector.runtimeEntity(f.lane).orElseThrow().aggroPriority() == 0, "Round reset must clear the aggro bonus.");
                protector.onWaveStarted(f.lane, 2);
                protector.selectSpell(spell);
                check(protector.aggroPriority() == 0, "A mid-wave spell change must not grant the wave-start bonus.");
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void protectionMultipliesThreeAlliedAurasAndSurvivesSpellChangesUntilRoundEnd(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var protectedWizard = f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, 0);
            var source = protectedWizard.runtimeEntity(f.lane).orElseThrow();
            var ally = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 1);
            for (int i = 0; i < 4; i++) {
                var wizard = i == 0 ? protectedWizard : f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, i);
                wizard.onWaveStarted(f.lane, 1);
            }
            requireClose(.3998375, MagicSchoolSpellCombat.protection(source), "Self 30% and three 5% auras must multiply to 39.98375%, capped at three sources.");
            var recipient = ally.runtimeEntity(f.lane).orElseThrow();
            requireClose(.142625, MagicSchoolSpellCombat.protection(recipient), "An ally receives three independent 5% auras.");
            double before = source.getHealth();
            source.hurt(source.damageSources().generic(), 100);
            requireClose(before - 60.01625, source.getHealth(), "Actual incoming damage must use multiplicative protection.");
            protectedWizard.selectSpell(MagicSchoolSpell.PROTEGO);
            protectedWizard.selectSpell(MagicSchoolSpell.STUPEFY);
            requireClose(.3998375, MagicSchoolSpellCombat.protection(source), "Protection must last through spell changes until round end.");
            f.lane.resetForRound();
            requireClose(0, MagicSchoolSpellCombat.protection(protectedWizard.runtimeEntity(f.lane).orElseThrow()), "Round reset removes all protection.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void patronumGrowsExtraHitsAndRedirectsMissingTargets(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.EXPECTO_PATRONUM, 0);
            var primary = f.monster(3, 10000);
            double before = primary.getHealth();
            for (int extra : new int[]{1, 2, 2, 3}) {
                hit(wizard, primary);
                requireClose(before - 30 - extra * 7.5, primary.getHealth(), "Missing Patronum targets must redirect 25% hits to the primary.");
                before = primary.getHealth();
            }
            wizard.resetForRound(f.lane);
            var secondary = f.monster(4, 10000);
            hit(wizard, primary);
            requireClose(before - 30, primary.getHealth(), "With an extra enemy, the first additional hit must leave the primary.");
            requireClose(9992.5, secondary.getHealth(), "The new round resets Patronum to one 25% additional hit.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void cursesAreExclusivePerOwnerAndCrucioTicksStackOnlyOnDamage(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var crucio = f.wizard(MagicSchoolSpell.CRUCIO, 0);
            var other = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 1);
            check(!other.selectSpell(MagicSchoolSpell.CRUCIO), "The same owner cannot equip a curse twice.");
            var teammate = (MagicSchoolWizardTower) add(f.teammate, MagicSchoolTowers.BRAVE_ARCHWIZARD, f.pos(2));
            check(teammate.selectSpell(MagicSchoolSpell.CRUCIO), "A different owner may independently equip the same curse.");
            check(other.selectSpell(MagicSchoolSpell.IMPERIO), "Different curses may be equipped together.");
            var target = f.monster(3, 10000);
            var source = crucio.runtimeEntity(f.lane).orElseThrow();
            double casterHealth = source.getHealth();
            source.setInvulnerableTime(0);
            source.hurt(target.damageSources().mobAttack(target), 10);
            double originalIncoming = casterHealth - source.getHealth();
            requireClose(10, originalIncoming, "The baseline monster hit must actually damage the caster.");
            hit(crucio, target);
            requireClose(9995.5, target.getHealth(), "Crucio's first hit is 15% attack before adding vulnerability.");
            requireClose(.1, target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_CRUCIO_VULNERABILITY), "First hit adds one vulnerability stack.");
            target.schoolSpells().tick();
            requireClose(9995.5, target.getHealth(), "Crucio must wait two ticks.");
            target.schoolSpells().tick();
            requireClose(9990.55, target.getHealth(), "The first periodic hit uses 15% attack and the existing 10% vulnerability.");
            requireClose(.2, target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_CRUCIO_VULNERABILITY),
                    "A periodic hit must add its own stack to the damaged enemy.");
            for (int tick = 2; tick < 40; tick++) target.schoolSpells().tick();
            requireClose(2, target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_CRUCIO_VULNERABILITY), "Twenty vulnerability stacks cap at 200%.");
            double health = target.getHealth();
            for (int tick = 0; tick < 10; tick++) target.schoolSpells().tick();
            requireClose(health, target.getHealth(), "Periodic damage ends after forty ticks while vulnerability persists.");
            casterHealth = source.getHealth();
            source.setInvulnerableTime(0);
            source.hurt(target.damageSources().mobAttack(target), 10);
            requireClose(originalIncoming, casterHealth - source.getHealth(), "Twenty enemy stacks must not increase damage taken by the caster.");
            requireClose(0, source.activeEffectMagnitude(TimedEffectType.MONSTER_CRUCIO_VULNERABILITY), "The caster must never own the enemy vulnerability.");
            var allySource = other.runtimeEntity(f.lane).orElseThrow();
            var magic = other.damageResolvedTargetResult(allySource, target, 10, DamageType.MAGIC);
            var physical = other.damageResolvedTargetResult(allySource, target, 10, DamageType.PHYSICAL);
            requireClose(30, magic.dealtDamage(), "Other casters' magic must also benefit from the enemy's twenty stacks.");
            requireClose(10, physical.dealtDamage(), "Crucio must not amplify physical damage.");
            crucio.selectSpell(MagicSchoolSpell.EXPELLIARMUS);
            check(other.selectSpell(MagicSchoolSpell.CRUCIO), "Changing away from a curse releases its reservation.");
            f.lane.resetForRound();
            requireClose(0, target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_CRUCIO_VULNERABILITY), "Round reset clears curse vulnerability.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void avadaUsesTargetMaximumHealthAndImperioRedirectsRealMonsterAttacks(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var avada = f.wizard(MagicSchoolSpell.AVADA_KEDAVRA, 0);
            var entity = avada.runtimeEntity(f.lane).orElseThrow();
            check(entity.attackIntervalTicks() == 600, "Avada's 60% penalty and low-proficiency 90% penalty multiply.");
            avada.gainProficiency(500, f.lane);
            check(entity.attackIntervalTicks() == 600, "The penalty still applies at exactly five hundred proficiency.");
            avada.gainProficiency(1, f.lane);
            check(entity.attackIntervalTicks() == 60, "Above five hundred, only Avada's original penalty remains.");
            var victim = MagicSchoolTowerIntegrationTest.monster(context, f.lane, f.pos(3), 900, 300, 1000);
            hit(avada, victim);
            requireClose(500, victim.getHealth(), "Avada uses 200% maximum health before 300 magic resistance reduces it to one quarter.");
            hit(avada, victim);
            check(!victim.isAlive() || victim.getHealth() <= 0, "Avada must continue using maximum health, not remaining health.");
            var imperio = f.wizard(MagicSchoolSpell.IMPERIO, 1);
            var controlled = f.monster(3, 10000);
            var ally = f.monster(4, 10000);
            controlled.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(80);
            hit(imperio, controlled);
            controlled.setTarget(entity);
            double towerHealth = entity.getHealth();
            var goal = new MonsterAttackTargetGoal(controlled, 1);
            goal.tick();
            requireClose(9920, ally.getHealth(), "Imperio redirects the monster's real 100% attack to its ally.");
            requireClose(towerHealth, entity.getHealth(), "A controlled monster must not attack towers.");
            hit(imperio, ally);
            check(ally.schoolSpells().controlled(), "Every hit must control its target without a caster cooldown.");
            for (int tick = 0; tick < 20; tick++) controlled.aiStep();
            hit(imperio, controlled);
            check(controlled.schoolSpells().controlled(), "A repeat hit refreshes control immediately.");
            ally.schoolSpells().clearRound();
            var ordinary = new kim.biryeong.semiontd.tower.ProductionTower(MagicSchoolTowers.FRESHMAN,
                    f.lane.ownerPlayer(), f.lane.teamId(), f.lane.laneId(), f.pos(0));
            f.lane.addTower(ordinary);
            var ordinaryEntity = ordinary.runtimeEntity(f.lane).orElseThrow();
            ordinaryEntity.recordCurrentAttackTarget(controlled);
            new TowerAttackMonsterGoal(ordinaryEntity).tick();
            check(ordinaryEntity.currentAttackTarget() == ally, "Ordinary towers must also deprioritize a cached Imperio target.");
            ally.discard();
            double health = controlled.getHealth();
            new MonsterAttackTargetGoal(controlled, 1).tick();
            requireClose(health - 80, controlled.getHealth(), "When alone, a controlled monster attacks itself.");
            for (int tick = 0; tick < 39; tick++) controlled.aiStep();
            check(controlled.schoolSpells().controlled(), "Control must persist through 39 ticks after refreshing.");
            controlled.aiStep();
            check(!controlled.schoolSpells().controlled(), "Control must expire at exactly 40 ticks.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 140, structure = "semion-td-gametest:combat_arena")
    public void episkeySharesRecipientCooldownAcrossCastersAndHealsAlliedOwners(GameTestHelper context) {
        var f = new Fixture(context);
        try {
            var first = f.wizard(MagicSchoolSpell.EPISKEY, 0);
            var second = f.wizard(MagicSchoolSpell.EPISKEY, 1);
            var ally = (MagicSchoolWizardTower) add(f.teammate, MagicSchoolTowers.FRESHMAN, f.pos(2));
            var recipient = ally.runtimeEntity(f.teammate).orElseThrow();
            recipient.setNoAi(true);
            ally.syncHealth(10); recipient.setHealth(10);
            var target = f.monster(4, 10000);
            hit(first, target);
            requireClose(32.5, recipient.getHealth(), "Episkey must heal a nearby allied owner's wizard by 75% attack.");
            requireClose(9973, target.getHealth(), "Healing must accompany a 90% magic attack.");
            hit(second, target);
            requireClose(32.5, recipient.getHealth(), "A second caster cannot bypass the recipient cooldown.");
            context.runAtTickTime(59, () -> {
                try {
                    hit(second, target);
                    requireClose(32.5, recipient.getHealth(), "Episkey remains blocked before three seconds.");
                } catch (Throwable failure) { f.close(); throw failure; }
            });
            context.runAtTickTime(61, () -> {
                try {
                    hit(second, target);
                    requireClose(55, recipient.getHealth(), "Another caster may heal after the shared three-second cooldown.");
                    context.succeed();
                } finally { f.close(); }
            });
        } catch (Throwable failure) { f.close(); throw failure; }
    }

    @GameTest(maxTicks = 150, structure = "semion-td-gametest:combat_arena")
    public void rennervateCleansesAtStartAndEveryFiveSecondsWithoutStacking(GameTestHelper context) {
        var f = new Fixture(context);
        try {
            var first = f.wizard(MagicSchoolSpell.RENNERVATE, 0);
            var second = f.wizard(MagicSchoolSpell.RENNERVATE, 1);
            var ally = (MagicSchoolWizardTower) add(f.teammate, MagicSchoolTowers.FRESHMAN, f.pos(2));
            var recipient = ally.runtimeEntity(f.teammate).orElseThrow();
            recipient.setNoAi(true);
            recipient.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, .4, 500);
            recipient.applyTimedEffect(TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION, 10, 500);
            recipient.applyTimedEffect(TimedEffectType.TOWER_RANGE_BONUS, .2, 500);
            first.onWaveStarted(f.lane, 1); second.onWaveStarted(f.lane, 1);
            requireClose(0, recipient.activeEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION), "Rennervate must cleanse elder-guardian-style slows.");
            requireClose(0, recipient.activeEffectMagnitude(TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION), "It must cleanse other tower debuffs too.");
            requireClose(.2, recipient.activeEffectMagnitude(TimedEffectType.TOWER_RANGE_BONUS), "Cleansing must preserve buffs.");
            requireClose(.12, recipient.activeEffectMagnitude(TimedEffectType.TOWER_RENNERVATE_DAMAGE_BONUS), "Two casters must refresh, not stack.");
            requireClose(33.6, recipient.attackDamageAmount(null), "Rennervate adds exactly 12% attack.");
            context.runAtTickTime(61, () -> {
                try {
                    requireClose(0, recipient.activeEffectMagnitude(TimedEffectType.TOWER_RENNERVATE_DAMAGE_BONUS), "The buff expires after three seconds.");
                    recipient.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, .4, 500);
                    first.tick(f.lane);
                    requireClose(.4, recipient.activeEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION), "It must wait for its five-second recast.");
                } catch (Throwable failure) { f.close(); throw failure; }
            });
            context.runAtTickTime(101, () -> {
                try {
                    first.tick(f.lane);
                    requireClose(0, recipient.activeEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION), "The five-second pulse must cleanse again.");
                    requireClose(.12, recipient.activeEffectMagnitude(TimedEffectType.TOWER_RENNERVATE_DAMAGE_BONUS), "The recast must restore the buff.");
                    context.succeed();
                } finally { f.close(); }
            });
        } catch (Throwable failure) { f.close(); throw failure; }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void protegoReducesDamageByThirtyPercentAndLockedMuggleDefinitionHasFixedCombatValues(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.PROTEGO, 0);
            wizard.onWaveStarted(f.lane, 1);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            double health = source.getHealth();
            source.hurt(source.damageSources().generic(), 100);
            requireClose(health - 70, source.getHealth(), "Protego alone must reduce received damage by thirty percent.");
            var target = f.monster(3, 10000);
            double attack = source.attackDamageAmount(target);
            hit(wizard, target);
            requireClose(10000 - attack * .5, target.getHealth(), "Protego still deals a 50% magic basic attack.");
            check(!wizard.selectSpell(MagicSchoolSpell.MUGGLE_WAND), "The Muggle spell must remain locked through the public API.");
            f.lane.assignAugmentSnapshot(MagicSchoolAugmentCombatTest.snapshot(
                    MagicSchoolAugments.MUGGLE_WAND, MagicSchoolAugments.UNFORGIVABLE_CURSES));
            check(wizard.selectSpell(MagicSchoolSpell.MUGGLE_WAND), "Silver must unlock the spell through the public API.");
            source.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 3, 100);
            source.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, .5, 100);
            source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, 2, 100);
            check(source.attackIntervalTicks() == 20, "The reserved Muggle interval must ignore all speed changes.");
            double before = target.getHealth();
            hit(wizard, target);
            requireClose(before - 80, target.getHealth(), "The reserved Muggle hit must remain fixed at 80 despite damage buffs.");
            check(wizard.primaryDamageType() == DamageType.PHYSICAL, "The reserved Muggle damage must be physical.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void protegoThirtyPercentStillMultipliesWithOrdinaryReductionAndThreeMaximaAuras(GameTestHelper context) {
        for (int scenario = 0; scenario < 3; scenario++) {
            try (var f = new Fixture(context)) {
                var wizard = f.wizard(MagicSchoolSpell.PROTEGO, 0);
                wizard.onWaveStarted(f.lane, 1);
                var source = wizard.runtimeEntity(f.lane).orElseThrow();
                if (scenario >= 1) source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, .2, 200);
                if (scenario == 2) {
                    for (int i = 1; i <= 3; i++) f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, i).onWaveStarted(f.lane, 1);
                    requireClose(.3998375, MagicSchoolSpellCombat.protection(source), "Self 30% and three 5% auras must keep multiplying.");
                }
                double before = source.getHealth();
                source.hurt(source.damageSources().generic(), 100);
                double expected = new double[]{70, 56, 48.013}[scenario];
                requireClose(expected, before - source.getHealth(), "Actual Protego combination " + scenario);
                System.out.println("PROTEGO_30 scenario=" + scenario + " damage=" + (before - source.getHealth()));
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void leviosaDealsMagicAndLiftsOnlyEnemiesInsideSixBlocksForTwentyFourTicks(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.WINGARDIUM_LEVIOSA, 0);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            var target = f.monster(1, 10000);
            var edge = f.monster(6, 10000);
            var outside = f.monster(7, 10000);
            var foreign = f.monster(2, 10000);
            target.setPos(source.position().add(1, 0, 0));
            edge.setPos(source.position().add(6, 0, 0));
            outside.setPos(source.position().add(6.01, 0, 0));
            foreign.setPos(source.position().add(2, 0, 0));
            var runtime = new kim.biryeong.semiontd.entity.monster.Monster("foreign", f.teammate.teamId(), f.teammate.laneId(),
                    java.util.Optional.empty(), java.util.Optional.empty(), 10000, 0, 0,
                    kim.biryeong.semiontd.config.AttackKind.MELEE, "minecraft:zombie", 0);
            foreign.configureFrom(runtime, f.teammate.laneLayout());
            hit(wizard, target);
            requireClose(9974.5, target.getHealth(), "Leviosa's basic attack must deal 85% of thirty attack as magic damage.");
            check(wizard.primaryDamageType() == DamageType.MAGIC, "Leviosa must remain magic damage.");
            check(!target.isStunned(), "The ordinary attack must not cause the periodic stun.");
            MagicSchoolSpellCombat.leviosa(source);
            requireClose(9967, target.getHealth(), "The pulse adds 25% of thirty attack as magic damage.");
            requireClose(9992.5, edge.getHealth(), "The target at six blocks must receive pulse damage too.");
            requireClose(10000, outside.getHealth(), "Out-of-range monsters must not take pulse damage.");
            requireClose(10000, foreign.getHealth(), "Other-lane monsters must not take pulse damage.");
            requireClose(40.5, wizard.roundMagicDamageDealt(), "Both pulse hits and the basic attack must count as magic damage.");
            for (var enemy : List.of(target, edge)) {
                check(enemy.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN) == 24, "Every in-range enemy must be stunned for 1.2 seconds.");
                requireClose(.8, enemy.getDeltaMovement().y, "The stun must launch the enemy upward.");
            }
            check(!outside.isStunned() && !foreign.isStunned(), "Outside and other-lane enemies must not be affected.");
            target.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
            target.setTarget(source);
            var goal = new MonsterAttackTargetGoal(target, 1);
            double before = source.getHealth();
            goal.tick();
            requireClose(before, source.getHealth(), "Leviosa must block actual monster attacks.");
            double y = edge.getY();
            edge.travel(net.minecraft.world.phys.Vec3.ZERO);
            check(edge.getY() > y, "Stun must allow the forced upward motion to move the enemy.");
            for (int i = 0; i < 23; i++) target.aiStep();
            check(target.isStunned(), "The stun must still be active after twenty-three ticks.");
            target.aiStep();
            check(!target.isStunned(), "The stun must expire on its twenty-fourth tick.");
            target.setPos(source.position().add(1, 0, 0));
            goal.tick();
            check(source.getHealth() < before, "The enemy must attack again after the stun expires.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 330, structure = "semion-td-gametest:combat_arena")
    public void leviosaWaitsFiveSecondsRepeatsAndPreservesCadenceAcrossSpellChangesAndPromotion(GameTestHelper context) {
        var f = new Fixture(context);
        try {
            var wizard = f.wizard(MagicSchoolSpell.WINGARDIUM_LEVIOSA, 0);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            source.setNoGravity(true);
            var target = f.monster(2, 10000);
            target.setPos(source.position().add(2, 0, 0));
            wizard.onWaveStarted(f.lane, 1);
            wizard.tick(f.lane);
            check(!target.isStunned(), "Leviosa must not fire at battle start.");
            var current = new MagicSchoolWizardTower[]{wizard};
            context.runAtTickTime(99, () -> {
                try {
                    wizard.tick(f.lane);
                    check(!target.isStunned(), "The first pulse must wait five seconds.");
                } catch (Throwable error) { f.close(); throw error; }
            });
            context.runAtTickTime(101, () -> {
                try {
                    wizard.tick(f.lane);
                    check(target.isStunned(), "The first five-second pulse must fire without a basic attack.");
                    for (int i = 0; i < 24; i++) target.aiStep();
                    target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    target.setPos(source.position().add(2, 0, 0));
                    wizard.selectSpell(MagicSchoolSpell.STUPEFY);
                    wizard.selectSpell(MagicSchoolSpell.WINGARDIUM_LEVIOSA);
                    wizard.onWaveStarted(f.lane, 1);
                    wizard.tick(f.lane);
                    check(!target.isStunned(), "Switching and repeated wave start cannot create an extra pulse.");
                    var promoted = new HouseWizardTower(MagicSchoolTowers.BRAVE_ARCHWIZARD,
                            f.lane.ownerPlayer(), f.lane.teamId(), f.lane.laneId(), f.pos(0), f.pos(0));
                    promoted.copyFrom(wizard, 450);
                    f.lane.replaceTower(wizard, promoted);
                    var promotedEntity = promoted.runtimeEntity(f.lane).orElseThrow();
                    promotedEntity.setNoAi(true);
                    promotedEntity.setNoGravity(true);
                    current[0] = promoted;
                } catch (Throwable error) { f.close(); throw error; }
            });
            context.runAtTickTime(199, () -> {
                try {
                    current[0].tick(f.lane);
                    check(!target.isStunned(), "The second pulse cannot fire before ten seconds.");
                } catch (Throwable error) { f.close(); throw error; }
            });
            context.runAtTickTime(201, () -> {
                try {
                    current[0].tick(f.lane);
                    check(target.isStunned(), "The second pulse must keep the original cadence after replacement.");
                    for (int i = 0; i < 24; i++) target.aiStep();
                    target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    target.setPos(current[0].runtimeEntity(f.lane).orElseThrow().position().add(2, 0, 0));
                    current[0].resetForRound(f.lane);
                } catch (Throwable error) { f.close(); throw error; }
            });
            context.runAtTickTime(301, () -> {
                try {
                    current[0].tick(f.lane);
                    check(!target.isStunned(), "Round reset must stop further pulses during preparation.");
                    context.succeed();
                } finally { f.close(); }
            });
        } catch (Throwable error) { f.close(); throw error; }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void leviosaPulseUsesBuffsResistanceAndKillAttribution(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.WINGARDIUM_LEVIOSA, 0);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .5, 100);
            var target = MagicSchoolTowerIntegrationTest.monster(context, f.lane, f.pos(2), 900, 100, 1000);
            var doomed = f.monster(3, 5);
            MagicSchoolSpellCombat.leviosa(source);
            requireClose(1000 - 30 * 1.5 * .25 / 2, target.getHealth(), "Pulse damage must use attack buffs once and magic resistance, not armor.");
            check(!doomed.isAlive() || doomed.isRemoved(), "A lethal lift pulse must kill its target.");
            requireClose(30 * 1.5 * .25 / 2 + 5, wizard.roundMagicDamageDealt(), "Pulse kills must retain damage attribution.");
            requireClose(0, wizard.roundPhysicalDamageDealt(), "The lift must never become physical damage.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void spellAttacksAndSpecialEffectsCaptureTheCorrectSpellAndTarget(GameTestHelper context) {
        var observed = new java.util.ArrayList<MagicSchoolSpellVfx.Visual>();
        AreaEffectVfxTestHooks.setMagicSchoolObserver(observed::add);
        try (var f = new Fixture(context)) {
            f.lane.assignAugmentSnapshot(MagicSchoolAugmentCombatTest.snapshot(
                    MagicSchoolAugments.MUGGLE_WAND, MagicSchoolAugments.UNFORGIVABLE_CURSES));
            for (var spell : MagicSchoolSpell.values()) {
                var wizard = f.wizard(spell, 0);
                var source = wizard.runtimeEntity(f.lane).orElseThrow();
                var target = f.monster(3, 100000);
                source.recordCurrentAttackTarget(target);
                observed.clear();
                new TowerAttackMonsterGoal(source).tick();
                check(observed.stream().anyMatch(v -> v.spell() == spell && v.kind() == MagicSchoolSpellVfx.Kind.ATTACK),
                        "The real basic attack must emit " + spell + " dust.");
                if (spell == MagicSchoolSpell.EXPULSO || spell == MagicSchoolSpell.BOMBARDA) {
                    check(observed.stream().anyMatch(v -> v.spell() == spell && v.kind() == MagicSchoolSpellVfx.Kind.AREA
                            && v.radius() == spell.value("radius")), "Splash radius must remain visible without secondary victims.");
                }
                if (spell == MagicSchoolSpell.SECTUMSEMPRA) check(observed.stream().anyMatch(v -> v.kind() == MagicSchoolSpellVfx.Kind.WOUND),
                        "Sectumsempra must sweep its primary victim.");
                target.discard();
                f.lane.removeTower(wizard);
            }
            var healer = f.wizard(MagicSchoolSpell.EPISKEY, 0);
            var patient = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 1);
            var recipient = patient.runtimeEntity(f.lane).orElseThrow();
            patient.syncHealth(10); recipient.setHealth(10);
            observed.clear();
            hit(healer, f.monster(3, 10000));
            var heart = observed.stream().filter(v -> v.kind() == MagicSchoolSpellVfx.Kind.HEAL).findFirst().orElseThrow();
            check(heart.center().distanceTo(recipient.position().add(0, recipient.getBbHeight() + .3, 0)) < .001,
                    "Healing hearts belong above the recipient's head.");
            observed.clear();
            hit(healer, f.monster(4, 10000));
            check(observed.stream().noneMatch(v -> v.kind() == MagicSchoolSpellVfx.Kind.HEAL), "Blocked healing must not emit a heart.");
            var leviosa = f.wizard(MagicSchoolSpell.WINGARDIUM_LEVIOSA, 2);
            MagicSchoolSpellCombat.leviosa(leviosa.runtimeEntity(f.lane).orElseThrow());
            check(observed.stream().anyMatch(v -> v.spell() == MagicSchoolSpell.WINGARDIUM_LEVIOSA
                    && v.kind() == MagicSchoolSpellVfx.Kind.AREA && v.radius() == 6), "Lift must show its actual six-block range.");
            var protector = f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, -1);
            protector.onWaveStarted(f.lane, 1);
            check(observed.stream().anyMatch(v -> v.spell() == MagicSchoolSpell.PROTEGO_MAXIMA
                    && v.kind() == MagicSchoolSpellVfx.Kind.AREA && v.radius() == 6), "Maxima must show its six-block protection range.");
        } finally { AreaEffectVfxTestHooks.setMagicSchoolObserver(null); }
        context.succeed();
    }

    @GameTest(maxTicks = 80, structure = "semion-td-gametest:combat_arena")
    public void persistentSpellVisualsFollowTargetsAndStopOnExpiryAndReset(GameTestHelper context) {
        var f = new Fixture(context);
        var observed = new java.util.ArrayList<MagicSchoolSpellVfx.Visual>();
        try {
            var protector = f.wizard(MagicSchoolSpell.PROTEGO, 0);
            var crucio = f.wizard(MagicSchoolSpell.CRUCIO, 1);
            var imperio = f.wizard(MagicSchoolSpell.IMPERIO, 2);
            var cursed = f.monster(4, 100000);
            var controlled = f.monster(5, 100000);
            protector.onWaveStarted(f.lane, 1);

            AreaEffectVfxTestHooks.setMagicSchoolObserver(observed::add);
            protector.tick(f.lane);
            protector.tick(f.lane);
            check(observed.stream().filter(v -> v.kind() == MagicSchoolSpellVfx.Kind.SHIELD).count() == 1,
                    "Duplicate ticks must not emit duplicate protection rings.");
            hit(crucio, cursed); hit(imperio, controlled);
            cursed.schoolSpells().tick(); controlled.schoolSpells().tick();
            check(observed.stream().anyMatch(v -> v.kind() == MagicSchoolSpellVfx.Kind.DOT
                    && Math.abs(v.center().x - cursed.getX()) < .001), "Crucio particles belong on the victim.");
            check(observed.stream().anyMatch(v -> v.kind() == MagicSchoolSpellVfx.Kind.CONTROL
                    && Math.abs(v.center().x - controlled.getX()) < .001), "Control particles belong on the victim.");
            observed.clear();
            cursed.schoolSpells().tick(); controlled.schoolSpells().tick();
            check(observed.stream().noneMatch(v -> v.kind() == MagicSchoolSpellVfx.Kind.DOT || v.kind() == MagicSchoolSpellVfx.Kind.CONTROL),
                    "Repeated ticks must not flood persistent particles.");
            AreaEffectVfxTestHooks.setMagicSchoolObserver(null);
            context.runAfterDelay(9, () -> {
                try {
                    observed.clear();
                    AreaEffectVfxTestHooks.setMagicSchoolObserver(observed::add);
                    protector.tick(f.lane);
                    check(observed.stream().noneMatch(v -> v.kind() == MagicSchoolSpellVfx.Kind.SHIELD), "Shield cannot pulse before ten ticks.");
                } catch (Throwable error) { f.close(); throw error; }
                finally { AreaEffectVfxTestHooks.setMagicSchoolObserver(null); }
            });
            context.runAfterDelay(10, () -> {
                try {
                    observed.clear();
                    AreaEffectVfxTestHooks.setMagicSchoolObserver(observed::add);
                    protector.tick(f.lane);
                    check(observed.stream().filter(v -> v.kind() == MagicSchoolSpellVfx.Kind.SHIELD).count() == 1, "Shield must pulse every ten ticks.");
                } catch (Throwable error) { f.close(); throw error; }
                finally { AreaEffectVfxTestHooks.setMagicSchoolObserver(null); }
            });
            context.runAfterDelay(41, () -> {
                try {
                    observed.clear();
                    AreaEffectVfxTestHooks.setMagicSchoolObserver(observed::add);
                    protector.resetForRound(f.lane);
                    protector.tick(f.lane);
                    cursed.schoolSpells().tick(); controlled.schoolSpells().tick();
                    check(observed.isEmpty(), "Expired curses and reset protection must stop visual emissions.");
                    hit(crucio, cursed); hit(imperio, controlled);
                    cursed.schoolSpells().clearRound(); controlled.schoolSpells().clearRound();
                    observed.clear();
                    cursed.schoolSpells().tick(); controlled.schoolSpells().tick();
                    check(observed.isEmpty(), "Round cleanup must remove persistent visual sources immediately.");
                    context.succeed();
                } finally { AreaEffectVfxTestHooks.setMagicSchoolObserver(null); f.close(); }
            });
        } catch (Throwable error) { AreaEffectVfxTestHooks.setMagicSchoolObserver(null); f.close(); throw error; }
    }

    private static void hit(MagicSchoolWizardTower wizard, SemionMonsterEntity target) {
        var source = wizard.runtimeEntity(wizard.attachedLane()).orElseThrow();
        target.setInvulnerableTime(0);
        double attack = source.attackDamageAmount(target);
        var result = wizard.damagePrimaryAttackTargetResult(source, target, attack);
        source.recordAttack(target, attack, result.secondaryOutgoingDamage(), result.dealtDamage(), result.killed());
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void schoolProtectionMultipliesWithGeneralReductionWithoutChangingGeneralStacking(GameTestHelper context) {
        for (int scenario = 0; scenario < 5; scenario++) {
            try (var f = new Fixture(context)) {
                var wizard = f.wizard(scenario == 4 ? MagicSchoolSpell.EXPELLIARMUS : MagicSchoolSpell.PROTEGO_MAXIMA, 0);
                var entity = wizard.runtimeEntity(f.lane).orElseThrow();
                if (scenario != 4) {
                    wizard.onWaveStarted(f.lane, 1);
                    for (int i = 1; i <= 3; i++) f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, i).onWaveStarted(f.lane, 1);
                    if (scenario == 2 || scenario == 3) entity.setPersistentEffect(TimedEffectType.TOWER_PROTEGO,
                            MagicSchoolSpellCombat.id("protego_self"), .50);
                }
                if (scenario == 1 || scenario >= 3) entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION,
                        MagicSchoolSpellCombat.id("review_general_a"), .30, 2);
                if (scenario == 4) entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION,
                        MagicSchoolSpellCombat.id("review_general_b"), .20, 200);
                double expected = new double[]{60.01625, 42.011375, 42.86875, 30.008125, 50}[scenario];
                double before = entity.getHealth();
                entity.hurt(entity.damageSources().generic(), 100);
                requireClose(expected, before - entity.getHealth(), "Actual protection case " + scenario);
                System.out.println("SCHOOL_PROTECTION case=" + scenario + " damage=" + (before - entity.getHealth()));
                if (scenario == 1) {
                    entity.tick();
                    entity.tick();
                    requireClose(0, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "The ordinary buff must expire.");
                    before = entity.getHealth();
                    entity.setInvulnerableTime(0);
                    entity.hurt(entity.damageSources().generic(), 100);
                    requireClose(60.01625, before - entity.getHealth(), "The school protection survives expiration of an unrelated buff.");
                }
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void schoolProtectionKeepsOneAuraWhenCasterEntityIsReplaced(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, 0);
            var ally = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 1);
            wizard.onWaveStarted(f.lane, 1);
            var target = ally.runtimeEntity(f.lane).orElseThrow();
            requireClose(.05, MagicSchoolSpellCombat.protection(target), "One caster supplies one aura.");
            var originalEntity = wizard.runtimeEntity(f.lane).orElseThrow();
            var replacement = new HouseWizardTower(wizard.type(), wizard.ownerPlayer(), wizard.teamId(),
                    wizard.laneId(), wizard.originalPosition(), wizard.position());
            replacement.copyFrom(wizard, 0);
            f.lane.replaceTower(wizard, replacement);
            var nextEntity = replacement.runtimeEntity(f.lane).orElseThrow();
            check(!originalEntity.getUUID().equals(nextEntity.getUUID()), "Replacement must create a different entity.");
            check(wizard.logicalId().equals(replacement.logicalId()), "The logical caster identity must survive replacement.");
            MagicSchoolSpellCombat.onWaveStarted(replacement, nextEntity);
            MagicSchoolSpellCombat.onWaveStarted(replacement, nextEntity);
            requireClose(.05, MagicSchoolSpellCombat.protection(target), "Replacement and repeated callbacks cannot add another aura.");
            double before = target.getHealth();
            target.hurt(target.damageSources().generic(), 100);
            requireClose(95, before - target.getHealth(), "The duplicate-source rule must hold for actual damage.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void schoolProtectionHandlesZeroImmunityAndReductionIgnoringDamage(GameTestHelper context) {
        for (int scenario = 0; scenario < 4; scenario++) {
            try (var f = new Fixture(context)) {
                var wizard = f.wizard(MagicSchoolSpell.EXPELLIARMUS, 0);
                var entity = wizard.runtimeEntity(f.lane).orElseThrow();
                entity.setPersistentEffect(scenario == 2 ? TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA : TimedEffectType.TOWER_PROTEGO,
                        MagicSchoolSpellCombat.id("review_limit"), scenario == 0 ? 0 : 1);
                double before = entity.getHealth();
                if (scenario == 3) entity.hurtIgnoringReductions(entity.damageSources().generic(), 100);
                else entity.hurt(entity.damageSources().generic(), 100);
                requireClose(scenario == 0 || scenario == 3 ? 100 : 0, before - entity.getHealth(),
                        "Zero, immunity and reduction-ignoring damage retain their exact rules: " + scenario);
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void schoolProtectionPreservesPhysicalMagicAndReductionIgnoringDamageRules(GameTestHelper context) {
        for (int scenario = 0; scenario < 3; scenario++) {
            try (var f = new Fixture(context)) {
                var wizard = f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, 0);
                wizard.onWaveStarted(f.lane, 1);
                for (int i = 1; i <= 3; i++) f.wizard(MagicSchoolSpell.PROTEGO_MAXIMA, i).onWaveStarted(f.lane, 1);
                var entity = wizard.runtimeEntity(f.lane).orElseThrow();
                entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, .30, 200);
                var attacker = f.monster(5, 10000);
                var damageSource = scenario == 0 ? entity.damageSources().mobAttack(attacker) : entity.damageSources().magic();
                double before = entity.getHealth();
                if (scenario == 2) entity.hurtIgnoringReductions(damageSource, 100);
                else entity.hurt(damageSource, 100);
                requireClose(scenario == 2 ? 100 : 42.011375, before - entity.getHealth(),
                        "Physical and magic hits use protection while reduction-ignoring damage bypasses it: " + scenario);
            }
        }
        context.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper context;
        final SemionGame game;
        final PlayerLane lane;
        final PlayerLane teammate;
        final GridPosition anchor;
        Fixture(GameTestHelper context) {
            this.context = context;
            UUID owner = UUID.randomUUID();
            UUID friend = UUID.randomUUID();
            game = game(context, owner, friend);
            lane = game.playerLane(owner).orElseThrow();
            teammate = game.playerLane(friend).orElseThrow();
            anchor = GridPosition.from(net.minecraft.core.BlockPos.containing(lane.laneLayout().positionAt(.3)));
            for (UUID player : List.of(owner, friend)) {
                var economy = new PlayerEconomy(EconomyConfig.defaultConfig());
                economy.addMineral(100000);
                MagicSchoolCurriculum.purchase(player, MagicSchoolCurriculum.Upgrade.ADVANCED_SPELLS, economy);
                for (int tier = 2; tier <= 5; tier++) MagicSchoolCurriculum.unlockSpellTier(player, tier, economy);
                game.playerLane(player).orElseThrow().assignAugmentSnapshot(MagicSchoolAugmentCombatTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES));
            }
        }
        GridPosition pos(int offset) { return new GridPosition(anchor.x() + offset, anchor.y(), anchor.z()); }
        MagicSchoolWizardTower wizard(MagicSchoolSpell spell, int offset) {

            var type = TowerType.builder(MagicSchoolTowers.BRAVE_ARCHWIZARD.id(), "주문 검증용 대마법사")
                    .maxHealth(200).damage(30).range(8).attackIntervalTicks(24)
                    .visual(MagicSchoolTowers.FRESHMAN.visual()).primaryDamageType(DamageType.MAGIC).build();
            var wizard = new HouseWizardTower(type, lane.ownerPlayer(), lane.teamId(), lane.laneId(), pos(offset), pos(offset));
            lane.addTower(wizard);
            check(wizard.selectSpell(spell), "The fixture must equip " + spell);
            wizard.runtimeEntity(lane).orElseThrow().setNoAi(true);
            return wizard;
        }
        SemionMonsterEntity monster(int offset, double health) { return MagicSchoolTowerIntegrationTest.monster(context, lane, pos(offset), 0, 0, health); }
        @Override public void close() { game.close(); }
    }
}
