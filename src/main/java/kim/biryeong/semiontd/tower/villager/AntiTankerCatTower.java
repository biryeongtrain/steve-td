package kim.biryeong.semiontd.tower.villager;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

public class AntiTankerCatTower extends EntityBackedTower {
    private double killStackDamage;

    public AntiTankerCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public AntiTankerCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double adjustedDamage = damageAmount + killStackDamage;
        Monster runtimeMonster = target == null ? null : target.runtimeMonster();
        if (runtimeMonster == null || runtimeMonster.senderTeam().isEmpty()) {
            return adjustedDamage;
        }
        if (runtimeMonster.summonRoles().contains(SummonRole.TANK)) {
            return adjustedDamage * (1.0 + value("tankBonus"));
        }
        return adjustedDamage * (1.0 + value("nonWaveBonus"));
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        killStackDamage = Math.min(stackDamageCap(), killStackDamage + stackDamageStep());
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        return java.util.List.of(killStackLine(killStackDamage, stackDamageStep(), stackDamageCap()));
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof AntiTankerCatTower catTower) {
            this.killStackDamage = Math.min(stackDamageCap(), catTower.killStackDamage);
        }
    }

    private double stackDamageStep() {
        return value("stackDamage");
    }

    private double stackDamageCap() {
        return value("stackDamageCap");
    }

    private String killStackLine(double damage, double step, double cap) {
        int stacks = step <= 0.0 ? 0 : (int) Math.round(damage / step);
        int maxStacks = step <= 0.0 ? 0 : (int) Math.round(cap / step);
        return "킬 스택 " + stacks + "/" + maxStacks + " (공격력 +" + oneDecimal(damage) + ")";
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }
}
