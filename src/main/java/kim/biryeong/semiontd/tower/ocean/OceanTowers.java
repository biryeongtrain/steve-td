package kim.biryeong.semiontd.tower.ocean;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.SalmonVisual;
import kim.biryeong.semiontd.entity.visual.TropicalFishVisual;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

public final class OceanTowers {
    public static final TowerType T1_WATER = tower(
            "ocean_water_t1", "물 타워", 25, 70.0, 0.0, 0.0, 20, -10,
            BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
            List.of("<gray>주변 바다 타워에 물을 공급합니다.</gray>")
    );
    public static final TowerType T2_SPRING_WATER = tower(
            "ocean_water_t2", "샘물 타워", 60, 110.0, 0.0, 0.0, 20, -10,
            BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
            List.of("<gray>더 많은 물을 공급하는 물 타워입니다.</gray>")
    );
    public static final TowerType T3_CURRENT = tower(
            "ocean_water_t3", "해류 타워", 150, 180.0, 0.0, 0.0, 20, -10,
            BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
            List.of("<gray>강한 해류로 주변 바다 타워에 물을 공급합니다.</gray>")
    );

    public static final TowerType T1_PUFFERFISH = tower(
            "ocean_pufferfish_t1", "복어 타워", 40, 130.0, 2.4, 5.0, 16, 50,
            byId(EntityType.PUFFERFISH),
            List.of("<gray>피해를 받아 주변 바다 타워에 물을 나눠 줍니다.</gray>")
    );
    public static final TowerType T2_GUARDIAN = tower(
            "ocean_guardian_t2", "가디언 타워", 130, 230.0, 2.6, 9.0, 16, 70,
            byId(EntityType.GUARDIAN),
            List.of("<gray>받은 피해를 물로 전환하는 전방 타워입니다.</gray>")
    );
    public static final TowerType T3_ELDER_GUARDIAN = tower(
            "ocean_elder_guardian_t3", "엘더 가디언 타워", 210, 450.0, 3.0, 16.0, 20, 110,
            byId(EntityType.ELDER_GUARDIAN),
            List.of("<gray>높은 생존력으로 아군의 물을 유지합니다.</gray>")
    );

    public static final TowerType T1_TROPICAL_FISH = tower(
            "ocean_tropical_fish_t1", "열대어 타워", 40, 55.0, 0.0, 0.0, 100, -5,
            TropicalFishVisual.builder()
                    .pattern(TropicalFish.Pattern.KOB)
                    .baseColor(DyeColor.LIGHT_BLUE)
                    .patternColor(DyeColor.WHITE)
                    .build().withScale(0.7),
            List.of("<gray>물을 소비해 주변 타워의 공격을 강화합니다.</gray>")
    );
    public static final TowerType T2_LARGE_TROPICAL_FISH = tower(
            "ocean_tropical_fish_t2", "큰 열대어 타워", 110, 85.0, 0.0, 0.0, 90, -5,
            TropicalFishVisual.builder()
                    .pattern(TropicalFish.Pattern.SUNSTREAK)
                    .baseColor(DyeColor.CYAN)
                    .patternColor(DyeColor.BLUE)
                    .build(),
            List.of("<gray>더 강한 공격력·공격 속도 버프를 제공합니다.</gray>")
    );
    public static final TowerType T3_GIANT_TROPICAL_FISH = tower(
            "ocean_tropical_fish_t3", "거대 열대어 타워", 190, 130.0, 0.0, 0.0, 80, -5,
            TropicalFishVisual.builder()
                    .pattern(TropicalFish.Pattern.BETTY)
                    .baseColor(DyeColor.BLUE)
                    .patternColor(DyeColor.LIGHT_BLUE)
                    .build().withScale(1.3),
            List.of("<gray>넓은 전선을 빠르게 강화하는 지원 타워입니다.</gray>")
    );

