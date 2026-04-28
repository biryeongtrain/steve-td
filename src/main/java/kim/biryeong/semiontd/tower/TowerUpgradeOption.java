package kim.biryeong.semiontd.tower;

public record TowerUpgradeOption(
        String id,
        String displayName,
        String targetTypeId,
        long mineralCost
) {
    public TowerUpgradeOption {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tower upgrade id cannot be blank.");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        if (targetTypeId == null || targetTypeId.isBlank()) {
            throw new IllegalArgumentException("Tower upgrade target type id cannot be blank.");
        }
        if (mineralCost < 0) {
            throw new IllegalArgumentException("Tower upgrade mineral cost cannot be negative.");
        }
    }
}
