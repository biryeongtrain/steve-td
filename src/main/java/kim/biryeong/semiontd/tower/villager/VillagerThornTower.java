package kim.biryeong.semiontd.tower.villager;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;

import java.util.UUID;

public class VillagerThornTower extends EntityBackedTower {
    private static final int T2_THORN_COOLDOWN_TICKS = 40;
    private static final int T3_THORN_COOLDOWN_TICKS = 30;
    private static final int T2_THORN_DAMAGE = 10;
    private static final int T3_THORN_DAMAGE = 10;
    private static final float T2_THORN_RADIUS = 1.5f;
    private static final float T3_THORN_RADIUS = 2;
    private static final double T2_HEALTH_BONUS_PER_ROUND = 0.10;
    private static final double T3_HEALTH_BONUS_PER_ROUND = 0.20;
    private static final int MAX_HEALTH_BONUS_SCALING = 5;

    private int thornCooldownTicks = 0;
    private int survivalBonus = 0;
    private final boolean isT3;
    public VillagerThornTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        this.isT3 = type == VillagerTowers.T3_GOLEM_TOWER;
    }

    public VillagerThornTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        this.isT3 = type == VillagerTowers.T3_GOLEM_TOWER;
    }

    @Override
    public void onDamaged(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount, double previousHealth, double currentHealth) {
        if (this.thornCooldownTicks > 0) {
            return;
        }
        float range = isT3 ? T3_THORN_RADIUS : T2_THORN_RADIUS;
        float splashRadiusSqr = range * range;
        double damage = isT3 ? T3_THORN_DAMAGE : T2_THORN_DAMAGE;

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

        this.thornCooldownTicks = this.isT3 ? T3_THORN_COOLDOWN_TICKS : T2_THORN_COOLDOWN_TICKS;
    }

    @Override
    public double currentMaxHealth() {
        double scale = isT3 ? T3_HEALTH_BONUS_PER_ROUND : T2_HEALTH_BONUS_PER_ROUND;
        return maxHealth() * (1.0 + scale * survivalBonus);
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        survivalBonus = Math.min(MAX_HEALTH_BONUS_SCALING, survivalBonus + 1);
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
            survivalBonus = Math.min(MAX_HEALTH_BONUS_SCALING, thornTower.survivalBonus);
        }
    }
}
