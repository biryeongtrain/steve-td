package kim.biryeong.semiontd.game;

import net.minecraft.core.BlockPos;

public record GridPosition(int x, int y, int z) {
    public static GridPosition from(BlockPos blockPos) {
        return new GridPosition(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }
}
