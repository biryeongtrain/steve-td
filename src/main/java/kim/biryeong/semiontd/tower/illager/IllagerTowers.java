package kim.biryeong.semiontd.tower.illager;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;

public final class IllagerTowers {
    public static final TowerType T1_VINDICATOR = tower(
            "illager_vindicator_t1",
            "변명자타워",
            90,
            130,
            2.2,
            7,
            18,
            45,
            byId(EntityType.VINDICATOR),
            List.of(
                    "<gray>우민 빌더의 기본 탱커 타워입니다.</gray>",
                    "<green>습격 중 받는 피해가 감소합니다.</green>"
            )
    );

    public static final TowerType T2_VINDICATOR_CAPTAIN = tower(
            "illager_vindicator_captain_t2",
            "변명자 대장타워",
            170,
            190,
            2.4,
            10,
            17,
            55,
            byId(EntityType.VINDICATOR),
            List.of(
                    "<gray>현수막을 든 변명자 대장입니다.</gray>",
                    "<green>습격 중 받는 피해가 더 크게 감소합니다.</green>"
            )
    );

    public static final TowerType T3_RAVAGER = tower(
            "illager_ravager_t3",
            "파괴수타워",
            330,
            330,
            2.8,
            16,
            22,
            70,
            byId(EntityType.RAVAGER),
            List.of(
                    "<gray>라인을 버티는 최종 탱커 타워입니다.</gray>",
                    "<green>습격 중 광역 충돌 피해를 가합니다.</green>"
            )
    );

    public static final TowerType T1_PILLAGER = tower(
            "illager_pillager_t1",
            "약탈자타워",
            75,
            55,
            6.5,
            6,
            14,
            12,
            byId(EntityType.PILLAGER),
            List.of(
                    "<gray>우민 빌더의 기본 원거리 타워입니다.</gray>",
                    "<green>단일 또는 광역 대장 트리로 업그레이드할 수 있습니다.</green>"
            )
    );

    public static final TowerType T2_PILLAGER_CAPTAIN_SINGLE = tower(
            "illager_pillager_captain_single_t2",
            "약탈자 대장타워(단일)",
            160,
            75,
            7.0,
            11,
            13,
            15,
            byId(EntityType.PILLAGER),
            List.of(
                    "<gray>인컴/소환 적 처리에 특화된 약탈자 대장입니다.</gray>",
                    "<green>습격 중 특수 적에게 추가 피해를 입힙니다.</green>"
            )
    );

    public static final TowerType T2_PILLAGER_CAPTAIN_SPLASH = tower(
            "illager_pillager_captain_splash_t2",
            "약탈자 대장타워(광역)",
            155,
            70,
            6.5,
            8,
            15,
            12,
            byId(EntityType.PILLAGER),
            List.of(
                    "<gray>웨이브 정리에 특화된 약탈자 대장입니다.</gray>",
                    "<green>공격 대상 주변에 스플래시 피해를 입힙니다.</green>"
            )
    );

    public static final TowerType T3_EVOKER_SINGLE = tower(
            "illager_evoker_single_t3",
            "소환사타워(단일)",
            310,
            105,
            7.5,
            18,
            12,
            18,
            byId(EntityType.EVOKER),
            List.of(
                    "<gray>특수 적을 끊어내는 단일 소환사 타워입니다.</gray>",
                    "<green>습격 중 표식 대상과 특수 적에게 큰 피해를 입힙니다.</green>"
            )
    );

    public static final TowerType T3_EVOKER_SPLASH = tower(
            "illager_evoker_splash_t3",
            "소환사타워(광역)",
            300,
            100,
            7.0,
            13,
            14,
            15,
            byId(EntityType.EVOKER),
            List.of(
                    "<gray>웨이브를 정리하는 광역 소환사 타워입니다.</gray>",
                    "<green>습격 중 더 넓은 스플래시 피해를 입힙니다.</green>"
            )
    );

    public static final TowerType T1_VEX = tower(
            "illager_vex_t1",
            "벡스타워",
            70,
            45,
            5.5,
            4,
            12,
            8,
            byId(EntityType.VEX),
            List.of(
                    "<gray>약한 표식을 생성하는 보조 타워입니다.</gray>",
                    "<green>표식 대상은 받는 피해가 증가합니다.</green>"
            )
    );

