package kim.biryeong.semiontd.tower.plant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.world.phys.Vec3;

/**
 * 판다. 지형을 깔지 않고 어디에나 세우는 이동형 근접 타워입니다.
 *
 * <p>식물 빌더의 다른 타워들과 정반대입니다 - 자기 계열 지형 위에만 심을 수 있고 뿌리를 내려
 * 움직이지 않는 것이 식물의 규칙인데, 판다는 지형이 필요 없고 적을 쫓아 걸어갑니다. 지형 계열에
 * 묶이지 않으므로 개화·회복·성장 같은 지형 효과도 일절 받지 않습니다. 지형을 아직 못 깐 초반이나
 * 지형이 꽉 찬 뒤에 쓸 수 있는, 계열 밖의 선택지입니다.
 *
 * <p>평소에는 평범한 근접 공격이고, 주기적으로 앞으로 돌진해 경로에 걸린 적을 한꺼번에 밀어냅니다.
 * 돌진 피해는 공격력이 아니라 <b>자기 최대 체력 비율</b>이라, 티어를 올려 단단해질수록 그대로
 * 화력이 됩니다.
 */
public class PandaTower extends ProductionTower {
    @Override
    public double modifyAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        return super.modifyAttackDamage(source, target, damage) * (1.0 + PlantAugments.worldTreeBonus(this));
    }

    @Override
    protected double builderCurrentMaxHealth() {
        return super.builderCurrentMaxHealth() * (1.0 + PlantAugments.worldTreeBonus(this));
    }

    public PandaTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public PandaTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    /**
     * 돌진 한 번. 실행 간격이 곧 돌진 쿨타임입니다.
     *
     * <p>노릴 적이 없으면 아무 일도 하지 않고 짧게 다시 확인합니다. 빈 돌진으로 쿨타임을 태우면
     * 정작 몰려올 때 못 쓰기 때문입니다.
     */
    @Override
    protected boolean execute(PlayerLane lane) {
        if (lane == null || health() <= 0.0) {
            return false;
        }
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source == null) {
            return false;
        }
        double distance = ability("chargeDistance");
        double hitRadius = ability("chargeHitRadius");
        if (distance <= 0.0 || hitRadius <= 0.0) {
            return false;
        }
        SemionMonsterEntity target = nearestMonster(lane, source.position(), distance);
        if (target == null) {
            return false;
        }
        charge(lane, source, target, distance, hitRadius);
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return Math.max(1, abilityTicks("chargeIntervalTicks"));
    }

    /**
     * 돌진 피해는 <b>자기 최대 체력</b> 비율입니다.
     *
     * <p>공격력 기준으로 잡으면 근접 평타와 같은 축을 두 번 타서, 체력을 올리는 선택이 화력에
     * 아무 의미가 없어집니다. 체력 기준이면 "맞아 가며 밀어붙이는" 역할이 그대로 수치가 됩니다.
     */
    public double chargeDamage() {
        return currentMaxHealth() * Math.max(0.0, ability("chargeHealthRatio"));
    }

    private void charge(
            PlayerLane lane,
            SemionTowerEntity source,
            SemionMonsterEntity target,
            double distance,
            double hitRadius
    ) {
        Vec3 start = source.position();
        Vec3 direction = horizontal(target.position().subtract(start));
        Vec3 end = start.add(direction.scale(distance));
        double damage = chargeDamage();
        double knockback = ability("chargeKnockback");
        int debuffTicks = abilityTicks("chargeDebuffTicks");
        double attackSpeedReduction = ability("chargeAttackSpeedReduction");
        double rangeReduction = ability("chargeRangeReduction");

        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "panda_charge"),
                source,
                start.lerp(end, 0.5),
                distance / 2.0 + hitRadius,
                java.util.Set.of(),
                monster -> distanceToSegment(monster.position(), start, end) <= hitRadius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            Tower.DamageResult result = damageResolvedTargetResult(source, monster, damage, DamageType.PHYSICAL);
            if (result.killed()) {
                onKill(source, monster, damage);
                return AreaEffectOutcome.KILLED;
            }
            knockBack(monster, start, knockback);
            if (debuffTicks > 0) {
                if (attackSpeedReduction > 0.0) {
                    monster.applyTimedEffect(
                            TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, attackSpeedReduction, debuffTicks);
                }
                if (rangeReduction > 0.0) {
                    monster.applyTimedEffect(
                            TimedEffectType.MONSTER_ATTACK_RANGE_REDUCTION, rangeReduction, debuffTicks);
                }
            }
            // 어그로 초기화. 밀어내고도 표적이 그대로면 밀린 자리에서 그대로 다시 달려듭니다.
            monster.setTarget(null);
            return result.dealtDamage() > 0.0 ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });

        // 판다 자신도 앞으로 나갑니다. 좌표를 직접 옮기지 않는 것은 이 타워가 원래 걸어 다니는
        // 타워라, 속도만 주면 이후 이동은 평소 경로 탐색이 이어받기 때문입니다.
        source.setDeltaMovement(direction.x * knockback, 0.25, direction.z * knockback);
        source.hurtMarked = true;
    }

    private static void knockBack(SemionMonsterEntity monster, Vec3 from, double strength) {
        if (strength <= 0.0) {
            return;
        }
        Vec3 away = horizontal(monster.position().subtract(from)).scale(strength);
        monster.setDeltaMovement(away.x, 0.35, away.z);
        monster.hurtMarked = true;
    }

    /** 반경 안에서 가장 가까운 살아 있는 몹. 돌진 방향을 정하는 데만 씁니다. */
    private SemionMonsterEntity nearestMonster(PlayerLane lane, Vec3 center, double radius) {
        double radiusSqr = radius * radius;
        SemionMonsterEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var monster : List.copyOf(lane.activeMonsters())) {
            if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()) {
                continue;
            }
            if (!(lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)
                    || entity.isRemoved()) {
                continue;
            }
            double distanceSqr = entity.position().distanceToSqr(center);
            if (distanceSqr <= radiusSqr && distanceSqr < bestDistance) {
                bestDistance = distanceSqr;
                best = entity;
            }
        }
        return best;
    }

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new ArrayList<>(super.runtimeDetailLines());
        lines.add("돌진 피해 " + Math.round(chargeDamage())
                + " (최대 체력 " + Math.round(ability("chargeHealthRatio") * 100.0) + "%)");
        lines.add("돌진 주기 " + String.format("%.1f", abilityTicks("chargeIntervalTicks") / 20.0) + "초"
                + " · 거리 " + String.format("%.1f", ability("chargeDistance")));
        return List.copyOf(lines);
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0, vector.z);
        return flat.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    /** 점과 선분 사이 거리. 돌진 경로에 걸렸는지 판정합니다. */
    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0e-6) {
            return point.distanceTo(start);
        }
        double projection = Math.max(0.0, Math.min(1.0,
                point.subtract(start).dot(segment) / lengthSqr));
        return point.distanceTo(start.add(segment.scale(projection)));
    }

    private double ability(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int abilityTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

}
