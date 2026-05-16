package kim.biryeong.semiontd.tower;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import kim.biryeong.semiontd.test.tower.TestTower;

public final class ProductionTower extends TestTower {
    private final ProductionTowerBehavior behavior;
    private int mechanicStacks;
    private int idleTicks;

    public ProductionTower(
            TowerType type,
            ProductionTowerBehavior behavior,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition position
    ) {
        super(type, ownerPlayer, teamId, laneId, position);
        this.behavior = behavior;
    }

    public ProductionTower(
            TowerType type,
            ProductionTowerBehavior behavior,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        this.behavior = behavior;
    }

    public ProductionTowerBehavior behavior() {
        return behavior;
    }

    public TowerFaction faction() {
        return behavior.faction();
    }

    public int mechanicStacks() {
        return mechanicStacks;
    }

    public double damageMultiplier() {
        return 1.0 + mechanicStacks * behavior.damagePerStack();
    }

    public int adjustedAttackInterval(int baseIntervalTicks) {
        double multiplier = 1.0 - mechanicStacks * behavior.attackSpeedPerStack();
        return Math.max(1, (int) Math.ceil(baseIntervalTicks * Math.max(0.35, multiplier)));
    }

    @Override
    public double modifyAttackDamage(SemionTestTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount * damageMultiplier();
    }

    @Override
    public void onAttack(
            SemionTestTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount,
            boolean killedTarget
    ) {
        idleTicks = 0;
        if (behavior.stackOnHit() || (killedTarget && behavior.stackOnKill())) {
            mechanicStacks = Math.min(behavior.maxStacks(), mechanicStacks + 1);
        }
    }

    @Override
    public void resetForRound(kim.biryeong.semiontd.game.PlayerLane lane) {
        super.resetForRound(lane);
        idleTicks = 0;
        if (faction() == TowerFaction.BEAST) {
            mechanicStacks = 0;
        }
    }

    @Override
    public void tick(kim.biryeong.semiontd.game.PlayerLane lane) {
        super.tick(lane);
        if (faction() == TowerFaction.BEAST && mechanicStacks > 0) {
            idleTicks++;
            if (idleTicks >= 80) {
                mechanicStacks--;
                idleTicks = 0;
            }
        }
    }
}
