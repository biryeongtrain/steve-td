package kim.biryeong.semiontd.tower.plant;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;

public final class PlantAugments {
    public static final String WORLD_TREE = "job_plant_towers_p";
    public static final String GROWTH = "job_plant_towers_g1";

    private PlantAugments() {}

    public static void onSelected(PlayerLane lane, String cardId, AugmentConfig config) {
        if (lane == null || !(GROWTH.equals(cardId) || ("semiontd:" + GROWTH).equals(cardId))) return;
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof PlantCombatTower plant && lane.ownerPlayer().equals(tower.ownerPlayer())) {
                PlantSoil soil = plant.standingSoil();
                if (soil == PlantSoil.MEADOW || soil == PlantSoil.PODZOL) {
                    plant.addGrowthRounds((int) config.parameter(cardId, "growthRounds", 5), lane);
                }
            }
        }
    }

    public static PlantTerraformTower worldTree(PlayerLane lane, UUID owner) {
        return lane == null ? null : worldTree(lane, owner, lane.augmentSnapshot());
    }

    private static PlantTerraformTower worldTree(PlayerLane lane, UUID owner, AugmentSnapshot snapshot) {
        if (lane == null || !snapshot.has(WORLD_TREE)) return null;
        UUID selected = snapshot.choice(WORLD_TREE).primaryTargetId();
        if (selected == null) return null;
        return lane.towers().stream().filter(PlantTerraformTower.class::isInstance).map(PlantTerraformTower.class::cast)
                .filter(tower -> selected.equals(tower.logicalId()) && owner.equals(tower.ownerPlayer()))
                .findFirst().orElse(null);
    }

    public static boolean inWorldTree(PlayerLane lane, UUID owner, GridPosition position) {
        return lane != null && inWorldTree(lane, owner, position, lane.augmentSnapshot());
    }

    public static boolean inWorldTree(Tower tower) {
        return inWorldTree(tower.attachedLane(), tower.ownerPlayer(), tower.position(), tower.augmentSnapshot());
    }

    private static boolean inWorldTree(PlayerLane lane, UUID owner, GridPosition position, AugmentSnapshot snapshot) {
        PlantTerraformTower tree = worldTree(lane, owner, snapshot);
        if (tree == null || position == null) return false;
        double radius = snapshot.parameter(WORLD_TREE, "radius", 8);
        double dx = tree.position().x() - position.x(), dz = tree.position().z() - position.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    public static double worldTreeBonus(Tower tower) {
        return inWorldTree(tower) ? tower.augmentSnapshot().parameter(WORLD_TREE, "statBonus", 0.8) : 0.0;
    }

    public static void refreshWorldTree(PlayerLane lane) {
        if (lane == null) return;
        for (Tower tower : List.copyOf(lane.towers())) {
            if (PlantTowers.isPlantTower(tower.type())) tower.onStateChanged(lane);
        }
    }
}
