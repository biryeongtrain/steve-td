package kim.biryeong.semiontd.tower.magicschool;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;

public final class HogwartsTower extends ProductionTower {
    public HogwartsTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
            GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        entity.setNoAi(true);
    }

    @Override
    public boolean canChaseTargets() {
        return false;
    }

    @Override
    public boolean invulnerable() {
        return true;
    }

    @Override
    public boolean drawsAggro() {
        return false;
    }

    @Override
    public boolean targetableByMonsters() {
        return false;
    }

    @Override
    public boolean countsForLaneDefense() {
        return false;
    }

    @Override
    public boolean participatesInFinalDefense() {
        return false;
    }

    @Override
    public double adjustMovementSpeed(double baseSpeed) {
        return 0.0;
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        return 0.0;
    }

    @Override
    public boolean canUseBasicAttacks() {
        return false;
    }

    @Override
    public boolean canAttackTarget(SemionTowerEntity source, SemionMonsterEntity target) {
        return false;
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {

    }
}
