package kim.biryeong.semiontd.tower.magicschool;

import static kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowerIntegrationTest.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum.Upgrade;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class MagicSchoolCurriculumCombatTest implements kim.biryeong.semiontd.gametest.RuntimeArenaFixture {
    @GameTest(maxTicks = 100, structure = "semion-td-gametest:combat_arena")
    public void transferUsesActualDamageFiveTargetsAndSeventyTickCooldown(GameTestHelper context) {
        var f = new Fixture(context);
        try {
            var first = f.wizard(0);
            var second = f.wizard(1);
            f.buy(Upgrade.SPELL_TRANSFER);
            var primary = f.monster(2, 10000);
            primary.schoolSpells().lumos();
            var others = new ArrayList<SemionMonsterEntity>();
            for (int i = 0; i < 9; i++) {
                var enemy = f.monster(3 + i, 1000);
                enemy.schoolSpells().lumos();
                others.add(enemy);
            }
            var unlit = f.monster(2, 10000);
            var foreign = f.monster(3, 10000);
            foreign.schoolSpells().lumos();

            var otherLane = f.game.playerLane(f.friend).orElseThrow();
            var foreignRuntime = new kim.biryeong.semiontd.entity.monster.Monster("foreign", otherLane.teamId(), otherLane.laneId(),
                    java.util.Optional.empty(), java.util.Optional.empty(), 10000, 0, 0,
                    kim.biryeong.semiontd.config.AttackKind.MELEE, "minecraft:zombie", 0);
            foreign.configureFrom(foreignRuntime, otherLane.laneLayout());
            var source = first.runtimeEntity(f.lane).orElseThrow();
            var result = first.damageResolvedTargetResult(source, primary, 100, DamageType.MAGIC);
            requireClose(112, result.dealtDamage(), "The trigger must use actual damage after Lumos.");
            for (int i = 0; i < 9; i++) {
                requireClose(i < 5 ? 987.456 : 1000, others.get(i).getHealth(), "Transfer must hit exactly five eligible enemies.");
            }
            requireClose(10000, unlit.getHealth(), "Unlit targets are excluded.");
            requireClose(10000, foreign.getHealth(), "Another lane is excluded even at the same position.");
            first.damageResolvedTargetResult(source, primary, 100, DamageType.MAGIC);
            requireClose(987.456, others.getFirst().getHealth(), "Another simultaneous hit must not transfer again.");
            second.damageResolvedTargetResult(second.runtimeEntity(f.lane).orElseThrow(), primary, 100, DamageType.MAGIC);
            requireClose(974.912, others.getFirst().getHealth(), "Each student has an independent cooldown.");
            var promoted = (MagicSchoolWizardTower) kim.biryeong.semiontd.tower.ProductionTowerCatalog.find(MagicSchoolTowers.GRYFFINDOR.id())
                    .orElseThrow().create(f.owner, f.lane.teamId(), f.lane.laneId(), f.pos(1));
            promoted.copyFrom(second, 200);
            f.lane.replaceTower(second, promoted);
            var promotedSource = promoted.runtimeEntity(f.lane).orElseThrow();
            promotedSource.setNoAi(true);
            promotedSource.setNoGravity(true);
            promoted.damageResolvedTargetResult(promotedSource, primary, 100, DamageType.MAGIC);
            requireClose(974.912, others.getFirst().getHealth(), "Promotion must preserve the transfer cooldown.");
            context.runAtTickTime(69, () -> {
                try {
                    first.damageResolvedTargetResult(source, primary, 100, DamageType.MAGIC);
                    requireClose(974.912, others.getFirst().getHealth(), "Transfer must remain blocked before seventy ticks.");
                } catch (Throwable error) { f.close(); throw error; }
            });
            context.runAtTickTime(71, () -> {
                try {
                    first.damageResolvedTargetResult(source, primary, 100, DamageType.MAGIC);
                    requireClose(962.368, others.getFirst().getHealth(), "Transfer must recover after seventy ticks.");
                    context.succeed();
                } finally { f.close(); }
            });
        } catch (Throwable error) { f.close(); throw error; }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void transferFiresOnSplashAndPeriodicDamageWithoutRecursiveTransfers(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(0);
            f.buy(Upgrade.SPELL_TRANSFER);
            var primary = f.monster(2, 10000);
            var litSplash = f.monster(3, 10000);
            var litFar = f.monster(6, 10000);
            litSplash.schoolSpells().lumos();
            litFar.schoolSpells().lumos();
            f.unlock(3);
            check(wizard.selectSpell(MagicSchoolSpell.EXPULSO), "Expulso must equip.");
            hit(wizard, primary);
            double dealt = wizard.runtimeEntity(f.lane).orElseThrow().attackDamageAmount(litSplash) * .5 * 1.12;
            requireClose(10000 - dealt, litSplash.getHealth(), "The first lit splash victim triggers the transfer.");
            requireClose(10000 - dealt * .1 * 1.12, litFar.getHealth(), "Secondary damage must use the same transfer hook.");
            wizard.resetForRound(f.lane);
            var arch = (MagicSchoolWizardTower) add(f.lane, MagicSchoolTowers.BRAVE_ARCHWIZARD, f.pos(0));
            arch.runtimeEntity(f.lane).orElseThrow().setNoAi(true);
            f.unlock(6);
            check(arch.selectSpell(MagicSchoolSpell.CRUCIO), "Crucio must equip.");
            var source = arch.runtimeEntity(f.lane).orElseThrow();

            litSplash.schoolSpells().crucio(arch, source);
            double before = litFar.getHealth();
            for (int i = 0; i < 2; i++) litSplash.schoolSpells().tick();
            check(litFar.getHealth() < before, "Periodic spell damage must trigger a transfer.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void potionChecksFinalDamageOnceAndSurvivesPromotionStateCopy(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(0);
            var other = f.wizard(1);
            f.buy(Upgrade.POTIONS);
            f.unlock(2);
            wizard.selectSpell(MagicSchoolSpell.PROTEGO);
            f.lane.markWaveStarted(1);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            double before = source.getHealth();
            source.hurt(source.damageSources().generic(), 250);
            requireClose(before - 175, source.getHealth(), "Thirty-percent protection must reduce the hit before testing lethality.");
            source.setInvulnerableTime(0);
            source.hurt(source.damageSources().generic(), 1000);
            requireClose(wizard.currentMaxHealth() * .08, source.getHealth(), "A fatal hit must leave exactly eight percent health.");
            requireClose(source.getHealth(), wizard.health(), "Runtime and entity health must agree.");
            wizard.onWaveStarted(f.lane, 1);
            var promoted = (MagicSchoolWizardTower) kim.biryeong.semiontd.tower.ProductionTowerCatalog.find(MagicSchoolTowers.GRYFFINDOR.id())
                    .orElseThrow().create(f.owner, f.lane.teamId(), f.lane.laneId(), f.pos(0));
            promoted.copyFrom(wizard, 200);
            f.lane.replaceTower(wizard, promoted);
            var after = promoted.runtimeEntity(f.lane).orElseThrow();
            after.hurtIgnoringReductions(after.damageSources().generic(), 10000);
            check(after.getHealth() <= 0, "Promotion and repeated wave-start calls must not restore a used potion.");
            var independent = other.runtimeEntity(f.lane).orElseThrow();
            independent.hurtIgnoringReductions(independent.damageSources().generic(), 10000);
            requireClose(other.currentMaxHealth() * .08, independent.getHealth(), "Each student receives its own potion charge.");
            f.lane.resetForRound();
            f.lane.markWaveStarted(2);
            var restored = promoted.runtimeEntity(f.lane).orElseThrow();
            restored.hurtIgnoringReductions(restored.damageSources().generic(), 10000);
            requireClose(promoted.currentMaxHealth() * .08, restored.getHealth(), "The next round restores one potion charge.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void quidditchPaysImmediatelyForOwnClearAndNeverForFailedDefense(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            f.wizard(0);
            f.buy(Upgrade.QUIDDITCH);
            long initial = f.game.players().get(f.owner).economy().diamond();
            long friendInitial = f.game.players().get(f.friend).economy().diamond();
            long total = 0;
            for (int round = 1; round <= 7; round++) {
                f.lane.resetForRound();
                f.lane.markWaveStarted(round);
                var enemy = f.monster(2, 1);
                setMonsterHealth(enemy, 0);
                f.lane.tick(context.getLevel().getServer(), null, f.game.players());
                total += 5 + 5 * Math.min(5, round - 1);
                requireClose(initial + total, f.game.players().get(f.owner).economy().diamond(), "The lane clear must pay immediately.");
                f.lane.tick(context.getLevel().getServer(), null, f.game.players());
                requireClose(initial + total, f.game.players().get(f.owner).economy().diamond(), "Clear polling cannot pay twice.");
            }
            requireClose(friendInitial, f.game.players().get(f.friend).economy().diamond(), "An ally must not receive the owner's reward.");
            f.lane.resetForRound();
            f.lane.markWaveStarted(8);
            f.lane.towers().forEach(tower -> tower.syncHealth(0));
            f.lane.towers().stream().filter(kim.biryeong.semiontd.tower.EntityBackedTower.class::isInstance)
                    .map(kim.biryeong.semiontd.tower.EntityBackedTower.class::cast)
                    .forEach(tower -> tower.runtimeEntity(f.lane).ifPresent(entity -> entity.setHealth(0)));
            f.lane.tick(context.getLevel().getServer(), null, f.game.players());
            requireClose(initial + total, f.game.players().get(f.owner).economy().diamond(), "A broken lane cannot receive a clear reward.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void transfigurationSpawnsImmobileOneHitDecoysWithSharedCooldown(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var first = f.wizard(0);
            var second = f.wizard(1);
            f.buy(Upgrade.TRANSFIGURATION);
            f.lane.markWaveStarted(5);
            var enemy = f.monster(2, 1);
            enemy.setPos(enemy.position().add(.23, .1, .37));
            var death = enemy.position();
            hit(first, enemy);
            var barrels = f.lane.towers().stream().filter(MagicSchoolBarrelTower.class::isInstance).toList();
            check(barrels.size() == 1, "One kill must spawn one barrel.");
            var barrel = (MagicSchoolBarrelTower) barrels.getFirst();
            var entity = barrel.runtimeEntity(f.lane).orElseThrow();
            requireClose(2, entity.getHealth(), "At round five the barrel must have two health.");
            check(entity.position().distanceToSqr(death) < 1e-8, "The barrel must appear exactly at the kill position.");
            barrel.onStateChanged(f.lane);
            check(entity.position().distanceToSqr(death) < 1e-8, "A state refresh must preserve its exact spawn position.");
            check(BlockDisplayVisual.blockState(barrel.visual()).is(Blocks.BARREL), "The decoy must use the barrel block model.");
            check(barrel.aggroPriority() == 100 && entity.isNoAi() && !barrel.canUseBasicAttacks(), "The barrel is an immobile aggro target.");
            check(kim.biryeong.semiontd.tower.TowerCapacity.slotCost(barrel) == 0 && !barrel.countsForLaneDefense(), "Temporary barrels do not occupy slots or prevent a failed defense.");
            var attacker = f.monster(3, 10000);
            new AcquireLaneDefenseTargetGoal(attacker).start();
            check(attacker.getTarget() == entity, "Real monster targeting must prefer the barrel.");
            hit(second, f.monster(4, 1));
            check(f.lane.towers().stream().filter(MagicSchoolBarrelTower.class::isInstance).count() == 1,
                    "Different students must share the player's cooldown.");
            entity.hurt(entity.damageSources().mobAttack(attacker), 100000);
            requireClose(1, entity.getHealth(), "Arbitrary incoming damage must remove exactly one health.");
            entity.hurtIgnoringReductions(entity.damageSources().mobAttack(attacker), 100000);
            check(entity.getHealth() <= 0, "Damage ignoring reductions must also remove one health.");
            f.lane.resetForRound();
            check(!f.lane.towers().contains(barrel) && entity.isRemoved(), "Round reset must remove the temporary decoy.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void bombBarrelsAreEnemiesExplodeFromDestroyerAttackAndDoNotRecreateBarrels(GameTestHelper context) throws Exception {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(0);
            var destroyer = f.wizard(1);
            f.buy(Upgrade.TRANSFIGURATION);
            f.buy(Upgrade.EXPLOSIVE_BARRELS);
            f.lane.markWaveStarted(25);
            var victim = f.monster(2, 1);
            hit(wizard, victim);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            var bombs = context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class, source.getBoundingBox().inflate(20));
            check(bombs.size() == 1, "A kill must create an enemy barrel after the conversion upgrade.");
            var bomb = bombs.getFirst();
            requireClose(1, bomb.getHealth(), "Bomb barrels stay at one health even after round twenty-five.");
            check(source.isValidAttackTarget(bomb) && bomb.hasBarrelVisual(), "A barrel must be rendered and targetable by students.");
            var visualField = MagicSchoolBombBarrelEntity.class.getDeclaredField("barrelVisual");
            visualField.setAccessible(true);
            var visual = (eu.pb4.polymer.virtualentity.api.ElementHolder) visualField.get(bomb);
            check(visual.getElements().stream().anyMatch(element -> element instanceof eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement block
                    && block.getBlockState().is(net.minecraft.world.level.block.Blocks.TNT)),
                    "An explosive barrel must send the TNT block model to clients.");
            check(!f.lane.activeMonsters().contains(bomb.runtimeMonster()), "Props must not block wave completion or award kill income.");
            var near = f.monster(4, 10000);
            var edge = f.monster(5, 10000);
            var far = f.monster(6, 10000);
            near.setPos(bomb.position().add(1.9, 0, 0));
            edge.setPos(bomb.position().add(2, 0, 0));
            far.setPos(bomb.position().add(2.01, 0, 0));
            var destroyerSource = destroyer.runtimeEntity(f.lane).orElseThrow();
            destroyerSource.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .5, 100);
            double expected = destroyer.resolveBasicAttackOutgoingDamage(destroyerSource, null, destroyerSource.attackDamageAmount(null)) * .3;
            hit(destroyer, bomb);
            requireClose(10000 - expected, near.getHealth(), "Explosion damage must use the destroyer's current attack including bonuses.");
            requireClose(10000 - expected, edge.getHealth(), "The 2-block boundary is included.");
            requireClose(10000, far.getHealth(), "Explosion must not damage outside its radius.");
            check(bomb.isRemoved() && !bomb.hasBarrelVisual(), "Destroyed barrel visuals must be cleaned up.");
            check(context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class, source.getBoundingBox().inflate(20)).isEmpty(),
                    "Destroying a barrel cannot create another barrel.");
            f.lane.resetForRound();
            f.lane.markWaveStarted(26);
            hit(wizard, f.monster(2, 1));
            var leftover = context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class, source.getBoundingBox().inflate(20)).getFirst();
            f.close();
            check(leftover.isRemoved() && !leftover.hasBarrelVisual(), "Match close must remove surviving bombs and their visuals.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void lastWizardDeathDiscardsOnlyItsLaneBombsWithoutExplosion(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var first = f.wizard(0);
            var last = f.wizard(1);
            var school = add(f.lane, MagicSchoolTowers.HOGWARTS, f.pos(-1));
            f.buy(Upgrade.TRANSFIGURATION);
            f.buy(Upgrade.EXPLOSIVE_BARRELS);
            f.lane.markWaveStarted(1);
            hit(first, f.monster(2, 1));
            var source = first.runtimeEntity(f.lane).orElseThrow();
            var bomb = context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class,
                    source.getBoundingBox().inflate(20)).getFirst();
            var enemy = f.monster(3, 10000);
            enemy.setPos(bomb.position().add(1, 0, 0));

            var friendLane = f.game.playerLane(f.friend).orElseThrow();
            var friendPos = GridPosition.from(BlockPos.containing(friendLane.laneLayout().positionAt(.3)));
            var friend = (MagicSchoolWizardTower) add(friendLane, MagicSchoolTowers.FRESHMAN, friendPos);
            var friendEntity = friend.runtimeEntity(friendLane).orElseThrow();
            friendEntity.setNoAi(true);
            friendEntity.setNoGravity(true);
            var funds = f.game.players().get(f.friend).economy();
            funds.addDiamond(1000);
            MagicSchoolCurriculum.purchase(f.friend, Upgrade.TRANSFIGURATION, funds);
            MagicSchoolCurriculum.purchase(f.friend, Upgrade.EXPLOSIVE_BARRELS, funds);
            friendLane.markWaveStarted(1);
            hit(friend, MagicSchoolTowerIntegrationTest.monster(context, friendLane, friendPos, 0, 0, 1));
            var friendBomb = context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class,
                    friendEntity.getBoundingBox().inflate(20)).stream()
                    .filter(entity -> entity.runtimeMonster().targetLaneId() == friendLane.laneId())
                    .findFirst().orElseThrow();

            source.hurtIgnoringReductions(source.damageSources().generic(), 10000);
            f.lane.tick(context.getLevel().getServer(), null, f.game.players());
            check(!bomb.isRemoved(), "A surviving wizard on this lane must keep the bomb.");
            var lastEntity = last.runtimeEntity(f.lane).orElseThrow();
            lastEntity.hurtIgnoringReductions(lastEntity.damageSources().generic(), 10000);
            f.lane.tick(context.getLevel().getServer(), null, f.game.players());
            check(bomb.isRemoved() && !bomb.hasBarrelVisual(), "The last wizard's death removes the bomb and its visual.");
            requireClose(10000, enemy.getHealth(), "Cleanup must not cause explosion damage.");
            check(school.health() > 0 && friend.health() > 0, "Hogwarts and another lane's wizard cannot keep this lane's bomb.");
            check(!friendBomb.isRemoved(), "A living teammate's lane keeps its own bomb.");

            f.lane.resetForRound();
            f.lane.markWaveStarted(2);
            hit(first, f.monster(2, 1));
            check(context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class,
                    first.runtimeEntity(f.lane).orElseThrow().getBoundingBox().inflate(20)).stream()
                    .anyMatch(entity -> entity.runtimeMonster().targetLaneId() == f.lane.laneId()),
                    "The next wave must still create bombs after death cleanup.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void bombPrerequisiteDoesNotSpendAndPurchaseConvertsExistingDecoys(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(0);
            var school = (HogwartsTower) add(f.lane, MagicSchoolTowers.HOGWARTS, f.pos(1));
            long before = f.game.players().get(f.owner).economy().diamond();
            check(MagicSchoolCurriculum.purchase(f.game, f.owner, school, Upgrade.EXPLOSIVE_BARRELS)
                    == MagicSchoolCurriculum.PurchaseResult.PREREQUISITE_REQUIRED, "Bombs require transfiguration.");
            check(f.game.players().get(f.owner).economy().diamond() == before
                    && MagicSchoolCurriculum.canUpgradeThisRound(f.owner, f.game.currentRound()),
                    "An unmet prerequisite cannot consume diamonds or the round allowance.");
            check(MagicSchoolCurriculum.purchase(f.game, f.owner, school, Upgrade.TRANSFIGURATION)
                    == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "The prerequisite purchase must succeed.");
            f.lane.markWaveStarted(1);
            hit(wizard, f.monster(2, 1));
            var barrel = (MagicSchoolBarrelTower) f.lane.towers().stream().filter(MagicSchoolBarrelTower.class::isInstance).findFirst().orElseThrow();
            var original = barrel.runtimeEntity(f.lane).orElseThrow();
            var position = original.position();
            MagicSchoolTestSetup.resetCurriculumRoundLimit(f.owner);
            check(MagicSchoolCurriculum.purchase(f.game, f.owner, school, Upgrade.EXPLOSIVE_BARRELS)
                    == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "Bomb conversion must buy normally.");
            check(original.isRemoved() && !f.lane.towers().contains(barrel), "Conversion removes the friendly decoy.");
            var bomb = context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class, original.getBoundingBox().inflate(1)).getFirst();
            check(position.distanceToSqr(bomb.position()) < 1e-8 && bomb.getHealth() == 1, "Existing barrels convert in place with one health.");
            check(f.game.players().get(f.owner).economy().diamond() == before - 700, "The two purchases must charge exactly seven hundred diamonds.");

            f.lane.tick(context.getLevel().getServer(), null, f.game.players());
            check(f.lane.clearedThisRound() && bomb.isRemoved(), "A clear removes leftover bombs without requiring their destruction.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void attributedIgniteUsesCurriculumDamageAndKillHooks(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(0);
            f.buy(Upgrade.SPELL_TRANSFER);
            f.buy(Upgrade.TRANSFIGURATION);
            f.lane.markWaveStarted(1);
            var victim = f.monster(2, 1);
            var receiver = f.monster(3, 100);
            victim.schoolSpells().lumos();
            receiver.schoolSpells().lumos();
            victim.applyIgnite(f.owner, wizard, kim.biryeong.semiontd.trait.TraitLoadout.none(), 100, 0, 1, 20, 1);
            victim.aiStep();
            requireClose(99.888, receiver.getHealth(), "Overkill transfer must use the one point of actual health damage, including ignite.");
            check(f.lane.towers().stream().filter(MagicSchoolBarrelTower.class::isInstance).count() == 1,
                    "An attributed periodic kill must also spawn a barrel.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void duelingPracticePaysRealHealthOnceDespiteProtectionAndPromotion(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(0);
            var capped = f.wizard(1);
            capped.gainProficiency(1000, f.lane);
            var copy = f.wizard(2);
            copy.markTemporaryCopy(wizard.logicalId());
            var dead = f.wizard(3);
            f.lane.killTower(dead);
            var otherLane = f.game.playerLane(f.friend).orElseThrow();
            var ally = (MagicSchoolWizardTower) add(otherLane, MagicSchoolTowers.FRESHMAN, f.pos(4));
            f.buy(Upgrade.DUELING_PRACTICE);
            f.buy(Upgrade.POTIONS);
            f.unlock(2);
            wizard.selectSpell(MagicSchoolSpell.PROTEGO);
            var source = wizard.runtimeEntity(f.lane).orElseThrow();
            MagicSchoolSpellCombat.applySelfProtection(wizard, source);
            f.lane.markWaveStarted(1);
            ally.onWaveStarted(otherLane, 1);
            requireClose(20.1485, wizard.proficiency(), "Eleven wave proficiency and 30% of 30.495 health must be awarded.");
            requireClose(30.495, source.getMaxHealth() - source.getHealth(), "Protego cannot halve the health cost.");
            requireClose(source.getHealth(), wizard.health(), "Health cost and proficiency growth must keep runtime health synchronized.");
            requireClose(100, capped.proficiency(), "Proficiency cannot exceed its cap.");
            requireClose(195.5, capped.runtimeEntity(f.lane).orElseThrow().getHealth(), "Capped students still pay fifteen percent health.");
            requireClose(200, copy.health(), "Temporary copies do not pay or gain proficiency.");
            requireClose(0, dead.proficiency(), "Dead students cannot practice.");
            requireClose(ally.currentMaxHealth(), ally.health(), "A teammate's students must not pay for this player's curriculum.");
            wizard.onWaveStarted(f.lane, 1);
            requireClose(20.1485, wizard.proficiency(), "The same wave cannot award dueling twice.");

            source.hurtIgnoringReductions(source.damageSources().generic(), 10000);
            requireClose(wizard.currentMaxHealth() * .08, source.getHealth(), "Dueling must leave the emergency potion available.");
            var promoted = (MagicSchoolWizardTower) kim.biryeong.semiontd.tower.ProductionTowerCatalog.find(MagicSchoolTowers.GRYFFINDOR.id())
                    .orElseThrow().create(f.owner, f.lane.teamId(), f.lane.laneId(), f.pos(0));
            promoted.copyFrom(wizard, 200);
            f.lane.replaceTower(wizard, promoted);
            double health = promoted.health();
            promoted.onWaveStarted(f.lane, 1);
            requireClose(0, promoted.proficiency(), "Promotion resets proficiency but cannot repeat the same wave's practice.");
            requireClose(health, promoted.health(), "Promotion cannot repeat the health cost.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void duelingTogglesOnlyAffectTheNextWave(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var wizard = f.wizard(0);
            var school = (HogwartsTower) add(f.lane, MagicSchoolTowers.HOGWARTS, f.pos(1));
            f.buy(Upgrade.DUELING_PRACTICE);
            MagicSchoolCurriculum.toggle(f.game, f.owner, school, Upgrade.DUELING_PRACTICE);
            f.lane.markWaveStarted(1);
            requireClose(11, wizard.proficiency(), "OFF gives only the ordinary wave gain.");
            requireClose(wizard.currentMaxHealth(), wizard.health(), "OFF does not consume health.");
            MagicSchoolCurriculum.toggle(f.game, f.owner, school, Upgrade.DUELING_PRACTICE);
            wizard.onWaveStarted(f.lane, 1);
            requireClose(11, wizard.proficiency(), "Enabling after battle start must not apply immediately.");
            f.lane.resetForRound();
            f.lane.markWaveStarted(2);
            requireClose(23 + 31.035 * .30, wizard.proficiency(), "The next wave applies the newly enabled practice once.");
            requireClose(31.035, wizard.currentMaxHealth() - wizard.health(), "The cost uses the next wave's updated maximum health.");
        }
        context.succeed();
    }

    private static void hit(MagicSchoolWizardTower wizard, SemionMonsterEntity target) {
        var source = wizard.runtimeEntity(wizard.attachedLane()).orElseThrow();
        target.setInvulnerableTime(0);
        double attack = source.attackDamageAmount(target);
        var result = wizard.damagePrimaryAttackTargetResult(source, target, attack);
        source.recordAttack(target, attack, result.secondaryOutgoingDamage(), result.dealtDamage(), result.killed());
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper context;
        final UUID owner = UUID.randomUUID();
        final UUID friend = UUID.randomUUID();
        final SemionGame game;
        final PlayerLane lane;
        final GridPosition anchor;
        boolean closed;

        Fixture(GameTestHelper context) {
            this.context = context;
            game = game(context, owner, friend);
            lane = game.playerLane(owner).orElseThrow();
            anchor = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            game.players().get(owner).economy().addDiamond(20000);
        }

        GridPosition pos(int offset) { return new GridPosition(anchor.x() + offset, anchor.y(), anchor.z()); }
        MagicSchoolWizardTower wizard(int offset) {
            var wizard = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, pos(offset));
            wizard.runtimeEntity(lane).orElseThrow().setNoAi(true);
            wizard.runtimeEntity(lane).orElseThrow().setNoGravity(true);
            return wizard;
        }
        SemionMonsterEntity monster(int offset, double health) { return MagicSchoolTowerIntegrationTest.monster(context, lane, pos(offset), 0, 0, health); }
        void buy(Upgrade upgrade) { check(MagicSchoolCurriculum.purchase(owner, upgrade, game.players().get(owner).economy())
                == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "Fixture purchase failed: " + upgrade); }
        void unlock(int tier) {
            MagicSchoolCurriculum.purchase(owner, Upgrade.ADVANCED_SPELLS, game.players().get(owner).economy());
            if (tier == 6) lane.assignAugmentSnapshot(MagicSchoolAugmentCombatTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES));
            for (int next = 2; next <= Math.min(5, tier); next++) {
                MagicSchoolCurriculum.unlockSpellTier(owner, next, game.players().get(owner).economy());
            }
        }
        @Override public void close() { if (!closed) { closed = true; game.close(); } }
    }
}
