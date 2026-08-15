package kim.biryeong.semiontd.tower.mage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;

import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

public final class MageTowers {
    public static final TowerType WIZARD = wizardType(
            "mage_wizard",
            "마법사",
            35,
            List.of(
                    "<gray>기본 공격 대신 한 번 선택한 <light_purple>주문</light_purple>을 <aqua>마나</aqua>가 있는 동안 반복 시전합니다.</gray>",
                    "<gold>시전 횟수에 따라 초급→중급→대마법사로 자동 진화하며 주문 피해가 증가합니다.</gold>",
                    "<green>주문을 발동하지 않으면 라운드 종료 시 마나 {ability.mage_global.idleWizardMana:integer}을 생산합니다.</green>",
                    "<red>마나가 부족하면 시전을 멈추고, 마나를 다시 얻으면 자동으로 재개합니다.</red>"
            )
    );
    public static final TowerType PROPHET = prophetType(
            "mage_prophet",
            "예언가",
            35,
            List.of(
                    "<gray>준비 단계에 다음 라운드에 올 정확한 <light_purple>인컴 몬스터</light_purple> 한 종류를 예언합니다.</gray>",
                    "<green>적중하면 첫 일치 인컴을 즉사시키고 마나 {ability.mage_global.prophecyReward:integer}을 얻습니다.</green>",
                    "<green>예언하지 않거나 적중하면 라운드 종료 시 마나 {ability.mage_global.prophetMana:integer}을 생산합니다.</green>",
                    "<red>실패하면 해당 라운드의 자연 마나를 생산하지 못합니다.</red>"
            )
    );
    public static final TowerType MAGIC_CORE = TowerType.builder("mage_core", "마법핵")
            .mineralCost(45)
            .maxHealth(300.0)
            .range(0.0)
            .damage(0.0)
            .attackIntervalTicks(20)
            .aggroPriority(80)
            .visual(EntityVisual.vanilla(byId(EntityType.END_CRYSTAL)))
            .description(List.of(
                    "<gray><aqua>마나</aqua>를 최대 {ability.mage_global.manaCapacity:integer}까지 저장하며 플레이어당 하나만 설치할 수 있습니다.</gray>",
                    "<green>최초 설치 시 마나 {ability.mage_global.startingMana:integer}을 얻습니다.</green>",
                    "<green>살아 있으면 라운드 종료 시 마나 {ability.mage_global.coreMana:integer}을 생산합니다.</green>",
                    "<red>파괴되면 현재 마나의 {ability.mage_global.coreBreakManaLossRatio:percent}를 잃습니다.</red>",
                    "<red>판매하면 모든 마나를 잃고 선택한 주문과 예언이 초기화됩니다.</red>"
            ))
            .build();

    private static final Map<MageSpell, TowerType> SPELL_TYPES = createSpellTypes();
    private static final Map<String, TowerType> PREDICTION_TYPES = createPredictionTypes();
    private static final List<TowerType> ALL = createAll();

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private MageTowers() {
    }

    public static TowerType spellType(MageSpell spell) {
        return SPELL_TYPES.get(spell);
    }

