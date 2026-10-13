package kim.biryeong.semiontd.tower.frost;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.entity.visual.AxolotlVisual;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.SnowGolemVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** 혹한 빌더의 현재 공개 타워와 계열 분류. */
public final class FrostTowers {
    public static final TowerType ICE_VANGUARD = tower(
            "frost_ice_vanguard",
            "얼음 전위 타워",
            40,
            80.0,
            2.0,
            5.0,
            20,
            40,
            SnowGolemVisual.builder().hasPumpkin(false).scale(0.75).build(),
            List.of(
                    "<gray>혹한 빌더의 저렴한 앞라인 탱커입니다.</gray>",
                    "<aqua>웨이브 시작 시 모든 얼음 전위 계열의 수를 감지합니다.</aqua>",
                    "<green>{ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기에 따라 받는 피해가 {ability.damageReductionAt3:percent}/{ability.damageReductionAt6:percent}/{ability.damageReductionAt9:percent} 감소합니다. 업그레이드에 관계없이 감지합니다.</green>"
            )
    );

    public static final TowerType STURDY_ICE_VANGUARD = tower(
            "frost_sturdy_ice_vanguard",
            "견고한 얼음 전위 타워",
            0,
            180.0,
            2.0,
            9.0,
            20,
            55,
            SnowGolemVisual.builder().hasPumpkin(false).scale(1.0).build(),
            List.of(
                    "<gray>더 단단해진 얼음 전위입니다.</gray>",
                    "<green>{ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기에 따라 받는 피해가 {ability.damageReductionAt3:percent}/{ability.damageReductionAt6:percent}/{ability.damageReductionAt9:percent} 감소합니다.</green>"
            )
    );

    public static final TowerType DONGTAE = tower(
            "frost_dongtae",
            "동태 타워",
            0,
            400.0,
            2.0,
            16.0,
            20,
            60,
            AxolotlVisual.builder().variant(Axolotl.Variant.BLUE).build().withScale(1.2),
            List.of(
                    "<blue>https://www.youtube.com/shorts/3cp_9peWlnk</blue>",
                    "<gray>사실은 그저 냉동창고 속 동태입니다.</gray>",
                    "<gray>어째선지 웃음을 자아냅니다.</gray>",
                    "<green>{ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기에 따라 받는 피해가 {ability.damageReductionAt3:percent}/{ability.damageReductionAt6:percent}/{ability.damageReductionAt9:percent} 감소합니다.</green>",
                    "<aqua>냉기 방출 장치 계열의 타워에 맞아 한기가 가득 차면 한기를 초기화하고, 다음 {ability.frost_global.fullyFrozenDurationTicks:seconds}간 받는 피해를 {ability.frost_global.fullyFrozenDamageReduction:percent} 경감합니다.</aqua>",
                    "<blue>이후 주변 {ability.frost_global.fullyFrozenChillRadius:blocks} 내의 모든 적에게 한기 {ability.frost_global.chillPerHit:percent}를 부여합니다.</blue>"
            )
    );

    public static final TowerType EMISSION_COOLING_DEVICE = tower(
            "frost_emission_cooling_device",
            "냉기 방출 장치",
            300,
            100.0,
            0.0,
            1.0,
            40,
            0,
            BlockDisplayVisual.builder(Blocks.BLUE_ICE.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>2초마다 폭 {ability.waveWidth:number}, 전방 {ability.waveRange:blocks}의 관통 파동을 발사합니다.</gray>",
                    "<aqua>적중한 웨이브·인컴 몬스터에게 1 피해와 한기 {ability.frost_global.chillPerHit:percent}를 부여합니다.</aqua>",
                    "<blue>한기가 가득 찬 적은 냉매 상태가 되어, 피해량과 공격속도가 각각 {ability.frost_global.refrigerantDamageReduction:percent} 감소합니다.</blue>",
                    "<yellow>해동에 적중당하면 디버프가 사라지고 최대 체력의 {ability.frost_global.thawMaxHealthDamage:percent} 피해를 받습니다.</yellow>",
                    "<yellow>플레이어마다 하나만 설치할 수 있습니다.</yellow>"
            )
    );

