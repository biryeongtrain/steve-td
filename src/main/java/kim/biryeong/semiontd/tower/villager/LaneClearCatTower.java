package kim.biryeong.semiontd.tower.villager;

import java.util.UUID;
import java.util.Set;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.phys.Vec3;

public class LaneClearCatTower extends EntityBackedTower {
    private double killStackDamage;

    public LaneClearCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public LaneClearCatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double adjustedDamage = damageAmount + killStackDamage;
        Monster runtimeMonster = target == null ? null : target.runtimeMonster();
        if (runtimeMonster != null && runtimeMonster.senderTeam().isEmpty()) {
            return adjustedDamage * (1.0 + value("waveBonus"));
        }
        return adjustedDamage;
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        explode(towerEntity, target, damageAmount);
    }

    @Override
    public void onNearbyMonsterDeath(PlayerLane lane, Monster monster, Vec3 deathPosition) {
        if (isWithinDeathStackRange(deathPosition)) {
            incrementDeathStack();
        }
    }

    @Override
    public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
        if (destroyedTower != null && isWithinDeathStackRange(destroyedTower.position())) {
            incrementDeathStack();
        }
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        double step = stackDamageStep();
        int stacks = step <= 0.0 ? 0 : (int) Math.round(killStackDamage / step);
        int maxStacks = step <= 0.0 ? 0 : (int) Math.round(stackDamageCap() / step);
        return java.util.List.of("사망 스택 " + stacks + "/" + maxStacks + " (공격력 +" + oneDecimal(killStackDamage) + ")");
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof LaneClearCatTower catTower) {
            this.killStackDamage = Math.min(stackDamageCap(), catTower.killStackDamage);
        }
    }

    private void explode(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || target == null) {
            return;
        }
        double radius = value("explosionRadius");
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "corpse_explosion"),
                towerEntity,
                target.position(),
                radius,
                Set.of(target.getUUID()),
                null,
                AreaVfxSpec.onTrigger(AreaVfxStyles.CORPSE_EXPLOSION)
        );
        TowerAreaDamage.apply(this, towerEntity, request, monster -> damageAmount, false);
    }

    private void incrementDeathStack() {
        killStackDamage = Math.min(stackDamageCap(), killStackDamage + stackDamageStep());
    }

    private double stackDamageStep() {
        return value("stackDamage");
    }

    private double stackDamageCap() {
        return value("stackDamageCap");
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }
}
