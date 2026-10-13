package kim.biryeong.semiontd.tower.engineer;

import net.minecraft.core.Direction;

record EngineerTrapSignalSnapshot(Direction direction, EngineerTowers.PlateKind plateKind, long pressedAt) {
    boolean permits(Direction travelDirection) {
        return direction == null || direction == travelDirection;
    }
}
