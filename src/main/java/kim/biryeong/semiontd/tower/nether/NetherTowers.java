package kim.biryeong.semiontd.tower.nether;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;

public final class NetherTowers {
    public static final TowerType T1_STRIDER = tower(
            "nether_strider_t1",
            "스트라이더",
            35,
            70.0,
            3.5,
            5.0,
            10,
            8,
            byId(net.minecraft.world.entity.EntityTypes.STRIDER),
            List.of(
                    "<gray>빠르게 공격하며 흡혈로 체력 소모를 줄입니다.</gray>",
                    "<green>임계 체력에서 적중 시 체력 감소가 줄어듭니다.</green>"
            )
    );

    public static final TowerType T2_PIGLIN = tower(
            "nether_piglin_t2",
            "피글린",
            110,
            105.0,
            5.5,
            13.0,
            12,
            12,
            byId(net.minecraft.world.entity.EntityTypes.PIGLIN),
            List.of(
                    "<gray>인컴 몬스터에게 추가 피해를 줍니다.</gray>",
                    "<green>처치 시 짧게 공격력이 증가합니다.</green>"
            )
    );

    public static final TowerType T3_PIGLIN_BRUTE = tower(
            "nether_piglin_brute_t3",
            "피글린 야수",
            260,
            190.0,
            3.2,
            26.0,
            14,
            35,
            byId(net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE),
            List.of(
                    "<gray>높은 단일 피해와 흡혈을 가진 근접 캐리입니다.</gray>",
                    "<green>임계 체력에서 강한 대상에게 피해와 흡혈이 증가합니다.</green>"
            )
    );

    public static final TowerType T1_HOGLIN = tower(
            "nether_hoglin_t1",
            "호글린",
            50,
            180.0,
            2.4,
            8.0,
            18,
            45,
            byId(net.minecraft.world.entity.EntityTypes.HOGLIN),
            List.of(
                    "<gray>전방에서 버티는 근접 광역 타워입니다.</gray>",
                    "<green>여러 적을 맞히면 흡혈 효율이 올라갑니다.</green>"
            )
    );

    public static final TowerType T2_ZOGLIN = tower(
            "nether_zoglin_t2",
            "조글린",
            125,
            260.0,
            2.6,
            16.0,
            16,
            55,
            byId(net.minecraft.world.entity.EntityTypes.ZOGLIN),
            List.of(
                    "<gray>범위 피해가 늘어난 전방 타워입니다.</gray>",
                    "<green>체력이 낮을수록 공격 속도가 증가합니다.</green>"
            )
    );

    public static final TowerType T3_ZOMBIFIED_PIGLIN = tower(
            "nether_zombified_piglin_t3",
            "좀비화 피글린",
            280,
            330.0,
            2.8,
            24.0,
            13,
            60,
            byId(net.minecraft.world.entity.EntityTypes.ZOMBIFIED_PIGLIN),
            List.of(
                    "<gray>좀비 상태에서 빠른 광역 공격으로 마무리합니다.</gray>",
                    "<green>네더 상태 사망 시 공격 준비가 초기화됩니다.</green>"
            )
    );

    public static final TowerType T1_MAGMA_CUBE = tower(
            "nether_magma_cube_t1",
            "마그마 큐브",
            40,
            150.0,
            4.5,
            7.0,
            20,
            25,
            byId(net.minecraft.world.entity.EntityTypes.MAGMA_CUBE),
            List.of(
                    "<gray>느린 폭발 공격을 가합니다.</gray>",
                    "<green>임계 체력에서 주변에 주기 피해를 줍니다.</green>"
            )
    );

    public static final TowerType T2_BLAZE = tower(
            "nether_blaze_t2",
            "블레이즈",
            110,
            105.0,
            7.0,
            15.0,
            12,
            8,
            byId(net.minecraft.world.entity.EntityTypes.BLAZE),
            List.of(
                    "<gray>중거리 투사체로 웨이브를 정리합니다.</gray>",
                    "<green>임계 체력에서 일정 공격마다 추가 공격합니다.</green>"
            )
    );

