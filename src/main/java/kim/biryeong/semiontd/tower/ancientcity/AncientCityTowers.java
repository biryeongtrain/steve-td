package kim.biryeong.semiontd.tower.ancientcity;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

public final class AncientCityTowers {
    public static final TowerType CATALYST_T1 = magicTower(
            "ancient_city_catalyst_t1", "스컬크 촉매 타워", 50, 145.0, 2.5, 3.0, 20, 50,
            BlockDisplayVisual.builder(Blocks.SCULK_CATALYST.defaultBlockState()).scale(0.65).build(),
            catalystDescription("기본")
    );
    public static final TowerType CATALYST_T2 = magicTower(
            "ancient_city_catalyst_t2", "강화 스컬크 촉매 타워", 110, 310.0, 3.0, 5.0, 18, 80,
            BlockDisplayVisual.builder(Blocks.SCULK_CATALYST.defaultBlockState()).scale(0.8).build(),
            catalystDescription("중급")
    );
    public static final TowerType CATALYST_T3 = magicTower(
            "ancient_city_catalyst_t3", "고대 스컬크 촉매 타워", 230, 650.0, 3.5, 8.0, 16, 110,
            BlockDisplayVisual.builder(Blocks.SCULK_CATALYST.defaultBlockState()).scale(0.95).build(),
            catalystDescription("최종")
    );

    public static final TowerType SENSOR_T1 = magicTower(
            "ancient_city_sensor_t1", "스컬크 감지체 타워", 45, 50.0, 8.0, 2.0, 20, -10,
            BlockDisplayVisual.builder(Blocks.SCULK_SENSOR.defaultBlockState()).scale(0.65).build(),
            sensorDescription("기본")
    );
    public static final TowerType SENSOR_T2 = magicTower(
            "ancient_city_sensor_t2", "정밀 스컬크 감지체 타워", 90, 85.0, 9.0, 4.0, 18, -10,
            BlockDisplayVisual.builder(Blocks.CALIBRATED_SCULK_SENSOR.defaultBlockState()).scale(0.8).build(),
            sensorDescription("중급")
    );
    public static final TowerType SENSOR_T3 = magicTower(
            "ancient_city_sensor_t3", "고대 스컬크 감지체 타워", 190, 130.0, 10.0, 5.0, 16, -10,
            BlockDisplayVisual.builder(Blocks.CALIBRATED_SCULK_SENSOR.defaultBlockState()).scale(0.95).build(),
            sensorDescription("최종")
    );

    public static final TowerType SHRIEKER_T1 = magicTower(
            "ancient_city_shrieker_t1", "스컬크 비명체 타워", 55, 70.0, 6.0, 2.0, 20, 0,
            BlockDisplayVisual.builder(Blocks.SCULK_SHRIEKER.defaultBlockState()).scale(0.65).build(),
            shriekerDescription("기본")
    );
    public static final TowerType SHRIEKER_T2 = magicTower(
            "ancient_city_shrieker_t2", "강화 스컬크 비명체 타워", 110, 120.0, 7.0, 4.0, 18, 0,
            BlockDisplayVisual.builder(Blocks.SCULK_SHRIEKER.defaultBlockState()).scale(0.8).build(),
            shriekerDescription("중급")
    );
    public static final TowerType SHRIEKER_T3 = magicTower(
            "ancient_city_shrieker_t3", "고대 스컬크 비명체 타워", 220, 180.0, 8.0, 7.0, 16, 0,
            BlockDisplayVisual.builder(Blocks.SCULK_SHRIEKER.defaultBlockState()).scale(0.95).build(),
            shriekerDescription("최종")
    );

    public static final TowerType WARDEN_T1 = magicTower(
            "ancient_city_warden_t1", "워든 타워", 110, 120.0, 6.5, 4.0, 20, 0,
            EntityVisual.builder(byId(EntityType.WARDEN)).scale(0.22).build(),
            wardenDescription("기본")
    );
    public static final TowerType WARDEN_T2 = magicTower(
            "ancient_city_warden_t2", "강화 워든 타워", 160, 220.0, 7.5, 8.0, 18, 0,
            EntityVisual.builder(byId(EntityType.WARDEN)).scale(0.28).build(),
            wardenDescription("중급")
    );
    public static final TowerType WARDEN_T3 = magicTower(
            "ancient_city_warden_t3", "고대 워든 타워", 300, 360.0, 9.0, 12.0, 16, 0,
            EntityVisual.builder(byId(EntityType.WARDEN)).scale(0.33).build(),
            wardenDescription("최종")
    );
    public static final TowerType WARDEN_T4 = magicTower(
            "ancient_city_warden_t4", "심층 워든 타워", 650, 240.0, 9.5, 18.0, 14, 0,
            EntityVisual.builder(byId(EntityType.WARDEN)).scale(0.38).build(),
            wardenDescription("초월")
    );

