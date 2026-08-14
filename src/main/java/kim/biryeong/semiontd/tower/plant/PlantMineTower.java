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
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;

/**
 * 균사 계열 전투 타워. 공격하지 않고 묻혀 있다가 적이 밟으면 한 번 터지고 사라집니다.
 *
 * <p>폭발은 범위 피해와 함께 둔화, 그리고 공격 속도와 공격력을 동시에 100% 깎아 사실상 공격 불가
 * 상태를 만듭니다. 한 번 쓰면 없어지므로 웨이브를 읽고 길목에 미리 심는 소모품입니다.
 */
public class PlantMineTower extends PlantCombatTower {
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

    @Override
    protected boolean execute(PlayerLane lane) {
        if (lane == null || health() <= 0.0) {
            return true;
        }
        SemionTowerEntity source = towerEntity(lane).orElse(null);
        if (source == null || !triggered(lane)) {
            return true;
        }
        detonate(lane, source);
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        // 밟자마자 터져야 하므로 촘촘하게 확인합니다.
        return Math.max(1, abilityTicks("triggerIntervalTicks"));
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

        // 소모품이라 터지면 사라집니다. tickTowers 가 복사본을 순회하므로 여기서 제거해도 안전합니다.
        lane.removeTower(this);
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
        lines.add("무력화 " + oneDecimal(abilityTicks("explosionDisableTicks") / 20.0) + "초");
        return lines;
    }
}
