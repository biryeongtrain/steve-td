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
     *
     * <p>어느 레인 소속인지는 보지 않습니다. 예전에는 마왕이 자기 레인을 노리는 몹에게만 표적이
     * 됐는데, 레인 이탈 제한을 푼 뒤로는 남의 레인에 서 있는 마왕을 그 레인 몹들이 그냥 지나쳐
     * 가는 결과가 됐습니다. 눈앞에 서서 때리는 사람을 무시할 이유가 없습니다. 탐색 상자가
     * 거리를 이미 한정하고, 팀 아레나는 팀마다 월드가 따로라 상대 팀까지 닿지도 않습니다.
     *
     * <p>이래도 남의 레인 타워의 어그로를 뺏지는 않습니다. 이 경로는 방어 대상을 하나도 못
     * 찾았을 때만 도는 차선책이라, 그 레인에 지킬 타워가 있으면 그쪽이 먼저입니다.
     */
    private LivingEntity findDemonLordTarget() {
        return monster.level().getEntities(monster, monster.defenseSearchBox(), entity -> {
                    if (!(entity instanceof ServerPlayer player) || !player.isAlive()) {
                        return false;
                    }
                    DemonLordState state = DemonLordStates.get(player.getUUID());
                    return state != null && state.inCombat();
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
