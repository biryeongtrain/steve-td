package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class SiegeBreakerSummon extends SummonMonsterType {
    public SiegeBreakerSummon() {
        super(
                "siege_breaker",
                "Siege Breaker",
                140,
                8,
                220,
                4,
                15,
                AttackKind.MELEE,
                "minecraft:ravager",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T3,
                List.of(SummonRole.SIEGE),
                List.of(SummonAbilityActivation.CONDITIONAL),
                18
        );
    }
}