    public static final TowerType EMISSION_COOLING_DEVICE_EXPANDED = tower(
            "frost_emission_cooling_device_expanded",
            "산업용 냉기 순환팬",
            0,
            100.0,
            0.0,
            1.0,
            20,
            0,
            BlockDisplayVisual.builder(Blocks.BLUE_ICE.defaultBlockState()).scale(1.10).build(),
            List.of(
                    "<gray>사실은 그저 냉동창고의 냉기 순환팬입니다.</gray>",
                    "<gray>1초마다 폭 {ability.waveWidth:number}, 전방 {ability.waveRange:blocks}의 관통 파동을 발사합니다.</gray>",
                    "<aqua>적중한 웨이브·인컴 몬스터에게 1 피해와 한기 {ability.frost_global.chillPerHit:percent}를 부여합니다.</aqua>",
                    "<blue>한기가 가득 찬 적은 냉매 상태가 되어, 피해량과 공격속도가 각각 {ability.frost_global.refrigerantDamageReduction:percent} 감소합니다.</blue>",
                    "<yellow>해동에 적중당하면 디버프가 사라지고 최대 체력의 {ability.frost_global.thawMaxHealthDamage:percent} 피해를 받습니다.</yellow>",
                    "<yellow>플레이어마다 하나만 설치할 수 있습니다.</yellow>"
            )
    );

    public static final TowerType ERUPTION_COOLING_DEVICE = tower(
            "frost_eruption_cooling_device",
            "냉기 분출 장치",
            800,
            100.0,
            0.0,
            0.0,
            Integer.MAX_VALUE,
            0,
            BlockDisplayVisual.builder(Blocks.PACKED_ICE.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<red><bold>!!냉기 방출 타워의 앞에 위치하게 하세요!!</bold></red>",
                    "<gray>공격하지 않으며 웨이브 시작 시 아군 혹한 타워의 수로 분출 스택을 고정합니다.</gray>",
                    "<aqua>각 타워 계열의 보유 수가 {ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기일 때 {ability.frost_global.eruptionStacksAt3:integer}/{ability.frost_global.eruptionStacksAt6:integer}/{ability.frost_global.eruptionStacksAt9:integer}스택을 얻으며, 효과는 최대 {ability.frost_global.eruptionMaxStacks:integer}스택까지 증가합니다.</aqua>",
                    "<blue>본인 라인의 적은 스택당 공격력 {ability.frost_global.eruptionOwnDamageReductionPerStack:percent}, 공격속도 {ability.frost_global.eruptionOwnAttackSpeedReductionPerStack:percent}가 감소합니다.</blue>",
                    "<yellow>한 기만 설치할 수 있으며 현재 단계에서는 다른 아군 라인에 영향을 주지 않습니다.</yellow>",
                    "<aqua>방출 파동에 맞아 한기를 {ability.frost_global.fullOperationEruptionChill:percent}까지 저장하며 냉매 상태가 되지 않습니다.</aqua>",
                    "<gold>방출 파동으로 한기를 가득 채울 때마다 동태·냉동 식품·회복 계열의 특수 능력을 계열당 한 번씩 감지합니다.</gold>",
                    "<gold>세 특수 계열이 각각 {ability.frost_global.fullOperationMaxActivationsPerFamily:integer}회 발동해 {ability.frost_global.fullOperationRequiredActivations:integer}스택이 되면 9번 슬롯에서 완전 가동을 사용할 수 있습니다.</gold>"
            )
    );

