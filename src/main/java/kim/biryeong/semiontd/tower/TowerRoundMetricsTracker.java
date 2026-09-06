package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;

public final class TowerRoundMetricsTracker {
    private final String towerTypeId;
    private final boolean presentAtWaveStart;
    private int currentTick;
    private int firstCombatTick = -1;
    private int lastCombatTick = -1;
    private double physicalDamageDealt;
    private double magicDamageDealt;
    private double damageTaken;
    private double healingDone;
    private Double waveStartMaxHealth;
    private double enemyHpDamage;
    private double augmentSpecialDamageDealt;
    private long killCount;
    private long survivalTicks;
    private int deathCount;
    private boolean alive = true;
    private boolean active = true;

    public TowerRoundMetricsTracker(String towerTypeId, boolean presentAtWaveStart) {
        this.towerTypeId = towerTypeId;
        this.presentAtWaveStart = presentAtWaveStart;
        this.waveStartMaxHealth = presentAtWaveStart ? null : 0.0;
    }

    public void setCurrentTick(int currentTick) {
        this.currentTick = Math.max(0, currentTick);
    }

    public void recordSurvivalTick(boolean alive) {
        updateAlive(alive);
        if (active && alive) {
            survivalTicks++;
        }
    }

    public void updateAlive(boolean alive) {
        if (this.alive && !alive) {
            deathCount++;
        }
        this.alive = alive;
    }

    public void markRemoved() {
        active = false;
        alive = false;
    }

    public void recordDamageDealt(double amount, DamageType damageType) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        if (damageType == DamageType.MAGIC) {
            magicDamageDealt += amount;
        } else {
            physicalDamageDealt += amount;
        }
        markCombatEvent();
    }

    public void recordDamageTaken(double amount) {
        if (Double.isFinite(amount) && amount > 0.0) {
            damageTaken += amount;
            markCombatEvent();
        }
    }

    public void recordHealingDone(double amount) {
        if (Double.isFinite(amount) && amount > 0.0) {
            healingDone += amount;
            markCombatEvent();
        }
    }

    public void captureWaveStartMaxHealth(double maxHealth) {
        if (waveStartMaxHealth == null && Double.isFinite(maxHealth) && maxHealth > 0.0) {
            waveStartMaxHealth = maxHealth;
        }
    }

    public void recordEnemyHealthDamage(double amount) {
        if (Double.isFinite(amount) && amount > 0.0) {
            enemyHpDamage += amount;
            markCombatEvent();
        }
    }

    /** Records separately resolved augment secondary HP damage, not bonuses embedded in a basic hit. */
    public void recordAugmentSpecialDamage(double actualHpDamage) {
        if (Double.isFinite(actualHpDamage) && actualHpDamage > 0.0) {
            augmentSpecialDamageDealt += actualHpDamage;
            markCombatEvent();
        }
    }

    public void recordKill() {
        killCount++;
        markCombatEvent();
    }

    public TowerRoundMetricsSnapshot snapshot() {
        return new TowerRoundMetricsSnapshot(
                towerTypeId,
                1,
                presentAtWaveStart ? 1 : 0,
                active && alive ? 1 : 0,
                deathCount,
                physicalDamageDealt,
                magicDamageDealt,
                damageTaken,
                healingDone,
                killCount,
                firstCombatTick,
                lastCombatTick,
                survivalTicks,
                waveStartMaxHealth,
                enemyHpDamage,
                augmentSpecialDamageDealt
        );
    }

    private void markCombatEvent() {
        if (firstCombatTick < 0) {
            firstCombatTick = currentTick;
        }
        lastCombatTick = currentTick;
    }
}
