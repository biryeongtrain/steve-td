package kim.biryeong.semiontd.tower.plant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 균사 계열 전투 타워. 공격하지 않고 묻혀 있다가 적이 밟으면 터집니다.
 *
 * <p>폭발은 범위 피해와 함께 둔화, 그리고 공격 속도와 공격력을 동시에 100% 깎아 사실상 공격 불가
 * 상태를 만듭니다.
 *
 * <p><b>한 라운드에 한 번</b>만 터집니다. 터진 뒤에는 그 라운드 내내 빈 껍데기로 남고, 라운드가
 * 끝나면 한 단계 삭아 내려갑니다(뒤틀린 → 진홍빛 → 붉은). 붉은 버섯은 사라집니다.
 *
 * <p>예전에는 터지는 즉시 사라졌습니다. 소모 단위를 폭발 한 번에서 라운드 하나로 옮긴 것이라,
 * 뒤틀린 버섯을 심으면 세 라운드에 걸쳐 세 번 쓰는 셈입니다. 라운드 안에서 다시 장전하게 두면
 * 지뢰 하나가 광역 기관총이 되고, 무력화 시간이 재장전보다 길면 그 길목의 적은 영영 공격하지
 * 못합니다. 라운드당 한 번이면 그 두 가지를 값 조정 없이 구조로 막습니다.
 *
 * <p>삭아 내리는 처리는 {@code PlantTowerJob#onRoundEnded} 가 맡습니다 - 라운드 경계를 아는 쪽은
 * 타워가 아니라 직업입니다.
 */
public class PlantMineTower extends PlantCombatTower {
    /** 이번 라운드에 이미 터졌는지. 라운드가 새로 시작될 때만 풀립니다. */
    private boolean spentThisRound;

    /** 점화됐고 아직 안 터졌는지. 섬광이 뜬 뒤 도화선이 타는 동안 켜져 있습니다. */
    private boolean fuseLit;

    public PlantMineTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public PlantMineTower(
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
     * 몬스터는 지뢰를 때리지 않습니다.
     *
     * <p>터지고 사라지던 시절에는 어그로를 끌든 말든 상관이 없었습니다. 라운드 내내 남게 된
     * 지금은 다릅니다 - 어그로를 끌면 지뢰가 값싼 고기방패가 되어, 방금 사암 계열에서 막은
     * 도배 벽을 더 싼값에 세울 수 있습니다. 붉은 버섯은 30 다이아에 체력 110 으로 죽은 덤불보다
     * 다이아당 체력이 좋습니다.
     *
     * <p>매설된 함정이라는 역할에도 이쪽이 맞습니다. 적은 버섯을 물어뜯는 게 아니라 밟고 지나가야
     * 합니다. 무적은 아니라서 광역 피해로는 깎이고, 깎이면 폭발도 약해집니다.
     */
    @Override
    public boolean drawsAggro() {
        return false;
    }

    /** 터진 지뢰인지. 그 라운드 안에서는 다시 터지지 않습니다. */
    public boolean spentThisRound() {
        return spentThisRound;
    }

    /**
     * 라운드가 새로 시작되면 다시 장전됩니다.
     *
     * <p>삭아 내리는 과정에서 타워를 새로 만들기 때문에 사실 대부분 새 인스턴스로 시작하지만,
     * 그 사실에 기대지 않습니다. 되감기는 라운드 경계에서 벌어지는 일이고, 그건 이 훅의 몫입니다.
     */
    @Override
    public void resetForRound(PlayerLane lane) {
        super.resetForRound(lane);
        spentThisRound = false;
        fuseLit = false;
    }

    /**
     * 밟으면 먼저 섬광이 뜨고, 도화선이 다 타면 터집니다.
     *
     * <p>즉발이면 밟는 순간 이미 맞은 뒤라 피할 여지가 없습니다. 섬광은 "여기 밟았다"는 신호이고,
     * 도화선이 타는 동안 빠져나가면 폭발을 피할 수 있습니다 - 폭발 판정은 터지는 시점에 다시
     * 잡으므로, 그 사이에 나간 적은 실제로 안 맞습니다.
     *
     * <p>점화된 뒤에는 적이 사라져도 취소하지 않습니다. 도화선에 불이 붙었으면 타야 하고, 취소를
     * 허용하면 적이 스치듯 지나갈 때마다 무료로 예열해 두는 짓이 가능해집니다.
     */
    @Override
    protected boolean execute(PlayerLane lane) {
        if (lane == null || spentThisRound || health() <= 0.0) {
            return true;
        }
        SemionTowerEntity source = towerEntity(lane).orElse(null);
        if (source == null) {
            return true;
        }
        if (fuseLit) {
            detonate(lane, source);
            fuseLit = false;
            spentThisRound = true;
            return true;
        }
        if (!triggered(lane)) {
            return true;
        }
        lightFuse(source);
        fuseLit = true;
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        // 도화선이 타는 동안은 그 길이만큼 기다렸다가 터뜨립니다.
        if (fuseLit) {
            return Math.max(1, abilityTicks("fuseTicks"));
        }
        // 밟자마자 점화돼야 하므로 평소에는 촘촘하게 확인합니다.
        return Math.max(1, abilityTicks("triggerIntervalTicks"));
    }

    /** 지뢰가 점화됐음을 알리는 섬광 하나와 도화선 소리. */
    private void lightFuse(SemionTowerEntity source) {
        Vec3 center = source.position();
        TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(this, "spore_mine_fuse"),
                PlantVfx.MINE_FUSE,
                center,
                Math.max(1.0, ability("explosionRadius")),
                List.of(),
                0,
                0,
                0
        );
        source.level().playSound(
                null, center.x, center.y, center.z,
                SoundEvents.CREEPER_PRIMED, SoundSource.BLOCKS, 0.8f, 1.4f);
    }

    private boolean triggered(PlayerLane lane) {
        double radius = ability("triggerRadius");
        if (radius <= 0.0) {
            return false;
        }
        double radiusSqr = radius * radius;
        double x = position().x() + 0.5;
        double z = position().z() + 0.5;
        for (Monster monster : List.copyOf(lane.activeMonsters())) {
            if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()) {
                continue;
            }
            if (!(lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)
                    || entity.isRemoved()) {
                continue;
            }
            double dx = entity.getX() - x;
            double dz = entity.getZ() - z;
            if (dx * dx + dz * dz <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private void detonate(PlayerLane lane, SemionTowerEntity source) {
        double radius = Math.max(1.0, ability("explosionRadius"));
        double damage = explosionDamage();
        double slow = ability("explosionMoveSpeedReduction");
        int disableTicks = abilityTicks("explosionDisableTicks");

        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "spore_mine"),
                source,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        );
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            boolean killed = damage > 0.0
                    && damageResolvedTargetResult(source, monster, damage, DamageType.MAGIC).killed();
            if (disableTicks > 0) {
                if (slow > 0.0) {
                    monster.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, disableTicks);
                }
                // 공격 속도와 공격력을 함께 100% 깎아 공격을 무력화합니다.
                monster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, 1.0, disableTicks);
                monster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION, 1.0, disableTicks);
            }
            if (killed) {
                onKill(source, monster, damage);
                return AreaEffectOutcome.KILLED;
            }
            return AreaEffectOutcome.APPLIED;
        });
    }

    /**
     * 폭발 피해는 공격력뿐 아니라 <b>남은 체력</b>도 함께 터뜨립니다. 온전한 지뢰일수록 세게 터지고,
     * 미리 두들겨 맞아 체력이 깎이면 그만큼 약해집니다.
     */
    public double explosionDamage() {
        double base = type().damage() * ability("explosionDamageMultiplier")
                + health() * ability("explosionHealthRatio");
        return base * (1.0 + bloomBonus());
    }

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new ArrayList<>(super.runtimeDetailLines());
        lines.add("지뢰 발동 반경 " + oneDecimal(ability("triggerRadius"))
                + " · 폭발 반경 " + oneDecimal(ability("explosionRadius")));
        lines.add("폭발 피해 " + oneDecimal(explosionDamage())
                + " (체력 " + oneDecimal(health() * ability("explosionHealthRatio")) + " 포함)");
        lines.add("무력화 " + oneDecimal(abilityTicks("explosionDisableTicks") / 20.0) + "초"
                + " · 도화선 " + oneDecimal(abilityTicks("fuseTicks") / 20.0) + "초");
        if (spentThisRound) {
            lines.add("이번 라운드에 이미 터졌습니다 · 라운드가 끝나면 한 단계 삭습니다");
        } else if (fuseLit) {
            lines.add("점화됨");
        } else {
            lines.add("라운드당 한 번 터집니다");
        }
        return lines;
    }
}
