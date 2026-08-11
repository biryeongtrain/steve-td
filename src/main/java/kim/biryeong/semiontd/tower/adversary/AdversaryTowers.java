package kim.biryeong.semiontd.tower.adversary;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.FoxVisual;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.animal.Fox;

public final class AdversaryTowers {
    public static final TowerType FOX = TowerType.builder("adversary_fox", "대적자 여우")
            .category(TowerCategory.DIRECT)
            .mineralCost(AdversaryBalance.FOX_COST)
            .maxHealth(AdversaryBalance.defaultFormValue(FoxForm.BASE, "maxHealth"))
            .range(AdversaryBalance.defaultFormValue(FoxForm.BASE, "range"))
            .damage(AdversaryBalance.defaultFormValue(FoxForm.BASE, "damage"))
            .attackIntervalTicks(AdversaryBalance.defaultFormInt(FoxForm.BASE, "attackIntervalTicks"))
            .aggroPriority(50)
            .visual(FoxVisual.builder().variant(Fox.Variant.DEFAULT).build())
            .description(List.of(
                    "<gray>한 마리만 설치할 수 있는 대적자 빌더의 핵심 타워입니다.</gray>",
                    "<green>기본 공격은 반경 {ability.adversary_global.baseSplashRadius:blocks}의 추가 적 최대 {ability.adversary_global.baseSplashExtraTargets:integer}기에게 {ability.adversary_global.baseSplashDamageRatio:percent} 피해를 줍니다.</green>",
                    "<green>숙적 막타 점수에 따라 네 갈래 중 하나로 진화하며 강화 숙적은 더 많은 점수를 줍니다.</green>",
                    "<yellow>형태는 같은 여우에 유지되고 입에 든 아이템으로 표시됩니다.</yellow>"
            ))
            .build();

    public static final TowerType BREEZE_RIVAL = rivalType(RivalKind.BREEZE, false);
    public static final TowerType ENHANCED_BREEZE_RIVAL = rivalType(RivalKind.BREEZE, true);
    public static final TowerType CREEPER_RIVAL = rivalType(RivalKind.CREEPER, false);
    public static final TowerType ENHANCED_CREEPER_RIVAL = rivalType(RivalKind.CREEPER, true);
    public static final TowerType PHANTOM_RIVAL = rivalType(RivalKind.PHANTOM, false);
    public static final TowerType ENHANCED_PHANTOM_RIVAL = rivalType(RivalKind.PHANTOM, true);
    public static final TowerType POLAR_BEAR_RIVAL = rivalType(RivalKind.POLAR_BEAR, false);
    public static final TowerType ENHANCED_POLAR_BEAR_RIVAL = rivalType(RivalKind.POLAR_BEAR, true);

