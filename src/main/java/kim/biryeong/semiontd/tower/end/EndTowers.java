package kim.biryeong.semiontd.tower.end;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.ShulkerVisual;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.*;
import static kim.biryeong.semiontd.tower.end.EndFormatting.endText;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

public final class EndTowers {
    public static final String CONFIG_ID = "end_global";

    public static final EntityVisual DRAGON_EGG_VISUAL = BlockDisplayVisual.builder(Blocks.DRAGON_EGG.defaultBlockState()).build();
    public static final EntityVisual PHANTOM_VISUAL = EntityVisual.builder(byId(EntityType.PHANTOM)).build();
    public static final EntityVisual DRAGON_VISUAL = EntityVisual.builder(byId(EntityType.ENDER_DRAGON)).build();

    public static final TowerType BASE_END_TOWER = TowerType.builder("base_ender_dragon", "엔더 드래곤")
            .mineralCost(0)
            .maxHealth(200.0)
            .range(5.0)
            .damage(10.0)
            .attackIntervalTicks(15)
            .aggroPriority(100)
            .visual(DRAGON_EGG_VISUAL)
            .description(dragonDescription())
            .build();

    public static final TowerType T1_SHULKER_TOWER = TowerType.builder("t1_shulker_tower", "셜커")
            .mineralCost(50)
            .maxHealth(100)
            .range(0)
            .damage(5)
            .attackIntervalTicks(20)
            .aggroPriority(10)
            .visual(EntityVisual.vanilla(byId(EntityType.SHULKER)))
            .description(shulkerLineDescription("낮은", "셜커"))
            .build();

    public static final TowerType T2_SHULKER_TOWER = TowerType.builder("t2_shulker_tower", "견고한 셜커")
            .mineralCost(100)
            .maxHealth(150)
            .range(0)
            .damage(5)
            .attackIntervalTicks(20)
            .aggroPriority(10)
            .visual(ShulkerVisual.builder().color(DyeColor.PURPLE).build())
            .description(shulkerLineDescription("보통인", "견고한 셜커"))
            .build();

    public static final TowerType T3_SHULKER_TOWER = TowerType.builder("t3_shulker_tower", "완강한 셜커")
            .mineralCost(150)
            .maxHealth(200)
            .range(0)
            .damage(5)
            .attackIntervalTicks(20)
            .aggroPriority(10)
            .visual(ShulkerVisual.builder().color(DyeColor.BLACK).build())
            .description(shulkerLineDescription("높은", "완강한 셜커"))
            .build();

    public static final TowerType T1_ENDERMITE_TOWER = TowerType.builder("t1_endermite_tower", "엔더마이트")
            .mineralCost(50)
            .maxHealth(50)
            .range(0)
            .damage(10)
            .attackIntervalTicks(20)
            .aggroPriority(10)
            .visual(EntityVisual.vanilla(byId(EntityType.ENDERMITE)))
            .description(endCrystalLineDescription("낮은", "엔더마이트"))
            .build();

    public static final TowerType T2_ENDERMAN_TOWER = TowerType.builder("t2_enderman_tower", "엔더맨")
            .mineralCost(100)
            .maxHealth(50)
            .range(0)
            .damage(15)
            .attackIntervalTicks(20)
            .aggroPriority(10)
            .visual(EntityVisual.vanilla(byId(EntityType.ENDERMAN)))
            .description(endCrystalLineDescription("보통인", "엔더맨"))
            .build();

    public static final TowerType T3_END_CRYSTAL_TOWER = TowerType.builder("t3_end_crystal_tower", "엔드 수정")
            .mineralCost(150)
            .maxHealth(50)
            .range(0)
            .damage(20)
            .attackIntervalTicks(20)
            .aggroPriority(10)
            .visual(EntityVisual.vanilla(byId(EntityType.END_CRYSTAL)))
            .description(endCrystalLineDescription("높은", "엔드 수정"))
            .build();

    private static final List<TowerType> ALL = List.of(
            BASE_END_TOWER,
            T1_SHULKER_TOWER,
            T2_SHULKER_TOWER,
            T3_SHULKER_TOWER,
            T1_ENDERMITE_TOWER,
            T2_ENDERMAN_TOWER,
            T3_END_CRYSTAL_TOWER
    );

    private static final Set<String> ENDER_TOWER_IDS = Set.of(
            BASE_END_TOWER.id(),
            T1_SHULKER_TOWER.id(),
            T2_SHULKER_TOWER.id(),
            T3_SHULKER_TOWER.id(),
            T1_ENDERMITE_TOWER.id(),
            T2_ENDERMAN_TOWER.id(),
            T3_END_CRYSTAL_TOWER.id()
    );

    private static final Set<String> SHULKER_LINE_IDS = Set.of(
            T1_SHULKER_TOWER.id(), T2_SHULKER_TOWER.id(), T3_SHULKER_TOWER.id()
    );
    private static final Set<String> END_CRYSTAL_LINE_IDS = Set.of(
            T1_ENDERMITE_TOWER.id(), T2_ENDERMAN_TOWER.id(), T3_END_CRYSTAL_TOWER.id()
    );