    public static final TowerType T1_SALMON = tower(
            "ocean_salmon_t1", "연어 타워", 45, 55.0, 6.5, 6.0, 16, 0,
            SalmonVisual.builder().size(Salmon.Variant.SMALL).build().withScale(0.75),
            List.of("<gray>물을 더 소비해 대상 주변을 공격합니다.</gray>")
    );
    public static final TowerType T2_LARGE_SALMON = tower(
            "ocean_salmon_t2", "큰 연어 타워", 100, 80.0, 7.5, 13.0, 15, 0,
            SalmonVisual.builder().size(Salmon.Variant.MEDIUM).build(),
            List.of("<gray>더 넓은 범위에 높은 피해를 줍니다.</gray>")
    );
    public static final TowerType T3_GIANT_SALMON = tower(
            "ocean_salmon_t3", "거대 연어 타워", 200, 115.0, 8.5, 24.0, 14, 0,
            SalmonVisual.builder().size(Salmon.Variant.LARGE).build().withScale(1.3),
            List.of("<gray>저장한 물로 강한 범위 피해를 가합니다.</gray>")
    );

    public static final TowerType T1_COD = tower(
            "ocean_cod_t1", "대구 타워", 45, 55.0, 8.0, 6.0, 20, 0,
            EntityVisual.builder(byId(EntityType.COD)).scale(0.7).build(),
            List.of("<gray>현재 체력이 가장 높은 적을 우선 공격합니다.</gray>")
    );
    public static final TowerType T2_LARGE_COD = tower(
            "ocean_cod_t2", "큰 대구 타워", 100, 80.0, 11.0, 16.0, 15, 0,
            EntityVisual.builder(byId(EntityType.COD)).build(),
            List.of("<gray>높은 체력의 적과 인컴 몬스터를 노립니다.</gray>")
    );
    public static final TowerType T3_GIANT_COD = tower(
            "ocean_cod_t3", "거대 대구 타워", 210, 115.0, 12.0, 40.0, 12, 0,
            EntityVisual.builder(byId(EntityType.COD)).scale(1.35).build(),
            List.of("<gray>많은 물을 저장할수록 인컴 몬스터에게 강해집니다.</gray>")
    );

    private static final Set<String> WATER_IDS = Set.of(T1_WATER.id(), T2_SPRING_WATER.id(), T3_CURRENT.id());
    private static final Set<String> TANK_IDS = Set.of(T1_PUFFERFISH.id(), T2_GUARDIAN.id(), T3_ELDER_GUARDIAN.id());
    private static final Set<String> SUPPORT_IDS = Set.of(T1_TROPICAL_FISH.id(), T2_LARGE_TROPICAL_FISH.id(), T3_GIANT_TROPICAL_FISH.id());
    private static final Set<String> SPLASH_IDS = Set.of(T1_SALMON.id(), T2_LARGE_SALMON.id(), T3_GIANT_SALMON.id());
    private static final Set<String> HUNTER_IDS = Set.of(T1_COD.id(), T2_LARGE_COD.id(), T3_GIANT_COD.id());

    private OceanTowers() {
    }

    public static boolean isOceanTower(TowerType type) {
        return type != null && (isWaterTower(type) || isCombatTower(type));
    }

    public static boolean isCombatTower(TowerType type) {
        return type != null && (isTank(type) || isSupport(type) || isSplash(type) || isHunter(type));
    }

    public static boolean isWaterTower(TowerType type) {
        return type != null && WATER_IDS.contains(type.id());
    }

    public static boolean isTank(TowerType type) {
        return type != null && TANK_IDS.contains(type.id());
    }

    public static boolean isSupport(TowerType type) {
        return type != null && SUPPORT_IDS.contains(type.id());
    }

    public static boolean isSplash(TowerType type) {
        return type != null && SPLASH_IDS.contains(type.id());
    }

    public static boolean isHunter(TowerType type) {
        return type != null && HUNTER_IDS.contains(type.id());
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 1;
        }
        return type.id().endsWith("_t3") ? 3 : type.id().endsWith("_t2") ? 2 : 1;
    }
}