    public static final TowerType T2_WITCH_LOW = tower(
            "illager_witch_low_t2",
            "마녀타워(약자 표식)",
            150,
            65,
            6.0,
            5,
            14,
            10,
            byId(EntityType.WITCH),
            List.of(
                    "<gray>체력이 가장 낮은 적에게 표식을 생성합니다.</gray>",
                    "<green>주변 우민 타워가 표식 대상을 우선 공격합니다.</green>"
            )
    );

    public static final TowerType T2_WITCH_HIGH = tower(
            "illager_witch_high_t2",
            "마녀타워(강자 표식)",
            150,
            65,
            6.0,
            5,
            14,
            10,
            byId(EntityType.WITCH),
            List.of(
                    "<gray>체력이 가장 높은 적에게 표식을 생성합니다.</gray>",
                    "<green>주변 우민 타워가 표식 대상을 우선 공격합니다.</green>"
            )
    );

    public static final TowerType T3_ILLUSIONER_LOW = tower(
            "illager_illusioner_low_t3",
            "환술사타워(약자 표식)",
            280,
            85,
            6.5,
            9,
            12,
            12,
            byId(EntityType.ILLUSIONER),
            List.of(
                    "<gray>낮은 체력 대상을 강하게 표식하는 환술사 타워입니다.</gray>",
                    "<green>습격 중 표식 피해 증폭이 증가합니다.</green>"
            )
    );

    public static final TowerType T3_ILLUSIONER_HIGH = tower(
            "illager_illusioner_high_t3",
            "환술사타워(강자 표식)",
            280,
            85,
            6.5,
            9,
            12,
            12,
            byId(EntityType.ILLUSIONER),
            List.of(
                    "<gray>높은 체력 대상을 강하게 표식하는 환술사 타워입니다.</gray>",
                    "<green>습격 중 표식 피해 증폭이 증가합니다.</green>"
            )
    );

    private static final Set<String> ILLAGER_TOWER_IDS = Set.of(
            T1_VINDICATOR.id(),
            T2_VINDICATOR_CAPTAIN.id(),
            T3_RAVAGER.id(),
            T1_PILLAGER.id(),
            T2_PILLAGER_CAPTAIN_SINGLE.id(),
            T2_PILLAGER_CAPTAIN_SPLASH.id(),
            T3_EVOKER_SINGLE.id(),
            T3_EVOKER_SPLASH.id(),
            T1_VEX.id(),
            T2_WITCH_LOW.id(),
            T2_WITCH_HIGH.id(),
            T3_ILLUSIONER_LOW.id(),
            T3_ILLUSIONER_HIGH.id()
    );

    private static final Set<String> CAPTAIN_TOWER_IDS = Set.of(
            T2_VINDICATOR_CAPTAIN.id(),
            T2_PILLAGER_CAPTAIN_SINGLE.id(),
            T2_PILLAGER_CAPTAIN_SPLASH.id()
    );

    static {
        registerDescriptions();
    }

    private IllagerTowers() {
    }

    public static boolean isIllagerTower(TowerType type) {
        return type != null && ILLAGER_TOWER_IDS.contains(type.id());
    }

    public static boolean isCaptainTower(TowerType type) {
        return type != null && CAPTAIN_TOWER_IDS.contains(type.id());
    }

