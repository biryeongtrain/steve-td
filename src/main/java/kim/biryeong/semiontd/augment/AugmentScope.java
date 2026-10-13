package kim.biryeong.semiontd.augment;

public enum AugmentScope {
    COMMON("일반"), JOB_SPECIFIC("전용");

    private final String label;

    AugmentScope(String label) { this.label = label; }

    public String label() { return label; }

    public static AugmentScope of(AugmentDefinition definition) {
        return definition.requiredJobId() == null ? COMMON : JOB_SPECIFIC;
    }
}
