package kim.biryeong.semiontd.trait;


public record TraitSelectionConfig(boolean enabled, int selectionDurationSeconds) {
    public static final int DEFAULT_SELECTION_DURATION_SECONDS = 45;

    public TraitSelectionConfig {
        selectionDurationSeconds = selectionDurationSeconds <= 0
                ? DEFAULT_SELECTION_DURATION_SECONDS
                : selectionDurationSeconds;
    }

    public static TraitSelectionConfig defaultConfig() {
        return new TraitSelectionConfig(true, DEFAULT_SELECTION_DURATION_SECONDS);
    }

    public int selectionDurationTicks() {
        return selectionDurationSeconds * 20;
    }
}
