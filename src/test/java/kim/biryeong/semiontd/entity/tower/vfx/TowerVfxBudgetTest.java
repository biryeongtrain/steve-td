package kim.biryeong.semiontd.entity.tower.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TowerVfxBudgetTest {
    @Test
    void instancesAreIndependentAndRefillByTick() {
        TowerVfxBudget first = new TowerVfxBudget(100, 10);
        TowerVfxBudget second = new TowerVfxBudget(100, 10);

        assertEquals(80, first.claim(80, 0, false, 10, 20, 100));
        assertEquals(100, second.claim(100, 0, false, 10, 20, 100));
        assertEquals(40, first.claim(40, 0, false, 11, 20, 100));
    }

    @Test
    void essentialGeometryKeepsItsMinimumAfterBudgetExhaustion() {
        TowerVfxBudget bucket = new TowerVfxBudget(0, 10);

        assertEquals(12, bucket.claim(40, 12, true, 10, 20, 100));
        assertEquals(0, bucket.claim(40, 12, false, 10, 20, 100));
    }
}
