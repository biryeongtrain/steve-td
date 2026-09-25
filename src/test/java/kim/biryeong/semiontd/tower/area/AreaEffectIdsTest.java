package kim.biryeong.semiontd.tower.area;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AreaEffectIdsTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nativeCloneSuffixBecomesAValidResourcePath() {
        assertEquals("semion-td:tower/legion_fixture/illusion/merged_clone",
                AreaEffectIds.tower(tower("legion_fixture#illusion"), "merged_clone").toString());
    }

    @Test
    void ordinaryTowerAndUnknownSourceIdsRemainUnchanged() {
        assertEquals("semion-td:tower/legion_fixture/merged_clone",
                AreaEffectIds.tower(tower("legion_fixture"), "merged_clone").toString());
        assertEquals("semion-td:tower/unknown/merged_clone",
                AreaEffectIds.tower(null, "merged_clone").toString());
    }

    private static ProductionTower tower(String id) {
        return new ProductionTower(new TowerType(id, "Fixture", TowerCategory.DIRECT, 100,
                200, 20, 200, 20, 10), UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 0, 0));
    }
}
