package kim.biryeong.semiontd.tower.legion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.augment.AugmentTower;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IllusionAugmentExclusionTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allAugmentBodiesAreRejectedBeforeCatalogFactoryOrEntityCreation() {
        UUID owner = UUID.randomUUID();
        GridPosition position = new GridPosition(0, 0, 0);
        var summoner = new LegionGlobalIllusionTower(LegionTowers.ILLUSION_TOWER, owner, TeamId.RED, 1, position);
        for (var type : AugmentTowers.all()) {
            var source = new AugmentTower(type, owner, TeamId.RED, 1, position, position);
            // No world is needed: forbidden sources must stop before reaching entity construction.
            assertDoesNotThrow(() -> summoner.spawnQueuedClone(null, source, IllusionProfile.defaults(), Vec3.ZERO), type.id());
        }
    }
}
