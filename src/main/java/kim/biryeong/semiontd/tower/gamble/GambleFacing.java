package kim.biryeong.semiontd.tower.gamble;

import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;

final class GambleFacing {
    private GambleFacing() {
    }

    static void towardWave(SemionTowerEntity entity, PlayerLane lane) {
        var spawn = lane.laneLayout().spawn();
        double dx = spawn.x - entity.getX();
        double dz = spawn.z - entity.getZ();
        if (dx * dx + dz * dz < 0.0001) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.yBodyRot = yaw;
        entity.setXRot(0);
    }
}
