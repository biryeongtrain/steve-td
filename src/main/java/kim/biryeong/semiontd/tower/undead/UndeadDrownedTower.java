package kim.biryeong.semiontd.tower.undead;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;

public class UndeadDrownedTower extends UndeadHuskTower {
    private boolean lastStandUsed;
    private long lastStandEndsAt;

    public UndeadDrownedTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public UndeadDrownedTower(
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
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (towerEntity == null || damageAmount <= 0.0) {
            return damageAmount;
        }
        long gameTime = towerEntity.level().getGameTime();
        if (lastStandEndsAt > gameTime) {
            return 0.0;
        }
        if (!lastStandUsed && damageAmount >= towerEntity.getHealth()) {
            lastStandUsed = true;
            lastStandEndsAt = gameTime + ticks("lastStandTicks");
            return Math.max(0.0, towerEntity.getHealth() - 1.0);
        }
        return damageAmount;
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        super.onDamaged(towerEntity, damageSource, damageAmount, previousHealth, currentHealth);
        applyFlatDamageBoost(towerEntity, value("damageBoostOnHit"));
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        lastStandUsed = false;
        lastStandEndsAt = 0L;
        super.resetForRound(lane);
    }

    @Override
    protected int thornCooldownTicks() {
        return ticks("thornCooldownTicks");
    }
}
