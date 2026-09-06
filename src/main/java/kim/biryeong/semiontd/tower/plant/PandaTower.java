package kim.biryeong.semiontd.tower.plant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * <p>평소에는 평범한 근접 공격이고, 주기적으로 앞으로 <b>실제로 달려나가</b> 지나친 적을 밀어냅니다.
 * 돌진 피해는 공격력이 아니라 <b>자기 최대 체력 비율</b>이라, 티어를 올려 단단해질수록 그대로
 * 화력이 됩니다.
 */
public class PandaTower extends ProductionTower {
    /** 돌진이 몇 틱에 걸쳐 진행되는지. 짧으면 순간이동처럼 보이고 길면 굼떠 보입니다. */
    private static final int DASH_TICKS = 8;

    /** 남은 돌진 틱. 0 보다 크면 지금 달리는 중입니다. */
    private int dashTicksLeft;

    /** 이번 돌진의 방향. 달리는 동안 고정입니다 - 도중에 꺾이면 돌진이 아니라 추적입니다. */
    private Vec3 dashDirection = Vec3.ZERO;

    /** 이번 돌진에 이미 치인 대상. 같은 몹을 매 틱 갈아 버리지 않게 합니다. */
    private final Set<UUID> dashHits = new HashSet<>();
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
     * 돌진 한 번. 여러 틱에 걸쳐 실제로 달립니다.
     *
     * <p>이 메서드는 돌진 중에는 매 틱, 평소에는 주기마다 불립니다 -
     * {@link #cooldownTicksAfterExecute} 가 상태에 따라 1 틱과 재사용 주기를 오갑니다.
     *
     * <p>한 번에 판정을 끝내고 끝점으로 순간이동시키지 않는 이유는, 그러면 화면에서 제자리
     * 폭발로만 보이기 때문입니다. 달리는 동안 스친 적만 맞아야 "치고 들어간다" 는 것이 보입니다.
     *
     * <p>노릴 적이 없으면 아무 일도 하지 않고 짧게 다시 확인합니다. 빈 돌진으로 재사용 시간을
     * 태우면 정작 몰려올 때 못 씁니다.
     */
    @Override
    protected boolean execute(PlayerLane lane) {
        if (lane == null || health() <= 0.0) {
            endDash();
            return false;
        }
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source == null) {
            endDash();
            return false;
        }
        if (dashTicksLeft > 0) {
            advanceDash(source);
            return true;
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
        beginDash(source, target);
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        // 달리는 동안에는 매 틱 돌아와야 발이 움직입니다.
        return dashTicksLeft > 0 ? 1 : Math.max(1, abilityTicks("chargeIntervalTicks"));
    }

    /** 지금 달리는 중인지. 정보창과 테스트가 봅니다. */
    public boolean dashing() {
        return dashTicksLeft > 0;
    }

    private void beginDash(SemionTowerEntity source, SemionMonsterEntity target) {
        dashDirection = horizontal(target.position().subtract(source.position()));
        dashTicksLeft = DASH_TICKS;
        dashHits.clear();
        // 달리는 동안 경로 탐색이 끼어들면 방향이 꺾여 돌진이 아니라 추적이 됩니다.
        source.getNavigation().stop();
        advanceDash(source);
    }

    private void endDash() {
        dashTicksLeft = 0;
        dashHits.clear();
    }

    /**
     * 돌진 한 걸음. 앞으로 밀고, 그 자리에서 새로 스친 적만 때립니다.
     *
     * <p>벽에 막히면 거기서 멈춥니다. 판다를 밀어 넣는 것이 아니라 이동 자체를 바닐라 충돌에
     * 맡기기 때문에, 아레나 밖으로 뚫고 나갈 일이 없습니다.
     */
    private void advanceDash(SemionTowerEntity source) {
        double step = ability("chargeDistance") / DASH_TICKS;
        source.move(net.minecraft.world.entity.MoverType.SELF, dashDirection.scale(step));
        source.hurtMarked = true;
        sweep(source);
        dashTicksLeft--;
        if (dashTicksLeft <= 0) {
            endDash();
        }
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

    /**
     * 지금 서 있는 자리를 훑습니다. 이번 돌진에 아직 안 맞은 적만 대상입니다.
     *
     * <p>이미 맞은 대상을 걸러 내지 않으면 달리는 여덟 틱 동안 같은 몹이 여덟 번 갈립니다.
     * 돌진은 지나치며 한 번 치이는 기술이지 장판이 아닙니다.
     */
    private void sweep(SemionTowerEntity source) {
        double hitRadius = ability("chargeHitRadius");
        if (hitRadius <= 0.0) {
            return;
        }
        double damage = chargeDamage();
        double knockback = ability("chargeKnockback");
        int debuffTicks = abilityTicks("chargeDebuffTicks");
        double attackSpeedReduction = ability("chargeAttackSpeedReduction");
        double rangeReduction = ability("chargeRangeReduction");
        Vec3 here = source.position();

        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "panda_charge"),
                source,
                here,
                hitRadius,
                java.util.Set.copyOf(dashHits),
                monster -> !dashHits.contains(monster.getUUID()),
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            dashHits.add(monster.getUUID());
            Tower.DamageResult result = damageResolvedTargetResult(source, monster, damage, DamageType.PHYSICAL);
            if (result.killed()) {
                onKill(source, monster, damage);
                return AreaEffectOutcome.KILLED;
            }
            knockBack(monster, here, knockback);
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
                + " · 거리 " + String.format("%.1f", ability("chargeDistance"))
                + (dashing() ? " · 돌진 중" : ""));
        return List.copyOf(lines);
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0, vector.z);
        return flat.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    private double ability(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int abilityTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

}
