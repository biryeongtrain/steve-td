package kim.biryeong.semiontd.entity.monster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class MonsterDamageTest {
    @Test
    void damageUsesPercentageDefenseForPhysicalAndMagicOnly() {
        Monster physical = monster(20.0, 25.0);
        Monster magic = monster(20.0, 25.0);
        Monster trueDamage = monster(20.0, 25.0);

        physical.damage(100.0, DamageType.PHYSICAL);
        magic.damage(100.0, DamageType.MAGIC);
        trueDamage.damage(100.0, DamageType.TRUE);

        assertEquals(1000.0 - 100.0 * 100.0 / 120.0, physical.health(), 0.0001);
        assertEquals(920.0, magic.health(), 0.0001);
        assertEquals(900.0, trueDamage.health(), 0.0001);
    }

    @Test
    void temporaryArmorReductionAffectsOnlyPhysicalDamageWithoutChangingBaseDefense() {
        Monster physical = monster(100, 25);
        physical.damage(100, DamageType.PHYSICAL, 0.2);
        assertEquals(1000 - 100 * 100.0 / 180, physical.health(), 0.0001);
        assertEquals(100, physical.armor());
        double remaining = physical.health();
        physical.damage(100, DamageType.PHYSICAL);
        assertEquals(remaining - 50, physical.health(), 0.0001);

        Monster magic = monster(100, 25);
        magic.damage(100, DamageType.MAGIC, 0.2);
        assertEquals(920, magic.health(), 0.0001);
        Monster trueDamage = monster(100, 25);
        trueDamage.damage(100, DamageType.TRUE, 0.2);
        assertEquals(900, trueDamage.health(), 0.0001);
    }

    @Test
    void nonPositiveDamageDoesNothing() {
        Monster monster = monster(20.0, 25.0);

        monster.damage(0.0, DamageType.PHYSICAL);
        monster.damage(-10.0, DamageType.TRUE);

        assertEquals(1000.0, monster.health(), 0.0001);
    }

    private static Monster monster(double armor, double resistance) {
        return new Monster(
                "damage-test",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                1000.0,
                armor,
                10.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                resistance,
                null,
                List.of(),
                0
        );
    }
}
