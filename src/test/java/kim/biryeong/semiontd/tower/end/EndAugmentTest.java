package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.game.PlayerLane;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EndAugmentTest extends EndTestFixture {
    @Test
    void successfulTransfersTakeHalfTimeAndLeaveOneMineEach() {
        applyTransferDuration(4);
        PlayerLane lane = lane();
        lane.assignAugmentSnapshot(snapshot(EndAugments.MINE, EndAugments.GROWTH));
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower donor = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(core);
        lane.addTower(donor);
        core.onWaveStarted(lane, 5);
        core.tick(lane);
        assertEquals(.5, EndTransferController.progress(donor));
        assertTrue(donor.health() > 0);
        core.tick(lane);
        assertEquals(0, donor.health());
        assertTrue(core.runtimeDetailLines().contains("공허 지뢰: 1개"));
        core.tick(lane);
        assertTrue(core.runtimeDetailLines().contains("공허 지뢰: 1개"));
        core.resetForRound(lane);
        assertTrue(core.runtimeDetailLines().contains("공허 지뢰: 0개"));
    }

    @Test
    void interruptedTransfersGrantNeitherMineNorCharge() {
        applyTransferDuration(4);
        PlayerLane lane = lane();
        lane.assignAugmentSnapshot(snapshot(EndAugments.MINE, EndAugments.GROWTH));
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower donor = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(core);
        lane.addTower(donor);
        core.onWaveStarted(lane, 5);
        core.tick(lane);
        lane.removeTower(donor);
        core.tick(lane);
        assertTrue(core.runtimeDetailLines().contains("공허 지뢰: 0개"));
        assertTrue(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("전달 0기 / 추가 피해 대기")));
    }

    @Test
    void everyThirdCompletionStoresOnlyOneChargeAndResetsAtRoundEnd() {
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        core.syncAugments(snapshot(EndAugments.GROWTH), null);
        EndTower donor = tower(EndTowers.T1_SHULKER_TOWER, 1);
        EndAugments effects = new EndAugments();
        effects.onTransferCompleted(core, donor, 100);
        effects.onTransferCompleted(core, donor, 100);
        assertFalse(effects.charged());
        for (int i = 0; i < 4; i++) {effects.onTransferCompleted(core, donor, 100);}
        assertTrue(effects.charged());
        effects.reset();
        assertFalse(effects.charged());
        assertEquals(0, effects.mineCount());
    }

    @Test
    void twinKeepsHatchedFormAndHalfSnapshotWithoutGrowthOrAnotherTwin() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        lane.assignAugmentSnapshot(snapshot(EndAugments.TWIN, EndAugments.GROWTH));
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(core);
        core.onWaveStarted(lane, 5);
        EndTower twin = lane.towers().stream().filter(tower -> tower.isTemporaryCopy())
                .map(EndTower.class::cast).findFirst().orElseThrow();
        assertEquals(EndTowerState.PHANTOM, twin.state());
        assertEquals(core.currentMaxHealth() * .5, twin.currentMaxHealth());
        assertEquals(core.previewHatchedAttackDamage() * .5, twin.previewHatchedAttackDamage());
        assertTrue(twin.augmentSnapshot().selections().isEmpty());
        assertFalse(twin.canBeSold());
        assertEquals(0, twin.slotWeight());
        core.onWaveStarted(lane, 5);
        assertEquals(2, lane.towers().size());
        double frozenHealth = twin.currentMaxHealth();
        EndTower donor = tower(EndTowers.T1_SHULKER_TOWER, 2);
        lane.addTower(donor);
        twin.onWaveStarted(lane, 5);
        twin.tick(lane);
        assertTrue(donor.health() > 0);
        assertEquals(3, lane.towers().size());
        core.tick(lane);
        assertEquals(0, donor.health());
        assertEquals(frozenHealth, twin.currentMaxHealth());
        lane.killTower(core);
        assertTrue(twin.health() > 0);
        assertTrue(lane.towers().contains(twin));
        lane.removeTower(core);
        assertFalse(lane.towers().contains(twin));
    }

    @Test
    void breathUsesTwelveByThreeForwardRectangle() {
        Vec3 origin = Vec3.ZERO;
        Vec3 direction = new Vec3(1, 0, 0);
        assertTrue(EndAugments.inBreath(origin, direction, new Vec3(12, 0, 1.5), 12, 3));
        assertFalse(EndAugments.inBreath(origin, direction, new Vec3(12.01, 0, 0), 12, 3));
        assertFalse(EndAugments.inBreath(origin, direction, new Vec3(6, 0, 1.51), 12, 3));
        assertFalse(EndAugments.inBreath(origin, direction, new Vec3(-.01, 0, 0), 12, 3));
    }

    static AugmentSnapshot snapshot(String... ids) {
        List<PlayerAugmentState.Selection> selections = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            selections.add(new PlayerAugmentState.Selection(5 + i * 10, AugmentRarity.GOLD,
                    ids[i], PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()));
        }
        return new AugmentSnapshot(AugmentConfig.defaults(), selections);
    }
}
