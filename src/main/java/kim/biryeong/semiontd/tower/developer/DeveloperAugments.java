package kim.biryeong.semiontd.tower.developer;

import java.util.Comparator;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;

final class DeveloperAugments {
    static final String COPIER = "job_developer_towers_s";
    static final String INTENDED = "job_developer_towers_g1";
    static final String SUPER_HOTFIX = "job_developer_towers_g2";
    static final String BATCH = "job_developer_towers_p";

    private DeveloperAugments() {
    }

    static boolean conditionalDamageBug(DeveloperBug bug) {
        return bug == DeveloperBug.BOUNDARY || bug == DeveloperBug.BUFFER_OVERRUN
                || bug == DeveloperBug.HARDCODED || bug == DeveloperBug.STEALTH || bug == DeveloperBug.LAZY_LOADING;
    }

    static void onBugAdded(Tower source, DeveloperBug bug) {
        PlayerLane lane = source.attachedLane();
        if (!(source instanceof DeveloperTower) || !DeveloperTowers.isGrowthTower(source.type())
                || lane == null || !conditionalDamageBug(bug) || !AugmentCombat.allowsTriggers()
                || !source.augmentSnapshot().has(COPIER)
                || !DeveloperStates.of(source.ownerPlayer()).consumeFirstCopiedBug()) {
            return;
        }
        lane.towers().stream().filter(DeveloperTower.class::isInstance).map(DeveloperTower.class::cast)
                .filter(target -> target != source && source.ownerPlayer().equals(target.ownerPlayer())
                        && source.teamId() == target.teamId() && source.laneId() == target.laneId()
                        && DeveloperTowers.isGrowthTower(target.type()))
                .filter(target -> !target.hasBug(bug) && !DeveloperTowerData.hasCopiedBug(target, bug))
                .min(Comparator.comparingDouble(target -> distanceSquared(source.position(), target.position())))
                .ifPresent(target -> {
                    DeveloperTowerData.addCopiedBug(target, bug);
                    target.onStateChanged(lane);
                    DeveloperVfx.reproduce((DeveloperTower) source, target);
                });
    }

    static double bonusScale(DeveloperTower tower, DeveloperBug bug) {
        if (tower.hasBug(bug)) {
            return 1.0;
        }
        return DeveloperTowerData.hasCopiedBug(tower, bug)
                ? tower.augmentSnapshot().parameter(COPIER, "bonusRatio", .5) : 0.0;
    }

    private static double distanceSquared(GridPosition first, GridPosition second) {
        double x = (double) first.x() - second.x();
        double y = (double) first.y() - second.y();
        double z = (double) first.z() - second.z();
        return x * x + y * y + z * z;
    }
}
