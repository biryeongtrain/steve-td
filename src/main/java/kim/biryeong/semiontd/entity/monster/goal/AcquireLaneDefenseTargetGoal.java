package kim.biryeong.semiontd.entity.monster.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

public final class AcquireLaneDefenseTargetGoal extends Goal {
    private static final double GROUND_MELEE_DEMON_LORD_RANGE = 16.0;
    private static final double REACH_MARGIN = 1.0;
    private static final EntityTypeTest<Entity, SemionTowerEntity> TOWERS =
            EntityTypeTest.forClass(SemionTowerEntity.class);
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
        if (monster.runtimeMonster() == null || monster.ignoresDefenses()) {
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
     * <p>남의 레인 타워의 어그로를 뺏지는 않습니다. 이 경로는 방어 대상을 하나도 못
     * 찾았을 때만 도는 차선책이라, 그 레인에 지킬 타워가 있으면 그쪽이 먼저입니다.
     */
    private LivingEntity findDemonLordTarget() {
        if (DemonLordStates.none()) {
            return null;
        }
        double defenseRange = monster.defenseTargetSearchRange();
        double targetRange = monster.runtimeMonster().attackKind() == AttackKind.MELEE && monster.onGround()
                ? Math.max(GROUND_MELEE_DEMON_LORD_RANGE, defenseRange)
                : defenseRange;
        AABB targetBox = monster.getBoundingBox().inflate(targetRange);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player player : monster.level().players()) {
            if (!(player instanceof ServerPlayer) || !player.isAlive()
                    || !player.getBoundingBox().intersects(targetBox)) {
                continue;
            }
            DemonLordState state = DemonLordStates.get(player.getUUID());
            if (state == null || !state.inCombat() || !state.canFight(monster.runtimeMonster())) {
                continue;
            }
            double distance = monster.distanceToSqr(player);
            if (distance <= targetRange * targetRange && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private LivingEntity findDefenseTarget() {
        AABB searchBox = monster.defenseSearchBox();
        AABB reachBox = new AABB(monster.position(), monster.position())
                .inflate(monster.defenseTargetSearchRange() + REACH_MARGIN);
        int laneId = monster.runtimeMonster().targetLaneId();
        List<? extends Entity> defenders = SemionMonsterEntity.anyDominated()
                ? monster.level().getEntities(monster, reachBox, entity -> entity instanceof LivingEntity livingEntity
                        && entity instanceof LaneDefenseEntity laneDefenseEntity
                        && laneDefenseEntity.defendsLane(laneId)
                        && entity.isAlive()
                        && laneDefenseEntity.drawsAggro()
                        && monster.canTargetDefense(livingEntity)
                        && entity.getBoundingBox().intersects(searchBox))
                : monster.level().getEntities(TOWERS, reachBox, tower -> tower.defendsLane(laneId)
                        && tower.isAlive()
                        && tower.drawsAggro()
                        && monster.canTargetDefense(tower)
                        && tower.getBoundingBox().intersects(searchBox));
        List<LivingEntity> candidates = defenders.stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(entity -> !(entity instanceof SemionTowerEntity towerEntity)
                        || towerEntity.runtimeTower() == null
                        || towerEntity.runtimeTower().targetableByMonsters())
                .toList();
        Comparator<LivingEntity> priority = Comparator
                .comparingInt((LivingEntity entity) -> ((LaneDefenseEntity) entity).aggroPriority()).reversed()
                .thenComparingDouble(monster::distanceToSqr);
        LivingEntity baseline = candidates.stream().min(priority).orElse(null);
        if (baseline == null) return null;
        double distance = monster.distanceToSqr(baseline);
        return candidates.stream().filter(entity -> prefersEqualDistance(entity)
                        && Math.abs(monster.distanceToSqr(entity) - distance) < 1.0e-9)
                .min(priority).orElse(baseline);
    }

    private static boolean prefersEqualDistance(LivingEntity entity) {
        if (!(entity instanceof SemionTowerEntity tower) || tower.runtimeTower() == null) return false;
        return AugmentTowers.is(tower.runtimeTower().type(), AugmentTowers.FOLDING_BARRICADE)
                || AugmentCombat.prefersEqualDistance(tower.runtimeTower());
    }
}
