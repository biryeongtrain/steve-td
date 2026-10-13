package kim.biryeong.semiontd.summon.invasion;

import java.util.List;
import kim.biryeong.semiontd.entity.monster.MonsterAttackStyle;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.income.IncomeTowerService;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTower;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.plant.PlantTowers;
import kim.biryeong.semiontd.tower.villager.VillagerThornTower;
import kim.biryeong.semiontd.util.Scheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 침공군 유닛의 공격 방식. 피해는 애니메이션에서 무기가 닿는 틱({@link MonsterAttackStyle#hitDelayTicks()})에
 * 들어가고, 연출도 그 순간에 뜹니다.
 */
final class InvasionAttacks {
    private InvasionAttacks() {
    }

    // ------------------------------------------------------------------ 대상 찾기

    /** {@code center} 둘레 {@code radius} 안에서 이 몬스터가 때릴 수 있는 방어 대상(타워·마왕). */
    static List<LivingEntity> defensesNear(SemionMonsterEntity attacker, Vec3 center, double radius) {
        double radiusSqr = radius * radius;
        AABB box = new AABB(center, center).inflate(radius, 2.5, radius);
        return attacker.level().getEntitiesOfClass(LivingEntity.class, box, entity ->
                attacker.canDamageDefense(entity) && horizontalDistanceSqr(entity.position(), center) <= radiusSqr);
    }

    /** {@code from}에서 {@code to}까지의 선분에 폭 {@code width}로 걸리는 방어 대상. */
    static List<LivingEntity> defensesOnLine(SemionMonsterEntity attacker, Vec3 from, Vec3 to, double width) {
        AABB box = new AABB(from, to).inflate(width + 1.0);
        return attacker.level().getEntitiesOfClass(LivingEntity.class, box, entity ->
                attacker.canDamageDefense(entity)
                        && (entity.getBoundingBox().inflate(width).contains(from)
                        || entity.getBoundingBox().inflate(width).clip(from, to).isPresent()));
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static Vec3 aimPoint(LivingEntity target, Vec3 fallback) {
        return target == null ? fallback : target.position().add(0, target.getBbHeight() * 0.5, 0);
    }

    private static long seed(SemionMonsterEntity attacker) {
        return attacker.level().getGameTime() + attacker.getId();
    }

    private static ServerLevel level(SemionMonsterEntity attacker) {
        return attacker.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private static boolean alive(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    // ------------------------------------------------------------------ 기본

    /** 한 대상만 때리는 공격. {@code vfx}는 맞는 순간 대상 발밑(또는 공격자)에 띄울 연출입니다. */
    static MonsterAttackStyle single(int hitDelay, VfxAt vfx) {
        return new MonsterAttackStyle() {
            @Override
            public int hitDelayTicks() {
                return hitDelay;
            }

            @Override
            public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                if (!alive(target)) {
                    return;
                }
                MonsterAttackStyle.strike(attacker, target, attacker.attackDamageAmount());
                if (vfx != null) {
                    vfx.play(attacker, target.position());
                }
            }
        };
    }

    static MonsterAttackStyle area(int hitDelay, double radius, int maxTargets, VfxAt vfx) {
        return new MonsterAttackStyle() {
            @Override
            public int hitDelayTicks() {
                return hitDelay;
            }

            @Override
            public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                Vec3 center = target != null ? target.position() : attacker.position().add(attacker.getLookAngle().scale(2.0));
                double damage = attacker.attackDamageAmount();
                boolean dispatch = IncomeTowerService.isDispatch(attacker.runtimeMonster());
                if (!dispatch || attacker.canDamageDefense(target)) {
                    for (LivingEntity victim : nearestFirst(defensesNear(attacker, center, radius), target, center, maxTargets)) {
                        double multiplier = dispatch && victim != target ? 0.5 : 1.0;
                        MonsterAttackStyle.strike(attacker, victim, damage * multiplier);
                    }
                }
                if (vfx != null) {
                    vfx.play(attacker, center);
                }
            }
        };
    }

    static List<LivingEntity> nearestFirst(List<LivingEntity> victims, LivingEntity primary, Vec3 center, int limit) {
        return victims.stream()
                .sorted(java.util.Comparator.<LivingEntity>comparingInt(victim -> victim == primary ? 0 : 1)
                        .thenComparingDouble(victim -> horizontalDistanceSqr(victim.position(), center)))
                .limit(Math.max(1, limit))
                .toList();
    }

    /** 맞는 순간의 연출을 어디에 띄울지. */
    @FunctionalInterface
    interface VfxAt {
        void play(SemionMonsterEntity attacker, Vec3 at);
    }

    // ------------------------------------------------------------------ 고블린: 비전투 타워 즉시 처치

    /**
     * 고블린 칼질. 공격력이 없는 타워(개발자 조합대·생산·지원 타워 등)는 한 번에 부숩니다. 가시·반격 타워는 예외입니다.
     * 부서진 타워는 다른 타워처럼 다음 라운드에 다시 섭니다.
     */
    static MonsterAttackStyle goblin(int hitDelay) {
        return new MonsterAttackStyle() {
            @Override
            public int hitDelayTicks() {
                return hitDelay;
            }

            @Override
            public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                if (!alive(target)) {
                    return;
                }
                if (target instanceof SemionTowerEntity towerEntity && isExecutable(towerEntity.runtimeTower())) {
                    PlayerLane lane = AreaEffectLaneIndex.findForMonster(attacker).orElse(null);
                    if (lane != null && lane.killTower(towerEntity.runtimeTower())) {
                        InvasionVfx.playAt(level(attacker), InvasionVfx.goblinExecute(seed(attacker)), target.position());
                        return;
                    }
                }
                MonsterAttackStyle.strike(attacker, target, attacker.attackDamageAmount());
            }
        };
    }

    /** 고블린이 한 번에 부술 수 있는 타워: 공격하지 않는 타워 중 반격하지 않고 무적이 아닌 것. */
    static boolean isExecutable(Tower tower) {
        if (tower == null || tower.invulnerable() || tower.health() <= 0.0 || tower.type().damage() > 0.0) {
            return false;
        }
        return !isCounterAttackTower(tower);
    }

    /** 맞으면 되받아치는 타워(선인장 계열·가시 주민·고대 도시). */
    static boolean isCounterAttackTower(Tower tower) {
        String id = tower.type().id();
        return tower instanceof VillagerThornTower
                || tower instanceof AncientCityTower
                || id.equals(PlantTowers.T1_DESERT_TOWER.id())
                || id.equals(PlantTowers.T2_DESERT_TOWER.id())
                || id.equals(PlantTowers.T3_DESERT_TOWER.id());
    }

    // ------------------------------------------------------------------ 드워프: 관통 탄환

    /** 총구에서 대상 쪽으로 곧게 날아가 선 위의 모든 방어 대상을 꿰뚫는 탄환. */
    static MonsterAttackStyle pierce(int hitDelay, double length, double width) {
        return new MonsterAttackStyle() {
            @Override
            public int hitDelayTicks() {
                return hitDelay;
            }

            @Override
            public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                Vec3 muzzle = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
                Vec3 aim = aimPoint(target, muzzle.add(attacker.getLookAngle()));
                Vec3 direction = aim.subtract(muzzle);
                if (direction.lengthSqr() < 1.0e-4) {
                    direction = attacker.getLookAngle();
                }
                Vec3 end = muzzle.add(direction.normalize().scale(length));
                double damage = attacker.attackDamageAmount();
                if (IncomeTowerService.isDispatch(attacker.runtimeMonster())) {
                    if (attacker.canDamageDefense(target)
                            && (target.getBoundingBox().inflate(width).contains(muzzle)
                            || target.getBoundingBox().inflate(width).clip(muzzle, end).isPresent())) {
                        MonsterAttackStyle.strike(attacker, target, damage);
                    }
                } else {
                    for (LivingEntity victim : defensesOnLine(attacker, muzzle, end, width)) {
                        MonsterAttackStyle.strike(attacker, victim, damage);
                    }
                }
                Vec3 origin = attacker.position();
                InvasionVfx.playAt(level(attacker), InvasionVfx.dwarfShot(
                        InvasionVfx.relative(origin, muzzle.add(direction.normalize().scale(0.8))),
                        InvasionVfx.relative(origin, end), seed(attacker)), origin);
            }
        };
    }

    // ------------------------------------------------------------------ 트롤: 투창

    /** 창을 놓는 틱에 투창이 날아가기 시작해, 대상에 닿는 틱에 피해가 들어갑니다. */
    static MonsterAttackStyle javelin(int releaseDelay, double blocksPerTick) {
        return new MonsterAttackStyle() {
            @Override
            public int hitDelayTicks() {
                return releaseDelay;
            }

            @Override
            public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                if (!alive(target)) {
                    return;
                }
                Vec3 hand = attacker.position().add(0, attacker.getBbHeight() * 0.8, 0);
                Vec3 aim = aimPoint(target, hand);
                int flight = Math.max(1, Mth.ceil(hand.distanceTo(aim) / Math.max(0.1, blocksPerTick)));
                double damage = attacker.attackDamageAmount();
                Vec3 origin = attacker.position();
                InvasionVfx.playAt(level(attacker), InvasionVfx.trollJavelin(
                        InvasionVfx.relative(origin, hand), InvasionVfx.relative(origin, aim), flight), origin);
                // 연출은 틱 2부터 날아가므로 그만큼 늦게 맞힙니다. 던진 뒤 트롤이 죽어도 창은 날아가 맞습니다.
                Scheduler.INSTANCE.submit(server -> {
                    if (alive(target)) {
                        MonsterAttackStyle.strike(attacker, target, damage);
                    }
                }, flight + 2);
            }
        };
    }

    // ------------------------------------------------------------------ 연출 도우미

    static VfxAt at(java.util.function.LongFunction<kim.biryeong.semiontd.vfx.DisplayEffect> make) {
        return (attacker, at) -> InvasionVfx.playAt(level(attacker), make.apply(seed(attacker)), at);
    }

    /**
     * 공격자 발밑에, 공격자에서 맞은 자리({@code at})를 향하는 방향으로 띄웁니다. 몸이 도는 속도와 상관없이
     * 연출이 항상 대상 쪽을 향합니다.
     */
    static VfxAt facing(java.util.function.BiFunction<Float, Long, kim.biryeong.semiontd.vfx.DisplayEffect> make) {
        return (attacker, at) -> {
            Vec3 from = attacker.position();
            double dx = at.x - from.x;
            double dz = at.z - from.z;
            float yaw = dx * dx + dz * dz < 1.0e-6
                    ? (float) Math.toRadians(-attacker.getYRot())
                    : kim.biryeong.semiontd.vfx.DisplayShapes.yawOf(dx, dz);
            InvasionVfx.playAt(level(attacker), make.apply(yaw, seed(attacker)), from);
        };
    }

    static Vector3f zero() {
        return new Vector3f();
    }
}
