package kim.biryeong.semiontd.tower.resonance;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.List;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.MoobloomVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;

public final class ResonanceTowers {
    private static final String LINK_DESCRIPTION = "<gray>{ability.linkRange:integer}칸 안에 다른 종의 무블룸을 모으면 공명합니다.</gray>";

    public static final TowerType FOCUS_CRYSTAL = tower(
            "t1_resonance_focus_moobloom",
            "민들레 무블룸",
            50,
            45,
            8,
            8,
            20,
            5,
            moobloom("dandelion"),
            List.of(
                    "<gray>단일 타겟 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType FOCUS_PRISM = tower(
            "t2_resonance_focus_moobloom",
            "해바라기 무블룸",
            180,
            80,
            6.5,
            15,
            15,
            8,
            moobloom("sunflower"),
            List.of(
                    "<gray>단일 타겟 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType FOCUS_CORE = tower(
            "t3_resonance_focus_moobloom",
            "주황 튤립 무블룸",
            320,
            95,
            7,
            28,
            16,
            12,
            moobloom("orange_tulip"),
            List.of(
                    "<gray>단일 타겟 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType WAVE_CRYSTAL = tower(
            "t1_resonance_wave_moobloom",
            "수레국화 무블룸",
            45,
            70,
            7,
            8,
            16,
            10,
            moobloom("cornflower"),
            List.of(
                    "<gray>업그레이드 시 범위 공격을 하는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType WAVE_PRISM = tower(
            "t2_resonance_wave_moobloom",
            "파란 난초 무블룸",
            50,
            60,
            5.5,
            15,
            14,
            12,
            moobloom("blue_orchid"),
            List.of(
                    "<gray>업그레이드 시 범위 공격을 하는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType WAVE_CORE = tower(
            "t3_resonance_wave_moobloom",
            "푸른 들꽃 무블룸",
            300,
            80,
            8,
            22,
            12,
            15,
            moobloom("azure_bluet"),
            List.of(
                    "<gray>업그레이드 시 범위 공격을 하는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType FROST_CRYSTAL = tower(
            "t1_resonance_frost_moobloom",
            "은방울꽃 무블룸",
            45,
            50,
            7,
            8,
            20,
            0,
            moobloom("lily_of_the_valley"),
            List.of(
                    "<gray>적의 이동속도와 공격속도를 낮추는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType FROST_PRISM = tower(
            "t2_resonance_frost_moobloom",
            "하얀 튤립 무블룸",
            150,
            75,
            8,
            10,
            16,
            2,
            moobloom("white_tulip"),
            List.of(
                    "<gray>적의 이동속도와 공격속도를 낮추고, 주위 무블룸이 둔화 대상을 더 세게 치게 합니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType FROST_CORE = tower(
            "t3_resonance_frost_moobloom",
            "데이지 무블룸",
            280,
            105,
            12,
            16,
            12,
            5,
            moobloom("oxeye_daisy"),
            List.of(
                    "<gray>광역 제어와 둔화 대상 피해 오라를 맡는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType AMPLIFY_CRYSTAL = tower(
            "t1_resonance_amplify_moobloom",
            "알리움 무블룸",
            45,
            100,
            5,
            8,
            15,
            40,
            moobloom("allium"),
            List.of(
                    "<gray>업그레이드 시 매우 단단해지며 아군에게 저항 효과를 주는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType AMPLIFY_PRISM = tower(
            "t2_resonance_amplify_moobloom",
            "라일락 무블룸",
            200,
            200,
            5.5,
            11,
            12,
            45,
            moobloom("lilac"),
            List.of(
                    "<gray>업그레이드 시 매우 단단해지며 아군에게 저항 효과를 주는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    public static final TowerType AMPLIFY_CORE = tower(
            "t3_resonance_amplify_moobloom",
            "작약 무블룸",
            350,
            450,
            6,
            20,
            10,
            50,
            moobloom("peony"),
            List.of(
                    "<gray>업그레이드 시 매우 단단해지며 아군에게 저항 효과를 주는 무블룸입니다.</gray>",
                    "<green>다른 무블룸 종류를 옆에 설치하면 추가 효과를 받아요.</green>"
            )
    );

    private ResonanceTowers() {
    }

    public static ResonanceAspect aspectOf(TowerType type) {
        if (type == null) {
            return null;
        }
        String id = type.id();
        if (FOCUS_CRYSTAL.id().equals(id) || FOCUS_PRISM.id().equals(id) || FOCUS_CORE.id().equals(id)) {
            return ResonanceAspect.FOCUS;
        }
        if (WAVE_CRYSTAL.id().equals(id) || WAVE_PRISM.id().equals(id) || WAVE_CORE.id().equals(id)) {
            return ResonanceAspect.WAVE;
        }
        if (FROST_CRYSTAL.id().equals(id) || FROST_PRISM.id().equals(id) || FROST_CORE.id().equals(id)) {
            return ResonanceAspect.FROST;
        }
        if (AMPLIFY_CRYSTAL.id().equals(id) || AMPLIFY_PRISM.id().equals(id) || AMPLIFY_CORE.id().equals(id)) {
            return ResonanceAspect.AMPLIFY;
        }
        return null;
    }

    public static boolean isResonanceTower(TowerType type) {
        return aspectOf(type) != null;
    }

    static {
        registerDescription(FOCUS_CRYSTAL);
        registerDescription(FOCUS_PRISM);
        registerDescription(FOCUS_CORE);
        registerDescription(WAVE_CRYSTAL);
        registerDescription(WAVE_PRISM);
        registerDescription(WAVE_CORE);
        registerDescription(FROST_CRYSTAL);
        registerDescription(FROST_PRISM);
        registerDescription(FROST_CORE);
        registerDescription(AMPLIFY_CRYSTAL);
        registerDescription(AMPLIFY_PRISM);
        registerDescription(AMPLIFY_CORE);
    }

    private static void registerDescription(TowerType type) {
        java.util.ArrayList<String> template = new java.util.ArrayList<>(type.description());
        template.add(LINK_DESCRIPTION);
        template.add("<green>자신을 제외하고 종류가 다른 주변 무블룸 {ability.level1RequiredLinks:integer}/{ability.level2RequiredLinks:integer}/{ability.level3RequiredLinks:integer}기와 연결되면 공명 1/2/3단계 효과를 얻습니다.</green>");
        template.add("<green>현재 해금: 공명 {ability.maxResonanceLevel:integer}단계 까지 활성화 가능합니다.</green>");
        switch (aspectOf(type)) {
            case FOCUS -> addFocusDescription(template);
            case WAVE -> addWaveDescription(template);
            case FROST -> addFrostDescription(template);
            case AMPLIFY -> addBloomDescription(template);
        }
        TowerDescriptionRegistry.registerTemplate(type, template);
    }

    private static void addFocusDescription(java.util.List<String> template) {
        template.add("<green>공명 1단계: 공격속도 +{ability.focusLevel1AttackSpeedBonus:percent}</green>");
        template.add("<green>공명 2단계: 공격속도 +{ability.focusLevel2AttackSpeedBonus:percent}, 피해 +{ability.focusLevel2DamageBonus:percent}</green>");
        template.add("<green>공명 3단계: 공격속도 +{ability.focusLevel3AttackSpeedBonus:percent}, 피해 +{ability.focusLevel3DamageBonus:percent}</green>");
        template.add("<green>{ability.focusStrikeEveryAttacks:integer}번째 공격마다 주 대상에게 {ability.focusStrikeDamageRatio:percent} 추가 피해를 줍니다.</green>");
    }

    private static void addWaveDescription(java.util.List<String> template) {
        template.add("<green>공명 1단계: 공격속도 +{ability.waveLevel1AttackSpeedBonus:percent}</green>");
        template.add("<green>공명 2단계: 매 공격마다 {ability.waveLevel2SplashRadius:blocks} 범위에 {ability.waveLevel2SplashDamageRatio:percent} 스플래시 피해를 줍니다.</green>");
        template.add("<green>공명 3단계: 스플래시가 {ability.waveLevel3SplashRadius:blocks}, {ability.waveLevel3SplashDamageRatio:percent}로 강화됩니다.</green>");
        template.add("<green>{ability.wavePulseEveryAttacks:integer}번째 공격마다 {ability.wavePulseRadius:blocks} 범위에 {ability.wavePulseDamageRatio:percent} 파동 피해를 줍니다.</green>");
    }

    private static void addFrostDescription(java.util.List<String> template) {
        template.add("<green>공명 1단계: 공격 대상에게 {ability.frostLevel1SlowTicks:seconds} 동안 이동속도 -{ability.frostLevel1SlowMagnitude:percent}, 공격속도 -{ability.frostLevel1AttackSpeedReductionMagnitude:percent}를 줍니다.</green>");
        template.add("<green>공명 2단계: 디버프가 {ability.frostLevel2SlowTicks:seconds}, 이동속도 -{ability.frostLevel2SlowMagnitude:percent}, 공격속도 -{ability.frostLevel2AttackSpeedReductionMagnitude:percent}로 강화됩니다.</green>");
        template.add("<green>{ability.frostAuraRange:blocks} 안 무블룸은 둔화 대상에게 피해 +{ability.frostLevel2AuraDamageVsSlowedBonus:percent}를 얻습니다.</green>");
        template.add("<green>공명 3단계: 디버프가 {ability.frostLevel3SlowTicks:seconds}, 이동속도 -{ability.frostLevel3SlowMagnitude:percent}, 공격속도 -{ability.frostLevel3AttackSpeedReductionMagnitude:percent}로 강화되고 오라 피해가 +{ability.frostLevel3AuraDamageVsSlowedBonus:percent}가 됩니다.</green>");
        template.add("<green>{ability.frostPulseEveryAttacks:integer}번째 공격마다 {ability.frostPulseRadius:blocks} 범위에 {ability.frostPulseDamageRatio:percent} 피해와 {ability.frostPulseSlowTicks:seconds} 동안 이동속도 -{ability.frostPulseSlowMagnitude:percent}, 공격속도 -{ability.frostPulseAttackSpeedReductionMagnitude:percent}를 줍니다.</green>");
    }

    private static void addBloomDescription(java.util.List<String> template) {
        template.add("<green>공명 1단계: 받는 피해 -{ability.bloomLevel1DamageReduction:percent}</green>");
        template.add("<green>공명 2단계: 받는 피해 -{ability.bloomLevel2DamageReduction:percent}, {ability.bloomAuraRange:blocks} 안 무블룸 공격속도 +{ability.bloomLevel2AuraAttackSpeedBonus:percent}</green>");
        template.add("<green>공명 3단계: 받는 피해 -{ability.bloomLevel3DamageReduction:percent}, {ability.bloomAuraRange:blocks} 안 무블룸 공격속도 +{ability.bloomLevel3AuraAttackSpeedBonus:percent}</green>");
        template.add("<green>{ability.bloomProtectEveryAttacks:integer}번째 공격마다 {ability.bloomProtectRadius:blocks} 안 무블룸을 공격력의 {ability.bloomProtectHealRatio:percent}만큼 회복하고 {ability.bloomProtectTicks:seconds} 동안 받는 피해 -{ability.bloomProtectDamageReduction:percent}를 줍니다.</green>");
    }

    private static EntityVisual moobloom(String variant) {
        return MoobloomVisual.builder().variant(variant).build();
    }
}
