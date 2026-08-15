package kim.biryeong.semiontd.tower.futureagency;

public enum FutureAgencyRole {
    COMBAT("전투", "minecraft:pillager"),
    SUPPRESSION("제압", "minecraft:witch"),
    PROTECTION("방호", "minecraft:vindicator");

    private final String displayName;
    private final String entityTypeId;

    FutureAgencyRole(String displayName, String entityTypeId) {
        this.displayName = displayName;
        this.entityTypeId = entityTypeId;
    }

    public String displayName() {return displayName;}
    public String entityTypeId() {return entityTypeId;}
}
