package kim.biryeong.semiontd.api.area;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;

public interface AreaEffectApi {
    AreaEffectResult<SemionMonsterEntity> applyToMonsters(
            MonsterAreaEffectRequest request,
            AreaEffectAction<SemionMonsterEntity> action
    );

    AreaEffectResult<AreaTowerTarget> applyToTowers(
            TowerAreaEffectRequest request,
            AreaEffectAction<AreaTowerTarget> action
    );
}
