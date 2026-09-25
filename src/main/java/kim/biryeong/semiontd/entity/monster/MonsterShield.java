package kim.biryeong.semiontd.entity.monster;

import java.util.Optional;
import java.util.UUID;

/** One non-stacking shield resource of one damage type. */
final class MonsterShield {
    private final DamageType type;
    private double remaining;
    private long expiresAtTick;
    private UUID buyer;
    private MonsterSupportMetrics source;

    MonsterShield(DamageType type) {
        this.type = type;
    }

    boolean grant(double amount, long gameTime, int durationTicks, Monster caster) {
        if (!Double.isFinite(amount) || amount <= 0.0 || durationTicks <= 0) {
            return false;
        }
        expire(gameTime);
        long expiry = gameTime + durationTicks;
        boolean changed = amount > remaining || expiry > expiresAtTick;
        if (amount > remaining) {
            caster.supportMetrics().recordShieldGranted(type, amount - remaining);
            remaining = amount;
            buyer = caster.ownerPlayer().orElse(null);
            source = caster.supportMetrics();
        }
        expiresAtTick = Math.max(expiresAtTick, expiry);
        return changed;
    }

    double absorb(double damage, long gameTime) {
        expire(gameTime);
        double result = Math.min(remaining, Math.max(0.0, damage));
        remaining -= result;
        if (source != null && result > 0.0) {
            source.recordShieldAbsorbed(type, result);
        }
        return result;
    }

    void expire(long gameTime) {
        if (gameTime < expiresAtTick) {
            return;
        }
        if (source != null && remaining > 0.0) {
            source.recordShieldExpired(type, remaining);
        }
        remaining = 0.0;
        expiresAtTick = 0;
        buyer = null;
        source = null;
    }

    double remaining(long gameTime) {
        expire(gameTime);
        return remaining;
    }

    Optional<UUID> buyer(long gameTime) {
        expire(gameTime);
        return Optional.ofNullable(buyer);
    }
}
