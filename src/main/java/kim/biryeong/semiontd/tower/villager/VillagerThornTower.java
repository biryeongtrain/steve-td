package kim.biryeong.semiontd.tower.villager;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;

import java.util.UUID;

public class VillagerThornTower extends EntityBackedTower {
    private int thornCooldownTicks = 0;
    private int survivalBonus = 0;
    public VillagerThornTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public VillagerThornTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    @Override
    public void onDamaged(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount, double previousHealth, double currentHealth) {
        if (this.thornCooldownTicks > 0) {
            return;
        }
        float range = (float) value("thornRadius");
        float splashRadiusSqr = range * range;
        double damage = value("thornDamage");

        var box = towerEntity.getBoundingBox().inflate(range);
        towerEntity.level().getEntities(towerEntity, box, entity ->
            entity instanceof SemionMonsterEntity monster &&
                    monster.isAlive() && monster.runtimeMonster() != null &&
                    towerEntity.defendsLane(monster.runtimeMonster().targetLaneId()) &&
                    monster.distanceToSqr(towerEntity) <= splashRadiusSqr
        )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(entity -> damageTarget(towerEntity, entity, damage));

        this.thornCooldownTicks = ticks("thornCooldownTicks");
    }

    @Override
    public double currentMaxHealth() {
        double scale = value("healthBonusPerSurvivedRound");
        return maxHealth() * (1.0 + scale * survivalBonus);
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        double bonus = value("healthBonusPerSurvivedRound") * survivalBonus;
        return java.util.List.of("생존 스택 " + survivalBonus + "/" + maxSurvivalStacks()
                + " (체력 +" + percent(bonus) + ")");
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        if (!deployedAtFinalDefense()) {
            increaseSurvivalBonus();
        }
        super.resetForRound(lane);
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        increaseSurvivalBonus();
        super.moveToFinalDefense(lane, position);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (this.thornCooldownTicks > 0) {
            this.thornCooldownTicks--;
        }
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof VillagerThornTower thornTower) {
            survivalBonus = Math.min(TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks"), thornTower.survivalBonus);
            syncHealth(currentMaxHealth());
        }
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private int maxSurvivalStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks");
    }

    private void increaseSurvivalBonus() {
        double previousMaxHealth = currentMaxHealth();
        int previousBonus = survivalBonus;
        survivalBonus = Math.min(TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks"), survivalBonus + 1);
        if (survivalBonus > previousBonus) {
            syncHealth(health() + Math.max(0.0, currentMaxHealth() - previousMaxHealth));
        }
    }
}