    public static final TowerType T3_GHAST = tower(
            "nether_ghast_t3",
            "가스트",
            270,
            125.0,
            9.5,
            34.0,
            24,
            5,
            EntityVisual.builder(byId(net.minecraft.world.entity.EntityTypes.GHAST)).scale(0.45).build(),
            List.of(
                    "<gray>긴 사거리와 큰 폭발 범위를 가집니다.</gray>",
                    "<green>임계 체력에서 대상이 받는 타워 피해를 증가시킵니다.</green>"
            )
    );

    public static final TowerType T1_SKELETON = tower(
            "nether_skeleton_t1",
            "스켈레톤",
            35,
            65.0,
            7.0,
            8.0,
            14,
            5,
            byId(net.minecraft.world.entity.EntityTypes.SKELETON),
            List.of(
                    "<gray>긴 사거리로 낮은 체력 대상을 끊습니다.</gray>",
                    "<green>임계 체력에서 처치 흡혈이 증가합니다.</green>"
            )
    );

    public static final TowerType T2_WITHER_SKELETON = tower(
            "nether_wither_skeleton_t2",
            "위더 스켈레톤",
            115,
            135.0,
            3.0,
            18.0,
            14,
            30,
            byId(net.minecraft.world.entity.EntityTypes.WITHER_SKELETON),
            List.of(
                    "<gray>대상에게 받는 피해 증가 표식을 남깁니다.</gray>",
                    "<green>표식 대상 처치 시 자신을 회복합니다.</green>"
            )
    );

    public static final TowerType T3_WITHER = tower(
            "nether_wither_t3",
            "위더",
            280,
            220.0,
            7.5,
            36.0,
            24,
            40,
            EntityVisual.builder(byId(net.minecraft.world.entity.EntityTypes.WITHER)).scale(0.55).build(),
            List.of(
                    "<gray>높은 체력의 몬스터에게 강한 피해를 줍니다.</gray>",
                    "<green>임계 체력에서 폭발 피해와 표식을 함께 적용합니다.</green>"
            )
    );

    private static final Set<String> NETHER_TOWER_IDS = Set.of(
            T1_STRIDER.id(),
            T2_PIGLIN.id(),
            T3_PIGLIN_BRUTE.id(),
            T1_HOGLIN.id(),
            T2_ZOGLIN.id(),
            T3_ZOMBIFIED_PIGLIN.id(),
            T1_MAGMA_CUBE.id(),
            T2_BLAZE.id(),
            T3_GHAST.id(),
            T1_SKELETON.id(),
            T2_WITHER_SKELETON.id(),
            T3_WITHER.id()
    );
    private static final Set<String> STRIDER_LINE_IDS = Set.of(
            T1_STRIDER.id(), T2_PIGLIN.id(), T3_PIGLIN_BRUTE.id()
    );
    private static final Set<String> HOGLIN_LINE_IDS = Set.of(
            T1_HOGLIN.id(), T2_ZOGLIN.id(), T3_ZOMBIFIED_PIGLIN.id()
    );
    private static final Set<String> BLAZE_LINE_IDS = Set.of(
            T1_MAGMA_CUBE.id(), T2_BLAZE.id(), T3_GHAST.id()
    );
    private static final Set<String> SKELETON_LINE_IDS = Set.of(
            T1_SKELETON.id(), T2_WITHER_SKELETON.id(), T3_WITHER.id()
    );

