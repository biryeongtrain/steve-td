package kim.biryeong.semiontd.tower.adversary;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.FoxVisual;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.animal.Fox;

public final class AdversaryTowers {
    public static final TowerType FOX = TowerType.builder("adversary_fox", "히어로 여우")
            .category(TowerCategory.DIRECT)
            .mineralCost(AdversaryBalance.FOX_COST)
            .maxHealth(AdversaryBalance.defaultFormValue(FoxForm.BASE, "maxHealth"))
            .range(AdversaryBalance.defaultFormValue(FoxForm.BASE, "range"))
            .damage(AdversaryBalance.defaultFormValue(FoxForm.BASE, "damage"))
            .attackIntervalTicks(AdversaryBalance.defaultFormInt(FoxForm.BASE, "attackIntervalTicks"))
            .aggroPriority(50)
            .visual(FoxVisual.builder().variant(Fox.Variant.DEFAULT).build())
            .description(List.of(
                    "<gray>플레이어당 최대 {ability.adversary_global.maxFoxTowers:integer}기까지 설치할 수 있습니다.</gray>",
                    "<green>기본 공격이 반경 {ability.adversary_global.baseSplashRadius:blocks} 안의 다른 적 최대 {ability.adversary_global.baseSplashExtraTargets:integer}기에게 공격력의 {ability.adversary_global.baseSplashDamageRatio:percent}만큼 피해를 줍니다.</green>",
                    "<green>숙적을 직접 처치해 공유 점수를 모으고, 준비 단계에서 원하는 전직을 선택합니다.</green>",
                    "<yellow>같은 전직 계열은 플레이어당 한 여우만 선택할 수 있습니다.</yellow>",
                    "<gold>최종 전직 후 남은 점수 1점당 피해가 {ability.adversary_global.postEvolutionDamageBonusPerScore:percent} 증가합니다. 최대 {ability.adversary_global.postEvolutionDamageBonusCap:percent}입니다.</gold>",
                    "<green>숙적 처치 시 최대 체력의 {ability.adversary_global.baseRivalKillHealRatio:percent}, 강화 숙적은 {ability.adversary_global.enhancedRivalKillHealRatio:percent}를 회복합니다. 웨이브당 최대 {ability.adversary_global.rivalKillHealCapRatioPerWave:percent}입니다.</green>",
                    "<aqua>여우를 노리는 적이 1기를 넘을 때마다 받는 피해가 {ability.adversary_global.focusFireDamageReductionPerExtraAttacker:percent} 감소합니다. 최대 {ability.adversary_global.focusFireDamageReductionCap:percent}입니다.</aqua>",
                    "<green>고유 다중 공격이 없는 전직 형태는 주변 적 최대 {ability.adversary_global.baseSplashExtraTargets:integer}기에게 공격력의 {ability.adversary_global.evolvedSplashDamageRatio:percent}만큼 피해를 줍니다.</green>",
                    "<aqua>질풍의 연쇄 공격과 스컬크 폭발은 마법 피해이며, 나머지 공격은 물리 피해입니다.</aqua>",
                    "<yellow>입에 든 아이템으로 현재 형태를 확인할 수 있습니다.</yellow>"
            ))
            .build();

