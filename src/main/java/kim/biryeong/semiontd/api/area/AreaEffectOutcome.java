package kim.biryeong.semiontd.api.area;

public enum AreaEffectOutcome {
    UNCHANGED,
    APPLIED,
    KILLED;

    public boolean changed() {
        return this != UNCHANGED;
    }

    public boolean killed() {
        return this == KILLED;
    }
}
