package kim.biryeong.semiontd.tower.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class FrostTowerEruptionSnapshotTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("frost-eruption-snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        FrostTeamEffects.unregisterPlayer(OWNER);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void oneSnapshotRetainsIndependentThresholdsDeadCopiesAndOwnerFiltering() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        List<TowerType> families = List.of(FrostTowers.ICE_VANGUARD, FrostTowers.ICE_BREAKER_T1,
                FrostTowers.FROZEN_DUMPLING_T1, FrostTowers.ICEBOX_T1);
        for (int count = 0; count <= 10; count++) {
            PlayerLane lane = lane();
            int expected = 0;
            for (int family = 0; family < families.size(); family++) {
                int familyCount = Math.max(0, count - family);
                expected += FrostBalance.eruptionStacksForFamilyCount(familyCount);
                for (int index = 0; index < familyCount; index++) {
                    var tower = new ProductionTower(families.get(family), OWNER, TeamId.RED, 1,
                            new GridPosition(index, 64, family));
                    if (index % 2 == 0) tower.syncHealth(0);
                    if (index % 3 == 0) tower.markTemporaryCopy(OWNER);
                    lane.addTower(tower);
                }
            }
            lane.addTower(new ProductionTower(FrostTowers.ICE_VANGUARD, new UUID(0, 1), TeamId.RED, 1,
                    new GridPosition(0, 64, 9)));
            assertEquals(FrostBalance.clampEruptionStacks(expected),
                    FrostTeamEffects.snapshotEruptionStacks(OWNER, TeamId.RED, lane));
            assertEquals(0, FrostTeamEffects.snapshotEruptionStacks(OWNER, TeamId.BLUE, lane));
            lane.clearTowers();
        }
    }

    private static PlayerLane lane() {
        return new PlayerLane(TeamId.RED, 1, OWNER, null, new LaneRegionLayout(1,
                new Vec3(.5, 64, .5), List.of(new Vec3(.5, 64, 2.5)), new Vec3(.5, 64, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(20, 66, 10)),
                List.of(new GridPosition(0, 63, 10))));
    }
}
