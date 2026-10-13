package kim.biryeong.semiontd.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class TimedEffectSetTest {
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath("semion-td", "persistent-test");

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
    void diagnosticSnapshotsKeepSourcesAndDurationsWithoutSharingMutableState() {
        TimedEffectSet effects = new TimedEffectSet();
        effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, 0.1, 40);
        effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, SOURCE, 0.2, 80);
        effects.setPersistent(TimedEffectType.TOWER_DAMAGE_BONUS, SOURCE, 0.3);
        var captured = effects.snapshot();
        assertEquals(3, captured.size());
        assertEquals(0.6, captured.stream().mapToDouble(TimedEffectSet.Snapshot::magnitude).sum(), 0.00001);
        assertEquals(80, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS));
        for (int i = 0; i < 80; i++) effects.tick();
        assertEquals(3, captured.size());
        assertTrue(captured.stream().anyMatch(value -> Integer.valueOf(80).equals(value.remainingTicks())));
        assertEquals(1, effects.snapshot().size());
        assertTrue(effects.snapshot().getFirst().persistent());
        assertEquals(null, effects.snapshot().getFirst().remainingTicks());
    }

    @Test
    void monsterDebuffTypesExcludeBeneficialEffects() {
        assertTrue(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_ARMOR_REDUCTION.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_STUN.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_POISONED.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_MARKED.isMonsterDebuff());
        assertTrue(TimedEffectType.MONSTER_IGNITED.isMonsterDebuff());
        assertFalse(TimedEffectType.MONSTER_DAMAGE_REDUCTION.isMonsterDebuff());
        assertFalse(TimedEffectType.MONSTER_MOVE_SPEED_BONUS.isMonsterDebuff());
        assertFalse(TimedEffectType.MONSTER_STUN_IMMUNITY.isMonsterDebuff());
    }

    @Test
    void multiplicativeReadLimitsSourcesWithoutChangingLegacySumsOrExpiry() {
        var effects = new TimedEffectSet();
        var aura = TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA;
        for (int i = 0; i < 4; i++) {
            var source = Identifier.fromNamespaceAndPath("semion-td", "caster_" + i);
            effects.setPersistent(aura, source, .05);
            effects.setPersistent(aura, source, .05);
        }
        assertEquals(.20, effects.magnitude(aura), 1e-12);
        assertEquals(.142625, effects.multiplicativeMagnitude(aura, 3), 1e-12);
        assertEquals(0, effects.multiplicativeMagnitude(aura, 0));
        effects.apply(TimedEffectType.TOWER_DAMAGE_REDUCTION, SOURCE, .30, 2);
        effects.apply(TimedEffectType.TOWER_DAMAGE_REDUCTION, Identifier.withDefaultNamespace("other"), .20, 4);
        assertEquals(.50, effects.magnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), 1e-12);
        effects.tick();
        effects.tick();
        assertEquals(.20, effects.magnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), 1e-12);
        assertEquals(.142625, effects.multiplicativeMagnitude(aura, 3), 1e-12);
        effects.setPersistent(aura, SOURCE, 1.5);
        assertEquals(1, effects.multiplicativeMagnitude(aura, 3));
        effects.remove(aura);
        assertEquals(0, effects.multiplicativeMagnitude(aura, 3));
    }
    @Test
    void multiplicativeSelectionMatchesSortedReferenceBitForBitAcrossMutations() {
        var effects = new TimedEffectSet();
        var random = new java.util.Random(0x53454d494f4eL);
        var types = new TimedEffectType[]{TimedEffectType.TOWER_PROTEGO,
                TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA, TimedEffectType.TOWER_DAMAGE_REDUCTION};
        for (int step = 0; step < 1200; step++) {
            var type = types[random.nextInt(types.length)];
            var source = Identifier.fromNamespaceAndPath("semion-td", "differential_" + random.nextInt(48));
            double magnitude = random.nextInt(3) == 0 ? .05 : random.nextDouble() * .5;
            int duration = 1 + random.nextInt(12);
            switch (random.nextInt(7)) {
                case 0 -> effects.apply(type, magnitude, duration);
                case 1 -> effects.apply(type, source, magnitude, duration);
                case 2 -> effects.refresh(type, source, magnitude, duration);
                case 3 -> effects.setPersistent(type, source, magnitude);
                case 4 -> effects.setPersistent(type, source, 0);
                case 5 -> effects.tick();
                default -> { if (step % 11 == 0) effects.remove(type); }
            }
            var before = effects.snapshot();
            for (var inspected : types) {
                for (int limit : new int[]{-1, 0, 1, 2, 3, 8, 32, 512, Integer.MAX_VALUE}) {
                    assertEquals(Double.doubleToRawLongBits(sortedReference(effects, inspected, limit)),
                            Double.doubleToRawLongBits(effects.multiplicativeMagnitude(inspected, limit)),
                            "step=" + step + " type=" + inspected + " limit=" + limit);
                }
            }
            assertEquals(before, effects.snapshot());
        }
    }

    @Test
    void multiplicativeSelectionKeepsNonfiniteFilteringAndSingleSourceRounding() {
        var effects = new TimedEffectSet();
        var type = TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA;
        double[] values = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0, 0.0,
                Double.MIN_VALUE, Math.nextDown(.05), .05, .05, Math.nextUp(.05), .1,
                Math.nextDown(1.0), 1.0, 1.5, Double.MAX_VALUE};
        for (int index = 0; index < values.length; index++) {
            var source = Identifier.fromNamespaceAndPath("semion-td", "edge_" + index);
            effects.apply(type, source, values[index], 3);
            effects.setPersistent(type, source, values[index]);
            for (int limit : new int[]{1, 2, 3, 8, 64, Integer.MAX_VALUE}) {
                assertEquals(Double.doubleToRawLongBits(sortedReference(effects, type, limit)),
                        Double.doubleToRawLongBits(effects.multiplicativeMagnitude(type, limit)));
            }
        }
        for (double value : values) {
            effects.remove(type);
            effects.apply(type, value, 3);
            for (int limit : new int[]{1, 2, 3, Integer.MAX_VALUE}) {
                assertEquals(Double.doubleToRawLongBits(sortedReference(effects, type, limit)),
                        Double.doubleToRawLongBits(effects.multiplicativeMagnitude(type, limit)));
            }
        }
        effects.remove(type);
        effects.apply(type, .1, 1);
        assertEquals(Double.doubleToRawLongBits(1.0 - (1.0 - .1)),
                Double.doubleToRawLongBits(effects.multiplicativeMagnitude(type, 1)));
        effects.tick();
        assertEquals(0, Double.doubleToRawLongBits(effects.multiplicativeMagnitude(type, 1)));
        assertEquals(0, Double.doubleToRawLongBits(effects.multiplicativeMagnitude(null, 1)));
    }

    private static double sortedReference(TimedEffectSet effects, TimedEffectType type, int limit) {
        if (type == null || limit <= 0) return 0.0;
        var values = new java.util.ArrayList<Double>();
        for (var effect : effects.snapshot()) {
            if (effect.type() == type) values.add(effect.magnitude());
        }
        values.removeIf(value -> !Double.isFinite(value));
        values.sort(java.util.Comparator.reverseOrder());
        double remaining = 1.0;
        for (int index = 0; index < Math.min(limit, values.size()); index++) {
            remaining *= 1.0 - Math.clamp(values.get(index), 0.0, 1.0);
        }
        return 1.0 - remaining;
    }

}
