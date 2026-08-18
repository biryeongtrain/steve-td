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
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.level.block.Blocks;

public final class HeroPartyTowers {
    public static final String HERO_ID = "hero_party_hero";

    private static final EntityVisual HIDDEN_ANCHOR = BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build();
    private static final EnumMap<HeroCompanionRole, List<TowerType>> COMPANIONS = new EnumMap<>(HeroCompanionRole.class);
    private static final Map<String, CompanionSpec> SPECS_BY_ID = new LinkedHashMap<>();
    private static final List<TowerType> ALL;

    public static final TowerType HERO = TowerType.builder(HERO_ID, "용사")
            .mineralCost(112)
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
                new long[]{84, 126, 196, 308},
                new double[]{260, 430, 650, 900},
                new double[]{3.0, 3.0, 3.0, 3.0},
                new double[]{7.2, 10.8, 15.6, 21.6},
                new int[]{20, 19, 18, 16},
                80,
                false,
                "적의 공격을 받아 내는 앞줄 동료입니다."
        );
        register(
                HeroCompanionRole.ARCHER,
                new long[]{77, 119, 182, 280},
                new double[]{70, 105, 150, 200},
                new double[]{10.0, 10.0, 10.0, 10.0},
                new double[]{14.4, 20.4, 28.8, 38.4},
                new int[]{15, 13, 10, 7},
                0,
                false,
                "강한 적을 먼저 노리는 원거리 딜러입니다."
        );
        register(
                HeroCompanionRole.MAGE,
                new long[]{98, 147, 231, 364},
                new double[]{80, 120, 165, 220},
                new double[]{8.0, 8.0, 8.0, 8.0},
                new double[]{19.2, 26.4, 37.2, 50.4},
                new int[]{20, 19, 18, 16},
                0,
                true,
                "적이 모인 곳을 공격하는 광역 딜러입니다."
        );
        register(
                HeroCompanionRole.PRIEST,
                new long[]{91, 140, 217, 336},
                new double[]{100, 145, 205, 280},
                new double[]{7.0, 7.0, 7.0, 7.0},
                new double[]{4.8, 7.2, 12, 16.8},
                new int[]{20, 19, 18, 16},
                -5,
                true,
                "다친 파티원을 치유하는 지원가입니다."
        );
        register(
                HeroCompanionRole.ROGUE,
                new long[]{70, 105, 168, 259},
                new double[]{65, 95, 135, 180},
                new double[]{3.5, 3.5, 3.5, 3.5},
                new double[]{12, 16.8, 22.8, 31.2},
                new int[]{10, 10, 9, 8},
                10,
                false,
                "체력이 낮은 적을 마무리하는 근접 딜러입니다."
        );
        register(
                HeroCompanionRole.BARD,
                new long[]{84, 126, 196, 308},
                new double[]{90, 130, 180, 240},
                new double[]{7.0, 7.0, 7.0, 7.0},
                new double[]{4.8, 8.4, 12, 16.8},
                new int[]{20, 19, 18, 16},
                -5,
                true,
                "주변 파티원의 공격을 강화하는 지원가입니다."
        );

        ArrayList<TowerType> all = new ArrayList<>();
        all.add(HERO);
        COMPANIONS.values().forEach(all::addAll);
        ALL = List.copyOf(all);
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
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
                    .description(companionDescription(role, tier, description))
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

    private static List<String> companionDescription(HeroCompanionRole role, int tier, String roleDescription) {
        String first = firstAbilityName(role);
        String second = secondAbilityName(role);
        String abilityLine = switch (tier) {
            case 1 -> "T2에 " + first + ", T3에 " + second + "를 배웁니다.";
            case 2 -> "새 능력: " + first;
            case 3 -> "새 능력: " + second;
            case 4 -> "두 능력의 효과와 발동 주기가 강화됩니다.";
            default -> "";
        };
        return List.of(
                "<gray>" + roleDescription + "</gray>",
                "<yellow>" + abilityLine + "</yellow>"
        );
    }

    static String firstAbilityName(HeroCompanionRole role) {
        return switch (role) {
            case KNIGHT -> "방패 강타";
            case ARCHER -> "관통 사격";
            case MAGE -> "냉기 폭발";
            case PRIEST -> "보호의 축복";
            case ROGUE -> "연속 베기";
            case BARD -> "전투의 노래";
        };
    }

    static String secondAbilityName(HeroCompanionRole role) {
        return switch (role) {
            case KNIGHT -> "수호 진형";
            case ARCHER -> "약점 표식";
            case MAGE -> "마력 폭주";
            case PRIEST -> "연쇄 치유";
            case ROGUE -> "추격";
            case BARD -> "앙코르";
        };
    }

    private record CompanionSpec(HeroCompanionRole role, int tier) {
    }
}
