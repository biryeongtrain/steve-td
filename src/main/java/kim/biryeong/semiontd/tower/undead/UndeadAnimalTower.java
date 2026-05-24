package kim.biryeong.semiontd.tower.undead;

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
        boolean applied = false;
        for (SemionMonsterEntity monster : monstersAround(towerEntity)) {
            monster.applyTimedEffect(
                    TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION,
                    value("attackDamageReduction"),
                    ticks("debuffDurationTicks")
            );
            if (is(UndeadTowers.T2_UNDEAD_ANIMAL_TOWER)) {
                monster.applyTimedEffect(
                        TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS,
                        value("towerDamageTakenBonus"),
                        ticks("debuffDurationTicks")
                );
            }
            applied = true;
        }
        return applied;
    }

    private java.util.List<SemionMonsterEntity> monstersAround(SemionTowerEntity towerEntity) {
        double radius = value("radius");
        double radiusSqr = radius * radius;
        AABB box = towerEntity.getBoundingBox().inflate(radius);
        return towerEntity.level().getEntities(towerEntity, box, entity ->
                        entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster.runtimeMonster() != null
                                && monster.runtimeMonster().targetTeam() == teamId()
                                && towerEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                                && monster.distanceToSqr(towerEntity) <= radiusSqr
                )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .toList();
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
