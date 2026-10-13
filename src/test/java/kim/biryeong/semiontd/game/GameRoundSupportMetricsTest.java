package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class GameRoundSupportMetricsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void groupedCaptureMatchesLegacyOwnerAndLaneOrderExactly() {
        List<SemionTeam> teams = List.of(new SemionTeam(TeamId.RED), new SemionTeam(TeamId.BLUE));
        Set<UUID> purchasers = new LinkedHashSet<>();
        for (int i = 0; i < 30; i++) {
            purchasers.add(new UUID(0, i + 1));
        }
        for (SemionTeam team : teams) {
            for (int laneId = 1; laneId <= 5; laneId++) {
                PlayerLane lane = lane(team, laneId);
                for (UUID purchaser : purchasers) {
                    for (double amount : new double[]{1.0e16, 1.0, 0.25, 3.0}) {
                        Monster monster = monster(purchaser, laneId);
                        monster.supportMetrics().recordHealing(amount, amount * 0.75);
                        lane.enqueueSummonedMonster(monster);
                    }
                }
            }
        }
        Map<UUID, MonsterSupportMetrics.Snapshot> actual = GameRoundSupportMetrics.capture(teams, purchasers);
        assertEquals(purchasers, actual.keySet());
        for (UUID purchaser : purchasers) {
            assertEquals(legacy(teams, purchaser), actual.get(purchaser));
        }
    }

    @Test
    void excludesOtherPurchasersOriginsAndNextRoundReservationsWithoutDoubleCounting() {
        SemionTeam team = new SemionTeam(TeamId.RED);
        PlayerLane lane = lane(team, 1);
        UUID purchaser = new UUID(0, 1);
        UUID other = new UUID(0, 2);
        Monster paid = monster(purchaser, 1);
        paid.supportMetrics().recordHealing(10, 7);
        lane.enqueueSummonedMonster(paid);
        lane.activeMonsters().add(paid);
        for (MonsterOrigin origin : MonsterOrigin.values()) {
            if (origin == MonsterOrigin.NORMAL_PAID) {
                continue;
            }
            Monster excluded = monster(purchaser, 1);
            excluded.setOrigin(origin);
            excluded.supportMetrics().recordHealing(100, 100);
            lane.enqueueSummonedMonster(excluded);
        }
        Monster unrelated = monster(other, 1);
        unrelated.supportMetrics().recordHealing(100, 100);
        lane.enqueueSummonedMonster(unrelated);
        Monster reserved = monster(purchaser, 1);
        reserved.supportMetrics().recordHealing(1000, 1000);
        lane.enqueueNextRoundSummonedMonster(reserved);
        var actual = GameRoundSupportMetrics.capture(List.of(team), Set.of(purchaser));
        assertEquals(Set.of(purchaser), actual.keySet());
        assertEquals(legacy(List.of(team), purchaser), actual.get(purchaser));
        assertEquals(7.0, actual.get(purchaser).effectiveHealing());
        assertFalse(actual.containsKey(other));
    }

    @Test
    void capturesLateShieldChangesAndDoesNotCacheAcrossClearOrReservationPromotion() {
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        PlayerLane lane = lane(team, 1);
        UUID purchaser = new UUID(0, 1);
        Monster source = monster(purchaser, 1);
        Monster target = monster(purchaser, 1);
        lane.enqueueSummonedMonster(source);
        assertTrue(target.grantShield(DamageType.PHYSICAL, 50, 10, 0, source));
        var before = GameRoundSupportMetrics.capture(List.of(team), Set.of(purchaser));
        source.syncHealth(0);
        lane.disableMonsters();
        target.damage(20, DamageType.PHYSICAL);
        target.expireShields(10);
        var after = GameRoundSupportMetrics.capture(List.of(team), Set.of(purchaser));
        assertEquals(0.0, before.get(purchaser).physicalShieldAbsorbed());
        assertEquals(20.0, after.get(purchaser).physicalShieldAbsorbed());
        assertEquals(30.0, after.get(purchaser).physicalShieldExpired());
        lane.clearRoundMonsterMetrics();
        assertTrue(GameRoundSupportMetrics.capture(List.of(team), Set.of(purchaser)).isEmpty());
        Monster reserved = monster(purchaser, 1);
        reserved.supportMetrics().recordHealing(40, 30);
        lane.enqueueNextRoundSummonedMonster(reserved);
        assertTrue(GameRoundSupportMetrics.capture(List.of(team), Set.of(purchaser)).isEmpty());
        lane.resetForRound();
        assertEquals(30.0, GameRoundSupportMetrics.capture(List.of(team), Set.of(purchaser))
                .get(purchaser).effectiveHealing());
    }

    @Test
    void emptyParticipantsAndUnknownOwnersProduceNoSyntheticEntries() {
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        PlayerLane lane = lane(team, 1);
        UUID purchaser = new UUID(0, 1);
        lane.enqueueSummonedMonster(monster(null, 1));
        assertTrue(GameRoundSupportMetrics.capture(List.of(team), Set.of()).isEmpty());
        assertTrue(GameRoundSupportMetrics.capture(List.of(team), Set.of(purchaser)).isEmpty());
        assertTrue(GameRoundSupportMetrics.capture(List.of(), Set.of(purchaser)).isEmpty());
    }

    static MonsterSupportMetrics.Snapshot legacy(List<SemionTeam> teams, UUID purchaser) {
        return teams.stream().flatMap(team -> team.laneGroup().lanes().stream())
                .map(lane -> lane.utilitySupportMetrics(purchaser))
                .reduce(MonsterSupportMetrics.Snapshot.empty(), MonsterSupportMetrics.Snapshot::plus);
    }

    static PlayerLane lane(SemionTeam team, int laneId) {
        LaneRegionLayout layout = new LaneRegionLayout(laneId, new Vec3(0.5, 64, 0.5),
                List.of(new Vec3(0.5, 64, 2.5)), new Vec3(0.5, 64, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(64, 66, 10)),
                List.of(new GridPosition(0, 63, 10)));
        PlayerLane lane = new PlayerLane(team.id(), laneId, new UUID(1, laneId), null, layout);
        team.laneGroup().lanes().add(lane);
        return lane;
    }

    static Monster monster(UUID purchaser, int laneId) {
        Monster monster = new Monster("support", TeamId.BLUE, laneId, Optional.ofNullable(purchaser),
                Optional.of(TeamId.RED), 100, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0L);
        monster.setOrigin(MonsterOrigin.NORMAL_PAID);
        return monster;
    }
}
