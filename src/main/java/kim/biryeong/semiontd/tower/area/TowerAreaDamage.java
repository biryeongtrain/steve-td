package kim.biryeong.semiontd.tower.area;

import java.util.function.ToDoubleFunction;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaEffectResult;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.Tower;

public final class TowerAreaDamage {
    private TowerAreaDamage() {
    }

    public static AreaEffectResult<SemionMonsterEntity> apply(
            Tower tower,
            SemionTowerEntity source,
            MonsterAreaEffectRequest request,
            ToDoubleFunction<SemionMonsterEntity> damage,
            boolean propagateKills
    ) {
        return apply(tower, source, request, damage, propagateKills, (target, amount, killed) -> {
        });
    }

    public static AreaEffectResult<SemionMonsterEntity> apply(
            Tower tower,
            SemionTowerEntity source,
            MonsterAreaEffectRequest request,
            ToDoubleFunction<SemionMonsterEntity> damage,
            boolean propagateKills,
            AfterDamage afterDamage
    ) {
        return SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            double amount = Math.max(0.0, damage.applyAsDouble(target));
            if (amount <= 0.0) {
                return AreaEffectOutcome.UNCHANGED;
            }
            boolean killed = tower.damageTarget(source, target, amount);
            afterDamage.accept(target, amount, killed);
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
