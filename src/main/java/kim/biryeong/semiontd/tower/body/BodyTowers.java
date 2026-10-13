package kim.biryeong.semiontd.tower.body;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.level.block.Blocks;

/** 신체 빌더의 타워 종류와 계열 분류. */
public final class BodyTowers {
    // tower 인자 순서: ID, 이름, 비용, 체력, 사거리, 피해, 공격 주기, 어그로, 외형, 설명

    // ------------------------------------------------------------------ 심장

    public static final TowerType HEART_T1 = tower(
            "body_heart_t1",
            "심장 타워",
            0,
            100.0,
            0.0,
            0.0,
            24,
            0,
            BlockDisplayVisual.builder(Blocks.CREAKING_HEART.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>신체 전체를 움직이게 하는 타워입니다.</gray>",
                    "<aqua>이 타워의 공격 주기마다 다른 모든 신체가 행동합니다.</aqua>",
                    "<yellow>심장 계열은 플레이어마다 하나만 설치할 수 있습니다.</yellow>"
            )
    );

    public static final TowerType HEART_T2 = tower(
            "body_heart_t2",
            "맥동하는 심장 타워",
            145,
            150.0,
            0.0,
            0.0,
            24,
            0,
            BlockDisplayVisual.builder(Blocks.REDSTONE_BLOCK.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>신체 전체를 움직이게 하는 타워입니다.</gray>",
                    "<aqua>이 타워의 공격 주기마다 다른 모든 신체가 행동합니다.</aqua>",
                    "<red>자신의 다른 신체 타워가 사망할 때 영구 중첩을 1 얻습니다.</red>",
                    "<yellow>{ability.stacksPerIntervalReduction:integer}중첩마다 공격 주기가 1틱 감소하며, 최대 {ability.maxDeathStacks:integer}회 중첩됩니다.</yellow>",
                    "<yellow>심장 계열은 플레이어마다 하나만 설치할 수 있습니다.</yellow>"
            )
    );

    public static final TowerType HEART_T3 = tower(
            "body_heart_t3",
            "갈망하는 심장 타워",
            270,
            250.0,
            0.0,
            0.0,
            18,
            0,
            BlockDisplayVisual.builder(Blocks.FIRE_CORAL_BLOCK.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>신체 전체를 움직이게 하는 타워입니다.</gray>",
                    "<aqua>이 타워의 공격 주기마다 다른 모든 신체가 행동합니다.</aqua>",
                    "<red>자신의 다른 신체 타워가 사망할 때 영구 중첩을 1 얻습니다.</red>",
                    "<yellow>{ability.stacksPerIntervalReduction:integer}중첩마다 공격 주기가 1틱 감소하며, 최대 {ability.maxDeathStacks:integer}회 중첩됩니다.</yellow>",
                    "<yellow>심장 계열은 플레이어마다 하나만 설치할 수 있습니다.</yellow>"
            )
    );

    // ------------------------------------------------------------------ 뇌

    public static final TowerType BRAIN_T1 = tower(
            "body_brain_t1",
            "뇌 타워",
            70,
            55.0,
            14.0,
            3.0,
            20,
            0,
            BlockDisplayVisual.builder(Blocks.CONCRETE_POWDER.pink().defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>공격 대상과 반경 {ability.splashRadius:blocks} 안의 적에게 광역 피해를 줍니다.</gray>",
                    "<aqua>맞은 적은 받는 피해가 {ability.damageTaken:percent} 증가하고 주는 피해가 {ability.attackReduction:percent} 감소합니다.</aqua>",
                    "<yellow>약화 효과는 중첩되지 않으며, 다시 적중하면 지속시간이 갱신됩니다.</yellow>"
            )
    );

    public static final TowerType BRAIN_T2 = tower(
            "body_brain_t2",
            "지능적인 뇌 타워",
            110,
            80.0,
            17.0,
            6.0,
            20,
            0,
            BlockDisplayVisual.builder(Blocks.WOOL.pink().defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>공격 대상과 반경 {ability.splashRadius:blocks} 안의 적에게 광역 피해를 줍니다.</gray>",
                    "<aqua>맞은 적은 받는 피해가 {ability.damageTaken:percent} 증가하고 주는 피해가 {ability.attackReduction:percent} 감소합니다.</aqua>",
                    "<yellow>약화 효과는 중첩되지 않으며, 다시 적중하면 지속시간이 갱신됩니다.</yellow>"
            )
    );

