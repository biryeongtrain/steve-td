package kim.biryeong.semiontd.tower.warlock;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.AABB;

public class WarlockSacrificeTower extends EntityBackedTower {
    public WarlockSacrificeTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public WarlockSacrificeTower(
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
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        WarlockTower.refreshWarlockCoreStats(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        WarlockTower.refreshWarlockCoreStats(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        if (WarlockTowers.isMeleeSlave(type())) {
            applyMonsterEffect(lane, TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, value("towerDamageTakenBonus"));
        } else if (WarlockTowers.isRangedSlave(type())) {
            applyMonsterEffect(lane, TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, value("attackSpeedReduction"));
        }
    }

    private void applyMonsterEffect(PlayerLane lane, TimedEffectType effectType, double magnitude) {
        SemionTowerEntity towerEntity = towerEntity(lane);
        if (towerEntity == null || magnitude <= 0.0) {
            return;
        }
        double radius = value("deathEffectRadius");
        double radiusSqr = radius * radius;
        AABB box = towerEntity.getBoundingBox().inflate(radius);
        int durationTicks = ticks("deathEffectDurationTicks");
        towerEntity.level().getEntities(towerEntity, box, entity ->
                        entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster.runtimeMonster() != null
                                && towerEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                                && monster.distanceToSqr(towerEntity) <= radiusSqr
                )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(monster -> monster.applyTimedEffect(effectType, magnitude, durationTicks));
    }

    private SemionTowerEntity towerEntity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity towerEntity ? towerEntity : null;
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }
}
