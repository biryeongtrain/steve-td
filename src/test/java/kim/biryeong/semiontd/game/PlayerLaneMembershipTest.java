package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class PlayerLaneMembershipTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("player-lane-membership-owner".getBytes());
    private static final TowerType TYPE = new TowerType(
            "player_lane_membership",
            "Player Lane Membership",
            TowerCategory.DIRECT,
            0,
            100.0,
            0.0,
            0.0,
            20,
            0
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposedTowerListCannotBypassLaneMembership() {
        PlayerLane lane = lane();
        CallbackTower tower = new CallbackTower(0);
        lane.addTower(tower);

        assertThrows(UnsupportedOperationException.class, () -> lane.towers().add(new CallbackTower(1)));
        lane.addTower(tower);
        assertEquals(List.of(tower), lane.towers());
    }

    @Test
    void deathNotificationSkipsTowerRemovedByAnEarlierCallback() {
        PlayerLane lane = lane();
        CallbackTower remover = new CallbackTower(0);
        CallbackTower removed = new CallbackTower(1);
        CallbackTower destroyed = new CallbackTower(2);
        remover.removeOnNotification = removed;
        lane.addTower(remover);
        lane.addTower(removed);
        lane.addTower(destroyed);

        assertTrue(lane.killTower(destroyed));
        assertEquals(1, remover.notifications);
        assertEquals(0, removed.notifications);
        assertEquals(List.of(remover, destroyed), lane.towers());
    }

    @Test
    void tickSnapshotSkipsRemovedTowerAndDefersAddedTower() {
        PlayerLane lane = lane();
        CallbackTower first = new CallbackTower(0);
        CallbackTower removed = new CallbackTower(1);
        CallbackTower added = new CallbackTower(2);
        first.onTick = () -> {
            lane.removeTower(removed);
            lane.addTower(added);
        };
        lane.addTower(first);
        lane.addTower(removed);
        lane.tickTowers();
        assertEquals(1, first.ticks);
        assertEquals(0, removed.ticks);
        assertEquals(0, added.ticks);
        lane.tickTowers();
        assertEquals(2, first.ticks);
        assertEquals(1, added.ticks);
    }

    @Test
    void replacementAndClearKeepTickMembershipConsistent() {
        PlayerLane lane = lane();
        CallbackTower first = new CallbackTower(0);
        CallbackTower original = new CallbackTower(1);
        CallbackTower replacement = new CallbackTower(2);
        first.onTick = () -> lane.replaceTower(original, replacement);
        lane.addTower(first);
        lane.addTower(original);
        lane.tickTowers();
        assertEquals(0, original.ticks);
        assertEquals(0, replacement.ticks);
        lane.tickTowers();
        assertEquals(1, replacement.ticks);
        lane.clearTowers();
        lane.addTower(original);
        lane.tickTowers();
        assertEquals(1, original.ticks);
    }

    @Test
    void nestedTickSeesMembershipChangesWithoutChangingTheOuterSnapshot() {
        PlayerLane lane = lane();
        CallbackTower first = new CallbackTower(0);
        CallbackTower removed = new CallbackTower(1);
        CallbackTower added = new CallbackTower(2);
        first.onTick = () -> {
            first.onTick = () -> {};
            lane.removeTower(removed);
            lane.addTower(added);
            lane.tickTowers();
        };
        lane.addTower(first);
        lane.addTower(removed);
        lane.tickTowers();
        assertEquals(2, first.ticks);
        assertEquals(0, removed.ticks);
        assertEquals(1, added.ticks);
        lane.tickTowers();
        assertEquals(3, first.ticks);
        assertEquals(2, added.ticks);
    }

    private static PlayerLane lane() {
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(0.5, 64.0, 0.5),
                List.of(new Vec3(0.5, 64.0, 2.5)),
                new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(64, 66, 10)),
                List.of(new GridPosition(0, 63, 10))
        );
        return new PlayerLane(TeamId.BLUE, 1, OWNER, null, layout);
    }

    private static final class CallbackTower extends Tower {
        private Tower removeOnNotification;
        private int notifications;
        private int ticks;
        private Runnable onTick = () -> {};

        private CallbackTower(int x) {
            super(TYPE, OWNER, TeamId.BLUE, 1, new GridPosition(x, 64, 0));
        }

        @Override
        public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
            notifications++;
            if (removeOnNotification != null) {
                lane.removeTower(removeOnNotification);
            }
        }

        @Override
        public void tick(PlayerLane lane) {
            ticks++;
            onTick.run();
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
