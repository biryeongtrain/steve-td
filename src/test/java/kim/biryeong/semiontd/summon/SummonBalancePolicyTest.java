package kim.biryeong.semiontd.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SummonBalancePolicyTest {
    @Test
    void lateRoundsStrengthenAttackWithoutChangingHealthScaling() {
        assertEquals(1.65, SummonBalancePolicy.summonAttackDamageMultiplier(14), 0.0001);
        assertEquals(1.775, SummonBalancePolicy.summonAttackDamageMultiplier(15), 0.0001);
        assertEquals(2.40, SummonBalancePolicy.summonAttackDamageMultiplier(20), 0.0001);
        assertEquals(3.65, SummonBalancePolicy.summonAttackDamageMultiplier(30), 0.0001);
        assertEquals(4.90, SummonBalancePolicy.summonAttackDamageMultiplier(40), 0.0001);

        assertEquals(1.65, SummonBalancePolicy.summonHealthMultiplier(14), 0.0001);
        assertEquals(1.75, SummonBalancePolicy.summonHealthMultiplier(15), 0.0001);
        assertEquals(2.25, SummonBalancePolicy.summonHealthMultiplier(20), 0.0001);
        assertEquals(3.25, SummonBalancePolicy.summonHealthMultiplier(30), 0.0001);
        assertEquals(4.25, SummonBalancePolicy.summonHealthMultiplier(40), 0.0001);
    }
}
