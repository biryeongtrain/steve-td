package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AugmentTelemetry;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.frost.FrostSplashTower;
import kim.biryeong.semiontd.tower.frost.FrostTowers;
import kim.biryeong.semiontd.tower.pirate.PirateTower;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AugmentCombatGameTest {
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

    @GameTest
    public void villagerCorpseChainStopsAfterTwoGenerationsAndSuppressesOtherTriggers(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(1, 2, 3)));
        var cat = new kim.biryeong.semiontd.tower.villager.LaneClearCatTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T2_LANE_CLEAR_CAT_TOWER,
                lane.ownerPlayer(), TeamId.RED, 1, p);
        try {
            lane.addTower(cat);
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_g2", AugmentChoice.none()));
            lane.markWaveStarted(5);
            var source = entity(context, cat);
            source.setNoAi(true);
            double radius = kim.biryeong.semiontd.config.TowerBalanceRuntime.ability(cat.type().id(), "explosionRadius") + 1;
            double spacing = radius - .1;
            var corpses = new ArrayList<SemionMonsterEntity>();
            for (int i = 0; i < 5; i++) {corpses.add(monster(context, lane, source.position().add(i * spacing, 0, 0), 1));}
            cat.damageTargetResult(source, corpses.getFirst(), 100);
            cat.onKill(source, corpses.getFirst(), 100);
            for (int i = 1; i <= 3; i++) {
                close(0, corpses.get(i).runtimeMonster().health(), "Initial explosion and two chained generations must resolve.");
                AugmentCombat.withKillOrigin(corpses.get(i).runtimeMonster(), () -> {
                    if (AugmentCombat.allowsTriggers()) {throw new AssertionError("Chain kills must not charge another augment.");}
                });
            }
            close(1, corpses.get(4).runtimeMonster().health(), "A third generation must not explode.");
            if (!AugmentCombat.allowsTriggers()) {throw new AssertionError("Chain guard must unwind.");}
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void villagerInheritanceUsesPermanentStacksAcrossTiersOnlyOnce(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var dead = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T2_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1, p);
        var next = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T3_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1,
                new GridPosition(p.x() + 2, p.y(), p.z()));
        try {
            lane.addTower(dead);
            lane.addTower(next);
            kim.biryeong.semiontd.tower.villager.VillagerAugments.onSelected(lane, AugmentConfig.defaults());
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_s", AugmentChoice.none()));
            lane.markWaveStarted(5);
            double before = next.currentMaxHealth();
            dead.syncHealth(0);
            dead.notifyDeath(lane);
            if (next.currentMaxHealth() <= before) {throw new AssertionError("Cross-tier inheritance must increase health.");}
            close(3, kim.biryeong.semiontd.tower.villager.VillagerAugments.permanentStacks(next), "Inherited stacks must not become permanent.");
            double inherited = next.currentMaxHealth();
            dead.onDeath(lane);
            close(inherited, next.currentMaxHealth(), "Duplicate deaths must not inherit again.");
            lane.markWaveStarted(6);
            close(before, next.currentMaxHealth(), "Temporary inheritance expires at the next wave.");
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void villagerLongevityDoesNotConsumeGrowthOrRecurse(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var tower = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T2_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1, p);
        try {
            lane.addTower(tower);
            kim.biryeong.semiontd.tower.villager.VillagerAugments.onSelected(lane, AugmentConfig.defaults());
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_g1", AugmentChoice.none()));
            lane.markWaveStarted(5);
            var source = entity(context, tower);
            source.setNoAi(true);
            var target = monster(context, lane, source.position().add(.5, 0, 0), 100000);
            double damage = tower.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target));
            double before = target.runtimeMonster().health();
            for (int i = 0; i < 6; i++) {source.recordAttack(target, 1, 1, 1, false);}
            close(damage * 3, before - target.runtimeMonster().health(), "Three growth stacks give exactly three additional attacks.");
            close(3, kim.biryeong.semiontd.tower.villager.VillagerAugments.permanentStacks(tower), "Attacks do not spend permanent growth.");
            if (!AugmentCombat.allowsTriggers()) {throw new AssertionError("Additional attack guard must unwind.");}
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void villagerGiantPulsesAfterSixtyTicksAndStopsAfterRound(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var tower = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T3_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1, p);
        try {
            lane.addTower(tower);
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_p", new AugmentChoice(tower.logicalId(), null, "")));
            lane.markWaveStarted(5);
            var source = entity(context, tower);
            source.setNoAi(true);
            var target = monster(context, lane, source.position().add(2, 0, 0), 100000);
            var outside = monster(context, lane, source.position().add(5, 0, 0), 100000);
            double expected = tower.resolveOutgoingDamage(source, target, tower.currentMaxHealth() * .12);
            for (int i = 0; i < 59; i++) {tower.tick(lane);}
            close(100000, target.runtimeMonster().health(), "Pulse must wait sixty ticks.");
            tower.tick(lane);
            close(100000 - expected, target.runtimeMonster().health(), "Pulse uses current maximum health.");
            close(100000, outside.runtimeMonster().health(), "Pulse obeys four-block radius.");
            tower.resetForRound(lane);
            double remaining = target.runtimeMonster().health();
            for (int i = 0; i < 61; i++) {tower.tick(lane);}
            close(remaining, target.runtimeMonster().health(), "Preparation must not pulse.");
            context.succeed();
        } finally {cleanup(lane);}
    }

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
            source.setInvulnerable(true);
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
                    net.minecraft.resources.ResourceLocation.parse("semiontd:job_adversary_towers_g1"), 1, 120);
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

    @GameTest(maxTicks = 120)
    public void jobTntRepeatsAtFixedPositionWithSnapshotDamageAndCancelsOnReset(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var position = GridPosition.from(context.absolutePos(new BlockPos(7, 2, 7)));
        var platePosition = GridPosition.from(context.absolutePos(new BlockPos(6, 2, 7)));
        var tnt = new kim.biryeong.semiontd.tower.engineer.EngineerTrapTower(
                kim.biryeong.semiontd.tower.engineer.EngineerTowers.trap(kim.biryeong.semiontd.tower.engineer.EngineerTowers.TrapKind.TNT, 1),
                lane.ownerPlayer(), TeamId.RED, 1, position, position);
        var plate = new kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower(
                kim.biryeong.semiontd.tower.engineer.EngineerTowers.plate(kim.biryeong.semiontd.tower.engineer.EngineerTowers.PlateKind.WOOD),
                lane.ownerPlayer(), TeamId.RED, 1, platePosition, platePosition);
        lane.addTower(tnt);
        lane.addTower(plate);
        lane.assignAugmentSnapshot(snapshot("job_engineer_towers_g2", AugmentChoice.none()));
        lane.markWaveStarted(5);
        var source = entity(context, tnt);
        source.setNoAi(true);
        source.setNoGravity(true);
        Vec3 center = source.position();
        var first = monster(context, lane, center.add(1, 0, 0), 1000, false, DamageType.PHYSICAL, 100);
        var entering = monster(context, lane, center.add(-6, 0, 0), 1000, false, DamageType.PHYSICAL, 300);
        // Ignore vanilla suffocation/fire while retaining Semion's runtime damage path.
        first.setInvulnerable(true);
        entering.setInvulnerable(true);
        context.runAfterDelay(2, () -> {
            try {
                source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .25, 400);
                if (!plate.pressPlate(lane)) {throw new AssertionError("Plate must ignite TNT.");}
                for (int i = 0; i <= kim.biryeong.semiontd.tower.engineer.EngineerBalance.tntFuseTicks(); i++) {tnt.tick(lane);}
                double damage = 1000 - first.runtimeMonster().health();
                close(75, damage, "Original TNT applies +25% attack and the first target's 50% armor reduction once.");
                double repeatDamage = damage * 2 * .8 * .25;
                first.setPos(center.add(-6, 0, 0));
                entering.setPos(center.add(1, 0, 0));
                source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .50, 400);
                context.runAfterDelay(39, () -> {
                    tnt.tick(lane);
                    close(1000, entering.runtimeMonster().health(), "No repeat before forty ticks.");
                });
                context.runAfterDelay(40, () -> {
                    try {
                        tnt.tick(lane);
                        close(1000 - repeatDamage, entering.runtimeMonster().health(), "Repeat uses 80% outgoing snapshot and only the new target's armor, without current attack buffs.");
                        close(1000 - damage, first.runtimeMonster().health(), "Moved-out victim is not hit twice.");
                        tnt.tick(lane);
                        close(1000 - repeatDamage, entering.runtimeMonster().health(), "Repeat cannot execute twice in one tick.");
                        // Re-arm through the real wave hook, then cancel the scheduled repeat.
                        tnt.onWaveStarted(lane, 6);
                        var explode = tnt.getClass().getDeclaredMethod("explodeTnt", SemionTowerEntity.class);
                        explode.setAccessible(true);
                        explode.invoke(tnt, source);
                        tnt.resetForRound(lane);
                        double afterReset = entering.runtimeMonster().health();
                        context.runAfterDelay(41, () -> {
                            try {
                                tnt.tick(lane);
                                close(afterReset, entering.runtimeMonster().health(), "Round reset cancels delayed explosion.");
                                tnt.onWaveStarted(lane, 7);
                                explode.invoke(tnt, source);
                                tnt.moveToFinalDefense(lane, position);
                                if (tnt.runtimeDetailLines().stream().anyMatch(line -> line.contains("재폭발 대기"))) {
                                    throw new AssertionError("Elimination/final-defense transition must discard the pending repeat.");
                                }
                                tnt.onWaveStarted(lane, 8);
                                explode.invoke(tnt, source);
                                lane.removeTower(tnt);
                                if (tnt.runtimeDetailLines().stream().anyMatch(line -> line.contains("재폭발 대기"))) {
                                    throw new AssertionError("Permanent tower removal must discard the pending repeat.");
                                }
                                context.succeed();
                            } catch (ReflectiveOperationException failure) {
                                context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
                            } finally {cleanup(lane);}
                        });
                    } catch (ReflectiveOperationException | AssertionError failure) {
                        cleanup(lane);
                        context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
                    }
                });
            } catch (AssertionError failure) {
                cleanup(lane);
                context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void pirateGrowthAndAugmentsSurviveUpgradeWithoutDoubleMultiplying(GameTestHelper context) {
        PlayerLane lane = lane(context);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        PirateTower tower = (PirateTower) ProductionTowerCatalog.entry(PirateTowers.SWORDSMAN).orElseThrow()
                .create(lane.ownerPlayer(), TeamId.RED, 1, position);
        try {
            lane.addTower(tower);
            tower.addPermanentMaxHealthBonus(10, lane);
            tower.addPermanentFlatDamageBonus(4, lane);
            lane.assignAugmentSnapshot(snapshot("wartime_economy", AugmentChoice.none()));
            lane.markWaveStarted(5);
            SemionTowerEntity source = entity(context, tower);
            source.setNoGravity(true);
            source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .10, 100);
            close(15, tower.permanentMaxHealthBonus(), "Swordsman amplifies the stored ten health by fifty percent.");
            close(6, tower.permanentFlatDamageBonus(), "Swordsman amplifies the stored four damage by fifty percent.");
            close((tower.type().maxHealth() + 15) * 1.40, tower.currentMaxHealth(), "Wartime health applies once after permanent pirate growth.");
            close(tower.currentMaxHealth(), source.getMaxHealth(), "The live entity must share the augmented health cap.");
            SemionMonsterEntity target = monster(context, lane, source.position().add(1, 0, 0), 1000);
            new TowerAttackMonsterGoal(source).tick();
            close(1000 - (tower.type().damage() + 6) * 1.15 * 1.65, target.runtimeMonster().health(),
                    "Pirate flat growth, amplified timed damage and wartime damage each apply once.");

            PirateTower upgraded = (PirateTower) ProductionTowerCatalog.entry(PirateTowers.IRON_SWORDSMAN).orElseThrow()
                    .create(lane.ownerPlayer(), TeamId.RED, 1, position);
            upgraded.copyFrom(tower, 1200);
            if (!lane.replaceTower(tower, upgraded)) throw new AssertionError("The pirate upgrade must replace its existing tower.");
            if (!tower.logicalId().equals(upgraded.logicalId()) || !upgraded.augmentSnapshot().has("wartime_economy")) {
                throw new AssertionError("Upgrade must preserve logical identity and the chosen augment.");
            }
            lane.markWaveStarted(6);
            SemionTowerEntity nextSource = entity(context, upgraded);
            nextSource.setNoGravity(true);
            nextSource.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .10, 100);
            close(20, upgraded.permanentMaxHealthBonus(), "Iron swordsman doubles the original stored ten health, not fifteen.");
            close(8, upgraded.permanentFlatDamageBonus(), "Iron swordsman doubles the original stored four damage, not six.");
            close((upgraded.type().maxHealth() + 20) * 1.40, upgraded.currentMaxHealth(), "The upgraded health keeps pirate growth and wartime once.");
            close(upgraded.currentMaxHealth(), nextSource.getMaxHealth(), "The replacement entity must receive the augmented health cap.");
            double before = target.runtimeMonster().health();
            new TowerAttackMonsterGoal(nextSource).tick();
            close(before - (upgraded.type().damage() + 8) * 1.20 * 2 * 1.65, target.runtimeMonster().health(),
                    "The iron first strike retains its multiplier alongside timed and augment bonuses.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void finishingAndBarrageApplyOnlyToOriginalPrimaryAttack(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "primary", 20);
        lane.assignAugmentSnapshot(snapshot("finishing_fire_2", AugmentChoice.none(), "winning_barrage", AugmentChoice.none()));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity first = monster(context, lane, source.position().add(1, 0, 0), 100);
        first.runtimeMonster().syncHealth(50);
        first.setHealth(50);
        try {
            new TowerAttackMonsterGoal(source).tick();
            close(16, first.runtimeMonster().health(), "The original attack must receive finishing +70%.");
            source.damageTargetResult(first, 10);
            close(6, first.runtimeMonster().health(), "A ten-damage builder secondary must not receive finishing or kill before the next primary.");
            new TowerAttackMonsterGoal(source).tick();
            if (!AugmentCombat.detailLines(tower).contains("칼날비 3/3")) throw new AssertionError("An eligible primary kill must refill three charges.");
            source.setCustomName(net.minecraft.network.chat.Component.literal("빌더 고유 이름"));
            source.refreshAugmentNameplate();
            source.refreshAugmentNameplate();
            String visible = source.getCustomName().getString();
            if (!visible.equals("빌더 고유 이름 · 칼날비 3/3")) {
                throw new AssertionError("Augment counters must preserve builder names without duplicate suffixes: " + visible);
            }
            SemionMonsterEntity next = monster(context, lane, source.position().add(2, 0, 0), 100);
            new TowerAttackMonsterGoal(source).tick();
            close(68, next.runtimeMonster().health(), "The next original primary must receive barrage +60%.");
            if (!AugmentCombat.detailLines(tower).contains("칼날비 2/3")) throw new AssertionError("One actual primary hit consumes one charge.");
            AugmentCombat.captureRoundEnd(lane, 5);
            close(2, combatEnd(lane, tower).finishingHits(), "Only the two original finishing hits are counted.");
            close(1, combatEnd(lane, tower).barrageActivations(), "A reload is not a fired barrage; only the charged hit counts.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void frostImmediateExtraAttackDoesNotReceivePrimaryFinishingBonus(GameTestHelper context) {
        PlayerLane lane = lane(context);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        FrostSplashTower tower = new FrostSplashTower(FrostTowers.ICE_BREAKER_T3, lane.ownerPlayer(), TeamId.RED, 1, position, position);
        lane.addTower(tower);
        lane.assignAugmentSnapshot(snapshot("finishing_fire_2", AugmentChoice.none()));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity target = monster(context, lane, source.position().add(1, 0, 0), 100_000);
        target.runtimeMonster().syncHealth(50_000);
        target.setHealth(50_000);
        try {
            new TowerAttackMonsterGoal(source).tick();
            close(50_000 - 54, target.runtimeMonster().health(), "Frost must deal primary 34 plus one unaugmented extra 20.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void dominoReadsPostShieldAttemptAndDoesNotRepeatAttackerBonus(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "domino", 200);
        lane.assignAugmentSnapshot(snapshot("domino_fire", AugmentChoice.none(), "wartime_economy", AugmentChoice.none()));
        lane.markWaveStarted(15);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity first = monster(context, lane, source.position().add(1, 0, 0), 100);
        SemionMonsterEntity next = monster(context, lane, source.position().add(2, 0, 0), 1000);
        first.runtimeMonster().syncHealth(50);
        first.setHealth(50);
        if (!first.runtimeMonster().grantShield(DamageType.PHYSICAL, 30, 100,
                context.getLevel().getGameTime(), next.runtimeMonster())) {
            throw new AssertionError("Domino fixture must receive a thirty-point physical shield.");
        }
        try {
            Tower.DamageResult result = tower.damagePrimaryAttackTargetResult(source, first, 200);
            close(300, result.healthDamageAttempted(), "The original hit includes wartime once and subtracts the shield before HP capping.");
            AugmentCombat.onPrimaryAttackResolved(source, first, result);
            close(775, next.runtimeMonster().health(), "Domino transfers 225 without multiplying wartime a second time.");
            close(225, tower.roundMetricsTracker().snapshot().augmentSpecialDamageDealt(), "Only actual transferred HP damage is special damage.");
            AugmentCombat.captureRoundEnd(lane, 15);
            close(1, combatEnd(lane, tower).dominoTransfers(), "The resolved transfer must be counted exactly once.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void masteryCountsEnemyHealthLossButNotEnvironmentOrTransferredDamage(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "mastery", 20);
        lane.assignAugmentSnapshot(snapshot("battlefield_mastery", new AugmentChoice(tower.logicalId(), null, "")));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        SemionMonsterEntity attacker = monster(context, lane, source.position().add(1, 0, 0), 1000);
        try {
            source.hurt(attacker.damageSources().mobAttack(attacker), 30);
            if (AugmentCombat.detailLines(tower).stream().noneMatch(line -> line.contains("조건 피해 30/30"))) {
                throw new AssertionError("Mastery details must display the configured thirty-percent threshold.");
            }
            AugmentCombat.settleWave(lane, 5);
            close(1, AugmentCombat.masteryStacks(tower), "Thirty percent actual enemy HP damage and survival grants one stack.");
            close(120, tower.currentMaxHealth(), "One mastery stack grants twenty percent maximum HP.");
            close(84, tower.health(), "Growing maximum HP preserves the seventy-percent HP ratio.");
            lane.markWaveStarted(6);
            source.applyTransferredDamage(42);
            AugmentCombat.settleWave(lane, 6);
            close(1, AugmentCombat.masteryStacks(tower), "Transferred damage must not progress mastery.");
            close(0, tower.roundMetricsTracker().snapshot().enemyHpDamage(), "Transferred damage has no enemy HP credit.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void lowPressureCannotTriggerFinishingOrDominoButConsumesExistingBarrage(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "low_pressure_primary", 20);
        lane.assignAugmentSnapshot(snapshot("finishing_fire_2", AugmentChoice.none(),
                "winning_barrage", AugmentChoice.none(), "domino_fire", AugmentChoice.none()));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        try {
            SemionMonsterEntity ordinary = monster(context, lane, source.position().add(1, 0, 0), 10);
            primary(tower, source, ordinary, 20);
            if (!AugmentCombat.detailLines(tower).contains("칼날비 3/3")) {
                throw new AssertionError("An ordinary kill must prime barrage before the low-pressure hit.");
            }
            SemionMonsterEntity weakened = monster(context, lane, source.position().add(1, 0, 0), 100, true);
            weakened.runtimeMonster().syncHealth(35);
            weakened.setHealth(35);
            SemionMonsterEntity witness = monster(context, lane, source.position().add(2, 0, 0), 1000);
            Tower.DamageResult first = primary(tower, source, weakened, 20);
            close(32, first.outgoingDamage(), "A held barrage charge applies, but low-pressure cannot trigger finishing.");
            close(3, weakened.runtimeMonster().health(), "Charged damage must be applied to the weakened body.");
            if (!AugmentCombat.detailLines(tower).contains("칼날비 2/3")) {
                throw new AssertionError("Hitting a low-pressure body must consume a held charge.");
            }
            primary(tower, source, weakened, 20);
            if (!AugmentCombat.detailLines(tower).contains("칼날비 1/3")) {
                throw new AssertionError("A low-pressure kill must consume, not refill, barrage.");
            }
            close(1000, witness.runtimeMonster().health(), "A low-pressure kill must not start domino.");
            if (AugmentCombat.detailLines(tower).stream().anyMatch(line -> line.startsWith("마무리 사격"))) {
                throw new AssertionError("Low-pressure HP must not add a finishing proc count.");
            }
            AugmentCombat.captureRoundEnd(lane, 5);
            close(2, combatEnd(lane, tower).barrageActivations(), "Both held charges spent on low-pressure targets count as actual uses.");
            close(0, combatEnd(lane, tower).finishingHits(), "Low-pressure targets produce no finishing observation.");
            close(0, combatEnd(lane, tower).dominoTransfers(), "Low-pressure kills produce no transfer observation.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void lowPressureDamageIsMeasuredWithoutGrantingMastery(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "low_pressure_mastery", 20);
        lane.assignAugmentSnapshot(snapshot("battlefield_mastery", new AugmentChoice(tower.logicalId(), null, "")));
        lane.markWaveStarted(5);
        SemionTowerEntity source = entity(context, tower);
        try {
            SemionMonsterEntity weakened = monster(context, lane, source.position().add(1, 0, 0), 100, true);
            source.hurt(weakened.damageSources().mobAttack(weakened), 40);
            close(40, tower.roundMetricsTracker().snapshot().enemyHpDamage(), "Low-pressure damage is still actual enemy HP damage.");
            AugmentCombat.settleWave(lane, 5);
            close(40, combatEnd(lane, tower).enemyHpDamage(), "Telemetry retains all actual enemy HP damage.");
            close(0, combatEnd(lane, tower).masteryEligibleHpDamage(), "Telemetry separates the low-pressure-excluded mastery input.");
            close(0, AugmentCombat.masteryStacks(tower), "Low-pressure damage cannot grant a mastery stack.");

            lane.markWaveStarted(6);
            SemionMonsterEntity ordinary = monster(context, lane, source.position().add(2, 0, 0), 100);
            source.hurt(ordinary.damageSources().mobAttack(ordinary), 40);
            AugmentCombat.settleWave(lane, 6);
            close(1, AugmentCombat.masteryStacks(tower), "Ordinary enemy damage must still grant mastery.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest(maxTicks = 100)
    public void armorTelemetrySeparatesModifierDeltaFromActualDirectHpDamage(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ProductionTower tower = add(context, lane, "armor_telemetry", 20);
        lane.assignAugmentSnapshot(snapshot("biased_armor_physical", AugmentChoice.none()));
        lane.markWaveStarted(15);
        SemionTowerEntity source = entity(context, tower);
        try {
            SemionMonsterEntity physical = monster(context, lane, source.position().add(1, 0, 0), 100);
            SemionMonsterEntity magic = monster(context, lane, source.position().add(2, 0, 0), 100, false, DamageType.MAGIC);
            source.hurt(physical.damageSources().mobAttack(physical), 40);
            close(78, tower.health(), "Physical mode reduces forty to twenty-two before HP loss.");
            source.hurt(magic.damageSources().mobAttack(magic), 100);
            AugmentCombat.captureRoundEnd(lane, 15);
            var sample = combatEnd(lane, tower);
            close(100, sample.startingMaxHealth(), "Opening maximum HP is measured before combat.");
            close(22, sample.physicalDirectHpDamage(), "Physical direct damage is actual HP loss.");
            close(78, sample.magicDirectHpDamage(), "Lethal magic damage is capped by remaining HP.");
            close(18, sample.armorModifierReducedDamage(), "Reduction is the armor-stage difference, not inferred HP saved.");
            close(35, sample.armorModifierIncreasedDamage(), "The armor stage adds thirty-five even though only seventy-eight HP remained.");
            if (!"PHYSICAL".equals(sample.armorMode())) throw new AssertionError("The chosen armor mode must be recorded.");
            context.succeed();
        } finally { cleanup(lane); }
    }

    private static AugmentTelemetrySnapshot.CombatState combatEnd(PlayerLane lane, Tower tower) {
        int reference = lane.augmentTelemetry().towerRef(tower.logicalId());
        return lane.augmentTelemetry().snapshot().combatRounds().stream()
                .filter(sample -> sample.towerRef() == reference && sample.stage().equals("END"))
                .reduce((first, second) -> second).orElseThrow().state();
    }

    private static Tower.DamageResult primary(Tower tower, SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        Tower.DamageResult result = tower.damagePrimaryAttackTargetResult(source, target, damage);
        AugmentCombat.onPrimaryAttackResolved(source, target, result);
        return result;
    }

    private static ProductionTower add(GameTestHelper context, PlayerLane lane, String id, double damage) {
        TowerType type = new TowerType("augment_gametest_" + id, id, TowerCategory.DIRECT, 10, 100, 8, damage, 20, 0);
        ProductionTowerCatalog.registerStarter(type);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        ProductionTower tower = new ProductionTower(type, lane.ownerPlayer(), TeamId.RED, 1, position);
        lane.addTower(tower);
        entity(context, tower).setNoGravity(true);
        return tower;
    }

    private static SemionTowerEntity entity(GameTestHelper context, EntityBackedTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health) {
        return monster(context, lane, position, health, false);
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health, boolean lowPressure) {
        return monster(context, lane, position, health, lowPressure, DamageType.PHYSICAL);
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health,
                                               boolean lowPressure, DamageType damageType) {
        return monster(context, lane, position, health, lowPressure, damageType, 0);
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health,
                                               boolean lowPressure, DamageType damageType, double armor) {
        Monster monster = new Monster("augment_target_" + UUID.randomUUID(), TeamId.RED, 1, Optional.empty(), Optional.empty(),
                health, armor, 0, AttackKind.MELEE, "minecraft:zombie", null, damageType, 0,
                SummonTier.T1, List.of(SummonRole.RUSH), 0);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        if (lowPressure) {
            monster.setOrigin(MonsterOrigin.NORMAL_PAID);
            SemionPlayer buyer = new SemionPlayer(UUID.randomUUID(), "low-pressure-fixture", TeamId.BLUE, 1,
                    new PlayerEconomy(EconomyConfig.defaultConfig()));
            AugmentEconomyService.beginPrepare(buyer, 5);
            AugmentEconomyService.onSelected(buyer, "low_pressure_high_yield", 5, Map.of());
            AugmentEconomyService.setContract(buyer, 5, AugmentEconomyService.Contract.LOW_PRESSURE);
            var plan = AugmentEconomyService.quotePurchase(buyer, UUID.randomUUID(), 5,
                    true, true, false, true, true, 10, 5);
            AugmentEconomyService.applyPurchaseBody(monster, plan);
            if (!AugmentEconomyService.commitPurchase(buyer, plan, monster)) throw new AssertionError("Low-pressure fixture contract must commit.");
        }
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static AugmentSnapshot snapshot(Object... values) {
        List<PlayerAugmentState.Selection> selections = new ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            String id = (String) values[index];
            selections.add(new PlayerAugmentState.Selection(5 + index * 5, AugmentCatalog.find(id).orElseThrow().rarity(),
                    id, PlayerAugmentState.Outcome.SELECTED, null, (AugmentChoice) values[index + 1]));
        }
        return new AugmentSnapshot(AugmentConfig.defaults(), selections);
    }

    private static PlayerLane lane(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn, List.of(spawn.add(8, 0, 0)), spawn.add(8, 0, 10),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 10)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        lane.assignAugmentTelemetry(new AugmentTelemetry());
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static void cleanup(PlayerLane lane) {
        for (Monster monster : lane.activeMonsters()) {
            if (monster.hasMinecraftEntity() && lane.arenaWorld().getEntity(monster.minecraftEntityId()) != null) {
                lane.arenaWorld().getEntity(monster.minecraftEntityId()).discard();
            }
        }
        lane.activeMonsters().clear();
        for (Tower tower : List.copyOf(lane.towers())) lane.removeTower(tower);
        AreaEffectLaneIndex.unregister(lane);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    private static void close(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > .01) throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
    }
}
