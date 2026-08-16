package kim.biryeong.semiontd.tower.army;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;

/**
 * Tower types for the 군대 builder.
 *
 * <p>The family runs on two independent axes. Tier is bought with minerals and sets the base stats;
 * rank is earned by participating in waves and decides how much of that base the tower still fires. See
 * {@link ArmyRank}.
 *
 * <p>Listed damage is the fresh-rank value. T2 and T3 combat towers are intentionally weighted
 * toward the middle and late game, while the rank curve keeps aging cohorts active until discharge.
 *
 * <p>Three visual groups keep the roles readable: 본부 is a wandering trader (an obvious
 * non-combatant), 경계 is a tuff golem (a sentry that stands still by default), and only 전투 uses
 * armour-rendering humanoids, because 전투 is the only line whose rank has to be visible.
 */
public final class ArmyTowers {
    private static final String TRADER = "minecraft:wandering_trader";
    /**
     * Vanilla on purpose.
     *
     * <p>{@code friendsandfoes:tuff_golem} looked ideal — a sentry that stands still holding an
     * item — but the server logs {@code Couldn't create template entity} for it and the tower
     * renders as nothing at all. Modded entity ids reach vanilla clients through a Polymer patch
     * that does not cover every type, so this line stays on an entity that is guaranteed to draw.
     */
    private static final String SENTRY = "minecraft:iron_golem";
    private static final String RIFLE = "minecraft:pillager";
    private static final String ARTILLERY = "minecraft:witch";

    // ------------------------------------------------------------------ 본부 (wandering trader)

    public static final TowerType CLERK = tower(
            "army_clerk_t1", "행정병", 30, 70.0, 0.0, 0.0, 20, -5,
            visual(TRADER, 0.9),
            List.of(
                    "<gray> 공격하지 않고 주변 아군의 복무를 관리하는 비전투 요원입니다. </gray>",
                    "<aqua> 반경 <yellow>{ability.serviceRateRadius:blocks}</yellow> 안의 아군이 웨이브마다 짬을 <yellow>{ability.serviceRateBonus:integer}</yellow> 더 얻습니다. </aqua>",
                    "<gray> 진급이 빨라지면 전성기도 빨리 오고, 전역도 빨리 옵니다. </gray>"
            )
    );

    public static final TowerType DRILL_SERGEANT = tower(
            "army_drill_sergeant_t2", "조교", 75, 130.0, 0.0, 0.0, 20, -5,
            visual(TRADER, 1.05),
            List.of(
                    "<gray> 굴려서 짬을 채우는 쪽에 특화된 요원입니다. </gray>",
                    "<aqua> 반경 <yellow>{ability.serviceRateRadius:blocks}</yellow> 안의 아군이 웨이브마다 짬을 <yellow>{ability.serviceRateBonus:integer}</yellow> 더 얻습니다. </aqua>",
                    "<green> 회전을 빠르게 돌려 <yellow>훈장</yellow>을 많이 모으는 빌드용입니다. </green>",
                    "<red> 전역도 그만큼 빨라지므로 전력 공백에 주의합니다. </red>"
            )
    );

    public static final TowerType QUARTERMASTER = tower(
            "army_quartermaster_t2", "보급관", 75, 130.0, 0.0, 0.0, 20, -5,
            visual(TRADER, 1.05),
            List.of(
                    "<gray> 한 번 내보낼 때 많이 챙기는 쪽에 특화된 요원입니다. </gray>",
                    "<aqua> 반경 <yellow>{ability.serviceRateRadius:blocks}</yellow> 안의 아군이 웨이브마다 짬을 <yellow>{ability.serviceRateBonus:integer}</yellow> 더 얻습니다. </aqua>",
                    "<green> 전역금의 미환급분을 <yellow>{ability.dischargeRefundBonus:percent}</yellow> 회복하고, 훈장 효과가 <yellow>{ability.medalValueBonus:percent}</yellow> 증가합니다. </green>",
                    "<gray> 적게 돌리고 크게 먹는 빌드용입니다. </gray>"
            )
    );

    // ------------------------------------------------------------------ 경계 (tuff golem)

    public static final TowerType GUARD = tower(
            "army_guard_t1", "위병", 50, 230.0, 2.4, 6.0, 20, 55,
            visual(SENTRY, 0.9),
            List.of(
                    "<gray> 몬스터를 자기 쪽으로 끌어당기는 앞라인 타워입니다. </gray>",
                    "<green> 계급이 올라도 공격력이 줄지 않습니다. 대신 후임을 강화하지도 않습니다. </green>",
                    "<gray> 짬 체계 밖에 있어 전력 공백 없이 라인을 지킵니다. </gray>"
            )
    );