    private static void registerDescriptions() {
        TowerDescriptionRegistry.registerTemplate(T1_VINDICATOR, List.of(
                "<gray>우민 빌더의 기본 탱커 타워입니다. 짧은 사거리와 높은 어그로로 앞라인을 버팁니다.</gray>",
                commonRaidLine(),
                "<green>습격 발동 중 추가로 받는 피해가 {ability.raidDamageReduction:percent} 감소합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_VINDICATOR_CAPTAIN, List.of(
                "<gray>현수막을 든 변명자 대장입니다. 변명자보다 더 높은 체력과 어그로를 가집니다.</gray>",
                commonRaidLine(),
                "<green>습격 발동 중 추가로 받는 피해가 {ability.raidDamageReduction:percent} 감소합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_RAVAGER, List.of(
                "<gray>라인을 버티는 최종 탱커 타워입니다. 공격 대상 주변에도 피해를 줍니다.</gray>",
                commonRaidLine(),
                "<green>기본 공격 시 대상 주변 {ability.splashRadius:blocks}에 피해량의 {ability.splashDamageRatio:percent}를 스플래시 피해로 줍니다.</green>",
                "<green>습격 발동 중 추가로 받는 피해가 {ability.raidDamageReduction:percent} 감소하고, 스플래시 반경이 {ability.raidSplashRadiusBonus:blocks}, 스플래시 피해 비율이 {ability.raidSplashDamageRatioBonus:percent} 증가합니다.</green>"
        ));

        TowerDescriptionRegistry.registerTemplate(T1_PILLAGER, List.of(
                "<gray>우민 빌더의 기본 원거리 타워입니다. 단일 처리 또는 광역 처리 대장 트리로 업그레이드할 수 있습니다.</gray>",
                commonRaidLine(),
                "<green>습격 발동 중 추가로 표식 대상에게 {ability.raidMarkedDamageBonus:percent} 피해를 줍니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_PILLAGER_CAPTAIN_SINGLE, List.of(
                "<gray>인컴/소환 적 처리에 특화된 단일 약탈자 대장입니다.</gray>",
                commonRaidLine(),
                "<green>인컴/소환 적에게 기본으로 {ability.incomeDamageBonus:percent} 추가 피해를 줍니다.</green>",
                "<green>습격 발동 중 추가로 인컴/소환 적에게 {ability.raidIncomeDamageBonus:percent}, 표식 대상에게 {ability.raidMarkedDamageBonus:percent} 피해를 줍니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_PILLAGER_CAPTAIN_SPLASH, List.of(
                "<gray>웨이브 정리에 특화된 광역 약탈자 대장입니다.</gray>",
                commonRaidLine(),
                "<green>기본 공격 시 대상 주변 {ability.splashRadius:blocks}에 피해량의 {ability.splashDamageRatio:percent}를 스플래시 피해로 줍니다.</green>",
                "<green>습격 발동 중 추가로 스플래시 반경이 {ability.raidSplashRadiusBonus:blocks}, 스플래시 피해 비율이 {ability.raidSplashDamageRatioBonus:percent} 증가합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_EVOKER_SINGLE, List.of(
                "<gray>특수 적을 끊어내는 단일 소환사 타워입니다. 공격한 대상에게 표식도 남깁니다.</gray>",
                commonRaidLine(),
                "<green>인컴/소환 적에게 기본으로 {ability.incomeDamageBonus:percent} 추가 피해를 주고, 표식 대상은 {ability.markDamageTakenBonus:percent} 더 큰 피해를 {ability.markDurationTicks:seconds} 동안 받습니다.</green>",
                "<green>습격 발동 중 추가로 인컴/소환 적에게 {ability.raidIncomeDamageBonus:percent}, 표식 대상에게 {ability.raidMarkedDamageBonus:percent} 피해를 주며 표식 시간이 {ability.raidMarkDurationBonusTicks:seconds} 증가합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_EVOKER_SPLASH, List.of(
                "<gray>웨이브를 정리하는 광역 소환사 타워입니다.</gray>",
                commonRaidLine(),
                "<green>기본 공격 시 대상 주변 {ability.splashRadius:blocks}에 피해량의 {ability.splashDamageRatio:percent}를 스플래시 피해로 줍니다.</green>",
                "<green>습격 발동 중 추가로 스플래시 반경이 {ability.raidSplashRadiusBonus:blocks}, 스플래시 피해 비율이 {ability.raidSplashDamageRatioBonus:percent} 증가합니다.</green>"
        ));

        TowerDescriptionRegistry.registerTemplate(T1_VEX, List.of(
                "<gray>표식을 생성하는 기본 보조 타워입니다. 표식 대상은 받는 피해가 증가합니다.</gray>",
                commonRaidLine(),
                "<green>공격한 대상에게 {ability.markDurationTicks:seconds} 동안 받는 피해 {ability.markDamageTakenBonus:percent} 증가 표식을 남깁니다.</green>",
                "<green>습격 발동 중 추가로 표식의 받는 피해 증가량이 {ability.raidMarkDamageTakenBonus:percent}, 지속시간이 {ability.raidMarkDurationBonusTicks:seconds} 증가합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_WITCH_LOW, List.of(
                "<gray>현재 체력이 가장 낮은 적에게 표식을 생성하는 마녀 타워입니다.</gray>",
                commonRaidLine(),
                "<green>표식 대상은 {ability.markDurationTicks:seconds} 동안 받는 피해가 {ability.markDamageTakenBonus:percent} 증가하고, 이 타워 주변 {ability.forceTargetRadius:blocks} 안의 우민 타워가 해당 적을 우선 공격합니다.</green>",
                "<green>습격 발동 중 추가로 표식 피해 증가량이 {ability.raidMarkDamageTakenBonus:percent} + 약자 표식 보너스 {ability.raidLowHealthMarkDamageTakenBonus:percent}, 지속시간이 {ability.raidMarkDurationBonusTicks:seconds}, 강제 타게팅 반경이 {ability.raidForceTargetRadiusBonus:blocks} 증가합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_WITCH_HIGH, List.of(
                "<gray>현재 체력이 가장 높은 적에게 표식을 생성하는 마녀 타워입니다.</gray>",
                commonRaidLine(),
                "<green>표식 대상은 {ability.markDurationTicks:seconds} 동안 받는 피해가 {ability.markDamageTakenBonus:percent} 증가하고, 이 타워 주변 {ability.forceTargetRadius:blocks} 안의 우민 타워가 해당 적을 우선 공격합니다.</green>",
                "<green>습격 발동 중 추가로 표식 피해 증가량이 {ability.raidMarkDamageTakenBonus:percent} + 강자 표식 보너스 {ability.raidHighHealthMarkDamageTakenBonus:percent}, 지속시간이 {ability.raidMarkDurationBonusTicks:seconds}, 강제 타게팅 반경이 {ability.raidForceTargetRadiusBonus:blocks} 증가합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_ILLUSIONER_LOW, List.of(
                "<gray>현재 체력이 가장 낮은 적을 더 강하게 표식하는 환술사 타워입니다.</gray>",
                commonRaidLine(),
                "<green>표식 대상은 {ability.markDurationTicks:seconds} 동안 받는 피해가 {ability.markDamageTakenBonus:percent} 증가하고, 주변 {ability.forceTargetRadius:blocks} 안의 우민 타워가 해당 적을 우선 공격합니다.</green>",
                "<green>습격 발동 중 추가로 표식 피해 증가량이 {ability.raidMarkDamageTakenBonus:percent} + 약자 표식 보너스 {ability.raidLowHealthMarkDamageTakenBonus:percent}, 지속시간이 {ability.raidMarkDurationBonusTicks:seconds}, 강제 타게팅 반경이 {ability.raidForceTargetRadiusBonus:blocks} 증가합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_ILLUSIONER_HIGH, List.of(
                "<gray>현재 체력이 가장 높은 적을 더 강하게 표식하는 환술사 타워입니다.</gray>",
                commonRaidLine(),
                "<green>표식 대상은 {ability.markDurationTicks:seconds} 동안 받는 피해가 {ability.markDamageTakenBonus:percent} 증가하고, 주변 {ability.forceTargetRadius:blocks} 안의 우민 타워가 해당 적을 우선 공격합니다.</green>",
                "<green>습격 발동 중 추가로 표식 피해 증가량이 {ability.raidMarkDamageTakenBonus:percent} + 강자 표식 보너스 {ability.raidHighHealthMarkDamageTakenBonus:percent}, 지속시간이 {ability.raidMarkDurationBonusTicks:seconds}, 강제 타게팅 반경이 {ability.raidForceTargetRadiusBonus:blocks} 증가합니다.</green>"
        ));
    }

    private static String commonRaidLine() {
        return "<green>습격 공통: 라운드 시작 시 살아있던 우민 타워 1기당 공격속도 {ability.illager_raid.attackSpeedPercentPerTower:percent}, 공격력 {ability.illager_raid.damagePercentPerTower:percent} 증가.</green>";
    }
}
