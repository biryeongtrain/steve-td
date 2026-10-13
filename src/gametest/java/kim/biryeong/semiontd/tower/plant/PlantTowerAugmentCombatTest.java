package kim.biryeong.semiontd.tower.plant;

import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.augment.AugmentCombatFixture;
import kim.biryeong.semiontd.augment.AugmentChoice;

public final class PlantTowerAugmentCombatTest extends AugmentCombatFixture {
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
        first.setPermanentlyInvulnerable(true);
        entering.setPermanentlyInvulnerable(true);
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
}
