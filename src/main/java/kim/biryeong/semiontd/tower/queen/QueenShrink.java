package kim.biryeong.semiontd.tower.queen;

import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.resources.Identifier;

public final class QueenShrink {
    private static final MonsterDataKey<Double> POINTS = new MonsterDataKey<>(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "queen_shrink_points"), Double.class);
    public static final Identifier SHRINK_DEBUFF_SOURCE =
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "queen_shrink_debuff");
    public static final Identifier GIANT_DEBUFF_SOURCE =
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "queen_giant_debuff");

    private QueenShrink() {}

    public static boolean apply(SemionMonsterEntity target, double points) {
        if (target == null || target.runtimeMonster() == null || !target.isAlive()
                || !Double.isFinite(points) || points <= 0.0) return false;
        double currentScale = target.runtimeMonster().permanentStatScale();
        double minimumScale = QueenBalance.minimumStatScale();
        double requestedFactor = Math.pow(QueenBalance.shrinkFactorPerPoint(), points);
        double factor = Math.min(1.0, Math.max(minimumScale, currentScale * requestedFactor) / currentScale);
        double health = Math.min(target.getHealth(), target.runtimeMonster().health());
        double healthFactor = Math.max(Math.min(1.0, QueenBalance.giantInitialExecutionHealth()
                / Math.max(0.000001, health)), requestedFactor);
        if (factor >= 1.0 && healthFactor >= 1.0) {
            syncDebuffs(target);
            return false;
        }
        target.applyPermanentStatScale(factor, healthFactor, QueenBalance.minimumVisualScale());
        syncDebuffs(target);
        double appliedPoints = Math.min(points, Math.log(Math.min(factor, healthFactor))
                / Math.log(QueenBalance.shrinkFactorPerPoint()));
        target.runtimeMonster().setData(POINTS, points(target) + appliedPoints);
        return true;
    }

    public static double points(SemionMonsterEntity target) {
        return target == null || target.runtimeMonster() == null
                ? 0.0 : target.runtimeMonster().getData(POINTS).orElse(0.0);
    }

    private static void syncDebuffs(SemionMonsterEntity target) {
        target.setPersistentEffect(
                TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, SHRINK_DEBUFF_SOURCE, 0.0);
        target.setPersistentEffect(
                TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, SHRINK_DEBUFF_SOURCE,
                Math.min(0.70, 1.0 - target.runtimeMonster().permanentStatScale()));
    }
}
