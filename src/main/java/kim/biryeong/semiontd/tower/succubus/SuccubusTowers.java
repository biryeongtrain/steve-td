package kim.biryeong.semiontd.tower.succubus;

import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.RabbitVisual;
import kim.biryeong.semiontd.entity.visual.SheepVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

public final class SuccubusTowers {
    public static final TowerType DREAM_DUST_T1 = tower("succubus_dream_dust_t1", "꿈가루 타워", 45, 65, 7, 7, 14, 0, EntityType.ALLAY,
            List.of("<gray>공격을 반복해 적에게 꿈을 쌓습니다.</gray>", "<light_purple>{ability.stackEvery:integer}번째 적중마다 꿈 1스택을 부여합니다.</light_purple>"));
    public static final TowerType DREAM_DUST_T2 = tower("succubus_dream_dust_t2", "짙은 꿈가루 타워", 100, 105, 8, 13, 12, 0, EntityType.ALLAY,
            DREAM_DUST_T1.description());
    public static final TowerType DREAM_DUST_T3 = tower("succubus_dream_dust_t3", "심층 꿈가루 타워", 210, 155, 9, 23, 10, 0, EntityType.ALLAY,
            DREAM_DUST_T1.description());

    private static final EntityVisual PURPLE_SHEEP = SheepVisual.builder().color(DyeColor.PURPLE).build();
    private static final EntityVisual WHITE_RABBIT = RabbitVisual.builder().variant(Rabbit.Variant.WHITE).build();

    public static final TowerType SLEEPWALKER_T1 = tower("succubus_sleepwalker_t1", "몽유 타워", 50, 190, 2.5, 4, 20, 80, PURPLE_SHEEP,
            List.of("<gray>공격을 받아 꿈을 되돌려주는 전방 타워입니다.</gray>",
                    "<green>꿈이 있는 적에게 받는 피해가 {ability.dreamDamageReduction:percent} 감소합니다.</green>",
                    "<light_purple>공격자에게 {ability.counterCooldownTicks:seconds}마다 꿈 {ability.counterStacks:integer}스택을 부여합니다.</light_purple>"));
    public static final TowerType SLEEPWALKER_T2 = tower("succubus_sleepwalker_t2", "깊은 몽유 타워", 110, 340, 2.7, 8, 18, 90, PURPLE_SHEEP,
            SLEEPWALKER_T1.description());
    public static final TowerType SLEEPWALKER_T3 = tower("succubus_sleepwalker_t3", "끝없는 몽유 타워", 230, 620, 3, 14, 16, 100, PURPLE_SHEEP,
            SLEEPWALKER_T1.description());

    public static final TowerType LULLABY_T1 = support("succubus_lullaby_t1", "자장가 타워", 55, 75, 120, WHITE_RABBIT,
            4.5, 2, 3);
    public static final TowerType LULLABY_T2 = support("succubus_lullaby_t2", "꿈결 자장가 타워", 120, 115, 100, WHITE_RABBIT,
            5.0, 3, 5);
    public static final TowerType LULLABY_T3 = support("succubus_lullaby_t3", "심층 자장가 타워", 240, 170, 80, WHITE_RABBIT,
            5.5, 4, 7);

    public static final TowerType NIGHTMARE_T1 = tower("succubus_nightmare_t1", "악몽 타워", 65, 70, 8, 14, 16, 0, EntityType.PHANTOM,
            List.of("<gray>꿈이 깊은 적을 우선 공격하는 마법 타워입니다.</gray>",
                    "<light_purple>꿈 {ability.minimumStacks:integer}스택 이상인 적에게 꿈 1스택을 추가합니다.</light_purple>",
                    "<red>잠든 적에게 주는 피해가 {ability.sleepingDamageBonus:percent} 증가합니다.</red>"));
    public static final TowerType NIGHTMARE_T2 = tower("succubus_nightmare_t2", "가위눌림 타워", 135, 110, 9, 26, 14, 0, EntityType.PHANTOM,
            NIGHTMARE_T1.description());
    public static final TowerType NIGHTMARE_T3 = tower("succubus_nightmare_t3", "심연의 악몽 타워", 270, 160, 10, 46, 12, 0, EntityType.PHANTOM,
            NIGHTMARE_T1.description());