    static {
        TowerDescriptionRegistry.registerTemplate(BASE_END_TOWER, BASE_END_TOWER.description());
        TowerDescriptionRegistry.registerTemplate(T1_SHULKER_TOWER, T1_SHULKER_TOWER.description());
        TowerDescriptionRegistry.registerTemplate(T2_SHULKER_TOWER, T2_SHULKER_TOWER.description());
        TowerDescriptionRegistry.registerTemplate(T3_SHULKER_TOWER, T3_SHULKER_TOWER.description());
        TowerDescriptionRegistry.registerTemplate(T1_ENDERMITE_TOWER, T1_ENDERMITE_TOWER.description());
        TowerDescriptionRegistry.registerTemplate(T2_ENDERMAN_TOWER, T2_ENDERMAN_TOWER.description());
        TowerDescriptionRegistry.registerTemplate(T3_END_CRYSTAL_TOWER, T3_END_CRYSTAL_TOWER.description());
    }

    private EndTowers() {
    }

    private static List<String> dragonDescription() {
        return List.of(
                "<gray>알로 소환되며, 라운드 시작 시 " + endText("아기 드래곤") + "으로 변합니다.</gray>",
                "<gray>" + endText("아기 드래곤") + " 크기는 " + healthText("최대 체력 " + ability(PHANTOM_SCALE_HEALTH, "integer")) + "당 " + ability(PHANTOM_SCALE_STEP, "number") + "씩 증가합니다.</gray>",
                "<gray>" + healthText("최대 체력 " + ability(DRAGON_EVOLUTION, "integer")) + " 이상이면 " + endText("엔더 드래곤") + "으로 진화합니다.</gray>",
                "<gray>" + endText("엔더 드래곤") + "으로 진화하면 추가 능력을 획득합니다.</gray>",
                "<gray>힘 전달 " + ability(TRANSFER_TICKS, "seconds") + " 후 타워 사망, " + healthText("체력 " + ability(TRANSFER_HEAL, "integer")) + "을 회복합니다.</gray>",
                "<gray>전달 중인 셜커 타워의 " + healthText("최대 체력") + "의 " + healthText(ability(TRANSFER_HEAL_RATIO, "percent")) + "를 초당 회복합니다.</gray>",
                "<gray>타워 " + healthText("체력") + "의 " + healthText(ability(ROUND_HEALTH_RATIO, "percent")) + "를 임시 획득, " + healthText(ability(PERMANENT_HEALTH_RATIO, "percent")) + " 영구 누적</gray>",
                "<gray>타워 " + attackDamageText("피해") + "의 " + attackDamageText(ability(ROUND_DAMAGE_RATIO, "percent")) + "를 임시 획득, " + attackDamageText(ability(PERMANENT_DAMAGE_RATIO, "percent")) + " 영구 누적</gray>",
                "<gray>능력치는 높아질수록 증가 효율이 감소합니다.</gray>"
        );
    }

    private static List<String> shulkerLineDescription(String level, String name) {
        return List.of(
                "<gray>" + healthText("체력") + "이 " + level + " " + name + "입니다.</gray>",
                "<gray>받는 " + damageReductionText("피해") + "를 " + damageReductionText("{ability.damageReduction:percent} 감소") + "시킵니다.</gray>",
                "<gray>이 타워는 공격을 하지 않습니다.</gray>",
                "<gray>힘 전달을 완료하면 " + endText("엔더 드래곤") + "의 " + healthText("체력") + "을 강화합니다.</gray>"
        );
    }

    private static List<String> endCrystalLineDescription(String level, String name) {
        return List.of(
                "<gray>" + attackDamageText("피해") + "가 " + level + " " + name + "입니다.</gray>",
                "<gray>이 타워는 공격을 하지 않습니다.</gray>",
                "<gray>힘 전달을 완료하면 " + endText("엔더 드래곤") + "의 " + attackDamageText("피해") + "를 강화합니다.</gray>"
        );
    }

    private static String ability(EndConfig.Ability ability, String format) {
        return "{ability." + CONFIG_ID + "." + ability.key() + ":" + format + "}";
    }

    public static boolean isEndTower(TowerType type) {
        return type != null && ENDER_TOWER_IDS.contains(type.id());
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isBaseEndTower(TowerType type) {
        return type != null && type.id().equals(BASE_END_TOWER.id());
    }

    public static boolean isShulkerLine(TowerType type) {
        return type != null && SHULKER_LINE_IDS.contains(type.id());
    }

    public static boolean isEndCrystalLine(TowerType type) {
        return type != null && END_CRYSTAL_LINE_IDS.contains(type.id());
    }

    public static boolean isTransferableTower(TowerType type) {
        return isShulkerLine(type) || isEndCrystalLine(type);
    }

    public static int transferTier(TowerType type) {
        return ProductionTowerCatalog.entry(type).map(ProductionTowerCatalog.CatalogEntry::tier).orElse(0);
    }
}