    public static final TowerType ERUPTION_COOLING_DEVICE_EXPANDED = tower(
            "frost_eruption_cooling_device_expanded",
            "중앙 냉매 압축기",
            0,
            100.0,
            0.0,
            0.0,
            Integer.MAX_VALUE,
            0,
            BlockDisplayVisual.builder(Blocks.PACKED_ICE.defaultBlockState()).scale(1.10).build(),
            List.of(
                    "<red><bold>!!냉기 방출 타워의 앞에 위치하게 하세요!!</bold></red>",
                    "<gray>사실은 그저 냉동창고의 냉매 압축 장치입니다.</gray>",
                    "<gray>공격하지 않고 웨이브 시작 시 아군 혹한 타워 수로 분출 스택을 고정합니다.</gray>",
                    "<aqua>각 타워 계열의 보유 수가 {ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기일 때 {ability.frost_global.eruptionStacksAt3:integer}/{ability.frost_global.eruptionStacksAt6:integer}/{ability.frost_global.eruptionStacksAt9:integer}스택을 얻으며, 효과는 최대 {ability.frost_global.eruptionMaxStacks:integer}스택까지 증가합니다.</aqua>",
                    "<blue>본인 라인의 적은 스택당 공격력 {ability.frost_global.eruptionOwnDamageReductionPerStack:percent}, 공격속도 {ability.frost_global.eruptionOwnAttackSpeedReductionPerStack:percent}가 감소합니다.</blue>",
                    "<green>다른 아군 라인의 적도 스택당 공격력 {ability.frost_global.eruptionAllyDamageReductionPerStack:percent}, 공격속도 {ability.frost_global.eruptionAllyAttackSpeedReductionPerStack:percent}가 감소합니다.</green>",
                    "<yellow>다른 아군 라인에는 여러 혹한 빌더의 효과 중 가장 강한 하나만 적용됩니다.</yellow>",
                    "<aqua>방출 파동에 맞아 한기를 {ability.frost_global.fullOperationEruptionChill:percent}까지 저장하며 냉매 상태가 되지 않습니다.</aqua>",
                    "<gold>방출 파동으로 한기를 가득 채울 때마다 동태·냉동 식품·회복 계열의 특수 능력을 계열당 한 번씩 감지합니다.</gold>",
                    "<gold>세 특수 계열이 각각 {ability.frost_global.fullOperationMaxActivationsPerFamily:integer}회 발동해 {ability.frost_global.fullOperationRequiredActivations:integer}스택이 되면 9번 슬롯에서 완전 가동을 사용할 수 있습니다.</gold>"
            )
    );

    public static final TowerType ICE_BREAKER_T1 = splashTower(
            "frost_ice_breaker_t1", "얼음 파쇄병 타워", 55, 10.0, 20,
            Blocks.IRON_BLOCK, 0.80,
            List.of(
                    "<gray>공격 대상 주변 {ability.splashRadius:blocks}의 적에게 동일한 물리 피해를 줍니다.</gray>",
                    "<blue>공격 가능한 냉매 상태의 적을 우선하여 공격합니다.</blue>",
                    "<aqua>냉매 상태의 적을 적중하면 해동시켜 냉매를 지우고 최대 체력의 {ability.frost_global.thawMaxHealthDamage:percent} 고정 피해를 줍니다.</aqua>"
            )
    );

    public static final TowerType ICE_BREAKER_T2 = splashTower(
            "frost_ice_breaker_t2", "동토 분쇄자 타워", 0, 10.0, 17,
            Blocks.IRON_BLOCK, 0.95,
            ICE_BREAKER_T1.description()
    );

    public static final TowerType ICE_BREAKER_T3 = splashTower(
            "frost_ice_breaker_t3", "제상 히터 타워", 0, 20.0, 17,
            Blocks.BLAST_FURNACE, 1.10,
            List.of(
                    "<gray>사실은 그저 냉동창고 속 제상 히터입니다. 성에가 낄 걱정은 없겠군요.</gray>",
                    "<gray>공격 대상 주변 {ability.splashRadius:blocks}의 적에게 동일한 물리 피해를 줍니다.</gray>",
                    "<blue>공격 가능한 냉매 상태의 적을 우선하여 공격합니다.</blue>",
                    "<aqua>냉매 상태의 적을 적중하면 해동하여 냉매를 지우고 최대 체력의 {ability.frost_global.thawMaxHealthDamage:percent} 고정 피해를 줍니다.</aqua>",
                    "<gold>이후, 즉시 한 번의 추가 공격을 가합니다.</gold>"
            )
    );

