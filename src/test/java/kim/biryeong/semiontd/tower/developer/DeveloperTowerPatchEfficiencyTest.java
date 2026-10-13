package kim.biryeong.semiontd.tower.developer;

import xyz.nucleoid.map_templates.BlockBounds;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeveloperTowerPatchEfficiencyTest {
    private static final UUID OWNER = UUID.fromString("b0b33535-7ac8-40ef-b272-4b722c8e87ee");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void missingLaneKeepsTypeScaleAndReadOnlyMultiplier() {
        var tower = tower(DeveloperTowers.ALPHA, OWNER, 0);
        assertEquals(DeveloperBalance.patchScale(tower.type()), tower.patchEfficiency(null));
        assertTrue(DeveloperTowerData.addBug(tower, DeveloperBug.READ_ONLY));
        assertEquals(DeveloperBalance.patchScale(tower.type()) * (1 + DeveloperBug.READ_ONLY.primary()),
                tower.patchEfficiency(null), 1e-12);
    }

    @Test
    void auraTracksOnlyOtherOwnedNearbyTestBuildsAndRechecksRemoval() {
        var lane = new PlayerLane(TeamId.RED, 0, OWNER, null, testLayout(0));
        var recipient = tower(DeveloperTowers.TEST_BUILD, OWNER, 0);
        var first = tower(DeveloperTowers.TEST_BUILD, OWNER, 1);
        var second = tower(DeveloperTowers.TEST_BUILD, OWNER, 2);
        lane.addTower(recipient);
        lane.addTower(first);
        lane.addTower(second);
        lane.addTower(tower(DeveloperTowers.TEST_BUILD, UUID.randomUUID(), 1));
        lane.addTower(tower(DeveloperTowers.TEST_BUILD, OWNER, 10000));
        lane.addTower(tower(DeveloperTowers.ALPHA, OWNER, 1));
        double base = DeveloperBalance.patchScale(recipient.type());
        double bonus = DeveloperBalance.testBuildAuraBonus();
        assertEquals(base * (1 + bonus + bonus), recipient.patchEfficiency(lane), 1e-12);
        lane.removeTower(first);
        assertEquals(base * (1 + bonus), recipient.patchEfficiency(lane), 1e-12);
        lane.removeTower(second);
        assertEquals(base, recipient.patchEfficiency(lane), 1e-12);
    }

    private static DeveloperTower tower(TowerType type, UUID owner, int x) {
        return new DeveloperTower(type, owner, TeamId.RED, 0, new GridPosition(x, 64, 0));
    }

    private static LaneRegionLayout testLayout(int laneId) {
        return new LaneRegionLayout(laneId, new Vec3(0.5, 64, 0.5),
                List.of(new Vec3(0.5, 64, 2.5)), new Vec3(0.5, 64, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(1024, 66, 10)),
                List.of(new GridPosition(0, 63, 10)));
    }
}
