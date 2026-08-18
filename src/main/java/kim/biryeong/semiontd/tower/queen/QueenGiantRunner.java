package kim.biryeong.semiontd.tower.queen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

final class QueenGiantRunner {
    static final double REQUIRED_EXECUTION_VISUAL_SHRINK = 0.30;
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "queen_giant_run");
    private static final ResourceLocation SPAWN_EFFECT_ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "queen_giant_spawn");
    private final Entity entity;
    private final SemionTowerEntity source;
    private final List<Vec3> path;
    private final Set<UUID> contacted = new HashSet<>();
    private int segment = 1;
    private boolean active = true;

    private QueenGiantRunner(Entity entity, SemionTowerEntity source, List<Vec3> path) {
        this.entity = entity;
        this.source = source;
        this.path = path;
    }

    static boolean dispatch(QueenTower queen, PlayerLane lane, QueenStates.PlayerState state) {
        if (queen == null || lane == null || state == null || state.runnerActive()) return false;
        SemionTowerEntity source = queen.entity().orElse(null);
        if (source == null) return false;
        List<Vec3> path = queen.deployedAtFinalDefense() ? finalDefensePath(lane) : reverseLanePath(lane);
        if (path.size() < 2) return false;
        Entity giant = EntityType.GIANT.create(lane.arenaWorld(), EntitySpawnReason.TRIGGERED);
        if (giant == null) return false;
        giant.setInvulnerable(true);
        giant.setSilent(true);
        giant.setCustomName(Component.literal("자이언트"));
        giant.setCustomNameVisible(true);
        if (giant instanceof Mob mob) mob.setNoAi(true);
        Vec3 start = path.getFirst();
        giant.setPos(start.x, start.y, start.z);
        orientToward(giant, path.get(1));
        if (!lane.arenaWorld().addFreshEntity(giant)) return false;
        TowerVfxService.showAreaEffect(
                source, SPAWN_EFFECT_ID, AreaVfxStyles.PULSE, start, 2.5, List.of(), 0, 0, 0
        );
        state.runner(new QueenGiantRunner(giant, source, path));
        return true;
    }

    boolean active() {return active && !entity.isRemoved();}
    Vec3 position() {return entity.position();}
    float yaw() {return entity.getYRot();}

    void tick(QueenTower queen, PlayerLane lane, QueenStates.PlayerState state) {
        if (!active() || queen == null || lane == null || state == null) {
            remove();
            if (state != null) state.runner(null);
            return;
        }
        move();
        contact(queen, state);
        if (!active) state.runner(null);
    }

    void remove() {
        active = false;
        if (!entity.isRemoved()) entity.discard();
    }

    private void move() {
        double remaining = QueenBalance.giantSpeed();
        Vec3 current = entity.position();
        while (remaining > 0.0 && segment < path.size()) {
            Vec3 target = path.get(segment);
            Vec3 delta = target.subtract(current);
            double distance = delta.length();
            if (distance > 0.0001) orientToward(entity, target);
            if (distance <= remaining + 0.0001) {
                current = target;
                remaining -= distance;
                segment++;
                continue;
            }
            Vec3 step = delta.normalize().scale(remaining);
            current = current.add(step);
            remaining = 0.0;
        }
        entity.setPos(current.x, current.y, current.z);
        if (segment >= path.size()) remove();
    }

    private void contact(QueenTower queen, QueenStates.PlayerState state) {
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                EFFECT_ID, source, entity.position(), QueenBalance.giantContactRadius(), Set.of(),
                target -> target.runtimeMonster() != null
                        && target.runtimeMonster().targetTeam() == queen.teamId()
                        && !contacted.contains(target.getUUID()), AreaVfxSpec.onChange(AreaVfxStyles.DEBUFF));
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            contacted.add(target.getUUID());
            if (target.runtimeMonster() == null) return AreaEffectOutcome.UNCHANGED;
            if (canExecute(target.runtimeMonster(), state.executionHealth())) {
                double effectiveMaxHealth = target.runtimeMonster().maxHealth();
                double lethalDamage = Math.max(1.0, effectiveMaxHealth * 1_000_000.0);
                var damageResult = queen.damageResolvedTargetResult(source, target, lethalDamage, DamageType.TRUE);
                if (damageResult.killed()) {
                    queen.onKill(source, target, damageResult.outgoingDamage());
                    state.growExecutionHealth(effectiveMaxHealth);
                    return AreaEffectOutcome.KILLED;
                }
            }
            target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION,
                    QueenBalance.giantSlow(), QueenBalance.giantSlowTicks());
            target.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION,
                    QueenBalance.giantSlow(), QueenBalance.giantSlowTicks());
            return AreaEffectOutcome.APPLIED;
        });
    }

    static boolean canExecute(Monster target, double executionHealth) {
        return target != null
                && target.health() <= executionHealth
                && hasRequiredVisualShrink(target);
    }

    static boolean hasRequiredVisualShrink(Monster target) {
        return target != null
                && target.visualScale() <= 1.0 - REQUIRED_EXECUTION_VISUAL_SHRINK + 1.0e-9;
    }

    private static List<Vec3> finalDefensePath(PlayerLane lane) {
        ArrayList<Vec3> path = new ArrayList<>(lane.finalDefensePathLane().laneLayout().pathPoints());
        Collections.reverse(path);
        return path;
    }

    private static List<Vec3> reverseLanePath(PlayerLane lane) {
        List<Vec3> waypoints = lane.laneLayout().personalWaypoints();
        if (waypoints.isEmpty()) waypoints = lane.laneLayout().waypoints();
        ArrayList<Vec3> path = new ArrayList<>(waypoints.size() + 1);
        path.add(lane.laneLayout().spawn());
        path.addAll(waypoints);
        Collections.reverse(path);
        return path;
    }

    private static void orientToward(Entity entity, Vec3 target) {
        Vec3 delta = target.subtract(entity.position());
        if (delta.horizontalDistanceSqr() <= 0.000001) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        entity.setYRot(yaw);
        if (entity instanceof Mob mob) {
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
        }
    }
}