    private static final Map<RivalKind, TowerType> BASE_RIVALS = rivalMap(
            BREEZE_RIVAL,
            CREEPER_RIVAL,
            PHANTOM_RIVAL,
            POLAR_BEAR_RIVAL
    );
    private static final Map<RivalKind, TowerType> ENHANCED_RIVALS = rivalMap(
            ENHANCED_BREEZE_RIVAL,
            ENHANCED_CREEPER_RIVAL,
            ENHANCED_PHANTOM_RIVAL,
            ENHANCED_POLAR_BEAR_RIVAL
    );
    private static final List<TowerType> ALL = List.of(
            FOX,
            BREEZE_RIVAL,
            ENHANCED_BREEZE_RIVAL,
            CREEPER_RIVAL,
            ENHANCED_CREEPER_RIVAL,
            PHANTOM_RIVAL,
            ENHANCED_PHANTOM_RIVAL,
            POLAR_BEAR_RIVAL,
            ENHANCED_POLAR_BEAR_RIVAL
    );
    private static final Set<String> ALL_IDS = Set.of(
            FOX.id(),
            BREEZE_RIVAL.id(),
            ENHANCED_BREEZE_RIVAL.id(),
            CREEPER_RIVAL.id(),
            ENHANCED_CREEPER_RIVAL.id(),
            PHANTOM_RIVAL.id(),
            ENHANCED_PHANTOM_RIVAL.id(),
            POLAR_BEAR_RIVAL.id(),
            ENHANCED_POLAR_BEAR_RIVAL.id()
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private AdversaryTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static TowerType baseRival(RivalKind kind) {
        TowerType type = BASE_RIVALS.get(kind);
        if (type == null) {
            throw new IllegalArgumentException("Unknown rival kind: " + kind);
        }
        return type;
    }

    public static TowerType enhancedRival(RivalKind kind) {
        TowerType type = ENHANCED_RIVALS.get(kind);
        if (type == null) {
            throw new IllegalArgumentException("Unknown rival kind: " + kind);
        }
        return type;
    }

    public static boolean isAdversaryTower(TowerType type) {
        return type != null && ALL_IDS.contains(type.id());
    }

    public static boolean isFox(TowerType type) {
        return matches(type, FOX);
    }

    public static boolean isRival(TowerType type) {
        return rivalKind(type).isPresent();
    }

    public static boolean isEnhancedRival(TowerType type) {
        if (type == null) {
            return false;
        }
        return ENHANCED_RIVALS.values().stream().anyMatch(candidate -> matches(type, candidate));
    }

    public static Optional<RivalKind> rivalKind(TowerType type) {
        if (type == null) {
            return Optional.empty();
        }
        for (Map.Entry<RivalKind, TowerType> entry : BASE_RIVALS.entrySet()) {
            if (matches(type, entry.getValue()) || matches(type, ENHANCED_RIVALS.get(entry.getKey()))) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static boolean matches(TowerType first, TowerType second) {
        return first != null && second != null && first.id().equals(second.id());
    }

    private static TowerType rivalType(RivalKind kind, boolean enhanced) {
        String id = AdversaryBalance.rivalTowerId(kind, enhanced);
        String displayName = (enhanced ? "강화된 " : "") + kind.displayName() + " 숙적";
        double health = AdversaryBalance.defaultRivalBaseHealth(kind)
                * (enhanced ? AdversaryBalance.ENHANCED_RIVAL_HEALTH_MULTIPLIER : 1.0);
        double damage = AdversaryBalance.defaultRivalBaseDamage(kind)
                * (enhanced ? AdversaryBalance.ENHANCED_RIVAL_DAMAGE_MULTIPLIER : 1.0);
        return TowerType.builder(id, displayName)
                .category(TowerCategory.SUMMONER)
                .mineralCost(AdversaryBalance.defaultRivalBaseCost(kind))
                .maxHealth(health)
                .range(AdversaryBalance.defaultRivalRange(kind, enhanced))
                .damage(damage)
                .attackIntervalTicks(AdversaryBalance.defaultRivalAttackIntervalTicks(kind, enhanced))
                .aggroPriority(0)
                .visual(EntityVisual.vanilla(kind.entityTypeId()))
                .description(List.of(
                        "<red>웨이브가 시작되면 설치 위치에서 적으로 변합니다.</red>",
                        "<gray>기본 체력 {stat.maxHealth:number}, 방어 {ability.baseArmor:number}, 공격력 {stat.damage:number}</gray>",
                        "<yellow>여우가 막타를 내면 {ability.scorePerKill:integer}점을 제공합니다.</yellow>",
                        "<dark_red>판매 환불과 처치 보상은 0입니다.</dark_red>"
                ))
                .build();
    }

    private static Map<RivalKind, TowerType> rivalMap(TowerType... types) {
        EnumMap<RivalKind, TowerType> result = new EnumMap<>(RivalKind.class);
        for (TowerType type : types) {
            String id = type.id();
            for (RivalKind kind : RivalKind.values()) {
                if (id.contains("_" + kind.id() + "_")) {
                    result.put(kind, type);
                    break;
                }
            }
        }
        return Map.copyOf(result);
    }

}
