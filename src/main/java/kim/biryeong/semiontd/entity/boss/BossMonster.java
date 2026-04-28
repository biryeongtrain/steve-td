package kim.biryeong.semiontd.entity.boss;

import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.TeamId;

public final class BossMonster {
    private final TeamId teamId;
    private final double maxHealth;
    private final double attackDamage;
    private final int attackIntervalTicks;
    private double health;
    private int cooldownTicks;
    private BossState state = BossState.ALIVE;

    public BossMonster(TeamId teamId, double maxHealth, double attackDamage, int attackIntervalTicks) {
        this.teamId = teamId;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.attackDamage = attackDamage;
        this.attackIntervalTicks = Math.max(1, attackIntervalTicks);
    }

    public static BossMonster defaultBoss(TeamId teamId) {
        return new BossMonster(teamId, 1000, 25, 20);
    }

    public TeamId teamId() {
        return teamId;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public BossState state() {
        return state;
    }

    public boolean isAlive() {
        return state == BossState.ALIVE;
    }

    public void damage(double amount) {
        if (!isAlive() || amount <= 0) {
            return;
        }
        health = Math.max(0, health - amount);
        if (health <= 0) {
            state = BossState.DEAD;
        }
    }

    public void tickAttack(Monster target) {
        if (!isAlive() || target == null || target.isRemoved()) {
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        target.damage(attackDamage);
        cooldownTicks = attackIntervalTicks;
    }
}
