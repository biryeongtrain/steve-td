package kim.biryeong.semiontd.tower.insect;

import java.util.List;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.level.block.Blocks;

public final class InsectTowers {
    public static final TowerType SILVERFISH = unit(
            "insect_silverfish_t1", "좀벌레", 30, 90, 2.5, 7, 15, 35,
            EntityVisual.vanilla("minecraft:silverfish"), 1, UnitLine.SILVERFISH);
    public static final TowerType ENDERMITE = unit(
            "insect_endermite_t2", "엔더마이트", 0, 170, 2.7, 14, 13, 40,
            EntityVisual.vanilla("minecraft:endermite"), 2, UnitLine.SILVERFISH);
    public static final TowerType ENHANCED_ENDERMITE = unit(
            "insect_endermite_t3", "강화 엔더마이트", 0, 300, 3, 26, 11, 45,
            EntityVisual.builder("minecraft:endermite").scale(1.20).build(), 3, UnitLine.SILVERFISH);

    public static final TowerType CAVE_SPIDER = unit(
            "insect_cave_spider_t1", "동굴거미", 40, 150, 2.4, 4, 20, 80,
            EntityVisual.vanilla("minecraft:cave_spider"), 1, UnitLine.SPIDER);
    public static final TowerType SPIDER = unit(
            "insect_spider_t2", "거미", 0, 300, 2.5, 7, 18, 100,
            EntityVisual.vanilla("minecraft:spider"), 2, UnitLine.SPIDER);
    public static final TowerType ENHANCED_SPIDER = unit(
            "insect_spider_t3", "강화 거미", 0, 540, 2.7, 12, 16, 120,
            EntityVisual.builder("minecraft:spider").scale(1.20).build(), 3, UnitLine.SPIDER);

    public static final TowerType BEE = unit(
            "insect_bee_t1", "벌", 40, 50, 7, 7, 16, 0,
            EntityVisual.builder("minecraft:bee").scale(0.75).build(), 1, UnitLine.BEE);
    public static final TowerType ENHANCED_BEE = unit(
            "insect_bee_t2", "강화 벌", 0, 90, 8, 12, 13, 0,
            EntityVisual.builder("minecraft:bee").scale(0.95).build(), 2, UnitLine.BEE);
    public static final TowerType QUEEN_BEE = unit(
            "insect_bee_t3", "여왕벌", 0, 150, 9, 21, 10, 0,
            EntityVisual.builder("minecraft:bee").scale(1.20).build(), 3, UnitLine.BEE);

    public static final TowerType SPAWNER = TowerType.builder("insect_spawner", "스포너")
            .mineralCost(45)
            .maxHealth(280)
            .range(0)
            .damage(0)
            .attackIntervalTicks(20)
            .aggroPriority(20)
            .visual(BlockDisplayVisual.builder(Blocks.SPAWNER.defaultBlockState()).build())
            .description(List.of(
                    "<light_purple>스포너</light_purple> 반경 {ability.insect_spawner.reviveRadius:blocks} 안에서 죽은 자기 벌레를 되살립니다.",
                    "<red>스포너가 파괴되면 연결된 부활 대기도 즉시 취소됩니다.</red>"
            ))
            .build();

    private static final List<TowerType> ALL = List.of(
            SILVERFISH, ENDERMITE, ENHANCED_ENDERMITE,
            CAVE_SPIDER, SPIDER, ENHANCED_SPIDER,
            BEE, ENHANCED_BEE, QUEEN_BEE, SPAWNER
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private InsectTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isInsectTower(TowerType type) {
        return type != null && ALL.stream().anyMatch(candidate -> candidate.id().equals(type.id()));
    }

    public static boolean isSpawner(TowerType type) {
        return type != null && SPAWNER.id().equals(type.id());
    }

    public static boolean isCombatUnit(TowerType type) {
        return isInsectTower(type) && !isSpawner(type);
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 0;
        }
        if (List.of(SILVERFISH, CAVE_SPIDER, BEE).stream().anyMatch(value -> value.id().equals(type.id()))) {
            return 1;
        }
        if (List.of(ENDERMITE, SPIDER, ENHANCED_BEE).stream().anyMatch(value -> value.id().equals(type.id()))) {
            return 2;
        }
        return isCombatUnit(type) ? 3 : 0;
    }

    public static UnitLine line(TowerType type) {
        if (type == null) {
            return null;
        }
        if (List.of(SILVERFISH, ENDERMITE, ENHANCED_ENDERMITE).stream()
                .anyMatch(value -> value.id().equals(type.id()))) {
            return UnitLine.SILVERFISH;
        }
        if (List.of(CAVE_SPIDER, SPIDER, ENHANCED_SPIDER).stream()
                .anyMatch(value -> value.id().equals(type.id()))) {
            return UnitLine.SPIDER;
        }
        return List.of(BEE, ENHANCED_BEE, QUEEN_BEE).stream()
                .anyMatch(value -> value.id().equals(type.id())) ? UnitLine.BEE : null;
    }

    public static TowerType spider(int tier) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> CAVE_SPIDER;
            case 2 -> SPIDER;
            default -> ENHANCED_SPIDER;
        };
    }

    private static TowerType unit(
            String id,
            String name,
            long cost,
            double health,
            double range,
            double damage,
            int interval,
            int aggro,
            EntityVisual visual,
            int tier,
            UnitLine line
    ) {
        java.util.ArrayList<String> description = new java.util.ArrayList<>();
        description.add("<light_purple>스포너</light_purple> 근처에서 죽으면 더 긴 대기시간을 거쳐 계속 <green>부활</green>합니다.");
        description.add("<red>죽을 때마다 이번 라운드에 받는 피해가 {ability.insect_global.deathDamageTakenPerStack:percent} 증가합니다.</red>");
        if (tier == 1) {
            description.add("<gold>첫 배치</gold> 웨이브에는 최대 체력과 기본공격 피해가 {ability.insect_global.freshPowerMultiplier:number}배입니다.");
        }
        if (line == UnitLine.SPIDER) {
            description.add("<gray>받는 피해를 {ability." + id + ".damageReduction:percent} 감소시키는 탱커입니다.</gray>");
        }
        return TowerType.builder(id, name)
                .mineralCost(cost)
                .maxHealth(health)
                .range(range)
                .damage(damage)
                .attackIntervalTicks(interval)
                .aggroPriority(aggro)
                .visual(visual)
                .description(List.copyOf(description))
                .build();
    }

    public enum UnitLine {
        SILVERFISH,
        SPIDER,
        BEE
    }
}
