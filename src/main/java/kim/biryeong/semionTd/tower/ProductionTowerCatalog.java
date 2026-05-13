package kim.biryeong.semiontd.tower;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProductionTowerCatalog {
    public static final TowerType VILLAGER_CROSSBOW_POST = new TowerType(
            "villager_crossbow_post", "주민 쇠뇌 초소", TowerCategory.DIRECT,
            90, 65.0, 9.0, 10.0, 18, 0, "minecraft:villager"
    );
    public static final TowerType VILLAGER_BELL_MORTAR = new TowerType(
            "villager_bell_mortar", "주민 종 포대", TowerCategory.DIRECT,
            160, 80.0, 11.0, 15.0, 28, 5, "minecraft:iron_golem"
    );
    public static final TowerType VILLAGER_EMERALD_LENS = new TowerType(
            "villager_emerald_lens", "주민 에메랄드 렌즈", TowerCategory.DIRECT,
            240, 70.0, 15.0, 28.0, 34, -5, "minecraft:villager"
    );

    public static final TowerType UNDEAD_BONE_SPITTER = new TowerType(
            "undead_bone_spitter", "언데드 뼈 발사기", TowerCategory.DIRECT,
            95, 70.0, 9.0, 9.0, 17, 0, "minecraft:skeleton"
    );
    public static final TowerType UNDEAD_GRAVE_BOMBARD = new TowerType(
            "undead_grave_bombard", "언데드 묘지 폭격기", TowerCategory.DIRECT,
            170, 85.0, 10.0, 14.0, 30, 10, "minecraft:zombie"
    );
    public static final TowerType UNDEAD_SOUL_REAPER = new TowerType(
            "undead_soul_reaper", "언데드 영혼 수확자", TowerCategory.DIRECT,
            250, 90.0, 12.0, 24.0, 32, 5, "minecraft:wither_skeleton"
    );

    public static final TowerType BEAST_WOLF_DEN = new TowerType(
            "beast_wolf_den", "동물 늑대 소굴", TowerCategory.DIRECT,
            90, 72.0, 8.0, 8.0, 14, 5, "minecraft:wolf"
    );
    public static final TowerType BEAST_BOAR_CRASHER = new TowerType(
            "beast_boar_crasher", "동물 멧돼지 돌격대", TowerCategory.DIRECT,
            165, 110.0, 8.0, 17.0, 24, 30, "minecraft:pig"
    );
    public static final TowerType BEAST_HAWK_ROOST = new TowerType(
            "beast_hawk_roost", "동물 매 둥지", TowerCategory.DIRECT,
            220, 62.0, 18.0, 20.0, 24, -10, "minecraft:parrot"
    );

    private static final Map<String, CatalogEntry> ENTRIES = new LinkedHashMap<>();

    static {
        register(VILLAGER_CROSSBOW_POST, new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 1.25, 0.35, 8, 0.06, 0.0, false, true, 0.0, 0.0));
        register(VILLAGER_BELL_MORTAR, new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 2.75, 0.55, 8, 0.04, 0.0, true, false, 0.0, 0.0));
        register(VILLAGER_EMERALD_LENS, new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 1.75, 0.45, 6, 0.09, 0.0, false, true, 0.0, 0.0));

        register(UNDEAD_BONE_SPITTER, new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 1.75, 0.45, 5, 0.04, 0.0, true, false, 1.5, 0.35));
        register(UNDEAD_GRAVE_BOMBARD, new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 3.25, 0.65, 5, 0.03, 0.0, true, false, 2.5, 0.55));
        register(UNDEAD_SOUL_REAPER, new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 2.0, 0.5, 6, 0.07, 0.0, true, true, 2.0, 0.45));

        register(BEAST_WOLF_DEN, new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 1.0, 0.3, 7, 0.0, 0.045, true, false, 0.0, 0.0));
        register(BEAST_BOAR_CRASHER, new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 2.75, 0.55, 7, 0.03, 0.025, true, false, 0.0, 0.0));
        register(BEAST_HAWK_ROOST, new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 1.75, 0.45, 7, 0.02, 0.035, true, false, 0.0, 0.0));
    }

    private ProductionTowerCatalog() {
    }

    public static Optional<CatalogEntry> find(String towerId) {
        return Optional.ofNullable(ENTRIES.get(towerId));
    }

    public static Collection<CatalogEntry> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static List<CatalogEntry> forFaction(TowerFaction faction) {
        return ENTRIES.values().stream()
                .filter(entry -> entry.behavior().faction() == faction)
                .toList();
    }

    public static Optional<ProductionTowerBehavior> behavior(TowerType type) {
        return type == null ? Optional.empty() : find(type.id()).map(CatalogEntry::behavior);
    }

    private static void register(TowerType type, ProductionTowerBehavior behavior) {
        ENTRIES.put(type.id(), new CatalogEntry(type, behavior));
    }

    public record CatalogEntry(TowerType type, ProductionTowerBehavior behavior) {
    }
}
