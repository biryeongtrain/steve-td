package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.config.WaveSpawnMode;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.entity.monster.WaveHealingState;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class PlayerLaneSupportMetricsTest {
    private static final UUID LANE_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PURCHASER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void deadSourceRetainsLaterShieldAbsorptionThroughLaneElimination() {
        PlayerLane lane = lane(1);
        Monster source = paidMonster("shield-source", PURCHASER, 1);
        Monster target = paidMonster("shield-target", PURCHASER, 1);
        lane.enqueueSummonedMonster(source);
        // The logical object can occur in a queue and active list without becoming two sources.
        lane.activeMonsters().add(source);
        source.supportMetrics().recordHealing(30.0, 20.0);
        assertTrue(target.grantShield(DamageType.PHYSICAL, 50.0, 10, 0, source));
        source.syncHealth(0.0);
        lane.disableMonsters();
        lane.clearTowers();
        assertTrue(lane.activeMonsters().isEmpty());

        target.damage(20.0, DamageType.PHYSICAL);
        target.expireShields(10);

        var snapshot = lane.utilitySupportMetrics(PURCHASER);
        assertEquals(1, snapshot.healingAttempts());
        assertEquals(20.0, snapshot.effectiveHealing());
        assertEquals(50.0, snapshot.physicalShieldGranted());
        assertEquals(20.0, snapshot.physicalShieldAbsorbed());
        assertEquals(30.0, snapshot.physicalShieldExpired());
        assertEquals(MonsterSupportMetrics.Snapshot.empty(), lane.utilitySupportMetrics(LANE_OWNER));
        assertEquals(MonsterSupportMetrics.Snapshot.empty(), lane.naturalWaveSupportMetrics());

        lane.clearRoundMonsterMetrics();
        assertEquals(MonsterSupportMetrics.Snapshot.empty(), lane.utilitySupportMetrics(PURCHASER));
        assertEquals(20.0, snapshot.physicalShieldAbsorbed(), "Clearing retention must not mutate a completed snapshot.");
    }

    @Test
    void purchaserTotalsCanBeAddedAcrossReceivingLanes() {
        PlayerLane first = lane(1);
        PlayerLane second = lane(2);
        Monster firstSource = paidMonster("first-source", PURCHASER, 1);
        Monster secondSource = paidMonster("second-source", PURCHASER, 2);
        first.enqueueSummonedMonster(firstSource);
        second.enqueueSummonedMonster(secondSource);
        firstSource.supportMetrics().recordHealing(10.0, 10.0);
        secondSource.supportMetrics().recordHealing(20.0, 20.0);

        var total = first.utilitySupportMetrics(PURCHASER).plus(second.utilitySupportMetrics(PURCHASER));
        assertEquals(2, total.healingAttempts());
        assertEquals(30.0, total.effectiveHealing());
        assertEquals(MonsterSupportMetrics.Snapshot.empty(), second.utilitySupportMetrics(LANE_OWNER));
    }

    @Test
    void naturalHealingMetricsSurviveFinalDefenseAndEntityRecreationButExcludeProxies() {
        PlayerLane lane = lane(1);
        var wave = WaveConfig.defaultConfig().withSeason3Stages(false, true, Set.of())
                .candidatesForRound(16).getFirst();
        WaveMonsterEntry entry = wave.entriesForLane("lane_1").stream()
                .filter(candidate -> candidate.healing() != null).findFirst().orElseThrow();
        Monster healer = Monster.fromWaveEntry(entry, TeamId.BLUE, 1, MonsterOrigin.NATURAL_WAVE);
        Monster proxy = Monster.fromWaveEntry(entry, TeamId.BLUE, 1, MonsterOrigin.BUILDER_PROXY);
        lane.enqueueSummonedMonster(healer);
        lane.enqueueSummonedMonster(proxy);
        healer.supportMetrics().recordHealing(240.0, 180.0);
        healer.waveHealingState().recordAttempt(entry.healing(), 3, 180.0, 240.0, 180.0, WaveHealingState.Failure.NONE);
        proxy.supportMetrics().recordHealing(900.0, 900.0);
        proxy.waveHealingState().recordAttempt(entry.healing(), 3, 900.0, 900.0, 900.0, WaveHealingState.Failure.NONE);

        healer.enterFinalDefenseCombat();
        healer.markMinecraftEntitySpawned(11, 0, 64, 0);
        healer.clearMinecraftEntityReference();
        healer.markMinecraftEntitySpawned(12, 0, 64, 0);
        healer.clearMinecraftEntityReference();
        healer.syncHealth(0.0);
        healer.markRemoved();
        lane.disableMonsters();

        assertEquals(180.0, lane.naturalWaveSupportMetrics().effectiveHealing());
        assertEquals(1, lane.waveSupportMetrics().successfulCasts());
        assertEquals(180.0, lane.waveSupportMetrics().effectiveHealing());
        assertEquals(60.0, lane.waveSupportMetrics().overhealing());
        assertEquals(MonsterSupportMetrics.Snapshot.empty(), lane.utilitySupportMetrics(PURCHASER));
    }

    @Test
    void initialNaturalWaveHealthUsesExpandedQueueAndSeparatesMissingFromZero() {
        PlayerLane lane = lane(1);
        assertNull(lane.waveTemplateId());
        assertNull(lane.naturalWaveCount());
        assertNull(lane.naturalWaveStartingHealth());

        lane.enqueueWave(List.of(entry("small", 12.5, 3), entry("large", 25.0, 2)),
                WaveSpawnMode.ROUND_ROBIN, 1, 7L, "test_template");
        lane.enqueueSummonedMonster(paidMonster("income-excluded", PURCHASER, 1));
        assertEquals("test_template", lane.waveTemplateId());
        assertEquals(5, lane.naturalWaveCount());
        assertEquals(87.5, lane.naturalWaveStartingHealth());

        lane.disableMonsters();
        assertEquals(5, lane.naturalWaveCount());
        assertEquals(87.5, lane.naturalWaveStartingHealth());
        lane.resetForRound();
        assertNull(lane.waveTemplateId());
        assertNull(lane.naturalWaveCount());
        lane.enqueueWave(List.of(), WaveSpawnMode.SEQUENTIAL, 1, 0L, "empty_template");
        assertEquals(0, lane.naturalWaveCount());
        assertEquals(0.0, lane.naturalWaveStartingHealth());
    }

    @Test
    void nextRoundReservationIsNotCountedUntilPromotionAndResetDropsPreviousSources() {
        PlayerLane lane = lane(1);
        Monster previous = paidMonster("previous", PURCHASER, 1);
        lane.enqueueSummonedMonster(previous);
        previous.supportMetrics().recordHealing(10.0, 10.0);
        lane.disableMonsters();

        Monster reserved = paidMonster("reserved", PURCHASER, 1);
        reserved.supportMetrics().recordHealing(20.0, 20.0);
        lane.enqueueNextRoundSummonedMonster(reserved);
        assertEquals(10.0, lane.utilitySupportMetrics(PURCHASER).effectiveHealing());
        lane.resetForRound();
        assertEquals(0, lane.pendingNextRoundSummonCount());
        assertEquals(1, lane.queuedSummonCount());
        assertEquals(20.0, lane.utilitySupportMetrics(PURCHASER).effectiveHealing());
    }

    @Test
    void roundLeakTotalsCountEachLogicalMonsterOnceAndDoNotResetMatchAttribution() throws ReflectiveOperationException {
        PlayerLane lane = lane(1);
        SemionPlayer defender = new SemionPlayer(LANE_OWNER, "defender", TeamId.BLUE, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        SemionPlayer purchaser = new SemionPlayer(PURCHASER, "purchaser", TeamId.RED, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        Map<UUID, SemionPlayer> players = Map.of(LANE_OWNER, defender, PURCHASER, purchaser);
        Monster first = paidMonster("first-leak", PURCHASER, 1);
        Monster second = paidMonster("second-leak", PURCHASER, 1);
        double firstThreat = first.attributionThreat();
        double totalThreat = firstThreat + second.attributionThreat();
        var recordLeak = PlayerLane.class.getDeclaredMethod("recordLaneLeak", Monster.class, Map.class);
        recordLeak.setAccessible(true);

        recordLeak.invoke(lane, first, players);
        first.markMinecraftEntitySpawned(11, 0, 64, 0);
        first.clearMinecraftEntityReference();
        first.markMinecraftEntitySpawned(12, 0, 64, 0);
        first.clearMinecraftEntityReference();
        recordLeak.invoke(lane, first, players);
        assertEquals(1, lane.leakedCountThisRound());
        assertEquals(firstThreat, lane.leakedThreatThisRound());
        recordLeak.invoke(lane, second, players);
        assertEquals(2, lane.leakedCountThisRound());
        assertEquals(totalThreat, lane.leakedThreatThisRound());
        assertTrue(lane.leakedThisRound());
        assertEquals(totalThreat, defender.matchStats().snapshot(0).ownLaneLeakedThreat());
        assertEquals(totalThreat, purchaser.matchStats().snapshot(0).incomeAttackSuccessThreat());

        lane.disableMonsters();
        lane.clearTowers();
        assertEquals(2, lane.leakedCountThisRound(), "Elimination must retain round evidence until its snapshot.");
        lane.resetForRound();
        assertEquals(0, lane.leakedCountThisRound());
        assertEquals(0.0, lane.leakedThreatThisRound());
        assertFalse(lane.leakedThisRound());
        assertEquals(totalThreat, defender.matchStats().snapshot(0).ownLaneLeakedThreat());
    }

    private static WaveMonsterEntry entry(String id, double health, int count) {
        return new WaveMonsterEntry(id, health, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", null, count);
    }

    private static Monster paidMonster(String id, UUID purchaser, int laneId) {
        Monster monster = new Monster(id, TeamId.BLUE, laneId, Optional.of(purchaser), Optional.of(TeamId.RED),
                100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", 0L);
        monster.setOrigin(MonsterOrigin.NORMAL_PAID);
        return monster;
    }

    private static PlayerLane lane(int laneId) {
        LaneRegionLayout layout = new LaneRegionLayout(laneId, new Vec3(0.5, 64.0, 0.5),
                List.of(new Vec3(0.5, 64.0, 2.5)), new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(64, 66, 10)),
                List.of(new GridPosition(0, 63, 10)));
        return new PlayerLane(TeamId.BLUE, laneId, LANE_OWNER, null, layout);
    }
}
