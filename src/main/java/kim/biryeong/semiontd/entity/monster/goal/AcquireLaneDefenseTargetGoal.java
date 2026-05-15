package kim.biryeong.semiontd.entity.monster.goal;

import java.util.Comparator;
import java.util.EnumSet;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

public final class AcquireLaneDefenseTargetGoal extends Goal {
    private final SemionMonsterEntity monster;

    public AcquireLaneDefenseTargetGoal(SemionMonsterEntity monster) {
        this.monster = monster;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!monster.isAlive() || monster.runtimeMonster() == null || monster.getTarget() != null) {
            return false;
        }

        LivingEntity target = findTarget();
        return target != null;
    }

    @Override
    public void start() {
        LivingEntity target = findTarget();
        if (target != null) {
            monster.setTarget(target);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private LivingEntity findTarget() {
        if (monster.runtimeMonster() == null) {
            return null;
        }

        AABB searchBox = monster.defenseSearchBox();
        return monster.level().getEntities(
                        monster,
                        searchBox,
                        entity -> entity instanceof LivingEntity livingEntity
                                && entity instanceof LaneDefenseEntity laneDefenseEntity
                                && entity.isAlive()
                                && laneDefenseEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                                && monster.canTargetDefense(livingEntity)
                ).stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .sorted(Comparator
                        .comparingInt((LivingEntity entity) -> ((LaneDefenseEntity) entity).aggroPriority()).reversed()
                        .thenComparingDouble(monster::distanceToSqr))
                .findFirst()
                .orElse(null);
    }
}