    public static final TowerType FROZEN_DUMPLING_T1 = splashTower(
            "frost_frozen_dumpling_t1", "얼음 요리사 타워", 55, 12.0, 20,
            Blocks.SNOW_BLOCK, 0.80,
            List.of(
                    "<gray>공격 대상 주변 {ability.splashRadius:blocks}의 적에게 동일한 물리 피해를 줍니다.</gray>",
                    "<aqua>{ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기에 따라 추가 효과를 얻습니다. 업그레이드에 관계없이 감지합니다.</aqua>",
                    "<green>{ability.frost_global.firstThreshold:integer}기: 공격력이 {ability.frozenFoodDamageBonusAt3:number} 증가합니다.</green>",
                    "<green>{ability.frost_global.secondThreshold:integer}기: 스플래시 범위가 {ability.frost_global.frozenFoodSplashRadiusBonusAt6:blocks} 증가합니다.</green>",
                    "<gold>{ability.frost_global.thirdThreshold:integer}기: 최대 체력이 가장 높은 인컴 몬스터를 우선 공격하고, 인컴 대상 피해가 {ability.frozenFoodIncomeDamageBonusAt9:percent} 증가합니다.</gold>",
                    "<aqua>방출 파동으로 한기를 가득 채우면 한기를 초기화하고 공격 주기와 무관하게 즉시 {ability.frost_global.frozenFoodRefrigerantBonusAttacks:integer}회 추가 공격합니다.</aqua>"
            )
    );

    public static final TowerType FROZEN_DUMPLING_T2 = splashTower(
            "frost_frozen_dumpling_t2", "빙하 주계병 타워", 0, 30.0, 17,
            Blocks.SNOW_BLOCK, 0.95,
            List.of(
                    "<gray>공격 대상 주변 {ability.splashRadius:blocks}의 적에게 동일한 물리 피해를 줍니다.</gray>",
                    "<green>{ability.frost_global.firstThreshold:integer}기: 공격력이 {ability.frozenFoodDamageBonusAt3:number} 증가합니다.</green>",
                    "<green>{ability.frost_global.secondThreshold:integer}기: 스플래시 범위가 {ability.frost_global.frozenFoodSplashRadiusBonusAt6:blocks} 증가합니다.</green>",
                    "<gold>{ability.frost_global.thirdThreshold:integer}기: 최대 체력이 가장 높은 인컴 몬스터를 우선 공격하고 인컴 대상 피해가 {ability.frozenFoodIncomeDamageBonusAt9:percent} 증가합니다.</gold>",
                    "<aqua>방출 파동으로 한기를 가득 채우면 한기를 초기화하고 공격 주기와 무관하게 즉시 {ability.frost_global.frozenFoodRefrigerantBonusAttacks:integer}회 추가 공격합니다.</aqua>"
            )
    );

    public static final TowerType FROZEN_DUMPLING_T3 = splashTower(
            "frost_frozen_dumpling_t3", "냉동 식품 타워", 0, 45.0, 14,
            Blocks.SMOKER, 1.10,
            List.of(
                    "<gray>사실은 그저 냉동창고 속 냉동 식품입니다.</gray>",
                    "<gray>공격 대상 주변 {ability.splashRadius:blocks}의 적에게 동일한 물리 피해를 줍니다.</gray>",
                    "<green>{ability.frost_global.firstThreshold:integer}기: 공격력이 {ability.frozenFoodDamageBonusAt3:number} 증가합니다.</green>",
                    "<green>{ability.frost_global.secondThreshold:integer}기: 스플래시 범위가 {ability.frost_global.frozenFoodSplashRadiusBonusAt6:blocks} 증가합니다.</green>",
                    "<gold>{ability.frost_global.thirdThreshold:integer}기: 최대 체력이 가장 높은 인컴 몬스터를 우선 공격하고 인컴 대상 피해가 {ability.frozenFoodIncomeDamageBonusAt9:percent} 증가합니다.</gold>",
                    "<aqua>방출 파동으로 한기를 가득 채우면 한기를 초기화하고 공격 주기와 무관하게 즉시 {ability.frost_global.frozenFoodRefrigerantBonusAttacks:integer}회 추가 공격합니다.</aqua>"
            )
    );

