package kim.biryeong.semiontd.tower.atlantis;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.entity.visual.AxolotlVisual;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.level.block.Blocks;

public final class AtlantisTowers {
    public static final TowerType TURTLE_T1 = tower(
            "atlantis_turtle_t1", "바다거북 타워", 55, 190.0, 2.6, 5.0, 20, 55,
            EntityVisual.builder(byId(EntityType.TURTLE)).scale(0.8).build(),
            turtleDescription("기본")
    );
    public static final TowerType TURTLE_T2 = tower(
            "atlantis_turtle_t2", "심해 거북 타워", 115, 320.0, 2.8, 9.0, 18, 85,
            EntityVisual.builder(byId(EntityType.TURTLE)).scale(1.0).build(),
            turtleDescription("중급")
    );
    public static final TowerType TURTLE_T3 = tower(
            "atlantis_turtle_t3", "해저 바다거북 타워", 240, 620.0, 3.0, 14.0, 16, 115,
            EntityVisual.builder(byId(EntityType.TURTLE)).scale(1.2).build(),
            turtleDescription("최종")
    );

    public static final TowerType DOLPHIN_T1 = tower(
            "atlantis_dolphin_t1", "돌고래 타워", 55, 80.0, 6.5, 13.0, 16, 0,
            EntityVisual.builder(byId(EntityType.DOLPHIN)).scale(0.7).build(),
            dolphinDescription("기본")
    );
    public static final TowerType DOLPHIN_T2 = tower(
            "atlantis_dolphin_t2", "심해 돌고래 타워", 120, 130.0, 7.5, 24.0, 14, 0,
            EntityVisual.builder(byId(EntityType.DOLPHIN)).scale(0.85).build(),
            dolphinDescription("중급")
    );
    public static final TowerType DOLPHIN_T3 = tower(
            "atlantis_dolphin_t3", "해저 돌고래 타워", 250, 190.0, 8.5, 40.0, 12, 0,
            EntityVisual.builder(byId(EntityType.DOLPHIN)).scale(1.0).build(),
            dolphinDescription("최종")
    );

    public static final TowerType AXOLOTL_T1 = tower(
            "atlantis_axolotl_t1", "우파루파 타워", 45, 70.0, 7.0, 6.0, 20, -10,
            AxolotlVisual.builder().variant(Axolotl.Variant.LUCY).build().withScale(0.9),
            axolotlDescription(1)
    );
    public static final TowerType AXOLOTL_T2 = tower(
            "atlantis_axolotl_t2", "황금 우파루파 타워", 95, 110.0, 8.0, 10.0, 18, -10,
            AxolotlVisual.builder().variant(Axolotl.Variant.GOLD).build().withScale(1.05),
            axolotlDescription(2)
    );
    public static final TowerType AXOLOTL_T3 = tower(
            "atlantis_axolotl_t3", "심해 우파루파 타워", 200, 150.0, 9.0, 16.0, 16, -10,
            AxolotlVisual.builder().variant(Axolotl.Variant.BLUE).build().withScale(1.2),
            axolotlDescription(3)
    );

    public static final TowerType CONDUIT_T1 = tower(
            "atlantis_conduit_t1", "전달체 타워", 50, 90.0, 5.0, 0.0, 20, -5,
            BlockDisplayVisual.builder(Blocks.CONDUIT.defaultBlockState()).scale(0.6).build(),
            conduitDescription("기본")
    );
    public static final TowerType CONDUIT_T2 = tower(
            "atlantis_conduit_t2", "공명 전달체 타워", 105, 130.0, 5.5, 0.0, 18, -5,
            BlockDisplayVisual.builder(Blocks.CONDUIT.defaultBlockState()).scale(0.75).build(),
            conduitDescription("중급")
    );
    public static final TowerType CONDUIT_T3 = tower(
            "atlantis_conduit_t3", "심연 전달체 타워", 215, 200.0, 6.0, 0.0, 16, -5,
            BlockDisplayVisual.builder(Blocks.CONDUIT.defaultBlockState()).scale(0.9).build(),
            conduitDescription("최종")
    );

    private static final Set<String> TURTLE_IDS = ids(TURTLE_T1, TURTLE_T2, TURTLE_T3);
    private static final Set<String> DOLPHIN_IDS = ids(DOLPHIN_T1, DOLPHIN_T2, DOLPHIN_T3);
    private static final Set<String> AXOLOTL_IDS = ids(AXOLOTL_T1, AXOLOTL_T2, AXOLOTL_T3);
    private static final Set<String> CONDUIT_IDS = ids(CONDUIT_T1, CONDUIT_T2, CONDUIT_T3);
    private static final List<TowerType> ALL = List.of(
            TURTLE_T1, TURTLE_T2, TURTLE_T3,
            DOLPHIN_T1, DOLPHIN_T2, DOLPHIN_T3,
            AXOLOTL_T1, AXOLOTL_T2, AXOLOTL_T3,
            CONDUIT_T1, CONDUIT_T2, CONDUIT_T3
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private AtlantisTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isAtlantisTower(TowerType type) {
        return roleOf(type) != null;
    }

    public static AtlantisRole roleOf(TowerType type) {
        if (type == null) {
            return null;
        }
        String id = type.id();
        if (TURTLE_IDS.contains(id)) {
            return AtlantisRole.TURTLE;
        }
        if (DOLPHIN_IDS.contains(id)) {
            return AtlantisRole.DOLPHIN;
        }
        if (AXOLOTL_IDS.contains(id)) {
            return AtlantisRole.AXOLOTL;
        }
        return CONDUIT_IDS.contains(id) ? AtlantisRole.CONDUIT : null;
    }

    public static boolean isTurtle(TowerType type) {
        return roleOf(type) == AtlantisRole.TURTLE;
    }

    public static boolean isDolphin(TowerType type) {
        return roleOf(type) == AtlantisRole.DOLPHIN;
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 1;
        }
        return type.id().endsWith("_t3") ? 3 : type.id().endsWith("_t2") ? 2 : 1;
    }

