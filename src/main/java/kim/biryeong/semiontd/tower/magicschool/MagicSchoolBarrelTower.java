package kim.biryeong.semiontd.tower.magicschool;

import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class MagicSchoolBarrelTower extends EntityBackedTower {
    private final Vec3 spawnPosition;
    private final boolean finalDefense;

    MagicSchoolBarrelTower(PlayerLane lane, Vec3 position, int round, boolean finalDefense) {
        super(TowerType.builder("magic_school_barrel", "통").mineralCost(0)
                .maxHealth(MagicSchoolCurriculum.barrelHealth(round)).range(0).damage(0).attackIntervalTicks(20)
                .aggroPriority(MagicSchoolCurriculum.integer("barrelAggro", 100))
                .visual(BlockDisplayVisual.builder(Blocks.BARREL.defaultBlockState()).build()).build(),
                lane.ownerPlayer(), lane.teamId(), lane.laneId(), GridPosition.from(BlockPos.containing(position).below()));
        spawnPosition = position;
        this.finalDefense = finalDefense;
        markTemporaryCopy(null);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(spawnPosition);
    }

    @Override public boolean canUseBasicAttacks() { return false; }
    @Override public boolean canChaseTargets() { return false; }
    @Override public boolean countsForLaneDefense() { return false; }
    @Override public boolean participatesInFinalDefense() { return false; }
    @Override public boolean deployedAtFinalDefense() { return finalDefense; }
    @Override public boolean canReceiveAllyHealing() { return false; }
    @Override public double adjustMovementSpeed(double speed) { return 0; }
    @Override public void moveToFinalDefense(PlayerLane lane, GridPosition position) { }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        runtimeEntity(lane).ifPresent(entity -> entity.setPos(spawnPosition));
    }

    @Override
    public double modifyFinalIncomingDamage(SemionTowerEntity entity, DamageSource source, double original, double reduced) {
        return original > 0 ? 1 : 0;
    }

    @Override
    public double modifyIncomingDamageIgnoringReductions(SemionTowerEntity entity, DamageSource source, double damage) {
        return damage > 0 ? 1 : 0;
    }
}
