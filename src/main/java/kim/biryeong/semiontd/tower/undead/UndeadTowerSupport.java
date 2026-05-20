package kim.biryeong.semiontd.tower.undead;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.AABB;

abstract class UndeadTowerSupport extends EntityBackedTower {
    private long damageBoostExpiresAt;
    private double flatDamageBoost;

    protected UndeadTowerSupport(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    protected UndeadTowerSupport(
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
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || damageBoostExpiresAt <= towerEntity.level().getGameTime()) {
            return damageAmount;
        }
        return damageAmount + flatDamageBoost;
    }

    protected final void healFromDamage(SemionTowerEntity towerEntity, double damageAmount, double ratio) {
        if (towerEntity == null || damageAmount <= 0.0 || ratio <= 0.0) {
            return;
        }
        towerEntity.receiveHealing(damageAmount * ratio);
    }

    protected final void applyFlatDamageBoost(SemionTowerEntity towerEntity, double amount) {
        if (towerEntity == null || amount <= 0.0) {
            return;
        }
        flatDamageBoost = amount;
        damageBoostExpiresAt = towerEntity.level().getGameTime()
                + TowerBalanceRuntime.abilityTicks(type().id(), "damageBoostTicks");
    }

    protected final java.util.List<SemionMonsterEntity> monstersAround(
            SemionTowerEntity towerEntity,
            double radius,
            SemionMonsterEntity excluded
    ) {
        if (towerEntity == null || radius <= 0.0) {
            return java.util.List.of();
        }
        double radiusSqr = radius * radius;
        AABB box = towerEntity.getBoundingBox().inflate(radius);
        return towerEntity.level().getEntities(towerEntity, box, entity ->
                        entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster != excluded
                                && monster.runtimeMonster() != null
                                && towerEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                                && monster.distanceToSqr(towerEntity) <= radiusSqr
                )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .toList();
    }
}
