package kim.biryeong.semiontd.tower.villager;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.AABB;

public class LaneClearCatTower extends EntityBackedTower {
    private double killStackDamage;

    public LaneClearCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public LaneClearCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double adjustedDamage = damageAmount + killStackDamage;
        Monster runtimeMonster = target == null ? null : target.runtimeMonster();
        if (runtimeMonster != null && runtimeMonster.senderTeam().isEmpty()) {
            return adjustedDamage * (1.0 + value("waveBonus"));
        }
        return adjustedDamage;
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        incrementKillStack();
        explode(towerEntity, target, damageAmount);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof LaneClearCatTower catTower) {
            this.killStackDamage = Math.min(stackDamageCap(), catTower.killStackDamage);
        }
    }

    private void explode(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || target == null) {
            return;
        }
        double radius = value("explosionRadius");
        double radiusSqr = radius * radius;
        AABB explosionBox = target.getBoundingBox().inflate(radius);
        towerEntity.level().getEntities(towerEntity, explosionBox, entity ->
                        entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster != target
                                && monster.runtimeMonster() != null
                                && towerEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                                && monster.distanceToSqr(target) <= radiusSqr
                )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(monster -> {
                    if (damageTarget(towerEntity, monster, damageAmount)) {
                        incrementKillStack();
                    }
                });
    }

    private void incrementKillStack() {
        killStackDamage = Math.min(stackDamageCap(), killStackDamage + stackDamageStep());
    }

    private double stackDamageStep() {
        return value("stackDamage");
    }

    private double stackDamageCap() {
        return value("stackDamageCap");
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }
}
