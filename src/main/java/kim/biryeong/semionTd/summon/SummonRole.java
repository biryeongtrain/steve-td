package kim.biryeong.semiontd.summon;

public enum SummonRole {
    SWARM(0),
    RUSH(5),
    SIEGE(15),
    SUPPORT(35),
    TANK(45),
    DISRUPTOR(45);

    private final int targetPriority;

    SummonRole(int targetPriority) {
        this.targetPriority = targetPriority;
    }

    public int targetPriority() {
        return targetPriority;
    }

    public boolean allowsLowTierCooldownAbility() {
        return this == SUPPORT || this == DISRUPTOR;
    }
}
