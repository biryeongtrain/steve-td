package kim.biryeong.semiontd.tower.hero;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.level.block.Blocks;

public final class HeroPartyTowers {
    public static final String HERO_ID = "hero_party_hero";

    private static final EntityVisual HIDDEN_ANCHOR = BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build();
    private static final EnumMap<HeroCompanionRole, List<TowerType>> COMPANIONS = new EnumMap<>(HeroCompanionRole.class);
    private static final Map<String, CompanionSpec> SPECS_BY_ID = new LinkedHashMap<>();
    private static final List<TowerType> ALL;

    public static final TowerType HERO = TowerType.builder(HERO_ID, "용사")
            .mineralCost(80)
            .maxHealth(160.0)
            .range(3.5)
            .damage(12.0)
            .attackIntervalTicks(12)
            .aggroPriority(30)
            .visual(HIDDEN_ANCHOR)
            .description(List.of(
                    "<gray>무기를 교체하고 퀘스트로 파티를 성장시키는 중심 타워입니다.</gray>",
                    "<yellow>우클릭하여 용사 상점과 현재 퀘스트를 확인할 수 있습니다.</yellow>"
            ))
            .build();

    static {
        register(
                HeroCompanionRole.KNIGHT,
                new long[]{60, 90, 140, 220},
                new double[]{260, 430, 650, 900},
                new double[]{3.0, 3.0, 3.0, 3.0},
                new double[]{6, 9, 13, 18},
                new int[]{20, 19, 18, 16},
                80,
                false,
                "높은 체력과 피해 감소로 앞라인을 지킵니다."
        );
        register(
                HeroCompanionRole.ARCHER,
                new long[]{55, 85, 130, 200},
                new double[]{70, 105, 150, 200},
                new double[]{10.0, 10.0, 10.0, 10.0},
                new double[]{12, 17, 24, 32},
                new int[]{15, 14, 12, 10},
                0,
                false,
                "보스와 최대 체력이 높은 적을 우선 공격합니다."
        );
        register(
                HeroCompanionRole.MAGE,
                new long[]{70, 105, 165, 260},
                new double[]{80, 120, 165, 220},
                new double[]{8.0, 8.0, 8.0, 8.0},
                new double[]{16, 22, 31, 42},
                new int[]{20, 19, 18, 16},
                0,
                true,
                "밀집한 적을 우선 공격하고 주변에 마법 피해를 줍니다."
        );
        register(
                HeroCompanionRole.PRIEST,
                new long[]{65, 100, 155, 240},
                new double[]{100, 145, 205, 280},
                new double[]{7.0, 7.0, 7.0, 7.0},
                new double[]{4, 6, 10, 14},
                new int[]{20, 19, 18, 16},
                -5,
                true,
                "피해를 입은 파티원을 주기적으로 회복합니다."
        );
        register(
                HeroCompanionRole.ROGUE,
                new long[]{50, 75, 120, 185},
                new double[]{65, 95, 135, 180},
                new double[]{3.5, 3.5, 3.5, 3.5},
                new double[]{10, 14, 19, 26},
                new int[]{10, 10, 9, 8},
                10,
                false,
                "체력이 낮은 적을 빠르게 마무리합니다."
        );
        register(
                HeroCompanionRole.BARD,
                new long[]{60, 90, 140, 220},
                new double[]{90, 130, 180, 240},
                new double[]{7.0, 7.0, 7.0, 7.0},
                new double[]{4, 7, 10, 14},
                new int[]{20, 19, 18, 16},
                -5,
                true,
                "주변 파티원의 공격력과 공격 속도를 높입니다."
        );

        ArrayList<TowerType> all = new ArrayList<>();
        all.add(HERO);
        COMPANIONS.values().forEach(all::addAll);
        ALL = List.copyOf(all);
    }

    private HeroPartyTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static List<TowerType> companions(HeroCompanionRole role) {
        return role == null ? List.of() : COMPANIONS.getOrDefault(role, List.of());
    }

    public static TowerType companion(HeroCompanionRole role, int tier) {
        if (role == null || tier < 1 || tier > 4) {
            return null;
        }
        return companions(role).get(tier - 1);
    }

    public static boolean isHeroPartyTower(TowerType type) {
        return type != null && (HERO_ID.equals(type.id()) || SPECS_BY_ID.containsKey(type.id()));
    }

    public static boolean isHero(TowerType type) {
        return type != null && HERO_ID.equals(type.id());
    }

    public static boolean isCompanion(TowerType type) {
        return type != null && SPECS_BY_ID.containsKey(type.id());
    }

    public static Optional<HeroCompanionRole> role(TowerType type) {
        CompanionSpec spec = type == null ? null : SPECS_BY_ID.get(type.id());
        return spec == null ? Optional.empty() : Optional.of(spec.role());
    }

    public static int tier(TowerType type) {
        CompanionSpec spec = type == null ? null : SPECS_BY_ID.get(type.id());
        return spec == null ? 0 : spec.tier();
    }

    private static void register(
            HeroCompanionRole role,
            long[] costs,
            double[] health,
            double[] ranges,
            double[] damage,
            int[] intervals,
            int aggro,
            boolean magic,
            String description
    ) {
        ArrayList<TowerType> types = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            int tier = index + 1;
            String id = "hero_party_" + role.id() + "_" + tier;
            TowerType type = TowerType.builder(id, companionDisplayName(role, tier))
                    .mineralCost(costs[index])
                    .maxHealth(health[index])
                    .range(ranges[index])
                    .damage(damage[index])
                    .attackIntervalTicks(intervals[index])
                    .aggroPriority(aggro)
                    .visual(HIDDEN_ANCHOR)
                    .description(List.of(
                            "<gray>" + description + "</gray>",
                            "<yellow>타워 수 " + (tier + 1) + "</yellow>"
                    ))
                    .primaryDamageType(magic ? DamageType.MAGIC : DamageType.PHYSICAL)
                    .build();
            types.add(type);
            SPECS_BY_ID.put(id, new CompanionSpec(role, tier));
        }
        COMPANIONS.put(role, List.copyOf(types));
    }

    private static String companionDisplayName(HeroCompanionRole role, int tier) {
        if (role != HeroCompanionRole.PRIEST) {
            return role.displayName() + " T" + tier;
        }
        return switch (tier) {
            case 1 -> "견습 사제";
            case 2 -> "중견 사제";
            case 3 -> "베테랑 사제";
            case 4 -> "대사제";
            default -> role.displayName();
        };
    }

    private record CompanionSpec(HeroCompanionRole role, int tier) {
    }
}