    public static final TowerType BRAIN_T3 = tower(
            "body_brain_t3",
            "초지능 뇌 타워",
            200,
            115.0,
            20.0,
            10.0,
            20,
            0,
            BlockDisplayVisual.builder(Blocks.BRAIN_CORAL_BLOCK.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>공격 대상과 반경 {ability.splashRadius:blocks} 안의 적에게 광역 피해를 줍니다.</gray>",
                    "<aqua>맞은 적은 받는 피해가 {ability.damageTaken:percent} 증가하고 주는 피해가 {ability.attackReduction:percent} 감소합니다.</aqua>",
                    "<yellow>약화 효과는 중첩되지 않으며, 다시 적중하면 지속시간이 갱신됩니다.</yellow>"
            )
    );

    // ------------------------------------------------------------------ 피부

    public static final TowerType SKIN_T1 = tower(
            "body_skin_t1",
            "피부 타워",
            50,
            120.0,
            2.5,
            4.0,
            20,
            50,
            BlockDisplayVisual.builder(Blocks.OAK_WOOD.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>적의 공격을 끌어내는 신체의 방어막입니다.</gray>",
                    "<aqua>행동할 때 반경 {stat.range:blocks} 안의 모든 적에게 피해를 줍니다.</aqua>"
            )
    );

    public static final TowerType SKIN_T2 = tower(
            "body_skin_t2",
            "단단한 피부 타워",
            110,
            250.0,
            3.0,
            8.0,
            20,
            80,
            BlockDisplayVisual.builder(Blocks.SPRUCE_WOOD.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>적의 공격을 끌어내는 신체의 방어막입니다.</gray>",
                    "<aqua>행동할 때 반경 {stat.range:blocks} 안의 모든 적에게 피해를 줍니다.</aqua>",
                    "<yellow>행동할 때마다 받는 피해가 {ability.damageReductionPerStack:percent} 감소하며, 최대 3회 중첩됩니다.</yellow>"
            )
    );

    public static final TowerType SKIN_T3 = tower(
            "body_skin_t3",
            "외골격 피부 타워",
            210,
            450.0,
            3.5,
            14.0,
            20,
            110,
            BlockDisplayVisual.builder(Blocks.ANCIENT_DEBRIS.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>적의 공격을 끌어내는 신체의 방어막입니다.</gray>",
                    "<aqua>행동할 때 반경 {stat.range:blocks} 안의 모든 적에게 피해를 줍니다.</aqua>",
                    "<yellow>행동할 때마다 받는 피해가 {ability.damageReductionPerStack:percent} 감소하며, 최대 3회 중첩됩니다.</yellow>"
            )
    );

    // ------------------------------------------------------------------ 눈

    public static final TowerType EYE_T1 = tower(
            "body_eye_t1",
            "눈 타워",
            45,
            50.0,
            11.0,
            6.0,
            20,
            10,
            BlockDisplayVisual.builder(Blocks.CHORUS_PLANT.defaultBlockState()).scale(1.0).build(),
            List.of(
                    "<gray>몬스터 생성 지점만 바라보는 고정형 원거리 타워입니다.</gray>",
                    "<aqua>시선 앞의 적을 모두 관통하여 피해를 줍니다.</aqua>",
                    "<red>뒤쪽이나 시선 밖의 적은 공격할 수 없습니다.</red>"
            )
    );

    public static final TowerType EYE_T2 = tower(
            "body_eye_t2",
            "개안 타워",
            110,
            75.0,
            15.0,
            13.0,
            20,
            10,
            BlockDisplayVisual.builder(Blocks.CHORUS_FLOWER.defaultBlockState()).scale(1.0).build(),
            List.of(
                    "<gray>몬스터 생성 지점만 바라보는 고정형 원거리 타워입니다.</gray>",
                    "<aqua>시선 앞의 적을 모두 관통하여 피해를 줍니다.</aqua>",
                    "<red>뒤쪽이나 시선 밖의 적은 공격할 수 없습니다.</red>"
            )
    );

    public static final TowerType EYE_T3 = tower(
            "body_eye_t3",
            "천리안 타워",
            220,
            110.0,
            16.0,
            33.0,
            20,
            10,
            BlockDisplayVisual.builder(Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState()).scale(0.95).build(),
            List.of(
                    "<gray>몬스터 생성 지점만 바라보는 고정형 원거리 타워입니다.</gray>",
                    "<aqua>시선 앞의 적을 모두 관통하여 피해를 줍니다.</aqua>",
                    "<red>뒤쪽이나 시선 밖의 적은 공격할 수 없습니다.</red>"
            )
    );

