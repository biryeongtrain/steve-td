package kim.biryeong.semiontd.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WarlockTowerJobTest {
    @Test
    void summonIncomePenaltyIsSevenPercentRoundedUp() {
        WarlockTowerJob job = new WarlockTowerJob();

        assertEquals(93L, job.modifySummonIncomeGain(null, null, 100L));
        assertEquals(19L, job.modifySummonIncomeGain(null, null, 20L));
    }
}
