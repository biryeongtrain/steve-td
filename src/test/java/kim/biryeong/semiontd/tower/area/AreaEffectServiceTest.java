package kim.biryeong.semiontd.tower.area;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import org.junit.jupiter.api.Test;

class AreaEffectServiceTest {
    @Test
    void renderPolicyDistinguishesTriggersFromStateChanges() {
        assertTrue(AreaEffectService.shouldRender(AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH), 0));
        assertFalse(AreaEffectService.shouldRender(AreaVfxSpec.onChange(AreaVfxStyles.BUFF), 0));
        assertTrue(AreaEffectService.shouldRender(AreaVfxSpec.onChange(AreaVfxStyles.BUFF), 1));
        assertFalse(AreaEffectService.shouldRender(AreaVfxSpec.none(), 1));
    }
}