    // ------------------------------------------------------------------ 생식기

    public static final TowerType GENITAL_T1 = tower(
            "body_genital_t1",
            "생식기 타워",
            45,
            55.0,
            7.0,
            6.0,
            20,
            5,
            BlockDisplayVisual.builder(Blocks.LEVER.defaultBlockState()).scale(1.1).build(),
            List.of(
                    "<gray>단일 대상 특화 원거리 타워입니다.</gray>",
                    "<aqua>같은 적에게 두 번 적중하면 마법 피해 {ability.magicProcDamage:number}를 추가로 입히고 둔화시킵니다.</aqua>"
            )
    );

    public static final TowerType GENITAL_T2 = tower(
            "body_genital_t2",
            "기계화된 생식기 타워",
            100,
            85.0,
            9.0,
            15.0,
            20,
            5,
            BlockDisplayVisual.builder(Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState()).scale(1.1).build(),
            List.of(
                    "<gray>단일 대상 특화 원거리 타워입니다.</gray>",
                    "<aqua>같은 적에게 두 번 적중하면 마법 피해 {ability.magicProcDamage:number}를 추가로 입히고 둔화시킵니다.</aqua>",
                    "<yellow>대상 주변의 적 1마리를 추가 공격합니다.</yellow>"
            )
    );

    public static final TowerType GENITAL_T3 = tower(
            "body_genital_t3",
            "초월한 생식기 타워",
            205,
            120.0,
            12.0,
            30.0,
            20,
            5,
            BlockDisplayVisual.builder(Blocks.END_ROD.defaultBlockState()).scale(1.1).build(),
            List.of(
                    "<gray>단일 대상 특화 원거리 타워입니다.</gray>",
                    "<aqua>같은 적에게 두 번 적중하면 마법 피해 {ability.magicProcDamage:number}를 추가로 입히고 둔화시킵니다.</aqua>",
                    "<yellow>대상 주변의 적 2마리를 추가 공격합니다.</yellow>"
            )
    );

    // ------------------------------------------------------------------ 분류

    private static final Set<String> HEART_IDS = ids(HEART_T1, HEART_T2, HEART_T3);
    private static final Set<String> BRAIN_IDS = ids(BRAIN_T1, BRAIN_T2, BRAIN_T3);
    private static final Set<String> SKIN_IDS = ids(SKIN_T1, SKIN_T2, SKIN_T3);
    private static final Set<String> EYE_IDS = ids(EYE_T1, EYE_T2, EYE_T3);
    private static final Set<String> GENITAL_IDS = ids(GENITAL_T1, GENITAL_T2, GENITAL_T3);
    private static final List<TowerType> ALL = List.of(
            HEART_T1, HEART_T2, HEART_T3,
            BRAIN_T1, BRAIN_T2, BRAIN_T3,
            SKIN_T1, SKIN_T2, SKIN_T3,
            EYE_T1, EYE_T2, EYE_T3,
            GENITAL_T1, GENITAL_T2, GENITAL_T3
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private BodyTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isBodyTower(TowerType type) {
        return roleOf(type) != null;
    }

    public static boolean isHeart(TowerType type) {
        return type != null && HEART_IDS.contains(type.id());
    }

    public static Role roleOf(TowerType type) {
        if (type == null) {
            return null;
        }
        String id = type.id();
        if (HEART_IDS.contains(id)) {
            return Role.HEART;
        }
        if (BRAIN_IDS.contains(id)) {
            return Role.BRAIN;
        }
        if (SKIN_IDS.contains(id)) {
            return Role.SKIN;
        }
        if (EYE_IDS.contains(id)) {
            return Role.EYE;
        }
        if (GENITAL_IDS.contains(id)) {
            return Role.GENITAL;
        }
        return null;
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 1;
        }
        String id = type.id();
        if (id.endsWith("_t3")) {
            return 3;
        }
        if (id.endsWith("_t2")) {
            return 2;
        }
        return 1;
    }

    private static Set<String> ids(TowerType... types) {
        return Arrays.stream(types)
                .map(TowerType::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    public enum Role {
        HEART,
        BRAIN,
        SKIN,
        EYE,
        GENITAL
    }
}