    public static final TowerType MILITARY_POLICE = tower(
            "army_military_police_t2", "헌병", 105, 630.0, 2.6, 11.0, 19, 85,
            visual(SENTRY, 1.1),
            List.of(
                    "<gray> 방패를 든 정면 방어 계열입니다. </gray>",
                    "<aqua> 받는 피해의 <yellow>{ability.damageReduction:percent}</yellow>를 흘려보냅니다. </aqua>",
                    "<green> 계급 영향을 받지 않습니다. </green>"
            )
    );

    public static final TowerType MP_COMMANDER = tower(
            "army_mp_commander_t3", "헌병대장", 220, 975.0, 2.8, 20.0, 18, 110,
            visual(SENTRY, 1.3),
            List.of(
                    "<gray> 방패 계열의 최종 형태입니다. 계열 최고의 체력으로 버팁니다. </gray>",
                    "<aqua> 받는 피해의 <yellow>{ability.damageReduction:percent}</yellow>를 흘려보냅니다. </aqua>",
                    "<green> 계급 영향을 받지 않습니다. </green>"
            )
    );

    public static final TowerType GOP_SENTRY = tower(
            "army_gop_sentry_t2", "GOP 초병", 100, 450.0, 2.6, 10.0, 19, 85,
            visual(SENTRY, 1.1),
            List.of(
                    "<gray> 군기를 잡아 주변의 시간을 늦추는 계열입니다. </gray>",
                    "<light_purple> 반경 <yellow>{ability.serviceRateRadius:blocks}</yellow> 안의 아군이 웨이브마다 짬을 <yellow>{ability.serviceRatePenalty:integer}</yellow> 덜 얻습니다. </light_purple>",
                    "<gray> 체력은 헌병보다 낮습니다. </gray>",
                    "<green> 계급 영향을 받지 않습니다. </green>"
            )
    );

    public static final TowerType OUTPOST_CHIEF = tower(
            "army_outpost_chief_t3", "초소장", 215, 810.0, 2.8, 18.0, 18, 110,
            visual(SENTRY, 1.3),
            List.of(
                    "<gray> 군기 계열의 최종 형태입니다. </gray>",
                    "<light_purple> 반경 <yellow>{ability.serviceRateRadius:blocks}</yellow> 안의 아군이 웨이브마다 짬을 <yellow>{ability.serviceRatePenalty:integer}</yellow> 덜 얻습니다. </light_purple>",
                    "<green> 효율이 가장 좋은 <yellow>상병</yellow> 구간에 오래 머무르게 하는 것이 목적입니다. </green>",
                    "<red> 조교와 함께 두면 서로 상쇄되므로 한쪽만 고릅니다. </red>"
            )
    );

    // ------------------------------------------------------------------ 전투 (pillager / witch)

    public static final TowerType RECRUIT = tower(
            "army_recruit_t1", "훈련병", 50, 90.0, 6.0, 9.0, 13, 0,
            visual(RIFLE, 0.9),
            List.of(
                    "<gray> 계급이 오르는 유일한 계열입니다. </gray>",
                    "<aqua> 짬이 찰수록 공격력이 줄고 대신 후임을 강화합니다. </aqua>",
                    "<gray> 이름표에 계급이 표시됩니다. 티어마다 계급 체계가 다릅니다. </gray>"
            )
    );

    public static final TowerType SPECIALIST = tower(
            "army_specialist_t2", "특급전사", 130, 140.0, 6.5, 30.0, 12, 0,
            visual(RIFLE, 1.05),
            List.of(
                    "<gray> 단일 대상 화력을 담당하는 주력입니다. </gray>",
                    "<aqua> 이등병 기준 수치이며 계급이 오르면 공격력이 줄어듭니다. </aqua>"
            )
    );

    public static final TowerType PLATOON_LEADER = tower(
            "army_platoon_leader_t3", "소대장", 280, 190.0, 7.0, 60.0, 11, 0,
            visual(RIFLE, 1.2),
            List.of(
                    "<gray> 단일 계열의 최종 형태입니다. </gray>",
                    "<green> 최종 계급에는 공격력 <yellow>{ability.army_global.staffSergeantAttackMultiplier:percent}</yellow>로 싸우며 반경 <yellow>{ability.army_global.commandRadius:blocks}</yellow> 안의 후임에게 </green>",
                    "<green> 공격력 <yellow>+{ability.army_global.staffSergeantDamageBuff:percent}</yellow>, 공격 속도 <yellow>+{ability.army_global.staffSergeantAttackSpeedBuff:percent}</yellow>를 줍니다. </green>",
                    "<red> 후임이 2기 미만이면 손해입니다. </red>"
            )
    );

