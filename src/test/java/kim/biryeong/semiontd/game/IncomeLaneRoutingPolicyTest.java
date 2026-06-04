package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class IncomeLaneRoutingPolicyTest {
    @Test
    void leastThreatPressureChoosesLaneWithLowerQueuedThreatNotLowerCount() {
        PlayerLane lowThreatLane = lane(1);
        PlayerLane highThreatLane = lane(2);
        lowThreatLane.enqueueSummonedMonster(monster("chicken", 18, 1, 1));
        highThreatLane.enqueueSummonedMonster(monster("ravager", 260, 15, 2));

        Optional<PlayerLane> selected = new IncomeLaneRoutingPolicy(
                IncomeLaneRoutingConfig.defaultConfig(),
                new Random(1)
        ).select(List.of(lowThreatLane, highThreatLane));

        assertEquals(1, selected.orElseThrow().laneId());
        assertTrue(highThreatLane.queuedSummonThreat() > lowThreatLane.queuedSummonThreat());
    }

    @Test
    void pendingNextRoundThreatUsesConfiguredWeight() {
        PlayerLane currentQueueLane = lane(1);
        PlayerLane nextRoundQueueLane = lane(2);
        currentQueueLane.enqueueSummonedMonster(monster("current", 100, 0, 1));
        nextRoundQueueLane.enqueueNextRoundSummonedMonster(monster("next", 100, 0, 2));

        Optional<PlayerLane> selected = new IncomeLaneRoutingPolicy(
                new IncomeLaneRoutingConfig(true, IncomeLaneRoutingConfig.Mode.LEAST_THREAT_PRESSURE, 1.0, 0.5, IncomeLaneRoutingConfig.TieBreakMode.ROUND_ROBIN),
                new Random(1)
        ).select(List.of(currentQueueLane, nextRoundQueueLane));

        assertEquals(2, selected.orElseThrow().laneId());
    }

    @Test
    void equalThreatUsesRoundRobinTieBreak() {
        PlayerLane laneOne = lane(1);
        PlayerLane laneTwo = lane(2);
        IncomeLaneRoutingPolicy policy = new IncomeLaneRoutingPolicy(IncomeLaneRoutingConfig.defaultConfig(), new Random(1));

        assertEquals(1, policy.select(List.of(laneOne, laneTwo)).orElseThrow().laneId());
        assertEquals(2, policy.select(List.of(laneOne, laneTwo)).orElseThrow().laneId());
    }

    @Test
    void disabledConfigUsesRandomRoutingCompatibilityMode() {
        PlayerLane laneOne = lane(1);
        PlayerLane laneTwo = lane(2);
        laneOne.enqueueSummonedMonster(monster("heavy", 1000, 100, 1));
        IncomeLaneRoutingPolicy policy = new IncomeLaneRoutingPolicy(
                new IncomeLaneRoutingConfig(false, IncomeLaneRoutingConfig.Mode.LEAST_THREAT_PRESSURE, 1.0, 0.75, IncomeLaneRoutingConfig.TieBreakMode.ROUND_ROBIN),
                new Random(0)
        );

        assertTrue(policy.select(List.of(laneOne, laneTwo)).isPresent());
    }

    private static PlayerLane lane(int laneId) {
        Vec3 spawn = new Vec3(laneId + 0.5, 64.0, 0.5);
        LaneRegionLayout laneLayout = new LaneRegionLayout(
                laneId,
                spawn,
                List.of(new Vec3(laneId + 0.5, 64.0, 2.5)),
                new Vec3(laneId + 0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(laneId, 63, 0), new BlockPos(laneId + 2, 66, 10)),
                List.of(new GridPosition(laneId, 63, 10))
        );
        return new PlayerLane(TeamId.BLUE, laneId, UUID.nameUUIDFromBytes(("lane-" + laneId).getBytes()), null, laneLayout);
    }

    private static Monster monster(String id, double health, double attackDamage, int laneId) {
        return new Monster(id, TeamId.BLUE, laneId, Optional.empty(), Optional.of(TeamId.RED), health, 0, attackDamage, AttackKind.MELEE, "minecraft:zombie", 0);
    }
}
