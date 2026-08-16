package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarlockAwakeningProgressTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetState() {
        WarlockAwakeningProgress.clearAllForTesting();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void awakeningUnlocksExactlyAtConfiguredKillThreshold() {
        UUID owner = UUID.randomUUID();

        for (int kill = 1; kill < 1350; kill++) {
            assertFalse(WarlockAwakeningProgress.recordKill(owner));
        }
        WarlockAwakeningProgress.Snapshot locked = WarlockAwakeningProgress.snapshot(owner);
        assertEquals(1349L, locked.kills());
        assertEquals(1350L, locked.requiredKills());
        assertFalse(locked.unlocked());

        assertTrue(WarlockAwakeningProgress.recordKill(owner));
        WarlockAwakeningProgress.Snapshot unlocked = WarlockAwakeningProgress.snapshot(owner);
        assertEquals(1350L, unlocked.kills());
        assertTrue(unlocked.unlocked());
        assertFalse(WarlockAwakeningProgress.recordKill(owner));
    }

    @Test
    void clearRemovesProgressForTheNextMatch() {
        UUID owner = UUID.randomUUID();
        WarlockAwakeningProgress.recordKill(owner);

        WarlockAwakeningProgress.clear(owner);

        assertEquals(0L, WarlockAwakeningProgress.snapshot(owner).kills());
        assertFalse(WarlockAwakeningProgress.snapshot(owner).unlocked());
    }
}
