package kim.biryeong.semiontd.tower.undead;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.Vec3;

public final class UndeadBasicSkeletonTower extends ProductionTower {
    public UndeadBasicSkeletonTower(TowerType type, UUID owner, TeamId team, int lane,
                                    GridPosition original, GridPosition current) {
        super(type, owner, team, lane, original, current);
    }

    @Override
    protected double builderCurrentMaxHealth() {
        return UndeadAugments.copyHealth(this, super.builderCurrentMaxHealth());
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity entity, SemionMonsterEntity target, double damage) {
        return UndeadAugments.copyDamage(this, super.modifyAttackDamage(entity, target, damage));
    }

    @Override
    public void onNearbyMonsterDeath(PlayerLane lane, Monster monster, Vec3 position) {
        if (isWithinDeathStackRange(position)) {UndeadAugments.onMonsterDeath(this, lane, monster, false);}
    }
}
