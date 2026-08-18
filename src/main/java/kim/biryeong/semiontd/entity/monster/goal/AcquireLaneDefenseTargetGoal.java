package kim.biryeong.semiontd.entity.monster.goal;

import java.util.Comparator;
import java.util.EnumSet;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import net.minecraft.server.level.ServerPlayer;
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
            if (target instanceof SemionTowerEntity towerEntity
                    && towerEntity.deployedAtFinalDefense()
                    && monster.runtimeMonster() != null) {
                monster.runtimeMonster().enterFinalDefenseCombat();
            }
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
        LivingEntity defender = findDefenseTarget();
        return defender != null ? defender : findDemonLordTarget();
    }

    /**
     * 마왕은 타워가 아니라 플레이어라 {@link LaneDefenseEntity} 필터에 걸리지 않습니다.
     *
     * <p>마왕 빌더의 제단은 어그로를 끌지 않으므로, 방어 타워를 못 찾았을 때만 마왕 본인을 노리게
     * 하면 다른 빌더의 타게팅은 전혀 건드리지 않으면서 마왕만 표적이 됩니다.
     */
    private LivingEntity findDemonLordTarget() {
        int laneId = monster.runtimeMonster().targetLaneId();
        return monster.level().getEntities(monster, monster.defenseSearchBox(), entity -> {
                    if (!(entity instanceof ServerPlayer player) || !player.isAlive()) {
                        return false;
                    }
                    DemonLordState state = DemonLordStates.get(player.getUUID());
                    return state != null && state.inCombat() && state.laneId() == laneId;
                }).stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .min(Comparator.comparingDouble(monster::distanceToSqr))
                .orElse(null);
    }

    private LivingEntity findDefenseTarget() {
        AABB searchBox = monster.defenseSearchBox();
        return monster.level().getEntities(
                        monster,
                        searchBox,
                        entity -> entity instanceof LivingEntity livingEntity
                                && entity instanceof LaneDefenseEntity laneDefenseEntity
                                && entity.isAlive()
                                && laneDefenseEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                                && laneDefenseEntity.drawsAggro()
                                && monster.canTargetDefense(livingEntity)
                ).stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(entity -> !(entity instanceof SemionTowerEntity towerEntity)
                        || towerEntity.runtimeTower() == null
                        || towerEntity.runtimeTower().targetableByMonsters())
                .sorted(Comparator
                        .comparingInt((LivingEntity entity) -> ((LaneDefenseEntity) entity).aggroPriority()).reversed()
                        .thenComparingDouble(monster::distanceToSqr))
                .findFirst()
                .orElse(null);
    }
}
