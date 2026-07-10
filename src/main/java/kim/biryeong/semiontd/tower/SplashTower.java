package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;

import java.util.UUID;

public abstract class SplashTower extends EntityBackedTower {

    protected SplashTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    protected SplashTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        float splashRange = getSplashRange();
        if (splashRange <= 0.0F) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "splash"), towerEntity, target, splashRange,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        SemionTdApi.areaEffects().applyToMonsters(request, entity ->
                damage(towerEntity, entity, damageAmount) ? AreaEffectOutcome.KILLED : AreaEffectOutcome.APPLIED);
    }

    protected boolean damage(SemionTowerEntity tower, SemionMonsterEntity monster, double damage) {
        double splashDamage = damage * getSplashRatio();
        boolean killed = damageTarget(tower, monster, splashDamage);
        if (killed) {
            onKill(tower, monster, splashDamage);
        }
        return killed;
    }

    public abstract float getSplashRange();
    public abstract float getSplashRatio();
}
