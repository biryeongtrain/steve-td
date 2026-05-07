package kim.biryeong.semiontd.entity.monster;

import net.minecraft.world.entity.EntityDimensions;

public record MonsterDimensions(float width, float height) {
    public static final float DEFAULT_WIDTH = 0.6F;
    public static final float DEFAULT_HEIGHT = 1.95F;
    public static final MonsterDimensions DEFAULT = new MonsterDimensions(DEFAULT_WIDTH, DEFAULT_HEIGHT);

    public MonsterDimensions {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
            throw new IllegalArgumentException("Monster dimensions must be finite positive values.");
        }
    }

    public static MonsterDimensions of(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0.0 || height <= 0.0
                || width > Float.MAX_VALUE || height > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Monster dimensions must be finite positive values.");
        }
        return new MonsterDimensions((float) width, (float) height);
    }

    public static MonsterDimensions orDefault(MonsterDimensions dimensions) {
        return dimensions == null ? DEFAULT : dimensions;
    }

    public EntityDimensions toEntityDimensions() {
        return EntityDimensions.scalable(width, height);
    }
}
