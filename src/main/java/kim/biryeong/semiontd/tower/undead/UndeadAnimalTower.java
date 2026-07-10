package kim.biryeong.semiontd.tower.undead;

import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;

public class UndeadAnimalTower extends EntityBackedTower {
    private int scanCooldownTicks;

    public UndeadAnimalTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public UndeadAnimalTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (scanCooldownTicks > 0) {
            scanCooldownTicks--;
            return;
        }
        if (applyDebuffs(lane)) {
            scanCooldownTicks = ticks("scanIntervalTicks");
        }
    }

    private boolean applyDebuffs(PlayerLane lane) {
        SemionTowerEntity towerEntity = towerEntity(lane);
        if (towerEntity == null) {
            return false;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "debuff"),
                towerEntity,
                value("radius"),
                AreaVfxSpec.onChange(AreaVfxStyles.DEBUFF)
        ).withFilter(monster -> monster.runtimeMonster().targetTeam() == teamId());
        var result = SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            boolean changed = false;
            double previousAttack = monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION);
            monster.applyTimedEffect(
                    TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION,
                    value("attackDamageReduction"),
                    ticks("debuffDurationTicks")
            );
            changed |= Double.compare(previousAttack,
                    monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION)) != 0;
            if (is(UndeadTowers.T2_UNDEAD_ANIMAL_TOWER)) {
                double previousTaken = monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS);
                monster.applyTimedEffect(
                        TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS,
                        value("towerDamageTakenBonus"),
                        ticks("debuffDurationTicks")
                );
                changed |= Double.compare(previousTaken,
                        monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS)) != 0;
            }
            return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });
        return result.candidateCount() > 0;
    }

    private SemionTowerEntity towerEntity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity towerEntity ? towerEntity : null;
    }

    private boolean is(TowerType towerType) {
        return type().id().equals(towerType.id());
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }
}