    private static final Map<FoxForm, TowerType> FOX_TYPES = foxTypes();

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
    private static final List<TowerType> RIVALS = List.of(
            BREEZE_RIVAL, ENHANCED_BREEZE_RIVAL,
            CREEPER_RIVAL, ENHANCED_CREEPER_RIVAL,
            PHANTOM_RIVAL, ENHANCED_PHANTOM_RIVAL,
            POLAR_BEAR_RIVAL, ENHANCED_POLAR_BEAR_RIVAL
    );
    private static final List<TowerType> ALL = java.util.stream.Stream
            .concat(FOX_TYPES.values().stream(), RIVALS.stream())
            .toList();
    private static final Set<String> ALL_IDS = ALL.stream()
            .map(TowerType::id)
            .collect(Collectors.toUnmodifiableSet());

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private AdversaryTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    /** Tower-stat entries kept in the towers section; fox forms live in ability entries. */
    public static List<TowerType> configurableTowers() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(FOX), RIVALS.stream()).toList();
    }

    public static TowerType typeFor(FoxForm form) {
        TowerType type = FOX_TYPES.get(form == null ? FoxForm.BASE : form);
        if (type == null) {
            throw new IllegalArgumentException("Unknown fox form: " + form);
        }
        return type;
    }

    public static TowerType resolvedTypeFor(FoxForm form) {
        FoxForm resolved = form == null ? FoxForm.BASE : form;
        return resolved == FoxForm.BASE ? FOX : configuredFoxType(resolved);
    }

    public static Optional<FoxForm> foxForm(TowerType type) {
        if (type == null) {
            return Optional.empty();
        }
        return FOX_TYPES.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(type.id()))
                .map(Map.Entry::getKey)
                .findFirst();
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
        return foxForm(type).isPresent();
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
                        "<red>웨이브가 시작되면 설치한 자리에서 적으로 변합니다.</red>",
                        "<gray>기본 능력치: 체력 {stat.maxHealth:number} / 방어력 {ability.baseArmor:number} / 공격력 {stat.damage:number}</gray>",
                        "<gray>능력치는 라운드가 오를수록 증가합니다.</gray>",
                        "<gray>플레이어 특성 효과를 받지 않습니다.</gray>",
                        "<yellow>여우가 처치하면 전직 점수 {ability.scorePerKill:integer}점을 얻습니다.</yellow>",
                        "<dark_red>판매 환불과 처치 보상은 없습니다.</dark_red>"
                ))
                .build();
    }

    private static Map<FoxForm, TowerType> foxTypes() {
        EnumMap<FoxForm, TowerType> result = new EnumMap<>(FoxForm.class);
        result.put(FoxForm.BASE, FOX);
        for (FoxForm form : FoxForm.values()) {
            if (form != FoxForm.BASE) {
                result.put(form, foxType(form));
            }
        }
        return Map.copyOf(result);
    }

    private static TowerType foxType(FoxForm form) {
        return foxType(
                form,
                AdversaryBalance.defaultFormValue(form, "maxHealth"),
                AdversaryBalance.defaultFormValue(form, "range"),
                AdversaryBalance.defaultFormValue(form, "damage"),
                AdversaryBalance.defaultFormInt(form, "attackIntervalTicks")
        );
    }

    private static TowerType configuredFoxType(FoxForm form) {
        return foxType(form, form.maxHealth(), form.range(), form.damage(), form.attackIntervalTicks());
    }

    private static TowerType foxType(
            FoxForm form,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks
    ) {
        return TowerType.builder(AdversaryBalance.formConfigId(form), form.displayName())
                .category(TowerCategory.DIRECT)
                .mineralCost(0L)
                .maxHealth(maxHealth)
                .range(range)
                .damage(damage)
                .attackIntervalTicks(attackIntervalTicks)
                .aggroPriority(50)
                .visual(FoxVisual.builder().variant(Fox.Variant.DEFAULT).build())
                .description(formDescription(form))
                .primaryDamageType(form == FoxForm.SCULK_CORE ? DamageType.MAGIC : DamageType.PHYSICAL)
                .build();
    }

    private static List<String> formDescription(FoxForm form) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("<gold>" + form.displayName() + "</gold>");
        lines.add("<gray>전직 점수는 플레이어가 공유하며, 이 형태가 사용한 점수는 다른 여우가 쓸 수 없습니다.</gray>");
        if (form.isIntermediate()) {
            lines.add("<yellow>이 형태로 웨이브를 한 번 완료하면 두 최종 전직 중 하나를 선택할 수 있습니다.</yellow>");
        } else if (form.isFinal()) {
            lines.add("<gold>모든 최종 여우는 남은 공유 점수에 따른 피해 증가를 함께 받습니다.</gold>");
        }
        lines.add(switch (form) {
            case BREEZE -> "<aqua>빠른 공격이 다른 적에게 연쇄 마법 피해를 줍니다.</aqua>";
            case GOLDEN_FANG -> "<yellow>같은 적을 연속 공격하면 추가 타격을 가합니다.</yellow>";
            case SHIELD_BEARER -> "<yellow>받는 피해를 줄이고 공격자를 반격합니다.</yellow>";
            case BELL_KEEPER -> "<aqua>다친 다른 여우 한 기를 주기적으로 회복합니다.</aqua>";
            case BEACON_KEEPER -> "<aqua>다친 다른 여우 최대 두 기를 더 자주 회복합니다.</aqua>";
            case OMINOUS_HEXER -> "<dark_purple>종지기의 회복을 유지하며 적의 공격력과 공격 속도를 낮추고 받는 피해를 늘립니다.</dark_purple>";
            case TRACKER -> "<green>사거리 안의 위협적인 대상을 추적합니다.</green>";
            case FIREWORK_PIERCER -> "<red>웨이브 적을 우선하는 다중 공격을 가합니다.</red>";
            case BIG_GAME_TRACKER -> "<green>인컴 적과 최대 체력이 높은 적을 집중 공격합니다.</green>";
            case ECHO_FOX -> "<dark_aqua>같은 적을 연속 공격할수록 피해가 강해집니다.</dark_aqua>";
            case MACE_EXECUTIONER -> "<red>집중 후 강력한 철퇴 공격과 휩쓸기를 가합니다.</red>";
            case SCULK_CORE -> "<dark_aqua>지연 후 넓은 범위에 마법 폭발을 일으킵니다.</dark_aqua>";
            case BASE -> "<gray>숙적을 처치해 전직 점수를 모읍니다.</gray>";
        });
        return List.copyOf(lines);
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
