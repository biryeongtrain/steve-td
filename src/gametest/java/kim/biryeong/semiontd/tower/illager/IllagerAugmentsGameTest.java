package kim.biryeong.semiontd.tower.illager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
import kim.biryeong.semiontd.tower.augment.OffensiveAugmentTower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class IllagerAugmentsGameTest {
    @GameTest
    public void ambushStartsRaidImmediatelyAndAddsThirtyPercentAttackSpeedForEightSeconds(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            IllagerTower tower = tower(context, lane, 3);
            IllagerTower copy = tower(context, lane, 4);
            copy.markTemporaryCopy(tower.logicalId());
            var entity = tower.runtimeEntity(lane).orElseThrow();
            var copyEntity = copy.runtimeEntity(lane).orElseThrow();
            IllagerRaidStates.onWaveStarted(lane);
            require(!IllagerRaidStates.active(lane.ownerPlayer()), "Without Ambush, wave start must not activate raid.");
            require(close(entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), 0),
                    "Without Ambush, wave start must not grant the speed effect.");
            select(lane, IllagerRaidStates.AMBUSH);
            IllagerRaidStates.onWaveStarted(lane);
            require(IllagerRaidStates.active(lane.ownerPlayer()), "Ambush must activate raid at wave start.");
            require(close(entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), .3),
                    "Ambush must grant 30% attack speed.");
            require(entity.activeTimedEffectTicks(TimedEffectType.TOWER_ATTACK_SPEED_BONUS) == 160,
                    "Ambush must last exactly eight seconds.");
            require(IllagerRaidStates.get(lane.ownerPlayer()).orElseThrow().consumePendingActivationEffects(),
                    "Ambush must retain native raid activation feedback.");
            require(close(copyEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), 0),
                    "A temporary copy must not receive the Ambush speed effect.");
            IllagerRaidStates.onWaveStarted(lane);
            require(close(entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), .3),
                    "Repeated wave-start input must not stack Ambush speed.");
            require(!IllagerRaidStates.get(lane.ownerPlayer()).orElseThrow().consumePendingActivationEffects(),
                    "An already active raid must not replay activation feedback.");
            context.succeed();
        } finally {cleanup(lane, List.of());}
    }

    @GameTest
    public void markedCorpseTransfersNativeAndOmenToThreeNearbyEnemiesOnce(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ArrayList<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            IllagerTower tower = tower(context, lane, 3);
            select(lane, IllagerTower.NEXT_TARGET);
            var source = tower.runtimeEntity(lane).orElseThrow();
            var corpse = target(context, lane, source.position().add(1, 0, 0));
            targets.add(corpse);
            for (double offset : new double[]{.5, 1, 1.5, 2, 3.01}) {
                targets.add(target(context, lane, corpse.position().add(0, 0, offset)));
            }
            IllagerMarks.apply(corpse.runtimeMonster(), lane.ownerPlayer(), .3, 200, tower.position(), 4);
            IllagerMarks.applyOmen(corpse.runtimeMonster(), lane.ownerPlayer(), .2, 80);
            for (int tick = 0; tick < 60; tick++) {corpse.runtimeMonster().tickSurvivalScaling(null, 0);}
            double damage = source.attackDamageAmount(null) * 1.5;
            tower.damageTargetResult(source, corpse, 10000);
            for (int index = 1; index <= 3; index++) {
                Monster recipient = targets.get(index).runtimeMonster();
                require(close(10000 - recipient.health(), damage), "Transferred targets must take 150% magic damage.");
                IllagerMark mark = IllagerMarks.activeMark(recipient, lane.ownerPlayer()).orElseThrow();
                require(mark.expiresAtMonsterTick() == recipient.activeTicks() + 200, "Native mark regains full duration.");
                require(mark.forcesTargetFor(tower.position()), "Transferred native mark must preserve forced targeting.");
                require(close(IllagerMarks.omenBonus(recipient, lane.ownerPlayer()), .2), "Omen transfers with the native mark.");
            }
            require(close(targets.get(4).runtimeMonster().health(), 10000), "Transfer must cap at three targets.");
            require(close(targets.get(5).runtimeMonster().health(), 10000), "Transfer must stay within three blocks.");
            require(close(tower.roundMagicDamageDealt(), damage * 3), "Transfer must record magic damage.");
            IllagerMarks.onAttributedKill(tower, source, corpse);
            require(close(tower.roundMagicDamageDealt(), damage * 3), "A corpse may only transfer marks once.");
            context.succeed();
        } finally {cleanup(lane, targets);}
    }

    @GameTest
    public void commonAugmentTowerKillTransfersMarksButAnExtraAttackKillCannot(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ArrayList<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(3, 2, 4)));
            var tower = new OffensiveAugmentTower(AugmentTowers.CAPACITOR_POST, lane.ownerPlayer(), TeamId.RED, 1,
                    position, position);
            lane.addTower(tower);
            select(lane, IllagerTower.NEXT_TARGET);
            var source = tower.runtimeEntity(lane).orElseThrow();
            require(tower.isAugmentTower(), "The source must exercise the common augment tower path.");
            var guardedCorpse = target(context, lane, source.position().add(1, 0, 0));
            var guardedRecipient = target(context, lane, guardedCorpse.position().add(0, 0, 1));
            targets.addAll(List.of(guardedCorpse, guardedRecipient));
            IllagerMarks.apply(guardedCorpse.runtimeMonster(), lane.ownerPlayer(), .3, 200, position, 4);
            AugmentCombat.runWithoutTriggers(() -> tower.damageTargetResult(source, guardedCorpse, 10000));
            require(close(guardedRecipient.runtimeMonster().health(), 10000),
                    "An augment-generated extra attack kill must not cause mark transfer damage.");
            require(IllagerMarks.activeMark(guardedRecipient.runtimeMonster(), lane.ownerPlayer()).isEmpty(),
                    "An augment-generated kill must not transfer the mark either.");
            guardedCorpse.discard();
            guardedRecipient.discard();

            var corpse = target(context, lane, source.position().add(1, 0, 0));
            var recipient = target(context, lane, corpse.position().add(0, 0, 1));
            targets.addAll(List.of(corpse, recipient));
            IllagerMarks.apply(corpse.runtimeMonster(), lane.ownerPlayer(), .3, 200, position, 4);
            double expectedDamage = source.attackDamageAmount(null) * 1.5;
            tower.damageTargetResult(source, corpse, 10000);
            require(close(10000 - recipient.runtimeMonster().health(), expectedDamage),
                    "An ordinary common augment tower kill must transfer its own 150% attack damage.");
            require(IllagerMarks.activeMark(recipient.runtimeMonster(), lane.ownerPlayer()).isPresent(),
                    "The common augment killer must transfer the owner's mark.");
            context.succeed();
        } finally {cleanup(lane, targets);}
    }

    @GameTest
    public void grandRaidConsumesFiftyGaugeAndOnlyAttackingTowersFireWithoutRecharging(GameTestHelper context) {
        PlayerLane lane = lane(context);
        ArrayList<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            IllagerTower first = tower(context, lane, 3);
            IllagerTower second = tower(context, lane, 4);
            IllagerTower idle = tower(context, lane, 5);
            select(lane, IllagerRaidStates.GRAND_RAID);
            IllagerRaidStates.onWaveStarted(lane);
            IllagerRaidState raid = IllagerRaidStates.get(lane.ownerPlayer()).orElseThrow();
            require(raid.grandRaidThreshold() == 50, "Grand raid must require exactly fifty extra gauge.");
            raid.addGauge(100, 100);
            var firstEntity = first.runtimeEntity(lane).orElseThrow();
            var secondEntity = second.runtimeEntity(lane).orElseThrow();
            var target = target(context, lane, firstEntity.position().add(1, 0, 1));
            targets.add(target);
            firstEntity.recordCurrentAttackTarget(target);
            secondEntity.recordCurrentAttackTarget(target);
            require(firstEntity.currentAttackTarget() == target && secondEntity.currentAttackTarget() == target,
                    "Both active towers must retain their valid current target.");
            double damage = (firstEntity.attackDamageAmount(target) + secondEntity.attackDamageAmount(target)) * 3;
            AugmentCombat.runWithoutTriggers(() -> IllagerRaidStates.onTowerDeath(lane, idle));
            require(raid.extraGauge() == 0, "Augment-generated events must not charge grand raid.");
            IllagerRaidStates.onTowerDeath(lane, idle);
            require(raid.extraGauge() == 25, "A native tower death must grant the packaged twenty-five gauge.");
            raid.addExtraGauge(24);
            IllagerRaidStates.tick(lane);
            require(close(target.runtimeMonster().health(), 10000), "Forty-nine gauge must not fire.");
            raid.addExtraGauge(1);
            IllagerRaidStates.tick(lane);
            require(close(10000 - target.runtimeMonster().health(), damage),
                    "Attacking towers must fire 300% physical damage. Expected " + damage
                            + ", got " + (10000 - target.runtimeMonster().health()) + ".");
            require(close(first.roundPhysicalDamageDealt() + second.roundPhysicalDamageDealt(), damage),
                    "Volley damage must use physical attribution.");
            require(close(idle.roundPhysicalDamageDealt(), 0), "Idle towers must not pick a new target.");
            require(raid.extraGauge() == 0, "Exactly fifty gauge must fire once without recharging.");
            raid.addExtraGauge(60);
            IllagerRaidStates.tick(lane);
            require(close(10000 - target.runtimeMonster().health(), damage * 2), "Sixty more gauge must fire only one additional volley.");
            require(raid.extraGauge() == 10, "Fifty gauge is consumed and overflow remains.");
            IllagerRaidStates.tick(lane);
            require(close(10000 - target.runtimeMonster().health(), damage * 2), "Consumed volleys must not repeat.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {cleanup(lane, targets);}
    }

    private static PlayerLane lane(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        var layout = new LaneRegionLayout(1, Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 13))),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static IllagerTower tower(GameTestHelper context, PlayerLane lane, int x) {
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(x, 2, 4)));
        IllagerTower tower = new IllagerTower(IllagerTowers.T1_PILLAGER, lane.ownerPlayer(), TeamId.RED, 1,
                position, position);
        lane.addTower(tower);
        return tower;
    }

    private static SemionMonsterEntity target(GameTestHelper context, PlayerLane lane, Vec3 position) {
        Monster runtime = new Monster("illager_augment_target", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
        var target = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        target.configureFrom(runtime, lane.laneLayout());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(position);
        require(context.getLevel().addFreshEntity(target), "Test target must spawn.");
        runtime.markMinecraftEntitySpawned(target.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return target;
    }

    private static void select(PlayerLane lane, String card) {
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
    }

    private static void cleanup(PlayerLane lane, List<SemionMonsterEntity> targets) {
        targets.forEach(SemionMonsterEntity::discard);
        lane.clearTowers();
        AreaEffectLaneIndex.unregister(lane);
        IllagerRaidStates.clear(lane.ownerPlayer());
    }

    private static boolean close(double a, double b) {return Math.abs(a - b) < .001;}
    private static void require(boolean condition, String message) {if (!condition) {throw new AssertionError(message);}}
}