    private static Set<String> ids(TowerType... types) {
        return Arrays.stream(types).map(TowerType::id).collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> turtleDescription(String grade) {
        return List.of(
                "<gray>높은 체력과 어그로로 전방을 지키는 " + grade + " 거북 타워입니다.</gray>",
                "<green>몬스터가 오는 쪽 경로에 고압 구역을 <yellow>{ability.zoneCapacity:integer}개</yellow> 늘어놓습니다. 구역 반경은 {ability.zoneRadius:blocks}입니다.</green>",
                "<green>고압 구역 안의 몬스터는 압력 스택이 더 빠르게 쌓이고 이동 속도가 느려집니다.</green>",
                "<green>고압 구역 안의 아군 타워는 받는 피해가 {ability.zoneAllyDamageReduction:percent} 감소합니다.</green>",
                "<aqua>구역이 겹치는 자리에서는 두 효과가 <yellow>합쳐집니다</yellow>. 다만 각각 상한이 있어 무한정 강해지지는 않습니다.</aqua>",
                zoneRule()
        );
    }

    private static List<String> dolphinDescription(String grade) {
        return List.of(
                "<gray>압력을 쌓아 터뜨리는 " + grade + " 주력 공격 타워입니다.</gray>",
                "<green>공격할 때마다 대상에게 압력 스택을 {ability.stackPerHit:integer} 부여합니다.</green>",
                "<green>대상이 고압 구역을 벗어나거나 스택이 만료되거나 사망하면 <yellow>수압</yellow>이 터집니다.</green>",
                "<green>수압 피해는 이 타워의 공격력에 스택 수를 곱해 계산하며, 수압 배율이 {ability.waterPressureRatioBonus:percent} 증가합니다.</green>",
                waterPressureRule()
        );
    }

    /**
     * Axolotl descriptions are built per tier because abilities accumulate: referencing a key the
     * tier does not define would leave an unresolved {@code {ability.…}} placeholder in the dialog
     * and the web export.
     */
    private static List<String> axolotlDescription(int tier) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("<gray>아군을 보조하는 지원 타워입니다. 티어가 오를수록 능력이 누적됩니다.</gray>");
        lines.add("<green>{ability.supportIntervalTicks:seconds}마다 반경 {ability.supportRadius:blocks} 안의 아군 타워를 {ability.regenAmount:health}만큼 회복시킵니다.</green>");
        if (tier >= 2) {
            lines.add("<green>같은 범위의 아군 타워 공격 속도를 {ability.attackSpeedBonus:percent} 증가시킵니다.</green>");
        }
        if (tier >= 3) {
            lines.add("<green>범위 안 돌고래 타워의 압력 스택 부여량이 {ability.stackBonus:integer} 증가합니다.</green>");
            lines.add("<green>범위 안 돌고래 타워의 수압 배율이 {ability.waterPressureRatioBonus:percent} 증가합니다.</green>");
        }
        return List.copyOf(lines);
    }

    private static List<String> conduitDescription(String grade) {
        return List.of(
                "<gray>압력을 증폭하는 " + grade + " 전달체입니다. 직접 공격하지 않습니다.</gray>",
                "<green>반경 {ability.amplifyRadius:blocks} 안에서 압력 스택 상한이 {ability.maxStackBonus:integer} 증가합니다.</green>",
                "<green>반경 안의 수압 배율이 {ability.waterPressureRatioBonus:percent} 증가합니다.</green>",
                zoneRule()
        );
    }

    private static String zoneRule() {
        return "<gray>고압 구역은 거북이를 설치하거나 승급할 때 <yellow>몬스터가 오는 쪽</yellow> 경로에 일렬로 깔립니다. 거북이가 늘거나 티어가 오르면 그만큼 벽이 길어집니다.</gray>";
    }

    private static String waterPressureRule() {
        return "<gray>수압 피해는 공격력의 최대 {ability.atlantis_global.waterPressureDamageCap:number}배이며, 몬스터가 죽으면 주변으로 이어집니다. 한 몬스터는 같은 연쇄에서 한 번만 터집니다.</gray>";
    }
}