    public static final TowerType SUCCUBUS = TowerType.builder("succubus", "서큐버스 타워")
            .mineralCost(120).maxHealth(160).range(8).damage(18).attackIntervalTicks(16).aggroPriority(20)
            .visual(BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build())
            .primaryDamageType(DamageType.MAGIC)
            .description(List.of(
                    "<gray>단 하나만 존재하는 서큐버스 빌더의 중심 타워입니다.</gray>",
                    "<light_purple>꿈의 효과를 {ability.succubus_global.succubusAmplification:percent} 증폭합니다.</light_purple>",
                    "<light_purple>아군 꿈 1스택당 공격력 {ability.succubus_global.allyDamagePerStack:percent}, 공격속도 {ability.succubus_global.allyAttackSpeedPerStack:percent} 증가. 적 각성 시 수면 중 잃은 체력의 {ability.succubus_global.monsterWakeBonusDamage:percent}를 추가 피해로 줍니다.</light_purple>",
                    "<dark_red>세 번째로 꿈나라에 든 적을 처형합니다.</dark_red>",
                    "<red>직접 처치한 적의 공격력 {ability.succubus_global.absorbAttackRatio:percent}, 최대 체력 {ability.succubus_global.absorbMaxHealthRatio:percent}를 흡수합니다.</red>"
            )).build();

    private static final List<TowerType> ALL = List.of(
            DREAM_DUST_T1, DREAM_DUST_T2, DREAM_DUST_T3,
            SLEEPWALKER_T1, SLEEPWALKER_T2, SLEEPWALKER_T3,
            LULLABY_T1, LULLABY_T2, LULLABY_T3,
            NIGHTMARE_T1, NIGHTMARE_T2, NIGHTMARE_T3,
            SUCCUBUS
    );
    private static final Map<SuccubusRole, Set<String>> IDS = Map.of(
            SuccubusRole.DREAM_DUST, ids(DREAM_DUST_T1, DREAM_DUST_T2, DREAM_DUST_T3),
            SuccubusRole.SLEEPWALKER, ids(SLEEPWALKER_T1, SLEEPWALKER_T2, SLEEPWALKER_T3),
            SuccubusRole.LULLABY, ids(LULLABY_T1, LULLABY_T2, LULLABY_T3),
            SuccubusRole.NIGHTMARE, ids(NIGHTMARE_T1, NIGHTMARE_T2, NIGHTMARE_T3),
            SuccubusRole.SUCCUBUS, ids(SUCCUBUS)
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private SuccubusTowers() {
    }

    public static List<TowerType> all() {return ALL;}
    public static boolean isSuccubusTower(TowerType type) {return roleOf(type) != null;}
    public static boolean isSuccubus(TowerType type) {return roleOf(type) == SuccubusRole.SUCCUBUS;}

    public static SuccubusRole roleOf(TowerType type) {
        if (type == null) return null;
        return IDS.entrySet().stream().filter(entry -> entry.getValue().contains(type.id()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public static int tier(TowerType type) {
        if (type == null || isSuccubus(type)) return 1;
        return type.id().endsWith("_t3") ? 3 : type.id().endsWith("_t2") ? 2 : 1;
    }

    private static TowerType tower(String id, String name, long cost, double health, double range, double damage,
                                   int interval, int aggro, EntityType<?> entity, List<String> description) {
        return tower(id, name, cost, health, range, damage, interval, aggro,
                EntityVisual.builder(byId(entity)).build(), description);
    }

    private static TowerType tower(String id, String name, long cost, double health, double range, double damage,
                                   int interval, int aggro, EntityVisual visual, List<String> description) {
        return TowerType.builder(id, name).mineralCost(cost).maxHealth(health).range(range).damage(damage)
                .attackIntervalTicks(interval).aggroPriority(aggro)
                .visual(visual).primaryDamageType(DamageType.MAGIC)
                .description(description).build();
    }

    private static TowerType support(String id, String name, long cost, double health, int interval,
                                     EntityVisual visual, double radius, int allyTargets, int enemyTargets) {
        return tower(id, name, cost, health, 0, 0, interval, -5, visual, List.of(
                "<gray>주변 아군과 적에게 함께 꿈을 들려주는 지원 타워입니다.</gray>",
                "<light_purple>{ability.pulseIntervalTicks:seconds}마다 반경 {ability.radius:blocks}의 아군 {ability.allyMaxTargets:integer}기와 적 {ability.enemyMaxTargets:integer}기에게 꿈 1스택을 부여합니다.</light_purple>"
        ));
    }

    private static Set<String> ids(TowerType... types) {
        return Arrays.stream(types).map(TowerType::id).collect(Collectors.toUnmodifiableSet());
    }
}
