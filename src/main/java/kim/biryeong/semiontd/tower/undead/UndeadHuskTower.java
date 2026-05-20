package kim.biryeong.semiontd.tower.undead;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;

public class UndeadHuskTower extends UndeadTowerSupport {
    private int thornCooldownTicks;

    public UndeadHuskTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public UndeadHuskTower(
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
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        healFromDamage(towerEntity, damageAmount, value("lifeStealRatio"));
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        applyFlatDamageBoost(towerEntity, value("damageBoostOnHit"));
        triggerThorns(towerEntity);
    }

    @Override
    public void tick(kim.biryeong.semiontd.game.PlayerLane lane) {
        super.tick(lane);
        if (thornCooldownTicks > 0) {
            thornCooldownTicks--;
        }
    }

    protected double thornRadius() {
        return value("thornRadius");
    }

    protected int thornCooldownTicks() {
        return ticks("thornCooldownTicks");
    }

    private void triggerThorns(SemionTowerEntity towerEntity) {
        if (thornCooldownTicks > 0 || towerEntity == null) {
            return;
        }
        int hitCount = 0;
        for (SemionMonsterEntity monster : monstersAround(towerEntity, thornRadius(), null)) {
            double thornDamage = towerEntity.attackDamageAmount(monster);
            if (damageTarget(towerEntity, monster, thornDamage)) {
                onKill(towerEntity, monster, thornDamage);
            }
            hitCount++;
        }
        if (hitCount > 0) {
            towerEntity.receiveHealing(value("thornHealPerHit") * hitCount);
            thornCooldownTicks = thornCooldownTicks();
        }
    }

    protected double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    protected int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }
}
