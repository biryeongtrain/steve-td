package kim.biryeong.semiontd.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class TimedEffectSetTest {
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath("semion-td", "persistent-test");

    @Test
    void persistentEffectsReplaceAndRemoveWithoutTickingDown() {
        TimedEffectSet effects = new TimedEffectSet();

        assertTrue(effects.setPersistent(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, SOURCE, 0.10));
        effects.apply(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, 0.05, 2);
        effects.tick();
        effects.tick();

        assertTrue(effects.hasPersistent(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, SOURCE));
        assertEquals(0.10, effects.magnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS), 0.000_001);
        assertTrue(effects.setPersistent(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, SOURCE, 0.20));
        assertEquals(0.20, effects.magnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS), 0.000_001);
        assertTrue(effects.setPersistent(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, SOURCE, 0.0));
        assertFalse(effects.hasPersistent(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS));
        assertEquals(0.0, effects.magnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS), 0.000_001);
    }

    @Test
    void stunExpiresWithoutRemovingSlowOrOwnerImmunity() {
        TimedEffectSet effects = new TimedEffectSet();
        effects.apply(TimedEffectType.MONSTER_STUN, 1.0, 2);
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, 0.5, 5);
        assertTrue(effects.apply(TimedEffectType.MONSTER_STUN_IMMUNITY, SOURCE, 1.0, 7));

        effects.tick();
        assertEquals(1, effects.remainingTicks(TimedEffectType.MONSTER_STUN));
        effects.tick();

        assertEquals(0.0, effects.magnitude(TimedEffectType.MONSTER_STUN));
        assertEquals(0.5, effects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION));
        assertEquals(5, effects.remainingTicks(TimedEffectType.MONSTER_STUN_IMMUNITY, SOURCE));
        assertFalse(effects.apply(TimedEffectType.MONSTER_STUN_IMMUNITY, SOURCE, 1.0, 7));
    }

    @Test
    void monsterDebuffTypesExcludeBeneficialEffects() {
        assertTrue(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_STUN.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_POISONED.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_MARKED.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_IGNITED.isMonsterDebuff());
        assertFalse(TimedEffectType.MONSTER_DAMAGE_REDUCTION.isMonsterDebuff());
        assertFalse(TimedEffectType.MONSTER_MOVE_SPEED_BONUS.isMonsterDebuff());
        assertFalse(TimedEffectType.MONSTER_STUN_IMMUNITY.isMonsterDebuff());
    }
}
