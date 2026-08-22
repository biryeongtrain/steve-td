package kim.biryeong.semiontd.tower.area;

import java.util.function.ToDoubleFunction;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaEffectResult;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.Tower;

public final class TowerAreaDamage {
    private TowerAreaDamage() {
    }

    public static AreaEffectResult<SemionMonsterEntity> apply(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills) {
        return apply(tower, source, request, damage, propagateKills, (target, amount, killed) -> {});
    }

    public static AreaEffectResult<SemionMonsterEntity> apply(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills, AfterDamage afterDamage) {
        return apply(tower, source, request, damage, propagateKills, afterDamage, false, false, DamageType.PHYSICAL);
    }

    public static AreaEffectResult<SemionMonsterEntity> apply(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills, AfterDamage afterDamage, DamageType damageType) {
        return apply(tower, source, request, damage, propagateKills, afterDamage, false, false, damageType);
    }

    public static AreaEffectResult<SemionMonsterEntity> applyBasicAttackSplash(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills) {
        return applyBasicAttackSplash(tower, source, request, damage, propagateKills, (target, amount, killed) -> {});
    }

    public static AreaEffectResult<SemionMonsterEntity> applyBasicAttackSplash(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills, AfterDamage afterDamage) {
        return apply(tower, source, request, damage, propagateKills, afterDamage, false, true, DamageType.PHYSICAL);
    }

    public static AreaEffectResult<SemionMonsterEntity> applyResolved(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills, AfterDamage afterDamage) {
        return applyResolved(tower, source, request, damage, propagateKills, afterDamage, DamageType.PHYSICAL);
    }

    public static AreaEffectResult<SemionMonsterEntity> applyResolved(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills, AfterDamage afterDamage, DamageType damageType) {
        return apply(tower, source, request, damage, propagateKills, afterDamage, true, false, damageType);
    }

    private static AreaEffectResult<SemionMonsterEntity> apply(Tower tower, SemionTowerEntity source, MonsterAreaEffectRequest request, ToDoubleFunction<SemionMonsterEntity> damage, boolean propagateKills, AfterDamage afterDamage, boolean resolvedOutgoingDamage, boolean igniteOnHit, DamageType damageType) {
        return SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            double amount = Math.max(0.0, damage.applyAsDouble(target));
            if (amount <= 0.0) {
                return AreaEffectOutcome.UNCHANGED;
            }
            Tower.DamageResult damageResult;
            if (resolvedOutgoingDamage) {
                damageResult = tower.damageResolvedTargetResult(source, target, amount, damageType);
            } else if (igniteOnHit) {
                damageResult = source.damageBasicAttackSecondaryTargetResult(target, amount);
            } else {
                damageResult = tower.damageTargetResult(source, target, amount, damageType);
            }
            boolean killed = damageResult.killed();
            afterDamage.accept(target, damageResult.dealtDamage(), killed);
            if (killed && propagateKills) {
                tower.onKill(source, target, amount);
            }
            return killed ? AreaEffectOutcome.KILLED : AreaEffectOutcome.APPLIED;
        });
    }

    @FunctionalInterface
    public interface AfterDamage {
        void accept(SemionMonsterEntity target, double damage, boolean killed);
    }
}
