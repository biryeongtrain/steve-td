package kim.biryeong.semiontd.tower.animal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

public class FoxTower extends AnimalStackTower {
    public FoxTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public FoxTower(
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
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        if (towerEntity == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        double attackRangeSqr = towerEntity.attackRange() * towerEntity.attackRange();
        List<FoxTargetCandidate> foxCandidates = candidates.stream()
                .map(candidate -> new FoxTargetCandidate(
                        candidate,
                        candidate.getHealth(),
                        candidate.getMaxHealth(),
                        towerEntity.distanceToSqr(candidate),
                        towerEntity.distanceToSqr(candidate) <= attackRangeSqr
                ))
                .toList();
        return FoxTargetingPolicy.select(foxCandidates, effectiveExecuteThreshold())
                .map(FoxTargetCandidate::monster);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (target == null || target.getMaxHealth() <= 0.0F || target.getHealth() / target.getMaxHealth() > effectiveExecuteThreshold()) {
            return damageAmount;
        }
        double bonusRatio = value("executeDamageBonusRatio") + currentStacks() * value("executeDamageBonusPerStack");
        return damageAmount * (1.0 + Math.max(0.0, bonusRatio));
    }

    @Override
    protected boolean isStackFamily(Tower tower) {
        return tower != null && (
                tower.type().id().equals(AnimalTowers.T1_FOX_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T2_FOX_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T3_FOX_TOWER.id())
        );
    }

    @Override
    protected int maxStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxStacks");
    }

    private double effectiveExecuteThreshold() {
        return FoxTargetingPolicy.effectiveThreshold(
                value("executeHealthThreshold"),
                currentStacks(),
                value("executeThresholdPerStack"),
                value("maxExecuteHealthThreshold")
        );
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private record FoxTargetCandidate(
            SemionMonsterEntity monster,
            double currentHealth,
            double maxHealth,
            double distanceSqr,
            boolean inAttackRange
    ) implements FoxTargetingPolicy.Candidate {
    }
}
