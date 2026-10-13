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
    void assaultReplacesTwinWithoutChangingStableCardId() {
        PlayerLane lane = lane();
        lane.assignAugmentSnapshot(snapshot(EndAugments.ASSAULT));
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(core);
        core.onWaveStarted(lane, 5);
        core.onWaveStarted(lane, 5);
        assertEquals("job_end_towers_p", EndAugments.ASSAULT);
        assertEquals(1, lane.towers().size());
        assertFalse(core.isTemporaryCopy());
        assertTrue(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("라운드당 1회")));
    }

    @Test
    void legacyTwinConfigurationMigratesWithoutRejectingOrRetainingStatRatio() {
        var json = com.google.gson.JsonParser.parseString("""
                {"parameters":{"semiontd:job_end_towers_p":{"statRatio":0.5}}}
                """).getAsJsonObject();
        AugmentConfig config = AugmentConfig.fromJson(json);
        assertFalse(config.parametersFor(EndAugments.ASSAULT).containsKey("statRatio"));
        assertEquals(1.0, config.parameter(EndAugments.ASSAULT, "rushDamageRatio", 0));
        assertEquals(.25, config.parameter(EndAugments.ASSAULT, "burnDamageRatio", 0));
        assertEquals(60, config.parameter(EndAugments.ASSAULT, "chargeTicks", 0));
        assertEquals(200, config.parameter(EndAugments.ASSAULT, "stunTicks", 0));
        assertEquals(20, config.parameter(EndAugments.ASSAULT, "knockbackDistance", 0));
        assertEquals(200, config.parameter(EndAugments.ASSAULT, "burnDurationTicks", 0));
        assertEquals(20, config.parameter(EndAugments.ASSAULT, "burnIntervalTicks", 0));
        assertEquals(10, config.parameter(EndAugments.ASSAULT, "flightHeight", 0));
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

    @Test
    void dimensionalGuardianKeepsTheSavedIdAndAssaultDamageRatios() {
        var definition = kim.biryeong.semiontd.augment.AugmentCatalog.find(EndAugments.ASSAULT).orElseThrow();
        assertEquals("semiontd:job_end_towers_p", definition.id());
        assertEquals("차원의 수호자", definition.displayName());
        AugmentSnapshot effects = snapshot(EndAugments.ASSAULT);
        assertEquals(1.0, effects.parameter(EndAugments.ASSAULT, "rushDamageRatio", -1), .000001);
        assertEquals(.25, effects.parameter(EndAugments.ASSAULT, "burnDamageRatio", -1), .000001);
    }

}
