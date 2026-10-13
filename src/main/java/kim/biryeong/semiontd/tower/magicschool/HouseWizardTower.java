package kim.biryeong.semiontd.tower.magicschool;

import java.util.Comparator;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;

public final class HouseWizardTower extends MagicSchoolWizardTower {
    public HouseWizardTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
            GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public boolean hasFreeSpellChanges() {
        return MagicSchoolTowers.belongsToHouse(type(), MagicSchoolTowers.RAVENCLAW);
    }

    @Override
    protected Comparator<SemionMonsterEntity> houseTargetOrder(SemionTowerEntity source) {
        Comparator<SemionMonsterEntity> order;
        if (MagicSchoolTowers.belongsToHouse(type(), MagicSchoolTowers.GRYFFINDOR)) {
            order = Comparator.comparingDouble(HouseWizardTower::maximumHealth).reversed();
        } else if (MagicSchoolTowers.belongsToHouse(type(), MagicSchoolTowers.HUFFLEPUFF)) {
            order = Comparator.comparingDouble((SemionMonsterEntity target) -> source.distanceToSqr(target)).reversed();
        } else if (MagicSchoolTowers.belongsToHouse(type(), MagicSchoolTowers.SLYTHERIN)) {
            order = Comparator.comparingDouble(HouseWizardTower::currentHealth);
        } else {
            order = Comparator.comparingDouble(source::distanceToSqr);
        }
        return order;
    }

    private static double maximumHealth(SemionMonsterEntity target) {
        return target.runtimeMonster() == null ? target.getMaxHealth() : target.runtimeMonster().maxHealth();
    }

    private static double currentHealth(SemionMonsterEntity target) {
        return target.runtimeMonster() == null ? target.getHealth() : target.runtimeMonster().health();
    }
}
