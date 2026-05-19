package kim.biryeong.semiontd.tower.villager;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

public class AntiTankerCatTower extends EntityBackedTower {
    private static final double T2_NON_WAVE_BONUS = 1;
    private static final double T2_TANK_BONUS = 1.50;
    private static final double T2_STACK_DAMAGE = 1;
    private static final double T2_STACK_DAMAGE_CAP = 10.0;
    private static final double T3_NON_WAVE_BONUS = 2.00;
    private static final double T3_TANK_BONUS = 4.00;
    private static final double T3_STACK_DAMAGE = 0.20;
    private static final double T3_STACK_DAMAGE_CAP = 20.0;

    private final boolean t3;
    private double killStackDamage;

    public AntiTankerCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        this.t3 = type == VillagerTowers.T3_ANTI_TANKER_CAT_TOWER;
    }

    public AntiTankerCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        this.t3 = type == VillagerTowers.T3_ANTI_TANKER_CAT_TOWER;
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double adjustedDamage = damageAmount + killStackDamage;
        Monster runtimeMonster = target == null ? null : target.runtimeMonster();
        if (runtimeMonster == null || runtimeMonster.senderTeam().isEmpty()) {
            return adjustedDamage;
        }
        if (runtimeMonster.summonRoles().contains(SummonRole.TANK)) {
            return adjustedDamage * (1.0 + (t3 ? T3_TANK_BONUS : T2_TANK_BONUS));
        }
        return adjustedDamage * (1.0 + (t3 ? T3_NON_WAVE_BONUS : T2_NON_WAVE_BONUS));
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        killStackDamage = Math.min(stackDamageCap(), killStackDamage + stackDamageStep());
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof AntiTankerCatTower catTower) {
            this.killStackDamage = Math.min(stackDamageCap(), catTower.killStackDamage);
        }
    }

    private double stackDamageStep() {
        return t3 ? T3_STACK_DAMAGE : T2_STACK_DAMAGE;
    }

    private double stackDamageCap() {
        return t3 ? T3_STACK_DAMAGE_CAP : T2_STACK_DAMAGE_CAP;
    }
}