    public static Optional<MageSpell> spellFor(TowerType type) {
        if (type == null) {
            return Optional.empty();
        }
        return SPELL_TYPES.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(type.id()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public static Optional<String> predictionFor(TowerType type) {
        if (type == null) {
            return Optional.empty();
        }
        return PREDICTION_TYPES.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(type.id()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public static Map<String, TowerType> predictionTypes() {
        return PREDICTION_TYPES;
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isMageTower(TowerType type) {
        return type != null && ALL.stream().anyMatch(candidate -> candidate.id().equals(type.id()));
    }

    public static boolean isWizard(TowerType type) {
        return type != null && (type.id().equals(WIZARD.id()) || spellFor(type).isPresent());
    }

    public static boolean isProphet(TowerType type) {
        return type != null && (type.id().equals(PROPHET.id()) || predictionFor(type).isPresent());
    }

    public static boolean isCore(TowerType type) {
        return type != null && type.id().equals(MAGIC_CORE.id());
    }

    public static boolean isTemporary(TowerType type) {
        return predictionFor(type).isPresent();
    }

    private static Map<MageSpell, TowerType> createSpellTypes() {
        EnumMap<MageSpell, TowerType> result = new EnumMap<>(MageSpell.class);
        for (MageSpell spell : MageSpell.values()) {
            result.put(spell, wizardType(
                    "mage_spell_" + spell.id(),
                    spell.displayName() + " 선택",
                    0,
                    spellDescription(spell)
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, TowerType> createPredictionTypes() {
        LinkedHashMap<String, TowerType> result = new LinkedHashMap<>();
        SummonConfig.defaultConfig().summons().values().stream()
                .filter(SummonConfig.SummonDefinition::enabled)
                .forEach(definition -> result.put(definition.id(), prophetType(
                        "mage_prophecy_" + definition.id(),
                        "예언: " + definition.displayName(),
                        0,
                        List.of(
                                "<gray><light_purple>" + definition.displayName() + "</light_purple> 인컴을 예언했습니다.</gray>",
                                "<green>첫 일치 인컴을 즉사시키고 마나 {ability.mage_global.prophecyReward:integer}을 얻습니다.</green>",
                                "<red>한 기도 오지 않으면 이번 라운드의 자연 마나를 생산하지 못합니다.</red>"
                        )
                )));
        return Collections.unmodifiableMap(result);
    }

    private static List<TowerType> createAll() {
        ArrayList<TowerType> result = new ArrayList<>();
        result.add(WIZARD);
        result.add(PROPHET);
        result.add(MAGIC_CORE);
        result.addAll(SPELL_TYPES.values());
        result.addAll(PREDICTION_TYPES.values());
        return List.copyOf(result);
    }

    private static TowerType wizardType(String id, String name, long cost, List<String> description) {
        return TowerType.builder(id, name)
                .mineralCost(cost)
                .maxHealth(50.0)
                .range(0.0)
                .damage(0.0)
                .attackIntervalTicks(20)
                .aggroPriority(5)
                .visual(EntityVisual.vanilla(byId(EntityType.WITCH)))
                .description(description)
                .build();
    }

    private static TowerType prophetType(String id, String name, long cost, List<String> description) {
        return TowerType.builder(id, name)
                .mineralCost(cost)
                .maxHealth(45.0)
                .range(0.0)
                .damage(0.0)
                .attackIntervalTicks(20)
                .aggroPriority(4)
                .visual(EntityVisual.vanilla("minecraft:illusioner"))
                .description(description)
                .build();
    }

    private static List<String> spellDescription(MageSpell spell) {
        String cost = "<aqua>마나 {ability.mage_global." + spell.id() + "ManaCost:integer}</aqua>";
        return switch (spell) {
            case MANA_MISSILE -> List.of(
                    "<gray>선택 후 매 웨이브 " + cost + "를 사용해 반복 시전합니다.</gray>",
                    "<light_purple>피해 {ability.mage_global.missileDamage:integer}의 미사일을 {ability.mage_global.missileCount:integer}회 발사하며 매번 재조준합니다.</light_purple>"
            );
            case WIND_CUTTER -> List.of(
                    "<gray>선택 후 매 웨이브 " + cost + "를 사용해 반복 시전합니다.</gray>",
                    "<light_purple>직선상의 최대 {ability.mage_global.windCutterMaxTargets:integer}기에게 피해 {ability.mage_global.windCutterDamage:integer}을 줍니다.</light_purple>"
            );
            case MANA_BOMB -> List.of(
                    "<gray>선택 후 매 웨이브 " + cost + "를 사용해 반복 시전합니다.</gray>",
                    "<light_purple>{ability.mage_global.manaBombDelayTicks:seconds} 뒤 반경 {ability.mage_global.manaBombRadius:blocks}의 최대 {ability.mage_global.manaBombMaxTargets:integer}기에게 피해 {ability.mage_global.manaBombDamage:integer}을 줍니다.</light_purple>"
            );
            case CHAIN_LIGHTNING -> List.of(
                    "<gray>선택 후 매 웨이브 " + cost + "를 사용해 반복 시전합니다.</gray>",
                    "<light_purple>최대 6기를 {ability.mage_global.chainDamage1:integer}/{ability.mage_global.chainDamage2:integer}/{ability.mage_global.chainDamage3:integer}/{ability.mage_global.chainDamage4:integer}/{ability.mage_global.chainDamage5:integer}/{ability.mage_global.chainDamage6:integer} 피해로 연쇄 공격합니다.</light_purple>"
            );
            case FROST_WAVE -> List.of(
                    "<gray>선택 후 매 웨이브 " + cost + "를 사용해 반복 시전합니다.</gray>",
                    "<light_purple>최대 {ability.mage_global.frostWaveMaxTargets:integer}기에게 피해 {ability.mage_global.frostWaveDamage:integer}과 {ability.mage_global.frostWaveSlow:percent} 감속을 부여합니다.</light_purple>"
            );
            case DIMENSIONAL_COLLAPSE -> List.of(
                    "<gray>선택 후 매 웨이브 <gold>" + cost + "</gold>를 사용해 반복 시전합니다.</gray>",
                    "<gold>첫 적 출현 후 {ability.mage_global.collapseDelayTicks:seconds} 뒤 자기 라인 모든 적에게 피해 {ability.mage_global.collapseDamage:integer}을 줍니다.</gold>"
            );
            case MAGIC_AMPLIFICATION -> List.of(
                    "<gray>선택 후 매 웨이브 " + cost + "를 한 번 사용합니다.</gray>",
                    "<green>자기 포함 반경 {ability.mage_global.supportRadius:blocks}의 내 마법사 주문 피해를 {ability.mage_global.amplificationBonus:percent} 높입니다.</green>"
            );
            case PROJECTILE_BARRIER -> List.of(
                    "<gray>선택 후 매 웨이브 " + cost + "를 한 번 사용합니다.</gray>",
                    "<green>자기 포함 반경 {ability.mage_global.supportRadius:blocks}의 내 마법사가 받는 일반 원거리 피해를 {ability.mage_global.rangedBarrierReduction:percent} 줄입니다.</green>"
            );
        };
    }
}
