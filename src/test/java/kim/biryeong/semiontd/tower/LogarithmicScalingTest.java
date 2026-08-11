package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogarithmicScalingTest {
    @Test
    void logarithmicBonusPreservesTheLinearRangeWithoutAHardCap() {
        assertEquals(0.0, LogarithmicScaling.logarithmicBonus(-1.0, 120.0), 0.0001);
        assertEquals(119.0, LogarithmicScaling.logarithmicBonus(119.0, 120.0), 0.0001);
        assertEquals(120.0, LogarithmicScaling.logarithmicBonus(120.0, 120.0), 0.0001);
        assertEquals(239.1902, LogarithmicScaling.logarithmicBonus(324.0, 120.0), 0.0001);
        assertEquals(271.9486, LogarithmicScaling.logarithmicBonus(300.0, 180.0), 0.0001);
        assertEquals(396.7151, LogarithmicScaling.logarithmicBonus(600.0, 180.0), 0.0001);
        assertEquals(488.6637, LogarithmicScaling.logarithmicBonus(1_000.0, 180.0), 0.0001);
    }

    @Test
    void logarithmicBonusSupportsAnIndependentThresholdAndScale() {
        assertEquals(150.0, LogarithmicScaling.logarithmicBonus(150.0, 150.0, 25.0), 0.0001);
        assertEquals(201.8607, LogarithmicScaling.logarithmicBonus(324.0, 150.0, 25.0), 0.0001);
        assertEquals(223.6109, LogarithmicScaling.logarithmicBonus(600.0, 150.0, 25.0), 0.0001);
        assertEquals(3972.9551, LogarithmicScaling.logarithmicBonus(6_000.0, 3_000.0, 500.0), 0.0001);
        assertEquals(4354.0251, LogarithmicScaling.logarithmicBonus(10_000.0, 3_000.0, 500.0), 0.0001);
    }
}
