package kim.biryeong.semiontd.tower.queen;

import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.resources.ResourceLocation;

public final class QueenShrink {
    private static final MonsterDataKey<Double> POINTS = new MonsterDataKey<>(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "queen_shrink_points"), Double.class);

    private QueenShrink() {}

    public static boolean apply(SemionMonsterEntity target, double points) {
        if (target == null || target.runtimeMonster() == null || !target.isAlive()
                || !Double.isFinite(points) || points <= 0.0) return false;
        double currentScale = target.runtimeMonster().permanentStatScale();
        double minimumScale = QueenBalance.minimumStatScale();
        if (currentScale <= minimumScale) return false;
        double requestedFactor = Math.pow(QueenBalance.shrinkFactorPerPoint(), points);
        double factor = Math.max(minimumScale, currentScale * requestedFactor) / currentScale;
        if (factor >= 1.0) return false;
        target.applyPermanentStatScale(factor, QueenBalance.minimumVisualScale());
        double appliedPoints = Math.min(points, Math.log(factor) / Math.log(QueenBalance.shrinkFactorPerPoint()));
        target.runtimeMonster().setData(POINTS, points(target) + appliedPoints);
        return true;
    }

    public static double points(SemionMonsterEntity target) {
        return target == null || target.runtimeMonster() == null
                ? 0.0 : target.runtimeMonster().getData(POINTS).orElse(0.0);
    }
}