    public static final TowerType ICEBOX_T1 = healingTower(
            "frost_icebox_t1", "얼음 구호병 타워", 45,
            BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace("white_shulker_box")).orElseThrow(), 0.80,
            List.of(
                    "<gray>{ability.healIntervalTicks:seconds}마다 반경 {ability.healRadius:blocks}의 부상당한 아군 타워를 치료합니다.</gray>",
                    "<green>치료파동의 회복량은 {ability.healAmount:health}입니다.</green>",
                    "<green>{ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기에 따라 치료받은 대상이 {ability.damageReductionTicks:seconds}간 받는 피해가 {ability.frost_global.healerDamageReductionAt3:percent}/{ability.frost_global.healerDamageReductionAt6:percent}/{ability.frost_global.healerDamageReductionAt9:percent} 감소합니다.</green>",
                    "<aqua>냉기 방출 장치 계열의 파동에 맞을 때마다 다음 치료가 {ability.frost_global.healerCoolingAdvanceTicks:seconds} 앞당겨지고 한기가 쌓입니다.</aqua>",
                    "<blue>한기가 가득 차면 즉시 초기화하고 {ability.frost_global.healerRefrigerantPulseMultiplier:number}배 별도 회복 파동을 방출합니다.</blue>"
            )
    );

    public static final TowerType ICEBOX_T2 = healingTower(
            "frost_icebox_t2", "혹한 의무관 타워", 0,
            BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace("light_blue_shulker_box")).orElseThrow(), 0.95,
            List.of(
                    "<gray>{ability.healIntervalTicks:seconds}마다 반경 {ability.healRadius:blocks}의 부상당한 아군 타워를 치료합니다.</gray>",
                    "<green>치료파동의 회복량은 {ability.healAmount:health}입니다.</green>",
                    "<green>{ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기에 따라 치료받은 대상이 {ability.damageReductionTicks:seconds}간 받는 피해가 {ability.frost_global.healerDamageReductionAt3:percent}/{ability.frost_global.healerDamageReductionAt6:percent}/{ability.frost_global.healerDamageReductionAt9:percent} 감소합니다.</green>",
                    "<aqua>냉기 방출 장치 계열의 파동에 맞을 때마다 다음 치료가 {ability.frost_global.healerCoolingAdvanceTicks:seconds} 앞당겨지고 한기가 쌓입니다.</aqua>",
                    "<blue>한기가 가득 차면 즉시 초기화하고 {ability.frost_global.healerRefrigerantPulseMultiplier:number}배 별도 회복 파동을 방출합니다.</blue>"
            )
    );

    public static final TowerType ICEBOX_T3 = healingTower(
            "frost_icebox_t3", "아이스박스 타워", 0,
            Blocks.BARREL, 1.10,
            List.of(
                    "<gray>사실은 그저 냉동창고 속 아이스박스입니다. 주변의 타워를 다시 얼려주는군요.</gray>",
                    "<gray>{ability.healIntervalTicks:seconds}마다 반경 {ability.healRadius:blocks}의 부상당한 아군 타워를 치료합니다.</gray>",
                    "<green>치료파동의 회복량은 {ability.healAmount:health}입니다.</green>",
                    "<green>{ability.frost_global.firstThreshold:integer}/{ability.frost_global.secondThreshold:integer}/{ability.frost_global.thirdThreshold:integer}기에 따라 치료받은 대상이 {ability.damageReductionTicks:seconds}간 받는 피해가 {ability.frost_global.healerDamageReductionAt3:percent}/{ability.frost_global.healerDamageReductionAt6:percent}/{ability.frost_global.healerDamageReductionAt9:percent} 감소합니다.</green>",
                    "<aqua>냉기 방출 장치 계열의 파동에 맞을 때마다 다음 치료가 {ability.frost_global.healerCoolingAdvanceTicks:seconds} 앞당겨지고 한기가 쌓입니다.</aqua>",
                    "<blue>한기가 가득 차면 즉시 초기화하고 {ability.frost_global.healerRefrigerantPulseMultiplier:number}배 별도 회복 파동을 방출합니다.</blue>"
            )
    );

    private static final Set<String> VANGUARD_IDS = Set.of(
            ICE_VANGUARD.id(),
            STURDY_ICE_VANGUARD.id(),
            DONGTAE.id()
    );
    private static final Set<String> EMISSION_COOLING_IDS = Set.of(
            EMISSION_COOLING_DEVICE.id(),
            EMISSION_COOLING_DEVICE_EXPANDED.id()
    );
    private static final Set<String> ERUPTION_COOLING_IDS = Set.of(
            ERUPTION_COOLING_DEVICE.id(),
            ERUPTION_COOLING_DEVICE_EXPANDED.id()
    );
    private static final Set<String> ICE_BREAKER_IDS = Set.of(
            ICE_BREAKER_T1.id(),
            ICE_BREAKER_T2.id(),
            ICE_BREAKER_T3.id()
    );
    private static final Set<String> FROZEN_DUMPLING_IDS = Set.of(
            FROZEN_DUMPLING_T1.id(),
            FROZEN_DUMPLING_T2.id(),
            FROZEN_DUMPLING_T3.id()
    );
    private static final Set<String> ICEBOX_IDS = Set.of(
            ICEBOX_T1.id(),
            ICEBOX_T2.id(),
            ICEBOX_T3.id()
    );
    private static final List<TowerType> ALL = List.of(
            ICE_VANGUARD,
            STURDY_ICE_VANGUARD,
            DONGTAE,
            EMISSION_COOLING_DEVICE,
            EMISSION_COOLING_DEVICE_EXPANDED,
            ERUPTION_COOLING_DEVICE,
            ERUPTION_COOLING_DEVICE_EXPANDED,
            ICE_BREAKER_T1,
            ICE_BREAKER_T2,
            ICE_BREAKER_T3,
            FROZEN_DUMPLING_T1,
            FROZEN_DUMPLING_T2,
            FROZEN_DUMPLING_T3,
            ICEBOX_T1,
            ICEBOX_T2,
            ICEBOX_T3
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private FrostTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isFrostTower(TowerType type) {
        return type != null && ALL.stream().anyMatch(candidate -> candidate.id().equals(type.id()));
    }

    public static boolean isVanguard(TowerType type) {
        return type != null && VANGUARD_IDS.contains(type.id());
    }

    public static boolean isEmissionCoolingDevice(TowerType type) {
        return type != null && EMISSION_COOLING_IDS.contains(type.id());
    }

    public static boolean isCoolingDevice(TowerType type) {
        return isEmissionCoolingDevice(type) || isEruptionCoolingDevice(type);
    }

    public static boolean isEruptionCoolingDevice(TowerType type) {
        return type != null && ERUPTION_COOLING_IDS.contains(type.id());
    }

    public static boolean isExpandedEruptionCoolingDevice(TowerType type) {
        return type != null && ERUPTION_COOLING_DEVICE_EXPANDED.id().equals(type.id());
    }

    public static boolean isIceBreaker(TowerType type) {
        return type != null && ICE_BREAKER_IDS.contains(type.id());
    }

    public static boolean isFrozenDumpling(TowerType type) {
        return type != null && FROZEN_DUMPLING_IDS.contains(type.id());
    }

    public static boolean isIcebox(TowerType type) {
        return type != null && ICEBOX_IDS.contains(type.id());
    }

    public static boolean isSplashAttacker(TowerType type) {
        return isIceBreaker(type) || isFrozenDumpling(type);
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 1;
        }
        if (STURDY_ICE_VANGUARD.id().equals(type.id())) {
            return 2;
        }
        if (DONGTAE.id().equals(type.id())) {
            return 3;
        }
        if (EMISSION_COOLING_DEVICE_EXPANDED.id().equals(type.id())
                || ERUPTION_COOLING_DEVICE_EXPANDED.id().equals(type.id())
                || ICE_BREAKER_T2.id().equals(type.id())
                || FROZEN_DUMPLING_T2.id().equals(type.id())
                || ICEBOX_T2.id().equals(type.id())) {
            return 2;
        }
        if (ICE_BREAKER_T3.id().equals(type.id())
                || FROZEN_DUMPLING_T3.id().equals(type.id())
                || ICEBOX_T3.id().equals(type.id())) {
            return 3;
        }
        return 1;
    }

    private static TowerType splashTower(
            String id,
            String displayName,
            long mineralCost,
            double damage,
            int attackIntervalTicks,
            net.minecraft.world.level.block.Block block,
            double scale,
            List<String> description
    ) {
        return tower(
                id,
                displayName,
                mineralCost,
                70.0,
                6.0,
                damage,
                attackIntervalTicks,
                0,
                BlockDisplayVisual.builder(block.defaultBlockState()).scale(scale).build(),
                description
        );
    }

    private static TowerType healingTower(
            String id,
            String displayName,
            long mineralCost,
            net.minecraft.world.level.block.Block block,
            double scale,
            List<String> description
    ) {
        return tower(
                id,
                displayName,
                mineralCost,
                100.0,
                3.0,
                0.0,
                100,
                0,
                BlockDisplayVisual.builder(block.defaultBlockState()).scale(scale).build(),
                description
        );
    }
}
