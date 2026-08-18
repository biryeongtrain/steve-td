package kim.biryeong.semiontd.tower.demonlord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * A demon lord altar. It grants its skill to the owning player and does nothing else.
 *
 * <p>All three combat hooks are switched off: it never chases, never draws aggro and never takes
 * damage. Leaving it killable would be a trap, because the builder has no defensive tower to
 * protect it with and losing an altar mid-round would silently delete a skill the player paid for.
 */
public class DemonLordSkillTower extends ProductionTower {
    private DemonLordBinding binding;

    public DemonLordSkillTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public DemonLordSkillTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public DemonLordSkill skill() {
        return DemonLordTowers.skillOf(type());
    }

    public int tier() {
        return DemonLordTowers.tierOf(type());
    }

    public int cooldownTicks() {
        return DemonLordTowers.cooldownTicks(type());
    }

    SemionTowerEntity entity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity entity
                ? entity
                : null;
    }

    /**
     * Key this altar answers to, refreshed by {@code DemonLordService} whenever the bar is rebuilt.
     *
     * <p>Cached on the tower purely so the tower info panel can show it - the binding itself is
     * always derived from build order, never stored as the source of truth.
     */
    public DemonLordBinding binding() {
        return binding;
    }

    void setBinding(DemonLordBinding binding) {
        this.binding = binding;
    }

    @Override
    public boolean canChaseTargets() {
        return false;
    }

    @Override
    public boolean invulnerable() {
        return true;
    }

    @Override
    public boolean drawsAggro() {
        return false;
    }

    /**
     * Placing or upgrading an altar changes which items belong in the hotbar, so the loadout is
     * marked dirty and the next service tick rebuilds slots 3-7.
     */
    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        DemonLordStates.markLoadoutDirty(ownerPlayer());
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        DemonLordStates.markLoadoutDirty(ownerPlayer());
        super.onRemoved(lane);
    }

    /**
     * 제단 정보창. 툴팁의 기본 수치가 아니라 <b>지금 이 마왕이 쓰면 나오는 값</b>을 보여줍니다.
     *
     * <p>툴팁은 밸런스 파일의 고정값이라 레벨과 스탯이 반영되지 않습니다. 마왕은 성장이 전부인
     * 빌더인데 정작 얼마나 세졌는지 확인할 곳이 없으면, 스탯을 어디에 넣을지 판단할 근거가
     * 사라집니다. 증폭이 하나도 없는 동안(레벨 1, 미투자)에는 굳이 줄을 늘리지 않습니다.
     */
    @Override
    public List<String> runtimeDetailLines() {
        DemonLordSkill skill = skill();
        if (skill == null) {
            return List.of();
        }
        DemonLordState state = DemonLordStates.get(ownerPlayer());
        List<String> lines = new ArrayList<>();
        lines.add(skill.displayName() + " · 쿨타임 " + seconds(effectiveCooldownTicks(state)) + "초"
                + (state == null || state.cooldownMultiplier() >= 1.0
                        ? ""
                        : " (기본 " + seconds(cooldownTicks()) + "초)"));
        lines.add("코스트 " + skill.slotCost()
                + (binding == null ? " · 키 없음" : " · [" + binding.label() + "] 키"));
        if (state == null) {
            return List.copyOf(lines);
        }

        String damage = amplified("damage", state.damageMultiplier());
        if (damage != null) {
            lines.add("피해 " + damage);
        }
        String area = amplified("areaDamage", state.damageMultiplier());
        if (area != null) {
            lines.add("범위 피해 " + area);
        }
        for (String key : REACH_KEYS) {
            String reach = amplified(key, state.skillRangeMultiplier());
            if (reach != null) {
                lines.add(REACH_LABELS.get(key) + " " + reach + "칸");
            }
        }
        lines.add("Lv." + state.level() + " · 피해 ×" + String.format("%.2f", state.damageMultiplier()));
        return List.copyOf(lines);
    }

    /** 배율이 걸리는 거리 계열 키. {@code DemonLordSkills.reach} 가 곱해 주는 것과 같은 목록입니다. */
    private static final List<String> REACH_KEYS = List.of(
            "range", "radius", "dashDistance", "blastRadius", "explosionRadius",
            "hitRadius", "placementRange", "projectileRange", "zoneRadius");

    private static final Map<String, String> REACH_LABELS = Map.of(
            "range", "사거리",
            "radius", "반경",
            "dashDistance", "돌진 거리",
            "blastRadius", "폭발 반경",
            "explosionRadius", "폭발 반경",
            "hitRadius", "적중 반경",
            "placementRange", "설치 거리",
            "projectileRange", "투사 거리",
            "zoneRadius", "장판 반경");

    /**
     * 밸런스 값에 배율을 곱한 표시 문자열. 해당 키가 없으면 {@code null}.
     *
     * <p>배율이 1이면 증폭 표기 없이 값만 보여, 아직 성장하지 않은 마왕의 정보창이 괄호로
     * 도배되지 않게 합니다.
     */
    private String amplified(String key, double multiplier) {
        double base = TowerBalanceRuntime.ability(type().id(), key, Double.NaN);
        if (Double.isNaN(base) || base <= 0.0) {
            return null;
        }
        double value = base * multiplier;
        String shown = String.format("%.1f", value);
        return multiplier == 1.0 ? shown : shown + " (기본 " + String.format("%.1f", base) + ")";
    }

    private int effectiveCooldownTicks(DemonLordState state) {
        if (state == null) {
            return cooldownTicks();
        }
        return Math.max(1, (int) Math.round(cooldownTicks() * state.cooldownMultiplier()));
    }

    private static String seconds(int ticks) {
        return String.format("%.1f", ticks / 20.0);
    }
}