    private static final Set<String> CATALYST_IDS = ids(CATALYST_T1, CATALYST_T2, CATALYST_T3);
    private static final Set<String> SENSOR_IDS = ids(SENSOR_T1, SENSOR_T2, SENSOR_T3);
    private static final Set<String> SHRIEKER_IDS = ids(SHRIEKER_T1, SHRIEKER_T2, SHRIEKER_T3);
    private static final Set<String> WARDEN_IDS = ids(WARDEN_T1, WARDEN_T2, WARDEN_T3, WARDEN_T4);
    private static final List<TowerType> ALL = List.of(
            CATALYST_T1, CATALYST_T2, CATALYST_T3,
            SENSOR_T1, SENSOR_T2, SENSOR_T3,
            SHRIEKER_T1, SHRIEKER_T2, SHRIEKER_T3,
            WARDEN_T1, WARDEN_T2, WARDEN_T3, WARDEN_T4
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private AncientCityTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isAncientCityTower(TowerType type) {
        return roleOf(type) != null;
    }

    public static AncientCityRole roleOf(TowerType type) {
        if (type == null) {
            return null;
        }
        String id = type.id();
        if (CATALYST_IDS.contains(id)) {
            return AncientCityRole.CATALYST;
        }
        if (SENSOR_IDS.contains(id)) {
            return AncientCityRole.SENSOR;
        }
        if (SHRIEKER_IDS.contains(id)) {
            return AncientCityRole.SHRIEKER;
        }
        return WARDEN_IDS.contains(id) ? AncientCityRole.WARDEN : null;
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 1;
        }
        return type.id().endsWith("_t4") ? 4 : type.id().endsWith("_t3") ? 3 : type.id().endsWith("_t2") ? 2 : 1;
    }

    private static TowerType magicTower(
            String id,
            String displayName,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority,
            EntityVisual visual,
            List<String> description
    ) {
        return tower(
                id,
                displayName,
                mineralCost,
                maxHealth,
                range,
                damage,
                attackIntervalTicks,
                aggroPriority,
                visual,
                description
        ).withPrimaryDamageType(DamageType.MAGIC);
    }

    private static Set<String> ids(TowerType... types) {
        return java.util.Arrays.stream(types).map(TowerType::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<String> catalystDescription(String grade) {
        return List.of(
                "<gray>높은 체력과 어그로로 전방을 지키는 " + grade + " 촉매 타워입니다.</gray>",
                "<green>생존 중 피격 시 반경 {ability.retaliationRadius:blocks}에 마법 피해 {ability.magicDamage:number}를 줍니다. 최대 {ability.retaliationCooldownTicks:seconds}에 한 번 발동합니다.</green>",
                "<green>스컬크 공명이 활성화되면 받는 피해가 {ability.sculkDamageReduction:percent} 감소합니다.</green>",
                resonanceRule()
        );
    }

    private static List<String> sensorDescription(String grade) {
        return List.of(
                "<gray>적을 감지하고 후속 마법 피해를 증폭하는 " + grade + " 지원 공격 타워입니다.</gray>",
                "<green>{ability.magicCooldownTicks:seconds}마다 대상에게 마법 피해 {ability.magicDamage:number}를 주고 {ability.markDurationTicks:seconds} 동안 감지 표식을 남깁니다.</green>",
                "<green>표식 대상은 이 소유자의 고대 도시 마법 피해를 {ability.markDamageBonus:percent} 더 받습니다. 표식을 남기는 최초 피해에는 적용되지 않으며, 여러 표식은 가장 강한 효과만 적용됩니다.</green>",
                resonanceRule()
        );
    }

    private static List<String> shriekerDescription(String grade) {
        return List.of(
                "<gray>비명 파동으로 적 무리를 제압하는 " + grade + " 범위 공격 타워입니다.</gray>",
                "<green>{ability.magicCooldownTicks:seconds}마다 대상 주변 {ability.magicRadius:blocks}에 마법 피해 {ability.magicDamage:number}를 줍니다.</green>",
                "<green>적의 이동속도를 {ability.slowMagnitude:percent}만큼 {ability.slowDurationTicks:seconds} 동안 감소시킵니다.</green>",
                resonanceRule()
        );
    }

    private static List<String> wardenDescription(String grade) {
        return List.of(
                "<gray>최대 체력이 높은 적을 우선해 소닉 붐을 발사하는 " + grade + " 마법 공격 타워입니다.</gray>",
                "<green>{ability.magicCooldownTicks:seconds}마다 최대 체력이 높은 적부터 {ability.targetCount:integer}기에게 주 대상 마법 피해 {ability.magicDamage:number}를 줍니다.</green>",
                "<green>보조 대상은 {ability.secondaryDamageRatio:percent}의 피해를 받고 감지 표식 증폭은 받지 않습니다. 스컬크 공명 활성 중에는 대상이 {ability.sculkExtraTargets:integer}기 증가합니다.</green>",
                resonanceRule()
        );
    }

    private static String resonanceRule() {
        return "<aqua>현재 위치가 자신의 스컬크 영토면 공명 피해 증가를 받습니다. 최종 방어선에서도 재생성된 스컬크 위에 있어야 합니다.</aqua>";
    }
}
