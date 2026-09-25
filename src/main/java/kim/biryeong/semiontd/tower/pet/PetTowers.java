package kim.biryeong.semiontd.tower.pet;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.visual.CatVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.ParrotVisual;
import kim.biryeong.semiontd.entity.visual.WolfVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.animal.CatVariants;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public final class PetTowers {
    private static final String OWNER_INTRO = "<gray>스스로 싸우지 않고 마당(주변 1칸)의 반려를 키우는 주인입니다.</gray>";
    private static final String OWNER_GRANT = "<aqua>라운드마다 마당의 반려에게 유대를 나눠줍니다. 반려가 적을수록 한 마리가 많이 받습니다.</aqua>";

    public static final TowerType BUTLER_T1 = owner("t1_pet_butler", "집사", 90, 120, List.of(
            OWNER_INTRO,
            OWNER_GRANT,
            "<light_purple>반려 한 마리에게 헌신합니다. 유대를 가장 빠르게 쌓아 승급이 이릅니다.</light_purple>"
    ));
    public static final TowerType BUTLER_T2 = owner("t2_pet_butler", "헌신하는 집사", 220, 240, BUTLER_T1.description());

    public static final TowerType TRAINER_T1 = owner("t1_pet_trainer", "훈련사", 80, 140, List.of(
            OWNER_INTRO,
            OWNER_GRANT,
            "<green>가르쳐서 더 멀리 보냅니다. 마당의 반려는 유대 상한이 크게 늘어납니다.</green>"
    ));
    public static final TowerType TRAINER_T2 = owner("t2_pet_trainer", "베테랑 훈련사", 200, 280, TRAINER_T1.description());

    public static final TowerType KEEPER_T1 = owner("t1_pet_keeper", "사육사", 70, 160, List.of(
            OWNER_INTRO,
            OWNER_GRANT,
            "<yellow>여럿을 두루 돌봅니다. 마당이 가득 차도 유대 지급이 크게 줄지 않습니다.</yellow>"
    ));
    public static final TowerType KEEPER_T2 = owner("t2_pet_keeper", "숙련 사육사", 190, 320, KEEPER_T1.description());

    private static final List<String> DOG_DESC = List.of(
            "<gray>앞에 서서 버티는 반려입니다. 개끼리 붙여 두면 무리로 이어집니다.</gray>",
            "<green>같은 무리의 다른 개 한 마리당 공격력과 체력이 각각 {ability.packDamagePerPackMate:percent}, {ability.packHealthPerPackMate:percent} 증가합니다. 상한은 없습니다.</green>",
            "<aqua>성체가 되면 받는 피해가 {ability.adultDamageReduction:percent} 감소합니다.</aqua>"
    );
    public static final TowerType DOG_T1 = companion("t1_pet_dog", "강아지 타워", 45, 180, 2.5, 9, 18, 45,
            WolfVisual.builder().variant(WolfVariants.SPOTTED).tame(true).collarColor(DyeColor.LIME).build(), DOG_DESC);
    public static final TowerType DOG_T2 = companion("t2_pet_dog", "반려견 타워", 110, 420, 2.8, 17, 17, 55,
            WolfVisual.builder().variant(WolfVariants.CHESTNUT).tame(true).collarColor(DyeColor.ORANGE).build(), DOG_DESC);
    public static final TowerType DOG_T3 = companion("t3_pet_dog", "충견 타워", 240, 780, 3.0, 28, 16, 65,
            WolfVisual.builder().variant(WolfVariants.BLACK).tame(true).collarColor(DyeColor.RED).build(), DOG_DESC);

    private static final List<String> CAT_DESC = List.of(
            "<gray>혼자일수록 강해지는 반려입니다.</gray>",
            "<light_purple>같은 마당에 다른 고양이가 없으면 공격력이 {ability.soloDamageBonus:percent} 증가합니다.</light_purple>",
            "<aqua>성체가 되면 대상 주변 {ability.adultSplashRadius:blocks} 내 추가로 최대 {ability.adultSplashMaxTargets:integer}마리에게 피해의 {ability.adultSplashDamageRatio:percent} 스플래시 피해를 줍니다.</aqua>"
    );
    public static final TowerType CAT_T1 = companion("t1_pet_cat", "아기 고양이 타워", 50, 95, 3.5, 12, 13, 5,
            CatVisual.builder().variant(CatVariants.TABBY).tame(true).build(), CAT_DESC);
    public static final TowerType CAT_T2 = companion("t2_pet_cat", "반려묘 타워", 120, 150, 3.8, 24, 12, 5,
            CatVisual.builder().variant(CatVariants.CALICO).tame(true).build(), CAT_DESC);
    public static final TowerType CAT_T3 = companion("t3_pet_cat", "개냥이 타워", 260, 230, 4.2, 38, 13, 0,
            CatVisual.builder().variant(CatVariants.RAGDOLL).tame(true).build(), CAT_DESC);

    private static final List<String> BIRD_DESC = List.of(
            "<gray>멀리서 쏘며 아군을 돌보는 반려입니다.</gray>",
            "<green>입힌 피해의 {ability.healRatio:percent}만큼 같은 마당에서 체력 비율이 가장 낮은 반려를 회복시킵니다.</green>"
    );
    public static final TowerType BIRD_T1 = companion("t1_pet_bird", "아가새 타워", 55, 85, 6.0, 10, 15, 0,
            ParrotVisual.builder().variant(Parrot.Variant.BLUE).build(), BIRD_DESC);
    public static final TowerType BIRD_T2 = companion("t2_pet_bird", "반려조 타워", 125, 130, 6.5, 21, 14, 0,
            ParrotVisual.builder().variant(Parrot.Variant.YELLOW_BLUE).build(), BIRD_DESC);
    public static final TowerType BIRD_T3 = companion("t3_pet_bird", "개무새 타워", 270, 195, 7.0, 34, 13, -10,
            ParrotVisual.builder().variant(Parrot.Variant.GRAY).build(), BIRD_DESC);

    private static final List<TowerType> ALL = List.of(
            BUTLER_T1, BUTLER_T2,
            TRAINER_T1, TRAINER_T2,
            KEEPER_T1, KEEPER_T2,
            DOG_T1, DOG_T2, DOG_T3,
            CAT_T1, CAT_T2, CAT_T3,
            BIRD_T1, BIRD_T2, BIRD_T3
    );

    private static final Map<PetRole, Set<String>> IDS = Map.of(
            PetRole.BUTLER, ids(BUTLER_T1, BUTLER_T2),
            PetRole.TRAINER, ids(TRAINER_T1, TRAINER_T2),
            PetRole.KEEPER, ids(KEEPER_T1, KEEPER_T2),
            PetRole.DOG, ids(DOG_T1, DOG_T2, DOG_T3),
            PetRole.CAT, ids(CAT_T1, CAT_T2, CAT_T3),
            PetRole.BIRD, ids(BIRD_T1, BIRD_T2, BIRD_T3)
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private PetTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static PetRole roleOf(TowerType type) {
        if (type == null) {
            return null;
        }
        return IDS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(type.id()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public static boolean isPetTower(TowerType type) {
        return roleOf(type) != null;
    }

    public static boolean isOwner(TowerType type) {
        PetRole role = roleOf(type);
        return role != null && role.isOwner();
    }

    public static boolean isCompanion(TowerType type) {
        PetRole role = roleOf(type);
        return role != null && role.isCompanion();
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 1;
        }
        String id = type.id();
        return id.startsWith("t3_") ? 3 : id.startsWith("t2_") ? 2 : 1;
    }

    /** Each owner wears a different cosmetic hat so the three read apart at a glance. */
    public static String hatPath(TowerType type) {
        PetRole role = roleOf(type);
        if (role == null) {
            return null;
        }
        return switch (role) {
            case TRAINER -> "hats/uniques/red_baseball_hat";
            case BUTLER -> "hats/uniques/black_beret_hat";
            case KEEPER -> "hats/villagers/shepherd_hat";
            default -> null;
        };
    }

    public static ItemStack hatStack(TowerType type) {
        String path = hatPath(type);
        if (path == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path))
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private static TowerType owner(String id, String name, long cost, double health, List<String> description) {
        return TowerType.builder(id, name)
                .mineralCost(cost)
                .maxHealth(health)
                .range(0)
                .damage(0)
                .attackIntervalTicks(20)
                .aggroPriority(id.startsWith("t2_") ? -50 : -40)
                .visual(EntityVisual.vanilla("minecraft:villager"))
                .primaryDamageType(DamageType.PHYSICAL)
                .description(description)
                .build();
    }

    private static TowerType companion(String id, String name, long cost, double health, double range, double damage,
                                       int interval, int aggro, EntityVisual visual, List<String> description) {
        return TowerType.builder(id, name)
                .mineralCost(cost)
                .maxHealth(health)
                .range(range)
                .damage(damage)
                .attackIntervalTicks(interval)
                .aggroPriority(aggro)
                .visual(visual)
                .primaryDamageType(DamageType.PHYSICAL)
                .description(description)
                .build();
    }

    private static Set<String> ids(TowerType... types) {
        return Arrays.stream(types).map(TowerType::id).collect(Collectors.toUnmodifiableSet());
    }
}
