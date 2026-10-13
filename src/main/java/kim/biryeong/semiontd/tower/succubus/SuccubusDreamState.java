package kim.biryeong.semiontd.tower.succubus;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;

final class SuccubusDreamState {
    UUID sourceOwner;
    Tower lastSource;
    SemionMonsterEntity monster;
    PlayerLane lane;
    int stacks;
    int remainingTicks;
    int asleepTicks;
    int immunityTicks;
    int sleepCount;
    int sleepAttackTicks;
    int contagionDepth;
    double sleepLostHealth;
    boolean asleep;
    boolean deathHandled;

    boolean add(PlayerLane lane, Tower source, int amount, int sleepDurationTicks,
                               boolean allowSleep) {
        if (this.asleep || this.immunityTicks > 0) return false;
        if (this.stacks == 0) this.sourceOwner = source.ownerPlayer();
        int previous = this.stacks;
        this.stacks = Math.min(SuccubusBalance.maxStacks(), this.stacks + amount);
        this.remainingTicks = SuccubusBalance.stackDurationTicks();
        this.lastSource = source;
        this.lane = lane;
        if (allowSleep && this.stacks >= SuccubusBalance.maxStacks()) {
            this.asleep = true;
            this.asleepTicks = sleepDurationTicks;
            this.sleepLostHealth = 0.0;
            this.sleepCount++;
            this.sleepAttackTicks = 0;
            this.contagionDepth = 0;
        }
        return this.stacks != previous || this.asleep;
    }

    void tickCounters() {
        if (this.immunityTicks > 0) this.immunityTicks--;
        if (this.asleep) this.asleepTicks--;
        else if (this.stacks > 0) this.remainingTicks--;
    }

    void clearStacks() {
        this.stacks = 0;
        this.remainingTicks = 0;
        this.asleep = false;
        this.asleepTicks = 0;
        this.sleepLostHealth = 0.0;
        this.sleepAttackTicks = 0;
        this.contagionDepth = 0;
    }

    void clearStacksForWake() {
        clearStacks();
        this.immunityTicks = SuccubusBalance.awakenedImmunityTicks();
    }
}
