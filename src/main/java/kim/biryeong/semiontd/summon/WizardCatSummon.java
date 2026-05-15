package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class WizardCatSummon extends SummonMonsterType {
    public WizardCatSummon() {
        super(
                "wizard_cat",
                SummonDisplayNames.WIZARD_CAT,
                125,
                8,
                95,
                0,
                9,
                AttackKind.RANGED,
                "minecraft:cat",
                "semion-td:summon/t3_wizard_cat",
                DamageType.MAGIC,
                9,
                SummonTier.T3,
                List.of(SummonRole.DISRUPTOR),
                List.of(SummonAbilityActivation.COOLDOWN),
                14
        );
    }
}
