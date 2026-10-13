package kim.biryeong.semiontd.tower.developer;

import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;

final class DeveloperTowerPatchEfficiency {
    private DeveloperTowerPatchEfficiency() {}

    static double resolve(DeveloperTower recipient, PlayerLane lane) {
        double scale = DeveloperBalance.patchScale(recipient.type());
        if (recipient.hasBug(DeveloperBug.READ_ONLY)) {
            scale *= 1.0 + DeveloperBug.READ_ONLY.primary();
        }
        scale *= 1.0 + testBuildAura(recipient, lane);
        return scale;
    }

    private static double testBuildAura(DeveloperTower recipient, PlayerLane lane) {
        if (lane == null) {
            return 0.0;
        }
        double radius = DeveloperBalance.testBuildAuraRadius();
        if (radius <= 0.0) {
            return 0.0;
        }
        double radiusSqr = radius * radius;
        double bonus = 0.0;
        for (var tower : lane.towers()) {
            if (tower == recipient || !DeveloperTowers.isTestBuild(tower.type())) {
                continue;
            }
            if (!recipient.ownerPlayer().equals(tower.ownerPlayer())) {
                continue;
            }
            if (distanceSqr(tower.position(), recipient.position()) <= radiusSqr) {
                bonus += DeveloperBalance.testBuildAuraBonus();
            }
        }
        return bonus;
    }

    private static double distanceSqr(GridPosition a, GridPosition b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
