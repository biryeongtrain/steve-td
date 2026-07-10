package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VfxConfigTest {
    @Test
    void missingValuesUseDefaults() {
        VfxConfig config = new VfxConfig(null, null, null, null, null, null).normalized();

        assertTrue(config.enabled());
        assertTrue(config.areaDamageEnabled());
        assertTrue(config.asyncPlanning());
        assertEquals(4, config.maxSampledHitRays());
        assertEquals(1024, config.vanilla().refillPointsPerTick());
        assertEquals(4096, config.vanilla().burstCapacityPoints());
        assertEquals(2048, config.vanilla().maxPacketsPerTickPerRecipient());
        assertEquals(128, config.gcb().maxShapeInstructionsPerTick());
    }

    @Test
    void invalidValuesAreClampedWithoutOverridingExplicitBooleans() {
        VfxConfig config = new VfxConfig(
                false,
                false,
                false,
                99,
                new VfxConfig.VanillaConfig(1, 2, 99_999),
                new VfxConfig.GcbConfig(99_999)
        ).normalized();

        assertFalse(config.enabled());
        assertFalse(config.areaDamageEnabled());
        assertFalse(config.asyncPlanning());
        assertEquals(8, config.maxSampledHitRays());
        assertEquals(64, config.vanilla().refillPointsPerTick());
        assertEquals(64, config.vanilla().burstCapacityPoints());
        assertEquals(8192, config.vanilla().maxPacketsPerTickPerRecipient());
        assertEquals(512, config.gcb().maxShapeInstructionsPerTick());
    }
}