    static {
        TowerDescriptionRegistry.registerTemplate(T1_STRIDER, List.of(
                "<gray>공격 간격이 짧고 가격이 낮은 근접 타워입니다.</gray>",
                "<green>추가 {ability.lifeStealBonus:percent} 흡혈을 가집니다.</green>",
                "<yellow>체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 적을 공격하면 {ability.decayReductionTicks:seconds} 동안 상태로 인한 체력 감소량이 {ability.decayReductionRatio:percent} 줄어듭니다.</yellow>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_PIGLIN, List.of(
                "<gray>중거리에서 단일 대상을 공격하며 인컴 몬스터를 처리합니다.</gray>",
                "<green>추가 {ability.lifeStealBonus:percent} 흡혈을 가지며, 체력 감소량을 {ability.decayReductionRatio:percent} 줄이는 효과가 {ability.decayReductionTicks:seconds} 동안 유지됩니다.</green>",
                "<green>인컴 몬스터에게 {ability.incomeDamageBonus:percent} 추가 피해를 줍니다. 적을 처치하면 {ability.killDamageBonusTicks:seconds} 동안 공격력이 {ability.killDamageBonus:percent} 증가합니다.</green>",
                "<red>[좀비] 공격 속도가 {ability.zombieAttackSpeedBonus:percent} 증가합니다.</red>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_PIGLIN_BRUTE, List.of(
                "<gray>높은 단일 피해와 흡혈로 강한 몬스터를 상대하는 근접 타워입니다.</gray>",
                "<green>추가 {ability.lifeStealBonus:percent} 흡혈을 가지며, 체력 감소량을 {ability.decayReductionRatio:percent} 줄이는 효과가 {ability.decayReductionTicks:seconds} 동안 유지됩니다.</green>",
                "<green>인컴 몬스터에게 {ability.incomeDamageBonus:percent} 추가 피해를 줍니다. 적을 처치하면 {ability.killDamageBonusTicks:seconds} 동안 공격력이 {ability.killDamageBonus:percent} 증가합니다.</green>",
                "<yellow>체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 탱커 또는 최대 체력 {ability.highHealthThreshold:integer} 이상의 적에게 {ability.tankDamageBonus:percent} 추가 피해를 주고 흡혈이 {ability.tankLifeStealBonus:percent} 증가합니다.</yellow>",
                "<red>[좀비] 공격 속도가 {ability.zombieAttackSpeedBonus:percent} 증가합니다. 전환 후 {ability.zombieTransitionDamageBonusTicks:seconds} 동안 공격력이 {ability.zombieTransitionDamageBonus:percent} 증가합니다.</red>"
        ));

        TowerDescriptionRegistry.registerTemplate(T1_HOGLIN, List.of(
                "<gray>전방에서 여러 적을 상대하는 근접 광역 타워입니다.</gray>",
                "<green>공격 대상 주변 {ability.splashRadius:blocks}에 주 대상 피해의 {ability.splashDamageRatio:percent}를 줍니다. 광역 피해로 맞힌 적마다 흡혈이 적용됩니다.</green>",
                "<yellow>체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 받는 피해가 {ability.criticalDamageReduction:percent} 감소합니다.</yellow>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_ZOGLIN, List.of(
                "<gray>넓은 범위를 공격하며 낮은 체력에서 공격 속도가 오르는 전방 타워입니다.</gray>",
                "<green>공격 대상 주변 {ability.splashRadius:blocks}에 주 대상 피해의 {ability.splashDamageRatio:percent}를 줍니다. 광역 피해로 맞힌 적마다 흡혈이 적용됩니다.</green>",
                "<yellow>잃은 체력에 비례해 공격 속도가 증가합니다. 최대 증가량은 {ability.missingHealthAttackSpeedBonusCap:percent}이며, 체력이 낮을 때 받는 피해가 {ability.criticalDamageReduction:percent} 감소합니다.</yellow>",
                "<red>[좀비] 광역 공격 반경이 {ability.zombieSplashRadiusBonus:blocks} 늘어납니다.</red>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_ZOMBIFIED_PIGLIN, List.of(
                "<gray>좀비 상태에서 빠른 광역 공격으로 전선을 정리하는 근접 타워입니다.</gray>",
                "<green>공격 대상 주변 {ability.splashRadius:blocks}에 주 대상 피해의 {ability.splashDamageRatio:percent}를 줍니다. 광역 피해로 맞힌 적마다 흡혈이 적용됩니다.</green>",
                "<yellow>잃은 체력에 비례해 공격 속도가 최대 {ability.missingHealthAttackSpeedBonusCap:percent} 증가하며, 체력이 낮을 때 받는 피해가 {ability.criticalDamageReduction:percent} 감소합니다.</yellow>",
                "<red>[좀비] 전환 즉시 공격 대기시간이 초기화됩니다. 공격 속도 증가 상한은 {ability.zombieMissingHealthAttackSpeedBonusCap:percent}로 오르고 광역 공격 반경이 {ability.zombieSplashRadiusBonus:blocks} 늘어납니다.</red>"
        ));

        TowerDescriptionRegistry.registerTemplate(T1_MAGMA_CUBE, List.of(
                "<gray>광역 공격과 주기 폭발로 적 무리를 상대합니다.</gray>",
                "<green>공격 대상 주변 {ability.splashRadius:blocks}에 주 대상 피해의 {ability.splashDamageRatio:percent}를 줍니다.</green>",
                "<yellow>잃은 체력에 비례해 공격 속도가 최대 {ability.missingHealthAttackSpeedBonusCap:percent} 증가합니다. 체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 {ability.pulseIntervalTicks:seconds}마다 공격 대상 주변 {ability.pulseRadius:blocks}에 기본 공격력의 {ability.pulseDamageRatio:percent} 마법 피해를 줍니다.</yellow>",
                "<red>[좀비] 전환 시 주변 {ability.zombieTransitionPulseRadius:blocks}에 기본 공격력의 {ability.zombieTransitionPulseDamageRatio:percent} 마법 피해를 한 번 줍니다.</red>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_BLAZE, List.of(
                "<gray>중거리에서 광역 공격과 추가 공격을 함께 사용합니다.</gray>",
                "<green>공격 대상 주변 {ability.splashRadius:blocks}에 주 대상 피해의 {ability.splashDamageRatio:percent}를 줍니다.</green>",
                "<yellow>잃은 체력에 비례해 공격 속도가 최대 {ability.missingHealthAttackSpeedBonusCap:percent} 증가합니다. 체력이 낮을 때 {ability.pulseIntervalTicks:seconds}마다 공격 대상 주변에 기본 공격력의 {ability.pulseDamageRatio:percent} 마법 피해를 줍니다.</yellow>",
                "<green>{ability.extraAttackEvery:integer}번째 공격마다 {ability.secondaryRange:blocks} 안의 다른 적에게 주 대상 피해의 {ability.extraAttackDamageRatio:percent}만큼 마법 피해를 추가로 줍니다. 다른 적이 없으면 같은 대상을 다시 공격합니다.</green>",
                "<red>[좀비] 전환 시 주변 {ability.zombieTransitionPulseRadius:blocks}에 기본 공격력의 {ability.zombieTransitionPulseDamageRatio:percent} 마법 피해를 한 번 줍니다.</red>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_GHAST, List.of(
                "<gray>긴 사거리에서 넓은 범위를 공격하는 광역 타워입니다.</gray>",
                "<green>공격 대상 주변 {ability.splashRadius:blocks}에 주 대상 피해의 {ability.splashDamageRatio:percent}를 줍니다. 체력이 낮아지면 반경이 {ability.lowHealthSplashRadiusBonus:blocks} 늘어납니다.</green>",
                "<yellow>잃은 체력에 비례해 공격 속도가 최대 {ability.missingHealthAttackSpeedBonusCap:percent} 증가합니다. 체력이 낮을 때 {ability.pulseIntervalTicks:seconds}마다 공격 대상 주변에 기본 공격력의 {ability.pulseDamageRatio:percent} 마법 피해를 줍니다.</yellow>",
                "<green>{ability.extraAttackEvery:integer}번째 공격마다 다른 적에게 주 대상 피해의 {ability.extraAttackDamageRatio:percent}만큼 마법 피해를 추가로 줍니다.</green>",
                "<yellow>체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 공격한 대상에게 {ability.markDurationTicks:seconds} 동안 받는 타워 피해 {ability.criticalMarkDamageTakenBonus:percent} 증가 표식을 남깁니다.</yellow>",
                "<red>[좀비] 전환 시 주변 {ability.zombieTransitionPulseRadius:blocks}에 기본 공격력의 {ability.zombieTransitionPulseDamageRatio:percent} 마법 피해를 한 번 줍니다.</red>"
        ));

        TowerDescriptionRegistry.registerTemplate(T1_SKELETON, List.of(
                "<gray>사거리 안에서 현재 체력 비율이 가장 낮은 적을 먼저 공격합니다.</gray>",
                "<green>체력이 {ability.lowTargetHealthThreshold:percent} 이하인 적에게 {ability.lowTargetDamageBonus:percent} 추가 피해를 줍니다.</green>",
                "<yellow>자신의 체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 적을 처치하면 준 피해의 {ability.criticalKillLifeStealRatio:percent}만큼 추가로 회복합니다.</yellow>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_WITHER_SKELETON, List.of(
                "<gray>현재 체력 비율이 가장 낮은 적을 노리고 받는 타워 피해 증가 표식을 쌓습니다.</gray>",
                "<green>체력이 {ability.lowTargetHealthThreshold:percent} 이하인 적에게 {ability.lowTargetDamageBonus:percent} 추가 피해를 줍니다.</green>",
                "<yellow>자신의 체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 적을 처치하면 준 피해의 {ability.criticalKillLifeStealRatio:percent}만큼 추가로 회복합니다.</yellow>",
                "<green>공격한 대상에게 {ability.markDurationTicks:seconds} 동안 받는 타워 피해 {ability.markDamageTakenBonus:percent} 증가 표식을 남깁니다. 표식은 최대 {ability.maxMarkStacks:integer}회 중첩됩니다.</green>",
                "<red>[좀비] 표식 하나의 수치가 추가로 {ability.zombieMarkDamageTakenBonus:percent} 증가합니다.</red>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_WITHER, List.of(
                "<gray>최대 체력 {ability.highHealthThreshold:integer} 이상의 적을 먼저 노립니다. 해당 적이 없으면 현재 체력 비율이 가장 낮은 적을 공격합니다.</gray>",
                "<green>체력이 {ability.lowTargetHealthThreshold:percent} 이하인 적에게 {ability.lowTargetDamageBonus:percent} 추가 피해를 줍니다. 자신의 체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 적을 처치하면 준 피해의 {ability.criticalKillLifeStealRatio:percent}만큼 추가로 회복합니다.</green>",
                "<green>공격한 대상에게 받는 타워 피해 {ability.markDamageTakenBonus:percent} 증가 표식을 남깁니다. 표식은 최대 {ability.maxMarkStacks:integer}회 중첩됩니다.</green>",
                "<green>최대 체력 {ability.highHealthThreshold:integer} 이상의 적에게 {ability.highHealthDamageBonus:percent} 추가 피해를 줍니다.</green>",
                "<yellow>자신의 체력이 {ability.nether_global.criticalHealthThreshold:percent} 이하일 때 공격 대상 주변 {ability.criticalSplashRadius:blocks}에 주 대상 피해의 {ability.criticalSplashDamageRatio:percent}를 주고, 받는 타워 피해 {ability.criticalMarkDamageTakenBonus:percent} 증가 표식을 남깁니다.</yellow>",
                "<red>[좀비] 체력이 {ability.zombieExecuteThreshold:percent} 이하인 적에게 {ability.zombieExecuteDamageBonus:percent} 추가 피해를 줍니다. 기본 흡혈은 {ability.zombieLifeStealRatio:percent}지만 저체력 흡혈 보너스는 유지됩니다.</red>"
        ));
    }

    private NetherTowers() {
    }

    public static boolean isNetherTower(TowerType type) {
        return type != null && NETHER_TOWER_IDS.contains(type.id());
    }

    public static boolean isStriderLine(TowerType type) {
        return type != null && STRIDER_LINE_IDS.contains(type.id());
    }

    public static boolean isHoglinLine(TowerType type) {
        return type != null && HOGLIN_LINE_IDS.contains(type.id());
    }

    public static boolean isBlazeLine(TowerType type) {
        return type != null && BLAZE_LINE_IDS.contains(type.id());
    }

    public static boolean isSkeletonLine(TowerType type) {
        return type != null && SKELETON_LINE_IDS.contains(type.id());
    }
}
