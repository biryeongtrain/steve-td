package kim.biryeong.semiontd.tower.legion;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;

public class LegionParrotTower extends EntityBackedTower {
    private int attackStacks;

    public LegionParrotTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public LegionParrotTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public int attackStacks() {
        return attackStacks;
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount * attackMultiplier();
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        return Math.max(1, (int) Math.ceil(baseIntervalTicks / attackMultiplier()));
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        attackStacks = Math.min(maxAttackStacks(), attackStacks + 1);
        if (towerEntity != null) {
            towerEntity.syncTowerState(this);
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        attackStacks = 0;
        super.resetForRound(lane);
    }

    private double attackMultiplier() {
        return 1.0 + attackStacks * TowerBalanceRuntime.ability(type().id(), "attackStackBonus");
    }

    private int maxAttackStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxAttackStacks");
    }
}
