package kim.biryeong.semionTd.entity.monster.goal;

import java.util.EnumSet;
import java.util.List;
import kim.biryeong.semionTd.entity.boss.SemionBossEntity;
import kim.biryeong.semionTd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LaneFollowGoal extends Goal {
    private static final double WAYPOINT_REACHED_DISTANCE = 1.2;
    private static final double BOSS_SEARCH_RANGE = 16.0;

    private final SemionMonsterEntity monster;
    private final double speedModifier;
    private int waypointIndex;

    public LaneFollowGoal(SemionMonsterEntity monster, double speedModifier) {
        this.monster = monster;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return monster.hasLanePath() && monster.isAlive() && monster.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return monster.hasLanePath() && monster.isAlive() && monster.getTarget() == null;
    }

    @Override
    public void start() {
        waypointIndex = 0;
    }

    @Override
    public void tick() {
        List<Vec3> points = monster.pathPoints();
        if (points.isEmpty()) {
            return;
        }

        if (waypointIndex >= points.size()) {
            SemionBossEntity boss = findBossTarget();
            if (boss != null) {
                monster.setTarget(boss);
            }
            return;
        }

        Vec3 targetPoint = points.get(waypointIndex);
        double distance = monster.position().distanceTo(targetPoint);
        if (distance <= WAYPOINT_REACHED_DISTANCE) {
            waypointIndex++;
            if (waypointIndex >= points.size()) {
                SemionBossEntity boss = findBossTarget();
                if (boss != null) {
                    monster.setTarget(boss);
                }
                monster.getNavigation().stop();
                return;
            }
            targetPoint = points.get(waypointIndex);
        }

        monster.getNavigation().moveTo(targetPoint.x, targetPoint.y, targetPoint.z, speedModifier);
        monster.getMoveControl().setWantedPosition(targetPoint.x, targetPoint.y, targetPoint.z, speedModifier);
        monster.getLookControl().setLookAt(targetPoint.x, targetPoint.y, targetPoint.z);
    }

    private SemionBossEntity findBossTarget() {
        AABB searchBox = monster.getBoundingBox().inflate(BOSS_SEARCH_RANGE);
        return monster.level().getEntities(monster, searchBox, entity -> entity instanceof SemionBossEntity && entity.isAlive())
                .stream()
                .filter(SemionBossEntity.class::isInstance)
                .map(SemionBossEntity.class::cast)
                .findFirst()
                .orElse(null);
    }
}
