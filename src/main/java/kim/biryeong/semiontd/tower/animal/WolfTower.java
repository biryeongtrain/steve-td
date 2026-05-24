package kim.biryeong.semiontd.tower.animal;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.AABB;

public class WolfTower extends AnimalStackTower {
    public WolfTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public WolfTower(
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
        double amount = damageAmount + currentStacks() * value("damagePerStack");
        if (is(AnimalTowers.T3_WOLF_DPS_TOWER) && atMaxStacks()) {
            amount += value("maxStackDamageBonus");
        }
        return amount;
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        int interval = baseIntervalTicks - (int) Math.round(currentStacks() * value("intervalReductionPerStack"));
        if (!is(AnimalTowers.T1_WOLF_TOWER) && atMaxStacks()) {
            interval -= ticks("maxStackExtraIntervalReduction");
        }
        return Math.max(1, interval);
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (is(AnimalTowers.T2_WOLF_DPS_TOWER) || is(AnimalTowers.T3_WOLF_DPS_TOWER)) {
            splash(towerEntity, target, damageAmount);
        }
    }

    @Override
    protected boolean isStackFamily(Tower tower) {
        return tower != null && (
                tower.type().id().equals(AnimalTowers.T1_WOLF_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T2_WOLF_DPS_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T3_WOLF_DPS_TOWER.id())
        );
    }

    @Override
    protected int maxStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxStacks");
    }

    private void splash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || target == null) {
            return;
        }
        double radius = value("splashRadius");
        double radiusSqr = radius * radius;
        AABB splashBox = target.getBoundingBox().inflate(radius);
        towerEntity.level().getEntities(towerEntity, splashBox, entity ->
                        entity instanceof SemionMonsterEntity splashTarget
                                && splashTarget.isAlive()
                                && splashTarget != target
                                && splashTarget.runtimeMonster() != null
                                && towerEntity.defendsLane(splashTarget.runtimeMonster().targetLaneId())
                                && splashTarget.distanceToSqr(target) <= radiusSqr
                )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(monster -> {
                    double splashDamage = damageAmount * value("splashDamageRatio");
                    if (damageTarget(towerEntity, monster, splashDamage)) {
                        onKill(towerEntity, monster, splashDamage);
                    }
                });
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