    public static final TowerType GUNNER = tower(
            "army_gunner_t2", "포병", 130, 110.0, 7.5, 30.0, 16, 0,
            visual(ARTILLERY, 1.0),
            List.of(
                    "<gray> 사거리가 길고 느린 광역 계열입니다. </gray>",
                    "<light_purple> 공격이 반경 <yellow>{ability.splashRadius:blocks}</yellow> 안의 적에게 <yellow>{ability.splashDamageRatio:percent}</yellow>로 퍼집니다. </light_purple>"
            )
    );

    public static final TowerType BATTERY_CHIEF = tower(
            "army_battery_chief_t3", "포대장", 280, 150.0, 8.5, 56.0, 15, 0,
            visual(ARTILLERY, 1.2),
            List.of(
                    "<gray> 광역 계열의 최종 형태입니다. 라인 전체를 덮습니다. </gray>",
                    "<light_purple> 공격이 반경 <yellow>{ability.splashRadius:blocks}</yellow> 안의 적에게 <yellow>{ability.splashDamageRatio:percent}</yellow>로 퍼집니다. </light_purple>",
                    "<green> 최종 계급에는 공격력 <yellow>{ability.army_global.staffSergeantAttackMultiplier:percent}</yellow>로 싸우며 후임을 강화합니다. </green>"
            )
    );

    // ------------------------------------------------------------------ 분류

    private static final Set<String> HQ_IDS = ids(CLERK, DRILL_SERGEANT, QUARTERMASTER);
    private static final Set<String> SENTRY_IDS =
            ids(GUARD, MILITARY_POLICE, MP_COMMANDER, GOP_SENTRY, OUTPOST_CHIEF);
    private static final Set<String> COMBAT_IDS =
            ids(RECRUIT, SPECIALIST, PLATOON_LEADER, GUNNER, BATTERY_CHIEF);
    private static final Set<String> ARTILLERY_IDS = ids(GUNNER, BATTERY_CHIEF);
    private static final Set<String> NCO_IDS = ids(SPECIALIST, GUNNER);
    private static final Set<String> OFFICER_IDS = ids(PLATOON_LEADER, BATTERY_CHIEF);

    private static final List<TowerType> ALL = List.of(
            CLERK, DRILL_SERGEANT, QUARTERMASTER,
            GUARD, MILITARY_POLICE, MP_COMMANDER, GOP_SENTRY, OUTPOST_CHIEF,
            RECRUIT, SPECIALIST, PLATOON_LEADER, GUNNER, BATTERY_CHIEF
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private ArmyTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isArmyTower(TowerType type) {
        return type != null
                && (HQ_IDS.contains(type.id()) || SENTRY_IDS.contains(type.id()) || COMBAT_IDS.contains(type.id()));
    }

    /** Combat line: the only towers that gain rank. */
    public static boolean isCombat(TowerType type) {
        return type != null && COMBAT_IDS.contains(type.id());
    }

    public static boolean isArtillery(TowerType type) {
        return type != null && ARTILLERY_IDS.contains(type.id());
    }

    /** Whether this tower's damage is scaled by its rank at all. */
    public static boolean ranks(TowerType type) {
        return isCombat(type);
    }

    /** Which rank ladder a tower climbs. Tier picks the ladder, service climbs it. */
    public static ArmyRankTrack trackOf(TowerType type) {
        if (type == null) {
            return ArmyRankTrack.ENLISTED;
        }
        if (OFFICER_IDS.contains(type.id())) {
            return ArmyRankTrack.OFFICER;
        }
        if (NCO_IDS.contains(type.id())) {
            return ArmyRankTrack.NCO;
        }
        return ArmyRankTrack.ENLISTED;
    }

    private static EntityVisual visual(String entityTypeId, double scale) {
        return EntityVisual.builder(entityTypeId).scale(scale).build();
    }

    private static Set<String> ids(TowerType... types) {
        return Arrays.stream(types).map(TowerType::id).collect(Collectors.toUnmodifiableSet());
    }
}
