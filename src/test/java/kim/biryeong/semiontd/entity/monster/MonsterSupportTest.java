package kim.biryeong.semiontd.entity.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class MonsterSupportTest {
    @Test
    void typedShieldsAbsorbAfterDefenseAndTrueDamageBypassesBoth() {
        Monster source = monster("support", TeamId.RED, 1);
        Monster target = monster("target", TeamId.RED, 1);
        target.grantShield(DamageType.PHYSICAL, 30, 100, 0, source);
        target.grantShield(DamageType.MAGIC, 40, 100, 0, source);

        var physical = target.damageResult(100, DamageType.PHYSICAL);
        assertEquals(20, physical.healthDamageAttempted());
        assertEquals(20, physical.appliedDamage());
        assertEquals(30, physical.absorbedDamage());
        var magic = target.damageResult(100, DamageType.MAGIC);
        assertEquals(40, magic.healthDamageAttempted());
        assertEquals(40, magic.absorbedDamage());
        target.grantShield(DamageType.PHYSICAL, 50, 100, 0, source);
        target.grantShield(DamageType.MAGIC, 50, 100, 0, source);
        target.damage(100, DamageType.TRUE);
        assertEquals(840, target.health());
        assertEquals(50, target.shieldRemaining(DamageType.PHYSICAL, 0));
        assertEquals(50, target.shieldRemaining(DamageType.MAGIC, 0));
    }

    @Test
    void refreshTakesMaximumRemainderAndExpiryAndOnlyIncreaseChangesBuyer() {
        Monster first = monster("first", TeamId.RED, 1);
        Monster second = monster("second", TeamId.RED, 1);
        Monster target = monster("target", TeamId.RED, 1);
        target.grantShield(DamageType.PHYSICAL, 50, 10, 0, first);
        target.damage(40, DamageType.PHYSICAL);
        target.grantShield(DamageType.PHYSICAL, 20, 30, 2, second);
        assertEquals(30, target.shieldRemaining(DamageType.PHYSICAL, 2));
        assertEquals(first.ownerPlayer(), target.shieldBuyer(DamageType.PHYSICAL, 2));
        target.damage(40, DamageType.PHYSICAL);
        target.grantShield(DamageType.PHYSICAL, 40, 5, 3, second);
        assertEquals(second.ownerPlayer(), target.shieldBuyer(DamageType.PHYSICAL, 3));
        assertEquals(40, target.shieldRemaining(DamageType.PHYSICAL, 20));
        assertEquals(0, target.shieldRemaining(DamageType.PHYSICAL, 32));
        target.expireShields(40);
        assertEquals(40, first.supportMetrics().snapshot().physicalShieldAbsorbed());
        assertEquals(30, second.supportMetrics().snapshot().physicalShieldGranted());
        assertEquals(40, second.supportMetrics().snapshot().physicalShieldExpired());
    }

    @Test
    void damageResultRetainsPostShieldAttemptBeforeHealthCap() {
        Monster source = monster("support", TeamId.RED, 1);
        Monster target = monster("target", TeamId.RED, 1);
        target.syncHealth(10);
        target.grantShield(DamageType.PHYSICAL, 30, 100, 0, source);
        var damage = target.damageResult(200, DamageType.PHYSICAL);
        assertEquals(70, damage.healthDamageAttempted());
        assertEquals(10, damage.appliedDamage());
        assertEquals(30, damage.absorbedDamage());
        assertFalse(target.isAlive());
    }

    @Test
    void casterDeathDoesNotLoseAbsorptionAttributionOrRemainingExpiryMetrics() {
        Monster source = monster("source", TeamId.RED, 1);
        Monster target = monster("target", TeamId.RED, 1);
        UUID buyer = source.ownerPlayer().orElseThrow();
        target.grantShield(DamageType.MAGIC, 50, 10, 0, source);
        source.syncHealth(0);
        source.markRemoved();
        assertEquals(Optional.of(buyer), target.shieldBuyer(DamageType.MAGIC, 1));
        target.damage(25, DamageType.MAGIC);
        target.expireShields(10);
        var snapshot = source.supportMetrics().snapshot();
        assertEquals(20, snapshot.magicShieldAbsorbed());
        assertEquals(30, snapshot.magicShieldExpired());
        assertEquals(snapshot, MonsterSupportMetrics.Snapshot.empty().plus(snapshot));
    }

    @Test
    void supportRejectsOtherLaneBossProxyLeakedAndDeadTargets() {
        Monster source = monster("support", TeamId.RED, 1);
        assertFalse(monster("other-team", TeamId.BLUE, 1).canReceiveUtilitySupportFrom(source));
        assertFalse(monster("other-lane", TeamId.RED, 2).canReceiveUtilitySupportFrom(source));
        assertFalse(monster("warden_boss_15", TeamId.RED, 1).canReceiveUtilitySupportFrom(source));
        Monster proxy = monster("proxy", TeamId.RED, 1);
        proxy.setOrigin(MonsterOrigin.BUILDER_PROXY);
        assertFalse(proxy.canReceiveUtilitySupportFrom(source));
        Monster leaked = monster("leaked", TeamId.RED, 1);
        leaked.markLaneLeakRecorded();
        assertFalse(leaked.canReceiveUtilitySupportFrom(source));
        Monster dead = monster("dead", TeamId.RED, 1);
        dead.syncHealth(0);
        dead.heal(500);
        assertEquals(0, dead.health());
        assertFalse(dead.grantShield(DamageType.PHYSICAL, 50, 100, 0, source));
    }

    @Test
    void supportMetricsRecordActualHealingAndOverhealWithoutFabricatingReduction() {
        Monster target = monster("target", TeamId.RED, 1);
        target.syncHealth(980);
        double before = target.health();
        target.heal(45);
        MonsterSupportMetrics metrics = new MonsterSupportMetrics();
        metrics.recordHealing(45, target.health() - before);
        assertEquals(1, metrics.snapshot().healingAttempts());
        assertEquals(45, metrics.snapshot().requestedHealing());
        assertEquals(20, metrics.snapshot().effectiveHealing());
        assertEquals(25, metrics.snapshot().overhealing());
        target.heal(Double.NaN);
        target.damage(Double.POSITIVE_INFINITY);
        assertEquals(1000, target.health());
    }

    @Test
    void logicalIdentityAndTransactionAreStableAcrossEntityReferences() {
        Monster target = monster("target", TeamId.RED, 1);
        UUID logicalId = target.logicalId();
        UUID purchase = UUID.randomUUID();
        target.setOrigin(MonsterOrigin.NORMAL_PAID);
        target.setSummonTransaction(purchase, 320, 15);
        target.setSummonTransaction(purchase, 320, 15);
        target.markMinecraftEntitySpawned(11, 0, 0, 0);
        target.markMinecraftEntitySpawned(12, 0, 0, 0);
        assertEquals(logicalId, target.logicalId());
        assertEquals(Optional.of(purchase), target.transactionId());
        assertEquals(320, target.paidEmerald());
        assertThrows(IllegalStateException.class, () -> target.setSummonTransaction(purchase, 0, 15));
        assertThrows(IllegalStateException.class, () -> target.setSummonTransaction(UUID.randomUUID(), 320, 15));
    }

    private static Monster monster(String id, TeamId team, int lane) {
        Monster monster = new Monster(id, team, lane, Optional.of(UUID.randomUUID()), Optional.of(TeamId.BLUE),
                1000, 100, 10, AttackKind.MELEE, "minecraft:zombie", null,
                DamageType.PHYSICAL, 25, null, List.of(), 0);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        return monster;
    }
}
